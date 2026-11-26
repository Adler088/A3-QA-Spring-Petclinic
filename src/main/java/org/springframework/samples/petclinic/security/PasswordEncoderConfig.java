package org.springframework.samples.petclinic.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Single, unconditional source of the {@link PasswordEncoder} bean.
 * <p> The encoder used to be declared inside {@link BasicAuthenticationConfig},
 * which is only active when {@code petclinic.security.enable=true}. With
 * security disabled the context could not start, because
 * {@code UserServiceImpl} always needs an encoder to hash new passwords.
 * Keeping the bean here also avoids duplicate {@code passwordEncoder}
 * definitions in the security configurations.
 */
@Configuration(proxyBeanMethods = false)
public class PasswordEncoderConfig {

    /**
     * BCrypt matches the hashes stored by {@code UserServiceImpl} and the seed
     * users of {@code data.sql}.
     *
     * @return the application password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
