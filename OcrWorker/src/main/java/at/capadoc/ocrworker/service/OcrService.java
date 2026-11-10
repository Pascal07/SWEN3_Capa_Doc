package at.capadoc.ocrworker.service;

public interface OcrService {

    String extractText(byte[] pdf);
}
