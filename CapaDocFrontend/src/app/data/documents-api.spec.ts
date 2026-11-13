import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { formatFileSize, MAX_PDF_FILE_SIZE_BYTES, normalizeUploadedAt } from './documents-api';
import { HttpDocumentsApi } from './http-documents-api';

describe('normalizeUploadedAt', () => {
  it('treats timezone-less API timestamps as UTC', () => {
    expect(new Date(normalizeUploadedAt('2026-10-09T16:16:00')).toISOString())
      .toBe('2026-10-09T16:16:00.000Z');
  });

  describe('formatFileSize', () => {
    it('shows small files in bytes and larger files in readable units', () => {
      expect(formatFileSize(850)).toBe('850 Bytes');
      expect(formatFileSize(1_500)).toBe('1,5 KB');
      expect(formatFileSize(2_500_000)).toBe('2,5 MB');
    });
  });

  it('preserves timestamps that already include a timezone', () => {
    expect(normalizeUploadedAt('2026-10-09T16:16:00+02:00'))
      .toBe('2026-10-09T16:16:00+02:00');
  });

  it('matches backend 10 MB upload limit', () => {
    expect(MAX_PDF_FILE_SIZE_BYTES).toBe(10 * 1024 * 1024);
  });
});

describe('HttpDocumentsApi search', () => {
  let api: HttpDocumentsApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(HttpDocumentsApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('calls GET /api/documents/search with query parameters and normalizes timestamps', async () => {
    const searchPromise = api.search('Hello', 0, 20);

    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/documents/search' &&
        r.params.get('q') === 'Hello' &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '20',
    );
    expect(req.request.method).toBe('GET');

    req.flush([
      {
        id: 1,
        filename: 'HelloWorld.pdf',
        contentType: 'application/pdf',
        sizeBytes: 1024,
        uploadedAt: '2026-11-11T12:00:00',
        ocrStatus: 'DONE',
        indexStatus: 'DONE',
        snippet: 'Hello World',
      },
    ]);

    const results = await searchPromise;
    expect(results.length).toBe(1);
    expect(results[0].filename).toBe('HelloWorld.pdf');
    expect(results[0].uploadedAt).toBe('2026-11-11T12:00:00Z');
    expect(results[0].snippet).toBe('Hello World');
  });
});
