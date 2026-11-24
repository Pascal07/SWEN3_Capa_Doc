package capa.genaiworker.client;

import capa.genaiworker.config.GenAiProperties;
import capa.genaiworker.exception.OllamaResponseException;
import capa.genaiworker.exception.OllamaUnavailableException;
import capa.genaiworker.model.OllamaGenerateRequest;
import capa.genaiworker.model.OllamaGenerateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaClient {

    private static final String GENERATE_PATH = "/api/generate";

    private final RestClient ollamaRestClient;
    private final GenAiProperties properties;

    public String generate(Long documentId, String prompt) {
        GenAiProperties.Ollama ollama = properties.getOllama();
        String model = ollama.getModel();

        OllamaGenerateRequest request = OllamaGenerateRequest.builder()
                .model(model)
                .prompt(prompt)
                .stream(false)
                .options(OllamaGenerateRequest.Options.builder()
                        .numCtx(ollama.getNumCtx())
                        .numPredict(ollama.getNumPredict())
                        .temperature(ollama.getTemperature())
                        .repeatPenalty(ollama.getRepeatPenalty())
                        .build())
                .build();

        log.info("Sending generate request to Ollama for documentId={}, model={}, promptLength={}, numCtx={}, numPredict={}",
                documentId, model, prompt.length(), ollama.getNumCtx(), ollama.getNumPredict());

        Instant start = Instant.now();
        try {
            OllamaGenerateResponse response = ollamaRestClient.post()
                    .uri(GENERATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(OllamaGenerateResponse.class);

            long durationMs = Duration.between(start, Instant.now()).toMillis();

            if (response == null || response.getResponse() == null || response.getResponse().isBlank()) {
                log.error("Received empty response from Ollama for documentId={} after {} ms", documentId, durationMs);
                throw new OllamaResponseException("Ollama returned an empty summary response for documentId=" + documentId);
            }

            String trimmed = response.getResponse().trim();
            log.info("Received Ollama response for documentId={} in {} ms (summaryLength={})",
                    documentId, durationMs, trimmed.length());
            return trimmed;
        } catch (ResourceAccessException ex) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            log.error("Ollama unreachable or timed out for documentId={} after {} ms: {}",
                    documentId, durationMs, ex.getMessage());
            throw new OllamaUnavailableException(
                    "Ollama service unreachable or timed out for documentId=" + documentId, ex);
        } catch (HttpClientErrorException ex) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.error("Ollama model '{}' not found/loaded (HTTP 404) for documentId={} after {} ms",
                        model, documentId, durationMs);
                throw new OllamaResponseException(
                        "Ollama model '" + model + "' not found or not yet pulled (HTTP 404) for documentId=" + documentId, ex);
            }
            log.error("Ollama returned client error HTTP {} for documentId={} after {} ms",
                    ex.getStatusCode().value(), documentId, durationMs);
            throw new OllamaResponseException(
                    "Ollama returned HTTP " + ex.getStatusCode().value() + " for documentId=" + documentId, ex);
        } catch (HttpServerErrorException ex) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            log.error("Ollama returned server error HTTP {} for documentId={} after {} ms",
                    ex.getStatusCode().value(), documentId, durationMs);
            throw new OllamaUnavailableException(
                    "Ollama returned server error HTTP " + ex.getStatusCode().value() + " for documentId=" + documentId, ex);
        } catch (RestClientException ex) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            if (hasIoCause(ex)) {
                log.error("Ollama I/O error or timeout for documentId={} after {} ms: {}",
                        documentId, durationMs, ex.getMessage());
                throw new OllamaUnavailableException(
                        "Ollama service unreachable or timed out for documentId=" + documentId, ex);
            }
            log.error("Failed to parse or process Ollama response for documentId={} after {} ms: {}",
                    documentId, durationMs, ex.getMessage());
            throw new OllamaResponseException(
                    "Invalid response from Ollama for documentId=" + documentId, ex);
        }
    }

    private boolean hasIoCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
