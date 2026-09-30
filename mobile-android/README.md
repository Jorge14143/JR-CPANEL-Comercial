# JR Server Mobile

Aplicación Android nativa oficial de JR Server Panel.

## V1 implementada
- URL del JR Server Panel configurable y obligatoriamente HTTPS.
- Vinculación segura con usuario, contraseña y TOTP.
- Token independiente y revocable por dispositivo; la contraseña no se guarda.
- Pantalla de estado global OK / advertencia / crítico.
- CPU, RAM, disco y temperatura del servidor principal.
- Multi Servidor con estado online/offline y latencia.
- Alertas e historial de incidentes y recuperaciones.
- Notificaciones por incidente, sin repetir continuamente el mismo fallo.
- Aviso de recuperación cuando desaparece un incidente.
- Monitor periódico Android con WorkManager (15 minutos; Android puede diferir su ejecución).
- Actualización manual.
- Ajustes, panel vinculado y desvinculación.
- Al desvincular se cancela el monitor local.
- Icono y branding JR Server Mobile.

## API
- POST `/api/mobile/v1/pair`
- GET `/api/mobile/v1/status`
- GET `/api/mobile/v1/incidents`
- GET `/api/mobile/v1/devices` (panel)
- DELETE `/api/mobile/v1/devices/:id` (panel)

## Compilar
Abrir `mobile-android/` con Android Studio usando JDK 17, dejar que sincronice Gradle y ejecutar la variante `debug`.

Si se agrega Gradle Wrapper al proyecto:
```bash
./gradlew assembleDebug
```

APK debug esperada:
`app/build/outputs/apk/debug/app-debug.apk`

## Antes del release comercial
- Compilar y corregir cualquier error detectado por Android/Gradle.
- Probar vinculación, monitor y notificaciones en un teléfono Android físico.
- Implementar código/QR de vinculación de un solo uso para no escribir credenciales administrativas en la app.
- Implementar push inmediato mediante FCM. WorkManager queda como respaldo y no garantiza entrega instantánea.
- Migrar el token local a almacenamiento respaldado por Android Keystore.
- Crear firma release y definir política de versionado/actualizaciones.
- Preparar iconos adaptativos/splash y branding final de distribución.

## Estado
El código fuente de V1 está preparado para la primera compilación, pero todavía no debe considerarse una APK validada hasta completar una compilación real y una prueba en dispositivo físico.
