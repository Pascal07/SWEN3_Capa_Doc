import { Component, inject } from '@angular/core';
import { DocumentsApi } from '../../data/documents-api';
import { Navbar } from '../../components/navbar/navbar';

@Component({
  selector: 'app-upload-page',
  standalone: true,
  imports: [Navbar],
  templateUrl: './upload-page.html',
  styleUrl: './upload-page.css',
})
export class UploadPage {
  private readonly documentsApi = inject(DocumentsApi);

  selectedFile: File | null = null;
  isSubmitting = false;
  errorMessage = '';
  validationMessage = '';
  successMessage = '';

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.validationMessage = '';
    this.successMessage = '';
    this.selectedFile = null;

    if (!file) return;

    const isPdf = file.name.toLowerCase().endsWith('.pdf')
      && (!file.type || file.type === 'application/pdf');

    if (!isPdf) {
      this.validationMessage = 'Bitte wähle eine PDF-Datei aus.';
      input.value = '';
      return;
    }

    if (file.size <= 0) {
      this.validationMessage = 'Die Datei darf nicht leer sein.';
      input.value = '';
      return;
    }

    this.selectedFile = file;
  }

  async createDocument(fileInput: HTMLInputElement): Promise<void> {
    this.validationMessage = '';
    this.errorMessage = '';
    this.successMessage = '';

    const file = this.selectedFile;
    if (!file || !file.name.trim() || file.size <= 0) {
      this.validationMessage = 'Bitte wähle eine gültige PDF-Datei aus.';
      return;
    }

    this.isSubmitting = true;

    try {
      await this.documentsApi.upload(file, file.name.trim());
      this.selectedFile = null;
      fileInput.value = '';
      this.successMessage = 'Dokument wurde hinzugefügt.';
    } catch {
      this.errorMessage = 'Dokument konnte nicht hochgeladen werden. Bitte versuche es erneut.';
    } finally {
      this.isSubmitting = false;
    }
  }

  formatSize(sizeBytes: number): string {
    if (sizeBytes < 1_000_000) return `${Math.max(1, Math.round(sizeBytes / 1_000))} KB`;
    return `${(sizeBytes / 1_000_000).toFixed(1)} MB`;
  }
}