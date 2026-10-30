# AutoFlow

Sistema de gerenciamento de ordens de serviço para oficinas mecânicas. Controla o ciclo completo de atendimento:
abertura de OS, diagnóstico, orçamento, execução e encerramento, com controle de acesso por perfil e área do cliente.

---

## Estrutura do Repositório

```
autoflow/
├── backend/    # API REST — Java 21 + Spring Boot
└── frontend/   # SPA — Angular 17 + Angular Material
```

---
## Documentação do Projeto

Este README é o ponto de entrada da entrega. Para os detalhes técnicos, consulte:

- [Backend](backend/README.md): API Spring Boot, configuração, testes, Swagger e Docker.
- [Frontend](frontend/README.md): aplicação Angular, rotas, configuração e execução.
- [Arquitetura do backend](docs/architecture.md).
- [Diagramas de sequência](docs/diagramas-sequencia/diagrama-sequencia.md).
- [Fluxo de orçamento público](docs/fluxo-orcamento-publico.md).
- [Contrato OpenAPI versionado](docs/openapi/autoflow-api.json) e [documentação da API](docs/openapi/README.md).

---

## Stack

Java 21 e Spring Boot; Angular; PostgreSQL; Docker; Kubernetes; Terraform; GitHub Actions; OpenAPI/Swagger;
JUnit, SonarQube e Snyk.

---

## Arquitetura

### Backend — Clean Architecture

O AutoFlow é um monólito Spring Boot organizado segundo a Clean Architecture. O backend possui as camadas
`presentation`, `application`, `domain`, `infrastructure` e `config`.

A direção principal das dependências é `presentation → application → domain`. A camada `infrastructure` implementa
detalhes técnicos e as portas definidas pela `application`, enquanto `config` realiza a composição da aplicação.

Os detalhes da organização e das regras arquiteturais estão em [`docs/architecture.md`](docs/architecture.md).

### Frontend — Angular Standalone + Signals

O frontend usa componentes standalone, lazy loading, Signals, guards funcionais e interceptors JWT. Para rotas,
estrutura e instruções específicas, consulte o [README do frontend](frontend/README.md).

---

## Funcionalidades

O sistema oferece cadastro de clientes, veículos, serviços e peças; abertura e execução de ordens de serviço;
orçamentos com PDF e aprovação autenticada ou pública; reparos adicionais; notificações por e-mail; e área do cliente.

---

## Dados de demonstração

As migrations de desenvolvimento incluem dados para avaliação local. Consulte a configuração local e as migrations para obter os dados de teste. Não publique CPF/CNPJ ou senhas e não use dados de demonstração em produção.

---

## Principais Fluxos

O sistema cobre o ciclo completo da OS, geração e aprovação de orçamentos, download de PDF, reparos adicionais e
acompanhamento público por token. Consulte os [diagramas de sequência](docs/diagramas-sequencia/diagrama-sequencia.md)
e o [fluxo de orçamento público](docs/fluxo-orcamento-publico.md) para os detalhes.

Para rotas, estrutura e instruções específicas da aplicação web, consulte o [README do frontend](frontend/README.md).

---

## Execução local

Pré-requisitos: Java 21, Maven, Node.js 20, npm, PostgreSQL e, opcionalmente, Docker. Os detalhes de cada aplicação
estão nos READMEs do [backend](backend/README.md) e do [frontend](frontend/README.md).

```bash
# backend
cd backend
mvn spring-boot:run

# frontend, em outro terminal
cd frontend
npm install
npm start
```

O backend fica disponível em `http://localhost:8081` e o frontend em `http://localhost:4200`.

## Docker Compose

Na raiz do repositório, suba a stack completa com:

```bash
docker compose up -d
```

## Kubernetes local 

Os manifestos Kubernetes da entrega em [k8s/](k8s/) e do perfil local em [k8s-local/](k8s-local/) estão versionados.
Manifests `secret.yaml` com valores não são versionados; em um clone novo, crie o Secret local a partir do modelo sem
credenciais e preencha-o somente com valores locais. É necessário ter Minikube, Kind ou Kubernetes habilitado no Docker
Desktop:

Os manifests de `k8s-local/` são para desenvolvimento local. Nos clusters dedicados de homologação e produção, os
Services NodePort privados são alcançáveis pelo ALB interno integrado ao API Gateway. Após reset do Learner Lab,
reexecute as pipelines CI/CD para provisionar novamente os recursos do ambiente.

