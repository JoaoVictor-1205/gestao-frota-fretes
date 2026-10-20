-- MySQL 8.0.16+. Execute conectado ao banco gestao_frota.
-- Reexecutar preserva todos os registros. Não altera tabelas de versões anteriores.
-- DDL no MySQL faz commit implícito; as tabelas usam InnoDB para transações e FKs.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS motorista (
    id INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    cpf VARCHAR(11) NOT NULL,
    cnh VARCHAR(11) NOT NULL,
    categoria_cnh VARCHAR(2) NOT NULL,
    telefone VARCHAR(20) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_motorista_cpf UNIQUE (cpf),
    CONSTRAINT uq_motorista_cnh UNIQUE (cnh),
    CONSTRAINT ck_motorista_nome CHECK (TRIM(nome) <> ''),
    CONSTRAINT ck_motorista_cpf CHECK (REGEXP_LIKE(cpf, '^[0-9]{11}$', 'c')),
    CONSTRAINT ck_motorista_cnh CHECK (REGEXP_LIKE(cnh, '^[0-9]{11}$', 'c')),
    CONSTRAINT ck_motorista_categoria CHECK (categoria_cnh IN ('A','B','AB','C','D','E','AC','AD','AE')),
    CONSTRAINT ck_motorista_telefone CHECK (TRIM(telefone) <> ''),
    CONSTRAINT ck_motorista_ativo CHECK (ativo IN (FALSE, TRUE))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;

CREATE TABLE IF NOT EXISTS veiculo (
    id INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
    placa VARCHAR(7) NOT NULL,
    modelo VARCHAR(100) NOT NULL,
    ano INTEGER NOT NULL,
    capacidade_kg DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'Disponível',
    CONSTRAINT uq_veiculo_placa UNIQUE (placa),
    CONSTRAINT ck_veiculo_placa CHECK (REGEXP_LIKE(placa, '^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$', 'c')),
    CONSTRAINT ck_veiculo_modelo CHECK (TRIM(modelo) <> ''),
    CONSTRAINT ck_veiculo_ano CHECK (ano BETWEEN 1900 AND 2100),
    CONSTRAINT ck_veiculo_capacidade CHECK (capacidade_kg > 0),
    CONSTRAINT ck_veiculo_status CHECK (status IN ('Disponível','Em viagem','Em manutenção'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;

CREATE TABLE IF NOT EXISTS cliente (
    id INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    cpf_cnpj VARCHAR(14) NOT NULL,
    telefone VARCHAR(20) NOT NULL,
    endereco VARCHAR(200) NOT NULL,
    CONSTRAINT uq_cliente_cpf_cnpj UNIQUE (cpf_cnpj),
    CONSTRAINT ck_cliente_nome CHECK (TRIM(nome) <> ''),
    CONSTRAINT ck_cliente_cpf_cnpj CHECK (REGEXP_LIKE(cpf_cnpj, '^([0-9]{11}|[0-9]{14})$', 'c')),
    CONSTRAINT ck_cliente_telefone CHECK (TRIM(telefone) <> ''),
    CONSTRAINT ck_cliente_endereco CHECK (TRIM(endereco) <> '')
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;

CREATE TABLE IF NOT EXISTS viagem (
    id INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
    motorista_id INTEGER NOT NULL,
    veiculo_id INTEGER NOT NULL,
    cliente_id INTEGER NOT NULL,
    origem VARCHAR(100) NOT NULL,
    destino VARCHAR(100) NOT NULL,
    data_saida DATE NOT NULL,
    data_chegada DATE,
    valor_frete DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'Planejada',
    motorista_em_andamento INTEGER GENERATED ALWAYS AS
        (CASE WHEN status = 'Em andamento' THEN motorista_id ELSE NULL END) VIRTUAL,
    veiculo_em_andamento INTEGER GENERATED ALWAYS AS
        (CASE WHEN status = 'Em andamento' THEN veiculo_id ELSE NULL END) VIRTUAL,
    CONSTRAINT fk_viagem_motorista FOREIGN KEY (motorista_id) REFERENCES motorista(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_viagem_veiculo FOREIGN KEY (veiculo_id) REFERENCES veiculo(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_viagem_cliente FOREIGN KEY (cliente_id) REFERENCES cliente(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_viagem_origem CHECK (TRIM(origem) <> ''),
    CONSTRAINT ck_viagem_destino CHECK (TRIM(destino) <> ''),
    CONSTRAINT ck_viagem_valor CHECK (valor_frete > 0),
    CONSTRAINT ck_viagem_status CHECK (status IN ('Planejada','Em andamento','Concluída','Cancelada')),
    CONSTRAINT ck_viagem_datas CHECK (data_chegada IS NULL OR data_chegada >= data_saida),
    CONSTRAINT ck_viagem_concluida CHECK (status <> 'Concluída' OR data_chegada IS NOT NULL),
    CONSTRAINT uq_viagem_motorista_andamento UNIQUE (motorista_em_andamento),
    CONSTRAINT uq_viagem_veiculo_andamento UNIQUE (veiculo_em_andamento),
    INDEX ix_viagem_motorista (motorista_id),
    INDEX ix_viagem_veiculo (veiculo_id),
    INDEX ix_viagem_cliente_saida (cliente_id, data_saida),
    INDEX ix_viagem_saida (data_saida)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;

CREATE TABLE IF NOT EXISTS manutencao (
    id INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
    veiculo_id INTEGER NOT NULL,
    data DATE NOT NULL,
    tipo VARCHAR(15) NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    custo DECIMAL(10,2) NOT NULL,
    quilometragem INTEGER NOT NULL,
    CONSTRAINT fk_manutencao_veiculo FOREIGN KEY (veiculo_id) REFERENCES veiculo(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_manutencao_tipo CHECK (tipo IN ('Preventiva','Corretiva')),
    CONSTRAINT ck_manutencao_descricao CHECK (TRIM(descricao) <> ''),
    CONSTRAINT ck_manutencao_custo CHECK (custo >= 0),
    CONSTRAINT ck_manutencao_quilometragem CHECK (quilometragem >= 0),
    INDEX ix_manutencao_veiculo_data (veiculo_id, data)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
