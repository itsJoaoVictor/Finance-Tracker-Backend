-- =============================================================================
-- V22: Valor contratado / do bem (referência opcional) em financiamentos
-- =============================================================================

ALTER TABLE financiamentos
    ADD COLUMN IF NOT EXISTS valor_contratado DECIMAL(15,2);
