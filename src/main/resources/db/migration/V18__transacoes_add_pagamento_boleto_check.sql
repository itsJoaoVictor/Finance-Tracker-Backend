ALTER TABLE transacoes DROP CONSTRAINT IF EXISTS transacoes_tipo_check;

ALTER TABLE transacoes ADD CONSTRAINT transacoes_tipo_check CHECK (tipo IN (
    'DEPOSITO',
    'SAQUE',
    'TRANSFERENCIA',
    'PIX',
    'COMPRA_CREDITO',
    'PAGAMENTO_CREDITO',
    'PAGAMENTO_BOLETO'
));
