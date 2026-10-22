# Variables e secrets

Catálogo observado no código e configuração deste repositório. Push em `develop` publica imagens, inicia Plan e, após aprovação do Environment `homolog`, executa Apply/rollout/smoke tests. `workflow_dispatch` é uma alternativa manual; Pull Requests e `main` não fazem deploy. Produção não integra este catálogo.

## GitHub Actions

Os jobs de PR não publicam imagens. Push em `develop` requer acesso ao Docker Hub para publicar tags SHA. `SONAR_TOKEN` e `SNYK_TOKEN` são usados pelos scans. Plan e Apply usam GitHub-hosted runners e os mesmos Repository Secrets/Variables; somente Apply usa o Environment `homolog` e aguarda reviewers efetivos. Não configure cópias dos valores operacionais no Environment, onde poderiam sobrepor as configurações de repositório.

### Secrets

| Nome | Tipo / finalidade | Onde configurar; origem | CI / CD | Exemplo seguro | Consumidor; default/ausência |
|---|---|---|---|---|---|
| `SONAR_TOKEN` | Secret; autenticação SonarQube | GitHub Repository secret; token emitido pelo SonarQube | CI condicional | Não aplicável | `.github/workflows/pipeline.yml`; step não roda em PR de fork, mas quando roda sem token a análise pode falhar. |
| `SNYK_TOKEN` | Secret; autenticação Snyk | GitHub Repository secret; token Snyk | CI condicional | Não aplicável | `pipeline.yml`; scan em push `develop` ou dispatch com `publish_images=true`; sem valor, scan falha. |
| `DOCKERHUB_PASSWORD` | Secret; autenticação para publicar imagem | GitHub Repository secret; token Docker Hub de publicação | Push `develop` | Não aplicável | `pipeline.yml`; obrigatório no job de publicação. |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` | Secrets; credenciais temporárias AWS da mesma sessão | Repository Secrets; sessão AWS Academy Learner Lab | CD | Não aplicável | `pipeline.yml`; usados por Plan e Apply; renovar juntos conforme validade da sessão. |
| `TF_PLAN_ENCRYPTION_KEY` | Secret; chave para cifrar o plan binário antes do upload | Repository Secret; gerar valor aleatório com no mínimo 32 caracteres | Plan/Apply | Não aplicável | `pipeline.yml`; mesmo valor nos dois jobs; nunca publicar em logs/artifact. |
| `AUTOFLOW_DB_USERNAME`, `AUTOFLOW_DB_PASSWORD` | Secrets; credenciais de banco do Secret Kubernetes | Repository Secrets; credenciais do banco de homolog | CD | Não aplicável | `pipeline.yml` → `scripts/apply_k8s_secret.py`; usados somente no Apply; sem defaults. |
| `AUTOFLOW_JWT_SECRET` | Secret; assinatura JWT da aplicação | Repository Secret; segredo compatível com autenticação da aplicação | CD | Não aplicável | Mesmo workflow/script; usado somente no Apply; sem default. |
| `AUTOFLOW_MAIL_PASSWORD` | Secret; autenticação SMTP | Repository Secret; credencial SMTP de homolog | CD | Não aplicável | Mesmo workflow/script; usado somente no Apply; sem default. |
| `AUTOFLOW_PUBLIC_TOKEN_SECRET` | Secret; tokens públicos de orçamento | Repository Secret; segredo de homolog | CD | Não aplicável | Mesmo workflow/script; usado somente no Apply; sem default. |
| `AUTOFLOW_TEST_CPF_CNPJ`, `AUTOFLOW_TEST_PASSWORD` | Secrets opcionais para smoke test autenticado | Repository Secrets; conta de teste sintética | Opcional | Não aplicável | `smoke_api_gateway.py`; smoke autenticado só roda com ambos presentes. Não usar dados reais. |
| `GITHUB_TOKEN` | Secret automático do GitHub para abrir PRs | Gerado pelo Actions; não cadastrar | CI/CD automático de PR | Não aplicável | `pipeline.yml` e `pr-automatico.yaml`; `secrets.GITHUB_TOKEN`; sem cadastro manual. |

### Variables

| Nome | Tipo / finalidade | Onde; origem | CI / CD | Exemplo seguro | Consumidor; default/ausência |
|---|---|---|---|---|---|
| `AWS_REGION` | Variable; região AWS | Repository Variable; região aprovada | CD | `us-east-1` | `pipeline.yml`; obrigatória, sem default. |
| `AWS_EXPECTED_ACCOUNT_ID` | Variable; conta permitida pelo guard | Repository Variable; ID da conta alvo | CD | `123456789012` | Workflow/script guard; obrigatória, sem default. |
| `AWS_TERRAFORM_STATE_BUCKET`, `AWS_TF_STATE_KEY` | Variables; bucket S3 e state da aplicação | Repository Variables; bucket/key existentes | CD | `autoflow-tfstate-homolog-exemplo`, `application/autoflow/terraform.tfstate` | Workflow/Terraform; obrigatórias, sem defaults. |
| `DOCKERHUB_USERNAME` | Variable; namespace Docker Hub | GitHub Repository variable | Push `develop` | `usuario-exemplo` | Namespace das imagens; sem default. Tags são sempre `${{ github.sha }}`. |
| `API_GATEWAY_ORIGIN`, `API_GATEWAY_CORS_ORIGINS` | Variables; origem e CORS do gateway | Repository Variables; URL e lista JSON exatas | CD | `https://api.example.invalid`, `[]` | Workflow/Terraform; obrigatórias; CORS deve ser array JSON. |
| `EKS_CLUSTER_NAME` | Variable; nome do cluster | Repository Variable; nome real | CD | `eks-autoflow` | Workflow; deve corresponder ao cluster implantado. |
| `TERRAFORM_APPLY_APPROVAL_CONFIGURED` | Variable; guard suplementar de aprovação | Repository Variable; `true` somente após configurar Required reviewers no Environment `homolog` | CD | `true` | Workflow; ausente/diferente bloqueia Apply. Não substitui a proteção do Environment. |

