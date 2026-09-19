package at.capadocapi.controller;

import at.capadocapi.model.dto.PingResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/ping")
public class PingController {

    @GetMapping
    public ResponseEntity<PingResponse> ping() {
        PingResponse response = new PingResponse("OK", "CapaDocAPI is running", LocalDateTime.now());
        return ResponseEntity.ok(response);
    }
}
