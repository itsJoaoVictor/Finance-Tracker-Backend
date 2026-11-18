-- =============================================================================
-- V16: Módulo Contas a Pagar (modelos, ocorrências, amortizações, notificações)
-- =============================================================================

CREATE TABLE contas_pagar_modelo (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL,
    nome VARCHAR(100) NOT NULL,
    tipo VARCHAR(20) NOT NULL, -- VARIAVEL, FIXO, PARCELADO
    categoria_id UUID NOT NULL,
    agrupador VARCHAR(50),
    dia_vencimento INTEGER NOT NULL,
    valor_padrao DECIMAL(15,2),
    total_parcelas INTEGER,
    parcela_inicial INTEGER,
    data_inicio DATE NOT NULL,
    data_fim DATE,
    debito_automatico BOOLEAN NOT NULL DEFAULT false,
    conta_padrao_id UUID,
    ativo BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_cpm_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT fk_cpm_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id),
    CONSTRAINT fk_cpm_conta_padrao FOREIGN KEY (conta_padrao_id) REFERENCES contas(id) ON DELETE SET NULL
);

CREATE INDEX idx_cpm_usuario_id ON contas_pagar_modelo(usuario_id);
CREATE INDEX idx_cpm_ativo ON contas_pagar_modelo(usuario_id, ativo);

CREATE TABLE contas_pagar_ocorrencia (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL,
    modelo_id UUID NOT NULL,
    mes_referencia DATE NOT NULL,
    numero_parcela INTEGER,
    data_vencimento DATE NOT NULL,
    valor_estimado DECIMAL(15,2),
    valor_real DECIMAL(15,2),
    valor_pago DECIMAL(15,2),
    data_pagamento DATE,
    status VARCHAR(20) NOT NULL, -- AGUARDANDO_VALOR, PENDENTE, PAGA, CANCELADA
    linha_digitavel VARCHAR(100),
    observacao VARCHAR(255),
    transacao_id UUID,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_cpo_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT fk_cpo_modelo FOREIGN KEY (modelo_id) REFERENCES contas_pagar_modelo(id) ON DELETE CASCADE,
    CONSTRAINT fk_cpo_transacao FOREIGN KEY (transacao_id) REFERENCES transacoes(id) ON DELETE SET NULL,
    CONSTRAINT uk_cpo_modelo_mes UNIQUE (modelo_id, mes_referencia)
);

CREATE INDEX idx_cpo_usuario_id ON contas_pagar_ocorrencia(usuario_id);
CREATE INDEX idx_cpo_modelo_id ON contas_pagar_ocorrencia(modelo_id);
CREATE INDEX idx_cpo_vencimento ON contas_pagar_ocorrencia(usuario_id, data_vencimento);
CREATE INDEX idx_cpo_transacao_id ON contas_pagar_ocorrencia(transacao_id);

CREATE TABLE contas_pagar_amortizacao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL,
    modelo_id UUID NOT NULL,
    valor DECIMAL(15,2) NOT NULL,
    data DATE NOT NULL,
    origem VARCHAR(20) NOT NULL, -- CONTA, EXTERNA
    conta_id UUID,
    modo VARCHAR(20) NOT NULL, -- REDUZIR_PRAZO, REDUZIR_PARCELA
    parcelas_eliminadas INTEGER,
    novo_valor_parcela DECIMAL(15,2),
    observacao VARCHAR(255),
    transacao_id UUID,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_cpa_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT fk_cpa_modelo FOREIGN KEY (modelo_id) REFERENCES contas_pagar_modelo(id) ON DELETE CASCADE,
    CONSTRAINT fk_cpa_conta FOREIGN KEY (conta_id) REFERENCES contas(id) ON DELETE SET NULL,
    CONSTRAINT fk_cpa_transacao FOREIGN KEY (transacao_id) REFERENCES transacoes(id) ON DELETE SET NULL
);

CREATE INDEX idx_cpa_modelo_id ON contas_pagar_amortizacao(modelo_id);

-- Configuração de notificações externas (Telegram + Web Push)
CREATE TABLE notificacao_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL UNIQUE,
    telegram_chat_id VARCHAR(60),
    telegram_ativo BOOLEAN NOT NULL DEFAULT false,
    web_push_ativo BOOLEAN NOT NULL DEFAULT false,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notif_cfg_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

CREATE TABLE push_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL,
    endpoint TEXT NOT NULL,
    p256dh VARCHAR(255) NOT NULL,
    auth VARCHAR(255) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_push_sub_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT uk_push_sub_endpoint UNIQUE (usuario_id, endpoint)
);

CREATE TABLE notificacoes_enviadas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ocorrencia_id UUID NOT NULL,
    gatilho VARCHAR(40) NOT NULL,
    enviado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notif_env_ocorrencia FOREIGN KEY (ocorrencia_id) REFERENCES contas_pagar_ocorrencia(id) ON DELETE CASCADE,
    CONSTRAINT uk_notif_env_ocorrencia_gatilho UNIQUE (ocorrencia_id, gatilho)
);

-- Ativar RLS nas novas tabelas
ALTER TABLE contas_pagar_modelo ENABLE ROW LEVEL SECURITY;
ALTER TABLE contas_pagar_ocorrencia ENABLE ROW LEVEL SECURITY;
ALTER TABLE contas_pagar_amortizacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE notificacao_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE push_subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE notificacoes_enviadas ENABLE ROW LEVEL SECURITY;

-- Atualizar CHECK constraint de ia_insights com os novos tipos de Contas a Pagar
ALTER TABLE ia_insights DROP CONSTRAINT IF EXISTS ia_insights_tipo_check;

ALTER TABLE ia_insights ADD CONSTRAINT ia_insights_tipo_check CHECK (tipo IN (
    'CARTAO_PREVISAO',
    'COBRANCA_DUPLICADA',
    'PROJECAO_PARCELAS',
    'MELHOR_CARTAO',
    'FADIGA_ASSINATURA',
    'REAJUSTE_SILENCIOSO',
    'ASSINATURA_ESQUECIDA',
    'SUGESTAO_VENCIMENTO',
    'ESTOURO_FATURA',
    'AVISO_FECHAMENTO',
    'MICRO_TRANSACOES',
    'ORCAMENTO_SOBRA_META',
    'DINHEIRO_DORMINDO',
    'RADAR_FIM_SEMANA',
    'QUEDA_RECEITA',
    'REFORCO_POSITIVO',
    'ACELERADOR_METAS',
    'INFLACAO_PESSOAL',
    'CONCENTRACAO_GASTOS_FATURA',
    'OTIMIZACAO_PARCELAMENTO',
    'EFEITO_DOMINO',
    'CONTA_ACIMA_MEDIA',
    'CONTA_VENCENDO',
    'CONTA_ATRASADA',
    'CONTA_SEM_VALOR'
));
