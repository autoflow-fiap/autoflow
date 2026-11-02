# Convenção de pacotes — Clean Architecture

Esta convenção descreve a estrutura atual do backend e complementa [`architecture.md`](../architecture.md) e
[`ADR-001`](../adr/ADR-001-sequencia-migracao-clean-architecture.md).

## Estrutura atual

```text
com.autoflow
├── domain/<componente>                  # regras e modelos de negócio puros
├── application
│   ├── input/<componente>               # comandos, filtros e entradas internas
│   ├── output/<componente>              # saídas internas
│   ├── port/in/<componente>             # portas de entrada/use cases
│   ├── gateway                         # portas de saída
│   ├── usecases/<componente>            # implementações dos use cases
│   ├── policy, security, transaction    # políticas e serviços transversais
│   └── exception                        # erros da aplicação
├── infrastructure
│   ├── persistence/entity/<componente>  # entidades JPA
│   ├── persistence/repository           # Spring Data
│   ├── persistence/mapper               # mapeamento de persistência
│   ├── persistence/adapters             # adapters dos gateways
│   ├── security                          # JWT e identidade
│   ├── notificacao, orcamento             # integrações externas
│   └── configuration                      # propriedades e adapters técnicos
├── presentation/<componente>            # controllers, requests e responses
└── config                                # composição de beans e OpenAPI
```

O agrupamento por componente é permitido dentro das camadas. Não crie módulos ou microserviços separados para cada
componente.

## Regras de dependência

| Pacote           | Pode depender de                                           | Deve evitar                                                          |
|------------------|------------------------------------------------------------|----------------------------------------------------------------------|
| `domain`         | Java e outros tipos do domínio                             | Spring, JPA, HTTP, Lombok, DTO REST e infraestrutura                 |
| `application`    | `domain` e portas internas                                 | `presentation`, `infrastructure`, JPA, HTTP e repositories concretos |
| `infrastructure` | `domain`, `application` e frameworks                       | `presentation`                                                       |
| `presentation`   | `application` e tipos de domínio necessários ao mapeamento | JPA, repositories e adapters concretos                               |
| `config`         | composição do framework                                    | regras de negócio                                                    |

Algumas classes da aplicação ainda usam anotações de composição do Spring, Lombok ou validação interna. Essa dependência
residual deve permanecer limitada e não justifica introduzir detalhes de persistência, HTTP ou segurança técnica nos
casos de uso.

## Convenções de nomes

| Artefato                  | Convenção                    | Exemplo                    |
|---------------------------|------------------------------|----------------------------|
| Porta de entrada          | verbo + `UseCase`            | `CriarClienteUseCase`      |
| Implementação de use case | mesmo nome + `Impl`          | `CriarClienteUseCaseImpl`  |
| Porta de saída            | responsabilidade + `Gateway` | `ClienteGateway`           |
| Adapter                   | responsabilidade + `Adapter` | `ClienteRepositoryAdapter` |
| Entidade JPA              | responsabilidade + `Entity`  | `ClienteEntity`            |
| Controller                | recurso + `Controller`       | `OrcamentoController`      |
| Request REST              | finalidade + `Request`       | `RecusarOrcamentoRequest`  |
| Response REST             | finalidade + `Response`      | `OrcamentoResponse`        |

Classes auxiliares `*Service` podem existir na aplicação quando encapsulam uma operação coesa, mas não devem virar
fachadas com regras de vários componentes.

## Critérios para novas movimentações

Antes de mover uma classe ou componente:

1. preserve endpoints, payloads, status HTTP e autorização;
2. mapeie dependências e defina a camada de destino;
3. adicione ou confirme testes de sucesso, falha e contrato;
4. mantenha persistência e transações funcionando;
5. execute o teste arquitetural e a suíte afetada;
6. registre a decisão quando a movimentação não seguir esta convenção.

O teste [`ArchitectureBoundaryTest`](../../backend/src/test/java/com/autoflow/architecture/ArchitectureBoundaryTest.java)
é a validação executável das fronteiras principais.
