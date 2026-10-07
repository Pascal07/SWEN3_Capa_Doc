import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { DocumentRecord, DocumentsApi } from '../../data/documents-api';
import { ShareLinkRecord, ShareLinksApi } from '../../data/share-links-api';
import { Navbar } from '../../components/navbar/navbar';

@Component({
  selector: 'app-share-page',
  standalone: true,
  imports: [Navbar, FormsModule, DatePipe],
  templateUrl: './share-page.html',
  styleUrl: './share-page.css',
})
export class SharePage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly documentsApi = inject(DocumentsApi);
  private readonly shareLinksApi = inject(ShareLinksApi);

  documents: DocumentRecord[] = [];
  selectedDocumentId = '';
  sharePassword = '';
  expiryDate = '';
  createdLink: ShareLinkRecord | null = null;
  shareUrl = '';
  loadError = '';
  createError = '';
  createSuccess = '';

  shortCode = '';
  resolvePassword = '';
  resolvedDocument: DocumentRecord | null = null;
  resolveError = '';
  resolveSuccess = '';
  isResolving = false;
  isDownloading = false;

  ngOnInit(): void {
    this.expiryDate = this.localDateTimeValue(new Date(Date.now() + 7 * 24 * 60 * 60 * 1000));
    this.route.paramMap.subscribe((params) => {
      this.shortCode = params.get('shortCode') ?? '';
      this.resolvedDocument = null;
      this.resolveError = '';
      this.resolveSuccess = '';
    });
    void this.loadDocuments();
  }

  async loadDocuments(): Promise<void> {
    try {
      this.documents = await this.documentsApi.list();
      this.selectedDocumentId = this.documents[0] ? String(this.documents[0].id) : '';
    } catch {
      this.loadError = 'Zum Erstellen eines Share-Codes musst du angemeldet sein.';
    }
  }

  async createShareLink(): Promise<void> {
    this.createError = '';
    this.createSuccess = '';
    this.createdLink = null;

    const documentId = Number(this.selectedDocumentId);
    if (!Number.isInteger(documentId) || documentId <= 0 || !this.sharePassword.trim() || !this.expiryDate) {
      this.createError = 'Bitte wähle ein Dokument, ein Passwort und ein Ablaufdatum aus.';
      return;
    }

    try {
      const link = await this.shareLinksApi.create(documentId, this.sharePassword, `${this.expiryDate}:00`);
      this.createdLink = link;
      this.shareUrl = `${window.location.origin}/share/${encodeURIComponent(link.shortCode)}`;
      this.createSuccess = 'Der Share-Link wurde erstellt.';
      this.sharePassword = '';
    } catch (error) {
      this.createError = error instanceof HttpErrorResponse && error.status === 401
        ? 'Zum Erstellen eines Share-Codes musst du angemeldet sein.'
        : 'Der Share-Link konnte nicht erstellt werden. Prüfe das Ablaufdatum und versuche es erneut.';
    }
  }

  async resolveShareLink(): Promise<void> {
    this.resolveError = '';
    this.resolveSuccess = '';
    this.resolvedDocument = null;

    if (!this.shortCode.trim() || !this.resolvePassword) {
      this.resolveError = 'Bitte gib einen Share-Code und das Passwort ein.';
      return;
    }

    this.isResolving = true;
    try {
      this.resolvedDocument = await this.shareLinksApi.resolve(this.shortCode.trim(), this.resolvePassword);
    } catch (error) {
      this.resolveError = this.linkErrorMessage(error);
    } finally {
      this.isResolving = false;
    }
  }

  async downloadAndSave(): Promise<void> {
    if (!this.resolvedDocument) return;
    this.resolveError = '';
    this.resolveSuccess = '';
    this.isDownloading = true;

    try {
      const file = await this.shareLinksApi.download(this.shortCode.trim(), this.resolvePassword);
      const objectUrl = URL.createObjectURL(file);
      const link = document.createElement('a');
      link.href = objectUrl;
      link.download = this.resolvedDocument.filename;
      link.click();
      setTimeout(() => URL.revokeObjectURL(objectUrl), 0);

      try {
        await this.documentsApi.upload(file, this.resolvedDocument.filename);
        this.resolveSuccess = 'Das PDF wurde heruntergeladen und in deinem Dashboard gespeichert.';
      } catch (error) {
        this.resolveError = error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403)
          ? 'Das PDF wurde heruntergeladen. Melde dich an, um es auch in deinem Dashboard zu speichern.'
          : 'Das PDF wurde heruntergeladen, konnte aber nicht in deinem Dashboard gespeichert werden.';
      }
    } catch (error) {
      this.resolveError = this.linkErrorMessage(error);
    } finally {
      this.isDownloading = false;
    }
  }

  private linkErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) return 'Das Passwort ist falsch.';
      if (error.status === 404) return 'Der Share-Link oder die PDF-Datei wurde nicht gefunden.';
      if (error.status === 410) return 'Dieser Share-Link ist abgelaufen.';
    }
    return 'Der Share-Link konnte nicht überprüft werden. Bitte versuche es erneut.';
  }

  private localDateTimeValue(date: Date): string {
    const offset = date.getTimezoneOffset() * 60_000;
    return new Date(date.getTime() - offset).toISOString().slice(0, 16);
  }

  formatSize(sizeBytes: number): string {
    if (sizeBytes < 1_000_000) return `${Math.max(1, Math.round(sizeBytes / 1_000))} KB`;
    return `${(sizeBytes / 1_000_000).toFixed(1)} MB`;
  }
}