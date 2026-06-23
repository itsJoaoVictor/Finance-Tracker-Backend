CREATE TABLE tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL,
    nome VARCHAR(50) NOT NULL,
    cor_hexadecimal VARCHAR(7) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_tag_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE INDEX idx_tag_usuario_id ON tags(usuario_id);

CREATE TABLE transacoes_tags (
    transacao_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    PRIMARY KEY (transacao_id, tag_id),
    CONSTRAINT fk_tt_transacao FOREIGN KEY (transacao_id) REFERENCES transacoes(id),
    CONSTRAINT fk_tt_tag FOREIGN KEY (tag_id) REFERENCES tags(id)
);