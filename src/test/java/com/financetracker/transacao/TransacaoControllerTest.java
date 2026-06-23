package com.financetracker.transacao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.cartao.entity.Cartao;
import com.financetracker.cartao.repository.CartaoRepository;
import com.financetracker.categoria.entity.Categoria;
import com.financetracker.categoria.repository.CategoriaRepository;
import com.financetracker.conta.entity.Conta;
import com.financetracker.conta.model.TipoConta;
import com.financetracker.conta.repository.ContaRepository;
import com.financetracker.security.TokenService;
import com.financetracker.transacao.dto.TransacaoCriacaoRequest;
import com.financetracker.transacao.entity.Fatura;
import com.financetracker.transacao.enums.StatusFatura;
import com.financetracker.transacao.repository.FaturaRepository;
import com.financetracker.transacao.repository.TransacaoRepository;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class TransacaoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private FaturaRepository faturaRepository;

    @Autowired
    private CartaoRepository cartaoRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TokenService tokenService;

    private Usuario usuarioLogado;
    private String tokenLogado;
    private Conta contaValida;
    private Categoria categoriaValida;
    private Cartao cartaoValido;

    @BeforeEach
    void setUp() {
        transacaoRepository.deleteAll();
        faturaRepository.deleteAll();
        cartaoRepository.deleteAll();
        contaRepository.deleteAll();
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();

        // 1. Criar Usuário (com data de criação fixa em 2026-06-22T00:00:00 para simularmos faturas passadas e futuras)
        Usuario u = new Usuario("usuarioTeste", "teste@example.com", "SenhaForte123!");
        // Usamos reflexão para injetar a data de criação no campo privado 'criadoEm', caso não tenhovo setter
        try {
            java.lang.reflect.Field field = Usuario.class.getDeclaredField("criadoEm");
            field.setAccessible(true);
            field.set(u, LocalDateTime.of(2026, 6, 22, 0, 0, 0));
        } catch (Exception e) {
            e.printStackTrace();
        }
        usuarioLogado = usuarioRepository.save(u);
        tokenLogado = tokenService.generateToken(usuarioLogado);

        // 2. Criar Categoria
        Categoria cat = new Categoria();
        cat.setNome("Alimentação");
        cat.setIcone("utensils");
        cat.setCorHexadecimal("#FF5733");
        cat.setUsuario(usuarioLogado);
        cat.setAtivo(true);
        categoriaValida = categoriaRepository.save(cat);

        // 3. Criar Conta
        Conta c1 = new Conta();
        c1.setUsuario(usuarioLogado);
        c1.setNome("Banco Inter");
        c1.setTipo(TipoConta.CORRENTE);
        c1.setSaldo(BigDecimal.valueOf(5000.00));
        c1.setAtivo(true);
        contaValida = contaRepository.save(c1);

        // 4. Criar Cartão (Limite total: 1000.00, diaFechamento: 21, diaVencimento: 28)
        Cartao cartao = new Cartao();
        cartao.setUsuario(usuarioLogado);
        cartao.setNome("Nubank");
        cartao.setLimite(BigDecimal.valueOf(1000.00));
        cartao.setLimiteDisponivel(BigDecimal.valueOf(1000.00));
        cartao.setDiaFechamento(21);
        cartao.setDiaVencimento(28);
        cartao.setConta(contaValida);
        cartao.setAtivo(true);
        cartaoValido = cartaoRepository.save(cartao);
    }

    @Test
    @DisplayName("POST /api/transacoes — Importação de compra parcelada retroativa com Opção B de faturas históricas")
    void importarCompraParceladaRetroativaOpcaoB() throws Exception {
        // Compra de 1000.00 parcelada em 10 vezes realizada em 19/03/2026
        // Parcelas: 10x de 100.00
        // Data Criação do Usuário: 22/06/2026
        // Faturas com vencimentos menores que 22/06/2026 (Março, Abril, Maio) devem nascer PAGAS automaticamente.
        // Fatura de Junho (vence em 28/06/2026 >= 22/06/2026) deve nascer FECHADA / NÃO PAGA.
        // Faturas futuras (Julho em diante) devem nascer ABERTAS.
        TransacaoCriacaoRequest request = new TransacaoCriacaoRequest(
                "Compra Retroativa Parcelada",
                BigDecimal.valueOf(1000.00),
                "COMPRA_CREDITO",
                null,
                null,
                cartaoValido.getId(),
                categoriaValida.getId(),
                LocalDate.of(2026, 3, 19),
                10,
                null,
                null,
                null
        );

        mockMvc.perform(post("/api/transacoes")
                        .header("Authorization", "Bearer " + tokenLogado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Validar Faturas salvas no Banco de Dados
        List<Fatura> faturas = faturaRepository.findByUsuarioId(usuarioLogado.getId());
        assertEquals(10, faturas.size(), "Devem ser criadas exatamente 10 faturas correspondentes às 10 parcelas.");

        // Encontrar as faturas específicas por mês de referência para validar o status
        for (Fatura f : faturas) {
            LocalDate ref = f.getMesReferencia();
            if (ref.equals(LocalDate.of(2026, 3, 1))) { // Vence 28/03 (Histórica)
                assertEquals(StatusFatura.PAGA, f.getStatus(), "Fatura de Março deve estar PAGA.");
                assertEquals(0, new BigDecimal("100.00").compareTo(f.getValorPago()), "Fatura de Março deve possuir valor pago de 100.00.");
            } else if (ref.equals(LocalDate.of(2026, 4, 1))) { // Vence 28/04 (Histórica)
                assertEquals(StatusFatura.PAGA, f.getStatus(), "Fatura de Abril deve estar PAGA.");
            } else if (ref.equals(LocalDate.of(2026, 5, 1))) { // Vence 28/05 (Histórica)
                assertEquals(StatusFatura.PAGA, f.getStatus(), "Fatura de Maio deve estar PAGA.");
            } else if (ref.equals(LocalDate.of(2026, 6, 1))) { // Vence 28/06 (Igual/Maior que data criação: Fechada porque já passou o fechamento em 21/06)
                assertEquals(StatusFatura.FECHADA, f.getStatus(), "Fatura de Junho deve estar FECHADA.");
                assertEquals(0, BigDecimal.ZERO.compareTo(f.getValorPago()), "Fatura de Junho não deve possuir valor pago.");
            } else if (ref.equals(LocalDate.of(2026, 7, 1))) { // Vence 28/07 (Futura)
                assertEquals(StatusFatura.ABERTA, f.getStatus(), "Fatura de Julho deve estar ABERTA.");
            }
        }

        // Validar limite do Cartão:
        // Limite Total = 1000.00
        // Compra Total = 1000.00
        // 3 parcelas históricas pagas = 300.00 liberados
        // Limite disponível deve ser 300.00 (indicando que 700.00 estão consumidos)
        Cartao cartaoPosTransacao = cartaoRepository.findById(cartaoValido.getId()).orElseThrow();
        assertEquals(0, BigDecimal.valueOf(300.00).compareTo(cartaoPosTransacao.getLimiteDisponivel()), 
                "O limite disponível do cartão deve ser de 300.00 devido à liberação das 3 parcelas históricas.");
    }

    @Test
    @DisplayName("POST /api/transacoes — Criar DEPOSITO sem categoria deve ter sucesso")
    void criarDepositoSemCategoriaSucesso() throws Exception {
        TransacaoCriacaoRequest request = new TransacaoCriacaoRequest(
                "Depósito de teste",
                BigDecimal.valueOf(500.00),
                "DEPOSITO",
                null,
                contaValida.getId(),
                null,
                null,
                LocalDate.now(),
                1,
                null,
                null,
                null
        );

        mockMvc.perform(post("/api/transacoes")
                        .header("Authorization", "Bearer " + tokenLogado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/transacoes — Criar SAQUE sem categoria deve ter sucesso")
    void criarSaqueSemCategoriaSucesso() throws Exception {
        TransacaoCriacaoRequest request = new TransacaoCriacaoRequest(
                "Saque de teste",
                BigDecimal.valueOf(200.00),
                "SAQUE",
                contaValida.getId(),
                null,
                null,
                null,
                LocalDate.now(),
                1,
                null,
                null,
                null
        );

        mockMvc.perform(post("/api/transacoes")
                        .header("Authorization", "Bearer " + tokenLogado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/transacoes — Criar PIX sem categoria deve ter sucesso")
    void criarPixSemCategoriaSucesso() throws Exception {
        TransacaoCriacaoRequest request = new TransacaoCriacaoRequest(
                "Pix de teste",
                BigDecimal.valueOf(150.00),
                "PIX",
                contaValida.getId(),
                null,
                null,
                null,
                LocalDate.now(),
                1,
                null,
                null,
                null
        );

        mockMvc.perform(post("/api/transacoes")
                        .header("Authorization", "Bearer " + tokenLogado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/transacoes/transferir — Transferência entre contas sem categoria deve ter sucesso")
    void transferirSemCategoriaSucesso() throws Exception {
        Conta c2 = new Conta();
        c2.setUsuario(usuarioLogado);
        c2.setNome("Banco do Brasil");
        c2.setTipo(TipoConta.POUPANCA);
        c2.setSaldo(BigDecimal.valueOf(100.00));
        c2.setAtivo(true);
        Conta contaDestino = contaRepository.save(c2);

        com.financetracker.transacao.dto.TransferenciaRequest request = new com.financetracker.transacao.dto.TransferenciaRequest(
                contaValida.getId(),
                contaDestino.getId(),
                BigDecimal.valueOf(50.00),
                "Transferência de teste",
                null
        );

        mockMvc.perform(post("/api/transacoes/transferir")
                        .header("Authorization", "Bearer " + tokenLogado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/transacoes — Criar DEPOSITO sem descrição deve ter sucesso")
    void criarDepositoSemDescricaoSucesso() throws Exception {
        TransacaoCriacaoRequest request = new TransacaoCriacaoRequest(
                null,
                BigDecimal.valueOf(500.00),
                "DEPOSITO",
                null,
                contaValida.getId(),
                null,
                null,
                LocalDate.now(),
                1,
                null,
                null,
                null
        );

        mockMvc.perform(post("/api/transacoes")
                        .header("Authorization", "Bearer " + tokenLogado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/transacoes — Criar COMPRA_CREDITO sem descrição deve falhar")
    void criarCompraCreditoSemDescricaoFalha() throws Exception {
        TransacaoCriacaoRequest request = new TransacaoCriacaoRequest(
                "",
                BigDecimal.valueOf(100.00),
                "COMPRA_CREDITO",
                null,
                null,
                cartaoValido.getId(),
                categoriaValida.getId(),
                LocalDate.now(),
                1,
                null,
                null,
                null
        );

        mockMvc.perform(post("/api/transacoes")
                        .header("Authorization", "Bearer " + tokenLogado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
