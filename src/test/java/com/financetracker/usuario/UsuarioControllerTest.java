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

import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private com.financetracker.security.RateLimitingFilter rateLimitingFilter;

    @Autowired
    private com.financetracker.security.TokenService tokenService;

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();
        usuarioRepository.save(new Usuario("existente", "existente@example.com", passwordEncoder.encode("SenhaForte123!")));
        rateLimitingFilter.resetLimits();
    }

    @Test
    @DisplayName("Teste 1: Cadastro com e-mail válido e senha forte deve retornar 201")
    void cadastroComEmailValidoESenhaForte() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Nome Valido");
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
        payload.put("name", "Nome Valido");
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
        payload.put("name", "Nome Valido");
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
        payload.put("name", "Nome Novo");
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
        payload.put("name", "");
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
        payload.put("name", "Nome Valido");
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
        payload.put("name", "Nome Valido");
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
        payload.put("name", "Nome Valido Hash");
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

    @Test
    @DisplayName("Teste 13: Rate Limiting no Cadastro deve retornar 429 após 5 tentativas de cadastro por IP")
    void rateLimitingCadastroBloqueiaAposExcederLimite() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Nome Limite");
        payload.put("email", "novo.usuario@example.com");
        payload.put("password", "SenhaForte123!");
        payload.put("confirmPassword", "SenhaForte123!");

        // Faz 5 tentativas bem-sucedidas de requisições de cadastro (com e-mails diferentes para evitar Conflict 409)
        for (int i = 1; i <= 5; i++) {
            payload.put("name", "Nome Limite " + i);
            payload.put("email", "usuario.rate" + i + "@example.com");
            mockMvc.perform(post("/usuarios/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isCreated());
        }

        // A 6ª tentativa deve retornar 429 Too Many Requests (Status status().isTooManyRequests() ou status().value(429))
        payload.put("name", "Nome Limite 6");
        payload.put("email", "usuario.rate6@example.com");
        mockMvc.perform(post("/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Teste 14: Obter perfil proprio (GET /me) sem token deve retornar 401")
    void obterMeSemToken() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/usuarios/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Teste 15: Obter perfil proprio (GET /me) com token valido deve retornar 200")
    void obterMeComToken() throws Exception {
        Usuario usuario = usuarioRepository.findByEmail("existente@example.com").orElseThrow();
        String token = tokenService.generateToken(usuario);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/usuarios/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.id").value(usuario.getId().toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.email").value(usuario.getEmail()));
    }

    @Test
    @DisplayName("Teste 16: Obter detalhes do proprio usuario (GET /{id}) com token valido deve retornar 200")
    void obterDetalhesUsuarioComToken() throws Exception {
        Usuario usuario = usuarioRepository.findByEmail("existente@example.com").orElseThrow();
        String token = tokenService.generateToken(usuario);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/usuarios/" + usuario.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.id").value(usuario.getId().toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.email").value(usuario.getEmail()));
    }

    @Test
    @DisplayName("Teste 17: Obter detalhes de usuario inexistente deve retornar 404 ou 403")
    void obterDetalhesUsuarioInexistente() throws Exception {
        Usuario usuario = usuarioRepository.findByEmail("existente@example.com").orElseThrow();
        String token = tokenService.generateToken(usuario);
        String uuidInexistente = java.util.UUID.randomUUID().toString();

        // UUID diferente do autenticado deve retornar 403 (IDOR bloqueado)
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/usuarios/" + uuidInexistente)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Teste 18: Obter detalhes de usuario sem token deve retornar 401")
    void obterDetalhesUsuarioSemToken() throws Exception {
        Usuario usuario = usuarioRepository.findByEmail("existente@example.com").orElseThrow();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/usuarios/" + usuario.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Teste 19: Atualizar informacoes do proprio usuario com token valido deve retornar 200")
    void atualizarUsuarioComToken() throws Exception {
        Usuario usuario = usuarioRepository.findByEmail("existente@example.com").orElseThrow();
        String token = tokenService.generateToken(usuario);

        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Novo Nome Atualizado");
        payload.put("email", "novoemail@example.com");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/usuarios/" + usuario.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Teste 20: Atualizar informacoes de usuario com dados invalidos deve retornar 400")
    void atualizarUsuarioComDadosInvalidos() throws Exception {
        Usuario usuario = usuarioRepository.findByEmail("existente@example.com").orElseThrow();
        String token = tokenService.generateToken(usuario);

        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "");
        payload.put("email", "email-invalido");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/usuarios/" + usuario.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Teste 21: Atualizar usuario de outro usuario (IDOR) deve retornar 403")
    void atualizarUsuarioDOutroIDOR() throws Exception {
        // Cria um segundo usuario
        Usuario outro = usuarioRepository.save(new Usuario("outro", "outro@example.com", passwordEncoder.encode("SenhaForte123!")));

        // Autentica como 'existente' e tenta alterar 'outro'
        Usuario usuario = usuarioRepository.findByEmail("existente@example.com").orElseThrow();
        String token = tokenService.generateToken(usuario);

        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Invasao");
        payload.put("email", "invasao@example.com");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/usuarios/" + outro.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Teste 22: Atualizar usuario sem token deve retornar 401")
    void atualizarUsuarioSemToken() throws Exception {
        Usuario usuario = usuarioRepository.findByEmail("existente@example.com").orElseThrow();
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Novo Nome");
        payload.put("email", "novoemail@example.com");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/usuarios/" + usuario.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Teste 23: Atualizar e-mail para um ja existente deve retornar 409")
    void atualizarEmailDuplicado() throws Exception {
        // Cria segundo usuario
        usuarioRepository.save(new Usuario("segundo", "segundo@example.com", passwordEncoder.encode("SenhaForte123!")));

        Usuario usuario = usuarioRepository.findByEmail("existente@example.com").orElseThrow();
        String token = tokenService.generateToken(usuario);

        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Existente");
        payload.put("email", "segundo@example.com"); // e-mail de outro usuario

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/usuarios/" + usuario.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Teste 24: Deletar proprio usuario com token valido deve retornar 200 e inativar (soft delete)")
    void deletarUsuarioComToken() throws Exception {
        Usuario usuario = usuarioRepository.findByEmail("existente@example.com").orElseThrow();
        String token = tokenService.generateToken(usuario);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/usuarios/" + usuario.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Usuario usuarioDeletado = usuarioRepository.findById(usuario.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertFalse(usuarioDeletado.isAtivo());
    }

    @Test
    @DisplayName("Teste 25: Deletar usuario de outro usuario (IDOR) deve retornar 403")
    void deletarUsuarioDOutroIDOR() throws Exception {
        Usuario outro = usuarioRepository.save(new Usuario("outro2", "outro2@example.com", passwordEncoder.encode("SenhaForte123!")));

        Usuario usuario = usuarioRepository.findByEmail("existente@example.com").orElseThrow();
        String token = tokenService.generateToken(usuario);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/usuarios/" + outro.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Teste 26: Deletar usuario inexistente deve retornar 403 (ID de outro usuario = acesso negado)")
    void deletarUsuarioInexistente() throws Exception {
        Usuario usuario = usuarioRepository.findByEmail("existente@example.com").orElseThrow();
        String token = tokenService.generateToken(usuario);
        String uuidInexistente = java.util.UUID.randomUUID().toString();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/usuarios/" + uuidInexistente)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Teste 27: Deletar usuario sem token deve retornar 401")
    void deletarUsuarioSemToken() throws Exception {
        Usuario usuario = usuarioRepository.findByEmail("existente@example.com").orElseThrow();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/usuarios/" + usuario.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Teste 28: Cadastro com e-mail contendo espacos e maiusculas deve normalizar antes de persistir")
    void cadastroComEmailNormalizado() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Nome Normalizado");
        payload.put("email", "  EMAIL.normalizado@Example.com  ");
        payload.put("password", "SenhaForte123!");
        payload.put("confirmPassword", "SenhaForte123!");

        mockMvc.perform(post("/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        // Verifica se persistiu com e-mail em lowercase e sem espacos
        org.junit.jupiter.api.Assertions.assertTrue(usuarioRepository.existsByEmail("email.normalizado@example.com"));
        // E verifica se tentar registrar novamente o mesmo e-mail normalizado, mesmo em outro formato, da conflito
        payload.put("name", "Outro Nome");
        payload.put("email", "email.normalizado@example.com");
        mockMvc.perform(post("/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Teste 29: Cadastro com nome ja existente deve retornar 409")
    void cadastroComNomeExistente() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "existente");
        payload.put("email", "novo.email@example.com");
        payload.put("password", "SenhaForte123!");
        payload.put("confirmPassword", "SenhaForte123!");

        mockMvc.perform(post("/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Teste 30: Atualizar e-mail com espacos e maiusculas deve normalizar antes de persistir")
    void atualizarEmailNormalizado() throws Exception {
        Usuario usuario = usuarioRepository.findByEmail("existente@example.com").orElseThrow();
        String token = tokenService.generateToken(usuario);

        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Nome Novo");
        payload.put("email", "  NOVO.email@Example.com  ");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/usuarios/" + usuario.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        Usuario usuarioAtualizado = usuarioRepository.findById(usuario.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("novo.email@example.com", usuarioAtualizado.getEmail());
    }

    @Test
    @DisplayName("Teste 31: GET /usuarios/me nao deve expor campos sensiveis")
    void responseNaoExpoeCamposSensiveis() throws Exception {
        Usuario usuario = usuarioRepository.findByEmail("existente@example.com").orElseThrow();
        String token = tokenService.generateToken(usuario);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/usuarios/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.ativo").doesNotExist())
                .andExpect(jsonPath("$.bloqueado").doesNotExist())
                .andExpect(jsonPath("$.mfaHabilitado").doesNotExist());
    }
}
