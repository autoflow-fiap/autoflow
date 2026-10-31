# CD automático após CI

## Habilitação por ambiente

O CD automático é opt-in e independente em cada GitHub Environment. Para habilitar `homolog` ou `production`, configure a
Environment Variable `AUTO_CD_ENABLED` com o valor literal `true` no Environment correspondente. Valor ausente, variável
inexistente ou qualquer outro valor mantém o CD automático desabilitado naquele ambiente. Esta alteração não cria nem
modifica Variables, Secrets, Environments ou Rulesets no GitHub.

## Fluxo automático

Um `push` em `develop` ou `main` inicia o gate do workflow `.github/workflows/pipeline.yml`. O gate valida branch,
repositório e SHA do evento, lê `AUTO_CD_ENABLED` no Environment selecionado (`homolog` para `develop`; `production`
para `main`) e, quando habilitado, aguarda a CI `AutoFlow CI` do mesmo repositório, branch e SHA. Somente a execução do
workflow `ci.yml` com conclusão `success` libera os jobs do CD; falha, cancelamento, `skipped`, divergência ou timeout
bloqueia a execução. Os jobs fazem checkout e confirmam o SHA validado.

Se `AUTO_CD_ENABLED` estiver ausente ou não for exatamente `true`, os jobs automáticos de build, validação e Plan não
são liberados. O caminho automático não publica imagens Docker e não executa Terraform Apply. O Plan automático usa o
Environment e as proteções existentes e pode executar verificações AWS necessárias para gerar o Plan.

## Execução manual e Apply

O `workflow_dispatch` existente permanece disponível independentemente de `AUTO_CD_ENABLED`. A publicação de imagens e
o Apply continuam exclusivos de uma execução manual com `apply=true`, aprovação exigida pelo GitHub Environment e os
guards de Apply existentes, incluindo `TERRAFORM_APPLY_APPROVAL_CONFIGURED=true`. A aprovação do Plan, inclusive de um
Plan automático, não autoriza Apply. Os controles de operações destrutivas permanecem separados.

## Validação

Os testes locais verificam a validação da origem do evento, do branch/SHA e da CI correspondente, além dos casos de
Variable ausente, `false` e `true`. Essa validação local não comprova a execução remota de workflows, a configuração
efetiva dos Environments nem a disponibilidade ou permissões de recursos AWS.
