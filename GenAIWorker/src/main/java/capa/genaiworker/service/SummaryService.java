package capa.genaiworker.service;

import capa.genaiworker.client.OllamaClient;
import capa.genaiworker.config.GenAiProperties;
import capa.genaiworker.exception.InvalidOcrTextException;
import capa.genaiworker.exception.OllamaResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Erzeugt kompakte Zusammenfassungen aus extrahiertem OCR-Text mittels {@link OllamaClient}.
 *
 * <p>Verhalten bei langen Texten:
 * Überschreitet der normalisierte OCR-Text {@code genai.max-text-length} (Standard: 12.000 Zeichen),
 * wird der Text bei aktiviertem Chunking ({@code genai.chunking-enabled=true}) in Abschnitte von
 * jeweils maximal {@code genai.chunk-size} Zeichen (Standard: 4.000) zerlegt. Jeder Abschnitt wird
 * einzeln zusammengefasst und die Teilzusammenfassungen werden anschließend in einem finalen Aufruf
 * zusammengeführt. Ist Chunking deaktiviert, wird der Text auf {@code genai.max-text-length} gekürzt.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryService {

    private final OllamaClient ollamaClient;
    private final GenAiProperties properties;

    public String generateSummary(Long documentId, String extractedText) {
        if (documentId == null || documentId <= 0) {
            throw new InvalidOcrTextException("Document ID must be a positive number");
        }
        if (extractedText == null || extractedText.isBlank()) {
            throw new InvalidOcrTextException("Extracted OCR text is empty or blank for documentId=" + documentId);
        }

        String normalizedText = normalizeText(extractedText);
        if (normalizedText.isBlank()) {
            throw new InvalidOcrTextException("Normalized OCR text is blank for documentId=" + documentId);
        }

        int maxTextLength = Math.max(200, properties.getMaxTextLength());
        String summary;

        if (normalizedText.length() <= maxTextLength) {
            String prompt = formatPrompt(properties.getPromptTemplate(), normalizedText);
            summary = ollamaClient.generate(documentId, prompt);
        } else if (properties.isChunkingEnabled()) {
            int chunkSize = Math.max(200, Math.min(properties.getChunkSize(), maxTextLength));
            List<String> chunks = splitIntoChunks(normalizedText, chunkSize);
            log.info("OCR text for documentId={} exceeds maxTextLength ({} > {}); splitting into {} chunks (chunkSize={})",
                    documentId, normalizedText.length(), maxTextLength, chunks.size(), chunkSize);

            List<String> partialSummaries = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                log.info("Summarizing chunk {}/{} for documentId={} (chunkLength={})",
                        i + 1, chunks.size(), documentId, chunks.get(i).length());
                String chunkPrompt = formatPrompt(properties.getPromptTemplate(), chunks.get(i));
                partialSummaries.add(ollamaClient.generate(documentId, chunkPrompt));
            }

            String combinedPartials = String.join("\n\n", partialSummaries);
            if (combinedPartials.length() > maxTextLength) {
                combinedPartials = combinedPartials.substring(0, maxTextLength);
            }
            log.info("Merging {} partial summaries for documentId={}", partialSummaries.size(), documentId);
            String mergePrompt = formatPrompt(properties.getMergePromptTemplate(), combinedPartials);
            summary = ollamaClient.generate(documentId, mergePrompt);
        } else {
            log.info("OCR text for documentId={} exceeds maxTextLength ({} > {}); truncating to {} characters",
                    documentId, normalizedText.length(), maxTextLength, maxTextLength);
            String truncated = normalizedText.substring(0, maxTextLength);
            String prompt = formatPrompt(properties.getPromptTemplate(), truncated);
            summary = ollamaClient.generate(documentId, prompt);
        }

        if (summary == null || summary.isBlank()) {
            throw new OllamaResponseException("Generated summary is blank for documentId=" + documentId);
        }
        return summary.trim();
    }

    private String normalizeText(String text) {
        return text.replaceAll("\\r\\n?", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String formatPrompt(String template, String content) {
        String effectiveTemplate = (template != null && !template.isBlank())
                ? template
                : GenAiProperties.DEFAULT_PROMPT_TEMPLATE;
        if (effectiveTemplate.contains("%s")) {
            return effectiveTemplate.replace("%s", content);
        }
        return effectiveTemplate + "\n\n" + content;
    }

    List<String> splitIntoChunks(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        int length = text.length();

        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            if (end < length) {
                int splitPoint = findSplitPoint(text, start, end);
                if (splitPoint > start) {
                    end = splitPoint;
                }
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            start = end;
        }
        return chunks;
    }

    private int findSplitPoint(String text, int start, int maxEnd) {
        int minBoundary = start + (maxEnd - start) / 2;
        int paragraphBreak = text.lastIndexOf("\n\n", maxEnd);
        if (paragraphBreak >= minBoundary) {
            return paragraphBreak + 2;
        }
        int sentenceBreak = text.lastIndexOf(". ", maxEnd);
        if (sentenceBreak >= minBoundary) {
            return sentenceBreak + 2;
        }
        int spaceBreak = text.lastIndexOf(' ', maxEnd);
        if (spaceBreak >= minBoundary) {
            return spaceBreak + 1;
        }
        return maxEnd;
    }
}
