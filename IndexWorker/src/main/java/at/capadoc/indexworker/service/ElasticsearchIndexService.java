package at.capadoc.indexworker.service;

import at.capadoc.indexworker.config.ElasticsearchProperties;
import at.capadoc.indexworker.exception.IndexingPermanentException;
import at.capadoc.indexworker.exception.IndexingTransientException;
import at.capadoc.indexworker.model.IndexedDocument;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.transport.TransportException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchIndexService implements DocumentIndexService {

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchProperties properties;

    @Override
    public void indexDocument(Long documentId, String filename, String content, LocalDateTime uploadedAt) {
        if (documentId == null) {
            throw new IndexingPermanentException("documentId must not be null");
        }
        if (filename == null || filename.isBlank()) {
            throw new IndexingPermanentException("filename must not be blank for documentId=" + documentId);
        }

        LocalDateTime effectiveUploadedAt = uploadedAt != null ? uploadedAt : LocalDateTime.now();
        String uploadedAtIso = effectiveUploadedAt.atOffset(ZoneOffset.UTC).toInstant().toString();
        String docIdStr = String.valueOf(documentId);

        IndexedDocument document = IndexedDocument.builder()
                .documentId(docIdStr)
                .filename(filename)
                .content(content != null ? content : "")
                .uploadedAt(uploadedAtIso)
                .build();

        IndexRequest<IndexedDocument> request = IndexRequest.of(i -> i
                .index(properties.getIndexName())
                .id(docIdStr)
                .document(document)
        );

        try {
            IndexResponse response = elasticsearchClient.index(request);
            log.info("Indexed documentId={} in Elasticsearch index='{}' (result={})",
                    documentId, properties.getIndexName(), response != null ? response.result() : "OK");
        } catch (ElasticsearchException ex) {
            if (ex.status() >= 400 && ex.status() < 500 && ex.status() != 408 && ex.status() != 429) {
                throw new IndexingPermanentException(
                        "Permanent Elasticsearch error (" + ex.status() + ") indexing documentId=" + documentId, ex);
            }
            throw new IndexingTransientException(
                    "Transient Elasticsearch error (" + ex.status() + ") indexing documentId=" + documentId, ex);
        } catch (IOException ex) {
            throw new IndexingTransientException(
                    "Elasticsearch connection/timeout error while indexing documentId=" + documentId, ex);
        } catch (RuntimeException ex) {
            throw new IndexingTransientException(
                    "Unexpected error while indexing documentId=" + documentId, ex);
        }
    }

    @Override
    public void deleteDocument(Long documentId) {
        if (documentId == null) {
            throw new IndexingPermanentException("documentId must not be null for delete");
        }

        String docIdStr = String.valueOf(documentId);
        DeleteRequest request = DeleteRequest.of(d -> d
                .index(properties.getIndexName())
                .id(docIdStr)
        );

        try {
            DeleteResponse response = elasticsearchClient.delete(request);
            if (response != null && response.result() == Result.NotFound) {
                log.info("Document documentId={} was not found in Elasticsearch index='{}' during delete (idempotent).",
                        documentId, properties.getIndexName());
            } else {
                log.info("Deleted documentId={} from Elasticsearch index='{}' (result={})",
                        documentId, properties.getIndexName(), response != null ? response.result() : "Deleted");
            }
        } catch (ElasticsearchException ex) {
            if (ex.status() == 404) {
                log.info("Document documentId={} already absent in Elasticsearch index='{}' (HTTP 404, idempotent).",
                        documentId, properties.getIndexName());
                return;
            }
            if (ex.status() >= 400 && ex.status() < 500 && ex.status() != 408 && ex.status() != 429) {
                throw new IndexingPermanentException(
                        "Permanent Elasticsearch error (" + ex.status() + ") deleting documentId=" + documentId, ex);
            }
            throw new IndexingTransientException(
                    "Transient Elasticsearch error (" + ex.status() + ") deleting documentId=" + documentId, ex);
        } catch (IOException ex) {
            throw new IndexingTransientException(
                    "Elasticsearch connection/timeout error while deleting documentId=" + documentId, ex);
        } catch (RuntimeException ex) {
            throw new IndexingTransientException(
                    "Unexpected error while deleting documentId=" + documentId, ex);
        }
    }
}
