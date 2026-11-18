package com.example.troubleticket.tools;

import com.example.troubleticket.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class TicketStatusValidator {
    public void validateNew(String status) {
        if (!"new".equals(status))
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Create accepts only status 'new'");
    }

    public void validateClosed(String status) {
        if (!"closed".equals(status))
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Patch accepts only status 'closed'");
    }
}
