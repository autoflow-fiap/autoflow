# Contrato versionado das APIs

Este diretório entrega a especificação OpenAPI versionada e distribuível da API do AutoFlow:

- [autoflow-api.json](autoflow-api.json): contrato completo, com 47 rotas e 56 operações;
- [fluxo de orçamento público](../fluxo-orcamento-publico.md): regras de token, validade, auditoria e efeitos no
  orçamento.

O arquivo JSON é o artefato distribuível da API. Ele complementa o Swagger gerado em runtime sem manter uma collection
Postman duplicada. A documentação interativa continua disponível em `http://localhost:8081/swagger-ui.html` e o contrato
gerado pela aplicação em `http://localhost:8081/v3/api-docs`.

## Variáveis

Use estas variáveis ao importar o OpenAPI em uma ferramenta compatível ou ao montar as requisições manualmente:

| Variável            | Valor local sugerido         | Uso                                           |
|---------------------|------------------------------|-----------------------------------------------|
| `baseUrl`           | `http://localhost:8081`      | URL da API sem o prefixo `/api`               |
| `authBaseUrl`       | `<url-base-do-api-gateway>`  | URL base da autenticação serverless           |
| `frontendBaseUrl`   | `http://localhost:4200`      | URL do frontend, inclusive nos links públicos |
| `jwtToken`          | `<token-retornado-no-login>` | Bearer token dos endpoints privados           |
| `publicBudgetToken` | `<token-do-link-publico>`    | Token de acompanhamento do orçamento          |
| `numeroOs`          | `<numero-da-os>`             | Identificador da ordem de serviço             |
| `servicoId`         | `<id-do-servico>`            | Identificador do serviço                      |
| `pecaInsumoId`      | `<id-da-peca-ou-insumo>`     | Identificador do item de estoque              |
| `orcamentoId`       | `<id-do-orcamento>`          | Identificador do orçamento                    |
| `clienteCpfCnpj`    | `<cpf-ou-cnpj-do-cliente>`   | Documento usado na abertura da OS             |

Não versionar JWTs, tokens públicos, senhas ou valores reais de ambiente. O prefixo `/api` é usado apenas pelo proxy do
frontend; chamadas diretas ao backend usam `baseUrl` sem esse prefixo.

## Autenticação

1. Obtenha um JWT:

```http
POST {{baseUrl}}/auth/login
Content-Type: application/json

{
  "cpfCnpj": "<cpf-ou-cnpj-do-usuario>",
  "senha": "<senha-do-ambiente>"
}
```

Resposta esperada:

```json
{
  "token": "<jwt-emitido-em-runtime>"
}
```

2. Envie o token nas rotas privadas:

```http
Authorization: Bearer {{jwtToken}}
```

O perfil do usuário (`ADMIN`, `ATENDENTE`, `MECANICO` ou `CLIENTE`) determina as operações permitidas. As rotas públicas
de orçamento e acompanhamento não usam JWT: recebem `?token={{publicBudgetToken}}`. O token público deve ser tratado
como
credencial, não deve ser registrado em logs e possui validade configurada pelo ambiente.

## Ordem sugerida de execução

1. `POST /auth/login` e armazenamento do `jwtToken`.
2. Cadastro ou consulta de cliente, veículo, serviço, peça e insumo.
3. `POST /ordens-servico` para abrir a OS.
4. `PATCH /ordens-servico/{numeroOs}/mecanico` para atribuir o mecânico.
5. Executar o diagnóstico: iniciar, registrar itens necessários, registrar laudo e finalizar.
6. Consultar o orçamento por JWT ou pelo link público e aprovar/recusar.
7. Iniciar e finalizar os serviços da OS.
8. `PATCH /ordens-servico/{numeroOs}/entregar` para concluir a entrega.
9. Consultar o acompanhamento público da OS quando houver token de acompanhamento.

Cada operação está descrita individualmente no [OpenAPI versionado](autoflow-api.json), incluindo parâmetros, perfis,
corpos e respostas. A sequência acima é apenas o fluxo operacional recomendado; não altera o contrato.

## Demonstração ponta a ponta da autenticação serverless

O login serverless e o login interno são fluxos distintos e continuam coexistindo:

| Etapa | Endpoint | Credencial | Resultado |
|---|---|---|---|
| 1 | `POST {{authBaseUrl}}/auth/login` na Lambda/API Gateway | CPF/CNPJ e senha | JWT HS256 com `sub` igual ao CPF/CNPJ normalizado |
| 2 | `GET {{backendBaseUrl}}/clientes/me` | `Authorization: Bearer <token>` | Dados do cliente autenticado, HTTP `200` |
| Interno | `POST {{baseUrl}}/auth/login` no backend | CPF/CNPJ e senha | Login por CPF/CNPJ de `ADMIN`, `ATENDENTE`, `MECANICO` e `CLIENTE` |

Importe a [coleção Postman](../../backend/postman/autoflow-serverless-auth.postman_collection.json), configure
`authBaseUrl`, `backendBaseUrl`, `cpfCnpj` e `senha`, e execute as requisições na ordem numérica. A primeira requisição
armazena o token automaticamente na variável `token`.

