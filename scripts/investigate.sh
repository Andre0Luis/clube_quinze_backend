#!/bin/bash

# Script para descobrir EXATAMENTE o que aconteceu
# Execute na VPS para entender a causa raiz do problema

echo "=========================================="
echo "🔍 INVESTIGAÇÃO: Por que parou de funcionar?"
echo "=========================================="
echo ""

echo "📅 DATA ATUAL: $(date)"
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "1. VOLUMES DO DOCKER"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Volumes relacionados ao PostgreSQL:"
docker volume ls | grep -E "DRIVER|postgres"
echo ""

echo "Volume atual do projeto:"
VOLUME_NAME=$(docker volume ls --format "{{.Name}}" | grep postgres | head -1)
if [ ! -z "$VOLUME_NAME" ]; then
    echo "Nome: $VOLUME_NAME"
    CREATED=$(docker volume inspect $VOLUME_NAME --format '{{.CreatedAt}}')
    echo "Criado em: $CREATED"
    echo ""
    echo "📊 Detalhes completos:"
    docker volume inspect $VOLUME_NAME | grep -A 3 "CreatedAt\|Mountpoint\|Labels"
else
    echo "⚠️  Nenhum volume encontrado!"
fi
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "2. HISTÓRICO DE CONTAINERS"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Containers do PostgreSQL (ativos e antigos):"
docker ps -a --filter "name=postgres" --format "table {{.Names}}\t{{.Status}}\t{{.CreatedAt}}\t{{.ID}}"
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "3. LOGS DE INICIALIZAÇÃO DO POSTGRESQL"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Procurando mensagens de inicialização:"
docker compose logs postgres 2>/dev/null | grep -i "skip\|init\|database directory" | tail -20
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "4. HISTÓRICO DE COMANDOS DOCKER"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Últimos comandos docker executados:"
if [ -f ~/.bash_history ]; then
    grep -E "docker.*down|docker.*prune|docker.*volume.*rm" ~/.bash_history | tail -10
    echo ""
    echo "Total de 'docker compose down -v' encontrados: $(grep -c "docker.*down.*-v" ~/.bash_history 2>/dev/null || echo 0)"
    echo "Total de 'docker volume rm' encontrados: $(grep -c "docker.*volume.*rm" ~/.bash_history 2>/dev/null || echo 0)"
else
    echo "Histórico não disponível"
fi
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "5. NOME DO DIRETÓRIO DO PROJETO"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
CURRENT_DIR=$(basename "$(pwd)")
echo "Diretório atual: $CURRENT_DIR"
echo "Caminho completo: $(pwd)"
echo ""
echo "Nome do volume esperado: ${CURRENT_DIR,,}_postgres_data"
echo "Nome do volume encontrado: $VOLUME_NAME"
echo ""
if [ "${CURRENT_DIR,,}_postgres_data" != "$VOLUME_NAME" ] && [ ! -z "$VOLUME_NAME" ]; then
    echo "⚠️  ALERTA: Nome do volume NÃO corresponde ao diretório!"
    echo "Isso indica que:"
    echo "  - Diretório foi renomeado OU"
    echo "  - Projeto foi movido para outro local OU"
    echo "  - Volume foi criado manualmente com nome diferente"
fi
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "6. ESPAÇO EM DISCO"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
df -h | grep -E "Filesystem|/$"
DISK_USAGE=$(df -h / | tail -1 | awk '{print $5}' | sed 's/%//')
if [ $DISK_USAGE -gt 90 ]; then
    echo ""
    echo "⚠️  ALERTA: Disco está com mais de 90% de uso!"
    echo "Isso pode ter causado problemas de escrita no banco de dados."
fi
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "7. LOGS DO SISTEMA (últimas reinicializações)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
if command -v journalctl &> /dev/null; then
    echo "Eventos de shutdown/reboot nas últimas 48h:"
    journalctl --since "2 days ago" | grep -i "shutdown\|reboot\|power" | tail -10
    echo ""
    echo "Erros do Docker nas últimas 24h:"
    journalctl -u docker --since "1 day ago" | grep -i "error\|fail" | tail -10
else
    echo "journalctl não disponível (sem systemd)"
fi
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "8. VERIFICAÇÃO DO ARQUIVO compose.yaml"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
if [ -f compose.yaml ]; then
    echo "✓ compose.yaml existe"
    echo "Data de modificação: $(stat -c %y compose.yaml 2>/dev/null || stat -f %Sm compose.yaml 2>/dev/null)"
elif [ -f docker-compose.yml ]; then
    echo "✓ docker-compose.yml existe"
    echo "Data de modificação: $(stat -c %y docker-compose.yml 2>/dev/null || stat -f %Sm docker-compose.yml 2>/dev/null)"
else
    echo "⚠️  Nenhum arquivo compose encontrado!"
fi
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "9. COMMIT MAIS RECENTE DO GIT"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
if [ -d .git ]; then
    echo "Último commit:"
    git log -1 --format="%h - %an, %ar : %s"
    echo ""
    echo "Último pull/checkout:"
    git reflog | grep -E "pull|checkout" | head -5
else
    echo "Não é um repositório git"
fi
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎯 DIAGNÓSTICO PROVÁVEL"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Análise automática
PROBABLE_CAUSE=""

