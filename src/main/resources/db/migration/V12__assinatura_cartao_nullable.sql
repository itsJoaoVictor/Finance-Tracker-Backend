-- Remove o FK NOT NULL para permitir soft-delete de cartões
ALTER TABLE assinaturas ALTER COLUMN cartao_id DROP NOT NULL;

-- Reconstrói o FK com ON DELETE SET NULL para que hard DELETE de cartões não falhe
ALTER TABLE assinaturas DROP CONSTRAINT IF EXISTS fk9w82hvp8jtnqw5mhp7035rrkb;
ALTER TABLE assinaturas ADD CONSTRAINT fk9w82hvp8jtnqw5mhp7035rrkb
    FOREIGN KEY (cartao_id) REFERENCES cartoes(id) ON DELETE SET NULL;
