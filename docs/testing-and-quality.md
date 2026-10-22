# Testes e qualidade

## Pré-requisitos

- Java 21 e Maven;
- Docker em execução para os testes de integração com Testcontainers;
- Node.js 20+, npm 10+ e Chrome/Chromium para testes Angular headless.

O repositório não possui `mvnw` ou `mvnw.cmd`; os comandos backend usam `mvn`.

## Backend

O backend possui testes de domínio, aplicação, infraestrutura, apresentação, arquitetura e integração. Os testes de
integração seguem o sufixo `*IT.java`, usam PostgreSQL em Testcontainers e executam migrations Flyway.

```bash
cd backend
mvn clean verify
```

O `verify` executa testes unitários, integração, ArchUnit, relatório JaCoCo e a regra mínima de 80% para linhas e
branches no bundle.

Relatórios:

- HTML: `backend/target/site/jacoco/index.html`;
- XML: `backend/target/site/jacoco/jacoco.xml`;
- CSV: `backend/target/site/jacoco/jacoco.csv`;
- integração: `backend/target/failsafe-reports/`.

Os resultados do backend devem ser avaliados pelos relatórios gerados na execução.

## Frontend

```bash
cd frontend
npm ci
npm run test:ci
npm run build
```

`test:ci` executa Karma em `ChromeHeadlessCI` com cobertura. O relatório fica em:

- `frontend/coverage/frontend/index.html`;
- `frontend/coverage/frontend/lcov.info`.

Os resultados do frontend devem ser avaliados pelos relatórios gerados na execução.

## Convenções de teste

- testes unitários devem evitar iniciar o contexto Spring quando uma unidade isolada for suficiente;
- testes de aplicação usam gateways falsos ou mocks e cobrem sucesso e falha;
- testes de infraestrutura validam adapters, mapeamentos e transações;
- testes de apresentação validam rota, payload, status HTTP e autorização;
- testes de integração validam o fluxo completo com banco real em container;
- testes frontend cobrem serviços, guards, interceptors, componentes e estados de erro;
- testes arquiteturais validam a direção de dependências.

Prefira nomes que expressem comportamento, por exemplo `metodo_quandoCondicao_deveResultado`.

## Qualidade e segurança

A pipeline executa SonarQube para o backend e Snyk em pushes para `develop`. O Sonar depende do secret `SONAR_TOKEN`; o
Snyk depende de `SNYK_TOKEN`.

Consulte:

- [Checklist SOLID](quality/checklist-solid.md);
- [Arquitetura](architecture.md);
- [Convenção de pacotes](conventions/package-convention.md);
- [Fluxo e gates atuais de CI/CD](../README.md#fluxo-do-cicd).
