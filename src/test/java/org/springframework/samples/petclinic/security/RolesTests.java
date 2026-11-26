package org.springframework.samples.petclinic.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PQT-8: the role names became {@code static final} constants (SpotBugs SS_SHOULD_BE_STATIC); the SpEL
 * expressions of {@code @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")} must keep resolving them through the bean.
 */
class RolesTests {

    private static Object evaluate(String expression) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("roles", new Roles());
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setBeanResolver(new BeanFactoryResolver(beanFactory));
        return new SpelExpressionParser().parseExpression(expression).getValue(context);
    }

    @Test
    @DisplayName("PQT-8 | Expressoes SpEL @roles.* continuam resolvendo as constantes")
    void spelExpressionsResolveStaticConstantsThroughTheBean() {
        assertThat(evaluate("@roles.OWNER_ADMIN")).isEqualTo("ROLE_OWNER_ADMIN");
        assertThat(evaluate("@roles.VET_ADMIN")).isEqualTo("ROLE_VET_ADMIN");
        assertThat(evaluate("@roles.ADMIN")).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("PQT-8 | Constantes podem ser usadas diretamente em codigo Java")
    void constantsAreStatic() {
        assertThat(Roles.OWNER_ADMIN).isEqualTo("ROLE_OWNER_ADMIN");
        assertThat(Roles.VET_ADMIN).isEqualTo("ROLE_VET_ADMIN");
        assertThat(Roles.ADMIN).isEqualTo("ROLE_ADMIN");
    }
}
