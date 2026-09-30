#!/bin/sh
set -eu
[ "$(id -u)" -eq 0 ] || { echo "Ejecutar con sudo"; exit 1; }
command -v python3 >/dev/null || { echo "Falta python3"; exit 1; }
install -m 0755 "$(dirname "$0")/jr-server-agent.py" /usr/local/sbin/jr-server-agent
install -m 0644 "$(dirname "$0")/jr-server-agent.service" /etc/systemd/system/jr-server-agent.service
if [ ! -f /etc/jr-server-agent.env ]; then
  cat >/etc/jr-server-agent.env <<'EOF'
JR_AGENT_PANEL=https://jrserver.jrelectricidad.com.ar
JR_AGENT_TOKEN=PEGAR_TOKEN_DE_VINCULACION
JR_AGENT_INTERVAL=30
EOF
  chmod 600 /etc/jr-server-agent.env
fi
systemctl daemon-reload
systemctl enable jr-server-agent
echo "Editá /etc/jr-server-agent.env con el token y luego: sudo systemctl restart jr-server-agent"
