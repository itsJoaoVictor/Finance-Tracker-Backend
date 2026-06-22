package com.financetracker.categoria.service;

import com.financetracker.categoria.dto.CategoriaCriacaoRequest;
import com.financetracker.categoria.dto.CategoriaEdicaoRequest;
import com.financetracker.categoria.dto.CategoriaResponse;
import com.financetracker.categoria.entity.Categoria;
import com.financetracker.categoria.exception.*;
import com.financetracker.categoria.repository.CategoriaRepository;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;

    public CategoriaService(CategoriaRepository categoriaRepository, UsuarioRepository usuarioRepository) {
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    private Usuario getAuthenticatedUsuario() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autenticado"));
    }

    @Transactional
    public CategoriaResponse criar(CategoriaCriacaoRequest request) {
        Usuario usuario = getAuthenticatedUsuario();

        // RN-06 — Limite de 50 categorias customizadas ativas
        long totalAtivas = categoriaRepository.countByUsuarioIdAndAtivoTrue(usuario.getId());
        if (totalAtivas >= 50) {
            throw new LimiteCategoriasException();
        }

        // RN-03 — Unicidade de nome de categoria ativa
        if (categoriaRepository.existsByNomeAtivoAndUsuarioId(request.nome(), usuario.getId())) {
            throw new NomeCategoriaDuplicadoException();
        }

        Categoria categoria = new Categoria(usuario, request.nome(), request.icone(), request.corHexadecimal(), true);
        return new CategoriaResponse(categoriaRepository.save(categoria));
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listar(boolean somenteAtivas) {
        Usuario usuario = getAuthenticatedUsuario();
        List<Categoria> categorias = somenteAtivas
                ? categoriaRepository.findAtivasByUsuarioId(usuario.getId())
                : categoriaRepository.findAllByUsuarioId(usuario.getId());
        return categorias.stream().map(CategoriaResponse::new).toList();
    }

    @Transactional(readOnly = true)
    public CategoriaResponse buscarPorId(UUID id) {
        Usuario usuario = getAuthenticatedUsuario();
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(CategoriaNaoEncontradaException::new);

        // RN-01 — Proteção Anti-IDOR e Escopo de Acesso (GET)
        if (categoria.getUsuario() != null && !categoria.getUsuario().getId().equals(usuario.getId())) {
            throw new CategoriaNaoEncontradaException();
        }

        return new CategoriaResponse(categoria);
    }

    @Transactional
    public CategoriaResponse editar(UUID id, CategoriaEdicaoRequest request) {
        Usuario usuario = getAuthenticatedUsuario();
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(CategoriaNaoEncontradaException::new);

        // RN-02 — Imutabilidade de Categorias Globais
        if (categoria.getUsuario() == null) {
            throw new CategoriaGlobalImutavelException();
        }

        // RN-01 — Proteção Anti-IDOR e Escopo de Acesso (PUT)
        if (!categoria.getUsuario().getId().equals(usuario.getId())) {
            throw new CategoriaNaoEncontradaException();
        }

        // RN-03 — Unicidade de nome de categoria ativa
        if (categoriaRepository.existsByNomeAtivoAndUsuarioIdExcludeId(request.nome(), usuario.getId(), id)) {
            throw new NomeCategoriaDuplicadoException();
        }

        categoria.setNome(request.nome());
        categoria.setIcone(request.icone());
        categoria.setCorHexadecimal(request.corHexadecimal());

        return new CategoriaResponse(categoriaRepository.save(categoria));
    }

    @Transactional
    public void excluirPermanentemente(UUID id) {
        Usuario usuario = getAuthenticatedUsuario();
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(CategoriaNaoEncontradaException::new);

        // RN-02 — Imutabilidade de Categorias Globais
        if (categoria.getUsuario() == null) {
            throw new CategoriaGlobalImutavelException();
        }

        // RN-01 — Proteção Anti-IDOR
        if (!categoria.getUsuario().getId().equals(usuario.getId())) {
            throw new CategoriaNaoEncontradaException();
        }

        // Hard delete — remove fisicamente do banco
        categoriaRepository.delete(categoria);
    }

    @Transactional
    public void excluir(UUID id) {
        Usuario usuario = getAuthenticatedUsuario();
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(CategoriaNaoEncontradaException::new);

        // RN-02 — Imutabilidade de Categorias Globais
        if (categoria.getUsuario() == null) {
            throw new CategoriaGlobalImutavelException();
        }

        // RN-01 — Proteção Anti-IDOR e Escopo de Acesso (DELETE)
        if (!categoria.getUsuario().getId().equals(usuario.getId())) {
            throw new CategoriaNaoEncontradaException();
        }

        // RN-04 — Soft Delete
        categoria.setAtivo(false);
        categoriaRepository.save(categoria);
    }

    @Transactional
    public void ativar(UUID id) {
        Usuario usuario = getAuthenticatedUsuario();
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(CategoriaNaoEncontradaException::new);

        // RN-02 — Imutabilidade de Categorias Globais
        if (categoria.getUsuario() == null) {
            throw new CategoriaGlobalImutavelException();
        }

        // RN-01 — Proteção Anti-IDOR
        if (!categoria.getUsuario().getId().equals(usuario.getId())) {
            throw new CategoriaNaoEncontradaException();
        }

        // RN-05 — Reativação / RN-03 unicidade
        if (categoriaRepository.existsByNomeAtivoAndUsuarioIdExcludeId(categoria.getNome(), usuario.getId(), id)) {
            throw new NomeCategoriaDuplicadoException();
        }

        categoria.setAtivo(true);
        categoriaRepository.save(categoria);
    }
}
