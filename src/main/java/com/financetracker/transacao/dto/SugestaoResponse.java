package com.financetracker.transacao.dto;

import java.util.List;
import java.util.UUID;

public record SugestaoResponse(
    UUID categoriaId,
    String categoriaNome,
    List<UUID> tagIds,
    List<TagInfo> tags
) {
    public record TagInfo(UUID id, String nome, String corHexadecimal) {}
}