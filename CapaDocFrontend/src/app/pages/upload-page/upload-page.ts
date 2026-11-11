import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TimeoutError } from 'rxjs';
import { Navbar } from '../../components/navbar/navbar';
import { DocumentRecord, DocumentsApi, formatFileSize, MAX_PDF_FILE_SIZE_BYTES } from '../../data/documents-api';
import { AuthService } from '../../services/auth/auth';

export { MAX_PDF_FILE_SIZE_BYTES };

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
  readonly isDragging = signal(false);
  readonly isSubmitting = signal(false);
  readonly errorMessage = signal('');
  readonly validationMessage = signal('');
  readonly successMessage = signal('');
  readonly uploadedDocument = signal<DocumentRecord | null>(null);

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!this.validateAndSelectFile(file)) {
      input.value = '';
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    if (!this.isSubmitting()) {
      this.isDragging.set(true);
    }
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging.set(false);
  }

  onDrop(event: DragEvent, fileInput: HTMLInputElement): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging.set(false);

    if (this.isSubmitting()) {
      return;
    }

    const file = event.dataTransfer?.files?.[0] ?? null;
    if (!this.validateAndSelectFile(file)) {
      fileInput.value = '';
    }
  }

  validateAndSelectFile(file: File | null): boolean {
    this.validationMessage.set('');
    this.errorMessage.set('');
    this.successMessage.set('');
    this.uploadedDocument.set(null);
    this.selectedFile.set(null);

    if (!file) {
      return false;
    }

    if (file.size <= 0) {
      this.validationMessage.set('Die ausgewählte Datei darf nicht leer sein.');
      return false;
    }

    const ext = this.fileExtension(file.name);
    const mimeType = (file.type || '').toLowerCase();
    if (ext !== 'pdf' || (mimeType !== 'application/pdf' && mimeType !== '')) {
      this.validationMessage.set('Es werden nur PDF-Dateien (.pdf, MIME-Typ application/pdf) unterstützt.');
      return false;
    }

    if (file.size > MAX_PDF_FILE_SIZE_BYTES) {
      this.validationMessage.set(
        `Die Datei ist zu groß (${this.formatSize(file.size)}). Maximal erlaubt sind ${this.formatSize(MAX_PDF_FILE_SIZE_BYTES)}.`,
      );
      return false;
    }

    this.selectedFile.set(file);
    return true;
  }

  async createDocument(fileInput: HTMLInputElement): Promise<void> {
    this.validationMessage.set('');
    this.errorMessage.set('');
    this.successMessage.set('');
    this.uploadedDocument.set(null);

    const file = this.selectedFile();
    if (!file || !file.name.trim() || file.size <= 0) {
      this.validationMessage.set('Bitte wähle eine gültige PDF-Datei aus.');
      return;
    }

    if (file.size > MAX_PDF_FILE_SIZE_BYTES) {
      this.validationMessage.set(
        `Die Datei ist zu groß (${this.formatSize(file.size)}). Maximal erlaubt sind ${this.formatSize(MAX_PDF_FILE_SIZE_BYTES)}.`,
      );
      return;
    }

    this.isSubmitting.set(true);

    try {
      const created = await this.documentsApi.upload(file, file.name.trim());
      this.selectedFile.set(null);
      fileInput.value = '';
      this.uploadedDocument.set(created);
      this.successMessage.set(
        `Dokument "${created.filename}" wurde erfolgreich hochgeladen und in RustFS gespeichert (${created.storageKey ?? 'documents/' + created.id + '.pdf'}).`,
      );
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        this.errorMessage.set('Bitte melde dich an, um Dokumente hochzuladen.');
      } else if (error instanceof HttpErrorResponse && error.status === 413) {
        this.errorMessage.set('Die Datei überschreitet die maximal erlaubte Dateigröße (10 MB).');
      } else if (error instanceof HttpErrorResponse && error.status === 400) {
        this.errorMessage.set(error.error?.message ?? 'Ungültige PDF-Datei.');
      } else if (error instanceof HttpErrorResponse && error.status === 0) {
        this.errorMessage.set('Netzwerkfehler: Die Verbindung zum Backend ist fehlgeschlagen. Bitte prüfe, ob das Backend läuft.');
      } else if (error instanceof TimeoutError) {
        this.errorMessage.set('Das Backend antwortet nicht (Timeout). Bitte versuche es erneut.');
      } else if (error instanceof HttpErrorResponse && error.status === 403) {
        this.errorMessage.set('Die Anfrage wurde abgelehnt. Bitte lade die Seite neu und versuche es erneut.');
      } else if (error instanceof HttpErrorResponse && (error.status === 502 || error.status === 503)) {
        this.errorMessage.set(error.error?.message ?? 'Ein Hintergrunddienst (Speicher oder Queue) ist derzeit nicht erreichbar.');
      } else {
        this.errorMessage.set('Dokument konnte nicht hochgeladen werden. Bitte versuche es erneut.');
      }
    } finally {
      this.isSubmitting.set(false);
    }
  }

  formatSize(sizeBytes: number): string {
    return formatFileSize(sizeBytes);
  }

  getContentType(file: File): string {
    return file.type || 'application/pdf';
  }

  private fileExtension(filename: string): string {
    const parts = filename.split('.');
    return parts.length > 1 ? (parts.pop()?.toLowerCase() ?? '') : '';
  }
}
