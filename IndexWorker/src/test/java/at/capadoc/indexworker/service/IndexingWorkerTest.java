package at.capadoc.indexworker.service;

import at.capadoc.indexworker.config.ElasticsearchIndexInitializer;
import at.capadoc.indexworker.config.ElasticsearchProperties;
import at.capadoc.indexworker.model.IndexedDocument;
import at.capadoc.indexworker.model.IndexingMessage;
import at.capadoc.indexworker.model.ProcessingStatus;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.ShardStatistics;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndexingWorkerTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    @Mock
    private DocumentStatusUpdater documentStatusUpdater;

    @Mock
    private IndexingDeadLetterPublisher deadLetterPublisher;

    private ElasticsearchProperties properties;
    private ElasticsearchIndexService indexService;
    private IndexingMessageListener listener;

    @BeforeEach
    void setUp() {
        properties = new ElasticsearchProperties();
        properties.setUrl("http://localhost:9200");
        properties.setIndexName("documents");
        properties.setIndexingMaxRetries(3);
        properties.setIndexingRetryBackoffMs(5L);
        properties.setMaxRetries(2);
        properties.setRetryDelayMs(5L);

        indexService = new ElasticsearchIndexService(elasticsearchClient, properties);
        listener = new IndexingMessageListener(
                indexService,
                documentStatusUpdater,
                deadLetterPublisher,
                properties
        );
    }

    @Test
    void indexDocument_success_indexesWithDocumentIdAndUpdatesStatusToDone() throws Exception {
        IndexingMessage message = IndexingMessage.builder()
                .documentId(42L)
                .storageKey("documents/42.pdf")
                .filename("HelloWorld.pdf")
                .extractedText("Hello World OCR content")
                .processedAt(LocalDateTime.of(2026, 11, 11, 12, 0))
                .build();

        when(documentStatusUpdater.getUploadedAt(42L))
                .thenReturn(Optional.of(LocalDateTime.of(2026, 11, 11, 11, 59)));
        when(elasticsearchClient.index(any(IndexRequest.class)))
                .thenReturn(mockIndexResponse("42", Result.Created));

        listener.handleIndexingMessage(message);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<IndexRequest<IndexedDocument>> captor = ArgumentCaptor.forClass(IndexRequest.class);
        verify(elasticsearchClient, times(1)).index(captor.capture());

        IndexRequest<IndexedDocument> request = captor.getValue();
        assertThat(request.index()).isEqualTo("documents");
        assertThat(request.id()).isEqualTo("42");
        assertThat(request.document()).isNotNull();
        assertThat(request.document().getDocumentId()).isEqualTo("42");
        assertThat(request.document().getFilename()).isEqualTo("HelloWorld.pdf");
        assertThat(request.document().getContent()).isEqualTo("Hello World OCR content");
        assertThat(request.document().getUploadedAt()).isEqualTo("2026-11-11T11:59:00Z");

        verify(documentStatusUpdater).updateIndexStatus(42L, ProcessingStatus.DONE);
        verify(deadLetterPublisher, never()).publishToDeadLetterQueue(any());
    }

    @Test
    void indexDocument_reindexingSameDocument_usesSameElasticsearchIdForIdempotency() throws Exception {
        IndexingMessage first = IndexingMessage.builder()
                .documentId(77L)
                .filename("report.pdf")
                .extractedText("Version 1")
                .uploadedAt(LocalDateTime.of(2026, 11, 11, 10, 0))
                .build();

        IndexingMessage second = IndexingMessage.builder()
                .documentId(77L)
                .filename("report.pdf")
                .extractedText("Version 2 updated")
                .uploadedAt(LocalDateTime.of(2026, 11, 11, 10, 5))
                .build();

        when(elasticsearchClient.index(any(IndexRequest.class)))
                .thenReturn(mockIndexResponse("77", Result.Created))
                .thenReturn(mockIndexResponse("77", Result.Updated));

        listener.handleIndexingMessage(first);
        listener.handleIndexingMessage(second);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<IndexRequest<IndexedDocument>> captor = ArgumentCaptor.forClass(IndexRequest.class);
        verify(elasticsearchClient, times(2)).index(captor.capture());

        assertThat(captor.getAllValues().get(0).id()).isEqualTo("77");
        assertThat(captor.getAllValues().get(0).document().getContent()).isEqualTo("Version 1");
        assertThat(captor.getAllValues().get(1).id()).isEqualTo("77");
        assertThat(captor.getAllValues().get(1).document().getContent()).isEqualTo("Version 2 updated");

        verify(documentStatusUpdater, times(2)).updateIndexStatus(77L, ProcessingStatus.DONE);
    }

    @Test
    void deleteDocument_existingEntry_deletesFromElasticsearch() throws Exception {
        IndexingMessage deleteMsg = IndexingMessage.deleteMessage(15L);

        when(elasticsearchClient.delete(any(DeleteRequest.class)))
                .thenReturn(mockDeleteResponse("15", Result.Deleted));

        listener.handleIndexingMessage(deleteMsg);

        ArgumentCaptor<DeleteRequest> captor = ArgumentCaptor.forClass(DeleteRequest.class);
        verify(elasticsearchClient, times(1)).delete(captor.capture());
        assertThat(captor.getValue().index()).isEqualTo("documents");
        assertThat(captor.getValue().id()).isEqualTo("15");
        verify(deadLetterPublisher, never()).publishToDeadLetterQueue(any());
    }

    @Test
    void deleteDocument_missingEntry_isIdempotentAndDoesNotFail() throws Exception {
        IndexingMessage deleteMsg = IndexingMessage.deleteMessage(999L);

        when(elasticsearchClient.delete(any(DeleteRequest.class)))
                .thenReturn(mockDeleteResponse("999", Result.NotFound));

        listener.handleIndexingMessage(deleteMsg);

        verify(elasticsearchClient, times(1)).delete(any(DeleteRequest.class));
        verify(deadLetterPublisher, never()).publishToDeadLetterQueue(any());
        verify(documentStatusUpdater, never()).updateIndexStatus(anyLong(), any());
    }

    @Test
    void deleteDocument_whenElasticsearchReturns404Exception_isIdempotent() throws Exception {
        ErrorResponse errorResponse = ErrorResponse.of(e -> e
                .status(404)
                .error(ErrorCause.of(c -> c.type("not_found").reason("document missing")))
        );
        when(elasticsearchClient.delete(any(DeleteRequest.class)))
                .thenThrow(new ElasticsearchException("delete", errorResponse));

        listener.handleIndexingMessage(IndexingMessage.deleteMessage(888L));

        verify(elasticsearchClient, times(1)).delete(any(DeleteRequest.class));
        verify(deadLetterPublisher, never()).publishToDeadLetterQueue(any());
    }

    @Test
    void indexDocument_whenElasticsearchUnreachable_retriesAndMarksFailedAndPublishesToDlq() throws Exception {
        IndexingMessage message = IndexingMessage.builder()
                .documentId(55L)
                .filename("unreachable.pdf")
                .extractedText("Some content")
                .uploadedAt(LocalDateTime.now())
                .build();

        when(elasticsearchClient.index(any(IndexRequest.class)))
                .thenThrow(new IOException("Connection refused: elasticsearch:9200"));

        listener.handleIndexingMessage(message);

        verify(elasticsearchClient, times(3)).index(any(IndexRequest.class));
        verify(documentStatusUpdater).updateIndexStatus(55L, ProcessingStatus.FAILED);
        verify(deadLetterPublisher).publishToDeadLetterQueue(message);
    }

    @Test
    void indexDocument_whenInvalidMessageWithoutFilename_doesNotRetryAndMarksFailed() throws Exception {
        IndexingMessage invalidMsg = IndexingMessage.builder()
                .documentId(66L)
                .filename("   ")
                .extractedText("Content without filename")
                .build();

        listener.handleIndexingMessage(invalidMsg);

        verify(elasticsearchClient, never()).index(any(IndexRequest.class));
        verify(documentStatusUpdater).updateIndexStatus(66L, ProcessingStatus.FAILED);
        verify(deadLetterPublisher, never()).publishToDeadLetterQueue(any());
    }

    @Test
    void indexInitializer_createsIndexWhenMissing() throws Exception {
        when(elasticsearchClient.indices()).thenReturn(indicesClient);
        when(indicesClient.exists(any(ExistsRequest.class)))
                .thenReturn(new BooleanResponse(false));
        when(indicesClient.create(any(CreateIndexRequest.class)))
                .thenReturn(CreateIndexResponse.of(b -> b.acknowledged(true).shardsAcknowledged(true).index("documents")));

        ElasticsearchIndexInitializer initializer = new ElasticsearchIndexInitializer(elasticsearchClient, properties);
        initializer.ensureIndexExists();

        ArgumentCaptor<CreateIndexRequest> captor = ArgumentCaptor.forClass(CreateIndexRequest.class);
        verify(indicesClient).create(captor.capture());
        CreateIndexRequest createReq = captor.getValue();
        assertThat(createReq.index()).isEqualTo("documents");
        assertThat(createReq.mappings()).isNotNull();
        assertThat(createReq.mappings().properties()).containsKeys("documentId", "filename", "content", "uploadedAt");
    }

    private IndexResponse mockIndexResponse(String id, Result result) {
        return IndexResponse.of(b -> b
                .index("documents")
                .id(id)
                .result(result)
                .seqNo(1L)
                .primaryTerm(1L)
                .version(1L)
                .shards(ShardStatistics.of(s -> s.total(1).successful(1).failed(0)))
        );
    }

    private DeleteResponse mockDeleteResponse(String id, Result result) {
        return DeleteResponse.of(b -> b
                .index("documents")
                .id(id)
                .result(result)
                .seqNo(1L)
                .primaryTerm(1L)
                .version(1L)
                .shards(ShardStatistics.of(s -> s.total(1).successful(1).failed(0)))
        );
    }
}
