# Variables e secrets

Catálogo observado no código e configuração deste repositório. Push em `develop` executa CD no Environment `homolog`; push em `main` executa CD no Environment `production`, após CI e gates efetivos do Environment. Cada Environment mantém credenciais, Variables, Secrets e State próprios; não compartilhar valores entre homologação e produção.

## GitHub Actions

Os jobs de PR e branches de trabalho não publicam imagens nem executam Plan/Apply AWS. Push em `develop` e `main` publica tags SHA imutáveis usando `vars.DOCKERHUB_USERNAME` e o Repository Secret `secrets.DOCKERHUB_PASSWORD`. `SONAR_TOKEN` e `SNYK_TOKEN` são usados pelas análises existentes. Plan e Apply usam GitHub-hosted runners e o Environment da branch; Apply aguarda os Required reviewers efetivos. Operações destrutivas não são autorizadas por push e só podem ser solicitadas por `workflow_dispatch`, com aprovação do Environment.

Configure os Environments `homolog` e `production`, limitando deployments a `develop` e `main`. Não cadastre `EKS_CLUSTER_NAME` nem `API_GATEWAY_ORIGIN`: após validar a identidade AWS, região e configuração do State, o workflow seleciona `eks-autoflow`/`autoflow-homolog` ou `eks-autoflow-production`/`autoflow-production`. `AWS_TF_STATE_KEY` e `AWS_RDS_STATE_KEY` devem ser distintos e começar por `homolog/` ou `production/` conforme o Environment; os sufixos são externos. Consulte [Operação de CI/CD](../.github/CI-CD-AWS.md) para os gates, checks do bucket e ordem de deploy.

### Secrets

