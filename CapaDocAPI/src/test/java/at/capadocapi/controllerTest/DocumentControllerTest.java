package at.capadocapi.controllerTest;

import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.dto.DocumentRequestDTO;
import at.capadocapi.service.Interfaces.DocumentService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
}
