-- =============================================================================
-- V19: Modos de parcelamento (FIXO, PROGRESSIVO, PERSONALIZADO), periodicidade
--      e flag de valores estimados em contas_pagar_modelo
-- =============================================================================

ALTER TABLE contas_pagar_modelo
    ADD COLUMN IF NOT EXISTS modo_parcelamento VARCHAR(20) NOT NULL DEFAULT 'FIXO',
    ADD COLUMN IF NOT EXISTS valor_ultima_parcela DECIMAL(15,2),
    ADD COLUMN IF NOT EXISTS intervalo_meses INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS valores_estimados BOOLEAN NOT NULL DEFAULT false;
