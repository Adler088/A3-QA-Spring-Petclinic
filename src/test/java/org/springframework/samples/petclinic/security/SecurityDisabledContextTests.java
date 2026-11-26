package org.springframework.samples.petclinic.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.samples.petclinic.model.User;
import org.springframework.samples.petclinic.service.UserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Context regression: with {@code petclinic.security.enable=false} the application did not start, because the
 * only {@link PasswordEncoder} bean was declared inside {@link BasicAuthenticationConfig} (conditional) while
 * {@code UserServiceImpl} always requires one.
 */
@SpringBootTest(properties = "petclinic.security.enable=false")
class SecurityDisabledContextTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserService userService;

    @Test
    @DisplayName("Contexto sobe sem seguranca, com um unico PasswordEncoder BCrypt")
    void contextStartsWithASinglePasswordEncoder() {
        assertThat(this.applicationContext.getBeansOfType(PasswordEncoder.class)).hasSize(1);
        assertThat(this.passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(this.applicationContext.getBeansOfType(BasicAuthenticationConfig.class)).isEmpty();
        assertThat(this.applicationContext.getBeansOfType(DisableSecurityConfig.class)).hasSize(1);
    }

    @Test
    @DisplayName("Com a seguranca desabilitada a API responde sem credenciais")
    void endpointsAreOpenWhenSecurityIsDisabled() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).apply(springSecurity()).build();
        mockMvc.perform(get("/api/owners")).andExpect(status().isOk());
    }

    @Test
    @Transactional
    @DisplayName("UserService continua gravando a senha com hash BCrypt")
    void userServiceStillEncodesPasswords() {
        User user = new User();
        user.setUsername("nosec-user");
        user.setPassword("s3cr3t");
        user.setEnabled(true);
        user.addRole("OWNER_ADMIN");

        this.userService.saveUser(user);

        assertThat(user.getPassword()).startsWith("$2a$");
        assertThat(this.passwordEncoder.matches("s3cr3t", user.getPassword())).isTrue();
    }
}
