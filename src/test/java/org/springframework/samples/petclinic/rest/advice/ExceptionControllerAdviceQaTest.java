package org.springframework.samples.petclinic.rest.advice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExceptionControllerAdviceQaTest {

    private HttpServletRequest request() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/petclinic/api/owners");
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:9966/petclinic/api/owners"));
        return request;
    }

    @Test
    @DisplayName("TA-32 | Telefone invalido deve retornar HTTP 400")
    void telefoneInvalidoRetorna400() {
        ConstraintViolationException exception = new ConstraintViolationException("Telefone invalido", Set.of());
        ResponseEntity<ProblemDetail> response = new ExceptionControllerAdvice()
                .handleConstraintViolationException(exception, request());
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
    }

    @Test
    @DisplayName("TA-33 | Validacao de telefone nao deve expor erro interno")
    void validacaoNaoRetornaErroInterno() {
        ConstraintViolationException exception = new ConstraintViolationException("Telefone com 12 digitos", Set.of());
        ResponseEntity<ProblemDetail> response = new ExceptionControllerAdvice()
                .handleConstraintViolationException(exception, request());
        assertEquals("The request contains invalid or missing parameters", response.getBody().getDetail());
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
