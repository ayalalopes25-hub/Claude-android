package com.ayalalopes.juego3d

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin

// ===== Mundo: cuadricula de calles. Las calles pasan por x = i*CELL y z = j*CELL =====
const val CELL = 60f          // separacion entre calles (m)
const val ROAD = 16f          // ancho de calle (m)
const val NB = 8              // bloques por lado
const val WORLD = 480f        // tamano del mundo (NB * CELL)
const val PIF = 3.1415927f
const val TWO_PI = 6.2831855f

val DXI = intArrayOf(1, 0, -1, 0)      // direcciones: 0 +x, 1 +z, 2 -x, 3 -z
val DZI = intArrayOf(0, 1, 0, -1)
val LANE = floatArrayOf(3f, -3f, -3f, 3f)   // carril (conduccion por la derecha)

// Tipos de coche: 0 sedan, 1 deportivo, 2 camioneta
val MAXV = floatArrayOf(30f, 38f, 24f)
val ACC = floatArrayOf(15f, 22f, 11f)
val TURN = floatArrayOf(2.2f, 2.7f, 1.8f)
val SPEEDMUL = floatArrayOf(1f, 1.15f, 0.9f)

val SPORT = arrayOf(
    floatArrayOf(0.70f, 0.72f, 0.76f),
    floatArrayOf(0.10f, 0.32f, 0.58f),
    floatArrayOf(0.80f, 0.06f, 0.07f)
)
val PALETTE = arrayOf(
    floatArrayOf(0.75f, 0.22f, 0.18f), floatArrayOf(0.18f, 0.40f, 0.80f),
    floatArrayOf(0.18f, 0.62f, 0.35f), floatArrayOf(0.84f, 0.63f, 0.16f),
    floatArrayOf(0.56f, 0.27f, 0.68f), floatArrayOf(0.86f, 0.86f, 0.86f),
    floatArrayOf(0.29f, 0.29f, 0.31f), floatArrayOf(0.90f, 0.50f, 0.13f)
)

// ===== Armas =====
class Weapon(
    val name: String, val dmg: Float, val rate: Float, val range: Float,
    val pellets: Int, val spread: Float, val auto: Boolean
)

val WEAPONS = arrayOf(
    Weapon("Pistola", 22f, 0.38f, 60f, 1, 0.015f, false),
    Weapon("Metralleta", 11f, 0.09f, 50f, 1, 0.06f, true),
    Weapon("Escopeta", 13f, 0.85f, 32f, 7, 0.11f, false)
)
val AMMO_PACK = intArrayOf(30, 90, 14)

fun rnd(a: Float, b: Float): Float = a + Math.random().toFloat() * (b - a)
fun clampF(v: Float, a: Float, b: Float): Float = if (v < a) a else if (v > b) b else v
fun dst(ax: Float, az: Float, bx: Float, bz: Float): Float = hypot(bx - ax, bz - az)

fun angDiff(a: Float, b: Float): Float {
    var d = (b - a) % TWO_PI
    if (d > PIF) d -= TWO_PI
    if (d < -PIF) d += TWO_PI
    return d
}

fun hash(i: Int, j: Int): Float {
    var h = i * 374761393 + j * 668265263
    h = h xor (h ushr 13)
    h *= 1274126177
    h = h xor (h ushr 16)
    return (h ushr 1).toFloat() / 2147483648f
}

fun isPark(i: Int, j: Int): Boolean = hash(i, j) < 0.12f

/** Verdadero si (x,z) esta dentro de un edificio o fuera del mundo. */
fun solid(x: Float, z: Float): Boolean {
    val lo = -ROAD / 2f
    val hi = WORLD + ROAD / 2f
    if (x < lo || z < lo || x > hi || z > hi) return true
    val i = floor(x / CELL).toInt()
    val j = floor(z / CELL).toInt()
    if (i < 0 || j < 0 || i >= NB || j >= NB) return false
    val u = x - i * CELL
    val v = z - j * CELL
    val m = ROAD / 2f
    if (u > m && u < CELL - m && v > m && v < CELL - m) return !isPark(i, j)
    return false
}

fun pickType(): Int {
    val r = Math.random()
    return if (r < 0.6) 0 else if (r < 0.8) 1 else 2
}

/** kind: 0 civil (IA), 1 policia, 2 conducido por el jugador, 3 aparcado. */
class Car(var x: Float, var z: Float, var a: Float, var kind: Int) {
    var type = pickType()
    var speed = 0f
    var dir = 0
    var maxSp = rnd(9f, 14f) * SPEEDMUL[type]
    var hp = 100f
    var stun = 0f
    var lastKey = -1
    var stuck = 0f
    var rev = 0f
    var direct = false
    var hitcd = 0f
    var fireCd = rnd(0.5f, 1.5f)
    var dead = false
    var col: FloatArray = PALETTE[(Math.random() * PALETTE.size).toInt() % PALETTE.size]

    init {
        // los deportivos salen en plata, azul metalico o rojo
        if (type == 1) col = SPORT[(Math.random() * SPORT.size).toInt() % SPORT.size]
    }
}

class Ped(var x: Float, var z: Float) {
    var a = rnd(0f, TWO_PI)
    var sp = rnd(1.5f, 2.6f)
    var t = rnd(1f, 4f)
    var flee = 0f
    var walkT = rnd(0f, 6f)
    val col: FloatArray = floatArrayOf(rnd(0.2f, 0.9f), rnd(0.2f, 0.9f), rnd(0.2f, 0.9f))
}

/** Lucia, la companera: te sigue a pie, se sube a tu coche y dispara a la policia. */
class Girl(var x: Float, var z: Float) {
    var a = 0f
    var inCar = false
    var fireCd = 0.5f
    var stuck = 0f
    var talkCd = 8f
    var walkT = 0f
    var moving = false
}

