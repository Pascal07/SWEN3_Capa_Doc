import { HttpClient, HttpParams } from '@angular/common/http';
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

  search(query: string, page = 0, size = 20): Promise<DocumentRecord[]> {
    const params = new HttpParams()
      .set('q', query)
      .set('page', String(page))
      .set('size', String(size));

    return firstValueFrom(
      this.http.get<DocumentRecord[]>(`${this.endpoint}/search`, { params }).pipe(
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

  upload(file: File, filename?: string): Promise<DocumentRecord> {
    const cleanFilename = (filename ?? file.name).trim() || 'document.pdf';
    const pdfBlob = file.type === 'application/pdf'
      ? file
      : new Blob([file], { type: 'application/pdf' });

    const formData = new FormData();
    formData.append('file', pdfBlob, cleanFilename);
    formData.append('filename', cleanFilename);

    return firstValueFrom(
      this.http.post<DocumentRecord>(this.endpoint, formData).pipe(
        map((document) => this.normalizeDocument(document)),
        timeout(30_000),
      ),
    );
  }

  create(request: DocumentCreateRequest | File): Promise<DocumentRecord> {
    if (request instanceof File) {
      return this.upload(request);
    }
    return firstValueFrom(
      this.http.post<DocumentRecord>(this.endpoint, request).pipe(
        map((document) => this.normalizeDocument(document)),
        timeout(15_000),
      ),
    );
  }

  download(id: number): Promise<Blob> {
    return firstValueFrom(
      this.http.get(`${this.endpoint}/${id}/download`, { responseType: 'blob' }).pipe(
        timeout(30_000),
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
