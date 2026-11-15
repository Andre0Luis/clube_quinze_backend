# Integração com o Google Agenda

Duas formas, independentes:

1. **Para o cliente** (sem login Google): botões "Google Agenda" e "Apple / Outlook (.ics)" nos e-mails e `GET /appointments/{id}/calendar-links`.
2. **Agenda do clube** (esta página): o administrador conecta a conta Google dele pelo app e os agendamentos do clube passam a
   ser criados, atualizados e removidos automaticamente numa agenda do Google Agenda.

## Como funciona (opção 2)
- **Conectar:** `POST /api/v1/admin/integrations/google/connect` devolve a URL de login do Google (o app abre no navegador). O Google volta em
  `/integrations/google/callback`, o backend troca o código por tokens e guarda o **refresh token criptografado** (AES-256-GCM).
- **Escolher a agenda:** `GET .../calendars` lista as agendas da conta; `PUT .../calendar` com `{mode:"CREATE", name}` cria uma nova
  ("Clube Quinze") ou `{mode:"EXISTING", calendarId}` usa uma existente. Só depois disso a sincronização começa.
- **Sincronizar:** a cada mudança de agendamento (criar, confirmar, remarcar, aprovar, recusar, cancelar) o backend enfileira um pedido
  *depois do commit*; o consumidor cria/atualiza o evento. Um reconciliador (a cada 2 min) cobre falhas e faz a carga inicial.
  Cancelado = evento removido. Cores: confirmado verde, agendado amarelo, remarcação pendente laranja, concluído grafite.
- **Desconectar:** `DELETE .../google` revoga o token no Google e apaga as credenciais. Eventos já criados ficam na agenda do Google.
- **Acesso revogado** (o usuário removeu o acesso no Google): o estado vira `REVOKED` e o app pede para reconectar.
- Só o **nome do cliente** vai ao Google (nada de e-mail ou telefone).

## Configuração (variáveis / segredos)
| Variável | O que é |
|---|---|
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | credencial OAuth (Cloud Console → Credentials, tipo *Web application*) |
| `GOOGLE_TOKEN_ENCRYPTION_KEY` | chave AES-256 em base64 (32 bytes): `openssl rand -base64 32` |
| `GOOGLE_REDIRECT_URI` (opcional) | padrão: `<APP_PUBLIC_BASE_URL>/integrations/google/callback` |
| `GOOGLE_APP_RETURN_URL` (opcional) | deep link para o navegador voltar ao app |
| `GOOGLE_NEW_CALENDAR_NAME` (opcional) | nome da agenda criada (padrão "Clube Quinze") |

Sem `GOOGLE_CLIENT_ID` a integração fica desligada (`status = NOT_CONFIGURED`) e o resto do sistema não é afetado.
Os segredos ficam nos GitHub Environments `staging` e `production` e são passados pelos workflows de deploy.

## Google Cloud (uma vez)
- Projeto com a **Google Calendar API** ativada.
- Tela de consentimento *External*, com página inicial `https://clubequinzeapp.cloud/`, política `https://clubequinzeapp.cloud/privacy-policy`,
  domínio autorizado verificado no Search Console e escopos:
  `calendar.app.created`, `calendar.events`, `calendar.calendarlist.readonly`.
- **Publicar o app** (*In production*). Em *Testing* o refresh token expira em 7 dias. Sem verificação do Google aparece o aviso de "app não verificado" (limite de 100 usuários).
- URIs de redirecionamento: produção, homologação (`:8443`) e `http://localhost:8081/integrations/google/callback`.

## Operação
- `POST /api/v1/admin/integrations/google/sync` força a sincronização do que está pendente.
- `google_event_id` / `google_synced_for` em `agendamentos` indicam o vínculo; o vínculo é gravado por query e **não** altera `atualizado_em`.
- Trocar de conta ou de agenda zera o vínculo e recria os eventos na nova agenda.
