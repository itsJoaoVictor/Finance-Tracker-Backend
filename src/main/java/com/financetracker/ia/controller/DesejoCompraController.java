package com.financetracker.ia.controller;

import com.financetracker.ia.dto.DesejoCompraDTO;
import com.financetracker.ia.dto.DesejoCompraRequest;
import com.financetracker.ia.service.DesejoCompraService;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/desejos-compra")
public class DesejoCompraController {

    private final DesejoCompraService service;
    private final UsuarioRepository usuarioRepository;

    public DesejoCompraController(DesejoCompraService service, UsuarioRepository usuarioRepository) {
        this.service = service;
        this.usuarioRepository = usuarioRepository;
    }

    private Usuario getUsuarioLogado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmail(email).orElse(null);
    }

    @GetMapping
    public ResponseEntity<?> listar() {
        Usuario usuario = getUsuarioLogado();
        if (usuario == null) return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        
        List<DesejoCompraDTO> lista = service.listarDesejos(usuario);
        return ResponseEntity.ok(lista);
    }

    @PostMapping
    public ResponseEntity<?> criar(@RequestBody DesejoCompraRequest request) {
        Usuario usuario = getUsuarioLogado();
        if (usuario == null) return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));

        if (request.nome() == null || request.nome().isBlank() || request.valor() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nome e valor são obrigatórios."));
        }

        try {
            DesejoCompraDTO dto = service.criarDesejo(usuario, request);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(@PathVariable UUID id, @RequestBody DesejoCompraRequest request) {
        Usuario usuario = getUsuarioLogado();
        if (usuario == null) return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));

        if (request.nome() == null || request.nome().isBlank() || request.valor() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nome e valor são obrigatórios."));
        }

        try {
            DesejoCompraDTO dto = service.atualizarDesejo(usuario, id, request);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletar(@PathVariable UUID id) {
        Usuario usuario = getUsuarioLogado();
        if (usuario == null) return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));

        try {
            service.deletarDesejo(usuario, id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
