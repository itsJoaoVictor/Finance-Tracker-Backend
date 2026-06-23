package com.financetracker.transacao.controller;

import com.financetracker.transacao.dto.TagCriacaoRequest;
import com.financetracker.transacao.dto.TagResponse;
import com.financetracker.transacao.service.TagService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    @PostMapping
    public ResponseEntity<TagResponse> criar(@Valid @RequestBody TagCriacaoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tagService.criar(request));
    }

    @GetMapping
    public ResponseEntity<List<TagResponse>> listar() {
        return ResponseEntity.ok(tagService.listar());
    }

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }
}