# Operação de CI/CD AWS

O catálogo de GitHub Secrets, Variables, inputs Terraform e configuração da aplicação está em
[variables-and-secrets.md](../docs/variables-and-secrets.md). Esta página descreve os gates, a seleção dos recursos e a
ordem operacional do deploy.

## Branches, eventos e ambientes

| Branch | GitHub Environment | Ação em `workflow_dispatch` | Cluster EKS esperado |
|---|---|---|---|
| `develop` | `homolog` | `apply=false`: Plan; `apply=true`: publicar imagens, Plan, Apply aprovado e rollout com smoke tests | `eks-autoflow` |
| `main` | `production` | `apply=false`: Plan; `apply=true`: publicar imagens, Plan, Apply aprovado e rollout | `eks-autoflow-production` |

O workflow `.github/workflows/ci.yml` executa automaticamente em Pull Requests para `develop`/`main` e pushes para
`develop`, `main` e `feature/*`: testes, builds, análises de segurança e validação Terraform local. Também constrói imagens
Docker localmente, sem publicação. A CI não usa credenciais AWS, não executa Plan remoto e não aplica recursos.
O workflow `.github/workflows/pipeline.yml` preserva `workflow_dispatch` e também recebe `push` em `develop`/`main`.
No caminho automático, um gate consulta `AUTO_CD_ENABLED` no Environment correspondente e aguarda a CI `ci.yml` do mesmo
repositório, branch e SHA. Somente a conclusão `success` da execução exata libera os jobs do CD; falha, cancelamento,
`skipped`, divergência ou timeout bloqueia o Plan. O checkout do CD usa e confirma o SHA validado. PRs e branches
`feature/**` não iniciam CD. Uma execução manual em outra branch não alcança os jobs do CD. Smoke tests manuais são
exclusivos de `develop`.

O workflow existente `pr-automatico.yaml` abre um Pull Request para `develop` após push em qualquer branch diferente
de `develop`. O evento de Pull Request executa somente CI; publicação, Plan e Apply não são disparados por push ou PR.

No CD, os jobs de teste/build e Terraform estático precedem o Plan. A publicação Docker ocorre somente quando o dispatch
define `apply=true` e passa pela aprovação do Environment selecionado; o caminho automático e um Plan-only (`apply=false`)
não publicam imagens. O Environment configurado também protege os Plans automáticos, que não autorizam Apply.
O scan Snyk existente é preservado com seu comportamento não bloqueante. O Plan e o Apply vinculam branch, Environment,
conta esperada, região, bucket e keys do ambiente; o Apply reutiliza o artifact criptografado da mesma execução e
confirma seus hashes, metadados e commit. A opção `allow_destructive` tem default `false` e só é considerada em
`workflow_dispatch`, após revisão do Plan e aprovação dos Required reviewers do Environment. O Apply também requer
`apply=true`, passa por aprovação independente do Environment e confirma `TERRAFORM_APPLY_APPROVAL_CONFIGURED=true`.
O Apply revalida o mesmo Plan antes de aplicá-lo.

Configure os GitHub Environments `homolog` e `production` com deployment restrito, respectivamente, a `develop` e
`main`, e com Required reviewers efetivos. O workflow exige `github.ref_protected` nos jobs production; configure
externamente a proteção/ruleset de `main` e checks CI. Defina `TERRAFORM_APPLY_APPROVAL_CONFIGURED=true` em cada Environment
somente depois de confirmar a regra no GitHub. A flag é um guard adicional; não substitui a proteção do Environment.
O Apply continua exigindo `apply=true`, aprovação independente do Environment e o marcador. A aprovação de Plan, inclusive
de um Plan automático, não autoriza Apply.

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

Para habilitar CD automático, configure a nova Environment Variable `AUTO_CD_ENABLED` com o valor literal `true` no
Environment `homolog` e/ou `production`. Cada Environment é independente; variável ausente ou diferente de `true`
mantém o respectivo ambiente desabilitado. A variável não contém segredo e não substitui `TERRAFORM_APPLY_APPROVAL_CONFIGURED`,
`apply=true` nem Required reviewers. Nenhuma configuração do GitHub é criada ou alterada por esta documentação.

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

Depois de provisionadas e validadas as dependências, habilite `AUTO_CD_ENABLED` no Environment desejado somente quando
as configurações externas e proteções estiverem prontas; caso contrário, mantenha a variável ausente. Os workflows não
coordenam dependências cross-repository automaticamente.

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
