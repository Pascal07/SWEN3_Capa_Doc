package at.capadocapi.controllerTest;

import at.capadocapi.model.dto.DocumentResponseDTO;
import at.capadocapi.model.dto.ShareLinkRequestDTO;
import at.capadocapi.model.dto.ShareLinkResponseDTO;
import at.capadocapi.service.ShareLinkServiceImpl;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-context MockMvc test for ShareLinkController.
 * ShareLinkServiceImpl is mocked so no real persistence/DB is required.
 *
 * Adjust @SpringBootTest(classes = ...) if the application context
 * cannot be picked up automatically (e.g. main class in a different
 * package than at.capadocapi).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShareLinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ShareLinkServiceImpl shareLinkService;

    private ShareLinkRequestDTO validRequest;
    private ShareLinkResponseDTO sampleResponse;
    private DocumentResponseDTO sampleDocument;

    @BeforeEach
    void setUp() {
        validRequest = new ShareLinkRequestDTO();
        validRequest.setPassword("secret123");
        validRequest.setExpiryDate(LocalDateTime.now().plusDays(7));

        sampleResponse = ShareLinkResponseDTO.builder()
                .id(1L)
                .shortCode("abc123")
                .expiryDate(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now())
                .build();

        sampleDocument = DocumentResponseDTO.builder()
                .id(1L)
                .filename("report.pdf")
                .contentType("application/pdf")
                .sizeBytes(1024L)
                .uploadedAt(LocalDateTime.now())
                .build();
    }

    // ---------- POST /api/links/create/{documentId} ----------

    @Test
    void createShareLink_returns201_withValidBody() throws Exception {
        when(shareLinkService.createShareLink(eq(1L), any(ShareLinkRequestDTO.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/links/create/{documentId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.shortCode").value("abc123"));

        verify(shareLinkService, times(1)).createShareLink(eq(1L), any(ShareLinkRequestDTO.class));
    }

    @Test
    void createShareLink_returns400_whenPasswordBlank() throws Exception {
        ShareLinkRequestDTO invalid = new ShareLinkRequestDTO();
        invalid.setPassword(""); // blank -> violates @NotBlank
        invalid.setExpiryDate(LocalDateTime.now().plusDays(7));

        mockMvc.perform(post("/api/links/create/{documentId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verify(shareLinkService, never()).createShareLink(anyLong(), any());
    }

    @Test
    void createShareLink_returns400_whenExpiryDateMissing() throws Exception {
        ShareLinkRequestDTO invalid = new ShareLinkRequestDTO();
        invalid.setPassword("secret123");
        invalid.setExpiryDate(null); // violates @NotNull

        mockMvc.perform(post("/api/links/create/{documentId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verify(shareLinkService, never()).createShareLink(anyLong(), any());
    }

    @Test
    void createShareLink_returns400_whenExpiryDateInPast() throws Exception {
        ShareLinkRequestDTO invalid = new ShareLinkRequestDTO();
        invalid.setPassword("secret123");
        invalid.setExpiryDate(LocalDateTime.now().minusDays(1)); // violates @Future

        mockMvc.perform(post("/api/links/create/{documentId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verify(shareLinkService, never()).createShareLink(anyLong(), any());
    }

    // ---------- GET /api/links/{shortCode} ----------

    @Test
    void resolveLink_returns200_withDocument_whenFound() throws Exception {
        when(shareLinkService.resolveLink("abc123", "secret123"))
                .thenReturn(List.of(sampleDocument));

        mockMvc.perform(get("/api/links/{shortCode}", "abc123")
                        .param("password", "secret123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.filename").value("report.pdf"));

        verify(shareLinkService, times(1)).resolveLink("abc123", "secret123");
    }

    @Test
    void resolveLink_returns404_whenNoDocumentsFound() throws Exception {
        when(shareLinkService.resolveLink("abc123", "wrongpass"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/links/{shortCode}", "abc123")
                        .param("password", "wrongpass"))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolveLink_returns400_whenPasswordParamMissing() throws Exception {
        mockMvc.perform(get("/api/links/{shortCode}", "abc123"))
                .andExpect(status().isBadRequest());

        verify(shareLinkService, never()).resolveLink(anyString(), anyString());
    }

    // ---------- DELETE /api/links/{id} ----------

    @Test
    void deleteShareLink_returns204() throws Exception {
        doNothing().when(shareLinkService).deleteShareLink(1L);

        mockMvc.perform(delete("/api/links/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(shareLinkService, times(1)).deleteShareLink(1L);
    }
}
