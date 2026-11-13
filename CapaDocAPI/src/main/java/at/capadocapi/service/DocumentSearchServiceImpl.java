package at.capadocapi.service;

import at.capadocapi.config.ElasticsearchProperties;
import at.capadocapi.exception.SearchServiceUnavailableException;
import at.capadocapi.mapper.DocumentMapper;
import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.dto.DocumentResponseDTO;
import at.capadocapi.model.dto.IndexedDocument;
import at.capadocapi.repository.DocumentRepository;
import at.capadocapi.service.Interfaces.DocumentSearchService;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.util.NamedValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentSearchServiceImpl implements DocumentSearchService {

    public static final int MAX_PAGE_SIZE = 100;

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchProperties properties;
    private final DocumentRepository documentRepository;
    private final DocumentMapper documentMapper;

    @Override
    public List<DocumentResponseDTO> searchDocuments(String query, int page, int size, String ownerSub) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query 'q' must not be blank");
        }
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be less than 0");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        String trimmedQuery = query.trim();
        String wildcardValue = "*" + escapeWildcard(trimmedQuery) + "*";

        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(properties.getIndexName())
                .from(page * size)
                .size(size)
                .query(q -> q
                        .bool(b -> b
                                .should(sh -> sh.multiMatch(mm -> mm
                                        .query(trimmedQuery)
                                        .fields("filename^2.0", "content")
                                        .fuzziness("AUTO")
                                ))
                                .should(sh -> sh.wildcard(w -> w
                                        .field("filename.keyword")
                                        .caseInsensitive(true)
                                        .value(wildcardValue)
                                ))
                                .minimumShouldMatch("1")
                        )
                )
                .highlight(h -> h
                        .preTags("")
                        .postTags("")
                        .fields(
                                NamedValue.of("content", HighlightField.of(hf -> hf.fragmentSize(160).numberOfFragments(1))),
                                NamedValue.of("filename", HighlightField.of(hf -> hf.numberOfFragments(1)))
                        )
                )
        );

        final SearchResponse<IndexedDocument> response;
        try {
            response = elasticsearchClient.search(searchRequest, IndexedDocument.class);
        } catch (ElasticsearchException | IOException ex) {
            log.error("Elasticsearch search failed for query length={}: {}", trimmedQuery.length(), ex.getMessage(), ex);
            throw new SearchServiceUnavailableException("Elasticsearch is currently unavailable", ex);
        } catch (RuntimeException ex) {
            log.error("Unexpected error querying Elasticsearch: {}", ex.getMessage(), ex);
            throw new SearchServiceUnavailableException("Elasticsearch search failed", ex);
        }

        if (response == null || response.hits() == null || response.hits().hits() == null
                || response.hits().hits().isEmpty()) {
            return List.of();
        }

        Map<Long, String> orderedHitsWithSnippets = new LinkedHashMap<>();
        for (Hit<IndexedDocument> hit : response.hits().hits()) {
            Long docId = extractDocumentId(hit);
            if (docId != null && !orderedHitsWithSnippets.containsKey(docId)) {
                orderedHitsWithSnippets.put(docId, extractSnippet(hit));
            }
        }

        if (orderedHitsWithSnippets.isEmpty()) {
            return List.of();
        }

        final Map<Long, DocumentEntity> dbDocumentsById;
        try {
            List<DocumentEntity> entities = documentRepository.findAllById(orderedHitsWithSnippets.keySet());
            dbDocumentsById = entities.stream()
                    .filter(doc -> doc != null && doc.getId() != null)
                    .collect(Collectors.toMap(DocumentEntity::getId, Function.identity(), (a, b) -> a));
        } catch (DataAccessException ex) {
            throw ex;
        }

        List<DocumentResponseDTO> results = new ArrayList<>();
        for (Map.Entry<Long, String> entry : orderedHitsWithSnippets.entrySet()) {
            Long docId = entry.getKey();
            DocumentEntity entity = dbDocumentsById.get(docId);
            if (entity == null) {
                log.debug("Skipping Elasticsearch hit for documentId={} because it no longer exists in DB.", docId);
                continue;
            }
            if (ownerSub != null && entity.getOwnerSub() != null && !entity.getOwnerSub().equals(ownerSub)) {
                continue;
            }
            DocumentResponseDTO dto = documentMapper.toResponseDto(entity);
            if (dto != null && entry.getValue() != null && !entry.getValue().isBlank()) {
                dto.setSnippet(entry.getValue());
            }
            results.add(dto);
        }

        return results;
    }

    private Long extractDocumentId(Hit<IndexedDocument> hit) {
        if (hit == null) {
            return null;
        }
        String rawId = hit.id();
        if ((rawId == null || rawId.isBlank()) && hit.source() != null) {
            rawId = hit.source().getDocumentId();
        }
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(rawId.trim());
        } catch (NumberFormatException ex) {
            log.warn("Ignoring non-numeric Elasticsearch hit id='{}'", rawId);
            return null;
        }
    }

    private String extractSnippet(Hit<IndexedDocument> hit) {
        if (hit.highlight() != null && !hit.highlight().isEmpty()) {
            List<String> contentHighlights = hit.highlight().get("content");
            if (contentHighlights != null && !contentHighlights.isEmpty() && !contentHighlights.getFirst().isBlank()) {
                return contentHighlights.getFirst().trim();
            }
            List<String> filenameHighlights = hit.highlight().get("filename");
            if (filenameHighlights != null && !filenameHighlights.isEmpty() && !filenameHighlights.getFirst().isBlank()) {
                return filenameHighlights.getFirst().trim();
            }
        }
        if (hit.source() != null && hit.source().getContent() != null && !hit.source().getContent().isBlank()) {
            String content = hit.source().getContent().trim().replaceAll("\\s+", " ");
            return content.length() > 160 ? content.substring(0, 160) + "..." : content;
        }
        return null;
    }

    private String escapeWildcard(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("?", "\\?");
    }
}
