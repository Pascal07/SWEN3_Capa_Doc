package at.capadocapi.serviceTests;

import at.capadocapi.config.ElasticsearchProperties;
import at.capadocapi.exception.SearchServiceUnavailableException;
import at.capadocapi.mapper.DocumentMapper;
import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.ProcessingStatus;
import at.capadocapi.model.dto.DocumentResponseDTO;
import at.capadocapi.model.dto.IndexedDocument;
import at.capadocapi.repository.DocumentRepository;
import at.capadocapi.service.DocumentSearchServiceImpl;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ShardStatistics;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentSearchServiceImplTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private DocumentRepository documentRepository;

    private ElasticsearchProperties properties;
    private DocumentMapper documentMapper;
    private DocumentSearchServiceImpl searchService;

    @BeforeEach
    void setUp() {
        properties = new ElasticsearchProperties();
        properties.setIndexName("documents");
        documentMapper = Mappers.getMapper(DocumentMapper.class);
        searchService = new DocumentSearchServiceImpl(
                elasticsearchClient,
                properties,
                documentRepository,
                documentMapper
        );
    }

    @Test
    void searchDocuments_returnsHitsInRelevanceOrderWithMetadataAndSnippet() throws Exception {
        Hit<IndexedDocument> hit1 = buildHit("20", "HelloWorld.pdf", "Hello World OCR text",
                Map.of("content", List.of("Hello World OCR text")));
        Hit<IndexedDocument> hit2 = buildHit("10", "SecondDoc.pdf", "Another hello document",
                Map.of("content", List.of("Another hello document")));

        when(elasticsearchClient.search(any(SearchRequest.class), eq(IndexedDocument.class)))
                .thenReturn(buildSearchResponse(List.of(hit1, hit2)));

        DocumentEntity doc10 = DocumentEntity.builder()
                .id(10L)
                .filename("SecondDoc.pdf")
                .contentType("application/pdf")
                .sizeBytes(2048L)
                .uploadedAt(LocalDateTime.of(2026, 11, 10, 9, 0))
                .storageKey("documents/10.pdf")
                .ocrStatus(ProcessingStatus.DONE)
                .indexStatus(ProcessingStatus.DONE)
                .build();

        DocumentEntity doc20 = DocumentEntity.builder()
                .id(20L)
                .filename("HelloWorld.pdf")
                .contentType("application/pdf")
                .sizeBytes(4096L)
                .uploadedAt(LocalDateTime.of(2026, 11, 11, 12, 0))
                .storageKey("documents/20.pdf")
                .ocrStatus(ProcessingStatus.DONE)
                .indexStatus(ProcessingStatus.DONE)
                .build();

        // Repository returns in arbitrary order (10 then 20), service must preserve ES hit order (20 then 10)
        when(documentRepository.findAllById(any())).thenReturn(List.of(doc10, doc20));

        List<DocumentResponseDTO> results = searchService.searchDocuments("Hello", 0, 20, null);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getId()).isEqualTo(20L);
        assertThat(results.get(0).getFilename()).isEqualTo("HelloWorld.pdf");
        assertThat(results.get(0).getSnippet()).isEqualTo("Hello World OCR text");
        assertThat(results.get(1).getId()).isEqualTo(10L);
        assertThat(results.get(1).getFilename()).isEqualTo("SecondDoc.pdf");
        assertThat(results.get(1).getSnippet()).isEqualTo("Another hello document");
    }

    @Test
    void searchDocuments_skipsHitWithoutDatabaseEntry() throws Exception {
        Hit<IndexedDocument> orphanHit = buildHit("999", "Deleted.pdf", "Hello orphan", Map.of());
        Hit<IndexedDocument> validHit = buildHit("5", "Existing.pdf", "Hello existing", Map.of());

        when(elasticsearchClient.search(any(SearchRequest.class), eq(IndexedDocument.class)))
                .thenReturn(buildSearchResponse(List.of(orphanHit, validHit)));

        DocumentEntity doc5 = DocumentEntity.builder()
                .id(5L)
                .filename("Existing.pdf")
                .contentType("application/pdf")
                .sizeBytes(1024L)
                .uploadedAt(LocalDateTime.of(2026, 11, 11, 10, 0))
                .ocrStatus(ProcessingStatus.DONE)
                .indexStatus(ProcessingStatus.DONE)
                .build();

        when(documentRepository.findAllById(any())).thenReturn(List.of(doc5));

        List<DocumentResponseDTO> results = searchService.searchDocuments("Hello", 0, 20, null);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getId()).isEqualTo(5L);
        assertThat(results.getFirst().getFilename()).isEqualTo("Existing.pdf");
    }

    @Test
    void searchDocuments_noHits_returnsEmptyListWithoutDbQuery() throws Exception {
        when(elasticsearchClient.search(any(SearchRequest.class), eq(IndexedDocument.class)))
                .thenReturn(buildSearchResponse(List.of()));

        List<DocumentResponseDTO> results = searchService.searchDocuments("nonexistent", 0, 20, null);

        assertThat(results).isEmpty();
        verifyNoInteractions(documentRepository);
    }

    @Test
    void searchDocuments_blankQuery_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> searchService.searchDocuments("   ", 0, 20, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");

        verifyNoInteractions(elasticsearchClient, documentRepository);
    }

    @Test
    void searchDocuments_sizeExceedsMax_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> searchService.searchDocuments("Hello", 0, 101, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 100");

        verifyNoInteractions(elasticsearchClient, documentRepository);
    }

    @Test
    void searchDocuments_whenElasticsearchFails_throwsSearchServiceUnavailableException() throws Exception {
        when(elasticsearchClient.search(any(SearchRequest.class), eq(IndexedDocument.class)))
                .thenThrow(new IOException("Connection refused"));

        assertThatThrownBy(() -> searchService.searchDocuments("Hello", 0, 20, null))
                .isInstanceOf(SearchServiceUnavailableException.class);
    }

    @Test
    void searchDocuments_withSpecialCharacters_buildsSafeQueryWithoutFailure() throws Exception {
        when(elasticsearchClient.search(any(SearchRequest.class), eq(IndexedDocument.class)))
                .thenReturn(buildSearchResponse(List.of()));

        List<DocumentResponseDTO> results = searchService.searchDocuments("Hello+World*?\"()[]:/\\", 0, 20, null);

        assertThat(results).isEmpty();
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(elasticsearchClient).search(captor.capture(), eq(IndexedDocument.class));
        assertThat(captor.getValue().index()).containsExactly("documents");
    }

    private Hit<IndexedDocument> buildHit(String id, String filename, String content, Map<String, List<String>> highlights) {
        IndexedDocument doc = IndexedDocument.builder()
                .documentId(id)
                .filename(filename)
                .content(content)
                .uploadedAt("2026-11-11T12:00:00Z")
                .build();
        return Hit.of(h -> h
                .index("documents")
                .id(id)
                .score(1.5)
                .source(doc)
                .highlight(highlights)
        );
    }

    private SearchResponse<IndexedDocument> buildSearchResponse(List<Hit<IndexedDocument>> hits) {
        return SearchResponse.of(b -> b
                .took(3L)
                .timedOut(false)
                .shards(ShardStatistics.of(s -> s.total(1).successful(1).failed(0)))
                .hits(HitsMetadata.of(hm -> hm
                        .total(TotalHits.of(th -> th.value(hits.size()).relation(TotalHitsRelation.Eq)))
                        .hits(hits)
                ))
        );
    }
}
