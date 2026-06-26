-- Atualiza a CHECK constraint da tabela ia_insights para incluir os 2 novos
-- tipos de insight de cartão: CONCENTRACAO_GASTOS_FATURA, OTIMIZACAO_PARCELAMENTO
-- E adiciona índice parcial para prevenir duplicatas de insights não-lidos

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
    'OTIMIZACAO_PARCELAMENTO'
));

-- Remove duplicatas existentes antes de criar o índice
DELETE FROM ia_insights
WHERE id NOT IN (
    SELECT MIN(id) FROM ia_insights
    GROUP BY usuario_id, tipo, titulo
);

-- Índice parcial: previne duplicatas de insights não-lidos por tipo+titulo+usuario
-- Tipos com card dedicado (só 1 por usuário)
CREATE UNIQUE INDEX IF NOT EXISTS idx_ia_insights_uniq_melhor_cartao
    ON ia_insights (usuario_id, titulo)
    WHERE tipo = 'MELHOR_CARTAO' AND lido = false;

CREATE UNIQUE INDEX IF NOT EXISTS idx_ia_insights_uniq_queda_receita
    ON ia_insights (usuario_id, titulo)
    WHERE tipo = 'QUEDA_RECEITA' AND lido = false;

CREATE UNIQUE INDEX IF NOT EXISTS idx_ia_insights_uniq_reforco_positivo
    ON ia_insights (usuario_id, titulo)
    WHERE tipo = 'REFORCO_POSITIVO' AND lido = false;

-- Tipos com card por cartão (1 por cartão por usuário)
CREATE UNIQUE INDEX IF NOT EXISTS idx_ia_insights_uniq_estouro_fatura
    ON ia_insights (usuario_id, metadados)
    WHERE tipo = 'ESTOURO_FATURA' AND lido = false;

CREATE UNIQUE INDEX IF NOT EXISTS idx_ia_insights_uniq_aviso_fechamento
    ON ia_insights (usuario_id, metadados)
    WHERE tipo = 'AVISO_FECHAMENTO' AND lido = false;

CREATE UNIQUE INDEX IF NOT EXISTS idx_ia_insights_uniq_concentracao_gastos
    ON ia_insights (usuario_id, metadados)
    WHERE tipo = 'CONCENTRACAO_GASTOS_FATURA' AND lido = false;

CREATE UNIQUE INDEX IF NOT EXISTS idx_ia_insights_uniq_otimizacao_parcelamento
    ON ia_insights (usuario_id, metadados)
    WHERE tipo = 'OTIMIZACAO_PARCELAMENTO' AND lido = false;
