import { DatePipe } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { Navbar } from '../../components/navbar/navbar';
import { DocumentCreateRequest, DocumentRecord, DocumentsApi } from '../../data/documents-api';
import { MockDocumentsApi } from '../../data/mock-documents-api';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [Navbar, DatePipe],
  providers: [{ provide: DocumentsApi, useClass: MockDocumentsApi }],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit {
  private readonly documentsApi = inject(DocumentsApi);

  documents: DocumentRecord[] = [];
  selectedFile: File | null = null;
  isLoading = true;
  isSubmitting = false;
  deletingId: number | null = null;
  errorMessage = '';
  validationMessage = '';
  successMessage = '';

  ngOnInit(): void {
    void this.loadDocuments();
  }

  async loadDocuments(): Promise<void> {
    this.isLoading = true;
    this.errorMessage = '';

    try {
      this.documents = await this.documentsApi.list();
    } catch {
      this.errorMessage = 'Dokumente konnten nicht geladen werden. Bitte versuche es erneut.';
    } finally {
      this.isLoading = false;
    }
  }

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
    const request: DocumentCreateRequest | null = file
      ? {
          filename: file.name.trim(),
          contentType: file.type || 'application/pdf',
          sizeBytes: file.size,
        }
      : null;

    if (!request || !request.filename || !request.contentType || request.sizeBytes <= 0) {
      this.validationMessage = 'Bitte wähle eine gültige PDF-Datei aus.';
      return;
    }

    this.isSubmitting = true;

    try {
      const createdDocument = await this.documentsApi.create(request);
      this.documents = [createdDocument, ...this.documents];
      this.selectedFile = null;
      fileInput.value = '';
      this.successMessage = 'Dokument wurde hinzugefügt.';
    } catch {
      this.errorMessage = 'Dokument konnte nicht hinzugefügt werden. Bitte versuche es erneut.';
    } finally {
      this.isSubmitting = false;
    }
  }

  async deleteDocument(document: DocumentRecord): Promise<void> {
    if (!window.confirm(`Möchtest du "${document.filename}" wirklich löschen?`)) return;

    this.errorMessage = '';
    this.successMessage = '';
    this.deletingId = document.id;

    try {
      await this.documentsApi.delete(document.id);
      this.documents = this.documents.filter((item) => item.id !== document.id);
      this.successMessage = 'Dokument wurde gelöscht.';
    } catch {
      this.errorMessage = 'Dokument konnte nicht gelöscht werden. Bitte versuche es erneut.';
    } finally {
      this.deletingId = null;
    }
  }

  formatSize(sizeBytes: number): string {
    if (sizeBytes < 1_000_000) return `${Math.max(1, Math.round(sizeBytes / 1_000))} KB`;
    return `${(sizeBytes / 1_000_000).toFixed(1)} MB`;
  }
}