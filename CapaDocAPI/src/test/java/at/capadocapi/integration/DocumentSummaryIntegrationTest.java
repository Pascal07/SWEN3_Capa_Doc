package at.capadocapi.integration;

import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.dto.OcrMessage;
import at.capadocapi.model.dto.SummaryResultMessage;
import at.capadocapi.repository.DocumentRepository;
import at.capadocapi.service.DocumentSummaryListener;
import at.capadocapi.service.Interfaces.DocumentMessagePublisher;
import at.capadocapi.service.Interfaces.DocumentSummaryService;
import at.capadocapi.service.Interfaces.StorageService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-End Integrationstest für den Sprint-5-Ablauf im REST-Server:
 * PDF-Upload -> OCR-Auftrag -> GenAI-Zusammenfassung (Ollama per leichtgewichtigem lokalem Mock-HTTP-Server
 * statt echtem Ollama-Container, da das LLM-Image für schnelle Maven-Builds zu schwergewichtig ist)
 * -> Speicherung in DB (inkl. Flyway-Migration) -> Abruf über GET /documents/{id}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentSummaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentSummaryService documentSummaryService;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private DocumentMessagePublisher messagePublisher;

    private HttpServer mockOllamaServer;
    private String mockOllamaBaseUrl;

    @BeforeEach
    void setUp() throws Exception {
        mockOllamaServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockOllamaServer.createContext("/api/generate", exchange -> {
            byte[] responseBytes = """
                    {
                      "model": "qwen2.5:1.5b",
                      "response": "Das hochgeladene PDF beschreibt den Projektstatus von CapaDoc. Alle Meilensteine wurden termingerecht erreicht.",
                      "done": true
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });
        mockOllamaServer.start();
        mockOllamaBaseUrl = "http://localhost:" + mockOllamaServer.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (mockOllamaServer != null) {
            mockOllamaServer.stop(0);
        }
    }

    @Test
    void uploadPdf_thenSummaryGeneratedAndStored_isReturnedByGetDocumentById() throws Exception {
        byte[] pdfBytes = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                "sprint5-demo.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfBytes
        );

        // 1. PDF per POST hochladen
        String uploadBody = mockMvc.perform(multipart("/api/documents")
                        .file(pdfFile)
                        .with(oidcLogin().idToken(t -> t.subject("integration-user")))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.filename").value("sprint5-demo.pdf"))
                .andExpect(jsonPath("$.summary").isEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long documentId = objectMapper.readTree(uploadBody).get("id").asLong();

        // Verify OCR message was queued for the uploaded document
        ArgumentCaptor<OcrMessage> ocrCaptor = ArgumentCaptor.forClass(OcrMessage.class);
        verify(messagePublisher).publishOcrMessage(ocrCaptor.capture());
        assertThat(ocrCaptor.getValue().getDocumentId()).isEqualTo(documentId);

        // 2. Simuliere GenAI-Worker-Aufruf gegen den gemockten Ollama-HTTP-Server
        RestClient restClient = RestClient.builder().baseUrl(mockOllamaBaseUrl).build();
        String ollamaJson = restClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", "qwen2.5:1.5b",
                        "prompt", "Fasse zusammen: Projektstatus CapaDoc...",
                        "stream", false
                ))
                .retrieve()
                .body(String.class);

        JsonNode ollamaNode = objectMapper.readTree(ollamaJson);
        String generatedSummary = ollamaNode.get("response").asText();

        // 3. Rückkanal: Summary-Ergebnis über den Listener im REST-Server verarbeiten
        DocumentSummaryListener listener = new DocumentSummaryListener(documentSummaryService, messagePublisher);
        listener.handleSummaryResult(SummaryResultMessage.builder()
                .documentId(documentId)
                .summary(generatedSummary)
                .build());

        // 4. Prüfen, dass Summary in der DB persistiert wurde
        DocumentEntity persisted = documentRepository.findById(documentId).orElseThrow();
        assertThat(persisted.getSummary()).isEqualTo(generatedSummary);

        // 5. Prüfen, dass GET /documents/{id} das Feld summary und summaryStatus=DONE liefert
        mockMvc.perform(get("/documents/{id}", documentId)
                        .with(oidcLogin().idToken(t -> t.subject("integration-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(documentId))
                .andExpect(jsonPath("$.summary").value(generatedSummary))
                .andExpect(jsonPath("$.summaryStatus").value("DONE"));
    }
}
