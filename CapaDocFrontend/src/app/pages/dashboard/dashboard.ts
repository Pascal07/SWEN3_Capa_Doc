import { DatePipe } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { signal } from '@angular/core';
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
export class Dashboard implements OnInit {
  private readonly documentsApi = inject(DocumentsApi);
  private readonly shareLinksApi = inject(ShareLinksApi);
  readonly auth = inject(AuthService);

  readonly documents = signal<DocumentRecord[]>([]);
  readonly isLoading = signal(true);
  readonly deletingId = signal<number | null>(null);
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
  editedFilename = '';
  readonly filenameExtension = signal('');
  sharePassword = '';
  shareExpiryDate = this.getDefaultExpiryDate();

  ngOnInit(): void {
    void this.loadDocuments();
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
    } catch (error) {
      this.errorMessage.set(this.getRequestErrorMessage(
        error,
        'Dokumente konnten nicht geladen werden. Bitte versuche es erneut.',
      ));
    } finally {
      this.isLoading.set(false);
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
      this.successMessage.set('Dokument wurde gelöscht.');
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
      const details = await this.documentsApi.get(document.id);
      this.editingDocument.set(details);
      this.editedFilename = this.filenameWithoutExtension(details.filename);
      this.filenameExtension.set(this.extensionOf(details.filename));
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
    this.editingDocument.set(null);
    this.editedFilename = '';
    this.filenameExtension.set('');
    this.detailError.set('');
    this.detailMessage.set('');
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
      this.shareLinks.set([link, ...this.shareLinks()]);
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
    } catch (error) {
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
    }

    return fallbackMessage;
  }
}