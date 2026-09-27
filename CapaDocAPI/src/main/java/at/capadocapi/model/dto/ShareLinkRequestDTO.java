package at.capadocapi.model.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

// Used for POST /documents/{id}/links
@Data
public class ShareLinkRequestDTO {

    @NotBlank(message = "password must not be blank")
    private String password;

    @NotNull(message = "expiryDate must not be null")
    @Future(message = "expiryDate must be in the future")
    private LocalDateTime expiryDate;
}
