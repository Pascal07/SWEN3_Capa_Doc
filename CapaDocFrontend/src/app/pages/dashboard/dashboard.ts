import { DatePipe } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { signal } from '@angular/core';
import { TimeoutError } from 'rxjs';
import { Navbar } from '../../components/navbar/navbar';
import { DocumentRecord, DocumentsApi } from '../../data/documents-api';
import { AuthService } from '../../services/auth/auth';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [Navbar, DatePipe, RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit {
  private readonly documentsApi = inject(DocumentsApi);
  readonly auth = inject(AuthService);

  readonly documents = signal<DocumentRecord[]>([]);
  readonly isLoading = signal(true);
  readonly deletingId = signal<number | null>(null);
  readonly errorMessage = signal('');
  readonly successMessage = signal('');

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