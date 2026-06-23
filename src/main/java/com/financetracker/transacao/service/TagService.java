package com.financetracker.transacao.service;

import com.financetracker.transacao.dto.TagCriacaoRequest;
import com.financetracker.transacao.dto.TagResponse;
import com.financetracker.transacao.dto.SugestaoResponse;
import com.financetracker.transacao.entity.Tag;
import com.financetracker.transacao.entity.Transacao;
import com.financetracker.transacao.repository.TagRepository;
import com.financetracker.transacao.repository.TransacaoRepository;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class TagService {

    private final TagRepository tagRepository;
    private final TransacaoRepository transacaoRepository;
    private final UsuarioRepository usuarioRepository;

    public TagService(TagRepository tagRepository,
                      TransacaoRepository transacaoRepository,
                      UsuarioRepository usuarioRepository) {
        this.tagRepository = tagRepository;
        this.transacaoRepository = transacaoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    private Usuario getAuthenticatedUsuario() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autenticado"));
    }

    @Transactional
    public TagResponse criar(TagCriacaoRequest request) {
        Usuario usuario = getAuthenticatedUsuario();
        Tag tag = new Tag();
        tag.setUsuario(usuario);
        tag.setNome(request.nome());
        tag.setCorHexadecimal(request.corHexadecimal());
        tag.setAtivo(true);
        return new TagResponse(tagRepository.save(tag));
    }

    @Transactional(readOnly = true)
    public List<TagResponse> listar() {
        Usuario usuario = getAuthenticatedUsuario();
        return tagRepository.findByUsuarioIdAndAtivoTrue(usuario.getId())
                .stream().map(TagResponse::new).toList();
    }

    @Transactional(readOnly = true)
    public SugestaoResponse sugerir(String descricao) {
        Usuario usuario = getAuthenticatedUsuario();
        if (descricao == null || descricao.isBlank()) {
            return null;
        }

        List<Transacao> historico = transacaoRepository.findTopByDescricaoLike(usuario.getId(), descricao);
        if (historico.isEmpty()) return null;

        // Find most frequent category
        Map<UUID, Long> categoryCount = new HashMap<>();
        Map<UUID, String> categoryNames = new HashMap<>();

        for (Transacao t : historico) {
            if (t.getCategoria() != null) {
                UUID catId = t.getCategoria().getId();
                categoryCount.merge(catId, 1L, Long::sum);
                categoryNames.putIfAbsent(catId, t.getCategoria().getNome());
            }
        }

        UUID bestCategory = categoryCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        if (bestCategory == null) return null;

        return new SugestaoResponse(bestCategory, categoryNames.get(bestCategory),
                List.of(), List.of());
    }
}