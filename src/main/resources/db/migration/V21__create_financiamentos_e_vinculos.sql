-- =============================================================================
-- V21: Tabela pai `financiamentos` (estilo Caixinha/Meta para projetos/imóveis),
--      FK `financiamento_id` em `contas_pagar_modelo` e tabela de vínculo
--      `financiamento_transacoes` para transações soltas já pagas.
-- =============================================================================

CREATE TABLE financiamentos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL,
    nome VARCHAR(120) NOT NULL,
    descricao VARCHAR(255),
    cor_hexadecimal VARCHAR(7) DEFAULT '#7c5cfc',
    valor_pago_anteriormente DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    ativo BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_fin_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

CREATE INDEX idx_fin_usuario_ativo ON financiamentos(usuario_id, ativo);

ALTER TABLE contas_pagar_modelo
    ADD COLUMN IF NOT EXISTS financiamento_id UUID;

ALTER TABLE contas_pagar_modelo
    DROP CONSTRAINT IF EXISTS fk_cpm_financiamento;

ALTER TABLE contas_pagar_modelo
    ADD CONSTRAINT fk_cpm_financiamento FOREIGN KEY (financiamento_id) REFERENCES financiamentos(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_cpm_financiamento_id ON contas_pagar_modelo(financiamento_id);

CREATE TABLE financiamento_transacoes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL,
    financiamento_id UUID NOT NULL,
    transacao_id UUID NOT NULL,
    observacao VARCHAR(255),
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_ft_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT fk_ft_financiamento FOREIGN KEY (financiamento_id) REFERENCES financiamentos(id) ON DELETE CASCADE,
    CONSTRAINT fk_ft_transacao FOREIGN KEY (transacao_id) REFERENCES transacoes(id) ON DELETE CASCADE,
    CONSTRAINT uk_ft_transacao UNIQUE (transacao_id)
);

CREATE INDEX idx_ft_financiamento_id ON financiamento_transacoes(financiamento_id);
CREATE INDEX idx_ft_usuario_id ON financiamento_transacoes(usuario_id);

ALTER TABLE financiamentos ENABLE ROW LEVEL SECURITY;
ALTER TABLE financiamento_transacoes ENABLE ROW LEVEL SECURITY;
