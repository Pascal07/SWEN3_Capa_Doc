package at.capadocapi.controllerTest;

import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.dto.DocumentRequestDTO;
import at.capadocapi.service.Interfaces.DocumentService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-context MockMvc test for DocumentController.
 * DocumentService is mocked so no real persistence/DB is required;
 * DocumentMapper runs as a real Spring bean.
 *
 * Adjust @SpringBootTest(classes = ...) if the application context
 * cannot be picked up automatically (e.g. main class in a different
 * package than at.capadocapi).
 */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DocumentService documentService;

    private DocumentEntity sampleEntity;
    private DocumentRequestDTO validRequest;

    @BeforeEach
    void setUp() {
        sampleEntity = new DocumentEntity();
        sampleEntity.setId(1L);
        sampleEntity.setFilename("report.pdf");
        sampleEntity.setContentType("application/pdf");
        sampleEntity.setSizeBytes(1024L);
        sampleEntity.setUploadedAt(LocalDateTime.of(2026, 1, 1, 10, 0));

        validRequest = new DocumentRequestDTO();
        validRequest.setFilename("report.pdf");
        validRequest.setContentType("application/pdf");
        validRequest.setSizeBytes(1024L);
    }

    // ---------- GET /api/documents ----------

    @Test
    void getAllDocuments_returnsListOfDocuments() throws Exception {
        when(documentService.getAllDocuments()).thenReturn(List.of(sampleEntity));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].filename").value("report.pdf"))
                .andExpect(jsonPath("$[0].contentType").value("application/pdf"))
                .andExpect(jsonPath("$[0].sizeBytes").value(1024));

        verify(documentService, times(1)).getAllDocuments();
    }

    @Test
    void getAllDocuments_returnsEmptyList_whenNoneExist() throws Exception {
        when(documentService.getAllDocuments()).thenReturn(List.of());

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- GET /api/documents/{id} ----------

    @Test
    void getDocumentById_returnsDocument_whenFound() throws Exception {
        when(documentService.getDocumentById(1L)).thenReturn(Optional.of(sampleEntity));

        mockMvc.perform(get("/api/documents/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.filename").value("report.pdf"));
    }

    @Test
    void getDocumentById_returns404_whenNotFound() throws Exception {
        when(documentService.getDocumentById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/documents/{id}", 99L))
                .andExpect(status().isNotFound());
    }

    // ---------- POST /api/documents ----------

    @Test
    void createDocument_returns201_withValidBody() throws Exception {
        when(documentService.createDocument(any(DocumentEntity.class))).thenReturn(sampleEntity);

        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.filename").value("report.pdf"));

        verify(documentService, times(1)).createDocument(any(DocumentEntity.class));
    }

    @Test
    void createDocument_returns400_whenFilenameBlank() throws Exception {
        DocumentRequestDTO invalid = new DocumentRequestDTO();
        invalid.setFilename(""); // blank -> violates @NotBlank
        invalid.setContentType("application/pdf");
        invalid.setSizeBytes(1024L);

        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verify(documentService, never()).createDocument(any());
    }

    @Test
    void createDocument_returns400_whenSizeBytesNotPositive() throws Exception {
        DocumentRequestDTO invalid = new DocumentRequestDTO();
        invalid.setFilename("report.pdf");
        invalid.setContentType("application/pdf");
        invalid.setSizeBytes(0L); // violates @Positive

        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verify(documentService, never()).createDocument(any());
    }

    // ---------- PUT /api/documents/{id} ----------

    @Test
    void updateDocument_returns200_whenFound() throws Exception {
        when(documentService.updateDocument(eq(1L), any(DocumentEntity.class))).thenReturn(sampleEntity);

        mockMvc.perform(put("/api/documents/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.filename").value("report.pdf"));
    }

    @Test
    void updateDocument_returns404_whenNotFound() throws Exception {
        when(documentService.updateDocument(eq(99L), any(DocumentEntity.class)))
                .thenThrow(new IllegalArgumentException("Document not found"));

        mockMvc.perform(put("/api/documents/{id}", 99L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateDocument_returns400_whenBodyInvalid() throws Exception {
        DocumentRequestDTO invalid = new DocumentRequestDTO();
        invalid.setFilename("report.pdf");
        invalid.setContentType(""); // blank -> violates @NotBlank
        invalid.setSizeBytes(1024L);

        mockMvc.perform(put("/api/documents/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verify(documentService, never()).updateDocument(anyLong(), any());
    }

    // ---------- DELETE /api/documents/{id} ----------

    @Test
    void deleteDocument_returns204() throws Exception {
        doNothing().when(documentService).deleteDocument(1L);

        mockMvc.perform(delete("/api/documents/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(documentService, times(1)).deleteDocument(1L);
    }
}
