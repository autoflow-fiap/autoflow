# Fluxo principal da Ordem de Serviço

Este diagrama representa o fluxo de abertura da OS, incluindo autenticação via JWT, encaminhamento pela API Gateway, processamento no backend Spring Boot e persistência no banco relacional.

```mermaid
---
config:
  theme: mc
---
sequenceDiagram
    autonumber
    actor Atendente
    participant Frontend as Frontend Angular
    participant Gateway as API Gateway HTTP API v2
    participant ALB as ALB interno via VPC Link
    participant Backend as Backend Spring Boot no EKS
    participant Security as Spring Security / JWT
    participant OSController as Controller de Ordem de Serviço
    participant OSUseCases as Caso de uso de abertura da OS
    participant Cliente as Caso de uso de cliente
    participant Veiculo as Caso de uso de veículo
    participant DB as RDS PostgreSQL

    Note over Atendente,Frontend: O JWT foi obtido previamente pelo fluxo de autenticação CPF/CNPJ.
    Atendente->>Frontend: Informa cliente, veículo e serviços
    Frontend->>Gateway: POST /api/ordens-servico + Bearer JWT
    Gateway->>ALB: HTTP_PROXY pelo VPC Link
    ALB->>Backend: Encaminha ao serviço Spring Boot
    Backend->>Security: Autenticar e autorizar requisição
    Security->>DB: Carregar usuário/perfil atual pelo subject do JWT
    DB-->>Security: Usuário e perfil
    Security-->>Backend: Identidade autorizada
    Backend->>OSController: Requisição autenticada
    OSController->>OSUseCases: criar(cpfCnpj, veículo, serviços)
    OSUseCases->>Cliente: buscarPorCpfCnpj(cpfCnpj)
    Cliente->>DB: Consultar cliente
    DB-->>Cliente: Cliente encontrado
    OSUseCases->>Veiculo: buscar ou associar veículo ao cliente
    Veiculo->>DB: Consultar/cadastrar veículo
    DB-->>Veiculo: Veículo associado
    OSUseCases->>DB: Salvar OS, serviços solicitados e histórico
    DB-->>OSUseCases: OS criada
    OSUseCases-->>OSController: OrdemServicoResponse
    OSController-->>Backend: Resposta HTTP 201
    Backend-->>ALB: Resposta
    ALB-->>Gateway: Resposta
    Gateway-->>Frontend: HTTP 201 — OrdemServicoResponse
```
