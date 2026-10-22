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

Os manifestos Kubernetes da entrega em [k8s/](k8s/) e do perfil local em [k8s-local/](k8s-local/) estão versionados,
exceto os arquivos Secret, que são ignorados pelo Git. É necessário ter Minikube, Kind ou Kubernetes habilitado no
Docker Desktop. Para o perfil local, mantenha `k8s-local/secret.yaml` apenas na máquina e preencha-o com os valores
locais requeridos pelo PostgreSQL e pela aplicação, sem adicioná-lo ao Git.

Os manifests de `k8s-local/` são para desenvolvimento local. O acesso AWS de homologação usa os serviços NodePort privados alcançáveis somente pelo ALB interno provisionado pelo API Gateway.

```bash
cd k8s-local
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
[autoflow-infra](https://github.com/BrunoSousa06/autoflow-infra). Este repositório gerencia Deployments, Services
internos/NodePort privados e a configuração Nginx do frontend.

O state do root `infra/` usa a key S3 `application/autoflow/terraform.tfstate`. Configure o bucket e a key aprovados
por variáveis do ambiente; o workflow confirma que o objeto remoto existe antes do Terraform. O backend exige
`AWS_TERRAFORM_STATE_BUCKET`, `AWS_TF_STATE_KEY`, `AWS_RDS_STATE_KEY`, `API_GATEWAY_ORIGIN` e
`API_GATEWAY_CORS_ORIGINS`. A API de negócio e o frontend são publicados na mesma origem HTTPS do API Gateway; não há
Ingress, plugin ou upstream Kong na arquitetura de aplicação.

Para a configuração por nome de GitHub Actions, Terraform, runtime e Kubernetes, consulte o [catálogo de variables e secrets](docs/variables-and-secrets.md) e o [histórico](docs/variables-changelog.md). Esta seção descreve a operação do Secret Kubernetes; a documentação operacional do pipeline permanece em [CI-CD-AWS.md](.github/CI-CD-AWS.md).

## Segredos Kubernetes da aplicação

O Secret Kubernetes chama-se `autoflow-secrets`, no namespace `default`, e é consumido somente pelo Deployment
`autoflow-backend` via `envFrom.secretRef`. O contrato mantém as chaves `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `MAIL_PASSWORD` e `APP_PUBLIC_TOKEN_SECRET`; o Deployment frontend não
consome esse Secret. Os manifests `k8s/secret.yaml` e `k8s-local/secret.yaml` ficam ignorados pelo Git; não os force
para o repositório.

Os Secrets `AUTOFLOW_DB_USERNAME`, `AUTOFLOW_DB_PASSWORD`, `AUTOFLOW_JWT_SECRET`, `AUTOFLOW_MAIL_PASSWORD` e
`AUTOFLOW_PUBLIC_TOKEN_SECRET` são os valores planejados para atualizar o Secret do workload quando o deploy privado
for habilitado. O workflow atual não os consome: deployment e `kubectl` estão bloqueados porque o runner hospedado não
tem conectividade autorizada/validada à API Kubernetes do EKS. Não há produção implantada declarada.

Para aplicar localmente em um contexto Kubernetes autorizado, forneça essas cinco variáveis no ambiente do processo
a partir de um gerenciador seguro (sem incluí-las em comandos registrados ou arquivos versionados) e execute:

```bash
python scripts/apply_k8s_secret.py
```

O script exige valores não vazios e envia um manifesto efêmero diretamente por stdin; não cria arquivo local. Configure
o RBAC da identidade de deploy com o mínimo necessário: `get`, `create`, `patch` e `update` de Secrets no namespace
`default`, além das permissões estritamente requeridas pelo Terraform para os demais recursos da aplicação. Acesso de
leitura a Secrets deve ser restrito a administradores/operadores autorizados, pois identidades com esse acesso podem
recuperar os valores.

