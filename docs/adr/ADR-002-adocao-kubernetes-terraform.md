# ADR-002 — Adoção de Kubernetes e Terraform

- **Status:** Aceita.
- **Data:** 22/08/2026.
- **Escopo:** provisionamento declarado de infraestrutura AWS e gerenciamento de workloads do AutoFlow.

## Contexto

Docker Compose continua atendendo à execução local da aplicação. Para a implantação distribuída descrita nos repositórios, a solução passou a declarar recursos AWS e workloads Kubernetes. A necessidade arquitetural é repetir a configuração de rede, cluster, banco, autenticação e aplicação, além de gerenciar ciclos de vida, réplicas, verificações de saúde e exposição privada sem depender de configuração manual não rastreável.

As forças em oposição são automação, reprodutibilidade e mecanismos Kubernetes versus custo, curva operacional, quantidade de serviços e arquivos, dependência da AWS e necessidade de coordenar vários repositórios e seus estados. Esta ADR preserva a escolha já registrada; não há evidência de benchmark formal de custo/capacidade ou de teste comparativo entre orquestradores.

A topologia provisionável declara Amazon EKS, VPC e node group em `autoflow-infra`; RDS PostgreSQL em `autoflow-db`; autenticação Lambda em `autoflow-lambda`; e recursos Kubernetes da aplicação neste repositório. `autoflow-infra` também mantém roots independentes para API Gateway e New Relic. Os roots têm Terraform State separado por ambiente e dependências entre repositórios; sua configuração local não comprova que recursos estejam ativos na AWS.

## Decisão

Adotar Terraform como IaC e Kubernetes gerenciado por Amazon EKS para os workloads AWS. Docker Compose permanece opção de desenvolvimento local, não o mecanismo principal de implantação declarado.

Responsabilidades observadas nos quatro repositórios:

| Repositório/root | Ownership declarado |
|---|---|
| `autoflow-infra/infra` | VPC, subnets, Security Groups, cluster EKS e node group. |
| `autoflow-db/infra` | RDS PostgreSQL, configuração de rede e State próprio. |
| `autoflow-lambda/infra` | Lambda de autenticação e recursos de execução. |
| `autoflow-infra/infra/api-gateway` | HTTP API v2, rotas, VPC Link, ALB interno, target groups e alarmes CloudWatch. |
| `autoflow/infra` | ConfigMaps, Deployments, Services/NodePorts e HPA via Terraform; o Secret de runtime é sincronizado pelo workflow protegido e seu valor não é gerenciado em arquivos ou State Terraform. |
| `autoflow-infra/infra/newrelic` | Release Helm e recursos de conta New Relic, quando habilitados. |

São seis roots lógicos distribuídos por quatro repositórios; não há orquestração automática única entre eles. Os workflows selecionam `homolog` em `develop` e `production` em `main`, usam States S3 distintos por root/ambiente e têm dependências que impõem uma ordem operacional. Terraform Plan descreve alterações propostas; apenas Apply autorizado altera recursos.

O HPA `autoscaling/v2` do backend é gerenciado como manifest pelo Terraform, de 1 a 3 réplicas, com utilização média alvo de CPU de 50%. O Deployment entrega requests de CPU e memória e o Terraform ignora `spec.replicas` para permitir que o HPA ajuste esse campo. A escala exige API `metrics.k8s.io` funcional e capacidade do node group; a configuração não comprova que tais métricas ou escalonamentos foram observados em runtime.

## Alternativas consideradas

Comparação retrospectiva de opções tecnicamente plausíveis; os registros consultados não apresentam avaliação formal ou benchmark entre elas.

