import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom, map, timeout } from 'rxjs';
import { DocumentCreateRequest, DocumentRecord, DocumentsApi, normalizeUploadedAt } from './documents-api';

@Injectable({ providedIn: 'root' })
export class HttpDocumentsApi extends DocumentsApi {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/documents';

  list(): Promise<DocumentRecord[]> {
    return firstValueFrom(
      this.http.get<DocumentRecord[]>(this.endpoint).pipe(
        map((documents) => documents.map((document) => this.normalizeDocument(document))),
        timeout(15_000),
      ),
    );
  }

  get(id: number): Promise<DocumentRecord> {
    return firstValueFrom(
      this.http.get<DocumentRecord>(`${this.endpoint}/${id}`).pipe(
        map((document) => this.normalizeDocument(document)),
        timeout(15_000),
      ),
    );
  }

  create(request: DocumentCreateRequest): Promise<DocumentRecord> {
    return firstValueFrom(
      this.http.post<DocumentRecord>(this.endpoint, request).pipe(
        map((document) => this.normalizeDocument(document)),
        timeout(15_000),
      ),
    );
  }

  update(id: number, request: DocumentCreateRequest): Promise<DocumentRecord> {
    return firstValueFrom(
      this.http.put<DocumentRecord>(`${this.endpoint}/${id}`, request).pipe(
        map((document) => this.normalizeDocument(document)),
        timeout(15_000),
      ),
    );
  }

  async delete(id: number): Promise<void> {
    await firstValueFrom(
      this.http.delete<void>(`${this.endpoint}/${id}`).pipe(timeout(15_000)),
    );
  }

  private normalizeDocument(document: DocumentRecord): DocumentRecord {
    return { ...document, uploadedAt: normalizeUploadedAt(document.uploadedAt) };
  }
}
