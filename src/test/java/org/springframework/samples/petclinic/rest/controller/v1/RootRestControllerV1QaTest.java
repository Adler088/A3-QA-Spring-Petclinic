package org.springframework.samples.petclinic.rest.controller.v1;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RootRestControllerV1QaTest {

    @Test
    void ta05_deveRedirecionarParaSwagger() throws Exception {
        RootRestControllerV1 controller = new RootRestControllerV1();
        ReflectionTestUtils.setField(controller, "servletContextPath", "/petclinic");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.redirectToSwagger(response);

        assertEquals(302, response.getStatus());
        assertEquals("/petclinic/swagger-ui/index.html", response.getRedirectedUrl());
    }

    @Test
    void ta06_deveRestringirRedirecionamentoAoGet() throws Exception {
        Method redirect = RootRestControllerV1.class.getMethod(
                "redirectToSwagger", jakarta.servlet.http.HttpServletResponse.class);

        assertNotNull(redirect.getAnnotation(GetMapping.class),
                "O metodo de redirecionamento deve ser explicitamente GET");
    }
}
