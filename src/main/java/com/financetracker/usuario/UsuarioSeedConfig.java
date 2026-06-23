package com.financetracker.usuario;

import com.financetracker.cartao.entity.Cartao;
import com.financetracker.cartao.repository.CartaoRepository;
import com.financetracker.conta.entity.Conta;
import com.financetracker.conta.model.TipoConta;
import com.financetracker.conta.repository.ContaRepository;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

@Configuration
@Profile("seed")
public class UsuarioSeedConfig {

    @Bean
    CommandLineRunner seedUsuarios(UsuarioRepository usuarioRepository, ContaRepository contaRepository, CartaoRepository cartaoRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (!usuarioRepository.existsByEmail("existente@example.com")) {
                Usuario usuario = new Usuario("existente", "existente@example.com", passwordEncoder.encode("SenhaForte123!"));
                usuarioRepository.save(usuario);
            }
            if (!usuarioRepository.existsByEmail("teste@teste.com")) {
                Usuario usuarioTeste = new Usuario("Conta Teste", "teste@teste.com", passwordEncoder.encode("@Teste123"));
                usuarioTeste = usuarioRepository.save(usuarioTeste);

                // Reserva de Emergência (Poupança) - 500
                Conta contaReserva = new Conta();
                contaReserva.setUsuario(usuarioTeste);
                contaReserva.setNome("Reserva de Emergência");
                contaReserva.setTipo(TipoConta.POUPANCA);
                contaReserva.setSaldo(new BigDecimal("500.00"));
                contaReserva.setAtivo(true);
                contaReserva.setContaPadrao(false);
                contaReserva.setCorHexadecimal("#FF5733");
                contaReserva = contaRepository.save(contaReserva);

                // Nubank (Conta Corrente) - 12000
                Conta contaNubank = new Conta();
                contaNubank.setUsuario(usuarioTeste);
                contaNubank.setNome("Nubank");
                contaNubank.setTipo(TipoConta.CORRENTE);
                contaNubank.setSaldo(new BigDecimal("12000.00"));
                contaNubank.setAtivo(true);
                contaNubank.setContaPadrao(true);
                contaNubank.setCorHexadecimal("#8A05BE");
                contaNubank = contaRepository.save(contaNubank);

                // Banco do Brasil (Conta Corrente) - 10000
                Conta contaBB = new Conta();
                contaBB.setUsuario(usuarioTeste);
                contaBB.setNome("Banco do Brasil");
                contaBB.setTipo(TipoConta.CORRENTE);
                contaBB.setSaldo(new BigDecimal("10000.00"));
                contaBB.setAtivo(true);
                contaBB.setContaPadrao(false);
                contaBB.setCorHexadecimal("#FCF310");
                contaBB = contaRepository.save(contaBB);

                // Cartão Nubank
                Cartao cartaoNubank = new Cartao();
                cartaoNubank.setUsuario(usuarioTeste);
                cartaoNubank.setConta(contaNubank);
                cartaoNubank.setNome("Nubank");
                cartaoNubank.setLimite(new BigDecimal("21000.00"));
                cartaoNubank.setLimiteDisponivel(new BigDecimal("21000.00"));
                cartaoNubank.setDiaFechamento(21);
                cartaoNubank.setDiaVencimento(28);
                cartaoNubank.setCorHexadecimal("#8A05BE");
                cartaoNubank.setAtivo(true);
                cartaoRepository.save(cartaoNubank);

                // Cartão Banco do Brasil
                Cartao cartaoBB = new Cartao();
                cartaoBB.setUsuario(usuarioTeste);
                cartaoBB.setConta(contaBB);
                cartaoBB.setNome("Banco do Brasil");
                cartaoBB.setLimite(new BigDecimal("13000.00"));
                cartaoBB.setLimiteDisponivel(new BigDecimal("13000.00"));
                cartaoBB.setDiaFechamento(5);
                cartaoBB.setDiaVencimento(12);
                cartaoBB.setCorHexadecimal("#FCF310");
                cartaoBB.setAtivo(true);
                cartaoRepository.save(cartaoBB);
            }
        };
    }
}