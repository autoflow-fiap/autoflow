# CD automático após CI

## Habilitação por ambiente

O CD automático é opt-in e independente por ambiente. Configure a Repository Variable `AUTO_CD_ENABLED_HOMOLOG=true` para
homologação e, separadamente, `AUTO_CD_ENABLED_PRODUCTION=true` para produção. Valor ausente ou diferente de `true` mantém
o ambiente correspondente desabilitado. O gate recebe esses valores diretamente pelo contexto `vars`, sem consultar APIs
administrativas dos GitHub Environments. Esta alteração não cria nem modifica Variables, Secrets, Environments ou Rulesets
no GitHub.

## Fluxo automático

Um `push` em `develop` ou `main` inicia o gate do workflow `.github/workflows/pipeline.yml`. O gate valida branch,
repositório e SHA do evento, seleciona a Repository Variable correspondente à branch e, quando habilitada, aguarda a CI
`AutoFlow CI` do mesmo repositório, branch e SHA. Somente a execução do workflow `ci.yml` com conclusão `success` libera
os jobs do CD; falha, cancelamento, `skipped`, divergência ou timeout bloqueia a execução. Os jobs fazem checkout e
confirmam o SHA validado.

Se a Repository Variable correspondente estiver ausente ou não for exatamente `true`, os jobs automáticos de build,
validação e Plan não são liberados. O caminho automático não publica imagens Docker e não executa Terraform Apply. O Plan
automático usa o Environment e as proteções existentes e pode executar verificações AWS necessárias para gerar o Plan.

## Execução manual e Apply

O `workflow_dispatch` existente permanece disponível independentemente das Variables `AUTO_CD_ENABLED_*`. A publicação de imagens e
o Apply continuam exclusivos de uma execução manual com `apply=true`, aprovação exigida pelo GitHub Environment e os
guards de Apply existentes, incluindo `TERRAFORM_APPLY_APPROVAL_CONFIGURED=true`. A aprovação do Plan, inclusive de um
Plan automático, não autoriza Apply. Os controles de operações destrutivas permanecem separados.

## Validação

Os testes locais verificam a validação da origem do evento, do branch/SHA e da CI correspondente, além dos casos de
Variable ausente, `false` e `true`. Essa validação local não comprova a execução remota de workflows, a configuração
efetiva dos Environments nem a disponibilidade ou permissões de recursos AWS.
