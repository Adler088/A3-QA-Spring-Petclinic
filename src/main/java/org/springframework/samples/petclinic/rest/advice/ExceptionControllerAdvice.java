package org.springframework.samples.petclinic.rest.advice;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.samples.petclinic.rest.controller.BindingErrorsResponse;
import org.springframework.samples.petclinic.rest.dto.ValidationMessageDto;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates exceptions into RFC 9457 {@link ProblemDetail} responses.
 * <p>
 * Client errors keep a 4xx status: bean validation failures (including
 * the ones raised by JPA when the transaction commits), Spring MVC
 * infrastructure errors such as 405 Method Not Allowed and authorization
 * failures. Only unexpected errors become HTTP 500, always with a generic
 * message.
 */
@ControllerAdvice
public class ExceptionControllerAdvice {

    /** Logger of the advice. */
    private static final Logger LOGGER =
        LoggerFactory.getLogger(ExceptionControllerAdvice.class);
    /** Detail of HTTP 500 responses. */
    private static final String ERROR_UNEXPECTED =
        "An unexpected error occurred while processing your request";
    /** Detail of HTTP 409 responses. */
    private static final String ERROR_DATA_INTEGRITY =
        "The requested resource could not be processed due to a data "
            + "constraint violation";
    /** Detail of HTTP 400 responses. */
    private static final String ERROR_INVALID_REQUEST =
        "The request contains invalid or missing parameters";
    /** Detail of HTTP 403 responses. */
    private static final String ERROR_ACCESS_DENIED = "Access denied";
    /** Detail of HTTP 404 responses. */
    private static final String ERROR_NOT_FOUND =
        "The requested resource was not found";
    /** Detail of HTTP 405 responses. */
    private static final String ERROR_METHOD_NOT_ALLOWED =
        "The HTTP method is not supported for the requested resource";
    /** Detail of HTTP 406 and 415 responses. */
    private static final String ERROR_MEDIA_TYPE =
        "The requested media type is not supported";
    /** Problem detail property that lists the invalid fields. */
    private static final String SCHEMA_VALIDATION_ERRORS =
        "schemaValidationErrors";

    private ProblemDetail detailBuild(final Exception e,
                                      final HttpStatusCode status,
                                      final StringBuffer url,
                                      final String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setType(URI.create(url.toString()));
        problemDetail.setTitle(e.getClass().getSimpleName());
        problemDetail.setDetail(detail);
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty(SCHEMA_VALIDATION_ERRORS,
            List.<ValidationMessageDto>of());
        return problemDetail;
    }

    private static ValidationMessageDto validationMessage(
        final String field, final Object rejectedValue,
        final String defaultMessage) {
        String rejected = Objects.toString(rejectedValue, "null");
        String message = Objects.toString(defaultMessage, "Validation failed");
        String text = "Field '%s' %s (rejected value: %s)"
            .formatted(field, message, rejected);
        return new ValidationMessageDto(text)
            .putAdditionalProperty("field", field)
            .putAdditionalProperty("rejectedValue", rejected)
            .putAdditionalProperty("defaultMessage", message);
    }

    private static List<ValidationMessageDto> toValidationMessages(
        final Set<ConstraintViolation<?>> violations) {
        if (violations == null) {
            return List.of();
        }
        return violations.stream()
            .sorted(Comparator.comparing(ExceptionControllerAdvice::fieldName)
                .thenComparing(v -> Objects.toString(v.getMessage(), "")))
            .map(v -> validationMessage(
                fieldName(v), v.getInvalidValue(), v.getMessage()))
            .toList();
    }

    /**
     * Client oriented name of a violated field: {@code telephone} for an
     * entity, {@code ownerId} instead of {@code getOwner.ownerId} for a
     * method parameter and {@code pets[0].name} for nested properties.
     *
     * @param violation the constraint violation
     * @return the field name exposed in the response
     */
    private static String fieldName(final ConstraintViolation<?> violation) {
        List<Path.Node> nodes = new ArrayList<>();
        violation.getPropertyPath().forEach(nodes::add);
        boolean hasProperties = nodes.stream()
            .anyMatch(node -> node.getKind() == ElementKind.PROPERTY);
        StringBuilder field = new StringBuilder();
        for (Path.Node node : nodes) {
            ElementKind kind = node.getKind();
            boolean executable = kind == ElementKind.METHOD
                || kind == ElementKind.CONSTRUCTOR
                || kind == ElementKind.CROSS_PARAMETER
                || kind == ElementKind.RETURN_VALUE;
            if (executable
                || (kind == ElementKind.PARAMETER && hasProperties)) {
                continue;
            }
            if (node.isInIterable() && field.length() > 0) {
                Object index = node.getIndex() != null
                    ? node.getIndex() : node.getKey();
                field.append('[').append(Objects.toString(index, ""))
                    .append(']');
            }
            if (node.getName() != null) {
                if (field.length() > 0) {
                    field.append('.');
                }
                field.append(node.getName());
            }
        }
        if (field.length() == 0) {
            return String.valueOf(violation.getPropertyPath());
        }
        return field.toString();
    }

