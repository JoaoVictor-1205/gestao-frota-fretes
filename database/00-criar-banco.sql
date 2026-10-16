-- Execute com psql conectado ao banco administrativo postgres.
-- A criação de banco precisa ficar fora de uma transação.
\set ON_ERROR_STOP on
SELECT 'CREATE DATABASE gestao_frota WITH ENCODING ''UTF8'' TEMPLATE template0'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'gestao_frota')
\gexec
\echo Banco gestao_frota disponivel. Execute 01-schema.sql conectado a ele.
