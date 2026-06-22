CREATE TABLE categorias (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID,
    nome VARCHAR(100) NOT NULL,
    icone VARCHAR(50) NOT NULL,
    cor_hexadecimal VARCHAR(7) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_categoria_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

CREATE INDEX idx_categoria_usuario_id ON categorias(usuario_id);
