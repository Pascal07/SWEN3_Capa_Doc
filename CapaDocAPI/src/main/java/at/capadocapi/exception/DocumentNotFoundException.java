package at.capadocapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class DocumentNotFoundException extends IllegalArgumentException {

    public DocumentNotFoundException(Long documentId) {
        super("Document not found with id: " + documentId);
    }
}
