
package com.threadviewer;

import com.threadviewer.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    @Test
    void shouldReturnConsistentErrorResponse() {

        ResponseStatusException exception =
                new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported file type"
                );

        var response =
                handler.handleResponseStatusException(exception);

        // Verify HTTP status
        assertEquals(400, response.getStatusCode().value());

        // Verify response body exists
        assertNotNull(response.getBody());

        Map<String, Object> body = response.getBody();

        // Verify error message
        assertEquals(
                "Unsupported file type",
                body.get("message")
        );

        // Verify error description
        assertEquals(
                "Bad Request",
                body.get("error")
        );

        // Verify timestamp exists
        assertNotNull(body.get("timestamp"));
    }

    @Test
    void shouldReturnPayloadTooLargeErrorResponse() {

        ResponseStatusException exception =
                new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "File size must not exceed 10 MB"
                );

        var response =
                handler.handleResponseStatusException(exception);

        assertEquals(413, response.getStatusCode().value());
        assertNotNull(response.getBody());

        Map<String, Object> body = response.getBody();

        assertEquals(
                "Content Too Large",
                body.get("error")
        );

        assertEquals(
                "File size must not exceed 10 MB",
                body.get("message")
        );

        assertNotNull(body.get("timestamp"));
    }
}
