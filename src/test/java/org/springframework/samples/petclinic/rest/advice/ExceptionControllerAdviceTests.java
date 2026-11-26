package org.springframework.samples.petclinic.rest.advice;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Valid;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.rest.dto.ValidationMessageDto;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.beanvalidation.MethodValidationAdapter;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests of the status mapping done by {@link ExceptionControllerAdvice} (PQT-2 and PQT-4).
 */
class ExceptionControllerAdviceTests {

    private final ExceptionControllerAdvice advice = new ExceptionControllerAdvice();

    private static HttpServletRequest request(String method, String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn("/petclinic" + uri);
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:9966/petclinic" + uri));
        return request;
    }

    private static ConstraintViolationException invalidTelephone(String telephone) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Owner owner = new Owner();
            owner.setFirstName("Maria");
            owner.setLastName("Souza");
            owner.setAddress("Rua das Flores, 123");
            owner.setCity("Sao Paulo");
            owner.setTelephone(telephone);
            Set<ConstraintViolation<Owner>> violations = factory.getValidator().validate(owner);
            return new ConstraintViolationException(violations);
        }
    }

    /** Handler signatures used to produce real method validation results. */
    static class SampleHandler {

        public void update(@Min(0) Integer ownerId, @Valid Owner owner) {
            // validated only
        }

        public @NotNull String find() {
            return null;
        }
    }

    /** Owners kept in a list and in a map, to check nested field names. */
    static class Household {

        @Valid
        private final List<Owner> owners;

        @Valid
        private final Map<String, Owner> ownersByCode;

        Household(List<Owner> owners, Map<String, Owner> ownersByCode) {
            this.owners = owners;
            this.ownersByCode = ownersByCode;
        }
    }

    private static Owner owner(String telephone) {
        Owner owner = new Owner();
        owner.setFirstName("Maria");
        owner.setLastName("Souza");
        owner.setAddress("Rua das Flores, 123");
        owner.setCity("Sao Paulo");
        owner.setTelephone(telephone);
        return owner;
    }

    private static Method updateMethod() throws NoSuchMethodException {
        return SampleHandler.class.getMethod("update", Integer.class, Owner.class);
    }

    private static List<String> fields(ResponseEntity<ProblemDetail> response) {
        return validationErrors(response).stream()
            .map(error -> (String) error.getAdditionalProperties().get("field"))
            .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<ValidationMessageDto> validationErrors(ResponseEntity<ProblemDetail> response) {
        Map<String, Object> properties = response.getBody().getProperties();
        return (List<ValidationMessageDto>) properties.get("schemaValidationErrors");
    }

    @Test
    @DisplayName("PQT-4 | Violacao de telefone na entidade vira 400 com campo, valor e mensagem")
    void constraintViolationListsTheTelephoneField() {
        ResponseEntity<ProblemDetail> response = advice.handleConstraintViolationException(
            invalidTelephone("119876543210"), request("POST", "/api/owners"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getDetail()).isEqualTo("The request contains invalid or missing parameters");
        List<ValidationMessageDto> errors = validationErrors(response);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getAdditionalProperties())
            .containsEntry("field", "telephone")
            .containsEntry("rejectedValue", "119876543210")
            .containsEntry("defaultMessage", Owner.TELEPHONE_MESSAGE);
    }

    @Test
    @DisplayName("PQT-4 | Violacao detectada no commit JPA (TransactionSystemException) vira 400, nao 500")
    void constraintViolationRaisedAtCommitIsABadRequest() {
        TransactionSystemException commitFailure = new TransactionSystemException(
            "Could not commit JPA transaction", invalidTelephone("11987654AB"));

        ResponseEntity<ProblemDetail> response = advice.handleTransactionSystemException(
            commitFailure, request("PUT", "/api/owners/1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getTitle()).isEqualTo("ConstraintViolationException");
        assertThat(validationErrors(response)).hasSize(1);
    }

    @Test
    @DisplayName("Falha de commit sem causa de validacao continua sendo erro interno generico")
    void otherTransactionFailuresStayInternalErrors() {
        TransactionSystemException commitFailure = new TransactionSystemException(
            "Could not commit JPA transaction", new IllegalStateException("connection reset"));

        ResponseEntity<ProblemDetail> response = advice.handleTransactionSystemException(
            commitFailure, request("PUT", "/api/owners/1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getDetail()).isEqualTo("An unexpected error occurred while processing your request");
    }

    @Test
    @DisplayName("PQT-2 | Metodo HTTP nao suportado vira 405 e preserva o cabecalho Allow")
    void methodNotSupportedKeeps405AndAllowHeader() {
        HttpRequestMethodNotSupportedException exception =
            new HttpRequestMethodNotSupportedException("POST", List.of("GET", "HEAD"));

        ResponseEntity<ProblemDetail> response = advice.handleErrorResponseException(exception, request("POST", "/"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow()).contains(HttpMethod.GET, HttpMethod.HEAD);
        assertThat(response.getBody().getDetail()).isEqualTo("The HTTP method is not supported for the requested resource");
    }

    @Test
    @DisplayName("Recurso inexistente vira 404")
    void noResourceFoundBecomes404() {
        ResponseEntity<ProblemDetail> response = advice.handleErrorResponseException(
            new NoResourceFoundException(HttpMethod.GET, "/api/unknown", "api/unknown"), request("GET", "/api/unknown"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getDetail()).isEqualTo("The requested resource was not found");
    }

    @Test
    @DisplayName("ErrorResponse 5xx mantem o status com mensagem generica")
    void serverSideErrorResponseKeepsStatusWithGenericDetail() {
        ResponseEntity<ProblemDetail> response = advice.handleErrorResponseException(
            new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE), request("GET", "/api/owners"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getDetail()).isEqualTo("An unexpected error occurred while processing your request");
    }

    @Test
    @DisplayName("Excecao sem status proprio cai no tratamento generico 500")
    void nonErrorResponseExceptionFallsBackTo500() {
        ResponseEntity<ProblemDetail> response = advice.handleErrorResponseException(
            new IllegalStateException("unexpected"), request("GET", "/api/owners"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getHeaders().containsHeader(HttpHeaders.ALLOW)).isFalse();
    }

    @Test
    @DisplayName("Acesso negado pelo @PreAuthorize vira 403")
    void accessDeniedBecomes403() {
        ResponseEntity<ProblemDetail> response = advice.handleAccessDeniedException(
            new AccessDeniedException("denied"), request("GET", "/api/owners"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getDetail()).isEqualTo("Access denied");
    }

    @Test
    @DisplayName("Lista de violacoes nula nao quebra o tratamento")
    void nullViolationSetIsHandled() {
        ResponseEntity<ProblemDetail> response = advice.handleConstraintViolationException(
            new ConstraintViolationException("no details", null), request("POST", "/api/owners"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(validationErrors(response)).isEmpty();
    }

    @Test
    @DisplayName("PQT-4 | HandlerMethodValidationException vira 400 com parametros e campos invalidos")
    void handlerMethodValidationListsParametersAndBeanFields() throws Exception {
        MethodValidationResult result = new MethodValidationAdapter().validateArguments(
            new SampleHandler(), updateMethod(), null, new Object[] {-1, owner("12AB")}, new Class<?>[0]);

        ResponseEntity<ProblemDetail> response = advice.handleHandlerMethodValidationException(
            new HandlerMethodValidationException(result), request("PUT", "/api/owners/-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getDetail()).isEqualTo("The request contains invalid or missing parameters");
        List<ValidationMessageDto> errors = validationErrors(response);
        assertThat(errors).hasSize(2);
        assertThat(errors).anySatisfy(error -> assertThat(error.getAdditionalProperties())
            .containsEntry("field", "ownerId")
            .containsEntry("rejectedValue", "-1"));
        assertThat(errors).anySatisfy(error -> assertThat(error.getAdditionalProperties())
            .containsEntry("field", "telephone")
            .containsEntry("rejectedValue", "12AB")
            .containsEntry("defaultMessage", Owner.TELEPHONE_MESSAGE));
    }

    @Test
    @DisplayName("PQT-4 | Validacao de metodo (AOP) expoe o nome do campo, sem o caminho do metodo")
    void executableViolationsUseClientFieldNames() throws Exception {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Set<ConstraintViolation<SampleHandler>> violations = factory.getValidator().forExecutables()
                .validateParameters(new SampleHandler(), updateMethod(), new Object[] {-1, owner("12AB")});

            ResponseEntity<ProblemDetail> response = advice.handleConstraintViolationException(
                new ConstraintViolationException(violations), request("PUT", "/api/owners/-1"));

            assertThat(fields(response)).containsExactly("ownerId", "telephone");
        }
    }

    @Test
    @DisplayName("Campos aninhados em listas e mapas mantem indice ou chave")
    void nestedViolationsKeepIndexAndKey() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Household household = new Household(
                List.of(owner("6085551023"), owner("12AB")), Map.of("home", owner("+5511")));
            Set<ConstraintViolation<Household>> violations = factory.getValidator().validate(household);

            ResponseEntity<ProblemDetail> response = advice.handleConstraintViolationException(
                new ConstraintViolationException(violations), request("POST", "/api/owners"));

            assertThat(fields(response))
                .containsExactlyInAnyOrder("owners[1].telephone", "ownersByCode[home].telephone");
        }
    }

    @Test
    @DisplayName("Violacao de valor de retorno usa o caminho completo como campo")
    void returnValueViolationFallsBackToThePropertyPath() throws Exception {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Set<ConstraintViolation<SampleHandler>> violations = factory.getValidator().forExecutables()
                .validateReturnValue(new SampleHandler(), SampleHandler.class.getMethod("find"), null);

            ResponseEntity<ProblemDetail> response = advice.handleConstraintViolationException(
                new ConstraintViolationException(violations), request("GET", "/api/owners"));

            assertThat(fields(response)).containsExactly("find.<return value>");
        }
    }

    @Test
    @DisplayName("Varias violacoes do mesmo campo saem em ordem estavel (por mensagem)")
    void violationsOfTheSameFieldAreSortedByMessage() {
        ResponseEntity<ProblemDetail> response = advice.handleConstraintViolationException(
            invalidTelephone(""), request("POST", "/api/owners"));

        // the second message (@NotEmpty) comes from the JVM locale: only the order is asserted
        List<String> messages = validationErrors(response).stream()
            .map(error -> (String) error.getAdditionalProperties().get("defaultMessage"))
            .toList();
        assertThat(messages).hasSize(2).contains(Owner.TELEPHONE_MESSAGE).isSorted();
    }

    @Test
    @DisplayName("Status 4xx sem mensagem especifica usa a mensagem de requisicao invalida")
    void otherClientErrorsUseTheInvalidRequestDetail() {
        ResponseEntity<ProblemDetail> conflict = advice.handleErrorResponseException(
            new ErrorResponseException(HttpStatus.CONFLICT), request("PUT", "/api/owners/1"));
        ResponseEntity<ProblemDetail> nonStandard = advice.handleErrorResponseException(
            new ErrorResponseException(HttpStatusCode.valueOf(499)), request("GET", "/api/owners"));

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().getDetail()).isEqualTo("The request contains invalid or missing parameters");
        assertThat(nonStandard.getStatusCode().value()).isEqualTo(499);
        assertThat(nonStandard.getBody().getDetail()).isEqualTo("The request contains invalid or missing parameters");
    }
}
