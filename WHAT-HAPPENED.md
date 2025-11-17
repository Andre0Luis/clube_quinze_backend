# 🔍 O Que Aconteceu? Timeline do Problema

## 📅 Linha do Tempo Provável

### ✅ ANTES (Funcionando)

```
Dia 1 - Primeira Execução:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. docker compose up -d
   └─ PostgreSQL inicia
   └─ Volume criado (vazio)
   └─ Script de init roda
   └─ Banco "clube_quinze" criado ✓
   └─ Aplicação conecta ✓
   └─ TUDO FUNCIONA! 🎉

Dias 2-5 - Funcionando Normal:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
- Container rodando
- Dados sendo salvos no volume
- Banco "clube_quinze" existe
- Aplicação funcionando ✓
```

### ❌ DEPOIS (Quebrou)

```
Dia 6 - Algo Aconteceu:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Possibilidade 1: Remoção Acidental do Volume
┌─────────────────────────────────────┐
│ $ docker compose down -v            │ ← Flag -v remove volumes!
│ $ docker volume prune               │ ← Remove volumes não usados
│ $ docker system prune -a --volumes  │ ← Remove tudo!
└─────────────────────────────────────┘
   └─ Volume deletado
   └─ Todos os dados perdidos ❌

Possibilidade 2: Mudança no Nome do Projeto
┌─────────────────────────────────────┐
│ Antes: /home/user/api               │ → Volume: api_postgres_data
│ Depois: /home/user/Clube Quinze API │ → Volume: clubequinzeapi_postgres_data
└─────────────────────────────────────┘
   └─ Volume NOVO criado
   └─ Volume ANTIGO ainda existe (com dados)
   └─ Mas Docker usa o NOVO (vazio)

Possibilidade 3: Banco Dropado
┌─────────────────────────────────────┐
│ $ psql -U postgres                  │
│ postgres=# DROP DATABASE clube_quinze; │
└─────────────────────────────────────┘
   └─ Banco removido manualmente
   └─ Volume ainda existe
   └─ Mas banco clube_quinze sumiu

Possibilidade 4: Reiniciou e PostgreSQL Ficou Confuso
┌─────────────────────────────────────┐
│ Servidor reiniciou                  │
│ Volume tinha dados CORROMPIDOS      │
│ PostgreSQL limpou e recriou         │
│ Mas não criou o banco clube_quinze  │
└─────────────────────────────────────┘

Possibilidade 5: Migração Entre Servidores
┌─────────────────────────────────────┐
│ Servidor Antigo: tinha dados        │
│ Servidor Novo: volume vazio/diferente │
└─────────────────────────────────────┘
```

### 🔄 O Que Acontece Agora (Loop de Erro)

```
docker compose up -d
   │
   ├─ PostgreSQL inicia
   │  └─ Verifica /var/lib/postgresql/data
   │  └─ Encontra dados existentes (ou pasta vazia com metadados)
   │  └─ "Ah, já está configurado!"
   │  └─ PULA inicialização ❌
   │  └─ NÃO cria banco "clube_quinze"
   │
   └─ Aplicação tenta conectar
      └─ ERRO: database "clube_quinze" does not exist ❌
      └─ Container fica reiniciando infinitamente 🔄
```

---

## 🕵️ Como Descobrir o Que Aconteceu na SUA VPS

Execute estes comandos na sua VPS:

### 1. Ver Volumes Existentes
```bash
docker volume ls
```

**O que procurar:**
- Se tem `clubequinzeapi_postgres_data` ✓
- Se tem volumes órfãos (outros nomes)
- Comparar com nome do diretório do projeto

### 2. Inspecionar o Volume
```bash
docker volume inspect clubequinzeapi_postgres_data
```

**O que procurar:**
```json
{
    "CreatedAt": "2025-11-08T10:30:00Z",  ← Quando foi criado?
    "Mountpoint": "/var/lib/docker/volumes/...",
    "Labels": {
        "com.docker.compose.project": "clubequinzeapi",  ← Nome do projeto
        "com.docker.compose.version": "2.x.x"
    }
}
```