class Particle(
    var x: Float, var y: Float, var z: Float,
    var vx: Float, var vy: Float, var vz: Float,
    var life: Float, val col: FloatArray, val size: Float
)

class Pickup(val x: Float, val z: Float, val v: Int)
class Crate(var x: Float, var z: Float, val w: Int)
class Tracer(val x1: Float, val z1: Float, val x2: Float, val z2: Float, var life: Float)

class Game {
    // ----- entrada (la escribe el hilo de la interfaz) -----
    @Volatile var inLeft = false
    @Volatile var inRight = false
    @Volatile var inGas = false
    @Volatile var inBrake = false
    @Volatile var inFire = false
    @Volatile var actEnter = false
    @Volatile var actWeapon = false
    /** Joystick (bolita): x derecha+, y adelante+, en -1..1. */
    @Volatile var joyX = 0f
    @Volatile var joyY = 0f
    /** Arrastre de camara (pixeles acumulados) y si el dedo sigue puesto. */
    @Volatile var lookDX = 0f
    @Volatile var lookDY = 0f
    @Volatile var lookActive = false

    val cars = ArrayList<Car>()
    val peds = ArrayList<Ped>()
    val parts = ArrayList<Particle>()
    val pickups = ArrayList<Pickup>()
    val crates = ArrayList<Crate>()
    val tracers = ArrayList<Tracer>()

    /** El "peon" del jugador: posicion, rumbo y velocidad (a pie o dentro del coche). No esta en [cars]. */
    val player = Car(2f * CELL, 2f * CELL + LANE[0], 0f, 2)
    /** Coche que conduce el jugador (null si va a pie). */
    var pcar: Car? = null
    val lucia = Girl(2f * CELL, 2f * CELL + LANE[0])

    var hp = 100f
    var curW = 0
    val ammo = intArrayOf(60, 0, 0)
    var fireCd = 0f
    var prevFire = false
    var flash = 0f
    var intro = true
    var introT = 0f
    var pWalk = 0f
    var camYaw = 0f
    var camPitch = 0.25f
    var lookOff = 0f

    var money = 0
    var heat = 0f
    var time = 0f
    var wastedT = 0f
    var copCd = 0f
    var msg = ""
    var msgT = 0f
    var missionX = 0f
    var missionZ = 0f
    var missionLen = 0f
    var camA = 0f
    @Volatile var mapCops = FloatArray(0)
    @Volatile var mapCrates = FloatArray(0)

    private val SPARK = floatArrayOf(1f, 0.85f, 0.3f)
    private val FIRE = floatArrayOf(1f, 0.5f, 0.1f)
    private val SMOKE = floatArrayOf(0.3f, 0.3f, 0.3f)
    private val PUFF = floatArrayOf(0.8f, 0.8f, 0.8f)

    init {
        val c0 = Car(2f * CELL, 2f * CELL + LANE[0], 0f, 2)
        c0.type = 1
        c0.col = floatArrayOf(0.85f, 0.12f, 0.12f)
        cars.add(c0)
        pcar = c0
        lucia.inCar = true
        for (k in 0 until 28) {
            val c = Car(0f, 0f, 0f, 0)
            placeCar(c, WORLD / 2f, WORLD / 2f, 0f, WORLD * 0.7f)
            if (dst(c.x, c.z, player.x, player.z) < 60f) placeCar(c, WORLD / 2f, WORLD / 2f, 150f, WORLD * 0.7f)
            cars.add(c)
        }
        for (k in 0 until 40) {
            val q = Ped(0f, 0f)
            placePed(q, WORLD / 2f, WORLD / 2f, 0f, WORLD * 0.7f)
            peds.add(q)
        }
        for (k in 0 until 8) crates.add(Crate(0f, 0f, k % 3).also { placeCrate(it) })
        newMission()
        showMsg("Lucía, tu novia, va contigo ♥ Conduce al punto amarillo o bájate y dispara")
    }

    fun showMsg(s: String) { msg = s; msgT = 3.5f }

    /** Termina la intro y empieza la partida. */
    fun start() {
        intro = false
        lookDX = 0f
        lookDY = 0f
        showMsg("Lucía va contigo ♥ Conduce al punto amarillo o bájate y dispara")
    }

    private fun jx(): Float = clampF(joyX + (if (inRight) 1f else 0f) - (if (inLeft) 1f else 0f), -1f, 1f)
    private fun jy(): Float = clampF(joyY + (if (inGas) 1f else 0f) - (if (inBrake) 1f else 0f), -1f, 1f)

    // ---------- creacion ----------
    private fun placeCar(c: Car, cx: Float, cz: Float, rmin: Float, rmax: Float) {
        val ang = rnd(0f, TWO_PI)
        val d = rnd(rmin, rmax)
        val x = clampF(cx + cos(ang) * d, 1f, WORLD - 1f)
        val z = clampF(cz + sin(ang) * d, 1f, WORLD - 1f)
        if (Math.random() < 0.5) {
            val j = clampF(round(z / CELL), 0f, NB.toFloat()).toInt()
            c.dir = if (Math.random() < 0.5) 0 else 2
            c.x = x
            c.z = j * CELL + LANE[c.dir]
        } else {
            val i = clampF(round(x / CELL), 0f, NB.toFloat()).toInt()
            c.dir = if (Math.random() < 0.5) 1 else 3
            c.x = i * CELL + LANE[c.dir]
            c.z = z
        }
        c.a = c.dir * PIF / 2f
        c.speed = 0f
        c.lastKey = -1
        c.direct = false
        c.stun = 0f
        c.stuck = 0f
        c.rev = 0f
    }

