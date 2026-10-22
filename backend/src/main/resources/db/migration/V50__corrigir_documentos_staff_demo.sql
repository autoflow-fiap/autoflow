-- Completa os documentos dos usuários de demonstração não-clientes da V40.
-- Os documentos são sintéticos; IDs, papéis, senhas e relações permanecem intactos.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM (VALUES
            ('mecanico3@autoflow.com', '58273619419', 'MECANICO'),
            ('atendente2@autoflow.com', '69427183519', 'ATENDENTE')
        ) AS docs(email, documento, role)
        JOIN usuarios u ON u.cpf_cnpj = docs.documento
        WHERE u.email <> docs.email
    ) THEN
        RAISE EXCEPTION 'Documento sintético V50 já está associado a outro usuário';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM (VALUES
            ('mecanico3@autoflow.com', '58273619419', 'MECANICO'),
            ('atendente2@autoflow.com', '69427183519', 'ATENDENTE')
        ) AS docs(email, documento, role)
        LEFT JOIN usuarios u ON u.email = docs.email
        WHERE u.id IS NULL
           OR u.role <> docs.role
           OR (u.cpf_cnpj IS NOT NULL AND u.cpf_cnpj <> docs.documento)
    ) THEN
        RAISE EXCEPTION 'Usuário de demonstração ausente, com papel divergente ou documento inesperado';
    END IF;
END $$;

UPDATE usuarios u
SET cpf_cnpj = docs.documento
FROM (VALUES
    ('mecanico3@autoflow.com', '58273619419', 'MECANICO'),
    ('atendente2@autoflow.com', '69427183519', 'ATENDENTE')
) AS docs(email, documento, role)
WHERE u.email = docs.email
  AND u.role = docs.role
  AND u.cpf_cnpj IS NULL;
