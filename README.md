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
