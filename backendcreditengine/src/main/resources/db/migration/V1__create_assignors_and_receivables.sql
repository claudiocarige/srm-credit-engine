CREATE TABLE assignors (
    id UUID NOT NULL,
    document VARCHAR(14) NOT NULL,
    name VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_assignors PRIMARY KEY (id),
    CONSTRAINT uk_assignors_document UNIQUE (document),
    CONSTRAINT ck_assignors_document_digits CHECK (document ~ '^[0-9]{11,14}$'),
    CONSTRAINT ck_assignors_name_not_blank CHECK (length(btrim(name)) > 0)
);

CREATE TABLE receivables (
    id UUID NOT NULL,
    assignor_id UUID NOT NULL,
    document_number VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    face_value_brl NUMERIC(19, 2) NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_receivables PRIMARY KEY (id),
    CONSTRAINT fk_receivables_assignor FOREIGN KEY (assignor_id) REFERENCES assignors (id) ON DELETE RESTRICT,
    CONSTRAINT uk_receivables_assignor_document_number UNIQUE (assignor_id, document_number),
    CONSTRAINT ck_receivables_document_number_format CHECK (document_number ~ '^[A-Z0-9._/-]{1,64}$'),
    CONSTRAINT ck_receivables_type CHECK (type IN ('DUPLICATA_MERCANTIL', 'CHEQUE_PRE_DATADO')),
    CONSTRAINT ck_receivables_face_value_brl_range CHECK (face_value_brl BETWEEN 0.01 AND 999999999.99),
    CONSTRAINT ck_receivables_status CHECK (status IN ('OPEN', 'SETTLED'))
);

