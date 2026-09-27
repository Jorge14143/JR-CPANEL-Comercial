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


## V0.4.0 — Fase 4 PM2
- Inventario PM2 autenticado con estado, CPU, RAM, PID, reinicios, uptime, versión de Node y modo.
- Visualización de logs recientes por proceso.
- Acciones iniciar, reiniciar y detener con confirmación.
- Acciones protegidas por sesión y CSRF.
- Los procesos se validan contra el inventario real de PM2; no se aceptan comandos arbitrarios.
- Las acciones PM2 quedan registradas en auditoría.
- No se implementan delete, shell ni ejecución libre de comandos.


## V0.5.0 — Fase 5 Nginx
- Estado, versión, conexiones TCP y sitios habilitados.
- Visor de access/error logs cuando el sistema permite lectura.
- Validación de configuración con nginx -t.
- Recarga segura: siempre valida antes de ejecutar systemctl reload nginx.
- Helper privilegiado de lista cerrada; no acepta argumentos ni comandos arbitrarios.
- Acciones protegidas por sesión, CSRF, confirmación y auditoría.
- No incluye stop/restart de Nginx desde la web para reducir riesgo de dejar el servidor inaccesible.


## V0.6.0 — Fase 6 MySQL
- Estado y versión del servidor MySQL, uptime, conexiones y consultas.
- Tamaño y cantidad de tablas de producción y pruebas.
- Inventario de tablas con tamaño, filas aproximadas y motor.
- Vista de conexiones/procesos actuales.
- Helper privilegiado con consultas SQL fijas y estrictamente de solo lectura.
- Sin editor SQL, DROP/DELETE/UPDATE, restauraciones ni credenciales expuestas al navegador.


## V0.7.0 — Fase 7 Backups
- Inventario de respaldos locales bajo ~/backups-jr, con fecha, tipo y tamaño.
- Resumen del último backup, cantidad de archivos y espacio utilizado.
- Verificación del script fijo ~/backup-jr.sh.
- Creación manual de backup con confirmación, CSRF y auditoría.
- Helper privilegiado de lista cerrada; no acepta rutas ni comandos enviados por el navegador.
- Sin restauración, borrado ni sobrescritura de respaldos desde la web.


## V2.0.0 — Release estable
- Dashboard y monitoreo integral del servidor.
- Control seguro de PM2, Nginx, MySQL, backups, Cloudflare/DDNS, SSL y actualizaciones APT.
- Git/Deploy para panel, producción y pruebas.
- Terminal web autenticada y Explorador de Archivos V2.
- Editor de código con guardado atómico y auditoría.
- Logs centralizados y Alertas V2 para recursos, servicios, backups, SSL y actualizaciones de seguridad.
- Almacenamiento V2 con proyectos administrados bajo /srv/proyectos.
- Sistema V2 con systemd, procesos, red, MySQL, tareas, timeline, diagnóstico, acciones rápidas, ajustes y preparación multi-servidor.
- Login protegido, sesiones, Google Authenticator 2FA, códigos de recuperación, CSRF y auditoría de acciones sensibles.

**Estado:** V2 cerrada y estable.
