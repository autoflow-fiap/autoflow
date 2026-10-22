# Registro histórico da arquitetura de deploy da Fase 2

Este documento e o diagrama associado preservam o desenho da entrega da Fase 2. Eles não representam integralmente a configuração atual da branch, que recebeu preparação local para Kong, mudanças nos Services e backend remoto Terraform. Para o estado atual e seus limites, consulte a [visão de arquitetura compartilhada](../../../.agents/ai-context/01-system-architecture.md), o [progresso do Kong](../../../docs/specs/kong-gateway/08-progresso-implementacao.md) e o [runbook de operação do Kong](../kong-gateway-operations.md).

O desenho registra o modelo de deploy documentado para a Fase 2. AWS/EKS, RDS PostgreSQL e Docker Hub aparecem nos artefatos versionados; implantação efetiva não é inferida por este registro histórico.

## Desenho

A fonte Mermaid está em [`arquitetura-deploy-fase-2.mermaid`](arquitetura-deploy-fase-2.mermaid).

## Componentes representados

| Componente                                 | Fonte versionada                                                                                                                                                  |
|--------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Pipeline GitHub Actions                    | [`.github/workflows/pipeline.yml`](../../.github/workflows/pipeline.yml)                                                                                          |
| Registry Docker Hub                        | [pipeline GitHub Actions](../../.github/workflows/pipeline.yml)                                                                                                  |
| VPC, subnets, rotas, NAT e security groups | [`infra/`](../../infra/)                                                                                                                                          |
| Cluster EKS e node group                   | [`eks-cluster.tf`](../../../autoflow-infra/infra/eks-cluster.tf) e [`eks-node.tf`](../../../autoflow-infra/infra/eks-node.tf)                                       |
| RDS PostgreSQL                             | [`rds-postgresql.tf`](../../../autoflow-db/infra/rds-postgresql.tf)                                                                                                |
| Frontend e backend                         | [`k8s/`](../../k8s/)                                                                                                                                              |
| ConfigMaps e Secret                        | [`k8s/configmap.yaml`](../../k8s/configmap.yaml), [`k8s/frontend-configmap.yaml`](../../k8s/frontend-configmap.yaml) e [`k8s/secret.yaml`](../../k8s/secret.yaml) |
| HPA do backend                             | [`k8s/hpa.yaml`](../../k8s/hpa.yaml)                                                                                                                              |

## Fluxo representado

1. Usuários acessam o frontend por um Service `LoadBalancer`.
2. O frontend encaminha as chamadas ao Service do backend.
3. O backend acessa o RDS PostgreSQL e o servidor SMTP.
4. O GitHub Actions executa testes, Sonar e builds; o Snyk roda como verificação paralela em push para `develop`.
5. Após os builds, as imagens são publicadas no Docker Hub.
6. Em push para `develop`, o job `deploy-aws` executa Terraform para provisionar/atualizar a AWS e aplicar os
   manifestos Kubernetes versionados.

## Observações do estado atual

- Os Deployments usam a tag mutável `latest`.
- O manifesto do HPA do backend declara mínimo 1, máximo 3 réplicas e CPU em 50%; sua aplicação em produção não é comprovada pela presença do arquivo.
- O bucket S3 `state-autoflow-terraform` é criado/validado pelo pipeline e não é representado como backend remoto
  Terraform no desenho.
- O perfil local em [`k8s-local/`](../../k8s-local/) usa PostgreSQL StatefulSet e Metrics Server; ele não deve ser
  confundido com o perfil AWS/EKS representado no desenho principal.

Essas observações descrevem limitações operacionais do fluxo atual. O desenho não contém credenciais, tokens ou valores
de Secret.
