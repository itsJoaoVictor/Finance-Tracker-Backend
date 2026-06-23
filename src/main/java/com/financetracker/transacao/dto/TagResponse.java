package com.financetracker.transacao.dto;

import com.financetracker.transacao.entity.Tag;
import java.time.LocalDateTime;
import java.util.UUID;

public record TagResponse(
    UUID id,
    String nome,
    String corHexadecimal,
    LocalDateTime criadoEm
) {
    public TagResponse(Tag tag) {
        this(tag.getId(), tag.getNome(), tag.getCorHexadecimal(), tag.getCriadoEm());
    }
}