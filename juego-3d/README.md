# Juego 3D

Juego de mundo abierto en 3D (estilo GTA clásico) para Android, hecho con OpenGL ES 2.0 y Kotlin puro, sin librerías externas.

## Cómo se juega
| Botón | Conduciendo | A pie |
| --- | --- | --- |
| ◀ ▶ | girar | girar |
| ▲ | acelerar | caminar |
| ▼ | frenar / reversa | retroceder |
| SUBIR / BAJAR | bajarte del coche, o subirte a uno cercano | |
| FUEGO | (bájate para disparar) | disparar (mantén para ráfagas) |
| ARMA | | cambiar de arma |

- **Coches:** sedán, deportivo (rápido) y camioneta (lenta). Puedes robar cualquier coche civil o aparcado.
- **Armas:** pistola, metralleta y escopeta, con munición. Recoge **cajas de colores** por las calles (azul = pistola, naranja = metralleta, roja = escopeta); las patrullas destruidas también dejan una. Si hay una patrulla cerca, el disparo apunta solo hacia ella.
- **Policía:** atropellar peatones, robar coches, chocar o disparar sube las estrellas ★. Las patrullas te persiguen y te disparan.
- **Lucía:** tu compañera. Te sigue a pie, se sube a tu coche y dispara a la policía por ti. Aparece en rosa en el minimapa.
- **Misiones:** llega al punto amarillo para cobrar.
- Si mueres reapareces con un coche nuevo y pagas $200.
- Con teclado (emulador / mando): WASD o flechas, Espacio/F fuego, E subir/bajar, Q cambiar de arma.

## Estructura
- `Game.kt`: lógica (ciudad, tráfico, policía, armas, peatones, Lucía, misiones). Sin dependencias de Android.
- `Renderer.kt`: dibujo 3D con OpenGL ES 2.0 (cubos con luz y niebla).
- `MiniMap.kt`: minimapa.
- `MainActivity.kt`: ventana, HUD y controles táctiles.

Se compila con el workflow `Build Android apps` del repo (misma receta que las demás apps).