Uma resposta de login bem-sucedida tem o formato:

```json
{
  "token": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

O mesmo token pode ser demonstrado no Swagger do backend em `http://localhost:8081/swagger-ui.html`: selecione
`Authorize`, informe o JWT e execute `GET /clientes/me`. O login por CPF/CNPJ não aparece como operação do Swagger do
backend porque é publicado separadamente pela API Gateway; o `POST /auth/login` do Swagger documenta o login interno
por CPF/CNPJ.

### Cenários negativos reproduzíveis

| Cenário | Onde executar | Resultado esperado |
|---|---|---|
| CPF/CNPJ ou senha incorretos | Login serverless | HTTP `401`, sem token |
| Documento ou corpo inválido | Login serverless | HTTP `400`, sem token |
| JWT malformado ou com assinatura inválida | Qualquer rota protegida do backend | HTTP `403` |
| JWT expirado | Qualquer rota protegida do backend | HTTP `403` |

Para reproduzir a expiração de ponta a ponta sem versionar segredo, use um ambiente controlado: configure
`JWT_EXPIRES_IN_SECONDS=1` na Lambda, faça o login, aguarde mais de um segundo e envie o token para
`GET /clientes/me`. Depois restaure o valor normal do ambiente. A coleção também possui uma requisição dedicada para
esse caso por meio da variável `expiredToken`.

Os testes automatizados correspondentes ficam em `autoflow-lambda/test_handler.py`,
`backend/src/test/java/com/autoflow/config/security/service/JwtServiceTest.java`,
`backend/src/test/java/com/autoflow/config/security/JwtFilterTest.java` e
`backend/src/test/java/com/autoflow/integration/AuthIT.java`.

## Cobertura de endpoints

As rotas abaixo são as 47 rotas presentes no artefato. Quando há mais de um método na mesma rota, cada operação possui
descrição e respostas próprias no JSON.

| Método      | Rota                                                                | Acesso principal                             |
|-------------|---------------------------------------------------------------------|----------------------------------------------|
| POST        | `/auth/cadastro`                                                    | público                                      |
| POST        | `/auth/login`                                                       | público                                      |
| GET         | `/auth/usuarios`                                                    | ADMIN                                        |
| GET         | `/auth/mecanicos`                                                   | ADMIN, ATENDENTE                             |
| GET, POST   | `/usuarios`                                                         | ADMIN, ATENDENTE                             |
| GET         | `/clientes/me`                                                      | CLIENTE                                      |
| GET, POST   | `/clientes`                                                         | ADMIN, ATENDENTE; autenticado                |
| GET         | `/clientes/{id}`                                                    | ADMIN, ATENDENTE                             |
| PATCH       | `/clientes/{id}/atualizacao`                                        | ADMIN, ATENDENTE                             |
| DELETE      | `/clientes/{id}`                                                    | ADMIN                                        |
| GET         | `/clientes/me/ordens-servico`                                       | ADMIN, CLIENTE                               |
| GET, POST   | `/veiculos`                                                         | ADMIN, ATENDENTE, CLIENTE; autenticado       |
| GET, DELETE | `/veiculos/{id}`                                                    | ADMIN, ATENDENTE, CLIENTE; ADMIN             |
| PATCH       | `/veiculos/{id}/atualizacao`                                        | ADMIN, ATENDENTE, CLIENTE                    |
| GET, POST   | `/servicos`                                                         | ADMIN, ATENDENTE, MECANICO; ADMIN, MECANICO  |
| GET, DELETE | `/servicos/{id}`                                                    | ADMIN, ATENDENTE, MECANICO; ADMIN            |
| PATCH       | `/servicos/{id}/atualizacao`                                        | ADMIN, MECANICO                              |
| GET         | `/servicos/metricas/tempo-medio`                                    | ADMIN                                        |
| GET, POST   | `/peca-insumo`                                                      | ADMIN, ATENDENTE, MECANICO                   |
| GET, DELETE | `/peca-insumo/{id}`                                                 | ADMIN, ATENDENTE, MECANICO; ADMIN, ATENDENTE |
| PATCH       | `/peca-insumo/{id}/atualizacao`                                     | ADMIN, ATENDENTE, MECANICO                   |
| GET, POST   | `/ordens-servico`                                                   | ADMIN, ATENDENTE; ADMIN, ATENDENTE, MECANICO |
| GET         | `/ordens-servico/{numeroOs}`                                        | ADMIN, ATENDENTE, MECANICO                   |
| GET         | `/ordens-servico/{numeroOs}/status`                                 | ADMIN, ATENDENTE, MECANICO, CLIENTE          |
| GET         | `/ordens-servico/metricas/tempo-medio`                              | ADMIN                                        |
| POST        | `/ordens-servico/{numeroOs}/servicos`                               | ADMIN, ATENDENTE, MECANICO                   |
| PATCH       | `/ordens-servico/{numeroOs}/mecanico`                               | ADMIN, ATENDENTE                             |
| PATCH       | `/ordens-servico/{numeroOs}/diagnostico/iniciar`                    | ADMIN, MECANICO                              |
| PATCH       | `/ordens-servico/{numeroOs}/servicos/{servicoId}/itens-necessarios` | ADMIN, MECANICO                              |
| PATCH       | `/ordens-servico/{numeroOs}/diagnostico/laudo`                      | ADMIN, MECANICO                              |
| PATCH       | `/ordens-servico/{numeroOs}/diagnostico/finalizar`                  | ADMIN, MECANICO                              |
| PATCH       | `/ordens-servico/{numeroOs}/servicos/{servicoId}/iniciar`           | ADMIN, MECANICO                              |
| PATCH       | `/ordens-servico/{numeroOs}/servicos/{servicoId}/finalizar`         | ADMIN, MECANICO                              |
| PATCH       | `/ordens-servico/{numeroOs}/entregar`                               | ADMIN, ATENDENTE                             |
| POST        | `/ordens-servico/{numeroOs}/reparos-adicionais`                     | ADMIN, MECANICO                              |
| GET         | `/orcamentos`                                                       | ADMIN, ATENDENTE, CLIENTE                    |
| GET         | `/orcamentos/{orcamentoId}`                                         | ADMIN, ATENDENTE, CLIENTE                    |
| POST        | `/orcamentos/{orcamentoId}/aprovar`                                 | ADMIN, CLIENTE                               |
| POST        | `/orcamentos/{orcamentoId}/recusar`                                 | ADMIN, CLIENTE                               |
| GET         | `/orcamentos/{orcamentoId}/pdf`                                     | ADMIN, ATENDENTE, CLIENTE                    |
| GET         | `/public/orcamentos/{orcamentoId}`                                  | token público                                |
| GET         | `/public/orcamentos/{orcamentoId}/pdf`                              | token público                                |
| POST        | `/public/orcamentos/{orcamentoId}/aprovar`                          | token público                                |
| POST        | `/public/orcamentos/{orcamentoId}/recusar`                          | token público                                |
| GET         | `/public/orcamentos/{orcamentoId}/pdf/acompanhamento`               | token público                                |
| POST        | `/public/orcamentos/{orcamentoId}/aprovar/acompanhamento`           | token público                                |
| GET         | `/public/ordens-servico/acompanhamento`                             | token de acompanhamento                      |

