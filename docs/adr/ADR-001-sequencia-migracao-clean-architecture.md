# ADR-001 — Migração incremental para Clean Architecture

- **Status:** Aceita.
- **Data:** 12/07/2026.
- **Escopo:** organização interna do backend Spring Boot, sem mudança pretendida de comportamento externo.

## Contexto

O baseline descrito quando esta decisão foi registrada organizava o backend por função técnica:

```text
controller → service → mapper → repository → entidade de domínio/JPA → banco
```

O histórico da ADR registra controllers, services, mappers e repositories como estruturas transversais. Services conheciam DTOs REST, mappers e repositories Spring Data concretos; entidades JPA estavam no pacote `domain`. Isso fazia a regra de negócio depender de detalhes de persistência e apresentação, dificultava testar casos de uso sem infraestrutura e tornava mudanças de banco ou HTTP arriscadas para o núcleo.

As forças em oposição eram separar responsabilidades e proteger o domínio, mas continuar entregando funcionalidades sem interromper a evolução, preservar a API consumida pelo Angular e não misturar reorganização de pacotes com alterações de regra, autorização ou schema. A Ordem de Serviço (OS) concentra estados e integrações de orçamento, estoque, usuário, notificação, histórico e acompanhamento, portanto tem raio de regressão maior que CRUDs menores.

Endpoints, payloads, status HTTP, autorização, mensagens observáveis, schema e migrations existentes são restrições de compatibilidade da refatoração. O histórico original não registra métricas comparativas nem um estudo formal de alternativas.

## Decisão

Adotar migração incremental por componente, em fatias pequenas, revisáveis e testáveis; não fazer uma reestruturação global em uma única entrega. A preferência registrada foi estabelecer primeiro a base transversal, usar `servico` como piloto de menor risco, seguir para `cliente` ou `veiculo` conforme a caracterização disponível, depois tratar `pecainsumo`/estoque e deixar OS para quando seus fluxos e gateways estivessem caracterizados.

`servico` foi preferido no plano inicial por ter CRUD menor, testes existentes, soft-delete e métrica separável. `cliente` e `veiculo` eram opções aceitáveis com justificativa de risco; OS não deveria ser o primeiro piloto. Essa ordem é o plano histórico, não uma afirmação de que a migração ainda está pendente ou de que a cronologia de cada componente foi comprovada.

As fronteiras atualmente verificáveis são:

| Área | Responsabilidade e regra |
|---|---|
| `domain` | Modelo e regras sem dependência de camadas externas ou frameworks técnicos. |
| `application` | Portas de entrada, casos de uso, modelos internos e gateways de saída; casos de uso não dependem de Spring Data, HTTP ou entidades JPA. |
| `infrastructure` | Adapters, entidades/repositories JPA, segurança e integrações técnicas; implementa gateways definidos pela aplicação. |
| `presentation` | Controllers, validação e mapeamento dos contratos HTTP para entradas/saídas da aplicação; não acessa persistência diretamente. |
| `config` | Composição de beans e configurações, sem regras de negócio. |

A implementação atual já apresenta `presentation`, `application`, `domain`, `infrastructure` e `config`, use cases e gateways, adapters de persistência e um teste ArchUnit de fronteiras. A arquitetura é um monólito Spring Boot. Permanecem dependências técnicas limitadas, como anotações de composição Spring/Lombok em partes da aplicação, registradas em [`architecture.md`](../architecture.md); a decisão não afirma isolamento absoluto de frameworks.

Para cada componente, a migração deve caracterizar contratos e falhas, mapear dependências, separar portas/use cases e adapters, manter os DTOs REST compatíveis e executar testes de unidade, integração, contrato/comportamento e arquitetura disponíveis antes de avançar. OS exige caracterização de abertura, diagnóstico, orçamento, execução, estoque, histórico, acompanhamento, entrega, métricas e erros.

## Alternativas consideradas

