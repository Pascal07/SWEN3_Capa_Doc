package at.capadocapi.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class UserController {

    @GetMapping({"/user", "/api/user"})
    public Map<String, Object> me(@AuthenticationPrincipal OidcUser user) {
        if (user == null) {
            return Map.of();
        }
        return Map.of(
                "sub", user.getSubject() != null ? user.getSubject() : "",
                "name", user.getFullName() != null ? user.getFullName() : "",
                "email", user.getEmail() != null ? user.getEmail() : "",
                "picture", user.getPicture() != null ? user.getPicture() : ""
        );
    }
}
