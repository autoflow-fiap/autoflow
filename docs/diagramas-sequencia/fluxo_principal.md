# Diagrama de sequência — Abertura da ordem de serviço

O fluxo resume a abertura autenticada de uma OS sem detalhar componentes internos do backend. Na rota externa do API Gateway, o frontend chama `POST /api/ordens-servico`; a integração remove `/api` e encaminha `POST /ordens-servico` ao backend. O contrato OpenAPI versionado descreve o endpoint do backend, protegido por Bearer JWT, e declara `201 Created` para a criação bem-sucedida. O diagrama de login CPF/CNPJ e emissão do JWT está na [arquitetura do API Gateway](https://github.com/autoflow-fiap/autoflow-infra/blob/docs%2Farchitecture-adrs-rfcs-20261102/docs/api-gateway/arquitetura.md).

```mermaid
sequenceDiagram
    autonumber
    actor Atendente
    participant Frontend as Frontend Angular
    participant Gateway as API Gateway
    participant Backend as Backend Spring Boot
    participant DB as PostgreSQL

    Note over Atendente,Frontend: JWT obtido previamente pelo fluxo de autenticação CPF/CNPJ.
    Atendente->>Frontend: Informa CPF/CNPJ do cliente, veículo e serviços
    Frontend->>Gateway: POST /api/ordens-servico + Bearer JWT
    Gateway->>Backend: HTTP_PROXY pelo VPC Link, encaminha POST /ordens-servico
    Backend->>Backend: Valida assinatura e expiração do JWT e extrai CPF/CNPJ do subject
    Backend->>DB: Recarrega usuário, perfil e estado atuais pelo subject
    DB-->>Backend: Usuário e perfil atuais
    Backend->>Backend: Autoriza perfil ATENDENTE ou ADMIN e valida o payload
    Backend->>DB: Busca cliente pelo CPF/CNPJ informado
    DB-->>Backend: Dados do cliente
    Backend->>DB: Busca ou cadastra veículo associado ao cliente
    DB-->>Backend: Veículo associado
    loop Para cada serviço solicitado
        Backend->>DB: Consulta serviço e obtém nome e valor atuais
        DB-->>Backend: Serviço encontrado
    end
    Backend->>DB: Persiste OS RECEBIDA, serviços solicitados e histórico
    DB-->>Backend: OS criada
    Backend->>Backend: Gera token de acompanhamento e prepara link de resposta
    Backend->>DB: Persiste hash do token e validade de 30 dias
    DB-->>Backend: Hash e validade persistidos
    Note over Backend,DB: A notificação do link é tentada quando há e-mail e falha é tratada sem desfazer a OS.
    Backend-->>Gateway: HTTP 201 Created + OrdemServicoResponse e acompanhamentoUrl
    Gateway-->>Frontend: HTTP 201 Created + OrdemServicoResponse
    Frontend-->>Atendente: Exibe a OS criada
```

## Referências

- [Contrato OpenAPI versionado](../openapi/autoflow-api.json)
- [Documentação e operação `POST /ordens-servico`](../openapi/README.md)
- [Arquitetura e rotas do API Gateway](https://github.com/autoflow-fiap/autoflow-infra/blob/docs%2Farchitecture-adrs-rfcs-20261102/docs/api-gateway/arquitetura.md)
