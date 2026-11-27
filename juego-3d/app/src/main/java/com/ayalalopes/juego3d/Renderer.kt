package com.ayalalopes.juego3d

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class Renderer(private val game: Game) : GLSurfaceView.Renderer {

    private val VERT = """
        uniform mat4 uMVP;
        uniform mat4 uRot;
        attribute vec3 aPos;
        attribute vec3 aNorm;
        varying vec3 vN;
        varying float vDist;
        void main() {
            vec4 p = uMVP * vec4(aPos, 1.0);
            gl_Position = p;
            vDist = p.w;
            vN = (uRot * vec4(aNorm, 0.0)).xyz;
        }
    """.trimIndent()

    private val FRAG = """
        precision mediump float;
        uniform vec3 uColor;
        uniform vec3 uSky;
        uniform float uEmit;
        varying vec3 vN;
        varying float vDist;
        void main() {
            vec3 L = normalize(vec3(0.45, 0.85, 0.30));
            float diff = max(dot(normalize(vN), L), 0.0);
            float light = 0.42 + 0.62 * diff;
            vec3 c = mix(uColor * light, uColor, uEmit);
            float f = clamp((vDist - 110.0) / 170.0, 0.0, 1.0);
            gl_FragColor = vec4(mix(c, uSky, f), 1.0);
        }
    """.trimIndent()

    private val SKY = floatArrayOf(0.53f, 0.72f, 0.92f)
    private val GRASS = floatArrayOf(0.30f, 0.52f, 0.28f)
    private val ASPHALT = floatArrayOf(0.20f, 0.21f, 0.23f)
    private val SIDEWALK = floatArrayOf(0.62f, 0.62f, 0.60f)
    private val LINE = floatArrayOf(0.88f, 0.74f, 0.20f)
    private val GLASS = floatArrayOf(0.16f, 0.24f, 0.34f)
    private val ROOF = floatArrayOf(0.30f, 0.30f, 0.32f)
    private val PARK = floatArrayOf(0.24f, 0.55f, 0.24f)
    private val TREE = floatArrayOf(0.14f, 0.40f, 0.16f)
    private val TRUNK = floatArrayOf(0.35f, 0.24f, 0.14f)
    private val BLACK = floatArrayOf(0.07f, 0.07f, 0.08f)
    private val SHADOW = floatArrayOf(0.10f, 0.10f, 0.11f)
    private val HEADLIGHT = floatArrayOf(1f, 0.95f, 0.65f)
    private val TAILLIGHT = floatArrayOf(0.9f, 0.1f, 0.1f)
    private val RED = floatArrayOf(1f, 0.15f, 0.15f)
    private val BLUE = floatArrayOf(0.2f, 0.4f, 1f)
    private val YELLOW = floatArrayOf(1f, 0.85f, 0.1f)
    private val GREEN = floatArrayOf(0.25f, 0.9f, 0.4f)
    private val SKIN = floatArrayOf(0.94f, 0.76f, 0.60f)
    private val DARKPANTS = floatArrayOf(0.15f, 0.17f, 0.25f)
    private val BUILD = arrayOf(
        floatArrayOf(0.62f, 0.60f, 0.56f), floatArrayOf(0.55f, 0.45f, 0.40f),
        floatArrayOf(0.45f, 0.52f, 0.60f), floatArrayOf(0.70f, 0.66f, 0.55f),
        floatArrayOf(0.48f, 0.48f, 0.52f), floatArrayOf(0.60f, 0.38f, 0.34f),
        floatArrayOf(0.40f, 0.55f, 0.50f), floatArrayOf(0.75f, 0.75f, 0.72f)
    )

    private var prog = 0
    private var uMVP = 0
    private var uRot = 0
    private var uColor = 0
    private var uSky = 0
    private var uEmit = 0
    private var aPos = 0
    private var aNorm = 0
    private lateinit var vbuf: FloatBuffer
    private lateinit var ibuf: ShortBuffer

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val rot = FloatArray(16)
    private var last = 0L
    private var camX = 0f
    private var camZ = 0f

    private val bh = FloatArray(NB * NB)
    private val bw = FloatArray(NB * NB)
    private val bd = FloatArray(NB * NB)
    private val bc = Array(NB * NB) { floatArrayOf(0.5f, 0.5f, 0.5f) }

    init {
        for (i in 0 until NB) for (j in 0 until NB) {
            val k = i * NB + j
            bh[k] = 12f + hash(j + 5, i + 9) * 52f
            bw[k] = (CELL - ROAD) - 6f - hash(i + 2, j + 7) * 6f
            bd[k] = (CELL - ROAD) - 6f - hash(j + 8, i + 1) * 6f
            bc[k] = BUILD[(hash(i + 3, j + 11) * BUILD.size).toInt() % BUILD.size]
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(SKY[0], SKY[1], SKY[2], 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        val vs = compile(GLES20.GL_VERTEX_SHADER, VERT)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, FRAG)
        prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) Log.e("Juego3D", "Link error: " + GLES20.glGetProgramInfoLog(prog))
        GLES20.glUseProgram(prog)
        uMVP = GLES20.glGetUniformLocation(prog, "uMVP")
        uRot = GLES20.glGetUniformLocation(prog, "uRot")
        uColor = GLES20.glGetUniformLocation(prog, "uColor")
        uSky = GLES20.glGetUniformLocation(prog, "uSky")
        uEmit = GLES20.glGetUniformLocation(prog, "uEmit")
        aPos = GLES20.glGetAttribLocation(prog, "aPos")
        aNorm = GLES20.glGetAttribLocation(prog, "aNorm")
        buildCube()
        last = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / max(1, height).toFloat()
        Matrix.perspectiveM(proj, 0, 60f, aspect, 2f, 320f)
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) Log.e("Juego3D", "Shader error: " + GLES20.glGetShaderInfoLog(s))
        return s
    }

    /** Cubo unitario centrado en el origen: 24 vertices (posicion + normal). */
    private fun buildCube() {
        // cada cara: normal (n), eje u, eje v con u x v = n
        val faces = arrayOf(
            intArrayOf(1, 0, 0, 0, 1, 0, 0, 0, 1),
            intArrayOf(-1, 0, 0, 0, 0, 1, 0, 1, 0),
            intArrayOf(0, 1, 0, 0, 0, 1, 1, 0, 0),
            intArrayOf(0, -1, 0, 1, 0, 0, 0, 0, 1),
            intArrayOf(0, 0, 1, 1, 0, 0, 0, 1, 0),
            intArrayOf(0, 0, -1, 0, 1, 0, 1, 0, 0)
        )
        val sus = intArrayOf(-1, 1, 1, -1)
        val svs = intArrayOf(-1, -1, 1, 1)
        val v = FloatArray(24 * 6)
        val idx = ShortArray(36)
        var o = 0
        for (f in 0 until 6) {
            val n = faces[f]
            for (k in 0 until 4) {
                for (c in 0 until 3) {
                    v[o + c] = 0.5f * n[c] + 0.5f * sus[k] * n[3 + c] + 0.5f * svs[k] * n[6 + c]
                    v[o + 3 + c] = n[c].toFloat()
                }
                o += 6
            }
            val b = (f * 4).toShort()
            val base = f * 6
            idx[base] = b
            idx[base + 1] = (b + 1).toShort()
            idx[base + 2] = (b + 2).toShort()
            idx[base + 3] = b
            idx[base + 4] = (b + 2).toShort()
            idx[base + 5] = (b + 3).toShort()
        }
        vbuf = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vbuf.put(v)
        vbuf.position(0)
        ibuf = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        ibuf.put(idx)
        ibuf.position(0)
    }

    private fun box(
        cx: Float, cy: Float, cz: Float, sx: Float, sy: Float, sz: Float,
        yawDeg: Float, col: FloatArray, emit: Float
    ) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, cx, cy, cz)
        if (yawDeg != 0f) Matrix.rotateM(model, 0, yawDeg, 0f, 1f, 0f)
        Matrix.scaleM(model, 0, sx, sy, sz)
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
        Matrix.setRotateM(rot, 0, yawDeg, 0f, 1f, 0f)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uRot, 1, false, rot, 0)
        GLES20.glUniform3f(uColor, col[0], col[1], col[2])
        GLES20.glUniform1f(uEmit, emit)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 36, GLES20.GL_UNSIGNED_SHORT, ibuf)
    }

    /** Pieza de un coche en coordenadas locales (lx adelante, lz derecha). */
    private fun part(
        c: Car, lx: Float, ly: Float, lz: Float, sx: Float, sy: Float, sz: Float,
        col: FloatArray, emit: Float
    ) {
        val ca = cos(c.a)
        val sa = sin(c.a)
        box(c.x + ca * lx - sa * lz, ly, c.z + sa * lx + ca * lz, sx, sy, sz, -c.a * 57.29578f, col, emit)
    }

    private fun drawCar(c: Car, t: Float) {
        val police = c.kind == 1
        part(c, 0f, 0.03f, 0f, 4.7f, 0.02f, 2.3f, SHADOW, 0f)
        part(c, 0f, 0.65f, 0f, 4.4f, 0.75f, 2.0f, c.col, 0f)
        part(c, -0.3f, 1.3f, 0f, 2.3f, 0.65f, 1.8f, GLASS, 0f)
        part(c, -0.3f, 1.66f, 0f, 1.9f, 0.1f, 1.6f, c.col, 0f)
        for (sx in floatArrayOf(1.4f, -1.4f)) for (sz in floatArrayOf(1.0f, -1.0f)) {
            part(c, sx, 0.45f, sz, 0.9f, 0.9f, 0.35f, BLACK, 0f)
        }
        part(c, 2.2f, 0.75f, 0.7f, 0.1f, 0.25f, 0.4f, HEADLIGHT, 1f)
        part(c, 2.2f, 0.75f, -0.7f, 0.1f, 0.25f, 0.4f, HEADLIGHT, 1f)
        part(c, -2.2f, 0.75f, 0.7f, 0.1f, 0.2f, 0.4f, TAILLIGHT, 1f)
        part(c, -2.2f, 0.75f, -0.7f, 0.1f, 0.2f, 0.4f, TAILLIGHT, 1f)
        if (police) {
            part(c, 0f, 0.72f, 0f, 4.42f, 0.18f, 2.02f, BLACK, 0f)
            val f = (t * 8f).toInt() % 2 == 0
            part(c, -0.3f, 1.8f, 0.5f, 0.4f, 0.25f, 0.6f, if (f) RED else BLUE, 1f)
            part(c, -0.3f, 1.8f, -0.5f, 0.4f, 0.25f, 0.6f, if (f) BLUE else RED, 1f)
        }
    }

    private fun drawPed(q: Ped) {
        val yaw = -q.a * 57.29578f
        box(q.x, 0.35f, q.z, 0.35f, 0.7f, 0.5f, yaw, DARKPANTS, 0f)
        box(q.x, 1.0f, q.z, 0.4f, 0.7f, 0.6f, yaw, q.col, 0f)
        box(q.x, 1.6f, q.z, 0.4f, 0.4f, 0.4f, yaw, SKIN, 0f)
    }

    private fun drawWorld(t: Float) {
        // suelo
        box(WORLD / 2f, -0.6f, WORLD / 2f, 2200f, 1f, 2200f, 0f, GRASS, 0f)
        box(WORLD / 2f, -0.05f, WORLD / 2f, WORLD + ROAD, 0.1f, WORLD + ROAD, 0f, ASPHALT, 0f)

        // lineas centrales de las calles
        val seg = CELL - ROAD - 2f
        for (a in 0..NB) {
            for (b in 0 until NB) {
                val mid = b * CELL + CELL / 2f
                if (abs(mid - camX) < 260f && abs(a * CELL - camZ) < 260f)
                    box(mid, 0.06f, a * CELL, seg, 0.12f, 0.35f, 0f, LINE, 0f)
                if (abs(a * CELL - camX) < 260f && abs(mid - camZ) < 260f)
                    box(a * CELL, 0.06f, mid, 0.35f, 0.12f, seg, 0f, LINE, 0f)
            }
        }

        // bloques
        val bs = CELL - ROAD
        for (i in 0 until NB) for (j in 0 until NB) {
            val cx = i * CELL + ROAD / 2f + bs / 2f
            val cz = j * CELL + ROAD / 2f + bs / 2f
            if (abs(cx - camX) > 280f || abs(cz - camZ) > 280f) continue
            box(cx, 0.15f, cz, bs, 0.3f, bs, 0f, SIDEWALK, 0f)
            if (isPark(i, j)) {
                box(cx, 0.32f, cz, bs - 4f, 0.1f, bs - 4f, 0f, PARK, 0f)
                for (k in 0 until 6) {
                    val tx = cx - bs / 2f + 6f + hash(i * 10 + k, j) * (bs - 12f)
                    val tz = cz - bs / 2f + 6f + hash(i, j * 10 + k) * (bs - 12f)
                    box(tx, 1.8f, tz, 0.7f, 3f, 0.7f, 0f, TRUNK, 0f)
                    box(tx, 4.6f, tz, 3.4f, 3f, 3.4f, 0f, TREE, 0f)
                }
            } else {
                val k = i * NB + j
                val h = bh[k]
                box(cx, 0.3f + h / 2f, cz, bw[k], h, bd[k], 0f, bc[k], 0f)
                box(cx, 0.3f + h + 0.25f, cz, bw[k] + 0.6f, 0.5f, bd[k] + 0.6f, 0f, ROOF, 0f)
                var y = 4f
                while (y < h - 2f) {
                    box(cx, 0.3f + y, cz, bw[k] + 0.2f, 1.6f, bd[k] + 0.2f, 0f, GLASS, 0f)
                    y += 5f
                }
            }
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = min(0.05f, (now - last) / 1_000_000_000f)
        last = now
        game.update(dt)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val p = game.player
        val cdx = cos(game.camA)
        val cdz = sin(game.camA)
        val dist = 10f + abs(p.speed) * 0.15f
        camX = p.x - cdx * dist
        camZ = p.z - cdz * dist
        val camY = 4.6f + abs(p.speed) * 0.04f
        Matrix.setLookAtM(view, 0, camX, camY, camZ, p.x + cdx * 6f, 1.3f, p.z + cdz * 6f, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        GLES20.glUseProgram(prog)
        GLES20.glUniform3f(uSky, SKY[0], SKY[1], SKY[2])
        vbuf.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, vbuf)
        GLES20.glEnableVertexAttribArray(aPos)
        vbuf.position(3)
        GLES20.glVertexAttribPointer(aNorm, 3, GLES20.GL_FLOAT, false, 24, vbuf)
        GLES20.glEnableVertexAttribArray(aNorm)

        val t = game.time
        drawWorld(t)

        // punto de mision: pilar luminoso + estrella giratoria en el suelo
        val mx = game.missionX
        val mz = game.missionZ
        box(mx, 25f, mz, 1.6f, 50f, 1.6f, 0f, YELLOW, 1f)
        box(mx, 0.12f, mz, 12f, 0.2f, 12f, t * 60f, YELLOW, 1f)
        box(mx, 0.14f, mz, 12f, 0.2f, 12f, t * 60f + 45f, YELLOW, 1f)

        for (pk in game.pickups) {
            if (abs(pk.x - p.x) < 200f && abs(pk.z - p.z) < 200f)
                box(pk.x, 1.1f, pk.z, 0.9f, 0.9f, 0.3f, t * 150f, GREEN, 1f)
        }
        for (c in game.cars) {
            if (c.kind == 2 && game.wastedT > 0f) continue
            if (abs(c.x - p.x) < 260f && abs(c.z - p.z) < 260f) drawCar(c, t)
        }
        for (q in game.peds) {
            if (abs(q.x - p.x) < 200f && abs(q.z - p.z) < 200f) drawPed(q)
        }
        for (q in game.parts) {
            box(q.x, q.y, q.z, q.size, q.size, q.size, 0f, q.col, 1f)
        }
    }
}
