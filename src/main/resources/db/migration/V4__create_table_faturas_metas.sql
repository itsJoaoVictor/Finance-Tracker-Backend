CREATE TABLE faturas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL,
    cartao_id UUID NOT NULL,
    mes_referencia DATE NOT NULL,
    data_fechamento DATE NOT NULL,
    data_vencimento DATE NOT NULL,
    valor_total DECIMAL(15,2) NOT NULL,
    valor_pago DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(30) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_fatura_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_fatura_cartao FOREIGN KEY (cartao_id) REFERENCES cartoes(id)
);

CREATE INDEX idx_fatura_usuario_id ON faturas(usuario_id);
CREATE INDEX idx_fatura_cartao_id ON faturas(cartao_id);

CREATE TABLE metas_economia (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL,
    nome VARCHAR(100) NOT NULL,
    valor_alvo DECIMAL(15,2) NOT NULL,
    valor_acumulado DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    conta_vinculada_id UUID NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_meta_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_meta_conta FOREIGN KEY (conta_vinculada_id) REFERENCES contas(id)
);

CREATE INDEX idx_meta_usuario_id ON metas_economia(usuario_id);