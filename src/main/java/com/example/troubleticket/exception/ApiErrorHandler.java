package com.example.troubleticket.exception;

import com.example.troubleticket.dto.Models.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.UUID;

@RestControllerAdvice
public class ApiErrorHandler {
    static ErrorResponse error(String code, String message, HttpServletRequest request) {
        Object requestId=request.getAttribute("requestId");
        return new ErrorResponse(code,message,requestId == null ? UUID.randomUUID().toString() : requestId.toString());
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> custom(ApiException e,HttpServletRequest request) {
        return ResponseEntity.status(e.status).body(error(e.code,e.getMessage(),request));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
                       MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> invalid(Exception e, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error("VALIDATION_ERROR","Invalid request payload",request));
    }
}
