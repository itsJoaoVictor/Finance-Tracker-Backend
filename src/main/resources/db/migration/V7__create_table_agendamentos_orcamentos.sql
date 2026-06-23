CREATE TABLE agendamentos_transacoes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL,
    descricao VARCHAR(150) NOT NULL,
    valor DECIMAL(15,2) NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    conta_origem_id UUID,
    conta_destino_id UUID,
    categoria_id UUID NOT NULL,
    recorrencia VARCHAR(30) NOT NULL,
    dia_execucao INTEGER NOT NULL,
    data_inicio DATE NOT NULL,
    data_proxima_execucao DATE NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_agendamento_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_agendamento_conta_origem FOREIGN KEY (conta_origem_id) REFERENCES contas(id),
    CONSTRAINT fk_agendamento_conta_destino FOREIGN KEY (conta_destino_id) REFERENCES contas(id),
    CONSTRAINT fk_agendamento_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id)
);

CREATE INDEX idx_agendamento_usuario_id ON agendamentos_transacoes(usuario_id);

CREATE TABLE orcamentos_categorias (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL,
    categoria_id UUID NOT NULL,
    limite_mensal DECIMAL(15,2),
    mes_referencia DATE NOT NULL,
    CONSTRAINT fk_orcamento_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_orcamento_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id),
    CONSTRAINT uk_orcamento_usuario_categoria_mes UNIQUE (usuario_id, categoria_id, mes_referencia)
);

CREATE INDEX idx_orcamento_usuario_id ON orcamentos_categorias(usuario_id);