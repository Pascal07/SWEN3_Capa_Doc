import { DocumentCreateRequest, DocumentRecord, DocumentsApi } from './documents-api';

export class MockDocumentsApi extends DocumentsApi {
  private documents: DocumentRecord[] = [
    {
      id: 101,
      filename: 'Mietvertrag_Wien.pdf',
      contentType: 'application/pdf',
      sizeBytes: 1_840_000,
      uploadedAt: '2026-09-28T09:15:00',
    },
    {
      id: 102,
      filename: 'Rechnung_September.pdf',
      contentType: 'application/pdf',
      sizeBytes: 428_000,
      uploadedAt: '2026-09-24T14:32:00',
    },
    {
      id: 103,
      filename: 'Versicherung.pdf',
      contentType: 'application/pdf',
      sizeBytes: 2_310_000,
      uploadedAt: '2026-09-19T11:05:00',
    },
  ];

  async list(): Promise<DocumentRecord[]> {
    return this.documents.map((document) => ({ ...document }));
  }

  async create(request: DocumentCreateRequest): Promise<DocumentRecord> {
    const document: DocumentRecord = {
      ...request,
      id: Math.max(0, ...this.documents.map((item) => item.id)) + 1,
      uploadedAt: new Date().toISOString(),
    };
    this.documents = [document, ...this.documents];
    return { ...document };
  }

  async delete(id: number): Promise<void> {
    this.documents = this.documents.filter((document) => document.id !== id);
  }
}