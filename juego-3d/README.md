# Juego 3D

Juego de mundo abierto en 3D (ciudad al atardecer con luz dorada, sombras, ventanas iluminadas, farolas y neones) para Android, hecho con OpenGL ES 2.0 y Kotlin puro, sin librerías externas.

## Cómo se juega
Los protagonistas son la pareja: tú y **Lucía**. Al abrir el juego ves la pantalla de inicio; pulsa **JUGAR**.

| Control | Qué hace |
| --- | --- |
| **Bolita** (abajo izquierda) | A pie: caminas hacia donde empujes, respecto a la cámara. En coche: arriba/abajo acelera y frena, izquierda/derecha gira |
| **Arrastrar la pantalla** | Mirar a todos lados (cámara libre, arriba/abajo y alrededor) |
| SUBIR / BAJAR | bajarte del coche, o subirte a uno cercano |
| FUEGO | disparar (a pie) |
| ARMA | cambiar de arma |

- **Coches:** sedán, deportivo (rápido) y camioneta (lenta). Puedes robar cualquier coche civil o aparcado.
- **Armas:** pistola, metralleta y escopeta, con munición. Recoge **cajas de colores** por las calles (azul = pistola, naranja = metralleta, roja = escopeta); las patrullas destruidas también dejan una. Si hay una patrulla cerca, el disparo apunta solo hacia ella.
- **Policía:** atropellar peatones, robar coches, chocar o disparar sube las estrellas ★. Las patrullas te persiguen y te disparan.
- **Lucía:** tu novia. Va siempre contigo: te sigue a pie, se sube a tu coche, dispara a la policía por ti y te cuida (junto a ella te recuperas más rápido). Lleva chaqueta de cuero, vaqueros ajustados y melena larga, y aparece en rosa en el minimapa.
- **Misiones:** llega al punto amarillo para cobrar.
- Si mueres reapareces con un coche nuevo y pagas $200.
- Con teclado (emulador / mando): WASD o flechas, Espacio/F fuego, E subir/bajar, Q cambiar de arma.

## Estructura
- `Game.kt`: lógica (ciudad, tráfico, policía, armas, peatones, Lucía, misiones). Sin dependencias de Android.
- `Renderer.kt`: dibujo 3D con OpenGL ES 2.0 (cielo, luz solar, brillos, niebla y sombras).
- `MiniMap.kt`: minimapa.
- `MainActivity.kt`: ventana, HUD, pantalla de inicio y controles táctiles.
- `Joystick.kt`: la bolita de control.

Se compila con el workflow `Build Android apps` del repo (misma receta que las demás apps).
