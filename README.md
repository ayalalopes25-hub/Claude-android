# Claude-android

Repo único para varias apps de Android. Cada app vive en su propia carpeta
y se compila con GitHub Actions.

## Apps

| App | Descarga directa del APK |
| --- | --- |
| hola-mundo | https://github.com/ayalalopes25-hub/claude-android/releases/download/hola-mundo-latest/app-debug.apk |
| bitacora-demo | https://github.com/ayalalopes25-hub/claude-android/releases/download/bitacora-demo-latest/app-debug.apk |

## Cómo funciona

- Al hacer push a `main`, el workflow compila solo las carpetas que cambiaron.
- Cada app se publica en Releases con el tag `<carpeta>-latest`.
- Todas usan `debug.keystore` (en la raíz) para que las versiones nuevas se instalen encima de las anteriores.
- También se puede lanzar a mano desde la pestaña Actions.

## Agregar una app nueva

Crear una carpeta con su propio proyecto Gradle (`settings.gradle.kts` + módulo `app`),
usando `hola-mundo` como modelo.
