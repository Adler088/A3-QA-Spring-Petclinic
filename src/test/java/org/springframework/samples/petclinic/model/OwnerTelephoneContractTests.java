package org.springframework.samples.petclinic.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.samples.petclinic.rest.dto.OwnerFieldsDto;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PQT-1 root cause guard: the REST contract ({@code openapi.yml} -> generated {@link OwnerFieldsDto}) and the
 * domain rule ({@link Owner}) must accept exactly the same telephones. When they diverged (DTO: 1 to 20 digits,
 * entity: at most 10 digits) an input accepted by the API failed later in JPA and became HTTP 500.
 */
class OwnerTelephoneContractTests {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    @DisplayName("PQT-1 | DTO gerado e entidade usam a mesma regra de telefone (10 ou 11 digitos)")
    void dtoAndEntityShareTheSameTelephoneRule() throws Exception {
        Pattern dtoPattern = OwnerFieldsDto.class.getMethod("getTelephone").getAnnotation(Pattern.class);
        Size dtoSize = OwnerFieldsDto.class.getMethod("getTelephone").getAnnotation(Size.class);
        Pattern entityPattern = Owner.class.getDeclaredField("telephone").getAnnotation(Pattern.class);

        assertThat(entityPattern.regexp()).isEqualTo(Owner.TELEPHONE_REGEXP);
        assertThat(dtoPattern.regexp()).isEqualTo(entityPattern.regexp());
        assertThat(dtoPattern.message()).isEqualTo(Owner.TELEPHONE_MESSAGE);
        assertThat(dtoSize.min()).isEqualTo(10);
        assertThat(dtoSize.max()).isEqualTo(11);
    }

    @ParameterizedTest(name = "PQT-1 [{index}] DTO e entidade concordam sobre \"{0}\"")
    @NullSource
    @ValueSource(strings = {"1132654321", "11987654321", "119876543", "119876543210", "11987654321000000000",
        "11987654AB", "(11)98765-4321", "+5511987654321", "", " ", "1198765432\n"})
    void dtoAndEntityAgreeOnEveryTelephone(String telephone) {
        OwnerFieldsDto dto = new OwnerFieldsDto().telephone(telephone);
        Owner owner = new Owner();
        owner.setTelephone(telephone);

        boolean acceptedByApi = validator.validateProperty(dto, "telephone").isEmpty();
        boolean acceptedByDomain = validator.validateProperty(owner, "telephone").isEmpty();

        assertThat(acceptedByApi).as("API x dominio para '%s'", telephone).isEqualTo(acceptedByDomain);
    }
}
