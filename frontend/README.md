# AutoFlow Frontend

SPA Angular para operação da oficina e acompanhamento do cliente.

## Stack

- Angular 17 e Angular Material 17;
- TypeScript 5.4 e RxJS 7.8;
- Node.js 20+ e npm 10+;
- Nginx unprivileged na imagem de produção.

## Executar localmente

```bash
npm ci
npm start
```

Acesse <http://localhost:4200>. O servidor de desenvolvimento encaminha `/api` para `http://localhost:8081` por [`proxy.conf.json`](proxy.conf.json); o backend precisa estar ativo.

## Build, testes e Docker

```bash
npm run test:ci
npm run build
```

O build é gerado em `dist/frontend`. Pela raiz do projeto, a solução completa pode ser iniciada com:

```bash
docker compose up -d --build frontend
```

Para uma imagem isolada:

```bash
docker build -t autoflow-frontend .
docker run --rm -p 4200:8080 -e BACKEND_URL=<url-do-backend> autoflow-frontend
```

O Nginx serve a aplicação na porta `8080` do container e encaminha `/api` para `BACKEND_URL`.

## Estrutura

| Diretório          | Responsabilidade                                      |
|--------------------|-------------------------------------------------------|
| `src/app/core`     | autenticação, guards, interceptors e serviços globais |
| `src/app/features` | telas de negócio e fluxos públicos                    |
| `src/app/layout`   | shell, navegação e filtros por perfil                 |
| `src/environments` | configurações de desenvolvimento e produção           |

Os componentes são standalone e as rotas usam lazy loading.

## Rotas

| Rota                        | Perfis                     | Finalidade                                |
|-----------------------------|----------------------------|-------------------------------------------|
| `/login`                    | Público                    | Autenticação                              |
| `/public/acompanhamento`    | Público                    | Acompanhamento por token                  |
| `/public/orcamentos/:id`    | Público                    | Consulta e decisão de orçamento por token |
| `/dashboard`                | ADMIN, ATENDENTE, MECANICO | Métricas e atalhos                        |
| `/clientes`, `/usuarios`    | ADMIN, ATENDENTE           | Cadastros administrativos                 |
| `/veiculos`                 | ADMIN, ATENDENTE, CLIENTE  | Veículos                                  |
| `/servicos`, `/peca-insumo` | ADMIN, ATENDENTE, MECANICO | Catálogos e estoque                       |
| `/ordens-servico`           | ADMIN, ATENDENTE, MECANICO | Lista e execução de OS                    |
| `/ordens-servico/nova`      | ADMIN, ATENDENTE           | Abertura de OS                            |
| `/ordens-servico/:numeroOs` | ADMIN, ATENDENTE, MECANICO | Detalhe e ações da OS                     |
| `/orcamentos`               | ADMIN, ATENDENTE           | Lista de orçamentos                       |
| `/orcamentos/:id`           | ADMIN, ATENDENTE, CLIENTE  | Detalhe, PDF e decisão                    |
| `/reparos-adicionais`       | ADMIN, ATENDENTE           | Reparos complementares                    |
| `/minha-conta/**`           | CLIENTE                    | Perfil e ordens próprias                  |

As autorizações são aplicadas por `authGuard` e `roleGuard`. Os fluxos públicos não exigem login, mas exigem token quando definido pelo backend.

## Login por CPF/CNPJ

O frontend envia `cpf_cnpj` e `senha` para `POST /auth/login` no host atual. Em homologação, o host é o endpoint HTTPS do Amazon API Gateway HTTP API, que invoca a Lambda nativamente. As chamadas de negócio e os assets usam a mesma origem.

As APIs de negócio usam o prefixo relativo `/api`. O JWT retornado é enviado nas chamadas ao backend por `Authorization: Bearer`. O backend e a Lambda precisam usar o mesmo `JWT_SECRET`, provisionado fora do frontend. A senha continua sendo credencial secreta; CPF/CNPJ é o identificador de login.

O seed disponibiliza contas de demonstração local. Consulte as migrations e a configuração local para dados de teste; não use contas ou senhas de demonstração em produção e não copie documentos pessoais para documentação pública.

## Configuração da API

Os ambientes versionados usam o prefixo `/api`. Em desenvolvimento, o proxy aponta para `localhost:8081`. Na imagem Docker, `BACKEND_URL` é lido pelo template do Nginx em tempo de execução.

## Fluxos e documentação

- [README principal](../README.md);
- [README do backend](../backend/README.md);
- [Diagramas de sequência](../docs/diagramas-sequencia/diagrama-sequencia.md);
- [Fluxo público de orçamento](../docs/fluxo-orcamento-publico.md).
