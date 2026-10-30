# Operação de CI/CD AWS

O catálogo de GitHub Secrets, Variables, inputs Terraform e configuração da aplicação está em
[variables-and-secrets.md](../docs/variables-and-secrets.md). Esta página descreve os gates, a seleção dos recursos e a
ordem operacional do deploy.

## Branches, eventos e ambientes

| Branch | GitHub Environment | Ação em `push` | Cluster EKS esperado |
|---|---|---|---|
| `develop` | `homolog` | Publicar imagens, Plan, Apply aprovado, rollout e smoke tests | `eks-autoflow` |
| `main` | `production` | Publicar imagens, Plan, Apply aprovado e rollout | `eks-autoflow-production` |

Pull Requests executam CI, testes, builds, análises de segurança e validação Terraform local, sem credenciais AWS,
Plan remoto ou Apply. Pushes para `feature/*` também executam apenas CI e build local das imagens. O `workflow_dispatch`
pode ser iniciado em qualquer ref, mas os jobs privilegiados validam evento, branch e GitHub Environment: somente
`develop`/`homolog` e `main`/`production` podem publicar imagens ou iniciar Plan/Apply. Dispatch em branch de trabalho
permanece sem acesso a AWS e sem deploy. Smoke tests manuais são exclusivos de `develop`.

O workflow existente `pr-automatico.yaml` abre um Pull Request para `develop` após push em qualquer branch diferente
de `develop`. O push em `feat/production-dedicated` aciona essa automação; o evento de Pull Request executa somente CI,
pois Publish, Plan e Apply aceitam apenas push/dispatch nas branches de ambiente.

Em `develop` e `main`, os jobs bloqueantes de testes/build e Terraform estático precedem a publicação Docker e o Plan.
O scan Snyk existente é preservado com seu comportamento não bloqueante. O Plan e o Apply vinculam branch, Environment,
conta esperada, região, bucket e keys do ambiente; o Apply reutiliza o artifact criptografado da mesma execução e
confirma seus hashes, metadados e commit. O push não autoriza destruições
ou substituições. A opção `allow_destructive` tem default `false` e só é considerada em `workflow_dispatch`, após
revisão do Plan e aprovação dos Required reviewers do Environment. O Apply revalida o mesmo Plan antes de aplicá-lo.

Configure os GitHub Environments `homolog` e `production` com deployment restrito, respectivamente, a `develop` e
`main`, e com Required reviewers efetivos. Defina `TERRAFORM_APPLY_APPROVAL_CONFIGURED=true` em cada Environment
somente depois de confirmar a regra no GitHub. A flag é um guard adicional; não substitui a proteção do Environment.
O workflow também confirma a identidade dos reviewers configurados antes do Apply.

## Conta, região, States e seleção de recursos

Cada job lê os Secrets e Variables do Environment selecionado. O guard compara a identidade autenticada com
`AWS_EXPECTED_ACCOUNT_ID` (12 dígitos) antes da descoberta de recursos; ausência, formato incorreto ou divergência
encerra o job. O workflow valida a região, confirma que o bucket S3 pertence à conta esperada e impede compartilhar a
key da aplicação com a key de RDS.

Configure `AWS_TERRAFORM_STATE_BUCKET`, `AWS_TF_STATE_KEY` e `AWS_RDS_STATE_KEY` por Environment. A aplicação e o RDS
devem apontar aos States do mesmo ambiente; produção usa keys sob `production/`, por exemplo
`production/application/autoflow/terraform.tfstate` e `production/rds/terraform.tfstate`. Em homologação, mantenha as
keys atualmente usadas pelos States operacionais. Se os ambientes estiverem na mesma conta/bucket, as keys de
homologação e produção devem continuar distintas. Se estiverem em contas diferentes, cada Environment usa bucket e
States da própria conta; não compartilhar bucket cross-account nem apontar produção a key de homologação.

A descoberta aceita outros clusters na mesma conta e região: seleciona somente o nome esperado para o branch. O API
Gateway também é selecionado pelo nome do ambiente (`autoflow-homolog` ou `autoflow-production`). O root Terraform da
aplicação recebe o endpoint HTTPS dessa API e `AWS_RDS_STATE_KEY` do Environment selecionado; não usa endpoint, cluster
ou State de outro ambiente. Ambos os Environments mantêm `AWS_REGION=us-east-1`, conforme os roots atuais.

Não configure nomes de cluster/API nem account IDs no repositório. Ao mudar a conta Learner Lab, atualize os Secrets e
Variables daquele Environment, o bucket/keys de State e as roles/credenciais autorizadas; não é necessário alterar
código para trocar a conta. A portabilidade declarada não comprova permissões IAM, cotas, recursos ou saldo disponíveis
na nova conta.

