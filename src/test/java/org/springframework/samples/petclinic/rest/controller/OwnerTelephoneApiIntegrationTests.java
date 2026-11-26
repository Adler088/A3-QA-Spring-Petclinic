package org.springframework.samples.petclinic.rest.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end regression tests (MVC + bean validation + JPA + H2/HSQLDB) for the owner telephone.
 * <ul>
 *     <li>PQT-1: telephones with 11 digits (mobile with area code) are accepted, persisted and returned.</li>
 *     <li>PQT-4: invalid telephones are rejected with HTTP 400 and field details, never HTTP 500.</li>
 * </ul>
 * Each test runs in a rolled back transaction.
 */
@SpringBootTest
@Transactional
class OwnerTelephoneApiIntegrationTests {

    private static final String OWNERS = "/api/owners";
    private static final String GENERIC_DETAIL = "The request contains invalid or missing parameters";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
    }

    private static RequestPostProcessor ownerAdmin() {
        return user("owner-admin").roles("OWNER_ADMIN");
    }

    /** Owner payload; a {@code null} telephone is sent as JSON {@code null}. */
    private static String ownerJson(String telephone) {
        Map<String, Object> owner = new LinkedHashMap<>();
        owner.put("firstName", "Maria");
        owner.put("lastName", "Souza");
        owner.put("address", "Rua das Flores, 123");
        owner.put("city", "Sao Paulo");
        owner.put("telephone", telephone);
        return JSON.writeValueAsString(owner);
    }

    static Stream<Arguments> invalidTelephones() {
        return Stream.of(
            Arguments.of("119876543", "9 digitos"),
            Arguments.of("119876543210", "12 digitos"),
            Arguments.of("11987654321000000000", "20 digitos (aceito pelo contrato antigo)"),
            Arguments.of("11987654AB", "letras"),
            Arguments.of("(11)98765-4321", "mascara com parenteses e hifen"),
            Arguments.of("+5511987654321", "prefixo internacional"),
            Arguments.of("11 98765 4321", "espacos"),
            Arguments.of("1198765432\n", "quebra de linha ao final"),
            Arguments.of("\uFF11\uFF11\uFF19\uFF18\uFF17\uFF16\uFF15\uFF14\uFF13\uFF12\uFF11", "digitos Unicode de largura total (U+FF10-U+FF19)"),
            Arguments.of("", "vazio"),
            Arguments.of("          ", "somente espacos"),
            Arguments.of(null, "nulo")
        );
    }

    @Test
    @DisplayName("PQT-1 | POST com telefone de 11 digitos retorna 201 e o numero e persistido")
    void shouldCreateOwnerWithElevenDigitTelephone() throws Exception {
        MvcResult result = this.mockMvc.perform(post(OWNERS).with(ownerAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ownerJson("11987654321")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.telephone").value("11987654321"))
            .andReturn();
        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        this.mockMvc.perform(get(OWNERS + "/" + id).with(ownerAdmin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.telephone").value("11987654321"));
    }

    @ParameterizedTest(name = "PQT-1 [{index}] telefone valido {0} -> 201")
    @ValueSource(strings = {"1132654321", "11987654321", "6085551023"})
    void shouldAcceptTenAndElevenDigitTelephones(String telephone) throws Exception {
        this.mockMvc.perform(post(OWNERS).with(ownerAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ownerJson(telephone)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.telephone").value(telephone));
    }

    @Test
    @DisplayName("PQT-1 | PUT com telefone de 11 digitos retorna 204 e atualiza o cadastro")
    void shouldUpdateOwnerWithElevenDigitTelephone() throws Exception {
        this.mockMvc.perform(put(OWNERS + "/1").with(ownerAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ownerJson("11987654321")))
            .andExpect(status().isNoContent());

        this.mockMvc.perform(get(OWNERS + "/1").with(ownerAdmin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.telephone").value("11987654321"));
    }

    @ParameterizedTest(name = "PQT-4 [{index}] POST com telefone invalido ({1}) -> 400")
    @MethodSource("invalidTelephones")
    void shouldRejectInvalidTelephoneOnCreateWithBadRequest(String telephone, String scenario) throws Exception {
        this.mockMvc.perform(post(OWNERS).with(ownerAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ownerJson(telephone)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.title").value("MethodArgumentNotValidException"))
            .andExpect(jsonPath("$.detail").value(GENERIC_DETAIL))
            .andExpect(jsonPath("$.schemaValidationErrors[*].field", hasItem("telephone")));
    }

    @ParameterizedTest(name = "PQT-4 [{index}] PUT com telefone invalido ({1}) -> 400 e cadastro inalterado")
    @MethodSource("invalidTelephones")
    void shouldRejectInvalidTelephoneOnUpdateWithBadRequest(String telephone, String scenario) throws Exception {
        this.mockMvc.perform(put(OWNERS + "/1").with(ownerAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ownerJson(telephone)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(GENERIC_DETAIL))
            .andExpect(jsonPath("$.schemaValidationErrors[*].field", hasItem("telephone")));

        this.mockMvc.perform(get(OWNERS + "/1").with(ownerAdmin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.telephone").value("6085551023"));
    }

    @Test
    @DisplayName("PQT-4 | Erro de telefone informa campo, valor rejeitado e regra violada")
    void shouldDescribeTheTelephoneViolation() throws Exception {
        this.mockMvc.perform(post(OWNERS).with(ownerAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ownerJson("11987654AB")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.schemaValidationErrors[0].field").value("telephone"))
            .andExpect(jsonPath("$.schemaValidationErrors[0].rejectedValue").value("11987654AB"))
            .andExpect(jsonPath("$.schemaValidationErrors[0].defaultMessage")
                .value("must contain only digits: 10 or 11 digits including the area code"));
    }

    @Test
    @DisplayName("PQT-4 | Corpo JSON malformado retorna 400")
    void shouldRejectMalformedJsonWithBadRequest() throws Exception {
        this.mockMvc.perform(post(OWNERS).with(ownerAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"telephone\": "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(GENERIC_DETAIL));
    }
}
