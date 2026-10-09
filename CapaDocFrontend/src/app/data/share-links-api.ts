import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';
import { DocumentRecord } from './documents-api';

export interface ShareLinkRecord {
  id: number;
  shortCode: string;
  expiryDate: string;
  createdAt: string;
}

export interface ShareLinkCreateRequest {
  password: string;
  expiryDate: string;
}

@Injectable({ providedIn: 'root' })
export class ShareLinksApi {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/links';
  private readonly createdByDocument = new Map<number, ShareLinkRecord[]>();

  async create(documentId: number, request: ShareLinkCreateRequest): Promise<ShareLinkRecord> {
    const link = await firstValueFrom(
      this.http.post<ShareLinkRecord>(
        `${this.endpoint}/create/${documentId}/`,
        request,
      ).pipe(timeout(15_000)),
    );
    this.createdByDocument.set(documentId, [
      link,
      ...(this.createdByDocument.get(documentId) ?? []),
    ]);
    return link;
  }

  createdForDocument(documentId: number): ShareLinkRecord[] {
    return this.createdByDocument.get(documentId) ?? [];
  }

  resolve(shortCode: string, password: string): Promise<DocumentRecord> {
    const params = new HttpParams().set('password', password);
    return firstValueFrom(
      this.http.get<DocumentRecord>(
        `${this.endpoint}/${encodeURIComponent(shortCode)}`,
        { params },
      ).pipe(timeout(15_000)),
    );
  }

  async delete(id: number): Promise<void> {
    await firstValueFrom(
      this.http.delete<void>(`${this.endpoint}/${id}`).pipe(timeout(15_000)),
    );
    for (const [documentId, links] of this.createdByDocument) {
      this.createdByDocument.set(documentId, links.filter((link) => link.id !== id));
    }
  }
}