| Alternativa | Vantagens | Desvantagens/impacto operacional e financeiro | Motivo para não adotá-la como principal |
|---|---|---|---|
| Docker Compose como implantação | Configuração direta, familiar e útil para desenvolvimento ou uma execução simples. | Menos recursos declarativos de rollout, autoscaling e reconciliação multi-node; exige solução externa para alta disponibilidade e escala. Não há comparação de custo medida. | Mantido para desenvolvimento local, mas não corresponde à plataforma declarada para workloads AWS distribuídos. |
| Amazon ECS/Fargate | Integração AWS e abstração da gestão de nodes; pode reduzir o trabalho de operar Kubernetes/node groups. | Exigiria adaptar manifests, ferramentas e processo operacional; pode trazer cobrança por recursos gerenciados, sem estudo de custo neste projeto. | Não escolhida; não há evidência de avaliação formal ou migração de workloads para ECS. |
| Amazon EKS/Kubernetes (decisão) | API declarativa, Deployments/Services/HPA e possibilidade de reutilizar manifests Kubernetes locais e de nuvem. | Custo de cluster/nodes/rede, operação de Kubernetes e dependência do provedor gerenciado; HPA depende de métricas e capacidade adequada. | Escolhida por ser a plataforma já representada pelos recursos e manifests versionados, não por benchmark comparativo. |
| Provisionamento manual | Baixa barreira inicial para alterações isoladas e pequenas. | Drift, baixa repetibilidade, auditoria e recuperação difíceis; risco de divergência entre ambientes. | Terraform mantém a infraestrutura e recursos gerenciados declarativos e revisáveis em Git. |
| Terraform (decisão) | Plano revisável, configuração versionada, dependências explícitas e integração com workflows. | State remoto sensível à integridade/ownership, curva de aprendizado, provider drift e necessidade de proteger cada Apply. | Escolhido como mecanismo IaC observado nos roots; operação segura depende de State e gates corretos. |

## Consequências positivas

- Rede, cluster, recursos Kubernetes e configurações aplicáveis são descritos em código revisável e podem ser recriados seguindo suas dependências.
- Deployments e probes fornecem reconciliação de Pods e verificações de saúde; o HPA declara faixa de escala do backend.
- States independentes delimitam ownership e reduzem acoplamento direto entre repositórios.
- O fluxo Plan separado de Apply permite inspecionar mudanças antes de provisionar.

## Consequências negativas

- EKS, node group, VPC, NAT, load balancers, RDS e serviços de observabilidade geram custo e dependência da AWS; nenhum valor de custo ou limite orçamentário foi validado nesta decisão.
- A operação exige conhecimento conjunto de Terraform, AWS, Kubernetes, IAM, rede, Helm e dos contratos entre roots.
- Múltiplos States e pipelines não encadeados aumentam o risco de ordem incorreta, drift, state ausente ou estado recuperado de forma incompleta.
- HPA não funciona corretamente sem `metrics.k8s.io`, requests de recursos e capacidade de nodes; não foi encontrada instalação de Metrics Server declarada neste repositório de infraestrutura.
- Plan/Apply ou manifests incompatíveis podem indisponibilizar tráfego e workloads; dependência AWS limita portabilidade sem mudanças.

## Riscos e mitigação

| Risco | Mitigação existente ou necessária |
|---|---|
| State errado, duplicado ou inicializado vazio sobre recursos existentes | Keys exclusivas por root e ambiente, backend remoto, validações de contrato, bootstrap isolado e bloqueio a edição manual; interromper diante de ownership ambíguo. |
| Sequência de roots incorreta | Runbook define dependências VPC/EKS → RDS → Lambda → API Gateway → aplicação → New Relic; executar Plan revisado em cada etapa. Pipelines seguem independentes. |
| Custo ou indisponibilidade de cluster/nodes/RDS | Revisar Plan e capacidade antes de Apply, observar métricas/custos na conta e evitar produção não autorizada; custos efetivos permanecem dependentes da conta/uso e não foram medidos. |
| HPA sem métricas ou capacidade | Confirmar `metrics.k8s.io`, requests e capacidade de nodes antes de habilitar; a presença do YAML não prova escala operacional. |
| Reset do AWS Learner Lab | Seguir bootstrap separado do bucket e runbook de primeiro provisionamento; confirmar identidade/State e recriar dependências em ordem. Não tratar reset como autorização para sobrescrever State. |
| Mudança destrutiva ou rollback incompleto | Aplicar somente Plan atual e aprovado pelo workflow; recuperar por mudanças declarativas e novos Plans, preservando State e registrando que não há rollback global automático. |

## Referências

- [RFC-002 — Kubernetes e Terraform](../rfc/RFC-002-kubernetes-terraform.md)
- [README e ordem dos roots](../../README.md#ordem-de-provisionamento)
- [Manifesto HPA](../../k8s/hpa.yaml) e [Terraform de Deployments/HPA](../../infra/k8s-deployment.tf)
- [Arquitetura de infraestrutura vigente — API Gateway, VPC Link e ALB](https://github.com/autoflow-fiap/autoflow-infra/blob/develop/docs/api-gateway/arquitetura.md)
- [Runbook de primeiro provisionamento e State](https://github.com/autoflow-fiap/autoflow-infra/blob/develop/docs/eks-primeiro-provisionamento.md)
- [Registro original da ADR no histórico](https://github.com/autoflow-fiap/autoflow/commit/ce0d71b47ddd180c017c50a12321a9e0f8afd010)
