package com.example.troubleticket.exception;

import com.example.troubleticket.dto.Models.ErrorResponse;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.UUID;

@RestControllerAdvice
public class ApiErrorHandler {
    static ErrorResponse error(String code, String message) {
        return new ErrorResponse(code,message,UUID.randomUUID().toString());
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> custom(ApiException e) {
        return ResponseEntity.status(e.status).body(error(e.code,e.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
                       MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> invalid(Exception e) {
        return ResponseEntity.badRequest().body(error("VALIDATION_ERROR","Invalid request payload"));
    }
}
