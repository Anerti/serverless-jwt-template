package com.techindna.template.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorBody {

    private int status;

    private String error;

    private String message;

    @Builder.Default
    private Instant timestamp = Instant.now();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void send(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(status.value());
        MAPPER.writeValue(
                response.getWriter(),
                ErrorBody.builder()
                        .status(status.value())
                        .error(status.getReasonPhrase().toUpperCase())
                        .message(message)
                        .build());
    }
}
