package com.financetracker.config;

import com.financetracker.conta.exception.ContaNaoEncontradaException;
import com.financetracker.conta.exception.LimitaContasException;
import com.financetracker.cartao.exception.CartaoNaoEncontradoException;
import com.financetracker.cartao.exception.LimiteCartoesException;
import com.financetracker.cartao.exception.LimiteDisponivelInvalidoException;
import com.financetracker.categoria.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ContaNaoEncontradaException.class)
    public ResponseEntity<Map<String, String>> handleContaNaoEncontrada(ContaNaoEncontradaException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(CartaoNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handleCartaoNaoEncontrada(CartaoNaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(LimitaContasException.class)
    public ResponseEntity<Map<String, String>> handleLimitaContas(LimitaContasException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(LimiteCartoesException.class)
    public ResponseEntity<Map<String, String>> handleLimiteCartoes(LimiteCartoesException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(LimiteDisponivelInvalidoException.class)
    public ResponseEntity<Map<String, String>> handleLimiteDisponivelInvalido(LimiteDisponivelInvalidoException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(CategoriaNaoEncontradaException.class)
    public ResponseEntity<Map<String, String>> handleCategoriaNaoEncontrada(CategoriaNaoEncontradaException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(CategoriaGlobalImutavelException.class)
    public ResponseEntity<Map<String, String>> handleCategoriaGlobalImutavel(CategoriaGlobalImutavelException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(NomeCategoriaDuplicadoException.class)
    public ResponseEntity<Map<String, String>> handleNomeCategoriaDuplicado(NomeCategoriaDuplicadoException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(LimiteCategoriasException.class)
    public ResponseEntity<Map<String, String>> handleLimiteCategorias(LimiteCategoriasException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Dados inválidos");
        body.put("campos", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}

