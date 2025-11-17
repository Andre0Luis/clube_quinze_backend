# 🔧 Solução para "database clube_quinze does not exist" na VPS

## 🎯 Solução Rápida (Copie e Cole)

Execute este comando na sua VPS:

```bash
docker exec -it clube-quinze-postgres psql -U postgres -c "CREATE DATABASE clube_quinze;" && docker compose restart api
```

**OU use o script automatizado:**

```bash
chmod +x scripts/fix-database.sh
./scripts/fix-database.sh
```

---

## 🔍 Por que isso aconteceu?

### Estava Funcionando Antes, Por Quê Agora Não?

**Resposta Rápida:** O volume do PostgreSQL JÁ EXISTIA na VPS!

Quando você rodou pela **primeira vez**, o PostgreSQL:
1. ✅ Criou o volume vazio
2. ✅ Viu que estava vazio
3. ✅ Rodou a inicialização automática
4. ✅ Criou o banco `clube_quinze` (via variável `POSTGRES_DB`)
5. ✅ Tudo funcionou!

Quando você **reiniciou o container** (ou o servidor):
1. ✅ Container subiu novamente
2. ✅ PostgreSQL verificou o volume
3. ❌ **VIU DADOS EXISTENTES no volume**
4. ❌ **PULOU a inicialização** (pensa: "já está configurado")
5. ❌ MAS o banco `clube_quinze` NÃO EXISTE nesse volume!

### Por Que o Banco Sumiu?

O banco não "sumiu". Provavelmente aconteceu uma destas situações:

**Cenário 1: Volume foi removido acidentalmente**
```bash
# Alguém rodou (acidentalmente):
docker compose down -v  # ← Flag -v REMOVE volumes!
docker volume prune     # ← Remove volumes não usados
```

**Cenário 2: Nome do projeto mudou**
```bash
# Volume antigo: projeto-antigo_postgres_data
# Volume novo:   clubequinzeapi_postgres_data (nome diferente!)
# PostgreSQL criou um volume NOVO e vazio
```

**Cenário 3: Banco foi dropado manualmente**
```bash
# Alguém conectou e dropou:
DROP DATABASE clube_quinze;  # ← Removeu o banco
```

**Cenário 4: Corrupção de dados**
- Desligamento abrupto do servidor
- Disco cheio durante write
- Problema no sistema de arquivos

### Como o PostgreSQL Funciona

```
PRIMEIRA VEZ (volume vazio):
├─ PostgreSQL inicia
├─ Verifica /var/lib/postgresql/data
├─ Está VAZIO ✓
├─ Executa scripts em /docker-entrypoint-initdb.d/
├─ Cria banco pela variável POSTGRES_DB
└─ SUCESSO! ✓

PRÓXIMAS VEZES (volume com dados):
├─ PostgreSQL inicia
├─ Verifica /var/lib/postgresql/data
├─ Tem DADOS ✓
├─ PULA inicialização (acha que já configurou)
└─ USA dados existentes
```

### O Problema

Se o volume tem dados MAS não tem o banco `clube_quinze`:
- PostgreSQL: "Já tenho dados, não preciso inicializar" ❌
- Aplicação: "Cadê o banco clube_quinze?" ❌
- ERRO: "database clube_quinze does not exist" ❌

---

## ✅ Soluções (escolha uma)

### Opção 1: Script Automatizado (RECOMENDADO)

```bash
# Dar permissão
chmod +x scripts/fix-database.sh

# Executar
./scripts/fix-database.sh
```

O script vai:
- ✓ Verificar se o container está rodando
- ✓ Verificar se o banco existe
- ✓ Criar o banco se não existir
- ✓ Testar a conexão
- ✓ Sugerir reiniciar a aplicação

### Opção 2: Comando Manual Rápido

```bash
# Criar o banco
docker exec -it clube-quinze-postgres psql -U postgres -c "CREATE DATABASE clube_quinze;"

# Reiniciar aplicação
docker compose restart api
```

### Opção 3: Via psql Interativo

```bash
# Entrar no psql
docker exec -it clube-quinze-postgres psql -U postgres

# Dentro do psql, executar:
CREATE DATABASE clube_quinze;

# Sair
\q

# Reiniciar aplicação
docker compose restart api
```

### Opção 4: Recriar Tudo (APAGA DADOS!)

⚠️ **ATENÇÃO: Isso apagará todos os dados!**

```bash
# Parar containers
docker compose down

# Remover volume (APAGA DADOS!)
docker volume rm clubequinzeapi_postgres_data

# Subir novamente (cria tudo do zero)
docker compose up -d
```

---

## 🔍 Verificar se Funcionou

### 1. Listar bancos de dados:
```bash
docker exec clube-quinze-postgres psql -U postgres -l
```

Você deve ver `clube_quinze` na lista.

### 2. Conectar ao banco:
```bash
docker exec -it clube-quinze-postgres psql -U postgres -d clube_quinze
```

Se conectar sem erro, está funcionando!

### 3. Ver logs da aplicação:
```bash
docker compose logs -f api
```

Não deve mais aparecer erro de "database does not exist".

---

## 📝 Para Prevenir no Futuro

### 1. Arquivos Atualizados

Os seguintes arquivos foram atualizados para prevenir isso:

**compose.yaml e docker-compose.yml:**
```yaml
volumes:
  - ./scripts/init-db.sh:/docker-entrypoint-initdb.d/init-db.sh
```

**scripts/init-db.sh:**
Script que sempre verifica e cria o banco se não existir.

### 2. Use os Scripts

