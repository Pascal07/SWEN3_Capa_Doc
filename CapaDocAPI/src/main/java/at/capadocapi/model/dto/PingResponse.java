package at.capadocapi.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PingResponse {
    private String status;
    private String message;
    private LocalDateTime timestamp;
}
