import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { DocumentRecord } from './documents-api';

export interface ShareLinkRecord {
  id: number;
  shortCode: string;
  expiryDate: string;
  createdAt: string;
}

export abstract class ShareLinksApi {
  abstract create(documentId: number, password: string, expiryDate: string): Promise<ShareLinkRecord>;
  abstract resolve(shortCode: string, password: string): Promise<DocumentRecord>;
  abstract download(shortCode: string, password: string): Promise<Blob>;
}

@Injectable()
export class HttpShareLinksApi extends ShareLinksApi {
  private readonly http = inject(HttpClient);

  create(documentId: number, password: string, expiryDate: string): Promise<ShareLinkRecord> {
    return firstValueFrom(this.http.post<ShareLinkRecord>(`/api/links/create/${documentId}`, {
      password,
      expiryDate,
    }));
  }

  resolve(shortCode: string, password: string): Promise<DocumentRecord> {
    const params = new HttpParams().set('password', password);
    return firstValueFrom(
      this.http.get<DocumentRecord>(`/api/links/${encodeURIComponent(shortCode)}`, { params }),
    );
  }

  download(shortCode: string, password: string): Promise<Blob> {
    const params = new HttpParams().set('password', password);
    return firstValueFrom(
      this.http.get(`/api/links/${encodeURIComponent(shortCode)}/download`, {
        params,
        responseType: 'blob',
      }),
    );
  }
}
