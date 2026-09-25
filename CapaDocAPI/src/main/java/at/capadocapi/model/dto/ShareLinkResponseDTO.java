package at.capadocapi.model.dto;

import at.capadocapi.model.ShareLinkEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareLinkResponseDTO {

    private Long id;
    private String shortCode;
    private LocalDateTime expiryDate;
    private LocalDateTime createdAt;

}
