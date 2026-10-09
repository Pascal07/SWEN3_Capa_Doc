import { formatFileSize, normalizeUploadedAt } from './documents-api';

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
});
