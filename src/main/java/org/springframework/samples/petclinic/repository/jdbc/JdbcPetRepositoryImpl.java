/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.repository.jdbc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.model.Visit;
import org.springframework.samples.petclinic.repository.OwnerRepository;
import org.springframework.samples.petclinic.repository.PetRepository;
import org.springframework.samples.petclinic.util.EntityUtils;
import org.springframework.stereotype.Repository;

/**
 * @author Ken Krebs
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Sam Brannen
 * @author Thomas Risberg
 * @author Mark Fisher
 * @author Vitaliy Fedoriv
 */
@DependsOnDatabaseInitialization
@Repository
@Profile("jdbc")
public class JdbcPetRepositoryImpl implements PetRepository {

    /** Loads one pet and its visits, whatever the value of owner_id. */
    private static final String SELECT_PET_WITH_VISITS =
        "SELECT pets.id as pets_id, name, birth_date, type_id, owner_id, "
            + "visits.id as visit_id, visit_date, description, "
            + "visits.pet_id as visits_pet_id "
            + "FROM pets LEFT OUTER JOIN visits ON pets.id = visits.pet_id "
            + "WHERE pets.id=:id";

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final SimpleJdbcInsert insertPet;

    private final OwnerRepository ownerRepository;

    public JdbcPetRepositoryImpl(DataSource dataSource,
                                 OwnerRepository ownerRepository) {
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);

        this.insertPet = new SimpleJdbcInsert(dataSource)
            .withTableName("pets")
            .usingGeneratedKeyColumns("id");

