# Fluxo de orçamento público

Este documento descreve o fluxo de consulta, download, aprovação e recusa de
orçamentos por link público. O fluxo não exige login, mas exige um token
aleatório, armazenado somente como hash no banco e com prazo de validade.

## Visão geral

1. O backend gera um token aleatório quando publica o orçamento.
2. O banco armazena o hash do token em `public_token_hash` e a expiração em
   `public_token_expira_em`.
3. O cliente recebe no e-mail o link da pagina:
   `/public/orcamentos/{orcamentoId}?token={token}`.
4. A página consulta o orçamento e permite baixar o PDF, aprovar ou recusar.
5. As ações de decisão são enviadas por `POST`, evitando alterar o estado
   apenas pela abertura do link ou por scanners de e-mail.

O token bruto nunca e persistido. O mesmo token tambem pode ser usado para
baixar o PDF enquanto estiver valido.

## Publicação

A publicação ocorre nos seguintes pontos:

- finalização do diagnóstico, para o orçamento principal;
- criação de reparo adicional, para o orçamento complementar.

A publicação gera dois links:

| Link                                                            | Finalidade                           |
|-----------------------------------------------------------------|--------------------------------------|
| `app.public-base-url/public/orcamentos/{id}/pdf?token=...`      | Download direto do PDF pela API      |
| `app.frontend-public-base-url/public/orcamentos/{id}?token=...` | Página pública de consulta e decisão |

O e-mail informa os dois usos: download do PDF e acesso à página de decisão.

## Endpoints públicos

Todos os endpoints abaixo são acessíveis sem `Authorization: Bearer`.

### Consultar o orçamento

```http
GET /public/orcamentos/{orcamentoId}?token={token}
```

Retorna `OrcamentoResponse` sem dados de autenticação, hash do token ou
identificadores sensíveis do cliente.

### Baixar o PDF

```http
GET /public/orcamentos/{orcamentoId}/pdf?token={token}
```

O PDF continua disponível por token público válido. Token inválido, expirado ou
ausente não libera o arquivo.

### Aprovar

```http
POST /public/orcamentos/{orcamentoId}/aprovar?token={token}
Content-Type: application/json

{
  "nome": "Maria da Silva"
}
```

O corpo é opcional. Quando `nome` não é informado, o sistema usa o nome do
cliente presente no snapshot do orçamento para a assinatura.

### Recusar

```http
POST /public/orcamentos/{orcamentoId}/recusar?token={token}
Content-Type: application/json

{
  "nome": "Maria da Silva",
  "motivo": "Valor acima do esperado"
}
```

O corpo é opcional. O motivo, quando informado, possui limite de 500
caracteres; o nome da assinatura possui limite de 120 caracteres.

## Regras de decisão

- O token deve corresponder ao hash do orçamento e estar dentro do prazo.
- Somente orçamentos `DISPONIVEL` aceitam uma nova decisão.
- Repetir a mesma decisão é idempotente: aprovar um orçamento `APROVADO` ou
  recusar um orçamento `REPROVADO` não executa novamente os efeitos.
- Decisões conflitantes são rejeitadas. Um orçamento `REPROVADO` não pode ser
  aprovado e um `APROVADO` não pode ser recusado.
- A decisão usa lock pessimista no orçamento para evitar concorrência entre
  duas requisicoes simultaneas.

## Efeitos na OS

| Tipo do orçamento | Aprovação                                  | Recusa                                           |
|-------------------|--------------------------------------------|--------------------------------------------------|
| Principal         | OS `AGUARDANDO_APROVACAO` -> `EM_EXECUCAO` | OS segue a regra vigente e vai para `FINALIZADA` |
| Complementar      | Atualiza o reparo adicional vinculado      | Recusa o reparo adicional vinculado              |

Os efeitos são executados pelos mesmos casos de uso das decisões autenticadas;
o token público apenas fornece uma forma segura de entrada.

## Auditoria

A decisão grava os seguintes dados no orçamento:

- `aprovado_em`, quando aprovado;
- `reprovado_em`, quando recusado;
- `assinatura_nome`, quando houver nome informado ou nome do cliente;
- `recusa_motivo`, quando houver motivo.

O hash e a expiração do token ficam no registro do orçamento. O valor bruto do
token não é gravado em logs ou no banco.

## Erros

As regras de negocio usam o formato padrao da API:

```json
{
  "erro": "Token invalido ou expirado"
}
```

| Situacao                                      | HTTP esperado      |
|-----------------------------------------------|--------------------|
| Orçamento inexistente                         | `404 Not Found`    |
| Token inválido, expirado ou divergente        | `401 Unauthorized` |
| Orçamento indisponível ou decisão conflitante | `400 Bad Request`  |
| Campo acima do limite                         | `400 Bad Request`  |

Em uma decisão rejeitada por token, o orçamento e a OS não sofrem alteração.

## Configuracao

O prazo padrão é de 7 dias e pode ser alterado por ambiente:

```bash
APP_PUBLIC_TOKEN_EXPIRATION_DAYS=7
APP_PUBLIC_TOKEN_SECRET=use-um-segredo-longo-e-aleatorio
APP_PUBLIC_BASE_URL=https://api.exemplo.com
FRONTEND_PUBLIC_BASE_URL=https://app.exemplo.com
```

Em produção, configure um segredo forte e não use o valor padrão de
desenvolvimento. A alteração de banco foi implementada pela migration
`V44__add_expiracao_token_publico_orcamento.sql`; migrations anteriores não
devem ser editadas.

## Validação

Os testes relacionados estão em:

- `backend/src/test/java/com/autoflow/presentation/orcamento/PublicOrcamentoControllerTest.java`;
- `backend/src/test/java/com/autoflow/application/usecases/orcamento/DecidirOrcamentoUseCaseTest.java`;
- `backend/src/test/java/com/autoflow/infrastructure/orcamento/OrcamentoPublicacaoServiceImplTest.java`;
- `backend/src/test/java/com/autoflow/integration/PublicAcompanhamentoIT.java`;
- `frontend/src/app/features/public/orcamento-publico.service.spec.ts`.

A especificação OpenAPI atualizada pode ser consultada no Swagger UI enquanto o
backend estiver em execução.
