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

// The API serializes container-local timestamps without a timezone; Docker runs in UTC.
export function normalizeUploadedAt(uploadedAt: string): string {
  return /(?:Z|[+-]\d{2}:\d{2})$/i.test(uploadedAt) ? uploadedAt : `${uploadedAt}Z`;
}

export function formatFileSize(sizeBytes: number): string {
  if (sizeBytes < 1_000) {
    return `${sizeBytes.toLocaleString('de-DE')} Bytes`;
  }

  const units = ['KB', 'MB', 'GB', 'TB'];
  let size = sizeBytes;
  let unitIndex = -1;
  do {
    size /= 1_000;
    unitIndex++;
  } while (size >= 1_000 && unitIndex < units.length - 1);

  return `${new Intl.NumberFormat('de-DE', { maximumFractionDigits: 1 }).format(size)} ${units[unitIndex]}`;
}

export abstract class DocumentsApi {
  abstract list(): Promise<DocumentRecord[]>;
  abstract get(id: number): Promise<DocumentRecord>;
  abstract create(request: DocumentCreateRequest): Promise<DocumentRecord>;
  abstract update(id: number, request: DocumentCreateRequest): Promise<DocumentRecord>;
  abstract delete(id: number): Promise<void>;
}