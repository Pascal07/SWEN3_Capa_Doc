package capa.genaiworker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "genai")
public class GenAiProperties {

    public static final String DEFAULT_PROMPT_TEMPLATE =
            "Fasse den folgenden Dokumenttext in 3 bis 5 prägnanten Sätzen in der Sprache des Dokuments zusammen. "
                    + "Gib ausschließlich die Zusammenfassung aus, ohne Einleitung, ohne Aufzählungszeichen und ohne erfundene Informationen.\n\n"
                    + "Text:\n%s";

    public static final String DEFAULT_MERGE_PROMPT_TEMPLATE =
            "Führe die folgenden Teilzusammenfassungen zu einer kohärenten Zusammenfassung in 3 bis 5 Sätzen in derselben Sprache zusammen. "
                    + "Gib ausschließlich die finale Zusammenfassung aus, ohne Einleitung und ohne erfundene Informationen.\n\n"
                    + "Teilzusammenfassungen:\n%s";

    private Ollama ollama = new Ollama();

    /**
     * Maximale Zeichenlänge für einen einzelnen Zusammenfassungs-Aufruf.
     * Texte oberhalb dieser Grenze werden (bei aktivem Chunking) in Chunks aufgeteilt
     * und mehrstufig zusammengefasst oder andernfalls auf diese Länge gekürzt.
     */
    private int maxTextLength = 12000;

    /**
     * Zielgröße pro Chunk in Zeichen, wenn ein Text {@code maxTextLength} überschreitet.
     */
    private int chunkSize = 4000;

    /**
     * Aktiviert mehrstufiges Chunking bei langen Texten (true) oder einfaches Kürzen (false).
     */
    private boolean chunkingEnabled = true;

    private String promptTemplate = DEFAULT_PROMPT_TEMPLATE;

    private String mergePromptTemplate = DEFAULT_MERGE_PROMPT_TEMPLATE;

    @Data
    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private String model = "qwen2.5:1.5b";
        private int connectTimeoutSeconds = 10;
        private int readTimeoutSeconds = 180;
        private int numCtx = 4096;
        private int numPredict = 256;
        private double temperature = 0.2;
        private double repeatPenalty = 1.15;
    }
}
