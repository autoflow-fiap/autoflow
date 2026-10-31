# Diagramas de sequência — AutoFlow

Esta pasta registra os principais fluxos temporais entre usuário, frontend Angular, API Spring Boot, casos de uso,
adapters e PostgreSQL. Os diagramas complementam o Swagger e não substituem o contrato HTTP.

## Índice

| Fluxo                             | Arquivo                                                                                  | Escopo                                        |
|-----------------------------------|------------------------------------------------------------------------------------------|-----------------------------------------------|
| Abertura da OS                   | [fluxo_principal.mermaid](fluxo_principal.mermaid)                                       | JWT, API Gateway, VPC Link, ALB, backend e persistência |
| Aprovação/recusa autenticada      | [aprovacao_recusa_orcamento.mermaid](aprovacao_recusa_orcamento.mermaid)                 | Decisão do cliente com JWT                    |
| Aprovação/recusa pública          | [aprovacao_recusa_orcamento_publico.mermaid](aprovacao_recusa_orcamento_publico.mermaid) | Token, consulta e decisão sem login           |
| Execução e reparo adicional       | [execucao_os_reparo_adicional.mermaid](execucao_os_reparo_adicional.mermaid)             | Estoque, execução e orçamento complementar    |
| Início de serviço e estoque       | [inicio_servico_baixa_estoque.mermaid](inicio_servico_baixa_estoque.mermaid)             | Disponibilidade e baixa de itens              |
| Registro de reparo adicional      | [registro_reparo_adicional.mermaid](registro_reparo_adicional.mermaid)                   | Criação, publicação e notificação             |
| Decisão de orçamento complementar | [decisao_orcamento_complementar.mermaid](decisao_orcamento_complementar.mermaid)         | Aprovação/recusa do reparo vinculado          |
| Conclusão da OS                   | [conclusao_servico_os.mermaid](conclusao_servico_os.mermaid)                             | Finalização de serviços e status              |

## Como ler

1. O ator interage com o frontend ou com a página pública.
2. O frontend envia a requisição para o controller correspondente.
3. Spring Security valida JWT, perfil ou token público quando aplicável.
4. O controller delega ao caso de uso.
5. Gateways/adapters acessam PostgreSQL, e-mail, PDF ou publicação de orçamento.
6. A resposta retorna pelo controller para o frontend.

Os nomes usados nos participantes representam responsabilidades atuais. Classes auxiliares podem mudar sem alterar o
fluxo, desde que o contrato e os efeitos de negócio sejam preservados.

## Regras comuns

- endpoints administrativos exigem autenticação e autorização;
- endpoints públicos de orçamento validam hash e expiração antes da leitura ou decisão;
- decisões de orçamento são idempotentes quando repetem o mesmo estado e bloqueiam conflitos;
- baixa de estoque ocorre antes de iniciar serviço quando houver itens necessários;
- a OS só é finalizada quando todos os serviços aplicáveis estão concluídos;
- falhas de negócio são convertidas pelo `GlobalExceptionHandler`.

## Visualização e manutenção

Arquivos `.mermaid` podem ser visualizados no GitHub, GitLab, extensões de IDE ou visualizadores compatíveis. Ao mudar
um fluxo, atualize o arquivo correspondente e verifique controllers, casos de uso e endpoints no código.

O fluxo público detalhado está em [`../fluxo-orcamento-publico.md`](../fluxo-orcamento-publico.md), e a arquitetura de
camadas em [`../architecture.md`](../architecture.md).
