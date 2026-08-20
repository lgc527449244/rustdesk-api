package com.rustdeskapi.server.common;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final URI INVALID_REQUEST_TYPE = URI.create("urn:rustdesk-api:invalid-request");

    @ExceptionHandler(InvalidPayloadException.class)
    ProblemDetail handleInvalidPayload(InvalidPayloadException exception) {
        return invalidRequest(exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleBeanValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> "field '" + error.getField() + "' " + error.getDefaultMessage())
                .orElse("request validation failed");
        return invalidRequest(detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody() {
        return invalidRequest("request body is not valid JSON");
    }

    private ProblemDetail invalidRequest(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid request");
        problem.setType(INVALID_REQUEST_TYPE);
        return problem;
    }
}
