-- =============================================================================
-- V20: Desconto/economia obtida, valor original abatido e direção de abatimento
--      em contas_pagar_amortizacao
-- =============================================================================

ALTER TABLE contas_pagar_amortizacao
    ADD COLUMN IF NOT EXISTS desconto_obtido DECIMAL(15,2),
    ADD COLUMN IF NOT EXISTS valor_original_abatido DECIMAL(15,2),
    ADD COLUMN IF NOT EXISTS direcao_abatimento VARCHAR(20) NOT NULL DEFAULT 'ULTIMAS';
