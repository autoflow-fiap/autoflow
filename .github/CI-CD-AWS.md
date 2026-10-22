# Operação de CI/CD AWS

O inventário por nome de secrets, variables, inputs Terraform e configuração da aplicação está no [catálogo deste repositório](../docs/variables-and-secrets.md), com [histórico](../docs/variables-changelog.md). Esta página permanece como referência operacional dos fluxos.

## Fluxo de branches e ambientes

CI executa em Pull Requests e pushes para `develop` e `main`, incluindo testes, builds, análise de segurança e validações estáticas. Pull Requests e `main` não publicam imagens nem fazem deploy. Todo push em `develop`, inclusive merge de PR, publica imagens com tag SHA e inicia Terraform Plan; o Apply aguarda a aprovação efetiva do Environment `homolog`, depois aplica o artifact aprovado, aguarda rollout backend/frontend e executa smoke tests. `workflow_dispatch` em `develop` permanece como opção adicional de reexecução.

O endpoint EKS `eks-autoflow` tem acesso público e privado habilitados. Cada job Plan/Apply consulta seu IP IPv4 de saída efetivo, autoriza exclusivamente o `/32` no endpoint e aguarda a atualização EKS antes de Terraform/kubectl. O lease é guardado no S3 state para permitir reconciliação após cancelamento abrupto; cleanup roda em `always()`. A allowlist existente, incluindo `189.6.8.245/32`, é preservada e `0.0.0.0/0` não é permitido. A concorrência do workflow serializa operações na branch.

### Autorização temporária dos runners hospedados pelo GitHub

Plan e Apply usam `ubuntu-latest`; não dependem de EC2 self-hosted, GitHub App, PAT ou Secrets Manager de runner. Ambos consomem a mesma sessão AWS definida em Repository Secrets. A role precisa das permissões para atualizar o endpoint EKS, consultar status da atualização e ler/escrever/remover o lease no prefixo S3 configurado. Consulte [o guia](../../autoflow-infra/docs/github-runner.md) para detalhes do mecanismo.

O Plan roda sem Environment e usa Repository Secrets/Variables. Ele gera `infra.tfplan`, verifica exclusões/substituições e publica o arquivo criptografado como artifact da execução com retenção de um dia. O artifact contém ciphertext e metadados; `TF_PLAN_ENCRYPTION_KEY` (mínimo de 32 caracteres aleatórios) é Repository Secret e não integra o artifact. Hashes e metadados vinculam o plan ao commit, Terraform, providers, backend e configuração.

Somente o job Apply usa o Environment `homolog`, que exige Required reviewers efetivos. Ele baixa o artifact da mesma execução, confere hashes, commit, versão Terraform, lockfile, fingerprint de `TF_VAR_*` e backend; reaplica guards destrutivos e consome exatamente o `.tfplan` baixado, sem gerar outro plan. Configure credenciais AWS, chave de criptografia, variáveis Terraform e secrets Kubernetes como Repository Secrets/Variables. Não duplique esses valores no Environment `homolog`: mantenha nele a proteção Required reviewers, de modo que Plan e Apply consumam os mesmos valores de repositório. A pipeline consulta a API do GitHub para confirmar reviewers; `TERRAFORM_APPLY_APPROVAL_CONFIGURED=true` é um guard adicional.

**Produção permanece desabilitada.** Os roots atuais não demonstram isolamento completo de produção: há nomes fixos de recursos, um único conjunto de keys/variáveis de RDS e workloads Kubernetes com nomes/namespace compartilhados. Não habilitar despacho em `main` nem criar o Environment `production` para esses workflows até concluir o plano de segregação abaixo, implementar roots/estados independentes e validar os planos sem aplicar.

Para habilitar qualquer CD, configurar nos GitHub Environments `homolog` e `production`: restrição de deployment à branch correspondente (`develop` ou `main`), Required reviewers, proteção de branch com PR/checks obrigatórios e desabilitação de bypass quando o plano GitHub utilizado oferecer essa opção. A aprovação do job ocorre pela proteção efetiva do Environment; a variável `TERRAFORM_APPLY_APPROVAL_CONFIGURED=true` é somente um guard adicional e nunca substitui Required reviewers. Definir a variável apenas depois de confirmar manualmente a proteção. No GitHub Actions, habilitar prevent self-review quando disponível e limitar quem pode administrar environments.

### Plano bloqueante para produção (sem alterações de infraestrutura nesta etapa)

1. Inventariar os recursos atualmente homologados e confirmar por revisão humana os estados AWS existentes, sem importar ou reutilizar state de homologação em produção.
2. Definir e provisionar isolamento independente para produção: keys de state/backend, VPC/EKS e roles, RDS/subnet group/SG/credenciais/banco, Lambda e API Gateway, nomes de recursos, logs/alarms e workload Kubernetes/namespace. Não permitir que production consuma remote states ou dados de RDS de homologação.
3. Parametrizar os roots e módulos para ambiente explícito, preservando por padrão os nomes e estados homologados já operacionais. Exigir que plans de produção sejam revisados antes de qualquer Apply.
4. Avaliar limites e serviços permitidos na conta AWS Academy Learner Lab, duração/renovação das credenciais temporárias, quotas, custos e disponibilidade dos serviços necessários antes de escolher topologia/capacidade. Essas condições não foram verificadas na conta nesta revisão; não assumir que existe capacidade para uma segunda VPC/EKS/RDS.
5. Só então adicionar jobs `workflow_dispatch` em `main` apontados exclusivamente ao Environment `production`, com credenciais/variables/state próprios, guard contra branch/ambiente cruzados, aprovação obrigatória e gates de destruição. Manter produção fechada até CI e planos estáticos confirmarem a separação.