    private fun placePed(q: Ped, cx: Float, cz: Float, rmin: Float, rmax: Float) {
        for (k in 0 until 20) {
            val ang = rnd(0f, TWO_PI)
            val d = rnd(rmin, rmax)
            val x = clampF(cx + cos(ang) * d, 1f, WORLD - 1f)
            val z = clampF(cz + sin(ang) * d, 1f, WORLD - 1f)
            if (!solid(x, z)) { q.x = x; q.z = z; return }
        }
    }

    /** Pone una caja de armas junto al borde de una calle. */
    private fun placeCrate(cr: Crate) {
        val line = (Math.random() * (NB + 1)).toInt() * CELL
        val along = rnd(10f, WORLD - 10f)
        val off = if (Math.random() < 0.5) 6.2f else -6.2f
        if (Math.random() < 0.5) { cr.x = along; cr.z = line + off } else { cr.x = line + off; cr.z = along }
        cr.x = clampF(cr.x, 1f, WORLD - 1f)
        cr.z = clampF(cr.z, 1f, WORLD - 1f)
    }

    private fun newMission() {
        for (k in 0 until 30) {
            val mx = (Math.random() * (NB + 1)).toInt() * CELL
            val mz = (Math.random() * (NB + 1)).toInt() * CELL
            if (dst(mx, mz, player.x, player.z) > 150f || k == 29) {
                missionX = mx; missionZ = mz
                missionLen = dst(mx, mz, player.x, player.z)
                return
            }
        }
    }

    private fun spawnCop() {
        val c = Car(0f, 0f, 0f, 1)
        c.hp = 80f
        c.type = 0
        c.col = floatArrayOf(0.95f, 0.95f, 0.95f)
        placeCar(c, player.x, player.z, 120f, 200f)
        c.speed = 10f
        cars.add(c)
    }

    // ---------- particulas ----------
    private fun burst(x: Float, y: Float, z: Float, n: Int, col: FloatArray, spd: Float, life: Float) {
        for (k in 0 until n) {
            if (parts.size > 160) return
            parts.add(Particle(x, y, z, rnd(-spd, spd), rnd(0f, spd), rnd(-spd, spd), rnd(life * 0.5f, life), col, rnd(0.2f, 0.5f)))
        }
    }

    // ---------- fisica de coches ----------
    private fun carBlocked(x: Float, z: Float, a: Float): Boolean {
        val ca = cos(a)
        val sa = sin(a)
        val hl = 2.1f
        val hw = 1.0f
        val lx = floatArrayOf(hl, hl, -hl, -hl, hl, -hl)
        val lz = floatArrayOf(hw, -hw, hw, -hw, 0f, 0f)
        for (k in 0 until 6) {
            if (solid(x + ca * lx[k] - sa * lz[k], z + sa * lx[k] + ca * lz[k])) return true
        }
        return false
    }

    private fun moveCar(c: Car, dt: Float) {
        val nx = c.x + cos(c.a) * c.speed * dt
        val nz = c.z + sin(c.a) * c.speed * dt
        if (carBlocked(c.x, c.z, c.a) || !carBlocked(nx, nz, c.a)) { c.x = nx; c.z = nz; return }
        val imp = abs(c.speed)
        if (!carBlocked(nx, c.z, c.a)) { c.x = nx; c.speed *= 0.9f }
        else if (!carBlocked(c.x, nz, c.a)) { c.z = nz; c.speed *= 0.9f }
        else {
            c.speed *= -0.3f
            if (imp > 8f && c.hitcd <= 0f) {
                c.hp -= (imp - 7f) * 1.6f
                c.hitcd = 0.3f
                burst(c.x, 1f, c.z, 6, SPARK, 6f, 0.5f)
            }
        }
    }

    private fun drivePlayer(c: Car, dt: Float) {
        val t = c.type
        val st = jx()
        val th = jy()
        if (th > 0.1f) c.speed += (if (c.speed < 0f) 40f else ACC[t]) * th * dt
        if (th < -0.1f) c.speed -= (if (c.speed > 0f) 30f else 9f) * (-th) * dt
        c.speed -= c.speed * 0.3f * dt
        if (abs(th) <= 0.1f) c.speed *= max(0f, 1f - 0.8f * dt)
        c.speed = clampF(c.speed, -9f, MAXV[t])
        val na = c.a + st * TURN[t] * clampF(c.speed / 7f, -1f, 1f) * dt
        if (!carBlocked(c.x, c.z, na)) c.a = na
        moveCar(c, dt)
        player.x = c.x; player.z = c.z; player.a = c.a; player.speed = c.speed
    }

    // ---------- jugador a pie ----------
    private fun footBlocked(x: Float, z: Float): Boolean {
        val r = 0.45f
        return solid(x + r, z) || solid(x - r, z) || solid(x, z + r) || solid(x, z - r)
    }

    private fun walkPlayer(dt: Float) {
        val p = player
        val ix = jx()
        val iy = jy()
        val m = min(1f, hypot(ix, iy))
        var v = 0f
        var dx = 0f
        var dz = 0f
        if (m > 0.12f) {
            val fx = cos(camYaw)
            val fz = sin(camYaw)
            dx = fx * iy - fz * ix
            dz = fz * iy + fx * ix
            val len = hypot(dx, dz)
            if (len > 0.001f) { dx /= len; dz /= len }
            v = if (m > 0.95f) 8f else 5.5f * m
        }
        // el personaje mira hacia donde camina; al disparar mira hacia donde apunta la camara
        val target = if (inFire) camYaw else if (v > 0f) atan2(dz, dx) else p.a
        p.a += clampF(angDiff(p.a, target), -12f * dt, 12f * dt)
        val nx = p.x + dx * v * dt
        val nz = p.z + dz * v * dt
        if (!footBlocked(nx, nz)) { p.x = nx; p.z = nz }
        else if (!footBlocked(nx, p.z)) p.x = nx
        else if (!footBlocked(p.x, nz)) p.z = nz
        p.speed = v
        pWalk += v * dt * 1.2f
    }

