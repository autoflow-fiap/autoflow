# Operação de CI/CD AWS

O catálogo de GitHub Secrets, Variables, inputs Terraform e configuração da aplicação está em
[variables-and-secrets.md](../docs/variables-and-secrets.md). Esta página descreve os gates, a seleção dos recursos e a
ordem operacional do deploy.

## Branches, eventos e ambientes

| Branch | GitHub Environment | Ação em `workflow_dispatch` | Cluster EKS esperado |
|---|---|---|---|
| `develop` | `homolog` | `apply=false`: Plan; `apply=true`: publicar imagens, Plan, Apply aprovado, rollout e smoke tests; `smoke_tests_only=true`: smoke tests isolados | `eks-autoflow` |
| `main` | `production` | `apply=false`: Plan; `apply=true`: publicar imagens, Plan, Apply aprovado, rollout e smoke tests; `smoke_tests_only=true`: smoke tests isolados | `eks-autoflow-production` |

O workflow `.github/workflows/ci.yml` executa automaticamente em Pull Requests para `develop`/`main` e pushes para
`develop`, `main` e `feature/*`: testes, builds, análises de segurança e validação Terraform local. Também constrói imagens
Docker localmente, sem publicação. A CI não usa credenciais AWS, não executa Plan remoto e não aplica recursos.
O workflow `.github/workflows/pipeline.yml` preserva `workflow_dispatch` e também recebe `push` em `develop`/`main`.
No caminho automático, o gate lê `AUTO_CD_ENABLED_HOMOLOG` ou `AUTO_CD_ENABLED_PRODUCTION` diretamente do contexto `vars`
e aguarda a CI `ci.yml` do mesmo repositório, branch e SHA. As duas Repository Variables são independentes; somente o
valor literal `true` habilita o ambiente correspondente. O gate não consulta APIs administrativas dos Environments.
Somente a conclusão `success` da execução exata libera os jobs do CD; falha, cancelamento, `skipped`, divergência ou
timeout bloqueia o Plan. O checkout do CD usa e confirma o SHA validado. PRs e branches `feature/**` não iniciam CD. Uma
execução manual em outra branch não alcança os jobs do CD. Smoke tests manuais são
exclusivos de `workflow_dispatch` em `develop`/`main`; a seleção `homolog`/`production` usa a saída validada pelo gate.

O workflow existente `pr-automatico.yaml` abre um Pull Request para `develop` após push em qualquer branch diferente
de `develop`. O evento de Pull Request executa somente CI; publicação, Plan e Apply não são disparados por push ou PR.

No CD, os jobs de teste/build e Terraform estático precedem o Plan. A publicação Docker ocorre somente quando o dispatch
define `apply=true` e passa pela aprovação do Environment selecionado; o caminho automático e um Plan-only (`apply=false`)
não publicam imagens. O Environment configurado também protege os Plans automáticos, que não autorizam Apply.
O scan Snyk existente é preservado com seu comportamento não bloqueante. O Plan e o Apply vinculam branch, Environment,
conta esperada, região, bucket e keys do ambiente; o Apply reutiliza o artifact criptografado da mesma execução e
confirma seus hashes, metadados e commit. A opção `allow_destructive` tem default `false` e só é considerada em
`workflow_dispatch`, após revisão do Plan e aprovação das proteções configuradas no Environment. O Apply também requer
`apply=true`, passa por aprovação independente do Environment e confirma `TERRAFORM_APPLY_APPROVAL_CONFIGURED=true`.
O Apply revalida o mesmo Plan antes de aplicá-lo.

Configure os GitHub Environments `homolog` e `production` com deployment restrito, respectivamente, a `develop` e
`main`, e com Required reviewers efetivos. O workflow exige `github.ref_protected` nos jobs production; configure
externamente a proteção/ruleset de `main` e checks CI. Defina `TERRAFORM_APPLY_APPROVAL_CONFIGURED=true` em cada Environment
somente depois de confirmar a regra no GitHub. A flag é um guard adicional; não substitui a proteção do Environment.
O Apply continua exigindo `apply=true`, aprovação independente do Environment e o marcador. A aprovação de Plan, inclusive
de um Plan automático, não autoriza Apply.