## Exemplos e erros

### Abrir uma ordem de serviço

```http
POST {{baseUrl}}/ordens-servico
Authorization: Bearer {{jwtToken}}
Content-Type: application/json

{
  "cpfCnpj": "<cpf-ou-cnpj-do-cliente>",
  "veiculo": {
    "placa": "ABC1D23",
    "marca": "Toyota",
    "modelo": "Corolla",
    "ano": 2022
  },
  "servicosSolicitados": [
    { "servicoId": "<id-do-servico>" }
  ]
}
```

O corpo e a resposta completos estão em `CriarOrdemServicoRequest` e nos schemas de resposta do OpenAPI. O número da OS
retornado deve alimentar `numeroOs` nas etapas seguintes.

### Aprovar um orçamento por link público

```http
POST {{baseUrl}}/public/orcamentos/{{orcamentoId}}/aprovar?token={{publicBudgetToken}}
Content-Type: application/json

{
  "nome": "<nome-do-cliente>"
}
```

As respostas de PDF usam `application/pdf`. Os principais erros são:

| HTTP | Situação                                                | Formato esperado                                                      |
|-----:|---------------------------------------------------------|-----------------------------------------------------------------------|
|  400 | validação, JSON inválido ou regra de negócio            | `{"erro":"<mensagem>"}`                                               |
|  401 | JWT ausente/inválido ou token público inválido/expirado | `{"erro":"<mensagem>"}`                                               |
|  403 | perfil sem autorização para a operação                  | `{"erro":"<mensagem>"}`                                               |
|  404 | recurso inexistente ou acompanhamento não encontrado    | `{"erro":"<mensagem>"}` ou texto simples em handlers públicos legados |
|  409 | duplicidade ou conflito de estado                       | `{"erro":"<mensagem>"}`                                               |

Os códigos e schemas de cada operação estão repetidos no contrato para permitir validação automática. Mensagens são
exemplificativas; a aplicação pode detalhá-las conforme a regra violada.

## Validação local

Valide o arquivo versionado sem expor credenciais:

```powershell
$spec = Get-Content docs/openapi/autoflow-api.json -Raw | ConvertFrom-Json
$spec.openapi
$spec.paths.PSObject.Properties.Count
```

Para comparar com a especificação publicada pela aplicação, suba o ambiente e consulte:

```powershell
docker compose up -d --build
Invoke-RestMethod http://localhost:8081/v3/api-docs | Out-Null
```

O primeiro comando requer Docker e as variáveis do `.env` local. O segundo confirma que o SpringDoc está acessível em
runtime; a revisão do contrato versionado deve confirmar os 47 caminhos e 56 operações documentados neste diretório.
