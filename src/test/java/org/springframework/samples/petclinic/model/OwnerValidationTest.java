package org.springframework.samples.petclinic.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PQT-1: Regressao da validacao do telefone do proprietario. */
class OwnerValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void iniciarValidador() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void fecharValidador() {
        if (factory != null) {
            factory.close();
        }
    }

    private Owner proprietario(String telefone) {
        Owner owner = new Owner();
        owner.setFirstName("Carlos");
        owner.setLastName("Silva");
        owner.setAddress("Rua das Flores, 123");
        owner.setCity("Sao Paulo");
        owner.setTelephone(telefone);
        return owner;
    }

    @Test
    void ta01_deveAceitarTelefoneCom10Digitos() {
        assertTrue(validator.validate(proprietario("1198765432")).isEmpty());
    }

    @Test
    void ta02_deveAceitarTelefoneCom11Digitos() {
        assertTrue(validator.validate(proprietario("11987654321")).isEmpty());
    }

    @Test
    void ta03_deveRejeitarTelefoneCom9Digitos() {
        assertFalse(validator.validateProperty(proprietario("119876543"), "telephone").isEmpty());
    }

    @Test
    void ta04_deveRejeitarTelefoneComLetras() {
        assertFalse(validator.validateProperty(proprietario("11987654AB"), "telephone").isEmpty());
    }

    @Test
    void ta08_deveRejeitarTelefoneVazio() {
        assertFalse(validator.validateProperty(proprietario(""), "telephone").isEmpty());
    }

    @Test
    void ta09_deveRejeitarTelefoneCom12Digitos() {
        assertFalse(validator.validateProperty(proprietario("119876543210"), "telephone").isEmpty());
    }

    @Test
    void ta10_deveRejeitarPrimeiroNomeVazio() {
        Owner owner = proprietario("11987654321");
        owner.setFirstName("");
        assertFalse(validator.validateProperty(owner, "firstName").isEmpty());
    }

    @Test
    void ta11_deveRejeitarSobrenomeVazio() {
        Owner owner = proprietario("11987654321");
        owner.setLastName("");
        assertFalse(validator.validateProperty(owner, "lastName").isEmpty());
    }

    @Test
    void ta12_deveRejeitarEnderecoVazio() {
        Owner owner = proprietario("11987654321");
        owner.setAddress("");
        assertFalse(validator.validateProperty(owner, "address").isEmpty());
    }

    @Test
    void ta13_deveRejeitarCidadeVazia() {
        Owner owner = proprietario("11987654321");
        owner.setCity("");
        assertFalse(validator.validateProperty(owner, "city").isEmpty());
    }

}
