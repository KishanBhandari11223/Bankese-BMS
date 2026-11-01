package com.bms.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTest {

    @Test
    void exposesStatusReasonForClientErrorsWithoutChangingStatusCode() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        ResponseEntity<java.util.Map<String, String>> response =
                handler.handleResponseStatusException(new ResponseStatusException(
                        HttpStatus.CONFLICT, "Insufficient funds"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Insufficient funds", response.getBody().get("message"));
    }
}