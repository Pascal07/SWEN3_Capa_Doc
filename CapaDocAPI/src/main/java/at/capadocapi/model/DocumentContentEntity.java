package at.capadocapi.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "document_contents")
@Getter
@Setter
@NoArgsConstructor
public class DocumentContentEntity {

    @Id
    private Long documentId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private DocumentEntity document;

    @Column(nullable = false)
    private byte[] content;
}
