package at.capadocapi.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String filename;

    private String contentType;

    private Long sizeBytes;

    private LocalDateTime uploadedAt;

    @Column(name = "owner_sub")
    private String ownerSub;

    @Column(name = "storage_key")
    private String storageKey;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "ocr_status", columnDefinition = "varchar(32) default 'PENDING'")
    private ProcessingStatus ocrStatus = ProcessingStatus.PENDING;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "index_status", columnDefinition = "varchar(32) default 'PENDING'")
    private ProcessingStatus indexStatus = ProcessingStatus.PENDING;

    // m:n relation to ShareLink – owning side lives here
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(
            name = "document_share_links",
            joinColumns = @JoinColumn(name = "document_id"),
            inverseJoinColumns = @JoinColumn(name = "share_link_id")
    )
    private Set<ShareLinkEntity> shareLinks = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
        if (ocrStatus == null) {
            ocrStatus = ProcessingStatus.PENDING;
        }
        if (indexStatus == null) {
            indexStatus = ProcessingStatus.PENDING;
        }
    }
}
