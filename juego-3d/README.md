# Juego 3D

Juego de mundo abierto en 3D (estilo GTA clásico) para Android, hecho con OpenGL ES 2.0 y Kotlin puro, sin librerías externas.

## Cómo se juega
- **◀ ▶** (izquierda): girar
- **▲** acelerar · **▼** frenar / reversa (derecha)
- Llega al **punto amarillo** para cobrar misiones.
- Atropellar peatones o chocar sube tu nivel de búsqueda (★): la policía te persigue.
- Si tu coche se destruye, reapareces y pagas $200 de grúa.
- Con teclado (emulador / mando): WASD o flechas.

## Estructura
- `Game.kt`: lógica (ciudad, tráfico, policía, peatones, misiones). Sin dependencias de Android.
- `Renderer.kt`: dibujo 3D con OpenGL ES 2.0 (cubos con luz y niebla).
- `MiniMap.kt`: minimapa.
- `MainActivity.kt`: ventana, HUD y controles táctiles.

Se compila con el workflow `Build Android apps` del repo (misma receta que las demás apps).
