package at.capadocapi.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ExceptionThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void documentNotFound_returnsNotFoundAndErrorDto() throws Exception {
        mockMvc.perform(get("/test/document-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Document not found with id: 12"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void queuePublishFailure_returnsServiceUnavailableAndGenericMessage() throws Exception {
        mockMvc.perform(get("/test/queue-failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message")
                        .value("The messaging service is currently unavailable. Please try again later."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void illegalArgument_returnsBadRequestAndErrorDto() throws Exception {
        mockMvc.perform(get("/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("invalid document"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @RestController
    static class ExceptionThrowingController {

        @GetMapping("/test/document-not-found")
        void documentNotFound() {
            throw new DocumentNotFoundException(12L);
        }

        @GetMapping("/test/queue-failure")
        void queueFailure() {
            throw new QueuePublishException("broker unavailable", new AmqpException("broker unavailable"));
        }

        @GetMapping("/test/bad-request")
        void badRequest() {
            throw new IllegalArgumentException("invalid document");
        }
    }
}
