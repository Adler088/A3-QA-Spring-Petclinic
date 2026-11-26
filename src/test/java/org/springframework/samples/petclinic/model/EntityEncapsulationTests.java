package org.springframework.samples.petclinic.model;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.samples.petclinic.mapper.UserMapper;
import org.springframework.samples.petclinic.rest.dto.RoleDto;
import org.springframework.samples.petclinic.rest.dto.UserDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PQT-5 (defensive copies, SpotBugs EI_EXPOSE_REP/EI_EXPOSE_REP2) and DM_CONVERT_CASE regressions on entities.
 */
class EntityEncapsulationTests {

    private static Role role(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }

    @Test
    @DisplayName("PQT-5 | getRoles devolve visao somente leitura")
    void rolesViewIsReadOnly() {
        User user = new User();
        user.addRole("OWNER_ADMIN");

        Set<Role> roles = user.getRoles();

        assertThatThrownBy(() -> roles.add(role("ADMIN"))).isInstanceOf(UnsupportedOperationException.class);
        assertThat(user.getRoles()).hasSize(1);
    }

    @Test
    @DisplayName("PQT-5 | setRoles guarda copia: alterar o conjunto original nao altera o usuario")
    void setRolesStoresADefensiveCopy() {
        Set<Role> external = new HashSet<>();
        external.add(role("OWNER_ADMIN"));
        User user = new User();

        user.setRoles(external);
        external.add(role("ADMIN"));

        assertThat(user.getRoles()).extracting(Role::getName).containsExactly("OWNER_ADMIN");
    }

    @Test
    @DisplayName("PQT-5 | Usuario sem papeis continua retornando null (regra do UserService)")
    void rolesStayNullWhenNotAssigned() {
        User user = new User();
        user.setRoles(null);

        assertThat(user.getRoles()).isNull();
    }

    @Test
    @DisplayName("PQT-5 | Elementos continuam mutaveis para o UserService prefixar ROLE_ e associar o usuario")
    void rolesElementsRemainUpdatable() {
        User user = new User();
        user.addRole("VET_ADMIN");

        for (Role role : user.getRoles()) {
            role.setName("ROLE_" + role.getName());
            role.setUser(user);
        }

        Role stored = user.getRoles().iterator().next();
        assertThat(stored.getName()).isEqualTo("ROLE_VET_ADMIN");
        assertThat(stored.getUser()).isSameAs(user);
    }

    @Test
    @DisplayName("PQT-5 | MapStruct continua mapeando papeis nos dois sentidos")
    void mapStructStillMapsRoles() {
        UserMapper mapper = Mappers.getMapper(UserMapper.class);
        UserDto dto = new UserDto().username("john").password("secret").enabled(true)
            .roles(List.of(new RoleDto().name("OWNER_ADMIN")));

        User user = mapper.toUser(dto);
        UserDto back = mapper.toUserDto(user);

        assertThat(user.getRoles()).extracting(Role::getName).containsExactly("OWNER_ADMIN");
        assertThat(back.getRoles()).extracting(RoleDto::getName).containsExactly("OWNER_ADMIN");
    }

    @Test
    @DisplayName("DM_CONVERT_CASE | Busca de pet por nome independe do Locale padrao")
    void petLookupByNameDoesNotDependOnDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            Owner owner = new Owner();
            Pet pet = new Pet();
            pet.setName("TITAN");
            owner.addPet(pet);

            assertThat(owner.getPet("titan")).isSameAs(pet);
            assertThat(owner.getPet("Titan", false)).isSameAs(pet);
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("Buscas de pet com argumentos nulos ou pets sem id nao lancam excecao")
    void petLookupsAreNullSafe() {
        Owner owner = new Owner();
        Pet newPet = new Pet();
        newPet.setName("Rex");
        owner.addPet(newPet);

        assertThat(owner.getPet((String) null)).isNull();
        assertThat(owner.getPet((Integer) null)).isNull();
        assertThat(owner.getPet(42)).isNull();
        assertThat(owner.getPet("Rex", true)).isNull();
    }
}