### 3. Ver Quando os Containers Foram Criados
```bash
docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.CreatedAt}}"
```

**O que procurar:**
- Container recém-criado? (problemas recentes)
- Container antigo mas Status "Restarting"?

### 4. Verificar Histórico de Comandos
```bash
history | grep docker
```

**O que procurar:**
- `docker compose down -v` ← CULPADO!
- `docker volume rm` ← CULPADO!
- `docker system prune -a --volumes` ← CULPADO!

### 5. Ver Logs do PostgreSQL na Inicialização
```bash
docker compose logs postgres | head -100
```

**O que procurar:**
```
PostgreSQL Database directory appears to contain a database; Skipping initialization
```
↑ Isso significa que pulou a inicialização!

### 6. Listar Bancos de Dados no PostgreSQL
```bash
docker exec clube-quinze-postgres psql -U postgres -l
```

**O que procurar:**
- Se `clube_quinze` aparece na lista ou não

### 7. Ver Espaço em Disco
```bash
df -h
```

**O que procurar:**
- Disco cheio? Pode ter causado corrupção

### 8. Verificar Logs do Sistema
```bash
# Ver logs recentes do Docker daemon
journalctl -u docker -n 200

# Procurar por erros ou reinicializações
journalctl -u docker | grep -i error
```

---

## 💡 Diagnóstico Rápido

Execute este script de diagnóstico:

```bash
#!/bin/bash
echo "=== DIAGNÓSTICO DO PROBLEMA ==="
echo ""

echo "1. VOLUMES DOCKER:"
docker volume ls | grep postgres
echo ""

echo "2. CONTAINERS:"
docker ps -a | grep clube
echo ""

echo "3. BANCOS DE DADOS NO POSTGRESQL:"
docker exec clube-quinze-postgres psql -U postgres -l 2>/dev/null || echo "Container não está rodando ou não tem psql"
echo ""

echo "4. ESPAÇO EM DISCO:"
df -h | grep -E "Filesystem|/$"
echo ""

echo "5. ÚLTIMOS COMANDOS DOCKER:"
history | grep docker | tail -10
echo ""

echo "6. DATA DE CRIAÇÃO DO VOLUME:"
docker volume inspect clubequinzeapi_postgres_data --format '{{.CreatedAt}}' 2>/dev/null || echo "Volume não existe"
echo ""

echo "7. LOGS DE INICIALIZAÇÃO DO POSTGRES:"
docker compose logs postgres | grep -i "skip\|init\|database"
echo ""
```

Salve como `diagnose.sh` e execute:
```bash
chmod +x diagnose.sh
./diagnose.sh
```

---

## 🎯 Conclusão: O Que Provavelmente Aconteceu

Com base no erro que você está vendo, a causa mais provável é:

### **Hipótese #1: Volume foi removido (60% de chance)**
- Alguém rodou `docker compose down -v` sem querer
- Ou executou limpeza de volumes
- PostgreSQL criou volume novo mas não inicializou corretamente

### **Hipótese #2: Nome do projeto mudou (30% de chance)**
- Pasta foi renomeada
- Docker criou volume com nome diferente
- Volume antigo ainda existe mas não está sendo usado

### **Hipótese #3: Problema na inicialização (10% de chance)**
- Bug no script de init
- PostgreSQL confundiu estado do volume
- Desligamento abrupto durante criação

---

## 🔬 Entendendo o Comportamento do PostgreSQL

### Como o PostgreSQL Decide se Inicializa ou Não?

O PostgreSQL usa uma lógica simples mas que pode causar esse problema:

```bash
# Ao iniciar, o PostgreSQL verifica:
if [ -z "$(ls -A /var/lib/postgresql/data)" ]; then
    # Diretório VAZIO → Executar inicialização completa
    echo "Primeira vez! Vou inicializar tudo..."
    run_initdb()
    create_databases()
    run_init_scripts()
else
    # Diretório TEM ALGO → Pular inicialização
    echo "Já tem dados aqui, vou usar o que existe..."
    skip_initialization()
    use_existing_data()
fi
```

