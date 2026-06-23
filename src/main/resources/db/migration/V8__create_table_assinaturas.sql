CREATE TABLE assinaturas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL,
    cartao_id UUID NOT NULL,
    categoria_id UUID NOT NULL,
    nome VARCHAR(100) NOT NULL,
    valor DECIMAL(15,2) NOT NULL,
    tipo_recorrencia VARCHAR(30) NOT NULL,
    frequencia INTEGER,
    unidade_frequencia VARCHAR(20),
    dia_cobranca INTEGER NOT NULL,
    data_inicio DATE NOT NULL,
    data_proxima_cobranca DATE NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_assinatura_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_assinatura_cartao FOREIGN KEY (cartao_id) REFERENCES cartoes(id),
    CONSTRAINT fk_assinatura_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id)
);

CREATE INDEX idx_assinatura_usuario_id ON assinaturas(usuario_id);
CREATE INDEX idx_assinatura_cartao_id ON assinaturas(cartao_id);