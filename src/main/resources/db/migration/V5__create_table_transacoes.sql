CREATE TABLE transacoes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL,
    descricao VARCHAR(150) NOT NULL,
    valor DECIMAL(15,2) NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    conta_origem_id UUID,
    conta_destino_id UUID,
    cartao_id UUID,
    fatura_id UUID,
    meta_origem_id UUID,
    meta_destino_id UUID,
    categoria_id UUID,
    data DATE NOT NULL,
    numero_parcela INTEGER,
    total_parcelas INTEGER,
    tipo_pagamento_fatura VARCHAR(30),
    agendamento_id UUID,
    estornada BOOLEAN NOT NULL DEFAULT false,
    ativo BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_transacao_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_transacao_conta_origem FOREIGN KEY (conta_origem_id) REFERENCES contas(id),
    CONSTRAINT fk_transacao_conta_destino FOREIGN KEY (conta_destino_id) REFERENCES contas(id),
    CONSTRAINT fk_transacao_cartao FOREIGN KEY (cartao_id) REFERENCES cartoes(id),
    CONSTRAINT fk_transacao_fatura FOREIGN KEY (fatura_id) REFERENCES faturas(id),
    CONSTRAINT fk_transacao_meta_origem FOREIGN KEY (meta_origem_id) REFERENCES metas_economia(id),
    CONSTRAINT fk_transacao_meta_destino FOREIGN KEY (meta_destino_id) REFERENCES metas_economia(id),
    CONSTRAINT fk_transacao_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id)
);

CREATE INDEX idx_transacao_usuario_id ON transacoes(usuario_id);
CREATE INDEX idx_transacao_conta_origem ON transacoes(conta_origem_id);
CREATE INDEX idx_transacao_conta_destino ON transacoes(conta_destino_id);
CREATE INDEX idx_transacao_cartao_id ON transacoes(cartao_id);
CREATE INDEX idx_transacao_fatura_id ON transacoes(fatura_id);
CREATE INDEX idx_transacao_data ON transacoes(data);