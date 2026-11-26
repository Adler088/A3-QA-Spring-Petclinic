package org.springframework.samples.petclinic.rest.controller.v1;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.model.Role;
import org.springframework.samples.petclinic.model.User;
import org.springframework.samples.petclinic.security.Roles;
import org.springframework.samples.petclinic.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/auth")
public class StaffAuthController {

    private static final String SQL_LIST_USERS =
        "SELECT u.username, u.enabled, r.role FROM users u LEFT JOIN roles r ON u.username = r.username ORDER BY u.username";
    private static final String SQL_COUNT_USER =
        "SELECT COUNT(*) FROM users WHERE username = ?";
    private static final String SQL_DELETE_ROLES =
        "DELETE FROM roles WHERE username = ?";
    private static final String SQL_DELETE_USER =
        "DELETE FROM users WHERE username = ?";

    private final UserService userService;
    private final JdbcTemplate jdbcTemplate;

    public StaffAuthController(UserService userService, DataSource dataSource) {
        this.userService = userService;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @GetMapping("/me")
    public ResponseEntity<Object> currentUser(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new DetailResponse("Usuário não autenticado"));
        }
        String username = authentication.getName();
        List<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .sorted()
            .toList();

        return ResponseEntity.ok(new StaffUserDto(username, resolveProfile(roles), roles));
    }

    @PreAuthorize("hasRole(@roles.ADMIN)")
    @GetMapping("/users")
    public ResponseEntity<List<StaffUserDto>> listStaff() {
        Map<String, List<String>> rolesByUser = new LinkedHashMap<>();
        jdbcTemplate.query(SQL_LIST_USERS, rs -> {
            String username = rs.getString("username");
            String role = rs.getString("role");
            rolesByUser.computeIfAbsent(username, k -> new ArrayList<>());
            if (role != null) {
                rolesByUser.get(username).add(role);
            }
        });

        List<StaffUserDto> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : rolesByUser.entrySet()) {
            List<String> userRoles = entry.getValue();
            result.add(new StaffUserDto(entry.getKey(), resolveProfile(userRoles), userRoles));
        }
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasRole(@roles.ADMIN)")
    @PostMapping("/users")
    public ResponseEntity<Object> createStaff(@RequestBody CreateStaffRequest request) {
        if (request == null || request.username() == null || !request.username().trim().matches("^[a-zA-Z0-9._-]{3,20}$")) {
            return ResponseEntity.badRequest()
                .body(new DetailResponse("O usuário deve ter entre 3 e 20 caracteres (letras, números, ponto ou hífen)."));
        }
        if (request.password() == null || request.password().trim().length() < 4) {
            return ResponseEntity.badRequest()
                .body(new DetailResponse("A senha deve ter pelo menos 4 caracteres."));
        }

        String cleanUsername = request.username().trim().toLowerCase(Locale.ROOT);
        Integer existing = jdbcTemplate.queryForObject(SQL_COUNT_USER, Integer.class, cleanUsername);
        if (existing != null && existing > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new DetailResponse("Já existe um colaborador cadastrado com este usuário."));
        }

        String profile = request.profile() == null ? "RECEPCIONISTA" : request.profile().trim().toUpperCase(Locale.ROOT);
        User user = new User();
        user.setUsername(cleanUsername);
        user.setPassword(request.password().trim());
        user.setEnabled(true);

        Set<Role> roles = new HashSet<>();
        if ("ADMIN".equals(profile)) {
            roles.add(buildRole(user, Roles.ADMIN));
            roles.add(buildRole(user, Roles.VET_ADMIN));
            roles.add(buildRole(user, Roles.OWNER_ADMIN));
        } else if ("VETERINARIO".equals(profile)) {
            roles.add(buildRole(user, Roles.VET_ADMIN));
            roles.add(buildRole(user, Roles.OWNER_ADMIN));
        } else if ("RECEPCIONISTA".equals(profile)) {
            roles.add(buildRole(user, Roles.OWNER_ADMIN));
        } else {
            return ResponseEntity.badRequest()
                .body(new DetailResponse("Perfil inválido. Use ADMIN, VETERINARIO ou RECEPCIONISTA."));
        }

        user.setRoles(roles);
        userService.saveUser(user);

        List<String> roleNames = roles.stream().map(Role::getName).sorted().toList();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new StaffUserDto(cleanUsername, profile, roleNames));
    }

    @PreAuthorize("hasRole(@roles.ADMIN)")
    @DeleteMapping("/users/{username}")
    public ResponseEntity<Object> deleteStaff(@PathVariable String username, Authentication authentication) {
        String target = username.trim();
        if ("admin".equalsIgnoreCase(target) || (authentication != null && authentication.getName().equalsIgnoreCase(target))) {
            return ResponseEntity.badRequest()
                .body(new DetailResponse("Não é permitido remover o administrador principal ou o usuário logado."));
        }
        jdbcTemplate.update(SQL_DELETE_ROLES, target);
        int deleted = jdbcTemplate.update(SQL_DELETE_USER, target);
        if (deleted == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new DetailResponse("Usuário não encontrado."));
        }
        return ResponseEntity.noContent().build();
    }

    private static Role buildRole(User user, String roleName) {
        Role role = new Role();
        role.setUser(user);
        role.setName(roleName);
        return role;
    }

    private static String resolveProfile(List<String> roles) {
        if (roles.contains(Roles.ADMIN)) {
            return "ADMIN";
        }
        if (roles.contains(Roles.VET_ADMIN)) {
            return "VETERINARIO";
        }
        return "RECEPCIONISTA";
    }

    public record CreateStaffRequest(String username, String password, String profile) {
    }

    public record StaffUserDto(String username, String profile, List<String> roles) {
    }

    public record DetailResponse(String detail) {
    }
}
