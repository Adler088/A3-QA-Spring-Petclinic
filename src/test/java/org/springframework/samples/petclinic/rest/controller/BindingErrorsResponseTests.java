package org.springframework.samples.petclinic.rest.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.validation.BeanPropertyBindingResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BindingErrorsResponse}.
 */
class BindingErrorsResponseTests {

    @Test
    @DisplayName("Sem erros, o JSON e uma lista vazia")
    void noErrorsSerializeAsAnEmptyList() {
        assertThat(new BindingErrorsResponse().toJSON()).isEqualTo("[]");
        assertThat(new BindingErrorsResponse(1, 1).toJSON()).isEqualTo("[]");
    }

    @Test
    @DisplayName("Id informado so no corpo e rejeitado")
    void bodyIdWithoutPathIdIsRejected() {
        String json = new BindingErrorsResponse(5).toJSON();

        assertThat(json)
            .contains("\"objectName\":\"body\"")
            .contains("\"fieldName\":\"id\"")
            .contains("\"fieldValue\":\"5\"")
            .contains("\"errorMessage\":\"must not be specified\"");
    }

    @Test
    @DisplayName("Id do corpo diferente do id do caminho e rejeitado")
    void bodyIdDifferentFromPathIdIsRejected() {
        BindingErrorsResponse response = new BindingErrorsResponse(1, 2);

        assertThat(response.toJSON()).contains("does not match pathId: 1");
        assertThat(response.toString())
            .startsWith("BindingErrorsResponse [bindingErrors=[BindingError [objectName=body");
    }

    @Test
    @DisplayName("Erros de binding sao copiados com objeto, campo, valor e mensagem")
    void bindingResultErrorsAreCopied() {
        Owner owner = new Owner();
        owner.setTelephone("12AB");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(owner, "owner");
        bindingResult.rejectValue("telephone", "Pattern", Owner.TELEPHONE_MESSAGE);
        BindingErrorsResponse response = new BindingErrorsResponse();

        response.addAllErrors(bindingResult);

        assertThat(response.toJSON())
            .contains("\"objectName\":\"owner\"")
            .contains("\"fieldName\":\"telephone\"")
            .contains("\"fieldValue\":\"12AB\"")
            .contains("\"errorMessage\":\"" + Owner.TELEPHONE_MESSAGE + "\"");
    }
}