Proteger `develop` e `main` com Pull Request obrigatorio, checks CI aprovados, conversas resolvidas e bloqueio de force-push/exclusao.

## Ordem e responsabilidades

Ordem operacional: VPC/EKS; RDS; Lambda e API Gateway HTTP; imagens e workloads Kubernetes. A HTTP API integra a Lambda para login e usa VPC Link para o ALB interno, que encaminha para NodePorts do EKS.

O root Terraform da aplicacao gerencia manifests Kubernetes. Spring Boot executa Flyway na inicializacao dos pods; as migrations precisam ser compativeis com a versao corrente da aplicacao. Aplicar primeiro mudancas de schema compativeis, depois a imagem; aguardar rollout/readiness antes dos smoke tests. Flyway coordena locking no banco; nao iniciar migrations concorrentes deliberadamente.

Cada root Terraform usa state S3 exclusivo por ambiente e lock file. Keys consumidoras/produtoras devem coincidir para EKS, RDS, Lambda, API Gateway e aplicacao. Os workflows verificam bucket/keys antes de inicializar os roots que consomem states remotos.

## GitHub Secrets

Configurar os secrets operacionais como Repository Secrets; criar segredos independentes para `production` somente quando o plano de produção for aprovado e a infraestrutura estiver isolada:

| Secret | Uso |
| --- | --- |
| `DOCKERHUB_PASSWORD` | Repository secret usado na publicação automática de imagens em push `develop` |
| `SONAR_TOKEN`, `SNYK_TOKEN` | Análises existentes de qualidade e segurança |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` | Repository Secrets com credenciais AWS temporárias; renovar juntas antes da expiração |
| `TF_PLAN_ENCRYPTION_KEY` | Repository Secret, chave aleatória com ao menos 32 caracteres para criptografar/descriptografar o `.tfplan` |
| `AUTOFLOW_DB_USERNAME`, `AUTOFLOW_DB_PASSWORD`, `AUTOFLOW_JWT_SECRET`, `AUTOFLOW_MAIL_PASSWORD`, `AUTOFLOW_PUBLIC_TOKEN_SECRET` | Secrets de runtime aplicados ao Secret Kubernetes no job aprovado `homolog` |
| `AUTOFLOW_TEST_CPF_CNPJ`, `AUTOFLOW_TEST_PASSWORD` | Secrets de smoke tests, se esses testes forem habilitados |

## GitHub Variables

Configurar como Repository Variables; não depender de defaults para conta, região ou state. Os valores de produção devem ser próprios e distintos dos de homologação:

| Variable | Uso |
| --- | --- |
| `DOCKERHUB_USERNAME` | Namespace Docker Hub, obrigatório para publicação SHA |
| `AWS_REGION`, `AWS_EXPECTED_ACCOUNT_ID` | Repository Variables de região e conta esperadas |
| `AWS_TERRAFORM_STATE_BUCKET`, `AWS_TF_STATE_KEY`, `AWS_RDS_STATE_KEY` | Repository Variables do bucket e keys `application/autoflow/terraform.tfstate` e `rds/terraform.tfstate` |
| `EKS_CLUSTER_NAME`, `API_GATEWAY_ORIGIN`, `API_GATEWAY_CORS_ORIGINS` | Repository Variables do cluster e origens; CORS deve ser array JSON, por exemplo `[]` |
| `AWS_TERRAFORM_STATE_BUCKET` | Inclui prefixo `ci/eks-endpoint-lease.json`, usado como registro durável de CIDR temporário; a role precisa de `s3:GetObject`, `s3:PutObject` e `s3:DeleteObject` nesse objeto |
| `DOCKERHUB_USERNAME` | Repository Variable do namespace Docker Hub usado nas imagens |
| `TERRAFORM_APPLY_APPROVAL_CONFIGURED` | Repository Variable guard adicional em `homolog`; não substitui Required reviewers efetivos |

## AWS Academy Learner Lab

Não presumir OIDC ou access keys permanentes no Learner Lab. O pipeline usa secrets temporários AWS fornecidos pelo operador; atualize os três valores juntos antes do Plan. O artifact de uma execução é baixável por usuários com acesso ao repositório, mas seu `.tfplan` está criptografado com AES-256-CBC/PBKDF2; a chave permanece em GitHub Secrets. O plan em claro existe temporariamente no disco do runner e é removido após empacotamento/aplicação. O summary restrito exibe apenas endereços e ações, nunca valores do plan.

O updater do Secret Kubernetes envia os valores por stdin, captura a saida de `kubectl`, relê o objeto e compara chaves/valores em memoria sem imprimir os valores.
