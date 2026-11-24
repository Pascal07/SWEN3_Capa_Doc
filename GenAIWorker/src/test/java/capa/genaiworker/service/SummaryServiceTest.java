package capa.genaiworker.service;

import capa.genaiworker.client.OllamaClient;
import capa.genaiworker.config.GenAiProperties;
import capa.genaiworker.exception.InvalidOcrTextException;
import capa.genaiworker.exception.OllamaUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummaryServiceTest {

    @Mock
    private OllamaClient ollamaClient;

    private GenAiProperties properties;
    private SummaryService summaryService;

    @BeforeEach
    void setUp() {
        properties = new GenAiProperties();
        properties.setMaxTextLength(500);
        properties.setChunkSize(200);
        properties.setChunkingEnabled(true);
        summaryService = new SummaryService(ollamaClient, properties);
    }

    @Test
    void generateSummary_normalText_callsOllamaOnceAndReturnsTrimmedSummary() {
        when(ollamaClient.generate(eq(10L), anyString()))
                .thenReturn("  Das Dokument beschreibt eine Rechnung über 120 EUR.  ");

        String summary = summaryService.generateSummary(10L, "Rechnung Nr. 123 über 120 EUR.");

        assertThat(summary).isEqualTo("Das Dokument beschreibt eine Rechnung über 120 EUR.");
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(ollamaClient, times(1)).generate(eq(10L), promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("Rechnung Nr. 123 über 120 EUR.");
    }

    @Test
    void generateSummary_longTextWithChunking_summarizesChunksAndMerges() {
        String longText = "Absatz eins mit vielen Informationen über das Projekt. ".repeat(15);
        assertThat(longText.length()).isGreaterThan(properties.getMaxTextLength());

        when(ollamaClient.generate(eq(20L), anyString()))
                .thenReturn("Teilzusammenfassung 1.")
                .thenReturn("Teilzusammenfassung 2.")
                .thenReturn("Teilzusammenfassung 3.")
                .thenReturn("Teilzusammenfassung 4.")
                .thenReturn("Teilzusammenfassung 5.")
                .thenReturn("Finale konsolidierte Zusammenfassung.");

        String summary = summaryService.generateSummary(20L, longText);

        assertThat(summary).isEqualTo("Finale konsolidierte Zusammenfassung.");
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(ollamaClient, times(6)).generate(eq(20L), promptCaptor.capture());
        List<String> prompts = promptCaptor.getAllValues();
        assertThat(prompts.getLast()).contains("Teilzusammenfassung 1.");
        assertThat(prompts.getLast()).contains("Teilzusammenfassung 5.");
    }

    @Test
    void generateSummary_longTextWithChunkingDisabled_truncatesAndCallsOllamaOnce() {
        properties.setChunkingEnabled(false);
        String longText = "A".repeat(800);

        when(ollamaClient.generate(eq(21L), anyString())).thenReturn("Gekürzte Zusammenfassung.");

        String summary = summaryService.generateSummary(21L, longText);

        assertThat(summary).isEqualTo("Gekürzte Zusammenfassung.");
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(ollamaClient, times(1)).generate(eq(21L), promptCaptor.capture());
        assertThat(promptCaptor.getValue()).doesNotContain("A".repeat(600));
    }

    @Test
    void generateSummary_emptyOrBlankText_throwsInvalidOcrTextExceptionWithoutCallingOllama() {
        assertThatThrownBy(() -> summaryService.generateSummary(30L, "   \n\t  "))
                .isInstanceOf(InvalidOcrTextException.class);

        assertThatThrownBy(() -> summaryService.generateSummary(30L, null))
                .isInstanceOf(InvalidOcrTextException.class);

        verify(ollamaClient, never()).generate(anyLong(), anyString());
    }

    @Test
    void generateSummary_ollamaFailure_propagatesException() {
        when(ollamaClient.generate(eq(40L), anyString()))
                .thenThrow(new OllamaUnavailableException("Ollama down"));

        assertThatThrownBy(() -> summaryService.generateSummary(40L, "Gültiger Dokumenttext"))
                .isInstanceOf(OllamaUnavailableException.class)
                .hasMessageContaining("Ollama down");
    }
}
