package at.capadocapi.serviceTests;

import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.ProcessingStatus;
import at.capadocapi.model.dto.SummaryResultMessage;
import at.capadocapi.repository.DocumentRepository;
import at.capadocapi.service.DocumentSummaryListener;
import at.capadocapi.service.DocumentSummaryServiceImpl;
import at.capadocapi.service.Interfaces.DocumentMessagePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentSummaryServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentMessagePublisher messagePublisher;

    private DocumentSummaryServiceImpl summaryService;
    private DocumentSummaryListener summaryListener;

    @BeforeEach
    void setUp() {
        summaryService = new DocumentSummaryServiceImpl(documentRepository);
        summaryListener = new DocumentSummaryListener(summaryService, messagePublisher);
    }

    @Test
    void saveSummary_existingDocument_persistsTrimmedSummary() {
        DocumentEntity doc = DocumentEntity.builder()
                .id(15L)
                .filename("report.pdf")
                .build();
        when(documentRepository.findById(15L)).thenReturn(Optional.of(doc));

        summaryService.saveSummary(15L, "  Dies ist die generierte Zusammenfassung.  ");

        ArgumentCaptor<DocumentEntity> captor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getSummary()).isEqualTo("Dies ist die generierte Zusammenfassung.");
        assertThat(captor.getValue().getSummaryStatus()).isEqualTo(ProcessingStatus.DONE);
    }

    @Test
    void saveSummary_documentNotFound_ignoresCleanlyWithoutSaving() {
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        summaryService.saveSummary(999L, "Zusammenfassung für gelöschtes Dokument.");

        verify(documentRepository, never()).save(any());
    }

    @Test
    void saveSummary_invalidInput_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> summaryService.saveSummary(null, "Summary"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> summaryService.saveSummary(0L, "Summary"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> summaryService.saveSummary(1L, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listener_validMessage_savesSummary() {
        DocumentEntity doc = DocumentEntity.builder()
                .id(22L)
                .filename("invoice.pdf")
                .build();
        when(documentRepository.findById(22L)).thenReturn(Optional.of(doc));

        summaryListener.handleSummaryResult(SummaryResultMessage.builder()
                .documentId(22L)
                .summary("Rechnung über 500 EUR.")
                .build());

        verify(documentRepository).save(doc);
        assertThat(doc.getSummary()).isEqualTo("Rechnung über 500 EUR.");
        verify(messagePublisher, never()).publishInvalidSummaryResultToDlq(any());
    }

    @Test
    void listener_invalidMessage_routesToDlq() {
        SummaryResultMessage invalid = SummaryResultMessage.builder()
                .documentId(22L)
                .summary("   ")
                .build();

        summaryListener.handleSummaryResult(invalid);

        verify(documentRepository, never()).findById(any());
        verify(messagePublisher).publishInvalidSummaryResultToDlq(invalid);
    }
}