    private static String clientErrorDetail(final HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        if (resolved == null) {
            return ERROR_INVALID_REQUEST;
        }
        return switch (resolved) {
            case NOT_FOUND -> ERROR_NOT_FOUND;
            case METHOD_NOT_ALLOWED -> ERROR_METHOD_NOT_ALLOWED;
            case NOT_ACCEPTABLE, UNSUPPORTED_MEDIA_TYPE -> ERROR_MEDIA_TYPE;
            default -> ERROR_INVALID_REQUEST;
        };
    }

    /**
     * Bean Validation failures raised outside the MVC binding, for instance
     * by Hibernate when an entity is persisted. They are client errors and
     * the response lists every violated field.
     *
     * @param e the validation failure
     * @param request the current request
     * @return HTTP 400 with the violated fields
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseBody
    public ResponseEntity<ProblemDetail> handleConstraintViolationException(
        final ConstraintViolationException e,
        final HttpServletRequest request) {
        LOGGER.warn("Validation error at {} {}: {}",
            request.getMethod(), request.getRequestURI(), e.getMessage());
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetail detail = this.detailBuild(e, status,
            request.getRequestURL(), ERROR_INVALID_REQUEST);
        detail.setProperty(SCHEMA_VALIDATION_ERRORS,
            toValidationMessages(e.getConstraintViolations()));
        return ResponseEntity.status(status).body(detail);
    }

    /**
     * JPA validates updated entities when the transaction commits; the
     * {@link ConstraintViolationException} then arrives wrapped in a
     * {@link TransactionSystemException} and is still a client error.
     *
     * @param e the commit failure
     * @param request the current request
     * @return HTTP 400 for validation failures, HTTP 500 otherwise
     */
    @ExceptionHandler(TransactionSystemException.class)
    @ResponseBody
    public ResponseEntity<ProblemDetail> handleTransactionSystemException(
        final TransactionSystemException e,
        final HttpServletRequest request) {
        if (NestedExceptionUtils.getMostSpecificCause(e)
            instanceof ConstraintViolationException violation) {
            return handleConstraintViolationException(violation, request);
        }
        return handleGeneralException(e, request);
    }

