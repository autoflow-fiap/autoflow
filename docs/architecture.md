# Arquitetura da aplicação

O AutoFlow é um monólito Spring Boot organizado segundo Clean Architecture. A aplicação permanece implantável como uma
unidade, mas separa regras de negócio, casos de uso, adapters técnicos e HTTP.

## Dependências entre camadas

```text
presentation ──→ application ──→ domain
infrastructure ──→ application
config ──→ presentation / application / infrastructure
```

As setas representam dependência de código. A infraestrutura implementa portas definidas pela aplicação, enquanto `config`
faz a composição das implementações. A apresentação não acessa repositories ou entidades JPA diretamente.

## Camadas e pacotes

### `domain`

Entidades e regras de negócio independentes de Spring, JPA, HTTP e infraestrutura. Os componentes são agrupados por
contexto, como `ordemservico`, `orcamento`, `cliente`, `veiculo` e `pecainsumo`.

### `application`

Orquestra casos de uso e contratos internos:

- `port/in`: portas de entrada e interfaces de use case;
- `usecases`: implementações dos casos de uso;
- `gateway`: portas de saída para persistência e integrações;
- `input` e `output`: modelos internos de entrada e saída;
- `policy`, `security`, `transaction` e `exception`: políticas e serviços transversais.

Alguns componentes ainda usam anotações de composição do Spring ou Lombok na camada de aplicação. Isso é uma dependência
técnica residual monitorada; não deve ser ampliada para JPA, HTTP, repositories concretos ou detalhes de infraestrutura.

### `infrastructure`

Implementa detalhes técnicos:

- `persistence/entity`: entidades JPA;
- `persistence/repository`: interfaces Spring Data;
- `persistence/adapters`: implementação dos gateways da aplicação;
- `persistence/mapper`: conversões de persistência;
- `security`: JWT, autenticação e usuário atual;
- `notificacao`, `orcamento` e `configuration`: e-mail, PDF, publicação e configurações externas.

### `presentation`

Adapta HTTP para a aplicação. Contém controllers, requests, responses, mappers REST, validações de entrada e
`GlobalExceptionHandler`.

### `config`

É o ponto de composição do Spring: registra beans, mappers, relógio e OpenAPI. Não deve receber regras de negócio.

## Regras verificáveis

O teste [`ArchitectureBoundaryTest`](../backend/src/test/java/com/autoflow/architecture/ArchitectureBoundaryTest.java)
verifica, entre outras regras:

- domínio sem dependências de frameworks ou camadas externas;
- aplicação sem dependência de presentation, infrastructure, entidades JPA ou detalhes HTTP;
- presentation sem acesso a JPA, repositories e infraestrutura concreta;
- adapters implementando gateways da aplicação;
- controllers em `presentation` e entidades JPA em `infrastructure.persistence`;
- ausência de ciclos entre pacotes.

Para nomes e critérios de movimentação, consulte [`package-convention.md`](conventions/package-convention.md) e
[`ADR-001`](adr/ADR-001-sequencia-migracao-clean-architecture.md).
