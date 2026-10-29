# ADR-002 — Adoção de Kubernetes e Terraform

## Status

Aceita

## Contexto

O projeto AutoFlow inicialmente utilizava Docker e Docker Compose para executar seus serviços, sendo uma abordagem adequada para o desenvolvimento local e para uma infraestrutura simples.

Com a evolução do projeto, adotou-se infraestrutura como código e orquestração Kubernetes. Esta decisão registra a arquitetura final e provisionável.

A arquitetura declara Amazon EKS para execução dos containers e Amazon RDS PostgreSQL para persistência. Os roots de plataforma e banco ficam em repositórios separados; este repositório gerencia os recursos Kubernetes da aplicação.

Diante disso, foi necessário definir uma estratégia para o provisionamento e gerenciamento da infraestrutura, bem como para a orquestração e execução dos containers da aplicação.

## Decisão

Foi decidido utilizar **Terraform** como ferramenta de Infrastructure as Code (IaC) e **Kubernetes**, por meio do **Amazon EKS**, como plataforma de orquestração dos containers do AutoFlow.

O Terraform é utilizado nos repositórios correspondentes para provisionar e gerenciar recursos AWS, incluindo:

* VPC, rede, cluster Amazon EKS, node group, Amazon API Gateway HTTP API, VPC Link e ALB em `autoflow-infra`;
* Amazon RDS PostgreSQL em `autoflow-db`;
* Lambda e sua configuração de execução em `autoflow-lambda`;
* Deployments, Services, ConfigMaps e demais recursos Kubernetes da aplicação neste repositório.

O Terraform da aplicação também aplica e gerencia os manifestos Kubernetes necessários para executar os workloads.

O Kubernetes será responsável pela execução e gerenciamento dos workloads da aplicação no Amazon EKS, incluindo:

* Gerenciamento do ciclo de vida dos Pods;
* Distribuição e gerenciamento das réplicas;
* Exposição dos serviços;
* Health checks e mecanismos de recuperação;
* Escalabilidade dos workloads;
* Gerenciamento dos Deployments e demais recursos da aplicação.

O `HorizontalPodAutoscaler` do backend é declarado em `k8s/hpa.yaml` e gerenciado por `kubectl_manifest.backend_hpa` em `infra/k8s-deployment.tf`. A configuração solicita de 1 a 3 réplicas e CPU média-alvo de 50%. O Deployment ignora `spec.replicas` por meio de `ignore_fields = ["spec.replicas"]`, deixando o número de réplicas sob controle do HPA.

Dessa forma, o Terraform será utilizado principalmente para o **provisionamento e gerenciamento da infraestrutura**, enquanto o Kubernetes será responsável pela **orquestração e execução dos workloads da aplicação**.

## Consequências

### Consequências positivas

* A infraestrutura passa a ser definida como código, facilitando sua reprodução e manutenção.
* O provisionamento dos recursos da AWS torna-se automatizado e padronizado.
* O HPA do backend está integrado à configuração Terraform; após reset do Learner Lab, reexecute as pipelines CI/CD para provisionar novamente os recursos.
* O Kubernetes fornece mecanismos de recuperação e gerenciamento do ciclo de vida dos Pods.
* A infraestrutura pode ser versionada juntamente com o código-fonte do projeto.
* O ambiente pode ser recriado de forma consistente a partir dos arquivos Terraform e manifestos Kubernetes.
* Reduz-se a necessidade de configuração manual da infraestrutura.

### Consequências negativas

* A arquitetura passa a possuir maior complexidade operacional.
* É necessário conhecimento de Terraform, Kubernetes, AWS e seus respectivos recursos.
* O gerenciamento da infraestrutura exige maior quantidade de arquivos de configuração.
* A utilização do Amazon EKS e dos demais recursos da AWS gera custos de infraestrutura.
* Problemas de configuração no Terraform ou Kubernetes podem afetar diretamente a disponibilidade da aplicação.

## Justificativa

A adoção de Terraform e Kubernetes proporciona automação, escalabilidade declarativa e padronização do gerenciamento de infraestrutura e workloads.


Embora Docker Compose continue sendo adequado para o desenvolvimento local, Amazon EKS associado ao Terraform fornece uma plataforma declarativa de orquestração para a aplicação.
