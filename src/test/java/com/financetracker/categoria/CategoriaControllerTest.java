package com.financetracker.categoria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.categoria.dto.CategoriaCriacaoRequest;
import com.financetracker.categoria.dto.CategoriaEdicaoRequest;
import com.financetracker.categoria.entity.Categoria;
import com.financetracker.categoria.repository.CategoriaRepository;
import com.financetracker.cartao.repository.CartaoRepository;
import com.financetracker.conta.repository.ContaRepository;
import com.financetracker.security.TokenService;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CategoriaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private CartaoRepository cartaoRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TokenService tokenService;

    private Usuario usuarioLogado;
    private Usuario usuarioInvasor;
    private String tokenLogado;
    private String tokenInvasor;

    @BeforeEach
    void setUp() {
        cartaoRepository.deleteAll();
        contaRepository.deleteAll();
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuarioLogado = usuarioRepository.save(new Usuario("usuarioLogado", "logado@example.com", "SenhaForte123!"));
        usuarioInvasor = usuarioRepository.save(new Usuario("usuarioInvasor", "invasor@example.com", "SenhaForte123!"));

        tokenLogado = tokenService.generateToken(usuarioLogado);
        tokenInvasor = tokenService.generateToken(usuarioInvasor);
    }

    @Test
    @DisplayName("POST /api/categorias — Criar categoria customizada com sucesso")
    void criarCategoriaComSucesso() throws Exception {
        CategoriaCriacaoRequest request = new CategoriaCriacaoRequest("Supermercado Mensal", "shopping-cart", "#10B981");

        mockMvc.perform(post("/api/categorias")
                        .header("Authorization", "Bearer " + tokenLogado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.usuarioId").value(usuarioLogado.getId().toString()))
                .andExpect(jsonPath("$.nome").value("Supermercado Mensal"))
                .andExpect(jsonPath("$.icone").value("shopping-cart"))
                .andExpect(jsonPath("$.corHexadecimal").value("#10B981"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("POST /api/categorias — Rejeitar criação de categoria com limite excedido (50)")
    void criarCategoriaLimiteExcedido() throws Exception {
        // Criar 50 categorias ativas
        for (int i = 0; i < 50; i++) {
            categoriaRepository.save(new Categoria(usuarioLogado, "Cat " + i, "home", "#111111", true));
        }

        CategoriaCriacaoRequest request = new CategoriaCriacaoRequest("Cat 51", "home", "#222222");

        mockMvc.perform(post("/api/categorias")
                        .header("Authorization", "Bearer " + tokenLogado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Limite máximo de 50 categorias customizadas atingido."));
    }

    @Test
    @DisplayName("POST /api/categorias — Rejeitar criação de categoria com nome já em uso")
    void criarCategoriaNomeDuplicado() throws Exception {
        categoriaRepository.save(new Categoria(usuarioLogado, "Alimentação", "shopping-basket", "#FF9F43", true));

        CategoriaCriacaoRequest request = new CategoriaCriacaoRequest("alimentação", "shopping-cart", "#10B981");

        mockMvc.perform(post("/api/categorias")
                        .header("Authorization", "Bearer " + tokenLogado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Já existe uma categoria ativa com este nome."));
    }

    @Test
    @DisplayName("GET /api/categorias — Listar categorias do usuário (somenteAtivas = true/false)")
    void listarCategorias() throws Exception {
        // Salvar uma global
        categoriaRepository.save(new Categoria(null, "Global Active", "globe", "#ffffff", true));
        
        // Salvar customizadas do usuário
        categoriaRepository.save(new Categoria(usuarioLogado, "Minha Ativa", "user", "#000000", true));
        categoriaRepository.save(new Categoria(usuarioLogado, "Minha Inativa", "user", "#111111", false));

        // Listar somente ativas (padrão)
        mockMvc.perform(get("/api/categorias")
                        .header("Authorization", "Bearer " + tokenLogado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Listar todas
        mockMvc.perform(get("/api/categorias?somenteAtivas=false")
                        .header("Authorization", "Bearer " + tokenLogado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("GET /api/categorias/{id} — Buscar por id com sucesso e proteção IDOR")
    void buscarPorIdEPromoverIDOR() throws Exception {
        Categoria c1 = categoriaRepository.save(new Categoria(usuarioLogado, "Minha", "user", "#000000", true));
        Categoria cInvasor = categoriaRepository.save(new Categoria(usuarioInvasor, "Do Invasor", "user", "#111111", true));
        Categoria cGlobal = categoriaRepository.save(new Categoria(null, "Global", "globe", "#ffffff", true));

        // Usuário logado busca a sua: ok
        mockMvc.perform(get("/api/categorias/" + c1.getId())
                        .header("Authorization", "Bearer " + tokenLogado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Minha"));

        // Usuário logado busca a global: ok
        mockMvc.perform(get("/api/categorias/" + cGlobal.getId())
                        .header("Authorization", "Bearer " + tokenLogado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Global"));

        // Usuário logado busca a do invasor: 404 (IDOR)
        mockMvc.perform(get("/api/categorias/" + cInvasor.getId())
                        .header("Authorization", "Bearer " + tokenLogado))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/categorias/{id} — Editar categoria e rejeitar edição de global")
    void editarCategoriaERejeitarGlobal() throws Exception {
        Categoria c1 = categoriaRepository.save(new Categoria(usuarioLogado, "Mercado", "shopping-cart", "#10B981", true));
        Categoria cGlobal = categoriaRepository.save(new Categoria(null, "Alimentação", "shopping-basket", "#FF9F43", true));

        CategoriaEdicaoRequest request = new CategoriaEdicaoRequest("Supermercado", "shopping-bag", "#059669");

        // Editar própria: ok
        mockMvc.perform(put("/api/categorias/" + c1.getId())
                        .header("Authorization", "Bearer " + tokenLogado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Supermercado"))
                .andExpect(jsonPath("$.icone").value("shopping-bag"));

        // Editar global: 403
        mockMvc.perform(put("/api/categorias/" + cGlobal.getId())
                        .header("Authorization", "Bearer " + tokenLogado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Não é permitido alterar ou inativar categorias padrão do sistema."));
    }

    @Test
    @DisplayName("DELETE /api/categorias/{id} — Inativar categoria (Soft Delete)")
    void inativarCategoriaComSucesso() throws Exception {
        Categoria c1 = categoriaRepository.save(new Categoria(usuarioLogado, "Viagem", "plane", "#222222", true));

        mockMvc.perform(delete("/api/categorias/" + c1.getId())
                        .header("Authorization", "Bearer " + tokenLogado))
                .andExpect(status().isNoContent());

        Categoria salva = categoriaRepository.findById(c1.getId()).orElseThrow();
        assertFalse(salva.getAtivo());
    }

    @Test
    @DisplayName("PATCH /api/categorias/{id}/ativar — Reativar categoria inativa")
    void reativarCategoriaComSucesso() throws Exception {
        Categoria c1 = categoriaRepository.save(new Categoria(usuarioLogado, "Viagem", "plane", "#222222", false));

        mockMvc.perform(patch("/api/categorias/" + c1.getId() + "/ativar")
                        .header("Authorization", "Bearer " + tokenLogado))
                .andExpect(status().isNoContent());

        Categoria salva = categoriaRepository.findById(c1.getId()).orElseThrow();
        assertTrue(salva.getAtivo());
    }

    @Test
    @DisplayName("PATCH /api/categorias/{id}/ativar — Reativar categoria com nome duplicado")
    void reativarCategoriaNomeDuplicado() throws Exception {
        categoriaRepository.save(new Categoria(usuarioLogado, "Viagem", "plane", "#222222", true));
        Categoria cInativa = categoriaRepository.save(new Categoria(usuarioLogado, "viagem", "plane", "#222222", false));

        mockMvc.perform(patch("/api/categorias/" + cInativa.getId() + "/ativar")
                        .header("Authorization", "Bearer " + tokenLogado))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Já existe uma categoria ativa com este nome."));
    }
}