As opções abaixo são uma análise retrospectiva baseada na estrutura observada e no contexto registrado; não há evidência de benchmark ou avaliação formal comparativa da equipe.

| Alternativa | Vantagens | Desvantagens | Motivo para não adotá-la |
|---|---|---|---|
| Migração incremental por componente (decisão) | Limita o raio de mudança, permite comparar comportamento por fatia e prioriza componentes menores antes de OS. | Mantém temporariamente duas formas de organização; exige disciplina de fronteiras, testes e revisão para evitar dependências cruzadas. | Adotada para equilibrar redução de acoplamento com continuidade de entrega e preservação de contratos. |
| Reestruturação completa em uma etapa | Uniformiza os pacotes rapidamente e evita coexistência prolongada. | Grande diff e raio de regressão; combina mudanças estruturais em muitos domínios e dificulta isolar falhas, especialmente em OS. | Não escolhida pelo risco de alteração simultânea de contratos, persistência e regras, sem evidência de caracterização global suficiente. |
| Manter a organização técnica anterior | Menor custo imediato e menos movimentação de código. | Preserva dependência entre services, DTOs, repositories concretos e entidades JPA; evolução de domínio continua condicionada a frameworks. | Não resolve o acoplamento e a testabilidade que motivaram a decisão. |

## Consequências positivas

- A direção de dependências fica explícita e verificável, com domínio no núcleo e infraestrutura substituível por portas.
- Regras e casos de uso podem ser testados sem iniciar o banco ou o contexto web quando a unidade permitir.
- A migração por fatias facilita revisão, localização de regressões e preservação dos contratos existentes.
- Adapters concentram conversões entre objetos de domínio e persistência; apresentação concentra conversões REST.

## Consequências negativas

- A coexistência temporária de componentes migrados e não migrados acrescenta complexidade e pode perpetuar duas convenções.
- Portas, DTOs internos, mappers e adapters aumentam o número de tipos e arquivos.
- A proteção depende de testes de fronteira e de revisão contínua; os testes não comprovam por si só a equivalência de todo comportamento histórico.
- As dependências residuais de Spring/Lombok na aplicação limitam a pureza arquitetural e devem ser monitoradas sem ampliar o acoplamento.

## Riscos e mitigação

| Risco | Mitigação definida/observável |
|---|---|
| Regressão em HTTP, autorização ou comportamento do frontend durante movimentação | Caracterizar rotas, payloads, status e falhas antes da alteração; manter requests/responses e validar controllers/integrações. O OpenAPI e testes estão versionados; execução atual não é inferida. |
| Mudança acidental de schema ou regra de persistência | Separar refatoração de migrations e mudanças funcionais; revisar Flyway e adapters por componente. |
| Coexistência virar estado permanente ou gerar dependências entre camadas | Aplicar os limites de pacote e `ArchitectureBoundaryTest`; registrar exceções e não criar novos atalhos para código legado. |
| OS sofrer regressão de fluxo cruzado | Não iniciar por OS; exigir caracterização dos fluxos e gateways listados antes de qualquer separação estrutural relevante. |
| Confundir o plano inicial com o estado atual | Usar o layout do código e [`architecture.md`](../architecture.md) como descrição atual; tratar a sequência acima como histórico e não como backlog implícito. |

## Referências

- [RFC-001 — Migração para Clean Architecture](../rfc/RFC-001-migracao-clean-architecture.md)
- [Arquitetura da aplicação](../architecture.md)
- [Convenção de pacotes](../conventions/package-convention.md)
- [`ArchitectureBoundaryTest`](../../backend/src/test/java/com/autoflow/architecture/ArchitectureBoundaryTest.java)
- [OpenAPI versionado](../openapi/README.md)
- [Guia de testes e qualidade](../testing-and-quality.md)
- [Registro original desta ADR no histórico](https://github.com/autoflow-fiap/autoflow/commit/e3ca42b2b50a3f06495bf7d6ef14cbbf79228392)