O código local não comprova criptografia em repouso do cluster EKS: o responsável pela plataforma deve confirmar que
o armazenamento de Secrets usa envelope encryption/KMS e políticas de acesso apropriadas antes de homologar. Terraform
não cria mais esse Secret e deixa de receber as credenciais de banco. O bloco `removed` preserva o recurso Kubernetes
existente enquanto o remove do state atual no próximo apply; versões históricas do state ainda podem conter valores
previamente aplicados. O backend S3 declara `encrypt = true`, mas isso, por si só, não elimina cópias antigas nem
comprova KMS. Antes de habilitar o gate, o owner deve avaliar o state histórico e restringir seu acesso; valores que
já foram versionados ou persistidos em state devem ser rotacionados por procedimento operacional aprovado.

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

O [workflow do GitHub Actions](.github/workflows/pipeline.yml) executa CI em Pull Requests e pushes para `develop` e `main`. Todo push em `develop` publica imagens pelo SHA, executa Terraform Plan e aguarda aprovação humana efetiva no Environment `homolog` antes do Apply, rollout e smoke tests. Plan/Apply usam `ubuntu-latest` com autorização temporária do IP de saída ao endpoint EKS. `workflow_dispatch` continua disponível para reexecução manual. Veja [Operação de CI/CD](.github/CI-CD-AWS.md) para pré-requisitos e ativação.

```mermaid
flowchart TD
  A[PR ou push em develop/main] --> B[CI: testes, builds, análises e Terraform estático]
  A --> C{Push em develop?}
  C -->|Sim| D[Publicação Docker Hub com tags SHA]
  D --> P[Plan no ubuntu-latest]
  P --> G{Aprovação do Environment homolog}
  G -->|Aprovado| T[Apply e rollout]
  G -->|Rejeitado| X[Deploy interrompido]
  C -->|Não| F[Sem publicação/deploy]
```

---

## Entrega — Tech Challenge Fase 2 (histórico)

### Objetivos da Fase 2

Evoluir o AutoFlow com Clean Architecture, testes automatizados, APIs operacionais, infraestrutura como código, CI/CD,
deploy em Kubernetes e escalabilidade automática.

### Arquitetura de deploy documentada para a Fase 2

O diagrama a seguir é um registro histórico da entrega da Fase 2; não representa os gatilhos e gates atuais do workflow, descritos na seção anterior.

O modelo de deploy descrito para a Fase 2 combina GitHub Actions, Docker Hub, Terraform, ConfigMaps, Secrets e HPA. O manifesto [`k8s/hpa.yaml`](k8s/hpa.yaml) está versionado, mas sua aplicação efetiva em qualquer ambiente não é comprovada pela presença do arquivo.

```mermaid
flowchart TD
    A["Push ou Pull Request<br/>develop / main"] --> B["Testes do backend + SonarQube<br/>Maven · Testcontainers · JaCoCo"]
    A --> C["Testes do frontend<br/>npm test"]

    B --> D["Build backend<br/>mvn package"]
    C --> E["Build frontend<br/>npm run build"]

    A --> F{"Push na branch develop?"}
    F -->|Sim| G["Snyk Security Scan<br/>dependências e imagens"]

    D --> H["Build e publicação Docker<br/>Backend e frontend no Docker Hub"]
    E --> H
    F -->|Sim| H

    H --> I["Deploy Terraform<br/>init · validate · plan · apply"]
    I --> J["AWS / EKS<br/>recursos Kubernetes"]
```

Consulte a [fonte Mermaid do diagrama](docs/diagramas-arquitetura/arquitetura-deploy-fase-2.mermaid) e a [documentação
detalhada da arquitetura de deploy](docs/diagramas-arquitetura/README.md).

### Evidências da entrega

- [Manifestos Kubernetes](k8s/)
- [Código Terraform](infra/)
- [Pipeline GitHub Actions](.github/workflows/pipeline.yml)
- [Contrato OpenAPI versionado](docs/openapi/autoflow-api.json)
- [Documentação da API](docs/openapi/README.md)
- [Vídeo demonstrativo da Fase 2](https://youtu.be/ObUkVeYQCOA)