O job `smoke-tests` associa-se ao mesmo Environment validado pelo gate e mantém `needs: [gate, deploy-apply]`. Cada
execução de smoke tests é um deployment adicional sujeito aos Required reviewers daquele Environment; após um Apply,
isso pode exigir uma nova aprovação. A execução isolada também aguarda aprovação antes de receber as credenciais. Esse
é o efeito necessário para consumir Environment Secrets sem alterar as regras existentes; a aprovação do Apply não é
reutilizada pelo deployment separado de smoke tests.

## Conta, região, States e seleção de recursos

Cada job lê os Secrets e Variables do Environment selecionado. O guard compara a identidade autenticada com
`AWS_EXPECTED_ACCOUNT_ID` (12 dígitos) antes da descoberta de recursos; ausência, formato incorreto ou divergência
encerra o job. O workflow valida a região, confirma que o bucket S3 pertence à conta esperada e impede compartilhar a
key da aplicação com a key de RDS.

Configure `AWS_TERRAFORM_STATE_BUCKET`, `AWS_TF_STATE_KEY` e `AWS_RDS_STATE_KEY` como Variables de cada Environment. As
keys devem iniciar pelo prefixo obrigatório `homolog/` ou `production/`; seus sufixos são definidos externamente. A
aplicação e o RDS devem apontar aos States do mesmo ambiente, com keys distintas. Se homolog e produção usarem o mesmo
bucket, as seis keys dos roots precisam ser únicas nesse bucket; contas e buckets devem corresponder ao Environment.
Não compartilhe bucket cross-account nem aponte produção a uma key de homologação. O workflow rejeita valores ausentes
no Environment, mesmo que haja Repository Variables com o mesmo nome.

A descoberta aceita outros clusters na mesma conta e região: seleciona somente o nome esperado para o branch. O API
Gateway também é selecionado pelo nome do ambiente (`autoflow-homolog` ou `autoflow-production`). O root Terraform da
aplicação recebe o endpoint HTTPS dessa API e `AWS_RDS_STATE_KEY` do Environment selecionado; não usa endpoint, cluster
ou State de outro ambiente. A região é fornecida pelo Environment e validada antes das operações AWS. O workflow deriva
`allowed_account_ids` de `AWS_EXPECTED_ACCOUNT_ID` para restringir o backend S3 e o `terraform_remote_state`. As
verificações de ownership do bucket e do objeto RDS usam `head-bucket`/`head-object --expected-bucket-owner` pela AWS CLI
antes de inicializar o backend e ler os outputs remotos; esse argumento não é passado ao Terraform.

### Lease durável do endpoint EKS

Os jobs `deploy-plan` e `deploy-apply` usam `EKS_LEASE_BUCKET` igual a `AWS_TERRAFORM_STATE_BUCKET` e uma key
separada por ambiente: `ci/eks-endpoint-lease-autoflow-homolog.json` ou
`ci/eks-endpoint-lease-autoflow-production.json`. As credenciais temporárias `AWS_ACCESS_KEY_ID`,
`AWS_SECRET_ACCESS_KEY` e `AWS_SESSION_TOKEN` são configuradas antes do acesso ao S3. O workflow valida o proprietário
do bucket com `AWS_EXPECTED_ACCOUNT_ID` antes de ler, gravar ou remover o lease.

Conceda ao principal dos jobs `s3:ListBucket` no bucket e `s3:GetObject`, `s3:PutObject` e `s3:DeleteObject` nas keys
do lease. `s3:ListBucket` permite distinguir uma key ausente (`NoSuchKey`/404) de falha de autorização; `AccessDenied`,
`ExpiredToken`, falhas de rede e erros desconhecidos encerram o job sem tratar o lease como ausente. Se houver SSE-KMS,
avalie também `kms:Decrypt` e `kms:GenerateDataKey` na policy IAM e na key policy.

O conteúdo do lease é validado antes da reconciliação. Escritas usam `If-None-Match: *`, recusando sobrescrever um lease
existente; leases inválidos não alteram o endpoint. A restauração remove apenas o CIDR temporário, repõe a allowlist
original e preserva CIDRs externos adicionados posteriormente. O workflow serializa execuções por ref; não remova esse
controle nem o armazenamento persistente do lease.

Não configure nomes de cluster/API nem account IDs no repositório. Ao mudar a conta AWS, atualize os Secrets e Variables
daquele Environment, o bucket/keys de State e as roles/credenciais autorizadas. A mudança de conta não concede
permissões IAM nem quotas; prepare esses recursos antes do deployment.

## Publicação Docker

