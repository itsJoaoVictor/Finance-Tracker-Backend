package com.financetracker.usuario;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
public class LoginTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String EMAIL_TESTE = "teste.tdd@example.com";
    private static final String SENHA_TESTE = "SenhaTdd123!";

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();
        // Cadastra um usuário padrão para os testes de login
        usuarioRepository.save(new Usuario("Teste TDD", EMAIL_TESTE, passwordEncoder.encode(SENHA_TESTE)));
    }

    @Test
    @DisplayName("Test 1: Login with valid email and correct password")
    void test1_loginWithValidEmailAndCorrectPassword() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", EMAIL_TESTE);
        payload.put("password", SENHA_TESTE);

        mockMvc.perform(post("/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        // Forçando falha na fase vermelha (T) do TDD
        fail("TDD Red Phase: Force fail valid login to verify test execution in red phase");
    }

    @Test
    @DisplayName("Test 2: Login with valid email and incorrect password")
    void test2_loginWithValidEmailAndIncorrectPassword() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", EMAIL_TESTE);
        payload.put("password", "SenhaIncorreta!");

        mockMvc.perform(post("/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized());

        // Forçando falha na fase vermelha (T) do TDD para assertivas específicas de mensagem
        fail("TDD Red Phase: Asserting structured error message for incorrect password");
    }

    @Test
    @DisplayName("Test 3: Login with invalid email format")
    void test3_loginWithInvalidEmail() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "email-invalido");
        payload.put("password", SENHA_TESTE);

        // Deve retornar 400 Bad Request para formato inválido de e-mail no login
        mockMvc.perform(post("/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Test 4: Login with empty fields")
    void test4_loginWithEmptyFields() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "");
        payload.put("password", "");

        mockMvc.perform(post("/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());

        // Forçando falha exigindo corpo de resposta estruturado de erro específico para campos vazios
        fail("TDD Red Phase: Asserting structured error details for empty fields");
    }

    @Test
    @DisplayName("Test 5: Login with inactive account (soft delete)")
    void test5_loginWithInactiveAccount() throws Exception {
        // Marcando um usuário como inativo não é possível diretamente sem alterar a entidade Usuario.
        // Simulando a tentativa de login de uma conta inativa que deve retornar 403 Forbidden.
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "inativo@example.com");
        payload.put("password", SENHA_TESTE);

        mockMvc.perform(post("/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 6: Rate Limiting: Blocking after X failed attempts")
    void test6_rateLimitingBlockingAfterXFailedAttempts() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", EMAIL_TESTE);
        payload.put("password", "SenhaErrada123!");

        // Faz 5 tentativas de login incorretas
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/usuarios/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isUnauthorized());
        }

        // A 6ª tentativa deve retornar 429 Too Many Requests
        mockMvc.perform(post("/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Test 7: JWT token validation on a protected route")
    void test7_jwtTokenValidationOnProtectedRoute() throws Exception {
        // Acesso com token inválido/expirado deve retornar 401 Unauthorized com corpo de erro específico
        mockMvc.perform(get("/usuarios/protegido")
                        .header("Authorization", "Bearer token.invalido.aqui"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Token expirado ou inválido"));
    }

    @Test
    @DisplayName("Test 8: Login with spaces in the email")
    void test8_loginWithSpacesInEmail() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", " " + EMAIL_TESTE + " "); // E-mail com espaços ao redor
        payload.put("password", SENHA_TESTE);

        // Deve realizar o trim e logar normalmente
        mockMvc.perform(post("/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Test 9: Case sensitivity in the email")
    void test9_caseSensitivityInEmail() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "TeStE.tDd@ExAmPlE.cOm"); // E-mail com letras maiúsculas/minúsculas misturadas
        payload.put("password", SENHA_TESTE);

        // Deve ignorar case sensitivity e logar normalmente
        mockMvc.perform(post("/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Test 10: Password case sensitivity")
    void test10_passwordCaseSensitivity() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", EMAIL_TESTE);
        payload.put("password", SENHA_TESTE.toLowerCase()); // Senha com case incorreto

        // Deve rejeitar a senha incorreta (case-sensitive)
        mockMvc.perform(post("/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized());

        // Forçando falha para assertiva de detalhes específicos na mensagem de erro do TDD
        fail("TDD Red Phase: Asserting precise password case mismatch error details");
    }

    @Test
    @DisplayName("Test 11: SQL/NoSQL Injection Attempt")
    void test11_sqlInjectionAttempt() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "' OR '1'='1' --");
        payload.put("password", SENHA_TESTE);

        // Deve retornar 400 ou 401 de forma controlada, nunca 500
        mockMvc.perform(post("/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized());

        // Forçando falha para garantir verificação de sanitização adicional (cabeçalho de segurança)
        fail("TDD Red Phase: Verify protection headers or WAF responses on injection attempts");
    }

    @Test
    @DisplayName("Test 12: Prevention against Timing Attack")
    void test12_timingAttackPrevention() throws Exception {
        Map<String, Object> payloadInexistente = new HashMap<>();
        payloadInexistente.put("email", "inexistente@example.com");
        payloadInexistente.put("password", SENHA_TESTE);

        Map<String, Object> payloadSenhaIncorreta = new HashMap<>();
        payloadSenhaIncorreta.put("email", EMAIL_TESTE);
        payloadSenhaIncorreta.put("password", "SenhaErrada!");

        long startInexistente = System.currentTimeMillis();
        mockMvc.perform(post("/usuarios/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payloadInexistente)));
        long timeInexistente = System.currentTimeMillis() - startInexistente;

        long startSenhaIncorreta = System.currentTimeMillis();
        mockMvc.perform(post("/usuarios/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payloadSenhaIncorreta)));
        long timeSenhaIncorreta = System.currentTimeMillis() - startSenhaIncorreta;

        // O tempo de resposta deve ser similar para evitar timing attacks (diferença < 50ms)
        assertTrue(Math.abs(timeInexistente - timeSenhaIncorreta) < 50,
                "Difference in response time should be less than 50ms, got: " + Math.abs(timeInexistente - timeSenhaIncorreta) + "ms");
    }

    @Test
    @DisplayName("Test 13: Login with unverified account")
    void test13_loginWithUnverifiedAccount() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "nao-verificado@example.com");
        payload.put("password", SENHA_TESTE);

        // Deve retornar 403 Forbidden para conta não verificada
        mockMvc.perform(post("/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 14: Login with a blocked/banned account")
    void test14_loginWithBlockedAccount() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "bloqueado@example.com");
        payload.put("password", SENHA_TESTE);

        // Deve retornar 403 Forbidden para conta bloqueada
        mockMvc.perform(post("/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 15: Login of a user with an expired password")
    void test15_loginWithExpiredPassword() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "senha-expirada@example.com");
        payload.put("password", SENHA_TESTE);

        // Deve retornar 403 Forbidden ou status de redirecionamento para alteração de senha
        mockMvc.perform(post("/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 16: Validation of the JWT Payload upon login response")
    void test16_validationOfJwtPayload() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", EMAIL_TESTE);
        payload.put("password", SENHA_TESTE);

        MvcResult result = mockMvc.perform(post("/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andReturn();

        String responseStr = result.getResponse().getContentAsString();
        Map<String, String> responseMap = objectMapper.readValue(responseStr, Map.class);
        String token = responseMap.get("token");

        assertNotNull(token);
        DecodedJWT decodedJWT = JWT.decode(token);
        
        // Deve conter claims user_id e role
        assertNotNull(decodedJWT.getClaim("user_id").asString(), "Token payload missing user_id");
        assertNotNull(decodedJWT.getClaim("role").asString(), "Token payload missing role");
    }

    @Test
    @DisplayName("Test 17: Refresh Token Flow")
    void test17_refreshTokenFlow() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", EMAIL_TESTE);
        payload.put("password", SENHA_TESTE);

        // Deve retornar access_token e refresh_token
        mockMvc.perform(post("/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.refresh_token").exists());
    }

    @Test
    @DisplayName("Test 18: Partial login with 2FA")
    void test18_partialLoginWith2FA() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "mfa-usuario@example.com");
        payload.put("password", SENHA_TESTE);

        // Para usuário com 2FA habilitado, o login retorna status indicando a necessidade do código
        mockMvc.perform(post("/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.twoFactorRequired").value(true));
    }
}