### O Problema: "Falso Positivo"

O PostgreSQL pode encontrar **arquivos de sistema** no volume mas **não o banco de dados**:

```
/var/lib/postgresql/data/
├── pg_hba.conf          ← Arquivo de config (existe)
├── postgresql.conf      ← Arquivo de config (existe)
├── base/                ← Diretório de bancos
│   ├── 1/               ← Banco 'template1' (existe)
│   ├── 13xxx/           ← Banco 'postgres' (existe)
│   └── clube_quinze/    ← NÃO EXISTE! ❌
└── pg_wal/              ← Write-Ahead Log (existe)
```

PostgreSQL vê que `/var/lib/postgresql/data` **não está vazio** e pensa:
- ✅ "Ah, já fui inicializado antes"
- ✅ "Vou usar os dados existentes"
- ❌ **MAS não verifica se o banco específico existe!**

### Por Que o Banco Sumiu?

**Cenário Real #1: Volume foi removido por engano**
```bash
# Você rodou:
docker compose down -v

# O que aconteceu:
1. Containers removidos ✓
2. VOLUME REMOVIDO (flag -v) ❌
3. Todos os dados perdidos ❌

# Próxima vez que subir:
docker compose up -d
1. Docker cria NOVO volume (vazio)
2. PostgreSQL inicia
3. Volume está vazio → Inicialização completa
4. MAS algo deu errado na inicialização
5. Banco 'clube_quinze' não foi criado
6. PostgreSQL continua rodando normalmente
7. Aplicação tenta conectar → ERRO!
```

**Cenário Real #2: Diretório renomeado**
```bash
# Antes:
/home/user/api → Volume: api_postgres_data (com dados)

# Você renomeou:
mv api "Clube Quinze API"

# O que aconteceu:
/home/user/Clube Quinze API → Volume: clubequinzeapi_postgres_data (NOVO!)

# Resultado:
- Volume ANTIGO (api_postgres_data) ainda existe com dados
- Volume NOVO (clubequinzeapi_postgres_data) foi criado vazio
- Docker usa o NOVO (porque está no diretório atual)
- PostgreSQL vê volume vazio e tenta inicializar
- Mas não criou o banco 'clube_quinze' corretamente
```

**Cenário Real #3: Inicialização parcial corrompida**
```bash
# Servidor desligou durante inicialização:
1. PostgreSQL estava criando banco pela primeira vez
2. Servidor perdeu energia / docker foi morto
3. Volume ficou com dados PARCIAIS:
   - ✓ Estrutura básica do PostgreSQL existe
   - ❌ Banco 'clube_quinze' não foi criado completamente
4. Próxima inicialização:
   - PostgreSQL vê dados existentes
   - Pula inicialização
   - Banco 'clube_quinze' não existe
```

## ✅ Solução (Independente da Causa)

Não importa o que aconteceu, a solução é simples:

```bash
# Criar o banco manualmente
docker exec -it clube-quinze-postgres psql -U postgres -c "CREATE DATABASE clube_quinze;"

# Reiniciar aplicação
docker compose restart api
```

**E para prevenir no futuro:**
1. ✅ Use os scripts de backup regularmente
2. ✅ Nunca use `-v` sem saber o que faz
3. ✅ Configure backups automáticos
4. ✅ Documente mudanças no servidor

## 🕵️ Descobrir EXATAMENTE o que aconteceu na sua VPS

Execute este script na VPS para investigar:

```bash
chmod +x scripts/investigate.sh
./scripts/investigate.sh > investigation_report.txt
cat investigation_report.txt
```

Este script vai te dizer:
- ✓ Quando o volume foi criado
- ✓ Se foi encontrado 'docker compose down -v' no histórico
- ✓ Se o diretório foi renomeado
- ✓ Logs de inicialização do PostgreSQL
- ✓ Diagnóstico automático da causa raiz

---

**Agora execute o fix e volte a trabalhar! 🚀**