Cada publicação usa tags imutáveis com o SHA do commit para `autoflow-backend` e `autoflow-frontend`. O job protegido
consome `vars.DOCKERHUB_USERNAME` e `secrets.DOCKERHUB_PASSWORD` no escopo selecionado para o deployment. Configure a
senha como Secret, nunca como Variable, e não grave credenciais Docker no repositório.

## Secrets e Variables

Configure as credenciais AWS temporárias e os dados da aplicação separadamente em `homolog` e `production`. Renove
`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` e `AWS_SESSION_TOKEN` juntos e mantenha `AWS_EXPECTED_ACCOUNT_ID`, bucket,
keys, credenciais de banco/JWT e configuração New Relic coerentes dentro do mesmo Environment. Consulte a
[matriz completa](../docs/variables-and-secrets.md); não duplique Secrets de produção em homologação.

`SONAR_TOKEN` e `SNYK_TOKEN` são Repository Secrets usados por jobs de CI que não declaram Environment. O job protegido de
publicação lê `DOCKERHUB_USERNAME` e `DOCKERHUB_PASSWORD`; configure-os no escopo autorizado para o deployment e não
grave credenciais Docker no repositório.

Para smoke tests autenticados, prefira o par `AUTOFLOW_HOMOLOG_TEST_CPF_CNPJ`/
`AUTOFLOW_HOMOLOG_TEST_PASSWORD` no Environment `homolog`. O workflow aceita o par global
`AUTOFLOW_TEST_CPF_CNPJ`/`AUTOFLOW_TEST_PASSWORD` como fallback apenas quando os dois valores Environment-only estão
ausentes; um par parcial falha antes de configurar AWS.

Em `production`, o workflow aceita somente o par Environment-only
`AUTOFLOW_PRODUCTION_TEST_CPF_CNPJ`/`AUTOFLOW_PRODUCTION_TEST_PASSWORD`, sem fallback global. Se o par não existir, os
smoke tests de autenticação são omitidos, sem afetar os demais checks. Use credenciais sintéticas e independentes por
ambiente. A validação lê os valores injetados pelos contextos `vars` e `secrets`.

Para habilitar CD automático, defina `AUTO_CD_ENABLED_HOMOLOG=true` e/ou `AUTO_CD_ENABLED_PRODUCTION=true` como Repository
Variables independentes. Valor ausente ou diferente de `true` mantém o respectivo ambiente desabilitado. Essas Variables
não contêm segredos e não substituem `TERRAFORM_APPLY_APPROVAL_CONFIGURED`, `apply=true` nem Required reviewers. Nenhuma
configuração do GitHub é criada ou alterada por esta documentação.

## New Relic

O backend inclui o agente New Relic APM, logs JSON correlacionados e eventos de negócio. Se
`ENABLE_NEWRELIC_APM=true`, o Apply valida `NEW_RELIC_LICENSE_SECRET_ID` e sincroniza `NEW_RELIC_LICENSE_KEY` do
GitHub Environment Secret para AWS Secrets Manager e o Secret Kubernetes `newrelic-license` no namespace `default`.
Esse fluxo se aplica a qualquer Environment com `ENABLE_NEWRELIC_APM=true`. O Secret não é gravado em Terraform State,
logs, argumentos ou artifacts. A sessão AWS precisa das permissões de Secrets Manager e Kubernetes correspondentes.

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

Depois de provisionadas e validadas as dependências, habilite a Repository Variable `AUTO_CD_ENABLED_HOMOLOG` ou
`AUTO_CD_ENABLED_PRODUCTION` somente quando as configurações externas e proteções estiverem prontas; caso contrário,
mantenha-a ausente ou diferente de `true`. Os workflows não coordenam dependências cross-repository automaticamente.

## Kubernetes e segurança dos Secrets

O Terraform da aplicação gerencia Deployments, Services, ConfigMap, requests/limits e o HPA: 1–3 réplicas, CPU alvo de
50%. O HPA requer que o cluster exponha `metrics.k8s.io`. O manifesto
`k8s-local/metrics-server.yaml` é apenas para desenvolvimento local e usa `--kubelet-insecure-tls` e
`insecureSkipTLSVerify: true`; não o use em clusters dedicados.

Secrets de aplicação são injetados pelo workflow a partir dos GitHub Environment Secrets via
`scripts/apply_k8s_secret.py`; não mantenha valores preenchidos em manifests versionados. Restringa leitura de Kubernetes
Secrets a identidades necessárias, use o controle KMS do cluster e rotacione credenciais que tenham sido expostas ou
armazenadas em Terraform State.
