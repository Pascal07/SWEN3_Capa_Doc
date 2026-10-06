package at.capadocapi.controllerTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void me_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/user"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_returnsUserData_whenAuthenticatedViaOidc() throws Exception {
        mockMvc.perform(get("/user")
                        .with(oidcLogin()
                                .idToken(token -> token
                                        .subject("sub-google-456")
                                        .claim("name", "Erika Mustermann")
                                        .claim("email", "erika@example.com")
                                        .claim("picture", "https://example.com/avatar.png"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value("sub-google-456"))
                .andExpect(jsonPath("$.name").value("Erika Mustermann"))
                .andExpect(jsonPath("$.email").value("erika@example.com"))
                .andExpect(jsonPath("$.picture").value("https://example.com/avatar.png"));
    }

    @Test
    void me_worksUnderApiUserPrefixToo() throws Exception {
        mockMvc.perform(get("/api/user")
                        .with(oidcLogin()
                                .idToken(token -> token
                                        .subject("sub-google-456")
                                        .claim("name", "Erika Mustermann")
                                        .claim("email", "erika@example.com")
                                        .claim("picture", "https://example.com/avatar.png"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value("sub-google-456"));
    }
}
