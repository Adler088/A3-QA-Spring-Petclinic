package org.springframework.samples.petclinic.repository.jdbc;

import java.time.LocalDate;
import java.util.Collection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.model.Visit;
import org.springframework.samples.petclinic.repository.PetTypeRepository;
import org.springframework.samples.petclinic.repository.SpecialtyRepository;
import org.springframework.samples.petclinic.repository.VetRepository;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.samples.petclinic.service.ClinicServiceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * PQT-7: edge cases of the JDBC pet repository against a real (embedded) database whose {@code pets.owner_id}
 * column is nullable, as in the PostgreSQL schema.
 */
class JdbcPetRepositoryImplTests {

    private EmbeddedDatabase database;

    private JdbcPetRepositoryImpl petRepository;

    @BeforeEach
    void setUp() {
        this.database = new EmbeddedDatabaseBuilder()
            .generateUniqueName(true)
            .setType(EmbeddedDatabaseType.H2)
            .addScript("db/jdbc-pet-edge-cases/schema.sql")
            .addScript("db/jdbc-pet-edge-cases/data.sql")
            .build();
        this.petRepository = new JdbcPetRepositoryImpl(this.database, new JdbcOwnerRepositoryImpl(this.database));
    }

    @AfterEach
    void tearDown() {
        this.database.shutdown();
    }

    @Test
    @DisplayName("PQT-7 | Pet com dono e carregado com dono e tipo")
    void findsPetWithItsOwnerAndType() {
        Pet pet = this.petRepository.findById(1);

        assertThat(pet.getName()).isEqualTo("Leo");
        assertThat(pet.getOwner().getLastName()).isEqualTo("Franklin");
        assertThat(pet.getType().getName()).isEqualTo("cat");
    }

    @Test
    @DisplayName("PQT-7 | Pet inexistente gera ObjectRetrievalFailureException (sem NPE)")
    void unknownPetRaisesObjectRetrievalFailure() {
        assertThatThrownBy(() -> this.petRepository.findById(999))
            .isInstanceOf(ObjectRetrievalFailureException.class);
    }

    @Test
    @DisplayName("PQT-7 | owner_id NULL: pet e retornado sem dono, com tipo e visitas, em vez de NullPointerException")
    void findsPetWithoutOwner() {
        Pet pet = this.petRepository.findById(2);

        assertThat(pet.getName()).isEqualTo("Stray");
        assertThat(pet.getOwner()).isNull();
        assertThat(pet.getType().getName()).isEqualTo("dog");
        assertThat(pet.getVisits()).extracting(Visit::getDescription).containsExactly("rabies shot");
    }

    @Test
    @DisplayName("PQT-7 | Atualizar pet sem dono grava owner_id NULL sem NullPointerException")
    void updatesPetWithoutOwner() {
        Pet pet = this.petRepository.findById(2);
        pet.setName("Lucky");

        this.petRepository.save(pet);

        Pet reloaded = this.petRepository.findById(2);
        assertThat(reloaded.getName()).isEqualTo("Lucky");
        assertThat(reloaded.getOwner()).isNull();
    }

    @Test
    @DisplayName("PQT-7 | Inserir pet sem dono gera id e owner_id NULL")
    void insertsPetWithoutOwner() {
        PetType dog = new PetType();
        dog.setId(2);
        Pet pet = new Pet();
        pet.setName("Bolt");
        pet.setBirthDate(LocalDate.of(2020, 1, 15));
        pet.setType(dog);

        this.petRepository.save(pet);

        assertThat(pet.getId()).isNotNull();
        assertThat(this.petRepository.findById(pet.getId()).getOwner()).isNull();
    }

    @Test
    @DisplayName("PQT-7 | Listagem completa tolera pet sem dono")
    void findAllToleratesPetsWithoutOwner() {
        Collection<Pet> pets = this.petRepository.findAll();

        assertThat(pets).hasSize(2);
        assertThat(pets).filteredOn(p -> "Stray".equals(p.getName())).singleElement()
            .satisfies(stray -> assertThat(stray.getOwner()).isNull());
        assertThat(pets).filteredOn(p -> "Leo".equals(p.getName())).singleElement()
            .satisfies(leo -> assertThat(leo.getOwner().getLastName()).isEqualTo("Franklin"));
    }

    @Test
    @DisplayName("PQT-7 | Listagem paginada tolera pet sem dono e informa o total")
    void pagedFindAllToleratesPetsWithoutOwner() {
        Page<Pet> page = this.petRepository.findAll(PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Pet::getName).containsExactly("Leo", "Stray");
    }

    @Test
    @DisplayName("PQT-7 | Visitas de pet sem dono sao carregadas sem falhar")
    void visitsOfPetsWithoutOwnerAreLoaded() {
        JdbcVisitRepositoryImpl visitRepository = new JdbcVisitRepositoryImpl(this.database);

        Visit visit = visitRepository.findById(1);

        assertThat(visit.getPet().getName()).isEqualTo("Stray");
        assertThat(visit.getPet().getOwner()).isNull();
        assertThat(visit.getDate()).isEqualTo(LocalDate.of(2013, 1, 1));
        assertThat(visitRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("PQT-7 | ClinicService converte pet inexistente em null (resposta 404 na API)")
    void clinicServiceReturnsNullForUnknownPet() {
        ClinicService clinicService = new ClinicServiceImpl(this.petRepository, mock(VetRepository.class),
            new JdbcOwnerRepositoryImpl(this.database), new JdbcVisitRepositoryImpl(this.database),
            mock(SpecialtyRepository.class), mock(PetTypeRepository.class));

        assertThat(clinicService.findPetById(999)).isNull();
        assertThat(clinicService.findPetById(2).getOwner()).isNull();
    }
}
