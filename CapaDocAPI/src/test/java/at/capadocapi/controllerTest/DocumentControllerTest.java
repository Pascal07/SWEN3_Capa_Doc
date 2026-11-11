package at.capadocapi.controllerTest;

import at.capadocapi.exception.SearchServiceUnavailableException;
import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.ProcessingStatus;
import at.capadocapi.model.dto.DocumentRequestDTO;
import at.capadocapi.model.dto.DocumentResponseDTO;
import at.capadocapi.service.Interfaces.DocumentSearchService;
import at.capadocapi.service.Interfaces.DocumentService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private DocumentSearchService documentSearchService;

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
        sampleEntity.setOwnerSub("user");
        sampleEntity.setStorageKey("documents/1.pdf");
        sampleEntity.setOcrStatus(ProcessingStatus.PENDING);
        sampleEntity.setIndexStatus(ProcessingStatus.PENDING);

        validRequest = new DocumentRequestDTO();
        validRequest.setFilename("report.pdf");
        validRequest.setContentType("application/pdf");
        validRequest.setSizeBytes(1024L);
    }

    // ---------- GET /user ----------

    @Test
    void me_returnsLoggedInUser_withOidcDetails() throws Exception {
        mockMvc.perform(get("/user")
                        .with(oidcLogin()
                                .idToken(token -> token
                                        .subject("google-sub-123")
                                        .claim("name", "Max Mustermann")
                                        .claim("email", "max@example.com")
                                        .claim("picture", "https://example.com/pic.jpg"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value("google-sub-123"))
                .andExpect(jsonPath("$.name").value("Max Mustermann"))
                .andExpect(jsonPath("$.email").value("max@example.com"))
                .andExpect(jsonPath("$.picture").value("https://example.com/pic.jpg"));
    }

    @Test
    void me_returnsUnauthorized_whenNotLoggedIn() throws Exception {
        mockMvc.perform(get("/user"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- GET /api/documents ----------

    @Test
    void getAllDocuments_returnsListOfDocuments() throws Exception {
        when(documentService.getDocumentsByOwner("user")).thenReturn(List.of(sampleEntity));

        mockMvc.perform(get("/api/documents").with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].filename").value("report.pdf"))
                .andExpect(jsonPath("$[0].contentType").value("application/pdf"))
                .andExpect(jsonPath("$[0].sizeBytes").value(1024));

        verify(documentService, times(1)).getDocumentsByOwner("user");
    }

    @Test
    void getAllDocuments_filtersByOwnerSub_whenOidcUserLoggedIn() throws Exception {
        when(documentService.getDocumentsByOwner("user-abc")).thenReturn(List.of(sampleEntity));

        mockMvc.perform(get("/api/documents")
                        .with(oidcLogin().idToken(token -> token.subject("user-abc"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1));

        verify(documentService, times(1)).getDocumentsByOwner("user-abc");
    }

    @Test
    void getAllDocuments_returnsEmptyList_whenNoneExist() throws Exception {
        when(documentService.getDocumentsByOwner("user")).thenReturn(List.of());

        mockMvc.perform(get("/api/documents").with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- GET /api/documents/{id} ----------

    @Test
    void getDocumentById_returnsDocument_whenFound() throws Exception {
        when(documentService.getDocumentById(1L)).thenReturn(Optional.of(sampleEntity));

        mockMvc.perform(get("/api/documents/{id}", 1L).with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.filename").value("report.pdf"));
    }

    @Test
    void getDocumentById_returns404_whenNotFound() throws Exception {
        when(documentService.getDocumentById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/documents/{id}", 99L).with(oidcLogin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDocumentById_returns403_whenForeignOwner() throws Exception {
        DocumentEntity foreignDoc = new DocumentEntity();
        foreignDoc.setId(1L);
        foreignDoc.setFilename("foreign.pdf");
        foreignDoc.setOwnerSub("other-user-sub");

        when(documentService.getDocumentById(1L)).thenReturn(Optional.of(foreignDoc));

        mockMvc.perform(get("/api/documents/{id}", 1L)
                        .with(oidcLogin().idToken(token -> token.subject("my-user-sub"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDocumentById_returns200_whenMatchingOwner() throws Exception {
        DocumentEntity myDoc = new DocumentEntity();
        myDoc.setId(1L);
        myDoc.setFilename("mine.pdf");
        myDoc.setOwnerSub("my-user-sub");

        when(documentService.getDocumentById(1L)).thenReturn(Optional.of(myDoc));

        mockMvc.perform(get("/api/documents/{id}", 1L)
                        .with(oidcLogin().idToken(token -> token.subject("my-user-sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("mine.pdf"));
    }

    // ---------- POST /api/documents ----------

    @Test
    void createDocument_returns201_withValidBody() throws Exception {
        when(documentService.createDocument(any(DocumentEntity.class))).thenReturn(sampleEntity);

        mockMvc.perform(post("/api/documents")
                        .with(csrf())
                        .with(oidcLogin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.filename").value("report.pdf"));

        verify(documentService, times(1)).createDocument(any(DocumentEntity.class));
    }

    @Test
    void createDocument_setsOwnerSubFromOidcUser() throws Exception {
        when(documentService.createDocument(any(DocumentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/documents")
                        .with(csrf())
                        .with(oidcLogin().idToken(token -> token.subject("my-sub-123")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerSub").value("my-sub-123"));

        ArgumentCaptor<DocumentEntity> captor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentService).createDocument(captor.capture());
        assertThat(captor.getValue().getOwnerSub()).isEqualTo("my-sub-123");
    }

    @Test
    void createDocument_returns400_whenFilenameBlank() throws Exception {
        DocumentRequestDTO invalid = new DocumentRequestDTO();
        invalid.setFilename(""); // blank -> violates @NotBlank
        invalid.setContentType("application/pdf");
        invalid.setSizeBytes(1024L);

        mockMvc.perform(post("/api/documents")
                        .with(csrf())
                        .with(oidcLogin())
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
                        .with(csrf())
                        .with(oidcLogin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verify(documentService, never()).createDocument(any());
    }

    // ---------- PUT /api/documents/{id} ----------

    @Test
    void updateDocument_returns200_whenFound() throws Exception {
        when(documentService.getDocumentById(1L)).thenReturn(Optional.of(sampleEntity));
        when(documentService.updateDocument(eq(1L), any(DocumentEntity.class))).thenReturn(sampleEntity);

        mockMvc.perform(put("/api/documents/{id}", 1L)
                        .with(csrf())
                        .with(oidcLogin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.filename").value("report.pdf"));
    }

    @Test
    void updateDocument_returns404_whenNotFound() throws Exception {
        when(documentService.getDocumentById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/documents/{id}", 99L)
                        .with(csrf())
                        .with(oidcLogin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateDocument_returns403_whenForeignOwner() throws Exception {
        DocumentEntity foreignDoc = new DocumentEntity();
        foreignDoc.setId(1L);
        foreignDoc.setOwnerSub("other-sub");
        when(documentService.getDocumentById(1L)).thenReturn(Optional.of(foreignDoc));

        mockMvc.perform(put("/api/documents/{id}", 1L)
                        .with(csrf())
                        .with(oidcLogin().idToken(t -> t.subject("my-sub")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isForbidden());

        verify(documentService, never()).updateDocument(anyLong(), any());
    }

    @Test
    void updateDocument_returns400_whenBodyInvalid() throws Exception {
        DocumentRequestDTO invalid = new DocumentRequestDTO();
        invalid.setFilename("report.pdf");
        invalid.setContentType(""); // blank -> violates @NotBlank
        invalid.setSizeBytes(1024L);

        mockMvc.perform(put("/api/documents/{id}", 1L)
                        .with(csrf())
                        .with(oidcLogin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verify(documentService, never()).updateDocument(anyLong(), any());
    }

    // ---------- DELETE /api/documents/{id} ----------

    @Test
    void deleteDocument_returns204() throws Exception {
        when(documentService.getDocumentById(1L)).thenReturn(Optional.of(sampleEntity));
        doNothing().when(documentService).deleteDocument(1L);

        mockMvc.perform(delete("/api/documents/{id}", 1L)
                        .with(csrf())
                        .with(oidcLogin()))
                .andExpect(status().isNoContent());

        verify(documentService, times(1)).deleteDocument(1L);
    }

    @Test
    void deleteDocument_returns403_whenForeignOwner() throws Exception {
        DocumentEntity foreignDoc = new DocumentEntity();
        foreignDoc.setId(1L);
        foreignDoc.setOwnerSub("other-sub");
        when(documentService.getDocumentById(1L)).thenReturn(Optional.of(foreignDoc));

        mockMvc.perform(delete("/api/documents/{id}", 1L)
                        .with(csrf())
                        .with(oidcLogin().idToken(t -> t.subject("my-sub"))))
                .andExpect(status().isForbidden());

        verify(documentService, never()).deleteDocument(anyLong());
    }

    @Test
    void deleteDocument_returns404_whenNotFound() throws Exception {
        when(documentService.getDocumentById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/documents/{id}", 999L)
                        .with(csrf())
                        .with(oidcLogin()))
                .andExpect(status().isNotFound());

        verify(documentService, never()).deleteDocument(anyLong());
    }

    // ---------- POST /api/documents (Multipart Upload) ----------

    @Test
    void uploadDocument_returns201_whenValidPdfMultipart() throws Exception {
        byte[] pdfBytes = "%PDF-1.7 sample pdf content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", pdfBytes);

        when(documentService.createDocument(any(DocumentEntity.class), any(MultipartFile.class)))
                .thenReturn(sampleEntity);

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .with(csrf())
                        .with(oidcLogin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.filename").value("report.pdf"))
                .andExpect(jsonPath("$.storageKey").value("documents/1.pdf"))
                .andExpect(jsonPath("$.ocrStatus").value("PENDING"))
                .andExpect(jsonPath("$.indexStatus").value("PENDING"));

        verify(documentService, times(1)).createDocument(any(DocumentEntity.class), any(MultipartFile.class));
    }

    @Test
    void uploadDocument_returns400_whenNotPdfContentType() throws Exception {
        MockMultipartFile nonPdf = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "%PDF-1.7 content".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/documents")
                        .file(nonPdf)
                        .with(csrf())
                        .with(oidcLogin()))
                .andExpect(status().isBadRequest());

        verify(documentService, never()).createDocument(any(), any());
    }

    @Test
    void uploadDocument_returns400_whenMissingPdfMagicBytes() throws Exception {
        MockMultipartFile fakePdf = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "Hello World not a PDF".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/documents")
                        .file(fakePdf)
                        .with(csrf())
                        .with(oidcLogin()))
                .andExpect(status().isBadRequest());

        verify(documentService, never()).createDocument(any(), any());
    }

    @Test
    void uploadDocument_returns400_whenFileEmpty() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/documents")
                        .file(emptyFile)
                        .with(csrf())
                        .with(oidcLogin()))
                .andExpect(status().isBadRequest());

        verify(documentService, never()).createDocument(any(), any());
    }

    // ---------- GET /api/documents/{id}/download ----------

    @Test
    void downloadDocument_returns200WithPdfBytes() throws Exception {
        byte[] pdfBytes = "%PDF-1.7 sample pdf content".getBytes(StandardCharsets.UTF_8);
        when(documentService.getDocumentById(1L)).thenReturn(Optional.of(sampleEntity));
        when(documentService.downloadDocument(1L)).thenReturn(pdfBytes);

        mockMvc.perform(get("/api/documents/{id}/download", 1L).with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(pdfBytes));

        verify(documentService, times(1)).downloadDocument(1L);
    }

    // ---------- GET /api/documents/search & /documents/search ----------

    @Test
    void searchDocuments_returnsMatchingDocumentsInRelevanceOrder() throws Exception {
        DocumentResponseDTO hit1 = DocumentResponseDTO.builder()
                .id(20L)
                .filename("HelloWorld.pdf")
                .contentType("application/pdf")
                .sizeBytes(4096L)
                .uploadedAt(LocalDateTime.of(2026, 11, 11, 12, 0))
                .ocrStatus(ProcessingStatus.DONE)
                .indexStatus(ProcessingStatus.DONE)
                .snippet("Hello World")
                .build();

        DocumentResponseDTO hit2 = DocumentResponseDTO.builder()
                .id(10L)
                .filename("OtherHello.pdf")
                .contentType("application/pdf")
                .sizeBytes(2048L)
                .uploadedAt(LocalDateTime.of(2026, 11, 10, 10, 0))
                .ocrStatus(ProcessingStatus.DONE)
                .indexStatus(ProcessingStatus.DONE)
                .snippet("Hello there")
                .build();

        when(documentSearchService.searchDocuments("Hello", 0, 20, "user"))
                .thenReturn(List.of(hit1, hit2));

        mockMvc.perform(get("/documents/search")
                        .param("q", "Hello")
                        .param("page", "0")
                        .param("size", "20")
                        .with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(20))
                .andExpect(jsonPath("$[0].filename").value("HelloWorld.pdf"))
                .andExpect(jsonPath("$[0].snippet").value("Hello World"))
                .andExpect(jsonPath("$[1].id").value(10))
                .andExpect(jsonPath("$[1].filename").value("OtherHello.pdf"));

        verify(documentSearchService).searchDocuments("Hello", 0, 20, "user");
    }

    @Test
    void searchDocuments_returnsEmptyList_whenNoMatches() throws Exception {
        when(documentSearchService.searchDocuments("NonExistentTerm", 0, 20, "user"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/documents/search")
                        .param("q", "NonExistentTerm")
                        .with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void searchDocuments_returns400_whenQueryBlankOrMissing() throws Exception {
        mockMvc.perform(get("/documents/search")
                        .param("q", "   ")
                        .with(oidcLogin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Search query 'q' must not be blank"));

        mockMvc.perform(get("/documents/search")
                        .with(oidcLogin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(documentSearchService);
    }

    @Test
    void searchDocuments_returns400_whenSizeTooLarge() throws Exception {
        mockMvc.perform(get("/documents/search")
                        .param("q", "Hello")
                        .param("size", "500")
                        .with(oidcLogin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(documentSearchService);
    }

    @Test
    void searchDocuments_returns503_whenElasticsearchUnavailable() throws Exception {
        when(documentSearchService.searchDocuments("Hello", 0, 20, "user"))
                .thenThrow(new SearchServiceUnavailableException("ES unreachable"));

        mockMvc.perform(get("/documents/search")
                        .param("q", "Hello")
                        .with(oidcLogin()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message")
                        .value("The search service is currently unavailable. Please try again later."));
    }
}
