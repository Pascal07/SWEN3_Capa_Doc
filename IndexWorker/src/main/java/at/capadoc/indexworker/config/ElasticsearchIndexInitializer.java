package at.capadoc.indexworker.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "elasticsearch.auto-create-index", havingValue = "true", matchIfMissing = true)
public class ElasticsearchIndexInitializer implements ApplicationRunner {

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        ensureIndexExists();
    }

    public void ensureIndexExists() {
        String indexName = properties.getIndexName();
        int maxRetries = Math.max(1, properties.getMaxRetries());
        long delayMs = Math.max(0L, properties.getRetryDelayMs());

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                boolean exists = elasticsearchClient.indices()
                        .exists(ExistsRequest.of(e -> e.index(indexName)))
                        .value();
                if (exists) {
                    log.info("Elasticsearch index '{}' already exists.", indexName);
                    return;
                }
                createIndexIdempotent(indexName);
                log.info("Elasticsearch index '{}' created successfully with mappings.", indexName);
                return;
            } catch (Exception ex) {
                log.warn("Attempt {}/{} to initialize Elasticsearch index '{}' failed: {}",
                        attempt, maxRetries, indexName, ex.getMessage());
                if (attempt == maxRetries) {
                    throw new IllegalStateException(
                            "Could not initialize Elasticsearch index '" + indexName + "' after " + maxRetries + " attempts", ex);
                }
                sleep(delayMs);
            }
        }
    }

    private void createIndexIdempotent(String indexName) throws Exception {
        try {
            CreateIndexRequest createRequest = CreateIndexRequest.of(c -> c
                    .index(indexName)
                    .mappings(m -> m
                            .properties("documentId", Property.of(p -> p.keyword(k -> k)))
                            .properties("filename", Property.of(p -> p.text(t -> t
                                    .fields("keyword", Property.of(fp -> fp.keyword(k -> k.ignoreAbove(256)))))))
                            .properties("content", Property.of(p -> p.text(t -> t)))
                            .properties("uploadedAt", Property.of(p -> p.date(d -> d)))
                    )
            );
            elasticsearchClient.indices().create(createRequest);
        } catch (ElasticsearchException ex) {
            if (ex.status() == 400 && ex.getMessage() != null
                    && ex.getMessage().contains("resource_already_exists_exception")) {
                log.info("Elasticsearch index '{}' was created concurrently.", indexName);
                return;
            }
            throw ex;
        }
    }

    private void sleep(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry Elasticsearch index initialization", ie);
        }
    }
}