    // ---------- subir / bajar de coches ----------
    private fun toggleVehicle() {
        if (wastedT > 0f) return
        val c = pcar
        if (c != null) {
            var placed = false
            for (s in floatArrayOf(1f, -1f)) {
                val x = c.x + cos(c.a + s * PIF / 2f) * 2.6f
                val z = c.z + sin(c.a + s * PIF / 2f) * 2.6f
                if (!solid(x, z)) { player.x = x; player.z = z; placed = true; break }
            }
            if (!placed) { player.x = c.x; player.z = c.z }
            player.a = c.a
            player.speed = 0f
            camYaw = camA + lookOff
            c.kind = 3
            pcar = null
            if (lucia.inCar) { lucia.inCar = false; girlTeleport(lucia) }
            showMsg("Bajaste del coche. Dispara con FUEGO")
        } else {
            var best: Car? = null
            var bd = 4.5f
            for (o in cars) {
                if (o.kind == 1 || o.kind == 2 || o.dead) continue
                val d = dst(o.x, o.z, player.x, player.z)
                if (d < bd) { bd = d; best = o }
            }
            if (best == null) { showMsg("Acércate a un coche para subir"); return }
            if (best.kind == 0) {
                heat = min(5.99f, heat + 0.5f)
                val q = Ped(best.x + cos(best.a + PIF / 2f) * 2.5f, best.z + sin(best.a + PIF / 2f) * 2.5f)
                q.flee = 5f
                if (!solid(q.x, q.z)) { peds.add(q); if (peds.size > 60) peds.removeAt(0) }
                showMsg("Robo de vehículo")
            }
            best.kind = 2
            best.stun = 0f
            best.stuck = 0f
            pcar = best
            camA = camYaw
            lookOff = 0f
            player.x = best.x; player.z = best.z; player.a = best.a; player.speed = best.speed
            if (dst(lucia.x, lucia.z, best.x, best.z) < 14f) lucia.inCar = true
        }
    }

    private fun cycleWeapon() {
        for (k in 1..WEAPONS.size) {
            val w = (curW + k) % WEAPONS.size
            if (ammo[w] > 0) { curW = w; showMsg(WEAPONS[w].name + ": " + ammo[w]); return }
        }
        showMsg("No tienes otras armas. Busca cajas azules")
    }

    // ---------- disparos ----------
    private fun shootUpdate(dt: Float) {
        fireCd -= dt
        val firing = inFire
        val w = WEAPONS[curW]
        val c = pcar
        if (c == null && firing && fireCd <= 0f && (w.auto || !prevFire)) {
            if (ammo[curW] > 0) {
                ammo[curW]--
                fireCd = w.rate
                fireWeapon(w)
            } else {
                fireCd = 0.4f
                var other = -1
                for (k in 0 until WEAPONS.size) if (ammo[k] > 0) { other = k; break }
                if (other >= 0) { curW = other; showMsg("Sin munición. Cambias a " + WEAPONS[other].name) }
                else showMsg("¡Sin munición! Busca cajas azules")
            }
        } else if (c != null && firing && !prevFire) {
            showMsg("Bájate del coche (ENTRAR) para disparar")
        }
        prevFire = firing
    }

    private fun fireWeapon(w: Weapon) {
        val p = player
        var base = p.a
        var bestD = 0.22f
        for (c in cars) {
            if (c.kind != 1) continue
            if (dst(p.x, p.z, c.x, c.z) > w.range) continue
            val ang = atan2(c.z - p.z, c.x - p.x)
            val diff = abs(angDiff(p.a, ang))
            if (diff < bestD && clearLine(p.x, p.z, c.x, c.z)) { bestD = diff; base = ang }
        }
        for (k in 0 until w.pellets) {
            val a = base + rnd(-w.spread, w.spread)
            hitscan(p.x + cos(a) * 0.8f, p.z + sin(a) * 0.8f, cos(a), sin(a), w.range, w.dmg, true, null)
        }
        flash = 0.07f
        heat = min(5.99f, heat + 0.03f)
        for (q in peds) if (dst(q.x, q.z, p.x, p.z) < 35f) q.flee = 4f
    }

    /** Disparo instantaneo. byPlayer: lo dispara el jugador o Lucia (contra policias, coches y peatones). */
    private fun hitscan(ox: Float, oz: Float, dx: Float, dz: Float, range: Float, dmg: Float, byPlayer: Boolean, ignore: Car?) {
        var wall = range
        var s = 1f
        while (s < range) {
            if (solid(ox + dx * s, oz + dz * s)) { wall = s; break }
            s += 1f
        }
        var bestT = wall
        var hitCar: Car? = null
        var hitPed: Ped? = null
        var hitFoot = false
        val pc = pcar
        for (c in cars) {
            if (c === ignore || c.dead) continue
            if (byPlayer && c === pc) continue
            val rx = c.x - ox
            val rz = c.z - oz
            val t = rx * dx + rz * dz
            if (t <= 0f || t >= bestT) continue
            if (abs(rx * dz - rz * dx) < 2.0f) { bestT = t; hitCar = c; hitPed = null; hitFoot = false }
        }
        if (byPlayer) {
            for (q in peds) {
                val rx = q.x - ox
                val rz = q.z - oz
                val t = rx * dx + rz * dz
                if (t <= 0f || t >= bestT) continue
                if (abs(rx * dz - rz * dx) < 0.9f) { bestT = t; hitPed = q; hitCar = null; hitFoot = false }
            }
        } else if (pc == null && wastedT <= 0f) {
            val rx = player.x - ox
            val rz = player.z - oz
            val t = rx * dx + rz * dz
            if (t > 0f && t < bestT && abs(rx * dz - rz * dx) < 0.8f) { bestT = t; hitFoot = true; hitCar = null; hitPed = null }
        }
        val ex = ox + dx * bestT
        val ez = oz + dz * bestT
        tracers.add(Tracer(ox, oz, ex, ez, 0.1f))
        if (tracers.size > 40) tracers.removeAt(0)
        burst(ex, 1.2f, ez, 3, SPARK, 4f, 0.3f)
        val hc = hitCar
        val hpd = hitPed
        if (hc != null) {
            if (hc === pc) hc.hp -= 3f
            else {
                hc.hp -= dmg
                if (byPlayer) heat = min(5.99f, heat + (if (hc.kind == 1) 0.3f else 0.1f))
            }
        } else if (hpd != null) {
            killPed(hpd)
        } else if (hitFoot) {
            hp -= 7f
        }
    }

