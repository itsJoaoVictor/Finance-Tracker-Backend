package com.financetracker.assinatura.controller;

import com.financetracker.assinatura.dto.*;
import com.financetracker.assinatura.service.AssinaturaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assinaturas")
public class AssinaturaController {

    private final AssinaturaService assinaturaService;

    public AssinaturaController(AssinaturaService assinaturaService) {
        this.assinaturaService = assinaturaService;
    }

    @PostMapping
    public ResponseEntity<AssinaturaResponse> criar(@Valid @RequestBody AssinaturaCriacaoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assinaturaService.criar(request));
    }

    @GetMapping
    public ResponseEntity<List<AssinaturaResponse>> listar() {
        return ResponseEntity.ok(assinaturaService.listar());
    }

    @GetMapping("/proximas")
    public ResponseEntity<List<AssinaturaProximaResponse>> proximas(
            @RequestParam(defaultValue = "7") int dias) {
        return ResponseEntity.ok(assinaturaService.proximas(dias));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssinaturaResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(assinaturaService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AssinaturaResponse> editar(
            @PathVariable UUID id,
            @Valid @RequestBody AssinaturaEdicaoRequest request) {
        return ResponseEntity.ok(assinaturaService.editar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        assinaturaService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pausar")
    public ResponseEntity<Void> pausar(@PathVariable UUID id) {
        assinaturaService.pausar(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reativar")
    public ResponseEntity<Void> reativar(@PathVariable UUID id) {
        assinaturaService.reativar(id);
        return ResponseEntity.ok().build();
    }
}