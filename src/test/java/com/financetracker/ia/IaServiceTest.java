package com.financetracker.ia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.categoria.entity.Categoria;
import com.financetracker.categoria.repository.CategoriaRepository;
import com.financetracker.ia.domain.IaDicionarioCategoria;
import com.financetracker.ia.domain.IaInsight;
import com.financetracker.ia.domain.TipoInsight;
import com.financetracker.ia.repository.IaCorrecaoUsuarioRepository;
import com.financetracker.ia.repository.IaDicionarioCategoriaRepository;
import com.financetracker.ia.repository.IaInsightRepository;
import com.financetracker.ia.service.IaService;
import com.financetracker.transacao.entity.Transacao;
import com.financetracker.transacao.repository.TransacaoRepository;
import com.financetracker.usuario.entity.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class IaServiceTest {

    @InjectMocks
    private IaService iaService;

    @Mock
    private IaInsightRepository iaInsightRepository;

    @Mock
    private IaDicionarioCategoriaRepository iaDicionarioCategoriaRepository;

    @Mock
    private IaCorrecaoUsuarioRepository iaCorrecaoUsuarioRepository;

    @Mock
    private TransacaoRepository transacaoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void deveHigienizarEAnonimizarDescricaoConformeLGPD() {
        // Teste de LGPD e FinOps (RN-12 e RN-13)
        String raw = "Compra Uber *Eats 123456 por joao@teste.com cpf 123.456.789-00 - 06/23";
        String clean = iaService.higienizarDescricao(raw);

        assertEquals("COMPRA UBER *EATS  POR [EMAIL] CPF [CPF]", clean);
    }

    @Test
    public void deveRetornarCategoriaDoCacheSeExistir() {
        UUID catId = UUID.randomUUID();
        Categoria mockCategoria = new Categoria();
        mockCategoria.setId(catId);
        mockCategoria.setNome("Alimentação");

        IaDicionarioCategoria cacheEntry = new IaDicionarioCategoria("UBER *EATS", mockCategoria);

        when(iaDicionarioCategoriaRepository.findById("UBER *EATS")).thenReturn(Optional.of(cacheEntry));

        var res = iaService.categorizarTransacao("UBER *EATS", UUID.randomUUID());

        assertEquals("Alimentação", res.get("categoriaSugerida"));
        assertEquals(catId, res.get("categoriaId"));
        verify(iaDicionarioCategoriaRepository, times(1)).findById("UBER *EATS");
    }
}