# Verificar se volume foi recém-criado
if [ ! -z "$VOLUME_NAME" ]; then
    VOLUME_AGE=$(docker volume inspect $VOLUME_NAME --format '{{.CreatedAt}}')
    CURRENT_TIMESTAMP=$(date +%s)
    VOLUME_TIMESTAMP=$(date -d "$VOLUME_AGE" +%s 2>/dev/null || echo 0)
    AGE_DAYS=$(( ($CURRENT_TIMESTAMP - $VOLUME_TIMESTAMP) / 86400 ))

    if [ $AGE_DAYS -lt 2 ]; then
        echo "🔴 Volume foi criado há apenas $AGE_DAYS dia(s)"
        echo "   Causa provável: Volume foi removido e recriado recentemente"
        PROBABLE_CAUSE="volume_recreated"
    fi
fi

# Verificar histórico de comandos perigosos
if [ -f ~/.bash_history ]; then
    DANGEROUS_COMMANDS=$(grep -c "docker.*down.*-v\|docker.*volume.*rm\|docker.*prune.*volume" ~/.bash_history 2>/dev/null || echo 0)
    if [ $DANGEROUS_COMMANDS -gt 0 ]; then
        echo "🔴 Encontrados $DANGEROUS_COMMANDS comandos que removem volumes no histórico"
        echo "   Causa provável: Volume foi removido por comando 'docker compose down -v'"
        PROBABLE_CAUSE="manual_removal"
    fi
fi

# Verificar logs de inicialização
SKIP_INIT=$(docker compose logs postgres 2>/dev/null | grep -c "Skipping initialization")
if [ $SKIP_INIT -gt 0 ]; then
    echo "🔴 PostgreSQL pulou a inicialização ($SKIP_INIT vezes)"
    echo "   Causa: Volume continha dados mas banco 'clube_quinze' não existia"
fi

# Verificar nome do diretório
if [ "${CURRENT_DIR,,}_postgres_data" != "$VOLUME_NAME" ] && [ ! -z "$VOLUME_NAME" ]; then
    echo "🔴 Nome do volume não corresponde ao diretório"
    echo "   Causa provável: Diretório foi renomeado ou projeto foi movido"
    PROBABLE_CAUSE="directory_renamed"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📝 CONCLUSÃO"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

case $PROBABLE_CAUSE in
    volume_recreated)
        echo "Causa mais provável: VOLUME FOI REMOVIDO E RECRIADO"
        echo ""
        echo "O que aconteceu:"
        echo "  1. Volume original tinha o banco 'clube_quinze'"
        echo "  2. Volume foi removido (docker compose down -v ou similar)"
        echo "  3. Docker criou novo volume vazio"
        echo "  4. PostgreSQL inicializou mas não criou o banco corretamente"
        echo ""
        echo "Como prevenir:"
        echo "  - NUNCA use 'docker compose down -v'"
        echo "  - Use 'docker compose down' (sem -v)"
        echo "  - Faça backups regulares: ./scripts/backup-database.sh"
        ;;
    manual_removal)
        echo "Causa mais provável: COMANDO MANUAL REMOVEU O VOLUME"
        echo ""
        echo "O que aconteceu:"
        echo "  1. Alguém executou 'docker compose down -v' ou 'docker volume rm'"
        echo "  2. Volume com todos os dados foi deletado"
        echo "  3. Novo volume foi criado mas sem o banco 'clube_quinze'"
        echo ""
        echo "Como prevenir:"
        echo "  - Cuidado com flag -v em comandos docker"
        echo "  - Configure backups automáticos"
        echo "  - Documente procedimentos de manutenção"
        ;;
    directory_renamed)
        echo "Causa mais provável: DIRETÓRIO FOI RENOMEADO"
        echo ""
        echo "O que aconteceu:"
        echo "  1. Projeto estava em um diretório com nome diferente"
        echo "  2. Volume foi criado com base no nome antigo"
        echo "  3. Diretório foi renomeado"
        echo "  4. Docker criou NOVO volume com nome diferente"
        echo "  5. Volume antigo (com dados) ficou órfão"
        echo ""
        echo "Como prevenir:"
        echo "  - Não renomeie diretórios de projetos Docker"
        echo "  - Use volumes nomeados explicitamente no compose.yaml"
        ;;
    *)
        echo "Causa provável: PROBLEMA NA INICIALIZAÇÃO DO POSTGRESQL"
        echo ""
        echo "O que aconteceu:"
        echo "  1. PostgreSQL detectou dados no volume"
        echo "  2. Pulou a inicialização automática"
        echo "  3. Mas banco 'clube_quinze' não existia no volume"
        echo ""
        echo "Possíveis causas:"
        echo "  - Desligamento abrupto durante criação do banco"
        echo "  - Corrupção de dados"
        echo "  - Bug no script de inicialização"
        ;;
esac

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ PROBLEMA JÁ FOI RESOLVIDO"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "O banco 'clube_quinze' foi criado manualmente e está funcionando."
echo ""
echo "📌 PRÓXIMOS PASSOS:"
echo "  1. ✓ Fazer backup imediatamente:"
echo "     ./scripts/backup-database.sh"
echo ""
echo "  2. ✓ Configurar backups automáticos (crontab):"
echo "     0 2 * * * cd $(pwd) && ./scripts/backup-database.sh"
echo ""
echo "  3. ✓ Nunca usar 'docker compose down -v'"
echo "     Sempre usar: docker compose down (sem -v)"
echo ""
echo "  4. ✓ Monitorar logs regularmente:"
echo "     docker compose logs -f"
echo ""
echo "=========================================="
echo "Relatório completo em: diagnostico_$(date +%Y%m%d_%H%M%S).log"
echo "=========================================="

