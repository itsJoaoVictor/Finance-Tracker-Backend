package com.financetracker.categoria.service;

import com.financetracker.categoria.dto.CategoriaRequest;
import com.financetracker.categoria.dto.CategoriaResponse;
import com.financetracker.categoria.entity.Categoria;
import com.financetracker.categoria.exception.CategoriaGlobalImutavelException;
import com.financetracker.categoria.exception.CategoriaNaoEncontradaException;
import com.financetracker.categoria.exception.LimiteCategoriasException;
import com.financetracker.categoria.exception.NomeCategoriaDuplicadoException;
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

    private Categoria getAndValidateAccess(UUID id, UUID usuarioId, boolean isWriteOperation) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new CategoriaNaoEncontradaException("Categoria não encontrada."));

        // RN-01 - Proteção Anti-IDOR
        if (categoria.getUsuario() != null && !categoria.getUsuario().getId().equals(usuarioId)) {
            throw new CategoriaNaoEncontradaException("Categoria não encontrada.");
        }

        // RN-02 - Imutabilidade de Categorias Globais
        if (isWriteOperation && categoria.getUsuario() == null) {
            throw new CategoriaGlobalImutavelException("Não é permitido alterar ou inativar categorias padrão do sistema.");
        }

        return categoria;
    }

    @Transactional
    public CategoriaResponse criar(CategoriaRequest request) {
        Usuario usuario = getAuthenticatedUsuario();
        UUID usuarioId = usuario.getId();

        // RN-06 - Limite de Categorias Customizadas
        long totalAtivas = categoriaRepository.countByUsuarioIdAndAtivoTrue(usuarioId);
        if (totalAtivas >= 50) {
            throw new LimiteCategoriasException("Limite máximo de 50 categorias customizadas atingido.");
        }

        // RN-03 - Unicidade de Nome
        if (categoriaRepository.existsByNomeIgnoreCaseAndActiveAndVisible(request.nome(), usuarioId)) {
            throw new NomeCategoriaDuplicadoException("Já existe uma categoria ativa com este nome.");
        }

        Categoria categoria = new Categoria();
        categoria.setUsuario(usuario);
        categoria.setNome(request.nome());
        categoria.setIcone(request.icone());
        categoria.setCorHexadecimal(request.corHexadecimal());
        categoria.setAtivo(true);

        return new CategoriaResponse(categoriaRepository.save(categoria));
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listar(boolean somenteAtivas) {
        Usuario usuario = getAuthenticatedUsuario();
        List<Categoria> categorias = somenteAtivas 
                ? categoriaRepository.findAllVisibleAndActive(usuario.getId())
                : categoriaRepository.findAllVisible(usuario.getId());

        return categorias.stream()
                .map(CategoriaResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoriaResponse buscarPorId(UUID id) {
        Usuario usuario = getAuthenticatedUsuario();
        Categoria categoria = getAndValidateAccess(id, usuario.getId(), false);
        return new CategoriaResponse(categoria);
    }

    @Transactional
    public CategoriaResponse editar(UUID id, CategoriaRequest request) {
        Usuario usuario = getAuthenticatedUsuario();
        UUID usuarioId = usuario.getId();

        Categoria categoria = getAndValidateAccess(id, usuarioId, true);

        // RN-03 - Unicidade de Nome excluindo a própria categoria
        if (categoriaRepository.existsByNomeIgnoreCaseAndActiveAndVisibleExcludingId(request.nome(), usuarioId, id)) {
            throw new NomeCategoriaDuplicadoException("Já existe uma categoria ativa com este nome.");
        }

        categoria.setNome(request.nome());
        categoria.setIcone(request.icone());
        categoria.setCorHexadecimal(request.corHexadecimal());

        return new CategoriaResponse(categoriaRepository.save(categoria));
    }

    @Transactional
    public void inativar(UUID id) {
        Usuario usuario = getAuthenticatedUsuario();
        Categoria categoria = getAndValidateAccess(id, usuario.getId(), true);
        categoria.setAtivo(false);
        categoriaRepository.save(categoria);
    }

    @Transactional
    public void ativar(UUID id) {
        Usuario usuario = getAuthenticatedUsuario();
        UUID usuarioId = usuario.getId();

        Categoria categoria = getAndValidateAccess(id, usuarioId, true);

        // RN-05 - Verificar unicidade ao reativar
        if (categoriaRepository.existsByNomeIgnoreCaseAndActiveAndVisible(categoria.getNome(), usuarioId)) {
            throw new NomeCategoriaDuplicadoException("Já existe uma categoria ativa com este nome.");
        }

        categoria.setAtivo(true);
        categoriaRepository.save(categoria);
    }
}
