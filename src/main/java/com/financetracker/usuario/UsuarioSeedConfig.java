package com.financetracker.usuario;

import com.financetracker.cartao.entity.Cartao;
import com.financetracker.cartao.repository.CartaoRepository;
import com.financetracker.conta.entity.Conta;
import com.financetracker.conta.model.TipoConta;
import com.financetracker.conta.repository.ContaRepository;
import com.financetracker.categoria.entity.Categoria;
import com.financetracker.categoria.repository.CategoriaRepository;
import com.financetracker.assinatura.entity.Assinatura;
import com.financetracker.assinatura.enums.TipoRecorrencia;
import com.financetracker.assinatura.repository.AssinaturaRepository;
import com.financetracker.transacao.entity.Fatura;
import com.financetracker.transacao.entity.Transacao;
import com.financetracker.transacao.enums.StatusFatura;
import com.financetracker.transacao.enums.TipoTransacao;
import com.financetracker.transacao.repository.FaturaRepository;
import com.financetracker.transacao.repository.TransacaoRepository;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Configuration
@Profile("seed")
public class UsuarioSeedConfig {

    @Bean
    CommandLineRunner seedUsuarios(UsuarioRepository usuarioRepository,
                                  ContaRepository contaRepository,
                                  CartaoRepository cartaoRepository,
                                  CategoriaRepository categoriaRepository,
                                  TransacaoRepository transacaoRepository,
                                  AssinaturaRepository assinaturaRepository,
                                  FaturaRepository faturaRepository,
                                  PasswordEncoder passwordEncoder) {
        return args -> {
            // Seed global categories if not already done by CategoriaSeedConfig
            Categoria catLazer = getOrCreateGlobalCategory(categoriaRepository, "Lazer", "smile", "#F1C40F");
            Categoria catServicos = getOrCreateGlobalCategory(categoriaRepository, "Serviços", "cpu", "#9B59B6");
            Categoria catSaude = getOrCreateGlobalCategory(categoriaRepository, "Saúde", "heartbeat", "#2ED573");
            Categoria catOutros = getOrCreateGlobalCategory(categoriaRepository, "Outros", "help-circle", "#7F8C8D");

            // Seed User 1: teste@teste.com
            Usuario user1 = getOrCreateUser(usuarioRepository, passwordEncoder, "Conta Teste 1", "teste@teste.com", "@Teste123");
            seedUserTestData(user1, contaRepository, cartaoRepository, faturaRepository, transacaoRepository, assinaturaRepository, catLazer, catServicos, catSaude, catOutros);

            // Seed User 2: teste2@teste.com
            Usuario user2 = getOrCreateUser(usuarioRepository, passwordEncoder, "Conta Teste 2", "teste2@teste.com", "@Teste123");
            seedUserTestData(user2, contaRepository, cartaoRepository, faturaRepository, transacaoRepository, assinaturaRepository, catLazer, catServicos, catSaude, catOutros);
        };
    }

    private Categoria getOrCreateGlobalCategory(CategoriaRepository repo, String nome, String icone, String cor) {
        return repo.findAllByUsuarioId(null).stream()
                .filter(c -> c.getNome().equalsIgnoreCase(nome))
                .findFirst()
                .orElseGet(() -> repo.save(new Categoria(null, nome, icone, cor, true)));
    }

    private Usuario getOrCreateUser(UsuarioRepository repo, PasswordEncoder encoder, String nome, String email, String senha) {
        Optional<Usuario> opt = repo.findByEmail(email);
        if (opt.isPresent()) {
            return opt.get();
        }
        Usuario user = new Usuario(nome, email, encoder.encode(senha));
        user.setCriadoEm(LocalDateTime.of(2025, 1, 1, 0, 0));
        return repo.save(user);
    }

