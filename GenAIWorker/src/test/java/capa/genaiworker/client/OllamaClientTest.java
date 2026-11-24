package capa.genaiworker.client;

import capa.genaiworker.config.GenAiProperties;
import capa.genaiworker.exception.OllamaResponseException;
import capa.genaiworker.exception.OllamaUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OllamaClientTest {

    private MockRestServiceServer mockServer;
    private OllamaClient ollamaClient;
    private GenAiProperties properties;

    @BeforeEach
    void setUp() {
        properties = new GenAiProperties();
        properties.getOllama().setBaseUrl("http://localhost:11434");
        properties.getOllama().setModel("qwen2.5:1.5b");
        properties.getOllama().setNumCtx(4096);

        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getOllama().getBaseUrl());
        mockServer = MockRestServiceServer.bindTo(builder).build();
        ollamaClient = new OllamaClient(builder.build(), properties);
    }

    @Test
    void generate_success_sendsExpectedPayloadAndReturnsTrimmedSummary() {
        mockServer.expect(requestTo("http://localhost:11434/api/generate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("qwen2.5:1.5b"))
                .andExpect(jsonPath("$.prompt").value("Summarize this"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.options.num_ctx").value(4096))
                .andExpect(jsonPath("$.options.num_predict").value(256))
                .andExpect(jsonPath("$.options.temperature").value(0.2))
                .andExpect(jsonPath("$.options.repeat_penalty").value(1.15))
                .andRespond(withSuccess(
                        "{\"model\":\"qwen2.5:1.5b\",\"response\":\"  Dies ist eine kompakte Zusammenfassung.  \",\"done\":true}",
                        MediaType.APPLICATION_JSON));

        String result = ollamaClient.generate(42L, "Summarize this");

        assertThat(result).isEqualTo("Dies ist eine kompakte Zusammenfassung.");
        mockServer.verify();
    }

    @Test
    void generate_timeout_throwsOllamaUnavailableException() {
        mockServer.expect(requestTo("http://localhost:11434/api/generate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> ollamaClient.generate(42L, "Summarize this"))
                .isInstanceOf(OllamaUnavailableException.class)
                .hasMessageContaining("42");

        mockServer.verify();
    }

    @Test
    void generate_http404ModelNotLoaded_throwsOllamaResponseException() {
        mockServer.expect(requestTo("http://localhost:11434/api/generate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"model 'qwen2.5:1.5b' not found\"}"));

        assertThatThrownBy(() -> ollamaClient.generate(42L, "Summarize this"))
                .isInstanceOf(OllamaResponseException.class)
                .hasMessageContaining("qwen2.5:1.5b")
                .hasMessageContaining("404");

        mockServer.verify();
    }

    @Test
    void generate_http400ClientError_throwsOllamaResponseException() {
        mockServer.expect(requestTo("http://localhost:11434/api/generate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid request\"}"));

        assertThatThrownBy(() -> ollamaClient.generate(42L, "Summarize this"))
                .isInstanceOf(OllamaResponseException.class)
                .hasMessageContaining("400");

        mockServer.verify();
    }

    @Test
    void generate_http500ServerError_throwsOllamaUnavailableException() {
        mockServer.expect(requestTo("http://localhost:11434/api/generate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"internal error\"}"));

        assertThatThrownBy(() -> ollamaClient.generate(42L, "Summarize this"))
                .isInstanceOf(OllamaUnavailableException.class)
                .hasMessageContaining("500");

        mockServer.verify();
    }

    @Test
    void generate_corruptJson_throwsOllamaResponseException() {
        mockServer.expect(requestTo("http://localhost:11434/api/generate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{not-valid-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> ollamaClient.generate(42L, "Summarize this"))
                .isInstanceOf(OllamaResponseException.class);

        mockServer.verify();
    }

    @Test
    void generate_emptyResponseField_throwsOllamaResponseException() {
        mockServer.expect(requestTo("http://localhost:11434/api/generate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"model\":\"qwen2.5:1.5b\",\"response\":\"   \",\"done\":true}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> ollamaClient.generate(42L, "Summarize this"))
                .isInstanceOf(OllamaResponseException.class)
                .hasMessageContaining("empty");

        mockServer.verify();
    }
}
