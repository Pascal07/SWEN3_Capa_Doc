package at.capadocapi.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

// Used for POST /documents and PUT /documents/{id}
// uploadedAt and id are set by the server, never accepted from the client.
@Data
public class DocumentRequestDTO {

    @NotBlank(message = "filename must not be blank")
    private String filename;

    @NotBlank(message = "contentType must not be blank")
    private String contentType;

    @Positive(message = "sizeBytes must be greater than 0")
    private Long sizeBytes;
}
