package org.springframework.samples.petclinic.rest.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack HTTP status tests (security filter chain + Spring MVC + {@code ExceptionControllerAdvice}).
 * <ul>
 *     <li>PQT-2: the Swagger redirect only answers GET; other methods get HTTP 405 with an {@code Allow} header.</li>
 *     <li>Client errors produced by Spring MVC or Spring Security keep their 4xx status instead of HTTP 500.</li>
 *     <li>PQT-9: the BCrypt hash of the seed data still authenticates the documented demo user.</li>
 * </ul>
 */
@SpringBootTest
class ApiHttpStatusIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
    }

    private static RequestPostProcessor admin() {
        return user("admin").roles("OWNER_ADMIN", "VET_ADMIN", "ADMIN");
    }

    @Test
    @DisplayName("PQT-2 | GET na raiz redireciona para o Swagger UI")
    void getRootRedirectsToSwaggerUi() throws Exception {
        this.mockMvc.perform(get("/").with(admin()))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }

    @ParameterizedTest(name = "PQT-2 [{index}] {0} na raiz -> 405 com Allow: GET")
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void unsafeMethodsOnRootAreNotAllowed(String method) throws Exception {
        this.mockMvc.perform(request(HttpMethod.valueOf(method), "/").with(admin()))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
            .andExpect(jsonPath("$.status").value(405))
            .andExpect(jsonPath("$.title").value("HttpRequestMethodNotSupportedException"))
            .andExpect(jsonPath("$.detail").value("The HTTP method is not supported for the requested resource"));
    }

    @Test
    @DisplayName("Metodo nao mapeado em recurso da API retorna 405, nao 500")
    void unsupportedMethodOnApiResourceReturns405() throws Exception {
        this.mockMvc.perform(request(HttpMethod.DELETE, "/api/owners").with(admin()))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")));
    }

    @Test
    @DisplayName("Recurso inexistente retorna 404, nao 500")
    void unknownResourceReturns404() throws Exception {
        this.mockMvc.perform(get("/api/does-not-exist").with(admin()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("The requested resource was not found"));
    }

    @Test
    @DisplayName("Content-Type nao suportado retorna 415, nao 500")
    void unsupportedMediaTypeReturns415() throws Exception {
        this.mockMvc.perform(post("/api/owners").with(admin())
                .contentType(MediaType.TEXT_PLAIN)
                .content("telephone=11987654321"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    @DisplayName("Parametro de caminho invalido retorna 400 com o parametro violado")
    void invalidPathVariableReturns400WithDetails() throws Exception {
        this.mockMvc.perform(get("/api/owners/-1").with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.schemaValidationErrors[0].field").value("ownerId"))
            .andExpect(jsonPath("$.schemaValidationErrors[0].rejectedValue").value("-1"));
    }

    @Test
    @DisplayName("Parametro de paginacao fora do limite retorna 400")
    void invalidPageSizeReturns400() throws Exception {
        this.mockMvc.perform(get("/api/v2/owners").param("size", "500").with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.schemaValidationErrors[0].field").value("size"));
    }

    @Test
    @DisplayName("Usuario sem o papel exigido recebe 403, nao 500")
    void missingRoleReturns403() throws Exception {
        this.mockMvc.perform(get("/api/owners").with(user("vet").roles("VET_ADMIN")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.detail").value("Access denied"));
    }

    @Test
    @DisplayName("Requisicao sem credenciais recebe 401 em JSON")
    void anonymousRequestReturns401() throws Exception {
        this.mockMvc.perform(get("/api/owners"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    @DisplayName("Senha incorreta recebe 401")
    void wrongPasswordReturns401() throws Exception {
        this.mockMvc.perform(get("/api/owners").with(httpBasic("admin", "wrong-password")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PQT-9 | Hash BCrypt do seed autentica o usuario de demonstracao admin/admin")
    void seedAdminCredentialAuthenticates() throws Exception {
        this.mockMvc.perform(get("/api/owners").with(httpBasic("admin", "admin")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Preflight CORS do front-end Angular e aceito")
    void corsPreflightFromAngularIsAccepted() throws Exception {
        this.mockMvc.perform(options("/api/owners")
                .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
    }
}
