package com.techindna.template.exception;

import com.techindna.template.exception.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<ErrorBody> buildResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(
                        ErrorBody.builder()
                                .error(status.getReasonPhrase().toUpperCase())
                                .message(message)
                                .status(status.value())
                                .build());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorBody> handleNotFound(NotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorBody> handleBadRequest(BadRequestException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(UnprocessableContentException.class)
    public ResponseEntity<ErrorBody> handleUnprocessable(UnprocessableContentException ex) {
        return buildResponse(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> handleBadArgument(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ErrorBody> handleTypeMismatch(TypeMismatchException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Invalid parameter: " + ex.getPropertyName());
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ErrorBody> handleMissingPathVariable(MissingPathVariableException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Missing or invalid path variable: " + ex.getVariableName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> handleHttpMessageNotReadable() {
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Request body is missing or malformed.");
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorBody> handleConflict(ConflictException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorBody> handleUnauthorized(UnauthorizedException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorBody> handleForbidden(ForbiddenException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(InternalServerErrorException.class)
    public ResponseEntity<ErrorBody> handleInternalServerError(InternalServerErrorException ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong, please try again later");
    }
}