## Terraform inputs

Os inputs são passados por `TF_VAR_*` no processo Terraform, não são GitHub Variables por si só. Em deploy, o workflow mapeia os valores homólogos acima. Para execução local use Terraform/configuração local protegida.

| Input (`infra/vars.tf`) | Obrigatório / default | Finalidade e origem |
|---|---|---|
| `project_name` | Opcional; `autoflow` | Prefixo de recursos; configuração local. |
| `region_default` | Opcional; `us-east-1` | Região provider; workflow injeta `AWS_REGION`. |
| `terraform_state_bucket` | Obrigatório; sem default | Bucket de states; `AWS_TERRAFORM_STATE_BUCKET`. |
| `rds_state_key` | Opcional; `rds/terraform.tfstate` | State remoto RDS; workflow injeta `AWS_RDS_STATE_KEY`. |
| `cidr_vpc` | Opcional; `10.0.0.0/16` | CIDR VPC; configuração local. |
| `instance_type` | Opcional; `t3.medium` | Tipo de instância; configuração local. |
| `dockerhub_username`, `backend_image_tag`, `frontend_image_tag` | Obrigatórios; sem defaults | Imagens de deploy; variables correspondentes do Environment. |

## Configuração da aplicação e Kubernetes

| Nome | Tipo / finalidade | Onde configurar; origem | CI / CD | Exemplo seguro | Consumidor; default/ausência |
|---|---|---|---|---|---|
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Variáveis de aplicação; conexão PostgreSQL | `k8s/secret.yaml`/ConfigMap ou ambiente local; credenciais do banco | Não requerido no CI; CD/runtime | `jdbc:postgresql://db.example.invalid:5432/autoflow_db`; demais não aplicável | `application.properties`, `docker-compose.yml`, manifests; URL/user/password requeridos. Em homolog, username/password vêm dos `AUTOFLOW_DB_*` e são traduzidos pelo script para chaves Kubernetes. |
| `JWT_SECRET`, `JWT_EXPIRATION` | Variáveis de aplicação; assinatura e duração JWT | Secret Kubernetes ou configuração local segura | Não / CD/runtime | Não aplicável; `3600000` | `application.properties`; segredo requerido; duração default `3600000` ms. GitHub `AUTOFLOW_JWT_SECRET` mapeia a chave Kubernetes `JWT_SECRET`. |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Variáveis de aplicação; autenticação SMTP | Secret Kubernetes/local | Não / CD/runtime | Não aplicável | `application.properties`; sem defaults. |
| `APP_PUBLIC_BASE_URL`, `APP_PUBLIC_TOKEN_SECRET`, `APP_PUBLIC_TOKEN_EXPIRATION_DAYS` | Variáveis de aplicação; links/tokens públicos | Configuração local ou Secret | Não / CD/runtime | `http://localhost:8081`, `7` | URL default local; segredo tem fallback de desenvolvimento `dev-local-secret`; expiração default `7` dias. Em deploy, segredo é injetado por `AUTOFLOW_PUBLIC_TOKEN_SECRET`. |
| `FRONTEND_PUBLIC_BASE_URL`, `CORS_ALLOWED_ORIGINS`, `SPRING_JPA_SHOW_SQL` | Variáveis de aplicação; URL frontend, CORS, logging SQL | Configuração local/ConfigMap | Não / CD/runtime | URLs localhost; `[]`; `false` | URLs têm default local, show SQL default `false`; CORS não deve usar wildcard em homolog/produção e é configurado por Terraform/gateway. |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Variáveis de aplicação do container DB local | Ambiente local/Compose | Não / não é input CD AWS | `autoflow_db`; credenciais não aplicável | `docker-compose.yml`; sem defaults no Compose. |

**Divergências relevantes:** GitHub `AUTOFLOW_DB_USERNAME/PASSWORD`, `AUTOFLOW_JWT_SECRET`, `AUTOFLOW_MAIL_PASSWORD` e `AUTOFLOW_PUBLIC_TOKEN_SECRET` são nomes de transporte; chaves reais do Secret Kubernetes usam `SPRING_DATASOURCE_USERNAME/PASSWORD`, `JWT_SECRET`, `MAIL_PASSWORD`, `APP_PUBLIC_TOKEN_SECRET`. Não confundir `AWS_TF_STATE_KEY` da aplicação com keys de outros roots.

**Observação operacional:** os Environments e valores descritos são configuração necessária para ativar a pipeline; a existência/configuração efetiva no GitHub não foi verificada localmente. A documentação em [.github/CI-CD-AWS.md](../.github/CI-CD-AWS.md) descreve os passos restantes.

**Fontes:** `.github/workflows/pipeline.yml`, `.github/CI-CD-AWS.md`, `infra/vars.tf`, `application.properties`, `docker-compose.yml`, `k8s/` e `scripts/apply_k8s_secret.py`.
