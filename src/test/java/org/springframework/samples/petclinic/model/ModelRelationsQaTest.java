package org.springframework.samples.petclinic.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Regras de dominio: relacionamentos, consultas e validacao de visitas. */
class ModelRelationsQaTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void iniciarValidador() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void fecharValidador() {
        factory.close();
    }

    private Pet pet(String nome) {
        Pet pet = new Pet();
        pet.setName(nome);
        return pet;
    }

    @Test
    void ta14_adicionarPetDeveAssociarProprietario() {
        Owner owner = new Owner();
        Pet pet = pet("Rex");
        owner.addPet(pet);
        assertSame(owner, pet.getOwner());
        assertTrue(owner.getPets().contains(pet));
    }

    @Test
    void ta15_buscarPetPeloNomeDeveEncontrar() {
        Owner owner = new Owner();
        Pet pet = pet("Rex");
        owner.addPet(pet);
        assertSame(pet, owner.getPet("Rex"));
    }

    @Test
    void ta16_buscarPetInexistenteDeveRetornarNulo() {
        Owner owner = new Owner();
        owner.addPet(pet("Rex"));
        assertNull(owner.getPet("Bolinha"));
    }

    @Test
    void ta17_listaDePetsDeveSerImutavel() {
        Owner owner = new Owner();
        owner.addPet(pet("Rex"));
        List<Pet> pets = owner.getPets();
        assertThrows(UnsupportedOperationException.class, () -> pets.clear());
    }

    @Test
    void ta18_adicionarVisitaDeveAssociarPet() {
        Pet pet = pet("Rex");
        Visit visita = new Visit();
        visita.setDescription("Consulta de rotina");
        pet.addVisit(visita);
        assertSame(pet, visita.getPet());
        assertTrue(pet.getVisits().contains(visita));
    }

    @Test
    void ta19_visitasDevemSerOrdenadasDaMaisRecente() {
        Pet pet = pet("Rex");
        Visit antiga = new Visit();
        antiga.setDate(LocalDate.of(2025, 1, 10));
        Visit recente = new Visit();
        recente.setDate(LocalDate.of(2025, 8, 20));
        pet.addVisit(antiga);
        pet.addVisit(recente);
        assertEquals(List.of(recente, antiga), pet.getVisits());
    }

    @Test
    void ta20_visitaComDescricaoVaziaDeveSerRejeitada() {
        Visit visita = new Visit();
        visita.setDescription("");
        assertFalse(validator.validateProperty(visita, "description").isEmpty());
    }
}
