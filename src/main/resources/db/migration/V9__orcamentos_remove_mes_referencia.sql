-- Remove a coluna mes_referencia da tabela orcamentos_categorias
-- e altera a constraint unique para (usuario_id, categoria_id) apenas.
-- Orçamentos passam a valer para todos os meses automaticamente.

-- 1. Remove a constraint unique antiga
ALTER TABLE orcamentos_categorias
    DROP CONSTRAINT IF EXISTS uk_orcamento_usuario_categoria_mes;

-- 2. Remove a coluna mes_referencia
ALTER TABLE orcamentos_categorias
    DROP COLUMN IF EXISTS mes_referencia;

-- 3. Cria a nova constraint unique por usuario + categoria
ALTER TABLE orcamentos_categorias
    ADD CONSTRAINT uk_orcamento_usuario_categoria UNIQUE (usuario_id, categoria_id);
