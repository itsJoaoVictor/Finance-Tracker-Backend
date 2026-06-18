CREATE TABLE contas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL,
    nome VARCHAR(100) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    saldo DECIMAL(15,2) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT true,
    conta_padrao BOOLEAN NOT NULL DEFAULT false,
    cor_hexadecimal VARCHAR(7),
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_conta_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE INDEX idx_conta_usuario_id ON contas(usuario_id);
