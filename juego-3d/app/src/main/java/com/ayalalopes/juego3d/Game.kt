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

val PALETTE = arrayOf(
    floatArrayOf(0.75f, 0.22f, 0.18f), floatArrayOf(0.18f, 0.40f, 0.80f),
    floatArrayOf(0.18f, 0.62f, 0.35f), floatArrayOf(0.84f, 0.63f, 0.16f),
    floatArrayOf(0.56f, 0.27f, 0.68f), floatArrayOf(0.86f, 0.86f, 0.86f),
    floatArrayOf(0.29f, 0.29f, 0.31f), floatArrayOf(0.90f, 0.50f, 0.13f)
)

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

class Car(var x: Float, var z: Float, var a: Float, var kind: Int) {   // kind: 0 civil, 1 policia, 2 jugador
    var speed = 0f
    var dir = 0
    var maxSp = rnd(9f, 14f)
    var hp = 100f
    var stun = 0f
    var lastKey = -1
    var stuck = 0f
    var rev = 0f
    var direct = false
    var hitcd = 0f
    var dead = false
    var col: FloatArray = PALETTE[(Math.random() * PALETTE.size).toInt() % PALETTE.size]
}

class Ped(var x: Float, var z: Float) {
    var a = rnd(0f, TWO_PI)
    var sp = rnd(1.5f, 2.6f)
    var t = rnd(1f, 4f)
    var flee = 0f
    val col: FloatArray = floatArrayOf(rnd(0.2f, 0.9f), rnd(0.2f, 0.9f), rnd(0.2f, 0.9f))
}

class Particle(
    var x: Float, var y: Float, var z: Float,
    var vx: Float, var vy: Float, var vz: Float,
    var life: Float, val col: FloatArray, val size: Float
)

class Pickup(val x: Float, val z: Float, val v: Int)

class Game {
    @Volatile var inLeft = false
    @Volatile var inRight = false
    @Volatile var inGas = false
    @Volatile var inBrake = false

    val cars = ArrayList<Car>()
    val peds = ArrayList<Ped>()
    val parts = ArrayList<Particle>()
    val pickups = ArrayList<Pickup>()
    val player = Car(2f * CELL, 2f * CELL + LANE[0], 0f, 2)

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

    init {
        player.col = floatArrayOf(0.85f, 0.12f, 0.12f)
        cars.add(player)
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
        newMission()
        showMsg("Conduce hasta el punto amarillo. ¡Cuidado con la policía!")
    }

    fun showMsg(s: String) { msg = s; msgT = 3.5f }

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

    private val SPARK = floatArrayOf(1f, 0.85f, 0.3f)
    private val FIRE = floatArrayOf(1f, 0.5f, 0.1f)
    private val SMOKE = floatArrayOf(0.3f, 0.3f, 0.3f)
    private val PUFF = floatArrayOf(0.8f, 0.8f, 0.8f)

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

    private fun drivePlayer(dt: Float) {
        val c = player
        val st = (if (inRight) 1f else 0f) - (if (inLeft) 1f else 0f)
        if (inGas) c.speed += (if (c.speed < 0f) 40f else 15f) * dt
        if (inBrake) c.speed -= (if (c.speed > 0f) 30f else 9f) * dt
        c.speed -= c.speed * 0.3f * dt
        if (!inGas && !inBrake) c.speed *= max(0f, 1f - 0.8f * dt)
        c.speed = clampF(c.speed, -9f, 34f)
        val na = c.a + st * 2.2f * clampF(c.speed / 7f, -1f, 1f) * dt
        if (!carBlocked(c.x, c.z, na)) c.a = na
        moveCar(c, dt)
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
        c.maxSp = 15f + heat * 1.6f
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
        val tsp = if (abs(diff) > 1.3f) 7f else 17f + heat * 1.7f
        c.speed += (tsp - c.speed) * min(1f, dt * 1.5f)
        moveCar(c, dt)
        if (c.speed < 2f) {
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
        if (q.flee > 0f || (abs(p.speed) > 6f && d < 22f)) {
            q.a = atan2(q.z - p.z, q.x - p.x)
            sp = 5.5f
            q.flee -= dt
        }
        val nx = q.x + cos(q.a) * sp * dt
        val nz = q.z + sin(q.a) * sp * dt
        if (solid(nx, nz)) q.a += PIF / 2f + rnd(0f, 1f) else { q.x = nx; q.z = nz }
        if (abs(p.speed) > 4f && d < 2.4f && wastedT <= 0f) killPed(q)
        else if (d > 260f) placePed(q, p.x, p.z, 120f, 220f)
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
            } else {
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
        if (c.kind == 2) {
            if (wastedT <= 0f) {
                wastedT = 3.2f
                c.speed = 0f
                burst(c.x, 1f, c.z, 30, FIRE, 12f, 1f)
                burst(c.x, 1f, c.z, 20, SMOKE, 8f, 1.2f)
                showMsg("¡Coche destruido!")
            }
        } else {
            burst(c.x, 1f, c.z, 24, FIRE, 12f, 1f)
            burst(c.x, 1f, c.z, 14, SMOKE, 8f, 1.2f)
            c.dead = true
            if (c.kind == 1) { money += 150; showMsg("Patrulla destruida +\$150") }
        }
    }

    private fun respawn() {
        val p = player
        p.x = 2f * CELL; p.z = 2f * CELL + LANE[0]; p.a = 0f; p.speed = 0f; p.hp = 100f
        camA = 0f
        heat = 0f
        for (c in cars) if (c.kind == 1) c.dead = true
        money = max(0, money - 200)
        showMsg("Gastos de grúa: -\$200")
    }

    // ---------- bucle principal ----------
    fun update(dt: Float) {
        time += dt
        if (msgT > 0f) msgT -= dt
        val p = player
        val lvl = min(5, heat.toInt())
        p.hitcd -= dt
        if (wastedT > 0f) {
            wastedT -= dt
            p.speed = 0f
            if (wastedT <= 0f) respawn()
        } else {
            drivePlayer(dt)
            if (heat < 1f) p.hp = min(100f, p.hp + 2f * dt)
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

        for (q in peds) pedUpdate(q, dt)

        for (k in pickups.size - 1 downTo 0) {
            val pk = pickups[k]
            if (dst(pk.x, pk.z, p.x, p.z) < 3f) { money += pk.v; pickups.removeAt(k) }
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
        heat = max(0f, heat - (if (near) 0.01f else 0.08f) * dt)

        for (q in parts) {
            q.x += q.vx * dt; q.y += q.vy * dt; q.z += q.vz * dt
            q.vy -= 9f * dt
            if (q.y < 0.15f) { q.y = 0.15f; q.vy = 0f }
            q.life -= dt
        }
        parts.removeAll { it.life <= 0f }
        if (abs(p.speed) > 20f && Math.random() < 0.3) burst(p.x, 0.4f, p.z, 1, PUFF, 1f, 0.4f)

        camA += angDiff(camA, p.a) * min(1f, dt * 3.5f)
    }
}
