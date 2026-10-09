export interface DocumentRecord {
  id: number;
  filename: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
}

export interface DocumentCreateRequest {
  filename: string;
  contentType: string;
  sizeBytes: number;
}

export abstract class DocumentsApi {
  abstract list(): Promise<DocumentRecord[]>;
  abstract get(id: number): Promise<DocumentRecord>;
  abstract create(request: DocumentCreateRequest): Promise<DocumentRecord>;
  abstract update(id: number, request: DocumentCreateRequest): Promise<DocumentRecord>;
  abstract delete(id: number): Promise<void>;
}