| Nome | Tipo / finalidade | Onde configurar; origem | CI / CD | Exemplo seguro | Consumidor; default/ausência |
|---|---|---|---|---|---|
| `SONAR_TOKEN` | Secret; autenticação SonarQube | GitHub Repository secret; token emitido pelo SonarQube | CI condicional | Não aplicável | `.github/workflows/pipeline.yml`; step não roda em PR de fork, mas quando roda sem token a análise pode falhar. |
| `SNYK_TOKEN` | Secret; autenticação Snyk | GitHub Repository Secret; token Snyk | CI condicional | Não aplicável | `pipeline.yml`; usado pelas análises de segurança existentes. |
| `DOCKERHUB_PASSWORD` | Secret; autenticação para publicar imagem | GitHub Repository Secret; token Docker Hub de publicação | Push `develop` e `main` | Não aplicável | `pipeline.yml`; obrigatório no job de publicação. |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` | Secrets; credenciais temporárias AWS da mesma sessão | GitHub Environment Secrets de `homolog` e `production`, com valores de cada conta | CD | Não aplicável | `pipeline.yml`; usados por Plan e Apply; renovar juntos conforme validade da sessão. |
| `TF_PLAN_ENCRYPTION_KEY` | Secret; chave para cifrar o plan binário antes do upload | GitHub Environment Secret de cada ambiente; gerar valor aleatório com no mínimo 32 caracteres | Plan/Apply | Não aplicável | `pipeline.yml`; mesmo valor no Plan e Apply daquele Environment; nunca publicar em logs/artifact. |
| `NEW_RELIC_LICENSE_KEY` | Secret; fonte de bootstrap da License Key de ingestão | GitHub Environment Secret de `homolog` e, quando habilitado, `production` no repositório `autoflow` | Apply aprovado quando `ENABLE_NEWRELIC_APM=true` | Não aplicável | `pipeline.yml` injeta apenas no passo de sincronização. Não é solicitado no Plan ou no repositório infra. |
| `AUTOFLOW_DB_USERNAME`, `AUTOFLOW_DB_PASSWORD` | Secrets; credenciais de banco do Secret Kubernetes | GitHub Environment Secrets; credenciais do RDS daquele ambiente | CD | Não aplicável | `pipeline.yml` → `scripts/apply_k8s_secret.py`; usados somente no Apply; sem defaults. |
| `AUTOFLOW_JWT_SECRET` | Secret; assinatura JWT da aplicação | GitHub Environment Secret; segredo compatível com autenticação daquele ambiente | CD | Não aplicável | Mesmo workflow/script; usado somente no Apply; sem default. |
| `AUTOFLOW_MAIL_PASSWORD` | Secret; autenticação SMTP | GitHub Environment Secret; credencial SMTP apropriada ao ambiente | CD | Não aplicável | Mesmo workflow/script; usado somente no Apply; sem default. |
| `AUTOFLOW_PUBLIC_TOKEN_SECRET` | Secret; tokens públicos de orçamento | GitHub Environment Secret; segredo independente por ambiente | CD | Não aplicável | Mesmo workflow/script; usado somente no Apply; sem default. |
| `AUTOFLOW_TEST_CPF_CNPJ`, `AUTOFLOW_TEST_PASSWORD` | Secrets opcionais para smoke test autenticado | GitHub Environment Secrets de `homolog`; dados sintéticos | Opcional | Não aplicável | `smoke_api_gateway.py`; smoke autenticado só roda com ambos presentes. Não usar dados reais ou Secrets de produção. |
| `GITHUB_TOKEN` | Secret automático do GitHub para abrir PRs | Gerado pelo Actions; não cadastrar | CI/CD automático de PR | Não aplicável | `pipeline.yml` e `pr-automatico.yaml`; `secrets.GITHUB_TOKEN`; sem cadastro manual. |

### Variables

| Nome | Tipo / finalidade | Onde; origem | CI / CD | Exemplo seguro | Consumidor; default/ausência |
|---|---|---|---|---|---|
| `AWS_REGION` | Variable; região AWS | GitHub Environment Variable de cada ambiente; atualmente `us-east-1` | CD | `us-east-1` | `pipeline.yml`; obrigatória, validada antes da descoberta. |
| `AWS_EXPECTED_ACCOUNT_ID` | Variable; conta permitida pelo guard | GitHub Environment Variable; ID da conta de destino | CD | Não incluir valor de conta em documentação | Workflow/script guard; obrigatório, validado como 12 dígitos e comparado à identidade AWS. |
| `AWS_TERRAFORM_STATE_BUCKET` | Variable; bucket S3 do ambiente | GitHub Environment Variable; bucket pertencente à conta esperada | CD | Não aplicável | Workflow/Terraform; obrigatório; owner é conferido antes de inicializar o backend. |
| `AWS_TF_STATE_KEY` | Variable; key S3 do State da aplicação | Obrigatória no GitHub Environment selecionado; sufixo fornecido externamente | CD | Prefixo `homolog/` ou `production/`, com sufixo externo | Workflow/Terraform; sem default, exclusiva do root e distinta das demais keys no bucket. |
| `AWS_RDS_STATE_KEY` | Variable; key S3 do State RDS consumido pela aplicação | Obrigatória no GitHub Environment selecionado; deve corresponder ao State RDS do mesmo ambiente | CD | Prefixo `homolog/` ou `production/`, com sufixo externo | Mapeada a `TF_VAR_rds_state_key`; sem default, igual à key do produtor RDS e distinta da key da aplicação. |
| `DOCKERHUB_USERNAME` | Variable; namespace Docker Hub | GitHub Repository Variable compartilhada | Push `develop` e `main` | `usuario-exemplo` | Namespace das imagens; sem default. Tags são sempre `${{ github.sha }}`. |
| `API_GATEWAY_CORS_ORIGINS` | Variable; allowlist CORS | GitHub Environment Variable de cada ambiente; lista JSON exata | CD | `[]` | Workflow/Terraform; obrigatória; deve ser array JSON. O endpoint HTTPS é descoberto da API do ambiente. |
| `TERRAFORM_APPLY_APPROVAL_CONFIGURED` | Variable; guard suplementar de aprovação | GitHub Environment Variable; `true` somente após configurar Required reviewers efetivos no mesmo Environment | CD | `true` | Ausente/diferente bloqueia Apply. Não substitui a proteção do Environment. |
| `ENABLE_NEWRELIC_APM` | Variable booleana que ativa o agente Java | GitHub Environment Variable de `homolog` e `production` | Plan/Apply | `true` ou `false` conforme ambiente | `homolog` exige `true`; `production` pode habilitar ou desabilitar. Quando habilitado, ausência da licença bloqueia sincronização/startup. |
| `NEW_RELIC_LICENSE_SECRET_ID` | Variable; identificador do Secret no AWS Secrets Manager | GitHub Environment Variable de cada ambiente em `autoflow` | Apply aprovado quando APM habilitado | Não aplicável | Obrigatória quando `ENABLE_NEWRELIC_APM=true`; o script usa o AWS SDK e nunca passa a licença em argumentos. |

## Terraform inputs

Os inputs são passados por `TF_VAR_*` no processo Terraform, não são GitHub Variables por si só. Em deploy, o workflow mapeia os valores homólogos acima. Para execução local use Terraform/configuração local protegida.

| Input (`infra/vars.tf`) | Obrigatório / default | Finalidade e origem |
|---|---|---|
| `project_name` | Opcional; `autoflow` | Prefixo de recursos; configuração local. |
| `region_default` | Opcional; `us-east-1` | Região provider; workflow injeta `AWS_REGION`. |
| `aws_allowed_account_ids` | Obrigatório; lista não vazia de IDs de 12 dígitos, sem default | Derivada pelo workflow de `AWS_EXPECTED_ACCOUNT_ID`; restringe o provider, o backend S3 e `terraform_remote_state`. |
| `terraform_state_bucket` | Obrigatório; sem default | Bucket de states; `AWS_TERRAFORM_STATE_BUCKET`. |
| `rds_state_key` | Obrigatória; sem default | State remoto RDS; workflow injeta `AWS_RDS_STATE_KEY` do mesmo Environment. |
| `cidr_vpc` | Opcional; `10.0.0.0/16` | CIDR VPC; configuração local. |
| `instance_type` | Opcional; `t3.medium` | Tipo de instância; configuração local. |
| `dockerhub_username`, `backend_image_tag`, `frontend_image_tag` | Obrigatórios; sem defaults | Imagens de deploy; variables correspondentes do Environment. |
| `enable_newrelic_apm` | Opcional; `false` | Controla se o startup exige a licença; `homolog` exige `true` e `production` pode habilitar ou desabilitar. |
| `deployment_environment` | Opcional; `local` | Derivado do Environment GitHub nos workflows de deploy. |

## Configuração da aplicação e Kubernetes

### License Key New Relic em runtime

O valor inicial da License Key é cadastrado como GitHub Environment Secret `NEW_RELIC_LICENSE_KEY` em cada ambiente onde `ENABLE_NEWRELIC_APM=true`. Depois da aprovação do job Apply, o script valida a identidade da conta/região e consulta o Secret identificado por `NEW_RELIC_LICENSE_SECRET_ID` na mesma região AWS. Se não existir, cria o SecretString usando o valor do Environment Secret; se existir com valor igual, reutiliza sem criar versão. Se os valores divergirem, bloqueia sincronização e informa que é necessária rotação controlada, sem expor valores. A licença é mantida em memória e enviada por stdin ao `kubectl`; não entra em Terraform state/plan, Helm values, logs, argumentos ou artifacts.

O `nri-bundle` e a aplicação usam por default o namespace `default`; o sincronizador cria/verifica `Secret/newrelic-license`, chave `licenseKey`, nesse mesmo namespace. Secrets Kubernetes não são compartilhados entre namespaces. A sessão AWS existente precisa de `secretsmanager:GetSecretValue` e, apenas quando ausente, `secretsmanager:CreateSecret` no secret configurado, além de permissões EKS/Kubernetes para criar/atualizar e ler Secrets em `default`. Falhas de autorização são sanitizadas. Não há dependência de ESO ou IRSA.

`autoflow-infra` usa duas GitHub Variables opcionais por Environment: `ENABLE_NEWRELIC_KUBERNETES` e `ENABLE_NEWRELIC_ACCOUNT_RESOURCES` (ausentes equivalem a `false`). São ativáveis separadamente e não exigem flags no dispatch. Os recursos de conta exigem `NEW_RELIC_ACCOUNT_ID`, `NEW_RELIC_REGION` e o Secret `NEW_RELIC_API_KEY`; Synthetic aceita `NEW_RELIC_SYNTHETIC_URL` e `NEW_RELIC_SYNTHETIC_LOCATIONS_JSON` somente em conjunto. O ambiente vem do Environment GitHub; cluster e endpoint API Gateway são consultados na AWS após validar conta/região. Exclusões só passam no guard com `workflow_dispatch`, `apply=true`, `allow_destructive=true` e aprovação do Environment.

### Application runtime

| Nome | Tipo / finalidade | Onde configurar; origem | CI / CD | Exemplo seguro | Consumidor; default/ausência |
|---|---|---|---|---|---|
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Variáveis de aplicação; conexão PostgreSQL | `k8s/secret.yaml`/ConfigMap ou ambiente local; credenciais do banco | Não requerido no CI; CD/runtime | `jdbc:postgresql://db.example.invalid:5432/autoflow_db`; demais não aplicável | `application.properties`, `docker-compose.yml`, manifests; URL/user/password requeridos. Em cada ambiente de deploy, username/password vêm dos `AUTOFLOW_DB_*` daquele Environment e são traduzidos pelo script para chaves Kubernetes. |
| `JWT_SECRET`, `JWT_EXPIRATION` | Variáveis de aplicação; assinatura e duração JWT | Secret Kubernetes ou configuração local segura | Não / CD/runtime | Não aplicável; `3600000` | `application.properties`; segredo requerido; duração default `3600000` ms. GitHub `AUTOFLOW_JWT_SECRET` mapeia a chave Kubernetes `JWT_SECRET`. |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Variáveis de aplicação; autenticação SMTP | Secret Kubernetes/local | Não / CD/runtime | Não aplicável | `application.properties`; sem defaults. |
| `APP_PUBLIC_BASE_URL`, `APP_PUBLIC_TOKEN_SECRET`, `APP_PUBLIC_TOKEN_EXPIRATION_DAYS` | Variáveis de aplicação; links/tokens públicos | Configuração local ou Secret | Não / CD/runtime | `http://localhost:8081`, `7` | URL default local; segredo tem fallback de desenvolvimento `dev-local-secret`; expiração default `7` dias. Em deploy, segredo é injetado por `AUTOFLOW_PUBLIC_TOKEN_SECRET`. |
| `FRONTEND_PUBLIC_BASE_URL`, `CORS_ALLOWED_ORIGINS`, `SPRING_JPA_SHOW_SQL` | Variáveis de aplicação; URL frontend, CORS, logging SQL | Configuração local/ConfigMap | Não / CD/runtime | URLs localhost; `[]`; `false` | URLs têm default local, show SQL default `false`; CORS não deve usar wildcard em homolog/produção e é configurado por Terraform/gateway. |
| `DEPLOYMENT_ENVIRONMENT`, `NEW_RELIC_LABELS`, `NEW_RELIC_REQUIRED`, `NEW_RELIC_APP_NAME` | Ambiente, rótulos e ativação do agente New Relic | ConfigMap e deployment renderizados por Terraform a partir do Environment | CD/runtime | Ambiente `local`; serviço `autoflow-backend` | O workflow injeta o Environment selecionado; local usa fallback `local`. `NEW_RELIC_REQUIRED` deriva de `ENABLE_NEWRELIC_APM`; licença usa o Secret `newrelic-license/licenseKey`. |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Variáveis de aplicação do container DB local | Ambiente local/Compose | Não / não é input CD AWS | `autoflow_db`; credenciais não aplicável | `docker-compose.yml`; sem defaults no Compose. |

**Divergências relevantes:** GitHub `AUTOFLOW_DB_USERNAME/PASSWORD`, `AUTOFLOW_JWT_SECRET`, `AUTOFLOW_MAIL_PASSWORD` e `AUTOFLOW_PUBLIC_TOKEN_SECRET` são nomes de transporte; chaves reais do Secret Kubernetes usam `SPRING_DATASOURCE_USERNAME/PASSWORD`, `JWT_SECRET`, `MAIL_PASSWORD`, `APP_PUBLIC_TOKEN_SECRET`. Não confundir `AWS_TF_STATE_KEY` da aplicação com keys de outros roots.

**Observação operacional:** os Environments e valores descritos são configuração necessária para ativar a pipeline; a existência/configuração efetiva no GitHub não foi verificada localmente. A documentação em [.github/CI-CD-AWS.md](../.github/CI-CD-AWS.md) descreve os passos restantes.

**Fontes:** `.github/workflows/pipeline.yml`, `.github/CI-CD-AWS.md`, `infra/vars.tf`, `application.properties`, `docker-compose.yml`, `k8s/` e `scripts/apply_k8s_secret.py`.
