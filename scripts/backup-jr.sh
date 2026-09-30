#!/bin/bash
set -euo pipefail

# ============================================
# JR ELECTRICIDAD V3 - BACKUP AUTOMATICO
# ============================================

FECHA=$(date +"%Y-%m-%d_%H-%M-%S")
BASE="/home/jorge/backups-jr/automaticos/$FECHA"
PROYECTO="/srv/proyectos/jrelectricidad1"
PANEL="/srv/proyectos/jr-server-panel"
REMOTE="backblaze-crypt:JR-ELECTRICIDAD/AUTOMATICOS/$FECHA"
RCLONE_CONFIG="/home/jorge/.config/rclone/rclone.conf"

echo "============================================"
echo "JR Electricidad - Backup"
echo "Fecha: $(date)"
echo "============================================"

mkdir -p "$BASE/database"
mkdir -p "$BASE/proyecto"
mkdir -p "$BASE/jr-server-panel"
mkdir -p "$BASE/servidor/info"

echo "[1/5] Backup MySQL..."

mysqldump \
  --single-transaction \
  --routines \
  --triggers \
  --no-tablespaces \
  jr_electricidad > "$BASE/database/jr_electricidad.sql"

grep -q "Dump completed" "$BASE/database/jr_electricidad.sql"

echo "[2/5] Backup JR Electricidad..."

rsync -a \
  --exclude='node_modules' \
  --exclude='.git' \
  "$PROYECTO/" \
  "$BASE/proyecto/jrelectricidad1/"

echo "[3/5] Backup JR Server Panel..."

rsync -a \
  --exclude='node_modules' \
  --exclude='.git' \
  "$PANEL/" \
  "$BASE/jr-server-panel/"

echo "[4/5] Backup configuracion servidor..."

cp -a /etc/nginx "$BASE/servidor/nginx"

pm2 jlist > "$BASE/servidor/info/pm2-processes.json"
node --version > "$BASE/servidor/info/node-version.txt"
npm --version > "$BASE/servidor/info/npm-version.txt"
pm2 --version > "$BASE/servidor/info/pm2-version.txt"
uname -a > "$BASE/servidor/info/system.txt"

echo "[5/5] Subiendo cifrado a Backblaze..."

rclone --config "$RCLONE_CONFIG" copy "$BASE/" "$REMOTE/" \
 --copy-links \
  --transfers 4 \
  --checkers 8

echo ""
echo "Verificando backup remoto..."

rclone --config "$RCLONE_CONFIG" size "$REMOTE/"

echo ""
echo "Limpiando backups locales con mas de 30 dias..."

find /home/jorge/backups-jr/automaticos \
  -mindepth 1 \
  -maxdepth 1 \
  -type d \
  -mtime +30 \
  -exec rm -rf {} +

echo ""

echo "Limpiando backups remotos completos con mas de 30 dias..."

LIMITE=$(date -d "30 days ago" +"%Y-%m-%d_%H-%M-%S")

rclone --config "$RCLONE_CONFIG" lsf backblaze-crypt:JR-ELECTRICIDAD/AUTOMATICOS/ --dirs-only |
while read -r carpeta; do

    nombre="${carpeta%/}"

    if [[ "$nombre" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}_[0-9]{2}-[0-9]{2}-[0-9]{2}$ ]] &&
       [[ "$nombre" < "$LIMITE" ]]; then

        echo "Eliminando backup remoto antiguo: $nombre"

        rclone --config "$RCLONE_CONFIG" purge \
          "backblaze-crypt:JR-ELECTRICIDAD/AUTOMATICOS/$nombre"
    fi
done

touch "$BASE/COMPLETE"

echo "BACKUP COMPLETADO CORRECTAMENTE"
echo "Destino: $REMOTE"
echo "Fecha: $(date)"
