import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TimeoutError } from 'rxjs';
import { Navbar } from '../../components/navbar/navbar';
import { DocumentCreateRequest, DocumentsApi } from '../../data/documents-api';
import { AuthService } from '../../services/auth/auth';

@Component({
  selector: 'app-upload-page',
  standalone: true,
  imports: [Navbar, FormsModule, RouterLink],
  templateUrl: './upload-page.html',
  styleUrl: './upload-page.css',
})
export class UploadPage {
  private readonly documentsApi = inject(DocumentsApi);
  readonly auth = inject(AuthService);

  readonly selectedFile = signal<File | null>(null);
  readonly isSubmitting = signal(false);
  readonly errorMessage = signal('');
  readonly validationMessage = signal('');
  readonly successMessage = signal('');

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.validationMessage.set('');
    this.errorMessage.set('');
    this.successMessage.set('');
    this.selectedFile.set(null);

    if (!file) return;

    if (file.size <= 0) {
      this.validationMessage.set('Die Datei darf nicht leer sein.');
      input.value = '';
      return;
    }

    this.selectedFile.set(file);
  }

  async createDocument(fileInput: HTMLInputElement): Promise<void> {
    this.validationMessage.set('');
    this.errorMessage.set('');
    this.successMessage.set('');

    const file = this.selectedFile();
    const request: DocumentCreateRequest | null = file
      ? {
          filename: file.name.trim(),
          contentType: this.getContentType(file),
          sizeBytes: file.size,
        }
      : null;

    if (!request || !request.filename || !request.contentType || request.sizeBytes <= 0) {
      this.validationMessage.set('Bitte wähle eine gültige Datei aus.');
      return;
    }

    this.isSubmitting.set(true);

    try {
      await this.documentsApi.create(request);
      this.selectedFile.set(null);
      fileInput.value = '';
      this.successMessage.set('Dokument wurde hinzugefügt.');
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        this.errorMessage.set('Bitte melde dich an, um Dokumente hochzuladen.');
      } else if (error instanceof HttpErrorResponse && error.status === 0) {
        this.errorMessage.set('Die Verbindung zum Backend ist fehlgeschlagen. Bitte prüfe, ob das Backend läuft.');
      } else if (error instanceof TimeoutError) {
        this.errorMessage.set('Das Backend antwortet nicht. Bitte versuche es erneut.');
      } else if (error instanceof HttpErrorResponse && error.status === 403) {
        this.errorMessage.set('Die Anfrage wurde abgelehnt. Bitte lade die Seite neu und versuche es erneut.');
      } else {
        this.errorMessage.set('Dokument konnte nicht hinzugefügt werden. Bitte versuche es erneut.');
      }
    } finally {
      this.isSubmitting.set(false);
    }
  }

  formatSize(sizeBytes: number): string {
    if (sizeBytes < 1_000_000) return `${Math.max(1, Math.round(sizeBytes / 1_000))} KB`;
    return `${(sizeBytes / 1_000_000).toFixed(1)} MB`;
  }

  getContentType(file: File): string {
    if (file.type && file.type !== 'application/octet-stream') return file.type;

    const extension = file.name.split('.').pop()?.toLowerCase();
    const contentTypes: Record<string, string> = {
      csv: 'text/csv',
      doc: 'application/msword',
      docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      jpeg: 'image/jpeg',
      jpg: 'image/jpeg',
      pdf: 'application/pdf',
      png: 'image/png',
      ppt: 'application/vnd.ms-powerpoint',
      pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
      txt: 'text/plain',
      xls: 'application/vnd.ms-excel',
      xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    };

    return contentTypes[extension ?? ''] ?? (file.type || 'application/octet-stream');
  }
}