Os scripts agora lidam com isso automaticamente:
- `fix-database.sh` - Corrige banco inexistente
- `check-volumes.sh` - Verifica status dos volumes
- `backup-database.sh` - Backup antes de mudanças

### 3. Backup Antes de Mudanças

```bash
# Sempre antes de mudanças importantes
./scripts/backup-database.sh
```

### 4. NUNCA Use `-v` Sem Querer!

```bash
# ❌ PERIGO! Remove volumes (apaga dados)
docker compose down -v

# ✅ SEGURO! Mantém volumes (preserva dados)
docker compose down
```

### 5. Investigar o Que Aconteceu na Sua VPS

Execute estes comandos para entender o que rolou:

```bash
# Ver quando o volume foi criado
docker volume inspect clubequinzeapi_postgres_data | grep CreatedAt

# Ver se há backups recentes
ls -lh backups/

# Ver histórico de comandos (se salvou)
history | grep docker

# Ver logs do sistema
journalctl -u docker -n 100

# Verificar disco cheio
df -h

# Ver quando containers foram criados
docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.CreatedAt}}"
```

### 6. Monitoramento Recomendado

Para produção, configure:

**a) Backups Automáticos Diários:**
```bash
# Adicionar ao crontab
crontab -e

# Backup diário às 2h
0 2 * * * cd /caminho/projeto && ./scripts/backup-database.sh

# Limpar backups com mais de 7 dias
0 3 * * * find /caminho/projeto/backups/ -name "*.sql*" -mtime +7 -delete
```

**b) Alertas de Disco:**
```bash
# Script para alertar se disco > 80%
#!/bin/bash
USAGE=$(df -h / | tail -1 | awk '{print $5}' | sed 's/%//')
if [ $USAGE -gt 80 ]; then
    echo "ALERTA: Disco em $USAGE%" | mail -s "Disco Cheio" seu@email.com
fi
```

**c) Health Check da Aplicação:**
```bash
# Verificar se aplicação está respondendo
curl -f http://localhost:8080/actuator/health || echo "API DOWN!"
```

**d) Logs Centralizados:**
```bash
# Salvar logs em arquivo
docker compose logs -f > logs/app_$(date +%Y%m%d).log 2>&1 &
```

---

## 🚀 Comandos Úteis para VPS

### Verificar Status:
```bash
# Status dos containers
docker compose ps

# Logs da aplicação
docker compose logs -f api

# Logs do banco
docker compose logs -f postgres

# Ver últimas 50 linhas
docker compose logs --tail=50 api
```

### Gerenciar Containers:
```bash
# Reiniciar tudo
docker compose restart

# Reiniciar apenas aplicação
docker compose restart api

# Parar tudo
docker compose stop

# Iniciar tudo
docker compose start

# Rebuild e restart
docker compose up --build -d
```

### Verificar Banco:
```bash
# Listar bancos
docker exec clube-quinze-postgres psql -U postgres -l

# Conectar ao banco
docker exec -it clube-quinze-postgres psql -U postgres -d clube_quinze

# Dentro do psql:
\dt              # Listar tabelas
\d usuarios      # Ver estrutura da tabela
SELECT COUNT(*) FROM usuarios;  # Contar registros
\q               # Sair
```

---

## 🎓 Entendendo o Problema

### O que são volumes Docker?

Volumes são espaços de armazenamento persistentes gerenciados pelo Docker:

```
Container PostgreSQL
    ↓
Volume (postgres_data)
    ↓
Disco da VPS (/var/lib/docker/volumes/)
```

### Por que o banco não foi criado?

1. PostgreSQL inicia
2. Verifica `/var/lib/postgresql/data` (montado do volume)
3. Encontra dados existentes
4. **Pula inicialização automática** (acha que já está configurado)
5. Mas o banco `clube_quinze` não existe nesse volume

### A solução:

Criar o banco manualmente já que o PostgreSQL pulou a inicialização.

---

## 📚 Documentação Relacionada

- **[DATA-PERSISTENCE.md](DATA-PERSISTENCE.md)** - Como os dados são persistidos
- **[SCRIPTS.md](SCRIPTS.md)** - Documentação de todos os scripts
- **[TROUBLESHOOTING.md](TROUBLESHOOTING.md)** - Outros problemas comuns
- **[QUICK-START.md](QUICK-START.md)** - Resumo rápido

---

## ✅ Checklist de Resolução

Execute na ordem:

- [ ] 1. Verificar containers rodando: `docker compose ps`
- [ ] 2. Executar script: `./scripts/fix-database.sh`
- [ ] 3. Verificar banco criado: `docker exec clube-quinze-postgres psql -U postgres -l`
- [ ] 4. Reiniciar aplicação: `docker compose restart api`
- [ ] 5. Verificar logs: `docker compose logs -f api`
- [ ] 6. Testar conexão: curl ou navegador
- [ ] 7. Fazer backup: `./scripts/backup-database.sh`

---

## 🆘 Ainda com Problemas?

Se após seguir os passos acima ainda tiver problemas:

1. **Verifique os logs completos:**
   ```bash
   docker compose logs --tail=200 > logs.txt
   cat logs.txt
   ```

2. **Verifique se há outros erros:**
   ```bash
   docker compose ps
   docker volume ls
   ```

3. **Consulte a documentação:**
   - [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
   - [DATA-PERSISTENCE.md](DATA-PERSISTENCE.md)

4. **Tente recriar (última opção, apaga dados):**
   ```bash
   docker compose down -v
   docker compose up -d
   ```

---

**Problema resolvido! 🎉**

Após criar o banco, seus dados estarão seguros e persistirão mesmo com reinicializações do container.

