# Registro histórico da arquitetura de deploy da Fase 2

Este documento e o diagrama associado preservam o desenho da entrega da Fase 2 e são históricos. Não representam a arquitetura vigente. Para a organização atual do backend, consulte [arquitetura da aplicação](../architecture.md); a entrada vigente por API Gateway está documentada na [arquitetura de infraestrutura](https://github.com/autoflow-fiap/autoflow-infra/blob/develop/docs/api-gateway/arquitetura.md).

O desenho registra o modelo de deploy documentado para a Fase 2. AWS/EKS, RDS PostgreSQL e Docker Hub aparecem nos artefatos versionados; implantação efetiva não é inferida por este registro histórico.

## Desenho

A fonte Mermaid está em [`arquitetura-deploy-fase-2.mermaid`](arquitetura-deploy-fase-2.mermaid).

## Componentes representados

| Componente                                 | Fonte versionada                                                                                                                                                  |
|--------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Pipeline GitHub Actions                    | [`.github/workflows/pipeline.yml`](../../.github/workflows/pipeline.yml)                                                                                          |
| Registry Docker Hub                        | [pipeline GitHub Actions](../../.github/workflows/pipeline.yml)                                                                                                  |
| VPC, subnets, rotas, NAT e security groups | [`infra/`](../../infra/)                                                                                                                                          |
| Cluster EKS e node group                   | [`eks-cluster.tf`](https://github.com/autoflow-fiap/autoflow-infra/blob/develop/infra/eks-cluster.tf) e [`eks-node.tf`](https://github.com/autoflow-fiap/autoflow-infra/blob/develop/infra/eks-node.tf) |
| RDS PostgreSQL                             | [`rds-postgresql.tf`](https://github.com/BrunoSousa06/autoflow-db/blob/develop/infra/rds-postgresql.tf)                                                          |
| Frontend e backend                         | [`k8s/`](../../k8s/)                                                                                                                                              |
| Configuração e Secrets                     | [`k8s/configmap.yaml`](../../k8s/configmap.yaml) e [injeção segura de Secrets](../../README.md#segredos-kubernetes-da-aplicação); manifests preenchidos não são versionados |
| HPA do backend                             | [`k8s/hpa.yaml`](../../k8s/hpa.yaml)                                                                                                                              |

## Fluxo representado

1. Usuários acessam o frontend por um Service `LoadBalancer`.
2. O frontend encaminha as chamadas ao Service do backend.
3. O backend acessa o RDS PostgreSQL e o servidor SMTP.
4. O GitHub Actions executa testes, Sonar e builds; o Snyk roda como verificação paralela em push para `develop`.
5. Após os builds, as imagens são publicadas no Docker Hub.
6. Em push para `develop`, o job `deploy-aws` executa Terraform para provisionar/atualizar a AWS e aplicar os
   manifestos Kubernetes versionados.

## Diferenças em relação à configuração atual

- Os Deployments usam a tag mutável `latest`.
- O HPA do backend é gerenciado pelo Terraform da aplicação, com mínimo 1, máximo 3 réplicas e CPU-alvo de 50%; `ignore_fields = ["spec.replicas"]` deixa a contagem sob controle do HPA. Isso é configuração declarada, não validação operacional: o Learner Lab foi resetado e o recurso precisa ser verificado após recriar a infraestrutura.
- O bucket S3 `state-autoflow-terraform` é criado/validado pelo pipeline e não é representado como backend remoto
  Terraform no desenho.
- O perfil local em [`k8s-local/`](../../k8s-local/) usa PostgreSQL StatefulSet e Metrics Server; ele não deve ser
  confundido com o perfil AWS/EKS representado no desenho principal.

Essas observações descrevem limitações operacionais do fluxo atual. O desenho não contém credenciais, tokens ou valores
de Secret.
