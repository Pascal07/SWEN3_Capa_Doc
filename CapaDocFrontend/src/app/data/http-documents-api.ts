import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';
import { DocumentCreateRequest, DocumentRecord, DocumentsApi } from './documents-api';

@Injectable({ providedIn: 'root' })
export class HttpDocumentsApi extends DocumentsApi {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/documents';

  list(): Promise<DocumentRecord[]> {
    return firstValueFrom(
      this.http.get<DocumentRecord[]>(this.endpoint).pipe(timeout(15_000)),
    );
  }

  create(request: DocumentCreateRequest): Promise<DocumentRecord> {
    return firstValueFrom(
      this.http.post<DocumentRecord>(this.endpoint, request).pipe(timeout(15_000)),
    );
  }

  async delete(id: number): Promise<void> {
    await firstValueFrom(
      this.http.delete<void>(`${this.endpoint}/${id}`).pipe(timeout(15_000)),
    );
  }
}
