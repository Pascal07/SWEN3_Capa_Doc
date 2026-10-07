import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { DocumentRecord, DocumentsApi } from './documents-api';

@Injectable()
export class HttpDocumentsApi extends DocumentsApi {
  private readonly http = inject(HttpClient);

  list(): Promise<DocumentRecord[]> {
    return firstValueFrom(this.http.get<DocumentRecord[]>('/api/documents'));
  }

  upload(file: Blob, filename: string): Promise<DocumentRecord> {
    const formData = new FormData();
    formData.append('file', file, filename);
    return firstValueFrom(this.http.post<DocumentRecord>('/api/documents/upload', formData));
  }

  async delete(id: number): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`/api/documents/${id}`));
  }
}
