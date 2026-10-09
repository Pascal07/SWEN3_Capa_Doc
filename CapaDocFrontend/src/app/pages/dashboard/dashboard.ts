import { DatePipe } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { signal } from '@angular/core';
import { TimeoutError } from 'rxjs';
import { Navbar } from '../../components/navbar/navbar';
import { DocumentCreateRequest, DocumentRecord, DocumentsApi } from '../../data/documents-api';
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
  readonly auth = inject(AuthService);

  readonly documents = signal<DocumentRecord[]>([]);
  readonly isLoading = signal(true);
  readonly deletingId = signal<number | null>(null);
  readonly editingDocument = signal<DocumentRecord | null>(null);
  readonly isUpdating = signal(false);
  readonly errorMessage = signal('');
  readonly successMessage = signal('');
  editedFilename = '';
  readonly filenameExtension = signal('');

  ngOnInit(): void {
    void this.loadDocuments();
  }

  async loadDocuments(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set('');

    try {
      this.documents.set(await this.documentsApi.list());
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

  openRenameDialog(document: DocumentRecord): void {
    this.errorMessage.set('');
    this.successMessage.set('');
    const extensionStart = document.filename.lastIndexOf('.');
    const hasExtension = extensionStart > 0;
    this.editedFilename = hasExtension
      ? document.filename.slice(0, extensionStart)
      : document.filename;
    this.filenameExtension.set(hasExtension ? document.filename.slice(extensionStart) : '');
    this.editingDocument.set(document);
  }

  cancelRename(): void {
    if (this.isUpdating()) return;
    this.editingDocument.set(null);
    this.editedFilename = '';
    this.filenameExtension.set('');
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
      this.successMessage.set('Dokumentname wurde geändert.');
      this.editingDocument.set(null);
      this.editedFilename = '';
      this.filenameExtension.set('');
    } catch (error) {
      this.errorMessage.set(this.getRequestErrorMessage(
        error,
        'Dokumentname konnte nicht geändert werden. Bitte versuche es erneut.',
      ));
    } finally {
      this.isUpdating.set(false);
    }
  }

  formatSize(sizeBytes: number): string {
    return `${sizeBytes.toLocaleString('de-DE')} Bytes`;
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