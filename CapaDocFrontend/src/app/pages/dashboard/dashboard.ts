import { DatePipe } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { Navbar } from '../../components/navbar/navbar';
import { DocumentRecord, DocumentsApi } from '../../data/documents-api';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [Navbar, DatePipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit {
  private readonly documentsApi = inject(DocumentsApi);

  documents: DocumentRecord[] = [];
  isLoading = true;
  deletingId: number | null = null;
  errorMessage = '';
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