        this.ownerRepository = ownerRepository;
    }

    @Override
    public List<PetType> findPetTypes() throws DataAccessException {
        Map<String, Object> params = new HashMap<>();
        return this.namedParameterJdbcTemplate.query(
            "SELECT id, name FROM types ORDER BY name",
            params,
            BeanPropertyRowMapper.newInstance(PetType.class));
    }

    /**
     * Loads a pet through its owner, so the owner, the type and the visits are
     * populated.
     * <p> {@code queryForObject} returns {@code null} when {@code owner_id} is
     * NULL, which the PostgreSQL schema allows. Unboxing that value used to
     * throw a {@link NullPointerException} (SpotBugs
     * {@code NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE}); such a pet is now loaded
     * on its own, without an owner, as the JPA implementations do.
     *
     * @param id the pet id
     * @return the pet, with a {@code null} owner when owner_id is NULL
     * @throws ObjectRetrievalFailureException when no pet has the given id
     */
    @Override
    public Pet findById(int id) throws DataAccessException {
        Map<String, Object> params = Map.of("id", id);
        Integer ownerId;
        try {
            ownerId = this.namedParameterJdbcTemplate.queryForObject(
                "SELECT owner_id FROM pets WHERE id=:id", params,
                Integer.class);
        } catch (EmptyResultDataAccessException ex) {
            throw new ObjectRetrievalFailureException(Pet.class, id);
        }
        if (ownerId == null) {
            return findPetWithoutOwner(id, params);
        }
        Owner owner = this.ownerRepository.findById(ownerId);
        return EntityUtils.getById(owner.getPets(), Pet.class, id);
    }

    private Pet findPetWithoutOwner(int id, Map<String, Object> params) {
        List<JdbcPet> pets = this.namedParameterJdbcTemplate.query(
            SELECT_PET_WITH_VISITS, params, new JdbcPetVisitExtractor());
        if (pets == null || pets.isEmpty()) {
            throw new ObjectRetrievalFailureException(Pet.class, id);
        }
        JdbcPet pet = pets.get(0);
        pet.setType(EntityUtils.getById(
            findPetTypes(), PetType.class, pet.getTypeId()));
        return pet;
    }

    @Override
    public void save(Pet pet) throws DataAccessException {
        if (pet.isNew()) {
            Number newKey = this.insertPet.executeAndReturnKey(
                createPetParameterSource(pet));
            pet.setId(newKey.intValue());
        } else {
            this.namedParameterJdbcTemplate.update(
                "UPDATE pets SET name=:name, birth_date=:birth_date, type_id=:type_id, "
                    + "owner_id=:owner_id WHERE id=:id",
                createPetParameterSource(pet));
        }
    }

    /**
     * Creates a {@link MapSqlParameterSource} based on data values from the
     * supplied {@link Pet} instance. A pet without owner or type is mapped to
     * NULL columns instead of failing with a {@link NullPointerException}.
     *
     * @param pet the pet to save
     * @return the SQL parameters
     */
    private MapSqlParameterSource createPetParameterSource(Pet pet) {
        return new MapSqlParameterSource()
            .addValue("id", pet.getId())
            .addValue("name", pet.getName())
            .addValue("birth_date", pet.getBirthDate())
            .addValue("type_id",
                pet.getType() == null ? null : pet.getType().getId())
            .addValue("owner_id",
                pet.getOwner() == null ? null : pet.getOwner().getId());
    }

    @Override
    public Collection<Pet> findAll() throws DataAccessException {
        Map<String, Object> params = new HashMap<>();
        Collection<Pet> pets = new ArrayList<>();
        Collection<JdbcPet> jdbcPets;
        jdbcPets = this.namedParameterJdbcTemplate
            .query("SELECT pets.id as pets_id, name, birth_date, type_id, owner_id FROM pets",
                params,
                new JdbcPetRowMapper());
        Collection<PetType> petTypes = this.namedParameterJdbcTemplate.query("SELECT id, name FROM types ORDER BY name",
            new HashMap<String,
                Object>(), BeanPropertyRowMapper.newInstance(PetType.class));
        Collection<Owner> owners = this.namedParameterJdbcTemplate.query(
            "SELECT id, first_name, last_name, address, city, telephone FROM owners ORDER BY last_name",
            new HashMap<String, Object>(),
            BeanPropertyRowMapper.newInstance(Owner.class));
        for (JdbcPet jdbcPet : jdbcPets) {
            jdbcPet.setType(EntityUtils.getById(petTypes, PetType.class, jdbcPet.getTypeId()));
            jdbcPet.setOwner(findOwnerOf(jdbcPet, owners));
            // TODO add visits
            pets.add(jdbcPet);
        }
        return pets;
    }

    @Override
    public Page<Pet> findAll(@NonNull Pageable pageable) throws DataAccessException {
        Map<String, Object> params = new HashMap<>();
        params.put("size", pageable.getPageSize());
        params.put("offset", pageable.getOffset());
        List<JdbcPet> jdbcPets = this.namedParameterJdbcTemplate.query(
            "SELECT pets.id as pets_id, name, birth_date, type_id, owner_id FROM pets ORDER BY id LIMIT :size OFFSET :offset",
            params,
            new JdbcPetRowMapper());
        Collection<PetType> petTypes = this.namedParameterJdbcTemplate.query(
            "SELECT id, name FROM types ORDER BY name",
            new HashMap<String, Object>(),
            BeanPropertyRowMapper.newInstance(PetType.class));

        Collection<Owner> owners = this.namedParameterJdbcTemplate.query(
            "SELECT id, first_name, last_name, address, city, telephone FROM owners ORDER BY last_name",
            new HashMap<String, Object>(),
            BeanPropertyRowMapper.newInstance(Owner.class));

        for (JdbcPet jdbcPet : jdbcPets) {
            jdbcPet.setType(EntityUtils.getById(petTypes, PetType.class, jdbcPet.getTypeId()));
            jdbcPet.setOwner(findOwnerOf(jdbcPet, owners));
        }

        Long total = this.namedParameterJdbcTemplate.queryForObject(
            "SELECT count(*) FROM pets",
            params,
            Long.class);
        return new PageImpl<>(new ArrayList<>(jdbcPets), pageable, total == null ? 0 : total);
    }

    /**
     * Owner of a pet listed by {@link #findAll()}, or {@code null} for a pet
     * whose {@code owner_id} is NULL (mapped to {@code 0} by
     * {@link JdbcPetRowMapper}); {@link EntityUtils#getById} would throw in
     * that case.
     *
     * @param pet the listed pet
     * @param owners the owners loaded for the listing
     * @return the owner of the pet, or {@code null}
     */
    private static Owner findOwnerOf(JdbcPet pet, Collection<Owner> owners) {
        for (Owner owner : owners) {
            if (Objects.equals(owner.getId(), pet.getOwnerId())) {
                return owner;
            }
        }
        return null;
    }

    @Override
    public void delete(Pet pet) throws DataAccessException {
        Map<String, Object> petParams = new HashMap<>();
        petParams.put("id", pet.getId());
        List<Visit> visits = pet.getVisits();
        // cascade delete visits
        for (Visit visit : visits) {
            Map<String, Object> visitParams = new HashMap<>();
            visitParams.put("id", visit.getId());
            this.namedParameterJdbcTemplate.update("DELETE FROM visits WHERE id=:id", visitParams);
        }
        this.namedParameterJdbcTemplate.update("DELETE FROM pets WHERE id=:id", petParams);
    }

}