## Publicação Docker

Cada publicação usa tags imutáveis com o SHA do commit para `autoflow-backend` e `autoflow-frontend`. O workflow usa
`vars.DOCKERHUB_USERNAME` e o Repository Secret `secrets.DOCKERHUB_PASSWORD` tanto para homologação quanto para
produção. Não configure a senha como Variable nem grave credenciais Docker no repositório.

## Secrets e Variables

Configure as credenciais AWS temporárias e os dados da aplicação separadamente em `homolog` e `production`. Renove
`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` e `AWS_SESSION_TOKEN` juntos e mantenha `AWS_EXPECTED_ACCOUNT_ID`, bucket,
keys, credenciais de banco/JWT e configuração New Relic coerentes dentro do mesmo Environment. Consulte a
[matriz completa](../docs/variables-and-secrets.md); não duplique Secrets de produção em homologação.

`DOCKERHUB_PASSWORD`, `SONAR_TOKEN` e `SNYK_TOKEN` são credenciais compartilhadas de CI/publicação e permanecem como
Repository Secrets. `DOCKERHUB_USERNAME` é Repository Variable compartilhada. `AUTOFLOW_TEST_CPF_CNPJ` e
`AUTOFLOW_TEST_PASSWORD`, quando usados, pertencem somente a homologação e devem ser dados sintéticos.

## New Relic

O APM Java, os logs JSON correlacionados e a configuração Kubernetes existentes permanecem associados à aplicação.
Se `ENABLE_NEWRELIC_APM=true`, o Apply valida `NEW_RELIC_LICENSE_SECRET_ID` e sincroniza `NEW_RELIC_LICENSE_KEY` do
GitHub Environment Secret para AWS Secrets Manager e o Secret Kubernetes `newrelic-license` no namespace `default`.
Esse mesmo fluxo atende `production` quando habilitado; produção pode manter o APM desabilitado até sua configuração
externa estar pronta. O Secret não é gravado em Terraform State, logs, argumentos ou artifacts. A sessão AWS precisa
das permissões de Secrets Manager e Kubernetes correspondentes.

Dashboards, Synthetic e componentes New Relic gerenciados pelo root `autoflow-infra` continuam sob responsabilidade
daquele repositório e seus States por ambiente; este workflow não cria dashboards ou recursos New Relic adicionais.

## Ordem de provisionamento

Para uma implantação inicial, prepare e aprove as dependências nesta ordem, sem compartilhar States entre ambientes:

1. VPC/EKS e acesso do operador no repositório `autoflow-infra`.
2. RDS privado e seu State no repositório `autoflow-db`.
3. Lambda de autenticação no repositório `autoflow-lambda`.
4. API Gateway HTTP, VPC Link e ALB em `autoflow-infra`, após Lambda e EKS.
5. Aplicação neste repositório, após RDS, API Gateway e publicação das imagens. O backend executa as migrations
   Flyway existentes durante a inicialização; aguarde readiness e rollout antes da validação funcional.
6. New Relic Kubernetes e recursos de observabilidade conforme configuração do repositório `autoflow-infra`.

Depois de provisionadas e validadas as dependências, cada push aprovado em `develop`/`main` executa o CD do ambiente
correspondente. Os workflows não coordenam dependências cross-repository automaticamente.

## Kubernetes e pendências de provisionamento

O Terraform da aplicação preserva Deployments, Services, ConfigMap, requests/limits e o HPA existente: 1–3 réplicas,
CPU alvo de 50%, com requests/limits declarados. Não há mudança de namespace; os ambientes usam clusters dedicados.

A presença do Metrics Server de produção não foi confirmada. Antes de aceitar métricas/HPA no cluster real, verifique a
API `metrics.k8s.io`. Se estiver ausente, a instalação precisa ser segura, declarativa e compatível com o cluster. Não
copie `k8s-local/metrics-server.yaml` para produção: ele usa `--kubelet-insecure-tls` e `insecureSkipTLSVerify: true`.
Essa verificação permanece uma pendência de provisionamento; não foi executada contra AWS nesta alteração.

Os manifests preenchidos de Secrets foram removidos em `develop` e não devem ser reintroduzidos. Como `main` ainda pode
conter os manifests historicamente versionados, a remoção deve chegar por Pull Request aprovado antes de habilitar o
deploy de produção; não reescreva o histórico Git.

## Estado das validações

Os testes e verificações locais da branch não consultam AWS, Terraform State remoto, GitHub Environments ou clusters.
Assim, a existência/configuração efetiva dos Environments, buckets/keys, credenciais, reviewers, permissões IAM,
Metrics Server, recursos AWS e deploys permanece a confirmar pelos responsáveis antes da primeira execução de produção.
