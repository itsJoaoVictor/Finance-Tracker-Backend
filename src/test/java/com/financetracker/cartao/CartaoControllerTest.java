package com.financetracker.cartao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.cartao.dto.CartaoCriacaoRequest;
import com.financetracker.cartao.dto.CartaoEdicaoRequest;
import com.financetracker.cartao.entity.Cartao;
import com.financetracker.cartao.repository.CartaoRepository;
import com.financetracker.conta.entity.Conta;
import com.financetracker.conta.model.TipoConta;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CartaoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private Conta contaValida;
    private Conta contaInvasor;

    @BeforeEach
    void setUp() {
        cartaoRepository.deleteAll();
        contaRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuarioLogado = usuarioRepository.save(new Usuario("usuarioLogado", "logado@example.com", "SenhaForte123!"));
        usuarioInvasor = usuarioRepository.save(new Usuario("usuarioInvasor", "invasor@example.com", "SenhaForte123!"));

        tokenLogado = tokenService.generateToken(usuarioLogado);
        tokenInvasor = tokenService.generateToken(usuarioInvasor);

        // Conta do usuário logado
        Conta c1 = new Conta();
        c1.setUsuario(usuarioLogado);
        c1.setNome("Carteira Logado");
        c1.setTipo(TipoConta.CORRENTE);
        c1.setSaldo(BigDecimal.valueOf(1000.00));
        c1.setAtivo(true);
        contaValida = contaRepository.save(c1);

        // Conta do invasor
        Conta c2 = new Conta();
        c2.setUsuario(usuarioInvasor);
        c2.setNome("Carteira Invasor");
        c2.setTipo(TipoConta.CORRENTE);
        c2.setSaldo(BigDecimal.valueOf(500.00));
        c2.setAtivo(true);
        contaInvasor = contaRepository.save(c2);
    }

    @Test
    @DisplayName("POST /api/cartoes — Criar cartão com dados válidos deve retornar 201")
    void criarCartaoComSucesso() throws Exception {
        CartaoCriacaoRequest request = new CartaoCriacaoRequest(
                "Nubank",
                BigDecimal.valueOf(5000.00),
                5,
                12,
                contaValida.getId(),
                "#8A05BE"
        );

        mockMvc.perform(post("/api/cartoes")
                        .header("Authorization", "Bearer " + tokenLogado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nome").value("Nubank"))
                .andExpect(jsonPath("$.limite").value(5000.00))
                .andExpect(jsonPath("$.limiteDisponivel").value(5000.00)) // RN-02
                .andExpect(jsonPath("$.diaFechamento").value(5))
                .andExpect(jsonPath("$.diaVencimento").value(12))
                .andExpect(jsonPath("$.contaId").value(contaValida.getId().toString()))
                .andExpect(jsonPath("$.corHexadecimal").value("#8A05BE"));
    }

    @Test
    @DisplayName("POST /api/cartoes — Criar cartão com conta de outro usuário deve retornar 404 (Anti-IDOR)")
    void criarCartaoComContaInvalidaIDOR() throws Exception {
        CartaoCriacaoRequest request = new CartaoCriacaoRequest(
                "Inter",
                BigDecimal.valueOf(1000.00),
                10,
                17,
                contaInvasor.getId(),
                "#FFC107"
        );

        mockMvc.perform(post("/api/cartoes")
                        .header("Authorization", "Bearer " + tokenLogado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound()); // Conta não encontrada para este usuário
    }

    @Test
    @DisplayName("POST /api/cartoes — Bloquear criação se passar do limite de 10 cartões ativos")
    void criarCartaoAcimaDoLimite() throws Exception {
        for (int i = 0; i < 10; i++) {
            Cartao c = new Cartao();
            c.setUsuario(usuarioLogado);
            c.setNome("Cartao " + i);
            c.setLimite(BigDecimal.valueOf(1000));
            c.setLimiteDisponivel(BigDecimal.valueOf(1000));
            c.setDiaFechamento(5);
            c.setDiaVencimento(15);
            c.setConta(contaValida);
            c.setAtivo(true);
            cartaoRepository.save(c);
        }

        CartaoCriacaoRequest request = new CartaoCriacaoRequest(
                "Estouro do Limite",
                BigDecimal.valueOf(1000.00),
                5,
                15,
                contaValida.getId(),
                "#FF0000"
        );

        mockMvc.perform(post("/api/cartoes")
                        .header("Authorization", "Bearer " + tokenLogado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("GET /api/cartoes — Listar cartões do usuário autenticado")
    void listarCartoesComSucesso() throws Exception {
        Cartao c = new Cartao();
        c.setUsuario(usuarioLogado);
        c.setNome("Nubank");
        c.setLimite(BigDecimal.valueOf(5000));
        c.setLimiteDisponivel(BigDecimal.valueOf(5000));
        c.setDiaFechamento(5);
        c.setDiaVencimento(12);
        c.setConta(contaValida);
        c.setAtivo(true);
        cartaoRepository.save(c);

        mockMvc.perform(get("/api/cartoes")
                        .header("Authorization", "Bearer " + tokenLogado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Nubank"));
    }

    @Test
    @DisplayName("GET /api/cartoes/{id} — Buscar cartão próprio por ID com sucesso")
    void buscarCartaoProprio() throws Exception {
        Cartao c = new Cartao();
        c.setUsuario(usuarioLogado);
        c.setNome("Nubank");
        c.setLimite(BigDecimal.valueOf(5000));
        c.setLimiteDisponivel(BigDecimal.valueOf(5000));
        c.setDiaFechamento(5);
        c.setDiaVencimento(12);
        c.setConta(contaValida);
        c.setAtivo(true);
        c = cartaoRepository.save(c);

        mockMvc.perform(get("/api/cartoes/" + c.getId())
                        .header("Authorization", "Bearer " + tokenLogado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Nubank"));
    }

    @Test
    @DisplayName("GET /api/cartoes/{id} — Buscar cartão de terceiro retorna 404 (Anti-IDOR)")
    void buscarCartaoTerceiro() throws Exception {
        Cartao c = new Cartao();
        c.setUsuario(usuarioInvasor);
        c.setNome("Nubank Invasor");
        c.setLimite(BigDecimal.valueOf(5000));
        c.setLimiteDisponivel(BigDecimal.valueOf(5000));
        c.setDiaFechamento(5);
        c.setDiaVencimento(12);
        c.setConta(contaInvasor);
        c.setAtivo(true);
        c = cartaoRepository.save(c);

        mockMvc.perform(get("/api/cartoes/" + c.getId())
                        .header("Authorization", "Bearer " + tokenLogado))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/cartoes/{id} — Edição ajusta proporcionalmente o limite disponível (aumento)")
    void editarCartaoAumentoLimite() throws Exception {
        Cartao c = new Cartao();
        c.setUsuario(usuarioLogado);
        c.setNome("Nubank");
        c.setLimite(BigDecimal.valueOf(5000.00));
        // limite utilizado de 800, ou seja, limiteDisponivel de 4200
        c.setLimiteDisponivel(BigDecimal.valueOf(4200.00));
        c.setDiaFechamento(5);
        c.setDiaVencimento(12);
        c.setConta(contaValida);
        c.setAtivo(true);
        c = cartaoRepository.save(c);

        // Altera limite total de 5000 para 6000 (+1000)
        CartaoEdicaoRequest req = new CartaoEdicaoRequest(
                "Nubank Black",
                BigDecimal.valueOf(6000.00),
                5,
                12,
                contaValida.getId(),
                "#8A05BE"
        );

        mockMvc.perform(put("/api/cartoes/" + c.getId())
                        .header("Authorization", "Bearer " + tokenLogado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Nubank Black"))
                .andExpect(jsonPath("$.limite").value(6000.00))
                .andExpect(jsonPath("$.limiteDisponivel").value(5200.00)); // 4200 + 1000 = 5200
    }

    @Test
    @DisplayName("PUT /api/cartoes/{id} — Edição rejeita alteração se limite disponível ficaria negativo")
    void editarCartaoLimiteNegativoRejeitado() throws Exception {
        Cartao c = new Cartao();
        c.setUsuario(usuarioLogado);
        c.setNome("Nubank");
        c.setLimite(BigDecimal.valueOf(5000.00));
        // limite utilizado de 4000 (disponivel de 1000)
        c.setLimiteDisponivel(BigDecimal.valueOf(1000.00));
        c.setDiaFechamento(5);
        c.setDiaVencimento(12);
        c.setConta(contaValida);
        c.setAtivo(true);
        c = cartaoRepository.save(c);

        // Altera limite total de 5000 para 3000 (-2000). Disponível cairia para -1000.
        CartaoEdicaoRequest req = new CartaoEdicaoRequest(
                "Nubank",
                BigDecimal.valueOf(3000.00),
                5,
                12,
                contaValida.getId(),
                "#8A05BE"
        );

        mockMvc.perform(put("/api/cartoes/" + c.getId())
                        .header("Authorization", "Bearer " + tokenLogado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity()); // RN-03
    }

    @Test
    @DisplayName("DELETE /api/cartoes/{id} — Inativa cartão (Soft Delete)")
    void inativarCartao() throws Exception {
        Cartao c = new Cartao();
        c.setUsuario(usuarioLogado);
        c.setNome("Inter");
        c.setLimite(BigDecimal.valueOf(2000.00));
        c.setLimiteDisponivel(BigDecimal.valueOf(2000.00));
        c.setDiaFechamento(10);
        c.setDiaVencimento(20);
        c.setConta(contaValida);
        c.setAtivo(true);
        c = cartaoRepository.save(c);

        mockMvc.perform(delete("/api/cartoes/" + c.getId())
                        .header("Authorization", "Bearer " + tokenLogado))
                .andExpect(status().isNoContent());

        Cartao cartaoSalvo = cartaoRepository.findById(c.getId()).orElseThrow();
        assertFalse(cartaoSalvo.getAtivo()); // RN-04
    }

    @Test
    @DisplayName("GET /api/cartoes/resumo — Resumo financeiro de limites e faturas totais")
    void obterResumoCartoes() throws Exception {
        // Cartão 1: limite 5000, disp 4200 (fatura = 800)
        Cartao c1 = new Cartao();
        c1.setUsuario(usuarioLogado);
        c1.setNome("Cartao 1");
        c1.setLimite(BigDecimal.valueOf(5000.00));
        c1.setLimiteDisponivel(BigDecimal.valueOf(4200.00));
        c1.setDiaFechamento(5);
        c1.setDiaVencimento(15);
        c1.setConta(contaValida);
        c1.setAtivo(true);
        cartaoRepository.save(c1);

        // Cartão 2: limite 10000, disp 10000 (fatura = 0)
        Cartao c2 = new Cartao();
        c2.setUsuario(usuarioLogado);
        c2.setNome("Cartao 2");
        c2.setLimite(BigDecimal.valueOf(10000.00));
        c2.setLimiteDisponivel(BigDecimal.valueOf(10000.00));
        c2.setDiaFechamento(10);
        c2.setDiaVencimento(20);
        c2.setConta(contaValida);
        c2.setAtivo(true);
        cartaoRepository.save(c2);

        mockMvc.perform(get("/api/cartoes/resumo")
                        .header("Authorization", "Bearer " + tokenLogado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLimite").value(15000.00))
                .andExpect(jsonPath("$.totalLimiteDisponivel").value(14200.00))
                .andExpect(jsonPath("$.totalFaturaEstimada").value(800.00))
                .andExpect(jsonPath("$.quantidadeCartoes").value(2));
    }
}
