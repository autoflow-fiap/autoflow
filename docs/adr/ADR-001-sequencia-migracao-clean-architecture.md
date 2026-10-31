# ADR-001 — Sequência de migração para Clean Architecture

## Situacao

Aceita; implementação evoluída desde o contexto inicial descrito abaixo.

## Contexto

Esta decisão foi registrada durante uma etapa anterior da migração. O trecho a seguir descreve o baseline daquela etapa,
não a estrutura atual do backend:

```text
controller -> service -> mapper -> repository -> domain/JPA entity -> database
```

Evidências do baseline histórico:

- A organização então observada usava `controller`, `service`, `mapper` e `repository` como camadas técnicas.
- Entidades JPA e serviços de aplicação ainda estavam acoplados à estrutura anterior.
- A ordem incremental foi escolhida para preservar contratos, regras de negócio e schema enquanto cada componente era migrado.

A migracao precisa acontecer sem alterar endpoints, payloads, status HTTP, autorizacao, schema de banco, migrations ou comportamento observavel. O risco principal e iniciar por OS ou mover pacotes fisicamente de forma ampla, misturando reorganizacao arquitetural com mudanca funcional.

## Decisao

A migracao para Clean Architecture sera incremental, por componente, em fatias pequenas e verificaveis. Nao sera feita uma reorganizacao global de pacotes em uma unica entrega.

A ordem definida e:

1. Executar a base transversal da issue `[REFATORACAO] Revisar infraestrutura, cobertura e fronteiras arquiteturais`.
2. Usar `servico` como piloto preferencial.
3. Repetir o padrao em `cliente` ou `veiculo`, conforme disponibilidade de testes de caracterizacao.
4. Migrar `pecainsumo` e estoque depois que gateways e adapters estiverem validados.
5. Migrar OS somente depois de checklist minimo de caracterizacao e depois que gateways de estoque, usuario, orcamento, notificacao e historico estiverem definidos.

`servico` e o piloto preferencial porque possui CRUD menor, testes existentes, soft-delete e metrica separavel. `cliente` e `veiculo` continuam alternativas aceitaveis se a equipe registrar justificativa de risco. OS nao deve ser o primeiro piloto.

A convencao de packages alvo e:

| Camada | Destino | Regra |
|---|---|---|
| Domain puro | `backend/src/main/java/com/autoflow/domain/<componente>` | Nao deve importar Spring, JPA, Hibernate, HTTP, mensageria, DTO REST ou repository concreto. |
| Application | `backend/src/main/java/com/autoflow/application/<componente>` | Deve conter use cases, commands, queries e DTOs internos. |
| Gateways | `backend/src/main/java/com/autoflow/application/gateway` | Use cases devem depender destes contratos, nao de Spring Data. |
| Persistence | `backend/src/main/java/com/autoflow/infrastructure/persistence/<componente>` | Deve conter adapters, mappers de persistencia e detalhes JPA do componente migrado. |
| Spring Data repositories | `backend/src/main/java/com/autoflow/infrastructure/persistence/repository` | Repositories concretos ficam na infraestrutura quando houver migracao fisica. |
| REST | `backend/src/main/java/com/autoflow/presentation/rest/<componente>` | Destino de controllers, requests e responses REST. A movimentacao fisica so ocorre com testes de contrato do componente. |

Enquanto um componente nao for migrado, os pacotes legados podem permanecer como estao. A convencao `presentation/rest` pode ser documentada antes da movimentacao fisica.

Antes de migrar um componente, devem existir contrato REST caracterizado, testes de sucesso e falhas relevantes, dependencias mapeadas, criterios de preservacao do frontend e decisao explicita de package conforme esta ADR.

Antes de migrar OS, devem estar caracterizados pelo menos abertura, inclusao de servico solicitado, atribuicao de mecanico, diagnostico, itens necessarios, laudo, orcamento, aprovacao ou recusa, execucao, baixa de estoque, historico, acompanhamento do cliente, entrega, metricas e erros principais. `OrdemServicoFluxoIT`, `OrdemServicoServiceTest` e testes de controller devem servir como baseline antes de qualquer separacao JPA/use cases em OS.

## Situação observada atualmente

O código atual está organizado nos pacotes `presentation`, `application`, `domain`, `infrastructure` e `config`. A documentação da estrutura e das fronteiras verificadas está em [`architecture.md`](../architecture.md). A migração estrutural descrita no contexto histórico já avançou; esta ADR permanece como registro da decisão de migrar incrementalmente e preservar contratos, não como descrição do layout atual.

## Consequências

Fica mais facil executar a migracao com rastreabilidade, porque a equipe passa a ter uma ordem clara, um piloto preferencial, criterios de entrada e saida por componente e uma regra explicita para adiar OS ate existir caracterizacao suficiente.

Fica mais facil preservar contratos publicos, porque endpoints, payloads, status HTTP, autorizacao, mensagens REST e schema de banco passam a ser restricoes formais da migracao, nao detalhes implicitos.

Fica mais facil revisar cards de componente, porque novos gateways devem ficar em `application/gateway`, repositories/adapters concretos em `infrastructure/persistence`, e controllers REST so devem se mover fisicamente para `presentation/rest` quando o componente estiver coberto por testes de contrato.

Fica mais dificil fazer refatoracoes mecanicas amplas, porque movimentos globais de packages, inicio por OS ou mudancas de contrato como efeito colateral deixam de ser aceitaveis sem nova decisao ou card especifico.

A equipe deve tratar esta ADR como pre-condicao das issues `[REFATORACAO] Extrair casos de uso de clientes preservando contratos REST`, `[REFATORACAO] Remover dependencia de seguranca e DTO REST do fluxo de veiculos`, `[REFATORACAO] Separar cadastro e metricas de servicos`, `[REFATORACAO] Isolar politica de estoque de pecas e insumos`, `[REFATORACAO] Extrair casos de uso da ordem de servico` e `[REFATORACAO] Revisar infraestrutura, cobertura e fronteiras arquiteturais`.

Esta ADR nao altera runtime, endpoints, schema, migrations, frontend ou deploy. Qualquer mudanca de contrato REST deve ser tratada como nova decisao ou card, nao como efeito colateral de Clean Architecture.
