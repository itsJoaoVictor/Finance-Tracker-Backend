package com.financetracker.cartao.controller;

import com.financetracker.cartao.dto.*;
import com.financetracker.cartao.service.CartaoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cartoes")
public class CartaoController {

    private final CartaoService cartaoService;

    public CartaoController(CartaoService cartaoService) {
        this.cartaoService = cartaoService;
    }

    @PostMapping
    public ResponseEntity<CartaoResponse> criar(@Valid @RequestBody CartaoCriacaoRequest request) {
        CartaoResponse response = cartaoService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<CartaoResponse>> listar() {
        return ResponseEntity.ok(cartaoService.listar());
    }

    @GetMapping("/resumo")
    public ResponseEntity<CartaoResumoResponse> resumo() {
        return ResponseEntity.ok(cartaoService.resumo());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CartaoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(cartaoService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CartaoResponse> editar(
            @PathVariable UUID id,
            @Valid @RequestBody CartaoEdicaoRequest request
    ) {
        return ResponseEntity.ok(cartaoService.editar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        cartaoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
