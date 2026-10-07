export interface DocumentRecord {
  id: number;
  filename: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
}

export abstract class DocumentsApi {
  abstract list(): Promise<DocumentRecord[]>;
  abstract upload(file: Blob, filename: string): Promise<DocumentRecord>;
  abstract delete(id: number): Promise<void>;
}