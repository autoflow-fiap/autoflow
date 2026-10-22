-- Corrige documentos dos clientes de demonstração e sincroniza os snapshots
-- das ordens de serviço. Não altera IDs, vínculos, ordens ou históricos.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM (VALUES
            ('ana@exemplo.com', '80657439193'),
            ('pedro@exemplo.com', '74185296355'),
            ('contato@xyz.com.br', '80657439120452'),
            ('fernanda@exemplo.com', '63920481704')
        ) AS docs(email, documento)
        JOIN usuarios u ON u.cpf_cnpj = docs.documento
        WHERE u.email <> docs.email
    ) OR EXISTS (
        SELECT 1
        FROM (VALUES
            ('ana@exemplo.com', '80657439193'),
            ('pedro@exemplo.com', '74185296355'),
            ('contato@xyz.com.br', '80657439120452'),
            ('fernanda@exemplo.com', '63920481704')
        ) AS docs(email, documento)
        JOIN clientes c ON c.cpf_cnpj = docs.documento
        WHERE c.email <> docs.email
    ) THEN
        RAISE EXCEPTION 'Documento sintético V49 já está associado a outro registro';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM (VALUES
            ('ana@exemplo.com'), ('pedro@exemplo.com'),
            ('contato@xyz.com.br'), ('fernanda@exemplo.com')
        ) AS docs(email)
        LEFT JOIN usuarios u ON u.email = docs.email
        LEFT JOIN clientes c ON c.email = docs.email
        WHERE u.id IS NULL OR c.id IS NULL OR c.usuario_id IS DISTINCT FROM u.id
    ) THEN
        RAISE EXCEPTION 'Seed de demonstração incompleto ou relação usuário/cliente inconsistente';
    END IF;
END $$;

UPDATE usuarios u
SET cpf_cnpj = docs.documento
FROM (VALUES
    ('ana@exemplo.com', '80657439193'),
    ('pedro@exemplo.com', '74185296355'),
    ('contato@xyz.com.br', '80657439120452'),
    ('fernanda@exemplo.com', '63920481704')
) AS docs(email, documento)
WHERE u.email = docs.email;

UPDATE clientes c
SET cpf_cnpj = docs.documento
FROM (VALUES
    ('ana@exemplo.com', '80657439193'),
    ('pedro@exemplo.com', '74185296355'),
    ('contato@xyz.com.br', '80657439120452'),
    ('fernanda@exemplo.com', '63920481704')
) AS docs(email, documento)
WHERE c.email = docs.email;

UPDATE ordem_servico os
SET cliente_cpf_cnpj = c.cpf_cnpj
FROM clientes c
WHERE os.cliente_id = c.id
  AND c.email IN ('ana@exemplo.com', 'pedro@exemplo.com',
                  'contato@xyz.com.br', 'fernanda@exemplo.com');