    // ---------- IA de trafico y policia ----------
    private fun validDir(ix: Int, jz: Int, d: Int): Boolean {
        val nx = ix + DXI[d]
        val nz = jz + DZI[d]
        return nx in 0..NB && nz in 0..NB
    }

    private fun civChoose(c: Car, ix: Int, jz: Int): Int {
        val r = Math.random()
        val want = if (r < 0.25) (c.dir + 1) % 4 else if (r < 0.5) (c.dir + 3) % 4 else c.dir
        if (validDir(ix, jz, want)) return want
        val opts = ArrayList<Int>()
        for (d in intArrayOf(c.dir, (c.dir + 1) % 4, (c.dir + 3) % 4)) if (validDir(ix, jz, d)) opts.add(d)
        if (opts.isEmpty()) return (c.dir + 2) % 4
        return opts[(Math.random() * opts.size).toInt() % opts.size]
    }

    private fun copChoose(c: Car, ix: Int, jz: Int): Int {
        val opts = ArrayList<Int>()
        for (d in intArrayOf(c.dir, (c.dir + 1) % 4, (c.dir + 3) % 4)) if (validDir(ix, jz, d)) opts.add(d)
        if (opts.isEmpty()) return (c.dir + 2) % 4
        if (Math.random() < 0.1) return opts[(Math.random() * opts.size).toInt() % opts.size]
        var best = opts[0]
        var bd = Float.MAX_VALUE
        for (d in opts) {
            val px = (ix + DXI[d]) * CELL
            val pz = (jz + DZI[d]) * CELL
            val dd = dst(px, pz, player.x, player.z)
            if (dd < bd) { bd = dd; best = d }
        }
        return best
    }

    private fun roadDrive(c: Car, dt: Float) {
        val cop = c.kind == 1
        if (c.stun > 0f) { c.stun -= dt; c.speed *= max(0f, 1f - 5f * dt); return }
        val dx = DXI[c.dir]
        val dz = DZI[c.dir]
        val horiz = c.dir % 2 == 0
        var target = c.maxSp
        val fx = c.x + dx * 7f
        val fz = c.z + dz * 7f
        for (o in cars) {
            if (o === c) continue
            if (cop && o.kind == 2) continue
            if (dst(o.x, o.z, fx, fz) < 3.8f) { target = 0f; break }
        }
        if (target > 0f) {
            for (q in peds) if (dst(q.x, q.z, fx, fz) < 2.6f) { target = 0f; break }
        }
        if (target > 0f && !cop && pcar == null && dst(player.x, player.z, fx, fz) < 3f) target = 0f
        c.stuck = if (c.speed < 0.6f) c.stuck + dt else 0f
        if (c.stuck > 2.5f) target = c.maxSp * 0.6f
        c.speed += (target - c.speed) * min(1f, dt * (if (target > 0f) 1.5f else 5f))
        val prev = if (horiz) c.x else c.z
        c.x += dx * c.speed * dt
        c.z += dz * c.speed * dt
        val along = if (horiz) c.x else c.z
        val lat = if (horiz) c.z else c.x
        val idx = clampF(round(along / CELL), 0f, NB.toFloat()).toInt()
        val ctr = idx * CELL
        val li = clampF(round(lat / CELL), 0f, NB.toFloat()).toInt()
        val ix = if (horiz) idx else li
        val jz = if (horiz) li else idx
        val key = ix * 100 + jz
        if (c.lastKey != key && (prev - ctr) * (along - ctr) <= 0f) {
            c.lastKey = key
            c.dir = if (cop) copChoose(c, ix, jz) else civChoose(c, ix, jz)
        }
        val nh = c.dir % 2 == 0
        val lat2 = if (nh) c.z else c.x
        val ri = clampF(round(lat2 / CELL), 0f, NB.toFloat()).toInt()
        val want = ri * CELL + LANE[c.dir]
        val nl = lat2 + (want - lat2) * min(1f, dt * 3f)
        if (nh) c.z = nl else c.x = nl
        c.a += angDiff(c.a, c.dir * PIF / 2f) * min(1f, dt * 6f)
    }

    private fun clearLine(ax: Float, az: Float, bx: Float, bz: Float): Boolean {
        val d = dst(ax, az, bx, bz)
        val n = ceil(d / 3f).toInt()
        for (k in 1 until n) {
            val t = k.toFloat() / n
            if (solid(ax + (bx - ax) * t, az + (bz - az) * t)) return false
        }
        return true
    }