    /**
     * Malformed requests: invalid JSON, wrong argument types and
     * {@link IllegalArgumentException}s.
     *
     * @param e the failure
     * @param request the current request
     * @return HTTP 400
     */
    @ExceptionHandler({
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    @ResponseBody
    public ResponseEntity<ProblemDetail> handleBadRequestExceptions(
        final Exception e, final HttpServletRequest request) {
        LOGGER.warn("Bad request at {} {}: {}",
            request.getMethod(), request.getRequestURI(), e.getMessage());
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetail detail = this.detailBuild(e, status,
            request.getRequestURL(), ERROR_INVALID_REQUEST);
        return ResponseEntity.status(status).body(detail);
    }

    /**
     * Constraint violations on handler method parameters (for instance
     * {@code @Min(0)} on a path variable or {@code @Max(100)} on a page
     * size).
     *
     * @param e the validation failure
     * @param request the current request
     * @return HTTP 400 with the violated parameters
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseBody
    public ResponseEntity<ProblemDetail> handleHandlerMethodValidationException(
        final HandlerMethodValidationException e,
        final HttpServletRequest request) {
        LOGGER.warn("Invalid parameters at {} {}: {}",
            request.getMethod(), request.getRequestURI(), e.getMessage());
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetail detail = this.detailBuild(e, status,
            request.getRequestURL(), ERROR_INVALID_REQUEST);
        List<ValidationMessageDto> messages = new ArrayList<>();
        for (ParameterValidationResult result
            : e.getParameterValidationResults()) {
            if (result instanceof ParameterErrors errors) {
                for (FieldError fieldError : errors.getFieldErrors()) {
                    messages.add(validationMessage(fieldError.getField(),
                        fieldError.getRejectedValue(),
                        fieldError.getDefaultMessage()));
                }
            } else {
                MethodParameter parameter = result.getMethodParameter();
                String name = Objects.toString(parameter.getParameterName(),
                    "arg" + parameter.getParameterIndex());
                for (MessageSourceResolvable error
                    : result.getResolvableErrors()) {
                    messages.add(validationMessage(name,
                        result.getArgument(), error.getDefaultMessage()));
                }
            }
        }
        detail.setProperty(SCHEMA_VALIDATION_ERRORS, messages);
        return ResponseEntity.status(status).body(detail);
    }

    /**
     * Spring MVC infrastructure exceptions already carry the right status
     * (405 with the {@code Allow} header, 404, 406, 415, 400...). Without
     * this handler the catch-all below turned them into HTTP 500.
     *
     * @param e the failure
     * @param request the current request
     * @return the status, headers and a generic detail of the exception
     */
    @ExceptionHandler({
        HttpRequestMethodNotSupportedException.class,
        HttpMediaTypeNotSupportedException.class,
        HttpMediaTypeNotAcceptableException.class,
        MissingServletRequestParameterException.class,
        NoHandlerFoundException.class,
        NoResourceFoundException.class,
        ErrorResponseException.class
    })
    @ResponseBody
    public ResponseEntity<ProblemDetail> handleErrorResponseException(
        final Exception e, final HttpServletRequest request) {
        if (!(e instanceof ErrorResponse errorResponse)) {
            return handleGeneralException(e, request);
        }
        HttpStatusCode status = errorResponse.getStatusCode();
        boolean serverError = status.is5xxServerError();
        if (serverError) {
            LOGGER.error("Server error at {} {}",
                request.getMethod(), request.getRequestURI(), e);
        } else {
            LOGGER.warn("Request rejected at {} {} with status {}: {}",
                request.getMethod(), request.getRequestURI(),
                status.value(), e.getMessage());
        }
        String message;
        if (serverError) {
            message = ERROR_UNEXPECTED;
        } else {
            message = clientErrorDetail(status);
        }
        ProblemDetail detail = this.detailBuild(e, status,
            request.getRequestURL(), message);
        return ResponseEntity.status(status)
            .headers(errorResponse.getHeaders()).body(detail);
    }

    /**
     * Method security ({@code @PreAuthorize}) failures.
     *
     * @param e the authorization failure
     * @param request the current request
     * @return HTTP 403 instead of HTTP 500
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseBody
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(
        final AccessDeniedException e, final HttpServletRequest request) {
        LOGGER.warn("Access denied at {} {}",
            request.getMethod(), request.getRequestURI());
        HttpStatus status = HttpStatus.FORBIDDEN;
        ProblemDetail detail = this.detailBuild(e, status,
            request.getRequestURL(), ERROR_ACCESS_DENIED);
        return ResponseEntity.status(status).body(detail);
    }

    /**
     * Unexpected failures; the detail never exposes technical messages.
     *
     * @param e the failure
     * @param request the current request
     * @return HTTP 500 with a generic detail
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<ProblemDetail> handleGeneralException(
        final Exception e, final HttpServletRequest request) {
        LOGGER.error("Unexpected error at {} {}",
            request.getMethod(), request.getRequestURI(), e);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ProblemDetail detail = this.detailBuild(e, status,
            request.getRequestURL(), ERROR_UNEXPECTED);
        return ResponseEntity.status(status).body(detail);
    }

    /**
     * Database constraint violations.
     *
     * @param e the failure
     * @param request the current request
     * @return HTTP 409 with a generic detail
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseBody
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolationException(
        final DataIntegrityViolationException e,
        final HttpServletRequest request) {
        LOGGER.warn("Data integrity violation at {} {}: {}",
            request.getMethod(),
            request.getRequestURI(),
            e.getMessage());
        HttpStatus status = HttpStatus.CONFLICT;
        ProblemDetail detail = this.detailBuild(e, status,
            request.getRequestURL(), ERROR_DATA_INTEGRITY);
        return ResponseEntity.status(status).body(detail);
    }

    /**
     * Invalid request bodies ({@code @Valid @RequestBody}).
     *
     * @param e the validation failure
     * @param request the current request
     * @return HTTP 400 with the violated fields
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValidException(
        final MethodArgumentNotValidException e,
        final HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        BindingErrorsResponse errors = new BindingErrorsResponse();
        BindingResult bindingResult = e.getBindingResult();
        ProblemDetail detail = this.detailBuild(e, status,
            request.getRequestURL(), ERROR_INVALID_REQUEST);
        if (bindingResult.hasErrors()) {
            errors.addAllErrors(bindingResult);
            List<ValidationMessageDto> schemaValidationErrors =
                bindingResult.getFieldErrors().stream()
                    .map(fieldError -> validationMessage(fieldError.getField(),
                        fieldError.getRejectedValue(),
                        fieldError.getDefaultMessage()))
                    .toList();
            detail.setProperty(SCHEMA_VALIDATION_ERRORS, schemaValidationErrors);
        }
        return ResponseEntity.status(status).body(detail);
    }
}
