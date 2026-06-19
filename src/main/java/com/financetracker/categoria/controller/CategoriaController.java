package com.financetracker.categoria.controller;

import com.financetracker.categoria.dto.CategoriaRequest;
import com.financetracker.categoria.dto.CategoriaResponse;
import com.financetracker.categoria.service.CategoriaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @PostMapping
    public ResponseEntity<CategoriaResponse> criar(@Valid @RequestBody CategoriaRequest request) {
        CategoriaResponse response = categoriaService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<CategoriaResponse>> listar(
            @RequestParam(name = "somenteAtivas", required = false, defaultValue = "true") boolean somenteAtivas
    ) {
        List<CategoriaResponse> response = categoriaService.listar(somenteAtivas);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoriaResponse> buscarPorId(@PathVariable UUID id) {
        CategoriaResponse response = categoriaService.buscarPorId(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoriaResponse> editar(
            @PathVariable UUID id,
            @Valid @RequestBody CategoriaRequest request
    ) {
        CategoriaResponse response = categoriaService.editar(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable UUID id) {
        categoriaService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/ativar")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        categoriaService.ativar(id);
        return ResponseEntity.noContent().build();
    }
}
