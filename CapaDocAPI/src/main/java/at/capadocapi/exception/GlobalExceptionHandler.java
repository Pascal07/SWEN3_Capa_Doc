package at.capadocapi.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidShareLinkPasswordException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(InvalidShareLinkPasswordException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        return error(HttpStatus.UNAUTHORIZED, "Authentication is required.");
    }

    @ExceptionHandler(ShareLinkNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ShareLinkNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDocumentNotFound(DocumentNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ShareLinkExpiredException.class)
    public ResponseEntity<ErrorResponse> handleGone(ShareLinkExpiredException ex) {
        return error(HttpStatus.GONE, ex.getMessage());
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        String message = ex instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .reduce((first, second) -> first + ", " + second)
                .orElse("Request validation failed.")
                : ex.getMessage();
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "You are not allowed to access this resource.");
    }

    @ExceptionHandler({DataAccessLayerException.class, DataAccessException.class})
    public ResponseEntity<ErrorResponse> handleDataAccess(Exception ex) {
        log.error("Data access failure", ex);
        return error(HttpStatus.SERVICE_UNAVAILABLE,
                "The data service is currently unavailable. Please try again later.");
    }

    @ExceptionHandler(QueuePublishException.class)
    public ResponseEntity<ErrorResponse> handleQueuePublish(QueuePublishException ex) {
        log.error("RabbitMQ error while publishing message", ex);
        return error(HttpStatus.SERVICE_UNAVAILABLE,
                "The messaging service is currently unavailable. Please try again later.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled application error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        ErrorResponse response = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message == null || message.isBlank() ? status.getReasonPhrase() : message,
                LocalDateTime.now()
        );
        return ResponseEntity.status(status).body(response);
    }
}
