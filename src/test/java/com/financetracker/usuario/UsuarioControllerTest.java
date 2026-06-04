package com.financetracker.usuario;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.BeforeEach;
import com.financetracker.usuario.repository.UsuarioRepository;
import com.financetracker.usuario.entity.Usuario;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();
        usuarioRepository.save(new Usuario("existente", "existente@example.com", passwordEncoder.encode("SenhaForte123!")));
    }

    @Test
    @DisplayName("Teste 1: Cadastro com e-mail válido e senha forte deve retornar 201")
    void cadastroComEmailValidoESenhaForte() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "usuario.valido@example.com");
        payload.put("password", "SenhaForte123!");
        payload.put("confirmPassword", "SenhaForte123!");

        mockMvc.perform(post("/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Teste 2: Cadastro com e-mail inválido deve retornar 400")
    void cadastroComEmailInvalido() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "email-invalido");
        payload.put("password", "SenhaForte123!");
        payload.put("confirmPassword", "SenhaForte123!");

        mockMvc.perform(post("/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Teste 3: Cadastro com senha fraca deve retornar 400")
    void cadastroComSenhaFraca() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "usuario2@example.com");
        payload.put("password", "123");
        payload.put("confirmPassword", "123");

        mockMvc.perform(post("/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Teste 4: Cadastro com e-mail já existente deve retornar 409")
    void cadastroComEmailExistente() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "existente@example.com");
        payload.put("password", "SenhaForte123!");
        payload.put("confirmPassword", "SenhaForte123!");

        mockMvc.perform(post("/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Teste 5: Cadastro com campos vazios deve retornar 400")
    void cadastroComCamposVazios() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "");
        payload.put("password", "");
        payload.put("confirmPassword", "");

        mockMvc.perform(post("/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Teste 6: Cadastro com senha e confirmação diferentes deve retornar 400")
    void cadastroComSenhasDiferentes() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "usuario3@example.com");
        payload.put("password", "SenhaForte123!");
        payload.put("confirmPassword", "OutraSenha456!");

        mockMvc.perform(post("/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Teste 7: Cadastro sem confirmação de senha deve retornar 400")
    void cadastroSemConfirmacaoSenha() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "usuario4@example.com");
        payload.put("password", "SenhaForte123!");
        // confirmPassword ausente

        mockMvc.perform(post("/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Teste 8: Cadastro bem-sucedido deve salvar senha com hash BCrypt")
    void cadastroSalvaSenhaComHash() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "senha.hash@example.com");
        payload.put("password", "SenhaForte123!");
        payload.put("confirmPassword", "SenhaForte123!");

        mockMvc.perform(post("/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        Usuario usuarioSalvo = usuarioRepository.findByEmail("senha.hash@example.com").orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(usuarioSalvo.getSenha().startsWith("$2a$") || usuarioSalvo.getSenha().startsWith("$2y$"));
    }

    @Test
    @DisplayName("Teste 9: Login com credenciais corretas deve retornar JWT")
    void loginComSucesso() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "existente@example.com");
        payload.put("password", "SenhaForte123!");

        mockMvc.perform(post("/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.token").exists());
    }

    @Test
    @DisplayName("Teste 10: Login com credenciais incorretas deve retornar 401")
    void loginComCredenciaisIncorretas() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "existente@example.com");
        payload.put("password", "SenhaIncorreta123!");

        mockMvc.perform(post("/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Teste 11: Acesso a rota protegida sem token deve retornar 401")
    void rotaProtegidaSemToken() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/usuarios/protegido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Teste 12: Acesso a rota protegida com token válido deve passar da autenticação")
    void rotaProtegidaComToken() throws Exception {
        // Obter token
        Map<String, Object> loginPayload = new HashMap<>();
        loginPayload.put("email", "existente@example.com");
        loginPayload.put("password", "SenhaForte123!");

        String responseString = mockMvc.perform(post("/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginPayload)))
                .andReturn().getResponse().getContentAsString();

        Map<String, String> responseMap = objectMapper.readValue(responseString, Map.class);
        String token = responseMap.get("token");

        // Acessar rota protegida (retornará 404 porque a rota não existe, mas NÃO 401 Unauthorized!)
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/usuarios/protegido")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