    private fun copUpdate(c: Car, dt: Float) {
        val t = player
        val d = dst(c.x, c.z, t.x, t.z)
        val onFoot = pcar == null
        c.maxSp = 15f + heat * 1.6f

        // la patrulla dispara al jugador si lo ve
        if (wastedT <= 0f) {
            c.fireCd -= dt
            if (c.fireCd <= 0f && d < 45f && clearLine(c.x, c.z, t.x, t.z)) {
                c.fireCd = rnd(0.8f, 1.6f)
                val a = atan2(t.z - c.z, t.x - c.x) + rnd(-0.07f, 0.07f)
                hitscan(c.x + cos(a) * 2.5f, c.z + sin(a) * 2.5f, cos(a), sin(a), 55f, 0f, false, c)
            }
            // atropello a un jugador a pie
            if (onFoot && d < 2.8f && abs(c.speed) > 3f && player.hitcd <= 0f) {
                hp -= 22f
                player.hitcd = 0.8f
                burst(player.x, 1f, player.z, 6, PUFF, 4f, 0.5f)
                val ang = atan2(t.z - c.z, t.x - c.x)
                val nx = player.x + cos(ang) * 2f
                val nz = player.z + sin(ang) * 2f
                if (!footBlocked(nx, nz)) { player.x = nx; player.z = nz }
            }
        }

        if (c.rev > 0f) {
            c.rev -= dt
            c.speed = -7f
            val na = c.a + 1.8f * dt
            if (!carBlocked(c.x, c.z, na)) c.a = na
            moveCar(c, dt)
            return
        }
        val los = d < 80f && wastedT <= 0f && clearLine(c.x, c.z, t.x, t.z)
        if (los) c.direct = true
        if (c.direct && !los) {
            val mx = c.x - round(c.x / CELL) * CELL
            val mz = c.z - round(c.z / CELL) * CELL
            val vr = abs(mx) < ROAD / 2f - 2f
            val hr = abs(mz) < ROAD / 2f - 2f
            if (vr || hr) {
                c.direct = false
                var a = c.a % TWO_PI
                if (a < 0f) a += TWO_PI
                c.dir = if (vr && !hr) {
                    if (sin(a) >= 0f) 1 else 3
                } else if (hr && !vr) {
                    if (cos(a) >= 0f) 0 else 2
                } else {
                    (a / (PIF / 2f)).roundToInt() % 4
                }
                c.lastKey = -1
                c.stuck = 0f
            }
        }
        if (!c.direct) { roadDrive(c, dt); return }
        val ta = atan2(t.z - c.z, t.x - c.x)
        val diff = angDiff(c.a, ta)
        val na = c.a + clampF(diff, -2.4f * dt, 2.4f * dt)
        if (!carBlocked(c.x, c.z, na)) c.a = na
        val tsp = if (onFoot && d < 12f) 3f else if (abs(diff) > 1.3f) 7f else 17f + heat * 1.7f
        c.speed += (tsp - c.speed) * min(1f, dt * 1.5f)
        moveCar(c, dt)
        if (c.speed < 2f && !(onFoot && d < 12f)) {
            c.stuck += dt
            if (c.stuck > 1.2f) { c.rev = 0.9f; c.stuck = 0f }
        } else c.stuck = 0f
    }

    // ---------- peatones ----------
    private fun killPed(q: Ped) {
        burst(q.x, 1f, q.z, 8, PUFF, 4f, 0.6f)
        pickups.add(Pickup(q.x, q.z, 20 + (Math.random() * 60).toInt()))
        heat = min(5.99f, heat + 0.8f)
        placePed(q, player.x, player.z, 120f, 220f)
        q.flee = 0f
    }

    private fun pedUpdate(q: Ped, dt: Float) {
        val p = player
        q.t -= dt
        if (q.t <= 0f) { q.a += rnd(-1.2f, 1.2f); q.t = rnd(1f, 4f) }
        val d = dst(q.x, q.z, p.x, p.z)
        var sp = q.sp
        if (q.flee > 0f || (abs(p.speed) > 6f && pcar != null && d < 22f)) {
            q.a = atan2(q.z - p.z, q.x - p.x)
            sp = 5.5f
            q.flee -= dt
        }
        q.walkT += sp * dt * 1.3f
        val nx = q.x + cos(q.a) * sp * dt
        val nz = q.z + sin(q.a) * sp * dt
        if (solid(nx, nz)) q.a += PIF / 2f + rnd(0f, 1f) else { q.x = nx; q.z = nz }
        if (pcar != null && abs(p.speed) > 4f && d < 2.4f && wastedT <= 0f) killPed(q)
        else if (d > 260f) placePed(q, p.x, p.z, 120f, 220f)
    }

    // ---------- Lucia ----------
    private fun girlMove(g: Girl, tx: Float, tz: Float, sp: Float, dt: Float) {
        val a = atan2(tz - g.z, tx - g.x)
        g.a = a
        g.moving = true
        g.walkT += sp * dt * 1.2f
        val nx = g.x + cos(a) * sp * dt
        val nz = g.z + sin(a) * sp * dt
        if (!solid(nx, nz)) { g.x = nx; g.z = nz; g.stuck = 0f }
        else if (!solid(nx, g.z)) { g.x = nx; g.stuck = 0f }
        else if (!solid(g.x, nz)) { g.z = nz; g.stuck = 0f }
        else g.stuck += dt
    }

    private fun girlTeleport(g: Girl) {
        val p = player
        for (k in 0 until 20) {
            val a = rnd(0f, TWO_PI)
            val d = rnd(2.5f, 5f)
            val x = p.x + cos(a) * d
            val z = p.z + sin(a) * d
            if (!solid(x, z)) { g.x = x; g.z = z; g.stuck = 0f; return }
        }
        g.x = p.x; g.z = p.z; g.stuck = 0f
    }