    private void seedUserTestData(Usuario usuario,
                                  ContaRepository contaRepository,
                                  CartaoRepository cartaoRepository,
                                  FaturaRepository faturaRepository,
                                  TransacaoRepository transacaoRepository,
                                  AssinaturaRepository assinaturaRepository,
                                  Categoria catLazer, Categoria catServicos, Categoria catSaude, Categoria catOutros) {
        
        // Skip if already seeded for this user
        if (contaRepository.countByUsuarioIdAndAtivoTrue(usuario.getId()) > 0) {
            return;
        }

        // 1. Contas
        Conta contaNubank = new Conta();
        contaNubank.setUsuario(usuario);
        contaNubank.setNome("Nubank");
        contaNubank.setTipo(TipoConta.CORRENTE);
        contaNubank.setSaldo(new BigDecimal("12000.00"));
        contaNubank.setAtivo(true);
        contaNubank.setContaPadrao(true);
        contaNubank.setCorHexadecimal("#8A05BE");
        contaNubank = contaRepository.save(contaNubank);

        Conta contaBB = new Conta();
        contaBB.setUsuario(usuario);
        contaBB.setNome("Banco do Brasil");
        contaBB.setTipo(TipoConta.CORRENTE);
        contaBB.setSaldo(new BigDecimal("10000.00"));
        contaBB.setAtivo(true);
        contaBB.setContaPadrao(false);
        contaBB.setCorHexadecimal("#FCF310");
        contaBB = contaRepository.save(contaBB);

        // Sofa - Compra total parcelada (10 parcelas de R$ 100 = R$ 1000 total)
        // Quando a transação foi cadastrada pelo seed antigo, foi inserido ad-hoc de forma manual sem decrementar do limite.
        // Vamos ajustar o limite do cartão inicial de acordo com todas as compras realizadas:
        // Maio: Supermercado R$ 300 - Pago R$ 100 = Consumido R$ 200
        // Junho: Netflix R$ 44.90 (assinatura) + Sofá total R$ 1000
        // Limite total: R$ 21.000,00
        // Consumido: R$ 200 (Maio restante) + R$ 44.90 (Netflix) + R$ 1000 (Sofá total) = R$ 1244.90
        // Limite disponível: 21000 - 1244.90 = R$ 19755.10

        // 2. Cartões
        Cartao cartaoNubank = new Cartao();
        cartaoNubank.setUsuario(usuario);
        cartaoNubank.setConta(contaNubank);
        cartaoNubank.setNome("Nubank");
        cartaoNubank.setLimite(new BigDecimal("21000.00"));
        cartaoNubank.setLimiteDisponivel(new BigDecimal("19755.10"));
        cartaoNubank.setDiaFechamento(21);
        cartaoNubank.setDiaVencimento(28);
        cartaoNubank.setCorHexadecimal("#8A05BE");
        cartaoNubank.setAtivo(true);
        cartaoNubank = cartaoRepository.save(cartaoNubank);

        // 3. Transações Comuns (Depósito, Saque, Pix, Transferência)
        // Depósito
        Transacao tDep = new Transacao();
        tDep.setUsuario(usuario);
        tDep.setDescricao("Depósito Inicial");
        tDep.setValor(new BigDecimal("5000.00"));
        tDep.setTipo(TipoTransacao.DEPOSITO);
        tDep.setContaDestino(contaNubank);
        tDep.setData(LocalDate.of(2026, 6, 1));
        tDep.setAtivo(true);
        transacaoRepository.save(tDep);

        // Saque
        Transacao tSaq = new Transacao();
        tSaq.setUsuario(usuario);
        tSaq.setDescricao("Saque Banco24Horas");
        tSaq.setValor(new BigDecimal("200.00"));
        tSaq.setTipo(TipoTransacao.SAQUE);
        tSaq.setContaOrigem(contaNubank);
        tSaq.setData(LocalDate.of(2026, 6, 5));
        tSaq.setAtivo(true);
        transacaoRepository.save(tSaq);

        // Pix
        Transacao tPix = new Transacao();
        tPix.setUsuario(usuario);
        tPix.setDescricao("Pix Enviado");
        tPix.setValor(new BigDecimal("150.00"));
        tPix.setTipo(TipoTransacao.PIX);
        tPix.setContaOrigem(contaNubank);
        tPix.setData(LocalDate.of(2026, 6, 10));
        tPix.setAtivo(true);
        transacaoRepository.save(tPix);

        // Transferência
        Transacao tTransf = new Transacao();
        tTransf.setUsuario(usuario);
        tTransf.setDescricao("Transferência interna");
        tTransf.setValor(new BigDecimal("300.00"));
        tTransf.setTipo(TipoTransacao.TRANSFERENCIA);
        tTransf.setContaOrigem(contaNubank);
        tTransf.setContaDestino(contaBB);
        tTransf.setData(LocalDate.of(2026, 6, 15));
        tTransf.setAtivo(true);
        transacaoRepository.save(tTransf);

        // 4. Faturas de teste para simulação do plano de implementação
        // A) Fatura de Maio/2026 - Rollover Test (Cenário 3: 300 reais, pago 100 parcial, restam 200 pendentes)
        Fatura faturaMaio = new Fatura();
        faturaMaio.setUsuario(usuario);
        faturaMaio.setCartao(cartaoNubank);
        faturaMaio.setMesReferencia(LocalDate.of(2026, 5, 1));
        faturaMaio.setDataFechamento(LocalDate.of(2026, 5, 21));
        faturaMaio.setDataVencimento(LocalDate.of(2026, 5, 28));
        faturaMaio.setStatus(StatusFatura.FECHADA); // Deixamos fechada para simular que venceu sem pagar totalmente
        faturaMaio.setValorTotal(new BigDecimal("300.00"));
        faturaMaio.setValorPago(new BigDecimal("100.00"));
        faturaMaio = faturaRepository.save(faturaMaio);

        // Compra da Fatura de Maio
        Transacao tCompraMaio = new Transacao();
        tCompraMaio.setUsuario(usuario);
        tCompraMaio.setDescricao("Supermercado - Maio");
        tCompraMaio.setValor(new BigDecimal("300.00"));
        tCompraMaio.setTipo(TipoTransacao.COMPRA_CREDITO);
        tCompraMaio.setCartao(cartaoNubank);
        tCompraMaio.setFatura(faturaMaio);
        tCompraMaio.setCategoria(catOutros);
        tCompraMaio.setData(LocalDate.of(2026, 5, 10));
        tCompraMaio.setAtivo(true);
        transacaoRepository.save(tCompraMaio);

        // Pagamento Parcial da Fatura de Maio
        Transacao tPactoMaio = new Transacao();
        tPactoMaio.setUsuario(usuario);
        tPactoMaio.setDescricao("Pagamento Parcial de Fatura - Maio/2026");
        tPactoMaio.setValor(new BigDecimal("100.00"));
        tPactoMaio.setTipo(TipoTransacao.PAGAMENTO_CREDITO);
        tPactoMaio.setContaOrigem(contaNubank);
        tPactoMaio.setCartao(cartaoNubank);
        tPactoMaio.setFatura(faturaMaio);
        tPactoMaio.setData(LocalDate.of(2026, 5, 25));
        tPactoMaio.setAtivo(true);
        transacaoRepository.save(tPactoMaio);

        // B) Fatura de Junho/2026 - Pré-populada
        Fatura faturaJunho = new Fatura();
        faturaJunho.setUsuario(usuario);
        faturaJunho.setCartao(cartaoNubank);
        faturaJunho.setMesReferencia(LocalDate.of(2026, 6, 1));
        faturaJunho.setDataFechamento(LocalDate.of(2026, 6, 21));
        faturaJunho.setDataVencimento(LocalDate.of(2026, 6, 28));
        faturaJunho.setStatus(StatusFatura.FECHADA); // Fechada para simular pagamento total/parcial
        faturaJunho.setValorTotal(new BigDecimal("144.90"));
        faturaJunho.setValorPago(BigDecimal.ZERO);
        faturaJunho = faturaRepository.save(faturaJunho);

        // Netflix
        Transacao tNetflix = new Transacao();
        tNetflix.setUsuario(usuario);
        tNetflix.setDescricao("Assinatura: Netflix");
        tNetflix.setValor(new BigDecimal("44.90"));
        tNetflix.setTipo(TipoTransacao.COMPRA_CREDITO);
        tNetflix.setCartao(cartaoNubank);
        tNetflix.setFatura(faturaJunho);
        tNetflix.setCategoria(catServicos);
        tNetflix.setData(LocalDate.of(2026, 6, 6));
        tNetflix.setAtivo(true);
        transacaoRepository.save(tNetflix);

        // Sofa
        Transacao tSofa = new Transacao();
        tSofa.setUsuario(usuario);
        tSofa.setDescricao("Sofá (Parcela 4 de 10)");
        tSofa.setValor(new BigDecimal("100.00"));
        tSofa.setTipo(TipoTransacao.COMPRA_CREDITO);
        tSofa.setCartao(cartaoNubank);
        tSofa.setFatura(faturaJunho);
        tSofa.setCategoria(catLazer);
        tSofa.setData(LocalDate.of(2026, 6, 19));
        tSofa.setNumeroParcela(4);
        tSofa.setTotalParcelas(10);
        tSofa.setAtivo(true);
        transacaoRepository.save(tSofa);

        // Cadastro das parcelas futuras do Sofá (Parcelas 5 a 10) nas respectivas faturas futuras
        for (int p = 5; p <= 10; p++) {
            LocalDate refFutura = LocalDate.of(2026, 6, 1).plusMonths(p - 4);
            Fatura faturaFutura = getOrCreateFatura(cartaoNubank, usuario, refFutura, faturaRepository);
            
            Transacao tSofaFutura = new Transacao();
            tSofaFutura.setUsuario(usuario);
            tSofaFutura.setDescricao("Sofá (Parcela " + p + " de 10)");
            tSofaFutura.setValor(new BigDecimal("100.00"));
            tSofaFutura.setTipo(TipoTransacao.COMPRA_CREDITO);
            tSofaFutura.setCartao(cartaoNubank);
            tSofaFutura.setFatura(faturaFutura);
            tSofaFutura.setCategoria(catLazer);
            tSofaFutura.setData(LocalDate.of(2026, 6, 19));
            tSofaFutura.setNumeroParcela(p);
            tSofaFutura.setTotalParcelas(10);
            tSofaFutura.setAtivo(true);
            transacaoRepository.save(tSofaFutura);

            faturaFutura.setValorTotal(faturaFutura.getValorTotal().add(new BigDecimal("100.00")));
            faturaRepository.save(faturaFutura);
        }

        // 5. Assinaturas (Mensal e Anual)
        Assinatura subNetflix = new Assinatura();
        subNetflix.setUsuario(usuario);
        subNetflix.setCartao(cartaoNubank);
        subNetflix.setCategoria(catServicos);
        subNetflix.setNome("Netflix");
        subNetflix.setValor(new BigDecimal("44.90"));
        subNetflix.setTipoRecorrencia(TipoRecorrencia.MENSAL);
        subNetflix.setDiaCobranca(6);
        subNetflix.setDataInicio(LocalDate.of(2026, 6, 6));
        subNetflix.setDataProximaCobranca(LocalDate.of(2026, 7, 6));
        subNetflix.setAtivo(true);
        assinaturaRepository.save(subNetflix);

        Assinatura subAnual = new Assinatura();
        subAnual.setUsuario(usuario);
        subAnual.setCartao(cartaoNubank);
        subAnual.setCategoria(catLazer);
        subAnual.setNome("Crunchyroll");
        subAnual.setValor(new BigDecimal("97.90"));
        subAnual.setTipoRecorrencia(TipoRecorrencia.ANUAL);
        subAnual.setDiaCobranca(20);
        subAnual.setDataInicio(LocalDate.of(2025, 12, 20));
        subAnual.setDataProximaCobranca(LocalDate.of(2026, 12, 20));
        subAnual.setAtivo(true);
        assinaturaRepository.save(subAnual);
    }

