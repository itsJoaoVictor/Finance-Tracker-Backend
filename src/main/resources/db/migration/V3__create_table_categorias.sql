CREATE TABLE categorias (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID,
    nome VARCHAR(100) NOT NULL,
    icone VARCHAR(50) NOT NULL,
    cor_hexadecimal VARCHAR(7) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_categoria_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE INDEX idx_categoria_usuario_id ON categorias(usuario_id);

-- Seed de Categorias Globais
INSERT INTO categorias (nome, icone, cor_hexadecimal, usuario_id) VALUES
('Alimentação', 'shopping-basket', '#FF9F43', NULL),
('Transporte', 'car', '#0984E3', NULL),
('Moradia', 'home', '#E84118', NULL),
('Saúde', 'heartbeat', '#2ED573', NULL),
('Educação', 'graduation-cap', '#9B59B6', NULL),
('Lazer', 'smile', '#F1C40F', NULL),
('Outros', 'help-circle', '#7F8C8D', NULL);
