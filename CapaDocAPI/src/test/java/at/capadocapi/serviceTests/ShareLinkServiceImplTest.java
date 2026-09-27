package at.capadocapi.serviceTests;

import at.capadocapi.mapper.DocumentMapper;
import at.capadocapi.mapper.ShareLinkMapper;
import at.capadocapi.repository.DocumentRepository;
import at.capadocapi.repository.ShareLinkRepository;
import at.capadocapi.service.ShareLinkServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import at.capadocapi.exception.InvalidShareLinkPasswordException;
import at.capadocapi.exception.ShareLinkExpiredException;
import at.capadocapi.exception.ShareLinkNotFoundException;
import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.ShareLinkEntity;
import at.capadocapi.model.dto.DocumentResponseDTO;
import at.capadocapi.model.dto.ShareLinkRequestDTO;
import at.capadocapi.model.dto.ShareLinkResponseDTO;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareLinkServiceImplTest {

    @Mock
    private ShareLinkRepository shareLinkRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private ShareLinkMapper shareLinkMapper;

    @InjectMocks
    private ShareLinkServiceImpl shareLinkService;

    // ==================== createShareLink() ====================

    @Test
    void createShareLink_documentExists_createsAndReturnsShareLink() {
        Long documentId = 1L;
        DocumentEntity document = document(1L);
        ShareLinkRequestDTO request = request();
        ShareLinkEntity link = new ShareLinkEntity();
        ShareLinkResponseDTO response = ShareLinkResponseDTO.builder().shortCode("response").build();

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(shareLinkMapper.toEntity(request)).thenReturn(link);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed-password");
        when(shareLinkRepository.existsByShortCode(anyString())).thenReturn(false);
        when(shareLinkRepository.save(link)).thenReturn(link);
        when(shareLinkMapper.toResponseDto(link)).thenReturn(response);

        ShareLinkResponseDTO result = shareLinkService.createShareLink(documentId, request);

        assertThat(result).isSameAs(response);
        assertThat(link.getShortCode()).hasSize(8);
        assertThat(link.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(document.getShareLinks()).containsExactly(link);
        verify(documentRepository).save(document);
    }

    @Test
    void createShareLink_documentNotFound_throwsIllegalArgumentException() {
        Long documentId = 99L;
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shareLinkService.createShareLink(documentId, request()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(documentId));

        verify(shareLinkRepository, never()).save(any());
    }

    @Test
    void createShareLink_hashesPasswordBeforeSaving() {
        DocumentEntity document = document(1L);
        ShareLinkRequestDTO request = request();
        ShareLinkEntity link = new ShareLinkEntity();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(shareLinkMapper.toEntity(request)).thenReturn(link);
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-password");
        when(shareLinkRepository.existsByShortCode(anyString())).thenReturn(false);
        when(shareLinkRepository.save(any(ShareLinkEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        shareLinkService.createShareLink(1L, request);

        ArgumentCaptor<ShareLinkEntity> captor = ArgumentCaptor.forClass(ShareLinkEntity.class);
        verify(shareLinkRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("encoded-password");
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo(request.getPassword());
        verify(passwordEncoder).encode(request.getPassword());
    }

    @Test
    void createShareLink_shortCodeCollision_generatesNewCodeUntilUnique() {
        DocumentEntity document = document(1L);
        ShareLinkRequestDTO request = request();
        ShareLinkEntity link = new ShareLinkEntity();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(shareLinkMapper.toEntity(request)).thenReturn(link);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(shareLinkRepository.existsByShortCode(anyString()))
                .thenReturn(true)
                .thenReturn(false);
        when(shareLinkRepository.save(any(ShareLinkEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        shareLinkService.createShareLink(1L, request);

        verify(shareLinkRepository, times(2)).existsByShortCode(anyString());
        verify(shareLinkRepository).save(link);
        assertThat(link.getShortCode()).hasSize(8);
    }

    @Test
    void createShareLink_addsLinkToDocumentAndSavesDocument() {
        DocumentEntity document = document(1L);
        ShareLinkRequestDTO request = request();
        ShareLinkEntity link = new ShareLinkEntity();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(shareLinkMapper.toEntity(request)).thenReturn(link);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(shareLinkRepository.existsByShortCode(anyString())).thenReturn(false);
        when(shareLinkRepository.save(link)).thenReturn(link);

        shareLinkService.createShareLink(1L, request);

        assertThat(document.getShareLinks()).contains(link);
        verify(documentRepository).save(document);
    }

    // ==================== resolveLink() ====================

    @Test
    void resolveLink_validShortCodeAndPassword_returnsDocumentList() {
        ShareLinkEntity link = linkWithExpiry(LocalDateTime.now().plusHours(1));
        DocumentEntity document = document(1L);
        link.setDocuments(new HashSet<>(List.of(document)));
        DocumentResponseDTO response = DocumentResponseDTO.builder().id(1L).build();
        when(shareLinkRepository.findByShortCode("abc123")).thenReturn(Optional.of(link));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);
        when(documentMapper.toResponseDto(document)).thenReturn(response);

        List<DocumentResponseDTO> result = shareLinkService.resolveLink("abc123", "password");

        assertThat(result).containsExactly(response);
    }

    @Test
    void resolveLink_shortCodeNotFound_throwsShareLinkNotFoundException() {
        when(shareLinkRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shareLinkService.resolveLink("missing", "password"))
                .isInstanceOf(ShareLinkNotFoundException.class);
    }

    @Test
    void resolveLink_linkExpired_throwsShareLinkExpiredException() {
        when(shareLinkRepository.findByShortCode("expired"))
                .thenReturn(Optional.of(linkWithExpiry(LocalDateTime.now().minusMinutes(1))));

        assertThatThrownBy(() -> shareLinkService.resolveLink("expired", "password"))
                .isInstanceOf(ShareLinkExpiredException.class);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void resolveLink_wrongPassword_throwsInvalidShareLinkPasswordException() {
        ShareLinkEntity link = linkWithExpiry(LocalDateTime.now().plusHours(1));
        when(shareLinkRepository.findByShortCode("abc123")).thenReturn(Optional.of(link));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> shareLinkService.resolveLink("abc123", "wrong"))
                .isInstanceOf(InvalidShareLinkPasswordException.class);
    }

    @Test
    void resolveLink_linkWithMultipleDocuments_returnsAllMappedDocuments() {
        ShareLinkEntity link = linkWithExpiry(LocalDateTime.now().plusHours(1));
        DocumentEntity first = document(1L);
        DocumentEntity second = document(2L);
        link.setDocuments(new HashSet<>(List.of(first, second)));
        DocumentResponseDTO firstResponse = DocumentResponseDTO.builder().id(1L).build();
        DocumentResponseDTO secondResponse = DocumentResponseDTO.builder().id(2L).build();
        when(shareLinkRepository.findByShortCode("abc123")).thenReturn(Optional.of(link));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);
        when(documentMapper.toResponseDto(first)).thenReturn(firstResponse);
        when(documentMapper.toResponseDto(second)).thenReturn(secondResponse);

        List<DocumentResponseDTO> result = shareLinkService.resolveLink("abc123", "password");

        assertThat(result).containsExactlyInAnyOrder(firstResponse, secondResponse);
    }

    // ==================== deleteShareLink() ====================

    @Test
    void deleteShareLink_linkExists_removesAssociationAndDeletesLink() {
        ShareLinkEntity link = new ShareLinkEntity();
        DocumentEntity document = document(1L);
        document.getShareLinks().add(link);
        link.setDocuments(new HashSet<>(List.of(document)));
        when(shareLinkRepository.findById(1L)).thenReturn(Optional.of(link));

        shareLinkService.deleteShareLink(1L);

        assertThat(document.getShareLinks()).doesNotContain(link);
        verify(documentRepository).save(document);
        verify(shareLinkRepository).delete(link);
    }

    @Test
    void deleteShareLink_linkNotFound_throwsIllegalArgumentException() {
        when(shareLinkRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shareLinkService.deleteShareLink(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");

        verify(shareLinkRepository, never()).delete(any());
    }

    @Test
    void deleteShareLink_linkWithMultipleDocuments_removesFromAllDocuments() {
        ShareLinkEntity link = new ShareLinkEntity();
        DocumentEntity first = document(1L);
        DocumentEntity second = document(2L);
        first.getShareLinks().add(link);
        second.getShareLinks().add(link);
        link.setDocuments(new HashSet<>(List.of(first, second)));
        when(shareLinkRepository.findById(1L)).thenReturn(Optional.of(link));

        shareLinkService.deleteShareLink(1L);

        assertThat(first.getShareLinks()).doesNotContain(link);
        assertThat(second.getShareLinks()).doesNotContain(link);
        verify(documentRepository, times(2)).save(any(DocumentEntity.class));
        verify(shareLinkRepository).delete(link);
    }

    @Test
    void deleteShareLink_linkWithNoDocuments_stillDeletesLink() {
        ShareLinkEntity link = new ShareLinkEntity();
        link.setDocuments(new HashSet<>());
        when(shareLinkRepository.findById(1L)).thenReturn(Optional.of(link));

        shareLinkService.deleteShareLink(1L);

        verify(documentRepository, never()).save(any());
        verify(shareLinkRepository).delete(link);
    }

    private ShareLinkRequestDTO request() {
        ShareLinkRequestDTO request = new ShareLinkRequestDTO();
        request.setPassword("plain-password");
        request.setExpiryDate(LocalDateTime.now().plusDays(1));
        return request;
    }

    private DocumentEntity document(Long id) {
        DocumentEntity document = new DocumentEntity();
        document.setId(id);
        return document;
    }

    private ShareLinkEntity linkWithExpiry(LocalDateTime expiryDate) {
        ShareLinkEntity link = new ShareLinkEntity();
        link.setShortCode("abc123");
        link.setPasswordHash("hash");
        link.setExpiryDate(expiryDate);
        link.setDocuments(new HashSet<>());
        return link;
    }
}