package com.example.troubleticket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class Models {
    private Models() {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginResponse(String accessToken, String tokenType, long expiresIn) {}
    public record CreateRequest(@NotNull @Size(min=1) String externalId, @NotNull @Min(1) Long serviceId,
                                @NotNull @Size(min=1) String description, @NotNull String status, @NotNull @Size(min=1) String note) {}
    public record CloseRequest(@NotNull String status) {}
    public record NoteRequest(@NotNull @Size(min=1) String text) {}
    public record NoteResponse(String id, String text, Instant date) {}
    public record TicketResponse(String id, String externalId, Long serviceId, String description,
                                 String status, List<NoteResponse> notes) {}
    public record TicketSummary(String externalId, Long serviceId, String description, String status) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(String code, String message, String requestId) {}
}
