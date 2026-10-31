ALTER TABLE clientes ADD COLUMN status VARCHAR(7);

UPDATE clientes
SET status = 'ATIVO'
WHERE status IS NULL;

ALTER TABLE clientes
    ALTER COLUMN status SET DEFAULT 'ATIVO';

ALTER TABLE clientes
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE clientes
    ADD CONSTRAINT ck_clientes_status
    CHECK (status IN ('ATIVO', 'INATIVO'));
