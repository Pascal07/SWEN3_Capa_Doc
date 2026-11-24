package capa.genaiworker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient ollamaRestClient(GenAiProperties properties) {
        GenAiProperties.Ollama ollama = properties.getOllama();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(Math.max(1, ollama.getConnectTimeoutSeconds())));
        requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(5, ollama.getReadTimeoutSeconds())));

        return RestClient.builder()
                .baseUrl(ollama.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
