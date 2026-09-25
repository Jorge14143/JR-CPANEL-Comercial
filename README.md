# JR Server Panel

Panel privado y separado de JR Electricidad para monitorear el servidor.

## V0.1.0
- Dashboard oscuro Opción 1.
- Métricas reales: carga, RAM, disco y uptime.
- Estado de Nginx, MySQL, Fail2ban y timer DDNS.
- Procesos PM2.
- Actualización cada 5 segundos.
- Backend **solo lectura**.
- Escucha únicamente en `127.0.0.1:3100`.

## Instalación en el servidor

```bash
cd ~
git clone git@github.com:Jorge14143/jr-server-panel.git
cd jr-server-panel
npm install
npm start
```

Para probar sin publicarlo todavía, desde la PC:

```powershell
ssh -L 3100:127.0.0.1:3100 jorge@192.168.1.15
```

Abrir `http://127.0.0.1:3100`.

> No publicar mediante Nginx hasta incorporar autenticación y 2FA.
