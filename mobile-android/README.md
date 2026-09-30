# JR Server Mobile

Aplicación Android nativa oficial de JR Server Panel.

## V1
- Vinculación segura con usuario, contraseña y TOTP.
- Token independiente por dispositivo; la contraseña no se guarda.
- Estado global OK / advertencia / crítico.
- CPU, RAM, disco y temperatura del servidor principal.
- Multi Servidor con estado online/offline y latencia.
- Alertas e historial de incidentes y recuperaciones.
- Monitor periódico Android con WorkManager.
- Notificaciones locales cuando cambia el estado.

## API
- POST `/api/mobile/v1/pair`
- GET `/api/mobile/v1/status`
- GET `/api/mobile/v1/incidents`
- GET `/api/mobile/v1/devices` (panel)
- DELETE `/api/mobile/v1/devices/:id` (panel)

## Compilar
Abrir `mobile-android/` con Android Studio (JDK 17) y sincronizar Gradle.

Por terminal, una vez disponible Gradle Wrapper:
```bash
./gradlew assembleDebug
```

APK debug esperada:
`app/build/outputs/apk/debug/app-debug.apk`

## Pendiente antes de release comercial
- URL del panel configurable (actualmente apunta al servidor JR de desarrollo).
- Push inmediato mediante FCM.
- Firma release y versionado.
- Icono/branding final.
- Pruebas en dispositivo físico.
