package at.capadoc.indexworker.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(ElasticsearchProperties.class)
public class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    public Rest5Client elasticsearchRestClient(ElasticsearchProperties properties) {
        Rest5ClientBuilder builder = Rest5Client.builder(URI.create(properties.getUrl()));

        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            String rawCredentials = properties.getUsername() + ":"
                    + (properties.getPassword() != null ? properties.getPassword() : "");
            String encoded = Base64.getEncoder()
                    .encodeToString(rawCredentials.getBytes(StandardCharsets.UTF_8));
            builder.setDefaultHeaders(new Header[]{
                    new BasicHeader("Authorization", "Basic " + encoded)
            });
        }

        builder.setConnectionConfigCallback(connectionConfigBuilder -> connectionConfigBuilder
                .setConnectTimeout(Timeout.ofMilliseconds(5000))
                .setSocketTimeout(Timeout.ofMilliseconds(10000)));
        builder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                .setResponseTimeout(Timeout.ofMilliseconds(10000)));

        return builder.build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(Rest5Client elasticsearchRestClient) {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ElasticsearchTransport transport = new Rest5ClientTransport(
                elasticsearchRestClient,
                new JacksonJsonpMapper(objectMapper)
        );
        return new ElasticsearchClient(transport);
    }
}
