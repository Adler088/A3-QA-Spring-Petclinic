package org.springframework.samples.petclinic.security;

import org.springframework.stereotype.Component;

/**
 * Role names used by the {@code @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")}
 * expressions.
 * <p> The values are true constants ({@code static final}, SpotBugs
 * {@code SS_SHOULD_BE_STATIC}). The class is still registered as the
 * {@code roles} bean because SpEL reads public static fields through the bean
 * instance, so the existing security expressions keep working unchanged.
 */
@Component
public class Roles {

    public static final String OWNER_ADMIN = "ROLE_OWNER_ADMIN";
    public static final String VET_ADMIN = "ROLE_VET_ADMIN";
    public static final String ADMIN = "ROLE_ADMIN";
}
