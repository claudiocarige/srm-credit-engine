package com.claudiocarige.backendcreditengine.common.web;

import com.claudiocarige.backendcreditengine.assignor.application.exception.AssignorNotFoundException;
import com.claudiocarige.backendcreditengine.exchangerate.application.exception.DuplicateExchangeRateException;
import com.claudiocarige.backendcreditengine.exchangerate.application.exception.ExchangeRateUnavailableException;
import com.claudiocarige.backendcreditengine.exchangerate.application.exception.InvalidCurrencyException;
import com.claudiocarige.backendcreditengine.common.exception.InvalidDueDateException;
import com.claudiocarige.backendcreditengine.receivable.application.exception.DuplicateReceivableDocumentException;
import com.claudiocarige.backendcreditengine.receivable.application.exception.ReceivableNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String ERROR_CODE_VALIDATION_ERROR = "VALIDATION_ERROR";
    private static final String ERROR_CODE_DUPLICATE_RATE = "DUPLICATE_EXCHANGE_RATE_EFFECTIVE_AT";
    private static final String ERROR_CODE_RATE_UNAVAILABLE = "EXCHANGE_RATE_UNAVAILABLE";
    private static final String ERROR_CODE_INVALID_DUE_DATE = "INVALID_DUE_DATE";
    private static final String ERROR_CODE_ASSIGNOR_NOT_FOUND = "ASSIGNOR_NOT_FOUND";
    private static final String ERROR_CODE_RECEIVABLE_NOT_FOUND = "RECEIVABLE_NOT_FOUND";
    private static final String ERROR_CODE_DUPLICATE_RECEIVABLE_DOC = "DUPLICATE_RECEIVABLE_DOCUMENT";
    private static final String ERROR_CODE_RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    private static final String ERROR_CODE_METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    private static final String ERROR_CODE_INTERNAL_ERROR = "INTERNAL_ERROR";

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<FieldErrorDetail> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(err -> new FieldErrorDetail(err.getField(), err.getDefaultMessage()))
                .toList();

        ProblemDetail problem = buildProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Validation Error",
                "Invalid request parameters.",
                request.getRequestURI(),
                ERROR_CODE_VALIDATION_ERROR
        );
        problem.setProperty("fieldErrors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = buildProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Validation Error",
                "JSON request payload contains unrecognized property, invalid field format or unquoted numeric value.",
                request.getRequestURI(),
                ERROR_CODE_VALIDATION_ERROR
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        String detail = "Invalid value for parameter: " + ex.getName();
        ProblemDetail problem = buildProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Validation Error",
                detail,
                request.getRequestURI(),
                ERROR_CODE_VALIDATION_ERROR
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleHandlerMethodValidation(
            HandlerMethodValidationException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = buildProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Validation Error",
                "Invalid request parameter.",
                request.getRequestURI(),
                ERROR_CODE_VALIDATION_ERROR
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(InvalidCurrencyException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCurrency(
            InvalidCurrencyException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = buildProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Validation Error",
                ex.getMessage(),
                request.getRequestURI(),
                ERROR_CODE_VALIDATION_ERROR
        );
        problem.setProperty("fieldErrors", List.of(new FieldErrorDetail("currency", ex.getMessage())));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(DuplicateExchangeRateException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateExchangeRate(
            DuplicateExchangeRateException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = buildProblemDetail(
                HttpStatus.CONFLICT,
                "Conflict",
                ex.getMessage(),
                request.getRequestURI(),
                ERROR_CODE_DUPLICATE_RATE
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(ExchangeRateUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleExchangeRateUnavailable(
            ExchangeRateUnavailableException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = buildProblemDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Unprocessable Entity",
                ex.getMessage(),
                request.getRequestURI(),
                ERROR_CODE_RATE_UNAVAILABLE
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(problem);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        String message = ex.getParameterName() + " is required";
        ProblemDetail problem = buildProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Validation Error",
                "Missing required parameter: " + ex.getParameterName(),
                request.getRequestURI(),
                ERROR_CODE_VALIDATION_ERROR
        );
        problem.setProperty("fieldErrors", List.of(new FieldErrorDetail(ex.getParameterName(), message)));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(AssignorNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleAssignorNotFound(
            AssignorNotFoundException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = buildProblemDetail(
                HttpStatus.NOT_FOUND,
                "Not Found",
                ex.getMessage(),
                request.getRequestURI(),
                ERROR_CODE_ASSIGNOR_NOT_FOUND
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(ReceivableNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleReceivableNotFound(
            ReceivableNotFoundException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = buildProblemDetail(
                HttpStatus.NOT_FOUND,
                "Not Found",
                ex.getMessage(),
                request.getRequestURI(),
                ERROR_CODE_RECEIVABLE_NOT_FOUND
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(DuplicateReceivableDocumentException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateReceivableDocument(
            DuplicateReceivableDocumentException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = buildProblemDetail(
                HttpStatus.CONFLICT,
                "Conflict",
                ex.getMessage(),
                request.getRequestURI(),
                ERROR_CODE_DUPLICATE_RECEIVABLE_DOC
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(InvalidDueDateException.class)
    public ResponseEntity<ProblemDetail> handleInvalidDueDate(
            InvalidDueDateException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = buildProblemDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Unprocessable Entity",
                ex.getMessage(),
                request.getRequestURI(),
                ERROR_CODE_INVALID_DUE_DATE
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(problem);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = buildProblemDetail(
                HttpStatus.NOT_FOUND,
                "Not Found",
                ex.getMessage(),
                request.getRequestURI(),
                ERROR_CODE_RESOURCE_NOT_FOUND
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = buildProblemDetail(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Method Not Allowed",
                ex.getMessage(),
                request.getRequestURI(),
                ERROR_CODE_METHOD_NOT_ALLOWED
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {
        if (ex instanceof ErrorResponse errorResponse) {
            ProblemDetail problem = buildProblemDetail(
                    errorResponse.getStatusCode(),
                    HttpStatus.valueOf(errorResponse.getStatusCode().value()).getReasonPhrase(),
                    ex.getMessage(),
                    request.getRequestURI(),
                    "HTTP_" + errorResponse.getStatusCode().value()
            );
            return ResponseEntity.status(errorResponse.getStatusCode()).body(problem);
        }
        log.error("Unhandled exception processing request to {}", request.getRequestURI(), ex);
        ProblemDetail problem = buildProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected internal error occurred.",
                request.getRequestURI(),
                ERROR_CODE_INTERNAL_ERROR
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private ProblemDetail buildProblemDetail(
            HttpStatusCode status,
            String title,
            String detail,
            String uri,
            String errorCode
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("about:blank"));
        problem.setInstance(URI.create(uri));
        problem.setProperty("errorCode", errorCode);
        Instant now = (clock != null && clock.instant() != null) ? clock.instant() : Instant.now();
        problem.setProperty("timestamp", now.toString());
        return problem;
    }

    public record FieldErrorDetail(String field, String message) {}
}