    private Fatura getOrCreateFatura(Cartao cartao, Usuario usuario, LocalDate mesReferencia, FaturaRepository faturaRepository) {
        Optional<Fatura> existente = faturaRepository
                .findByCartaoIdAndUsuarioIdAndMesReferencia(cartao.getId(), usuario.getId(), mesReferencia);
        if (existente.isPresent()) {
            return existente.get();
        }

        int diaFechamento = cartao.getDiaFechamento();
        int diaVencimento = cartao.getDiaVencimento();

        LocalDate dataFechamento = mesReferencia.withDayOfMonth(diaFechamento);
        LocalDate dataVencimento = mesReferencia.withDayOfMonth(diaVencimento);
        if (dataVencimento.isBefore(dataFechamento)) {
            dataVencimento = dataVencimento.plusMonths(1);
        }

        Fatura fatura = new Fatura();
        fatura.setUsuario(usuario);
        fatura.setCartao(cartao);
        fatura.setMesReferencia(mesReferencia);
        fatura.setDataFechamento(dataFechamento);
        fatura.setDataVencimento(dataVencimento);
        fatura.setValorTotal(BigDecimal.ZERO);
        fatura.setValorPago(BigDecimal.ZERO);
        fatura.setStatus(StatusFatura.ABERTA);

        return faturaRepository.save(fatura);
    }
}