    private fun luciaUpdate(dt: Float) {
        val g = lucia
        val p = player
        g.moving = false
        if (wastedT > 0f) return
        val d = dst(g.x, g.z, p.x, p.z)
        if (pcar != null) {
            if (g.inCar) {
                g.x = p.x; g.z = p.z; g.a = p.a
            } else if (d < 3.5f) {
                g.inCar = true
                showMsg("Lucía: ¡Vámonos, mi amor! ♥")
            } else if (d > 45f || g.stuck > 2f) {
                g.inCar = true
            } else {
                girlMove(g, p.x, p.z, 9f, dt)
            }
        } else {
            if (g.inCar) { g.inCar = false; girlTeleport(g) }
            if (d > 55f || g.stuck > 2f) girlTeleport(g)
            else if (d > 3.2f) girlMove(g, p.x - cos(p.a) * 1.5f, p.z - sin(p.a) * 1.5f, if (d > 12f) 9f else 6f, dt)
        }

        // Lucia dispara a las patrullas cercanas
        g.fireCd -= dt
        if (g.fireCd <= 0f && heat >= 1f) {
            val ox = if (g.inCar) p.x else g.x
            val oz = if (g.inCar) p.z else g.z
            var best: Car? = null
            var bd = if (g.inCar) 28f else 38f
            for (c in cars) {
                if (c.kind != 1 || c.dead) continue
                val dd = dst(ox, oz, c.x, c.z)
                if (dd < bd && clearLine(ox, oz, c.x, c.z)) { bd = dd; best = c }
            }
            if (best != null) {
                g.fireCd = 0.8f
                val a = atan2(best.z - oz, best.x - ox) + rnd(-0.12f, 0.12f)
                hitscan(ox + cos(a) * 1.2f, oz + sin(a) * 1.2f, cos(a), sin(a), 45f, 9f, true, null)
                g.talkCd -= 1f
                if (g.talkCd <= 0f) {
                    g.talkCd = 10f
                    showMsg(when ((Math.random() * 3).toInt()) {
                        0 -> "Lucía: ¡Yo me encargo de esos, mi amor!"
                        1 -> "Lucía: ¡Sigue, cariño, yo te cubro!"
                        else -> "Lucía: ¡Cuidado, vienen más! ♥"
                    })
                }
            } else g.fireCd = 0.3f
        }
    }

    // ---------- choques, explosiones, reaparicion ----------
    private fun collide(a: Car, b: Car, d: Float) {
        val nx = (b.x - a.x) / d
        val nz = (b.z - a.z) / d
        val push = (4.2f - d) / 2f
        a.x -= nx * push; a.z -= nz * push
        b.x += nx * push; b.z += nz * push
        val pl = if (a.kind == 2) a else if (b.kind == 2) b else null
        if (pl != null) {
            val o = if (pl === a) b else a
            if (pl.hitcd > 0f) return
            val rel = abs(pl.speed) + abs(o.speed) * 0.5f
            pl.hp -= rel * 0.5f
            pl.hitcd = 0.4f
            pl.speed *= 0.6f
            if (o.kind == 1) {
                o.hp -= abs(pl.speed) * 0.8f + 6f
            } else if (o.kind == 0) {
                o.stun = 1.5f
                o.speed = 0f
                if (rel > 7f) heat = min(5.99f, heat + 0.35f)
            }
            burst((a.x + b.x) / 2f, 1f, (a.z + b.z) / 2f, 6, SPARK, 6f, 0.5f)
        } else {
            if (a.kind == 1 && b.kind == 0) { b.stun = 1.2f; b.speed = 0f }
            else if (b.kind == 1 && a.kind == 0) { a.stun = 1.2f; a.speed = 0f }
        }
    }

    private fun explode(c: Car) {
        burst(c.x, 1f, c.z, 24, FIRE, 12f, 1f)
        burst(c.x, 1f, c.z, 14, SMOKE, 8f, 1.2f)
        c.dead = true
        if (c.kind == 2) {
            // el jugador sale despedido a pie y se hace dano
            pcar = null
            player.x = c.x; player.z = c.z; player.speed = 0f
            val nx = c.x + cos(c.a + PIF / 2f) * 3f
            val nz = c.z + sin(c.a + PIF / 2f) * 3f
            if (!solid(nx, nz)) { player.x = nx; player.z = nz }
            hp -= 35f
            if (lucia.inCar) { lucia.inCar = false; girlTeleport(lucia) }
            showMsg("¡Tu coche explotó!")
        } else if (c.kind == 1) {
            money += 150
            showMsg("Patrulla destruida +\$150")
            if (crates.size < 14) crates.add(Crate(c.x, c.z, (Math.random() * 3).toInt() % 3))
        }
    }

    private fun respawn() {
        val old = pcar
        if (old != null) old.kind = 3
        pcar = null
        val nc = Car(2f * CELL, 2f * CELL + LANE[0], 0f, 2)
        nc.type = 1
        nc.col = floatArrayOf(0.85f, 0.12f, 0.12f)
        cars.add(nc)
        pcar = nc
        player.x = nc.x; player.z = nc.z; player.a = 0f; player.speed = 0f
        hp = 100f
        heat = 0f
        camA = 0f
        camYaw = 0f
        lookOff = 0f
        lucia.inCar = true
        for (c in cars) if (c.kind == 1) c.dead = true
        if (ammo[0] < 30) ammo[0] = 30
        money = max(0, money - 200)
        showMsg("Gastos médicos: -\$200. Lucía te espera en el coche ♥")
    }

