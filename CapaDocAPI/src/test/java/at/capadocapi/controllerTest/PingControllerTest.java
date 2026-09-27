package at.capadocapi.controllerTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-context MockMvc test for PingController.
 * No dependencies to mock — this endpoint is self-contained.
 *
 * Assumes PingResponse(status, message, timestamp) maps to JSON fields
 * "status", "message" and "timestamp" (standard Lombok/record field naming).
 * Adjust the jsonPath field names if PingResponse uses different names.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ping_returns200_withStatusOkAndMessage() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.message").value("CapaDocAPI is running"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