```bash
cd k8s-local
cp secret.yaml.example secret.yaml
# Preencha localmente os campos POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD,
# SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD, JWT_SECRET,
# MAIL_PASSWORD e APP_PUBLIC_TOKEN_SECRET antes de aplicar.
minikube start
kubectl apply -f configmap.yaml
kubectl apply -f secret.yaml
kubectl apply -f postgresql-service.yaml
kubectl apply -f postgresql-statefulset.yaml
kubectl apply -f .
kubectl get pods
```

O frontend fica disponível em `http://localhost:30180` e o backend em `http://localhost:30080`.

---

## Terraform na AWS

O código Terraform de deploy da aplicação está em [infra/](infra/). A plataforma EKS, o API Gateway HTTP, o VPC Link,
o ALB interno, IAM e os recursos compartilhados pertencem ao repositório
[autoflow-infra](https://github.com/autoflow-fiap/autoflow-infra). Este repositório gerencia Deployments, Services
internos/NodePort privados e a configuração Nginx do frontend.

O root `infra/` usa backend S3 e recebe bucket e keys pelo GitHub Environment selecionado. `develop` implanta em
`homolog`; `main` implanta em `production`. Cada ambiente aponta para a conta AWS, bucket e credenciais próprios.
Quando os ambientes usam a mesma conta e bucket, suas keys precisam ser distintas. A key de produção usa o prefixo
`production/`; a aplicação e o RDS sempre usam keys diferentes. O workflow verifica a identidade AWS esperada, a
região, o owner do bucket e a separação das keys antes de inicializar Terraform.

| Branch | GitHub Environment | Cluster EKS esperado | API Gateway HTTP esperada |
|---|---|---|---|
| `develop` | `homolog` | `eks-autoflow` | `autoflow-homolog` |
| `main` | `production` | `eks-autoflow-production` | `autoflow-production` |

O workflow seleciona o recurso com o nome exato do ambiente, mesmo quando a conta contém outros clusters ou APIs. O
root `infra/` recebe `AWS_TERRAFORM_STATE_BUCKET`, `AWS_TF_STATE_KEY`, `AWS_RDS_STATE_KEY`,
`API_GATEWAY_CORS_ORIGINS`, região, cluster e endpoint HTTPS da API. Credenciais, account ID, endpoints e conteúdo
de Terraform State permanecem fora do repositório. Consulte o [catálogo de variables e secrets](docs/variables-and-secrets.md)
para a configuração dos Environments e [Operação de CI/CD](.github/CI-CD-AWS.md) para a ordem de provisionamento.
Não há Ingress, plugin ou upstream Kong na arquitetura de aplicação.

Para a configuração por nome de GitHub Actions, Terraform, runtime e Kubernetes, consulte o [catálogo de variables e secrets](docs/variables-and-secrets.md) e o [histórico](docs/variables-changelog.md). Esta seção descreve a operação do Secret Kubernetes; a documentação operacional do pipeline permanece em [CI-CD-AWS.md](.github/CI-CD-AWS.md).

### Escalabilidade declarada do backend

O Terraform da aplicação gerencia o manifesto [`k8s/hpa.yaml`](k8s/hpa.yaml) como `kubectl_manifest.backend_hpa`, após o Deployment do backend. O HPA solicita de 1 a 3 réplicas e CPU média-alvo de 50%. Em `kubectl_manifest.backend_deployment`, `ignore_fields = ["spec.replicas"]` deixa esse campo sob controle do HPA, sem que o Terraform reverta a quantidade de réplicas em cada aplicação.

A configuração do HPA está integrada ao Terraform e é provisionável pela pipeline da aplicação. Após reset do Learner Lab, reexecute as pipelines CI/CD para provisionar novamente os recursos do ambiente.

O Metrics Server do cluster de produção não foi confirmado. Verifique se a API `metrics.k8s.io` está disponível no EKS
real antes de aceitar a operação do HPA. Se estiver ausente, o provisionamento deve fornecer uma instalação segura,
declarativa e compatível; não copie `k8s-local/metrics-server.yaml`, que contém opções TLS inseguras.

## Segredos Kubernetes da aplicação

O Secret `autoflow-secrets`, no namespace `default`, é consumido somente pelo backend; o frontend não o utiliza. Em homologação e produção, a pipeline mapeia os Environment Secrets para esse Secret usando `scripts/apply_k8s_secret.py`, que envia o manifesto por stdin sem gravá-lo em arquivo ou log e reinicia o backend. Consulte o [catálogo de variables e secrets](docs/variables-and-secrets.md) e [Operação de CI/CD](.github/CI-CD-AWS.md) para os nomes e etapas do fluxo.

Para desenvolvimento local, use `k8s-local/secret.yaml.example` e mantenha os valores somente na máquina. Manifests Secret preenchidos foram removidos de `develop`; `k8s/secret.yaml` não é necessário para o deploy nos clusters dedicados. A remoção ainda precisa chegar a `main` por Pull Request aprovado antes de habilitar produção.

Restrinja o RBAC de leitura e escrita de Secrets, confirme a proteção KMS do cluster e rotacione credenciais que tenham sido versionadas ou persistidas em state. Após reset do Learner Lab, reexecute as pipelines CI/CD para provisionar o Secret e os workloads.

---

## API e OpenAPI

Para demonstrar o login serverless por CPF/CNPJ e o uso do token em rota protegida, consulte a
[demonstração ponta a ponta](docs/openapi/README.md#demonstração-ponta-a-ponta-da-autenticação-serverless) e importe a
[coleção Postman](backend/postman/autoflow-serverless-auth.postman_collection.json).

O [contrato OpenAPI versionado](docs/openapi/autoflow-api.json) é o artefato distribuível da API; a [documentação da
API](docs/openapi/README.md) detalha seu uso. Com o backend em execução, acesse [`http://localhost:8081/swagger-ui.html`](http://localhost:8081/swagger-ui.html)
ou [`http://localhost:8081/v3/api-docs`](http://localhost:8081/v3/api-docs).

---

## Segurança

O acesso às APIs de negócio é controlado por JWT e perfis (`ADMIN`, `ATENDENTE`, `MECANICO` e `CLIENTE`), com endpoints públicos e privados segregados. O frontend envia login ao API Gateway, que invoca a Lambda nativamente; as rotas de negócio seguem pelo VPC Link e ALB interno até o backend no EKS. Não são mantidos credenciais, tokens ou valores reais de ambiente neste README.

---

## Qualidade de Código

Testes automatizados e análises de qualidade e segurança usam JUnit, SonarQube, JaCoCo e Snyk.

## Fluxo do CI/CD

O [workflow do GitHub Actions](.github/workflows/pipeline.yml) mantém CI em Pull Requests e branches de trabalho sem
credenciais AWS ou Apply. Push em `develop` publica imagens imutáveis pelo SHA e implanta em `homolog`; push em `main`
executa o mesmo fluxo em `production`. Em ambos, o Plan só segue após testes/build/validação Terraform e publicação
Docker; Apply usa o artifact do mesmo commit e aguarda os gates efetivos do GitHub Environment. O push não autoriza
destruições ou substituições: a opção destrutiva só pode ser solicitada em `workflow_dispatch`, após revisão e aprovação.
O deploy inclui atualização do Secret Kubernetes, rollout de backend/frontend e, em homologação, smoke tests do API
Gateway. Em produção, não são executados smoke tests com credenciais sintéticas de homologação.

O provisionamento cross-repositório segue esta ordem: VPC/EKS, RDS, Lambda/autenticação, API Gateway e aplicação. A
aplicação depende do API Gateway para configurar a origem pública; o backend executa as migrations Flyway existentes
ao iniciar. Se New Relic APM estiver habilitado no Environment, a pipeline sincroniza a License Key do GitHub Secrets
com AWS Secrets Manager e Kubernetes sem registrá-la em logs, Terraform State ou artifacts.

Para habilitar a produção, configure o GitHub Environment `production` restrito a `main`, com credenciais, Variables,
Secrets, States e Required reviewers próprios. A remoção dos manifests de Secrets já presente em `develop` deve chegar
a `main` por Pull Request aprovado antes de habilitar o deploy de produção. A documentação de CI/CD descreve os
pré-requisitos externos e as pendências que só podem ser confirmadas no cluster real.

```mermaid
flowchart TD
  A[Push em develop/main] --> B[CI: testes, builds, segurança e Terraform estático]
  B --> D[Publicação Docker Hub com tags SHA]
  D --> P[Plan no Environment do branch]
  P --> G{Gates efetivos do Environment}
  G -->|Aprovado| T[Apply do plan e rollout]
  G -->|Rejeitado| X[Deploy interrompido]
  R[PR ou branch de trabalho] --> C[CI sem Apply ou acesso AWS]
```
