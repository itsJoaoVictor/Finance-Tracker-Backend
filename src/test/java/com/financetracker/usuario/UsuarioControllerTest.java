package com.financetracker.usuario;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
}
