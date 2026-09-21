package at.capadocapi.model.dto;

import at.capadocapi.model.ShareLinkEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Used as the response body for /links endpoints.
// Deliberately has NO password/passwordHash field - never send that back to the client.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareLinkResponseDTO {

    private Long id;
    private String shortCode;
    private LocalDateTime expiryDate;
    private LocalDateTime createdAt;

    public static ShareLinkResponseDTO from(ShareLinkEntity link) {
        return ShareLinkResponseDTO.builder()
                .id(link.getId())
                .shortCode(link.getShortCode())
                .expiryDate(link.getExpiryDate())
                .createdAt(link.getCreatedAt())
                .build();
    }
}
