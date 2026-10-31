-- =============================================================
-- SEED DE DADOS BÁSICOS PARA DESENVOLVIMENTO
-- Senha padrão de todos os usuários: Senha@1234
-- =============================================================
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS cpf_cnpj VARCHAR(18);
CREATE EXTENSION IF NOT EXISTS pgcrypto;