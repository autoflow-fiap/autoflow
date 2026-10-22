# AutoFlow Backend

API REST do AutoFlow, responsável pelas regras de negócio, autenticação, persistência, métricas e notificações por
e-mail.

## Stack

- Java 21 e Spring Boot 3.5;
- Spring Web, Validation, Security e Data JPA;
- PostgreSQL e Flyway;
- SpringDoc OpenAPI;
- JUnit 5, Mockito, Testcontainers, ArchUnit e JaCoCo.

## Executar

Pela raiz do projeto, a forma mais simples de subir backend, frontend e banco é:

```bash
docker compose up -d --build
```

Para executar apenas a API, instale Java 21, Maven e PostgreSQL, configure as variáveis e rode:

```bash
cd backend
mvn spring-boot:run
```

A API usa a porta `8081`.

Para construir uma imagem isolada:

```bash
docker build -t autoflow-backend .
docker run --rm --env-file ../.env -p 8081:8081 autoflow-backend
```

## Configuração

Forneça credenciais por ambiente ou por `.env` local. Não coloque valores reais no código, em exemplos ou na
documentação.

| Variável                                                       | Uso                                       |
|----------------------------------------------------------------|-------------------------------------------|
| `SPRING_DATASOURCE_URL`                                        | URL JDBC do PostgreSQL                    |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`    | Acesso ao banco                           |
| `JWT_SECRET` / `JWT_EXPIRATION`                                | Assinatura e validade do JWT; `JWT_SECRET` deve ser compartilhado com a Lambda de autenticação |
| `MAIL_USERNAME` / `MAIL_PASSWORD`                              | Envio de e-mails                          |
| `APP_PUBLIC_BASE_URL`                                          | URL pública da API nos links de orçamento |
| `APP_PUBLIC_TOKEN_SECRET` / `APP_PUBLIC_TOKEN_EXPIRATION_DAYS` | Token público de orçamento                |
| `FRONTEND_PUBLIC_BASE_URL`                                     | URL pública do frontend                   |
| `CORS_ALLOWED_ORIGINS`                                         | Origens permitidas pelo CORS              |

Os defaults e a porta estão em [`application.properties`](src/main/resources/application.properties). As migrações
Flyway ficam em `src/main/resources/db/migration`.

## Arquitetura

O backend é um monólito organizado em `domain`, `application`, `infrastructure` e `presentation`:

```text
presentation → application → domain
                    ↑
             infrastructure
```

Controllers e mappers REST adaptam HTTP; a aplicação concentra portas, casos de uso, políticas e modelos internos; o
domínio mantém regras; a infraestrutura implementa persistência, segurança e integrações. O detalhamento está em [
`docs/architecture.md`](../docs/architecture.md) e [`package-convention.md`](../docs/conventions/package-convention.md).

## Funcionalidades e métricas

- usuários, clientes, veículos, serviços, peças e insumos;
- ordens de serviço, diagnóstico, estoque, execução e entrega;
- orçamentos principais e complementares, PDF, aprovação e recusa;
- acompanhamento autenticado e público por token;
- métricas de tempo médio em:
  - `GET /ordens-servico/metricas/tempo-medio`;
  - `GET /servicos/metricas/tempo-medio`.

As métricas são calculadas por adapters de persistência e expostas por casos de uso, sem expor projections do banco ao
contrato REST.

## API e documentação

Para a demonstração ponta a ponta do login serverless por CPF/CNPJ, use a
[coleção Postman](postman/autoflow-serverless-auth.postman_collection.json) e o
[roteiro de autenticação](../docs/openapi/README.md#demonstração-ponta-a-ponta-da-autenticação-serverless).

- Swagger UI: <http://localhost:8081/swagger-ui.html>;
- OpenAPI: <http://localhost:8081/v3/api-docs>;
- [OpenAPI versionado](../docs/openapi/autoflow-api.json);
- [Guia de execução da API](../docs/openapi/README.md);
- [Diagramas de sequência](../docs/diagramas-sequencia/diagrama-sequencia.md);
- [Fluxo público de orçamento](../docs/fluxo-orcamento-publico.md).

Endpoints públicos de orçamento não exigem JWT, mas exigem `token` na query string:

| Método | Rota                              | Ação                |
|--------|-----------------------------------|---------------------|
| `GET`  | `/public/orcamentos/{id}`         | Consultar orçamento |
| `GET`  | `/public/orcamentos/{id}/pdf`     | Baixar PDF          |
| `POST` | `/public/orcamentos/{id}/aprovar` | Aprovar             |
| `POST` | `/public/orcamentos/{id}/recusar` | Recusar             |

Os detalhes de validade, auditoria e efeitos na OS estão em [
`fluxo-orcamento-publico.md`](../docs/fluxo-orcamento-publico.md).

## Contas do seed

Para a demonstracao local da pos-graduacao, o seed cria contas com uma senha definida nas migrations. Consulte a configuracao local aprovada para obter credenciais de teste; nao mantenha senhas literais nesta documentacao:

| CPF/CNPJ      | E-mail                   | Perfil      |
|---------------|--------------------------|-------------|
| `52998224725` | `admin@autoflow.com`     | `ADMIN`     |
| `16899535009` | `atendente@autoflow.com` | `ATENDENTE` |
| `93541134780` | `mecanico1@autoflow.com` | `MECANICO`  |
| `15350946056` | `mecanico2@autoflow.com` | `MECANICO`  |
| `11144477735` | `cliente@autoflow.com`   | `CLIENTE`   |

As migracoes `V36` e `V40` tambem criam contas complementares; a lista completa esta
no [README principal](../README.md). Sao credenciais de demonstracao local e nao devem ser usadas em producao.

## Validações e testes

As validações incluem campos obrigatórios, CPF/CNPJ, placa, unicidade e regras de domínio. Execute:

```bash
mvn clean verify
```

O comando executa testes unitários, testes de integração `*IT`, ArchUnit, Flyway/Testcontainers quando aplicável e o
check JaCoCo configurado no Maven. Os detalhes estão em [`testing-and-quality.md`](../docs/testing-and-quality.md).

## Segurança

- endpoints privados usam JWT e `@PreAuthorize`;
- o login usa CPF/CNPJ e senha para todos os perfis; o JWT pode ser emitido pelo `/auth/login` ou pela Lambda serverless;
  ambos usam HS256, `sub` com o CPF/CNPJ e o mesmo `JWT_SECRET`;
- o perfil usado na autorização é carregado do banco pelo CPF/CNPJ do `sub`; o claim `role` do token não concede privilégios;
- endpoints públicos são separados dos fluxos autenticados;
- tokens públicos são aleatórios, expiráveis e persistidos como hash;
- senhas e segredos devem ser injetados por ambiente ou Secret;
- tokens e credenciais não devem ser registrados em logs.
