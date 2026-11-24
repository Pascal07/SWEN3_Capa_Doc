import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TimeoutError } from 'rxjs';
import { Navbar } from '../../components/navbar/navbar';
import { DocumentCreateRequest, DocumentRecord, DocumentsApi, formatFileSize } from '../../data/documents-api';
import { ShareLinkRecord, ShareLinksApi } from '../../data/share-links-api';
import { AuthService } from '../../services/auth/auth';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [Navbar, DatePipe, FormsModule, RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit, OnDestroy {
  private readonly documentsApi = inject(DocumentsApi);
  private readonly shareLinksApi = inject(ShareLinksApi);
  readonly auth = inject(AuthService);

  readonly documents = signal<DocumentRecord[]>([]);
  readonly isLoading = signal(true);
  readonly deletingId = signal<number | null>(null);
  readonly downloadingId = signal<number | null>(null);
  readonly editingDocument = signal<DocumentRecord | null>(null);
  readonly isUpdating = signal(false);
  readonly isLoadingDetails = signal(false);
  readonly isCreatingShareLink = signal(false);
  readonly deletingShareLinkId = signal<number | null>(null);
  readonly shareLinks = signal<ShareLinkRecord[]>([]);
  readonly detailMessage = signal('');
  readonly detailError = signal('');
  readonly errorMessage = signal('');
  readonly successMessage = signal('');

  // Search state
  searchQuery = '';
  readonly activeSearchQuery = signal('');
  readonly isSearchActive = signal(false);
  readonly isSearching = signal(false);
  readonly searchResults = signal<DocumentRecord[]>([]);
  readonly searchError = signal('');
  readonly searchHint = signal('');
  readonly nowMs = signal(Date.now());
  private searchDebounceTimer: ReturnType<typeof setTimeout> | null = null;
  private summaryPollTimer: ReturnType<typeof setInterval> | null = null;
  private etaTickTimer: ReturnType<typeof setInterval> | null = null;
  private listPollTimer: ReturnType<typeof setInterval> | null = null;

  readonly displayedDocuments = computed(() =>
    this.isSearchActive() ? this.searchResults() : this.documents(),
  );

  editedFilename = '';
  readonly filenameExtension = signal('');
  sharePassword = '';
  shareExpiryDate = this.getDefaultExpiryDate();

  ngOnInit(): void {
    void this.loadDocuments();
  }

  ngOnDestroy(): void {
    this.clearDebounceTimer();
    this.stopSummaryPolling();
    this.stopListActivityTimers();
  }

  async loadDocuments(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set('');

    try {
      const documents = await this.documentsApi.list();
      this.documents.set(
        [...documents].sort(
          (a, b) => new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime(),
        ),
      );
      this.syncListActivityTimers();
      if (this.isSearchActive() && this.activeSearchQuery()) {
        await this.executeSearch(this.activeSearchQuery());
      }
    } catch (error) {
      this.errorMessage.set(this.getRequestErrorMessage(
        error,
        'Dokumente konnten nicht geladen werden. Bitte versuche es erneut.',
      ));
    } finally {
      this.isLoading.set(false);
    }
  }

  onSearchInput(): void {
    this.clearDebounceTimer();
    this.searchHint.set('');

    const trimmed = this.searchQuery.trim();
    if (!trimmed) {
      if (this.isSearchActive()) {
        this.isSearchActive.set(false);
        this.activeSearchQuery.set('');
        this.searchResults.set([]);
        this.searchError.set('');
        this.isSearching.set(false);
      }
      return;
    }

    this.searchDebounceTimer = setTimeout(() => {
      void this.executeSearch(trimmed);
    }, 350);
  }

  async onSearchSubmit(): Promise<void> {
    this.clearDebounceTimer();
    const trimmed = this.searchQuery.trim();

    if (!trimmed) {
      this.isSearchActive.set(false);
      this.activeSearchQuery.set('');
      this.searchResults.set([]);
      this.searchError.set('');
      this.isSearching.set(false);
      this.searchHint.set('Bitte gib einen Suchbegriff ein. Es wird die normale Dokumentenliste angezeigt.');
      return;
    }

    await this.executeSearch(trimmed);
  }

  async executeSearch(query: string): Promise<void> {
    const trimmed = query.trim();
    if (!trimmed) {
      return;
    }

    this.searchHint.set('');
    this.searchError.set('');
    this.isSearchActive.set(true);
    this.activeSearchQuery.set(trimmed);
    this.isSearching.set(true);

    try {
      const results = await this.documentsApi.search(trimmed, 0, 20);
      this.searchResults.set(results);
    } catch (error) {
      this.searchResults.set([]);
      this.searchError.set(this.getSearchErrorMessage(error));
    } finally {
      this.isSearching.set(false);
    }
  }

  retrySearch(): void {
    const query = this.activeSearchQuery() || this.searchQuery.trim();
    if (query) {
      void this.executeSearch(query);
    }
  }

  clearSearch(): void {
    this.clearDebounceTimer();
    this.searchQuery = '';
    this.activeSearchQuery.set('');
    this.isSearchActive.set(false);
    this.isSearching.set(false);
    this.searchResults.set([]);
    this.searchError.set('');
    this.searchHint.set('');
  }

  private clearDebounceTimer(): void {
    if (this.searchDebounceTimer !== null) {
      clearTimeout(this.searchDebounceTimer);
      this.searchDebounceTimer = null;
    }
  }

  async downloadDocument(doc: DocumentRecord): Promise<void> {
    this.errorMessage.set('');
    this.detailError.set('');
    this.downloadingId.set(doc.id);

    try {
      const blob = await this.documentsApi.download(doc.id);
      const url = window.URL.createObjectURL(blob);
      const anchor = window.document.createElement('a');
      anchor.href = url;
      anchor.download = doc.filename || `document-${doc.id}.pdf`;
      window.document.body.appendChild(anchor);
      anchor.click();
      window.document.body.removeChild(anchor);
      window.URL.revokeObjectURL(url);
    } catch (error) {
      const msg = this.getRequestErrorMessage(
        error,
        'Dokument konnte nicht aus RustFS heruntergeladen werden.',
      );
      if (this.editingDocument()) {
        this.detailError.set(msg);
      } else {
        this.errorMessage.set(msg);
      }
    } finally {
      this.downloadingId.set(null);
    }
  }

  async deleteDocument(document: DocumentRecord): Promise<void> {
    if (!window.confirm(`Möchtest du "${document.filename}" wirklich löschen?`)) return;

    this.errorMessage.set('');
    this.successMessage.set('');
    this.deletingId.set(document.id);

    try {
      await this.documentsApi.delete(document.id);
      this.documents.update((items) => items.filter((item) => item.id !== document.id));
      this.searchResults.update((items) => items.filter((item) => item.id !== document.id));
      if (this.editingDocument()?.id === document.id) {
        this.editingDocument.set(null);
      }
      this.successMessage.set('Dokument wurde aus Datenbank, RustFS und dem Suchindex gelöscht.');
    } catch (error) {
      this.errorMessage.set(this.getRequestErrorMessage(
        error,
        'Dokument konnte nicht gelöscht werden. Bitte versuche es erneut.',
      ));
    } finally {
      this.deletingId.set(null);
    }
  }

  async openDocumentDetails(document: DocumentRecord): Promise<void> {
    this.successMessage.set('');
    this.errorMessage.set('');
    this.detailError.set('');
    this.detailMessage.set('');
    this.editingDocument.set(document);
    const extensionStart = document.filename.lastIndexOf('.');
    const hasExtension = extensionStart > 0;
    this.editedFilename = hasExtension
      ? document.filename.slice(0, extensionStart)
      : document.filename;
    this.filenameExtension.set(hasExtension ? document.filename.slice(extensionStart) : '');
    this.shareLinks.set(this.shareLinksApi.createdForDocument(document.id));
    this.sharePassword = '';
    this.shareExpiryDate = this.getDefaultExpiryDate();
    this.isLoadingDetails.set(true);

    try {
      const [details, links] = await Promise.all([
        this.documentsApi.get(document.id),
        this.shareLinksApi.listForDocument(document.id),
      ]);
      this.editingDocument.set(details);
      this.shareLinks.set(links);
      this.editedFilename = this.filenameWithoutExtension(details.filename);
      this.filenameExtension.set(this.extensionOf(details.filename));
      this.syncSummaryPolling(details);
    } catch (error) {
      this.detailError.set(this.getRequestErrorMessage(
        error,
        'Dokumentdetails konnten nicht geladen werden.',
      ));
    } finally {
      this.isLoadingDetails.set(false);
    }
  }

  cancelRename(): void {
    if (this.isUpdating() || this.isCreatingShareLink() || this.deletingShareLinkId() !== null) return;
    this.stopSummaryPolling();
    this.editingDocument.set(null);
    this.editedFilename = '';
    this.filenameExtension.set('');
    this.detailError.set('');
    this.detailMessage.set('');
  }

  private syncSummaryPolling(document: DocumentRecord): void {
    this.stopSummaryPolling();
    if (document.summary || document.ocrStatus === 'FAILED' || document.summaryStatus === 'FAILED') {
      return;
    }

    const documentId = document.id;
    this.summaryPollTimer = setInterval(() => {
      void this.pollDocumentSummary(documentId);
    }, 5_000);
  }

  private async pollDocumentSummary(documentId: number): Promise<void> {
    if (this.editingDocument()?.id !== documentId) {
      this.stopSummaryPolling();
      return;
    }

    try {
      const refreshed = await this.documentsApi.get(documentId);
      if (this.editingDocument()?.id !== documentId) {
        this.stopSummaryPolling();
        return;
      }

      this.editingDocument.set(refreshed);
      this.documents.update((items) =>
        items.map((item) => item.id === refreshed.id ? refreshed : item),
      );
      this.searchResults.update((items) =>
        items.map((item) => item.id === refreshed.id ? { ...refreshed, snippet: item.snippet } : item),
      );

      if (refreshed.summary || refreshed.ocrStatus === 'FAILED' || refreshed.summaryStatus === 'FAILED') {
        this.stopSummaryPolling();
      }
    } catch {
      // Ignorieren während Background-Polling; manueller Reload bleibt jederzeit möglich
    }
  }

  private stopSummaryPolling(): void {
    if (this.summaryPollTimer !== null) {
      clearInterval(this.summaryPollTimer);
      this.summaryPollTimer = null;
    }
  }

  getSummaryEtaLabel(doc: DocumentRecord): string | null {
    if (!this.isSummaryPendingOrRunning(doc)) {
      return null;
    }

    const now = this.nowMs();
    const docUploadedAt = new Date(doc.uploadedAt).getTime();
    const ownEstimatedTotal = this.estimateSummarySeconds(doc.sizeBytes);
    const elapsedSeconds = Number.isFinite(docUploadedAt)
      ? Math.max(0, Math.floor((now - docUploadedAt) / 1000))
      : 0;

    let remainingSeconds = ownEstimatedTotal - elapsedSeconds;

    // Queue-Position berücksichtigen: Falls dieses Dokument noch PENDING ist,
    // Restzeit älterer aktiver/wartender Dokumente (Ollama verarbeitet sequentiell) addieren.
    if ((doc.summaryStatus ?? 'PENDING') !== 'IN_PROGRESS' && Number.isFinite(docUploadedAt)) {
      const aheadDocs = this.documents().filter((other) => {
        if (other.id === doc.id || !this.isSummaryPendingOrRunning(other)) {
          return false;
        }
        const otherUploadedAt = new Date(other.uploadedAt).getTime();
        return Number.isFinite(otherUploadedAt) && otherUploadedAt < docUploadedAt;
      });

      const queueWaitSeconds = aheadDocs.reduce((sum, other) => {
        const otherTotal = this.estimateSummarySeconds(other.sizeBytes);
        const otherElapsed = Math.max(0, Math.floor((now - new Date(other.uploadedAt).getTime()) / 1000));
        return sum + Math.max(5, otherTotal - otherElapsed);
      }, 0);

      remainingSeconds = Math.max(ownEstimatedTotal, remainingSeconds) + queueWaitSeconds;
    }

    if (remainingSeconds <= 0) {
      return 'gleich fertig…';
    }
    return this.formatEtaSeconds(remainingSeconds);
  }

  private isSummaryPendingOrRunning(doc: DocumentRecord): boolean {
    if (doc.summary) {
      return false;
    }
    if (doc.ocrStatus === 'FAILED') {
      return false;
    }
    const status = doc.summaryStatus ?? 'PENDING';
    return status === 'PENDING' || status === 'IN_PROGRESS';
  }

  private estimateSummarySeconds(sizeBytes: number): number {
    const safeBytes = Math.max(0, sizeBytes || 0);
    // Richtwert für CPU-only qwen2.5:1.5b (num_predict=256):
    // ~25s Basiszeit (OCR + Modell-Eval + ~200-256 Tokens) + ~5s pro 100 KB Dateigröße, gedeckelt auf 180s.
    return Math.min(180, Math.max(25, Math.round(25 + (safeBytes / 100_000) * 5)));
  }

  private formatEtaSeconds(totalSeconds: number): string {
    const rounded = Math.max(1, Math.round(totalSeconds));
    if (rounded < 60) {
      return `ca. ${rounded}s`;
    }
    const minutes = Math.floor(rounded / 60);
    const seconds = rounded % 60;
    return seconds === 0 ? `ca. ${minutes}m` : `ca. ${minutes}m ${seconds}s`;
  }

  private syncListActivityTimers(): void {
    const hasPending = this.documents().some((doc) => this.isSummaryPendingOrRunning(doc));
    if (!hasPending) {
      this.stopListActivityTimers();
      return;
    }

    if (this.etaTickTimer === null) {
      this.nowMs.set(Date.now());
      this.etaTickTimer = setInterval(() => {
        this.nowMs.set(Date.now());
      }, 1_000);
    }

    if (this.listPollTimer === null) {
      this.listPollTimer = setInterval(() => {
        void this.refreshDocumentsSilently();
      }, 5_000);
    }
  }

  private async refreshDocumentsSilently(): Promise<void> {
    try {
      const documents = await this.documentsApi.list();
      const sorted = [...documents].sort(
        (a, b) => new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime(),
      );
      this.documents.set(sorted);

      const currentEditing = this.editingDocument();
      if (currentEditing) {
        const updatedEditing = sorted.find((item) => item.id === currentEditing.id);
        if (updatedEditing) {
          this.editingDocument.set(updatedEditing);
        }
      }

      this.syncListActivityTimers();
    } catch {
      // Stilles Background-Polling: Fehler nicht als Banner einblenden
    }
  }

  private stopListActivityTimers(): void {
    if (this.etaTickTimer !== null) {
      clearInterval(this.etaTickTimer);
      this.etaTickTimer = null;
    }
    if (this.listPollTimer !== null) {
      clearInterval(this.listPollTimer);
      this.listPollTimer = null;
    }
  }

  async saveFilename(): Promise<void> {
    const document = this.editingDocument();
    const name = this.editedFilename.trim();
    if (!document || !name) return;

    const filename = `${name}${this.filenameExtension()}`;

    if (filename === document.filename) {
      this.cancelRename();
      return;
    }

    const request: DocumentCreateRequest = {
      filename,
      contentType: document.contentType,
      sizeBytes: document.sizeBytes,
    };

    this.errorMessage.set('');
    this.isUpdating.set(true);

    try {
      const updatedDocument = await this.documentsApi.update(document.id, request);
      this.documents.update((items) =>
        items.map((item) => item.id === updatedDocument.id ? updatedDocument : item),
      );
      this.searchResults.update((items) =>
        items.map((item) => item.id === updatedDocument.id ? { ...updatedDocument, snippet: item.snippet } : item),
      );
      this.editingDocument.set(updatedDocument);
      this.detailMessage.set('Dokumentname wurde geändert.');
    } catch (error) {
      this.detailError.set(this.getRequestErrorMessage(
        error,
        'Dokumentname konnte nicht geändert werden. Bitte versuche es erneut.',
      ));
    } finally {
      this.isUpdating.set(false);
    }
  }

  async createShareLink(): Promise<void> {
    const document = this.editingDocument();
    const password = this.sharePassword.trim();
    if (!document || !password || !this.shareExpiryDate) {
      this.detailError.set('Bitte gib ein Passwort und ein Ablaufdatum an.');
      return;
    }
    if (new Date(this.shareExpiryDate).getTime() <= Date.now()) {
      this.detailError.set('Das Ablaufdatum muss in der Zukunft liegen.');
      return;
    }

    this.detailError.set('');
    this.detailMessage.set('');
    this.isCreatingShareLink.set(true);

    try {
      const link = await this.shareLinksApi.create(document.id, {
        password,
        expiryDate: this.shareExpiryDate,
      });
      this.shareLinks.set([link, ...this.shareLinks().filter((item) => item.id !== link.id)]);
      this.sharePassword = '';
      this.detailMessage.set('Share-Link wurde erstellt.');
    } catch (error) {
      this.detailError.set(this.getRequestErrorMessage(error, 'Share-Link konnte nicht erstellt werden.'));
    } finally {
      this.isCreatingShareLink.set(false);
    }
  }

  async deleteShareLink(link: ShareLinkRecord): Promise<void> {
    this.detailError.set('');
    this.detailMessage.set('');
    this.deletingShareLinkId.set(link.id);

    try {
      await this.shareLinksApi.delete(link.id);
      const links = this.shareLinks().filter((item) => item.id !== link.id);
      this.shareLinks.set(links);
      this.detailMessage.set('Share-Link wurde gelöscht.');
    } catch (error) {
      this.detailError.set(this.getRequestErrorMessage(error, 'Share-Link konnte nicht gelöscht werden.'));
    } finally {
      this.deletingShareLinkId.set(null);
    }
  }

  shareLinkUrl(link: ShareLinkRecord): string {
    return `${window.location.origin}/share/${encodeURIComponent(link.shortCode)}`;
  }

  async copyShareLink(link: ShareLinkRecord): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.shareLinkUrl(link));
      this.detailMessage.set('Share-Link wurde kopiert.');
      this.detailError.set('');
    } catch {
      this.detailError.set('Der Share-Link konnte nicht in die Zwischenablage kopiert werden.');
    }
  }

  private getDefaultExpiryDate(): string {
    const date = new Date(Date.now() + 24 * 60 * 60 * 1000);
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  private filenameWithoutExtension(filename: string): string {
    const extensionStart = filename.lastIndexOf('.');
    return extensionStart > 0 ? filename.slice(0, extensionStart) : filename;
  }

  private extensionOf(filename: string): string {
    const extensionStart = filename.lastIndexOf('.');
    return extensionStart > 0 ? filename.slice(extensionStart) : '';
  }

  formatSize(sizeBytes: number): string {
    return formatFileSize(sizeBytes);
  }

  private getSearchErrorMessage(error: unknown): string {
    if (error instanceof TimeoutError) {
      return 'Zeitüberschreitung bei der Suche. Bitte versuche es erneut.';
    }
    if (error instanceof HttpErrorResponse) {
      if (error.status === 503 || error.status === 502) {
        return 'Der Suchdienst (Elasticsearch) ist derzeit nicht erreichbar. Bitte prüfe den Service und versuche es erneut.';
      }
      if (error.status === 400) {
        return error.error?.message || 'Ungültige Suchanfrage. Bitte gib einen gültigen Suchbegriff ein.';
      }
      if (error.status === 401) {
        return 'Bitte melde dich an, um Dokumente zu durchsuchen.';
      }
      if (error.status === 0) {
        return 'Netzwerkfehler: Die Verbindung zum Backend ist fehlgeschlagen.';
      }
    }
    return 'Die Suche konnte nicht durchgeführt werden. Bitte versuche es erneut.';
  }

  private getRequestErrorMessage(error: unknown, fallbackMessage: string): string {
    if (error instanceof TimeoutError) {
      return 'Das Backend antwortet nicht. Bitte versuche es erneut.';
    }

    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) {
        return 'Bitte melde dich an, um deine Dokumente zu sehen.';
      }
      if (error.status === 0) {
        return 'Die Verbindung zum Backend ist fehlgeschlagen. Bitte prüfe, ob das Backend läuft.';
      }
      if (error.status === 408) {
        return 'Das Backend antwortet nicht. Bitte versuche es erneut.';
      }
      if (error.status === 404) {
        return 'Die Datei wurde im Speicher nicht gefunden.';
      }
    }

    return fallbackMessage;
  }
}