    // ---------- bucle principal ----------
    fun update(dt: Float) {
        if (intro) {
            introT += dt
            time += dt
            for (c in cars) if (c.kind == 0) roadDrive(c, dt)
            for (q in peds) pedUpdate(q, dt)
            return
        }
        time += dt
        if (msgT > 0f) msgT -= dt
        val p = player
        val lvl = min(5, heat.toInt())
        if (actEnter) { actEnter = false; toggleVehicle() }
        if (actWeapon) { actWeapon = false; cycleWeapon() }
        p.hitcd -= dt
        flash -= dt

        if (wastedT > 0f) {
            wastedT -= dt
            p.speed = 0f
            if (wastedT <= 0f) respawn()
        } else {
            val pc = pcar
            if (pc != null) drivePlayer(pc, dt) else walkPlayer(dt)
            shootUpdate(dt)
            if (heat < 1f) {
                // junto a Lucia te recuperas mas rapido
                val rate = if (lucia.inCar || dst(lucia.x, lucia.z, p.x, p.z) < 8f) 5f else 2f
                hp = min(100f, hp + rate * dt)
                if (pc != null) pc.hp = min(100f, pc.hp + rate * dt)
            }
            if (hp <= 0f) {
                wastedT = 3.2f
                burst(p.x, 1f, p.z, 14, FIRE, 8f, 0.8f)
                val dead = pcar
                if (dead != null) { dead.kind = 3; pcar = null }
                if (lucia.inCar) lucia.inCar = false
                showMsg("¡Te mataron!")
            }
        }

        var cops = 0
        for (c in cars) if (c.kind == 1 && !c.dead) cops++
        copCd -= dt
        if (cops < min(6, lvl * 2) && copCd <= 0f && wastedT <= 0f) { spawnCop(); copCd = 2.5f }
        if (lvl == 0) for (c in cars) if (c.kind == 1) { c.kind = 0; c.maxSp = 11f; c.direct = false; c.lastKey = -1 }

        for (c in cars) {
            c.hitcd -= dt
            when (c.kind) {
                0 -> {
                    roadDrive(c, dt)
                    if (dst(c.x, c.z, p.x, p.z) > 320f) placeCar(c, p.x, p.z, 150f, 260f)
                }
                1 -> {
                    copUpdate(c, dt)
                    if (dst(c.x, c.z, p.x, p.z) > 420f) c.dead = true
                }
                3 -> {
                    c.speed *= max(0f, 1f - 3f * dt)
                    if (abs(c.speed) > 0.3f) moveCar(c, dt)
                    if (dst(c.x, c.z, p.x, p.z) > 320f) c.dead = true
                }
            }
        }
        for (a in 0 until cars.size) for (b in a + 1 until cars.size) {
            val ca = cars[a]
            val cb = cars[b]
            if (abs(ca.x - cb.x) > 4.3f || abs(ca.z - cb.z) > 4.3f) continue
            val d = dst(ca.x, ca.z, cb.x, cb.z)
            if (d < 4.2f && d > 0.01f) collide(ca, cb, d)
        }
        for (c in cars) if (c.hp <= 0f && !c.dead) explode(c)
        cars.removeAll { it.dead }

        luciaUpdate(dt)
        for (q in peds) pedUpdate(q, dt)

        for (k in pickups.size - 1 downTo 0) {
            val pk = pickups[k]
            if (dst(pk.x, pk.z, p.x, p.z) < 3f) { money += pk.v; pickups.removeAt(k) }
        }
        if (wastedT <= 0f) {
            for (cr in crates) {
                if (dst(cr.x, cr.z, p.x, p.z) < 3f) {
                    ammo[cr.w] += AMMO_PACK[cr.w]
                    if (ammo[curW] == 0) curW = cr.w
                    showMsg("+" + AMMO_PACK[cr.w] + " " + WEAPONS[cr.w].name)
                    placeCrate(cr)
                }
            }
        }
        if (wastedT <= 0f && dst(missionX, missionZ, p.x, p.z) < 9f) {
            val r = 200 + (missionLen / 2f).roundToInt()
            money += r
            showMsg("Misión completada +\$$r")
            newMission()
        }

        var near = false
        var nc = 0
        val snapshot = FloatArray(cars.size * 2)
        for (c in cars) if (c.kind == 1) {
            if (dst(c.x, c.z, p.x, p.z) < 60f) near = true
            snapshot[nc * 2] = c.x
            snapshot[nc * 2 + 1] = c.z
            nc++
        }
        mapCops = snapshot.copyOf(nc * 2)
        val cs = FloatArray(crates.size * 2)
        for (k in 0 until crates.size) { cs[k * 2] = crates[k].x; cs[k * 2 + 1] = crates[k].z }
        mapCrates = cs
        heat = max(0f, heat - (if (near) 0.01f else 0.08f) * dt)

        for (q in parts) {
            q.x += q.vx * dt; q.y += q.vy * dt; q.z += q.vz * dt
            q.vy -= 9f * dt
            if (q.y < 0.15f) { q.y = 0.15f; q.vy = 0f }
            q.life -= dt
        }
        parts.removeAll { it.life <= 0f }
        for (tr in tracers) tr.life -= dt
        tracers.removeAll { it.life <= 0f }
        if (abs(p.speed) > 20f && pcar != null && Math.random() < 0.3) burst(p.x, 0.4f, p.z, 1, PUFF, 1f, 0.4f)

        // camara libre: arrastrar para mirar a todos lados
        val ldx = lookDX
        val ldy = lookDY
        lookDX = 0f
        lookDY = 0f
        if (pcar != null) {
            lookOff = clampF(lookOff + ldx * 0.006f, -3.1f, 3.1f)
            if (!lookActive) lookOff *= max(0f, 1f - 2.5f * dt)
            camA += angDiff(camA, p.a) * min(1f, dt * 3.5f)
            camYaw = camA + lookOff
        } else {
            camYaw += ldx * 0.006f
            camA = camYaw
        }
        camPitch = clampF(camPitch + ldy * 0.004f, -0.15f, 1.0f)
    }
}
