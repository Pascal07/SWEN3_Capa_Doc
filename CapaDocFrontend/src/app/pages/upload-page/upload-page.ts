import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TimeoutError } from 'rxjs';
import { Navbar } from '../../components/navbar/navbar';
import { DocumentsApi, formatFileSize } from '../../data/documents-api';
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

    const ext = this.fileExtension(file.name);
    if (ext !== 'pdf' || (file.type && file.type !== 'application/octet-stream' && file.type !== 'application/pdf')) {
      this.validationMessage.set('Es werden nur PDF-Dateien (.pdf) unterstützt.');
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
    if (!file || !file.name.trim() || file.size <= 0) {
      this.validationMessage.set('Bitte wähle eine gültige PDF-Datei aus.');
      return;
    }

    this.isSubmitting.set(true);

    try {
      const created = await this.documentsApi.upload(file, file.name.trim());
      this.selectedFile.set(null);
      fileInput.value = '';
      this.successMessage.set(
        `Dokument "${created.filename}" wurde hochgeladen und in RustFS gespeichert (${created.storageKey ?? 'documents/' + created.id + '.pdf'}).`,
      );
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        this.errorMessage.set('Bitte melde dich an, um Dokumente hochzuladen.');
      } else if (error instanceof HttpErrorResponse && error.status === 413) {
        this.errorMessage.set('Die Datei überschreitet die maximal erlaubte Dateigröße.');
      } else if (error instanceof HttpErrorResponse && error.status === 400) {
        this.errorMessage.set(error.error?.message ?? 'Ungültige PDF-Datei.');
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
    return formatFileSize(sizeBytes);
  }

  getContentType(file: File): string {
    return file.type || 'application/pdf';
  }

  private fileExtension(filename: string): string {
    return filename.split('.').pop()?.toLowerCase() ?? '';
  }
}
