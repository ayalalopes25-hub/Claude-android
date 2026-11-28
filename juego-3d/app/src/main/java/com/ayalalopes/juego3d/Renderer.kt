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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Dibujo 3D con OpenGL ES 2.0. Estilo: ciudad al atardecer (hora dorada) con cielo y sol,
 * ventanas encendidas, brillos en carrocerias, sombras, farolas, palmeras y neones.
 */
class Renderer(private val game: Game) : GLSurfaceView.Renderer {

    // ---------------- shaders ----------------
    private val VERT = """
        uniform mat4 uVP;
        uniform mat4 uModel;
        attribute vec3 aPos;
        attribute vec3 aNorm;
        varying vec3 vN;
        varying vec3 vWorld;
        varying float vDist;
        void main() {
            vec4 w = uModel * vec4(aPos, 1.0);
            vec4 clip = uVP * w;
            gl_Position = clip;
            vWorld = w.xyz;
            vN = (uModel * vec4(aNorm, 0.0)).xyz;
            vDist = clip.w;
        }
    """.trimIndent()

    private val FRAG = """
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
        uniform vec3 uColor;
        uniform vec3 uFog;
        uniform vec3 uCam;
        uniform float uEmit;
        uniform float uSpec;
        uniform float uShin;
        uniform float uPat;
        uniform float uAlpha;
        uniform float uFogMax;
        uniform float uSeed;
        varying vec3 vN;
        varying vec3 vWorld;
        varying float vDist;

        float h21(vec2 p) {
            return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
        }

        void main() {
            vec3 n = normalize(vN);
            vec3 L = normalize(vec3(-0.45, 0.45, -0.77));
            vec3 sunC = vec3(1.0, 0.78, 0.55);
            vec3 amb = mix(vec3(0.34, 0.29, 0.27), vec3(0.45, 0.52, 0.70), n.y * 0.5 + 0.5);
            float diff = max(dot(n, L), 0.0);
            vec3 base = uColor;
            vec3 glow = vec3(0.0);
            if (uPat > 0.5 && abs(n.y) < 0.5) {
                float u = (abs(n.x) > 0.5) ? vWorld.z : vWorld.x;
                vec2 g = vec2(u / 3.2, vWorld.y / 3.6);
                vec2 cell = floor(g);
                vec2 fr = fract(g);
                float inWin = step(0.18, fr.x) * step(fr.x, 0.82) * step(0.22, fr.y) * step(fr.y, 0.78) * step(1.0, cell.y);
                if (inWin > 0.5) {
                    float r = h21(cell + vec2(uSeed, uSeed * 1.7));
                    if (r < 0.42) {
                        base = vec3(1.0, 0.80, 0.45);
                        glow = vec3(0.95, 0.70, 0.35) * 0.65;
                    } else {
                        base = mix(vec3(0.10, 0.16, 0.26), vec3(0.80, 0.50, 0.38), fr.y * 0.6);
                    }
                }
            }
            vec3 V = normalize(uCam - vWorld);
            vec3 c = base * (amb + sunC * diff * 0.95) + glow;
            c += base * vec3(0.9, 0.75, 0.65) * 0.16 * max(dot(n, V), 0.0);
            vec3 H = normalize(L + V);
            c += sunC * pow(max(dot(n, H), 0.0001), uShin) * uSpec * step(0.001, diff);
            c = mix(c, base, uEmit);
            float fogAmt = clamp((vDist - 90.0) / 190.0, 0.0, 1.0) * uFogMax;
            vec3 vd = normalize(vWorld - uCam);
            vec3 fogC = uFog + sunC * pow(max(dot(vd, L), 0.0001), 6.0) * 0.30;
            c = mix(c, fogC, fogAmt);
            gl_FragColor = vec4(c, uAlpha);
        }
    """.trimIndent()

    private val SKY_VERT = """
        attribute vec2 aP;
        varying vec2 vUv;
        void main() {
            vUv = aP;
            gl_Position = vec4(aP, 0.9999, 1.0);
        }
    """.trimIndent()

    private val SKY_FRAG = """
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
        uniform vec3 uFwd;
        uniform vec3 uRight;
        uniform vec3 uUp;
        uniform vec2 uScale;
        uniform vec3 uSun;
        varying vec2 vUv;
        void main() {
            vec3 d = normalize(uFwd + uRight * (vUv.x * uScale.x) + uUp * (vUv.y * uScale.y));
            float h = d.y;
            vec3 hor = vec3(0.96, 0.74, 0.58);
            vec3 mid = vec3(0.93, 0.56, 0.42);
            vec3 zen = vec3(0.22, 0.40, 0.74);
            vec3 col = mix(hor, mid, smoothstep(0.0, 0.14, h));
            col = mix(col, zen, smoothstep(0.10, 0.75, h));
            float sd = max(dot(d, uSun), 0.0);
            col += vec3(1.0, 0.78, 0.50) * pow(sd, 5.0) * 0.5;
            col += vec3(1.0, 0.92, 0.75) * smoothstep(0.9975, 0.9990, sd);
            float cl = smoothstep(0.5, 0.8, sin(d.x * 9.0 + d.z * 5.0) * 0.5 + 0.5)
                * smoothstep(0.08, 0.3, h) * (1.0 - smoothstep(0.3, 0.55, h));
            col = mix(col, vec3(1.0, 0.72, 0.58), cl * 0.35);
            gl_FragColor = vec4(col, 1.0);
        }
    """.trimIndent()

    // ---------------- colores ----------------
    private val FOG = floatArrayOf(0.96f, 0.74f, 0.58f)
    private val GRASS = floatArrayOf(0.30f, 0.46f, 0.26f)
    private val ASPHALT = floatArrayOf(0.17f, 0.17f, 0.19f)
    private val SIDEWALK = floatArrayOf(0.60f, 0.58f, 0.55f)
    private val LINE = floatArrayOf(0.88f, 0.74f, 0.20f)
    private val ROOF = floatArrayOf(0.30f, 0.30f, 0.32f)
    private val PARK = floatArrayOf(0.24f, 0.52f, 0.24f)
    private val TREE = floatArrayOf(0.14f, 0.40f, 0.16f)
    private val TRUNK = floatArrayOf(0.35f, 0.24f, 0.14f)
    private val PALM = floatArrayOf(0.16f, 0.46f, 0.18f)
    private val POLE = floatArrayOf(0.22f, 0.23f, 0.25f)
    private val LAMP = floatArrayOf(1f, 0.86f, 0.55f)
    private val SKYLINE = floatArrayOf(0.36f, 0.38f, 0.52f)
    private val HILL = floatArrayOf(0.42f, 0.32f, 0.30f)
    private val BLACK = floatArrayOf(0.07f, 0.07f, 0.08f)
    private val SHADOWC = floatArrayOf(0.04f, 0.03f, 0.06f)
    private val GLASS = floatArrayOf(0.16f, 0.24f, 0.34f)
    private val HEADLIGHT = floatArrayOf(1f, 0.95f, 0.65f)
    private val BEAM = floatArrayOf(1f, 0.88f, 0.55f)
    private val TAILLIGHT = floatArrayOf(0.9f, 0.1f, 0.1f)
    private val RED = floatArrayOf(1f, 0.15f, 0.15f)
    private val BLUE = floatArrayOf(0.2f, 0.4f, 1f)
    private val YELLOW = floatArrayOf(1f, 0.85f, 0.1f)
    private val GREEN = floatArrayOf(0.25f, 0.9f, 0.4f)
    private val SKIN = floatArrayOf(0.94f, 0.76f, 0.60f)
    private val DARKPANTS = floatArrayOf(0.15f, 0.17f, 0.25f)
    private val PINK = floatArrayOf(0.95f, 0.40f, 0.62f)
    private val FLASHCOL = floatArrayOf(1f, 0.9f, 0.4f)
    private val TRACER = floatArrayOf(1f, 0.95f, 0.5f)
    private val GUN = floatArrayOf(0.10f, 0.10f, 0.11f)
    private val CRATECOL = arrayOf(
        floatArrayOf(0.25f, 0.55f, 1f), floatArrayOf(1f, 0.6f, 0.15f), floatArrayOf(0.85f, 0.2f, 0.2f)
    )
    private val NEON = arrayOf(
        floatArrayOf(1f, 0.25f, 0.65f), floatArrayOf(0.2f, 0.9f, 1f),
        floatArrayOf(1f, 0.55f, 0.15f), floatArrayOf(0.7f, 0.4f, 1f)
    )
    private val BUILD = arrayOf(
        floatArrayOf(0.62f, 0.60f, 0.56f), floatArrayOf(0.55f, 0.45f, 0.40f),
        floatArrayOf(0.45f, 0.52f, 0.60f), floatArrayOf(0.70f, 0.66f, 0.55f),
        floatArrayOf(0.48f, 0.48f, 0.52f), floatArrayOf(0.60f, 0.38f, 0.34f),
        floatArrayOf(0.40f, 0.55f, 0.50f), floatArrayOf(0.75f, 0.75f, 0.72f)
    )
    // el protagonista: camiseta de malla negra, pantalon negro, pelo corto castano claro, reloj
    private val M_SHIRT = floatArrayOf(0.11f, 0.15f, 0.26f)
    private val M_SHIRT2 = floatArrayOf(0.20f, 0.21f, 0.15f)
    private val M_PANTS = floatArrayOf(0.16f, 0.16f, 0.09f)
    private val M_BOOT = floatArrayOf(0.27f, 0.16f, 0.08f)
    private val M_SKIN = floatArrayOf(0.90f, 0.72f, 0.60f)
    private val M_HAIR = floatArrayOf(0.12f, 0.08f, 0.06f)
    private val M_STUBBLE = floatArrayOf(0.40f, 0.30f, 0.26f)
    private val M_WATCH = floatArrayOf(0.80f, 0.82f, 0.85f)
    // Lucia: chaqueta de cuero negra, top oscuro, jeans ajustados, botines, pelo largo oscuro ondulado
    private val L_JEANS = floatArrayOf(0.10f, 0.09f, 0.08f)
    private val L_LEATHER = floatArrayOf(0.12f, 0.11f, 0.10f)
    private val L_TOP = floatArrayOf(0.22f, 0.22f, 0.19f)
    private val L_SKIN = floatArrayOf(0.80f, 0.58f, 0.44f)
    private val L_HAIR = floatArrayOf(0.12f, 0.07f, 0.05f)
    private val L_BOOT = floatArrayOf(0.05f, 0.05f, 0.05f)
    private val L_LIPS = floatArrayOf(0.72f, 0.10f, 0.16f)
    private val L_GOLD = floatArrayOf(0.95f, 0.78f, 0.25f)

    // ---------------- estado GL ----------------
    private var prog = 0
    private var skyProg = 0
    private var uVP = 0
    private var uModel = 0
    private var uColor = 0
    private var uFog = 0
    private var uCam = 0
    private var uEmit = 0
    private var uSpec = 0
    private var uShin = 0
    private var uPat = 0
    private var uAlpha = 0
    private var uFogMax = 0
    private var uSeed = 0
    private var aPos = 0
    private var aNorm = 0
    private var sFwd = 0
    private var sRight = 0
    private var sUp = 0
    private var sScale = 0
    private var sSun = 0
    private var aP = 0
    private lateinit var vbuf: FloatBuffer
    private lateinit var ibuf: ShortBuffer
    private lateinit var skyBuf: FloatBuffer

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val model = FloatArray(16)
    private var aspect = 1.7f
    private var last = 0L

    // camara
    private var eyeX = 0f
    private var eyeY = 0f
    private var eyeZ = 0f
    private var tgtX = 0f
    private var tgtY = 0f
    private var tgtZ = 0f

    // cache de uniformes
    private var lastCol: FloatArray? = null
    private var lastEmit = -1f
    private var lastSpec = -1f
    private var lastShin = -1f
    private var lastPat = -1f
    private var lastAlpha = -1f
    private var lastFogMax = -1f
    private var lastSeed = -999f

    // edificios por bloque
    private val bh = FloatArray(NB * NB)
    private val bw = FloatArray(NB * NB)
    private val bd = FloatArray(NB * NB)
    private val bc = Array(NB * NB) { floatArrayOf(0.5f, 0.5f, 0.5f) }

    // farolas visibles este fotograma (para dibujar su resplandor)
    private val lampX = FloatArray(160)
    private val lampZ = FloatArray(160)
    private var lampN = 0

    init {
        for (i in 0 until NB) for (j in 0 until NB) {
            val k = i * NB + j
            bh[k] = 14f + hash(j + 5, i + 9) * 56f
            bw[k] = (CELL - ROAD) - 6f - hash(i + 2, j + 7) * 6f
            bd[k] = (CELL - ROAD) - 6f - hash(j + 8, i + 1) * 6f
            bc[k] = BUILD[(hash(i + 3, j + 11) * BUILD.size).toInt() % BUILD.size]
        }
    }

    // ---------------- inicializacion ----------------
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        prog = link(VERT, FRAG)
        skyProg = link(SKY_VERT, SKY_FRAG)
        GLES20.glUseProgram(prog)
        uVP = GLES20.glGetUniformLocation(prog, "uVP")
        uModel = GLES20.glGetUniformLocation(prog, "uModel")
        uColor = GLES20.glGetUniformLocation(prog, "uColor")
        uFog = GLES20.glGetUniformLocation(prog, "uFog")
        uCam = GLES20.glGetUniformLocation(prog, "uCam")
        uEmit = GLES20.glGetUniformLocation(prog, "uEmit")
        uSpec = GLES20.glGetUniformLocation(prog, "uSpec")
        uShin = GLES20.glGetUniformLocation(prog, "uShin")
        uPat = GLES20.glGetUniformLocation(prog, "uPat")
        uAlpha = GLES20.glGetUniformLocation(prog, "uAlpha")
        uFogMax = GLES20.glGetUniformLocation(prog, "uFogMax")
        uSeed = GLES20.glGetUniformLocation(prog, "uSeed")
        aPos = GLES20.glGetAttribLocation(prog, "aPos")
        aNorm = GLES20.glGetAttribLocation(prog, "aNorm")
        sFwd = GLES20.glGetUniformLocation(skyProg, "uFwd")
        sRight = GLES20.glGetUniformLocation(skyProg, "uRight")
        sUp = GLES20.glGetUniformLocation(skyProg, "uUp")
        sScale = GLES20.glGetUniformLocation(skyProg, "uScale")
        sSun = GLES20.glGetUniformLocation(skyProg, "uSun")
        aP = GLES20.glGetAttribLocation(skyProg, "aP")
        buildCube()
        val tri = floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)
        skyBuf = ByteBuffer.allocateDirect(tri.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        skyBuf.put(tri)
        skyBuf.position(0)
        last = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = width.toFloat() / max(1, height).toFloat()
        Matrix.perspectiveM(proj, 0, 60f, aspect, 1.5f, 2400f)
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

    private fun link(v: String, f: String): Int {
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, v))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, f))
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) Log.e("Juego3D", "Link error: " + GLES20.glGetProgramInfoLog(p))
        return p
    }

    /** Cubo unitario centrado en el origen: 24 vertices (posicion + normal). */
    private fun buildCube() {
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

    // ---------------- primitivas ----------------
    private fun resetCache() {
        lastCol = null
        lastEmit = -1f
        lastSpec = -1f
        lastShin = -1f
        lastPat = -1f
        lastAlpha = -1f
        lastFogMax = -1f
        lastSeed = -999f
    }

    /** Material: brillo especular, dureza y si la fachada lleva ventanas. */
    private fun mat(spec: Float, shin: Float, pat: Float) {
        if (spec != lastSpec) { lastSpec = spec; GLES20.glUniform1f(uSpec, spec) }
        if (shin != lastShin) { lastShin = shin; GLES20.glUniform1f(uShin, shin) }
        if (pat != lastPat) { lastPat = pat; GLES20.glUniform1f(uPat, pat) }
    }

    private fun setAlpha(a: Float) { if (a != lastAlpha) { lastAlpha = a; GLES20.glUniform1f(uAlpha, a) } }
    private fun setFogMax(a: Float) { if (a != lastFogMax) { lastFogMax = a; GLES20.glUniform1f(uFogMax, a) } }
    private fun setSeed(a: Float) { if (a != lastSeed) { lastSeed = a; GLES20.glUniform1f(uSeed, a) } }

    private fun blendOn() {
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
    }

    private fun blendOff() {
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDepthMask(true)
        setAlpha(1f)
    }

    private fun box(
        cx: Float, cy: Float, cz: Float, sx: Float, sy: Float, sz: Float,
        yawDeg: Float, col: FloatArray, emit: Float
    ) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, cx, cy, cz)
        if (yawDeg != 0f) Matrix.rotateM(model, 0, yawDeg, 0f, 1f, 0f)
        Matrix.scaleM(model, 0, sx, sy, sz)
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0)
        if (col !== lastCol) { lastCol = col; GLES20.glUniform3f(uColor, col[0], col[1], col[2]) }
        if (emit != lastEmit) { lastEmit = emit; GLES20.glUniform1f(uEmit, emit) }
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 36, GLES20.GL_UNSIGNED_SHORT, ibuf)
    }

    /** Pieza en coordenadas locales de un objeto en (x,z) con rumbo a (lx adelante, lz derecha). */
    private fun lb(
        x: Float, z: Float, a: Float, lx: Float, ly: Float, lz: Float,
        sx: Float, sy: Float, sz: Float, col: FloatArray
    ) {
        val ca = cos(a)
        val sa = sin(a)
        box(x + ca * lx - sa * lz, ly, z + sa * lx + ca * lz, sx, sy, sz, -a * 57.29578f, col, 0f)
    }

    private fun part(
        c: Car, lx: Float, ly: Float, lz: Float, sx: Float, sy: Float, sz: Float,
        col: FloatArray, emit: Float
    ) {
        val ca = cos(c.a)
        val sa = sin(c.a)
        box(c.x + ca * lx - sa * lz, ly, c.z + sa * lx + ca * lz, sx, sy, sz, -c.a * 57.29578f, col, emit)
    }

    // ---------------- coches ----------------
    private fun drawCarBody(c: Car) {
        val police = c.kind == 1
        mat(0.9f, 56f, 0f)
        if (police || c.type == 0) {
            part(c, 0f, 0.65f, 0f, 4.4f, 0.75f, 2.0f, c.col, 0f)
            part(c, -0.3f, 1.3f, 0f, 2.3f, 0.65f, 1.8f, GLASS, 0f)
            part(c, -0.3f, 1.66f, 0f, 1.9f, 0.1f, 1.6f, c.col, 0f)
        } else if (c.type == 1) {
            // deportivo bajo y ancho, con capó en cuña, tomas laterales y alerón
            part(c, 0f, 0.42f, 0f, 4.6f, 0.38f, 2.0f, c.col, 0f)
            part(c, 1.4f, 0.72f, 0f, 1.7f, 0.22f, 1.9f, c.col, 0f)
            part(c, -1.5f, 0.78f, 0f, 1.5f, 0.30f, 1.95f, c.col, 0f)
            part(c, -0.15f, 0.92f, 0f, 1.7f, 0.42f, 1.7f, GLASS, 0f)
            part(c, -0.2f, 1.15f, 0f, 1.35f, 0.07f, 1.5f, c.col, 0f)
            part(c, -0.3f, 0.62f, 1.0f, 1.2f, 0.18f, 0.06f, BLACK, 0f)
            part(c, -0.3f, 0.62f, -1.0f, 1.2f, 0.18f, 0.06f, BLACK, 0f)
            part(c, 2.28f, 0.38f, 0f, 0.12f, 0.2f, 1.2f, BLACK, 0f)
            part(c, -2.15f, 1.08f, 0f, 0.4f, 0.07f, 2.0f, BLACK, 0f)
            part(c, -2.1f, 0.88f, 0.7f, 0.12f, 0.32f, 0.12f, BLACK, 0f)
            part(c, -2.1f, 0.88f, -0.7f, 0.12f, 0.32f, 0.12f, BLACK, 0f)
        } else {
            part(c, 0f, 0.7f, 0f, 4.8f, 0.8f, 2.1f, c.col, 0f)
            part(c, 0.7f, 1.45f, 0f, 1.8f, 0.75f, 1.9f, GLASS, 0f)
            part(c, 0.7f, 1.86f, 0f, 1.6f, 0.1f, 1.8f, c.col, 0f)
            part(c, -1.4f, 1.3f, 1.0f, 2.0f, 0.5f, 0.12f, c.col, 0f)
            part(c, -1.4f, 1.3f, -1.0f, 2.0f, 0.5f, 0.12f, c.col, 0f)
            part(c, -2.4f, 1.3f, 0f, 0.12f, 0.5f, 2.0f, c.col, 0f)
        }
        mat(0.1f, 8f, 0f)
        for (sx in floatArrayOf(1.4f, -1.4f)) for (sz in floatArrayOf(1.0f, -1.0f)) {
            part(c, sx, 0.45f, sz, 0.9f, 0.9f, 0.35f, BLACK, 0f)
        }
        part(c, 2.2f, 0.75f, 0.7f, 0.1f, 0.25f, 0.4f, HEADLIGHT, 1f)
        part(c, 2.2f, 0.75f, -0.7f, 0.1f, 0.25f, 0.4f, HEADLIGHT, 1f)
        part(c, -2.2f, 0.75f, 0.7f, 0.1f, 0.2f, 0.4f, TAILLIGHT, 1f)
        part(c, -2.2f, 0.75f, -0.7f, 0.1f, 0.2f, 0.4f, TAILLIGHT, 1f)
        if (police) {
            part(c, 0f, 0.72f, 0f, 4.42f, 0.18f, 2.02f, BLACK, 0f)
            val f = (game.time * 8f).toInt() % 2 == 0
            part(c, -0.3f, 1.8f, 0.5f, 0.4f, 0.25f, 0.6f, if (f) RED else BLUE, 1f)
            part(c, -0.3f, 1.8f, -0.5f, 0.4f, 0.25f, 0.6f, if (f) BLUE else RED, 1f)
        }
    }

    // ---------------- personajes ----------------
    /** El protagonista (a pie). spd: velocidad para animar las piernas; wep: arma en mano (-1 ninguna). */
    private fun drawMan(x: Float, z: Float, a: Float, ph: Float, spd: Float, wep: Int, flash: Boolean) {
        val sw = sin(ph) * clampF(spd / 3f, 0f, 1f)
        val l = 0.26f * sw
        val ar = 0.22f * sw
        mat(0.12f, 12f, 0f)
        // piernas y botas
        lb(x, z, a, l, 0.45f, 0.17f, 0.25f, 0.9f, 0.26f, M_PANTS)
        lb(x, z, a, -l, 0.45f, -0.17f, 0.25f, 0.9f, 0.26f, M_PANTS)
        lb(x, z, a, l + 0.05f, 0.07f, 0.17f, 0.36f, 0.14f, 0.28f, M_BOOT)
        lb(x, z, a, -l + 0.05f, 0.07f, -0.17f, 0.36f, 0.14f, 0.28f, M_BOOT)
        // torso ancho de malla, hombros fuertes
        lb(x, z, a, 0f, 1.27f, 0f, 0.34f, 0.72f, 0.62f, M_SHIRT)
        lb(x, z, a, 0.17f, 1.36f, 0f, 0.03f, 0.46f, 0.50f, M_SHIRT2)
        lb(x, z, a, 0f, 1.58f, 0f, 0.30f, 0.18f, 0.88f, M_SHIRT)
        // brazos: manga y antebrazo, con reloj en la muñeca izquierda
        lb(x, z, a, -ar, 1.42f, 0.5f, 0.22f, 0.36f, 0.22f, M_SHIRT)
        lb(x, z, a, ar, 1.42f, -0.5f, 0.22f, 0.36f, 0.22f, M_SHIRT)
        lb(x, z, a, -ar, 1.03f, 0.5f, 0.18f, 0.42f, 0.19f, M_SKIN)
        lb(x, z, a, ar, 1.03f, -0.5f, 0.18f, 0.42f, 0.19f, M_SKIN)
        lb(x, z, a, ar, 0.9f, -0.5f, 0.21f, 0.07f, 0.21f, M_WATCH)
        // cabeza: cuello, cara con barba corta y pelo castaño claro
        lb(x, z, a, 0f, 1.76f, 0f, 0.14f, 0.12f, 0.16f, M_SKIN)
        lb(x, z, a, 0f, 1.95f, 0f, 0.26f, 0.30f, 0.26f, M_SKIN)
        lb(x, z, a, 0.13f, 1.87f, 0f, 0.02f, 0.10f, 0.20f, M_STUBBLE)
        lb(x, z, a, 0f, 2.13f, 0f, 0.30f, 0.10f, 0.30f, M_HAIR)
        lb(x, z, a, -0.04f, 2.02f, 0f, 0.20f, 0.14f, 0.30f, M_HAIR)
        if (wep >= 0) {
            val len = if (wep == 0) 0.6f else if (wep == 1) 0.95f else 1.25f
            val off = 0.4f + len / 2f
            lb(x, z, a, off, 1.2f, 0.5f, len, 0.17f, 0.17f, GUN)
            if (flash) lb(x, z, a, off + len / 2f + 0.2f, 1.2f, 0.5f, 0.35f, 0.35f, 0.35f, FLASHCOL)
        }
    }

    /** Lucia: chaqueta de cuero, top oscuro, jeans ajustados, botines y pelo largo ondulado. */
    private fun drawLucia(x: Float, z: Float, a: Float, ph: Float, moving: Boolean, t: Float) {
        val sw = if (moving) sin(ph) else 0f
        val l = 0.24f * sw
        val ar = 0.20f * sw
        mat(0.12f, 12f, 0f)
        // piernas con jeans ajustados y botines
        lb(x, z, a, l, 0.48f, 0.12f, 0.22f, 0.82f, 0.22f, L_JEANS)
        lb(x, z, a, -l, 0.48f, -0.12f, 0.22f, 0.82f, 0.22f, L_JEANS)
        lb(x, z, a, l + 0.04f, 0.11f, 0.12f, 0.30f, 0.22f, 0.24f, L_BOOT)
        lb(x, z, a, -l + 0.04f, 0.11f, -0.12f, 0.30f, 0.22f, 0.24f, L_BOOT)
        lb(x, z, a, 0f, 0.98f, 0f, 0.30f, 0.22f, 0.50f, L_JEANS)
        // top oscuro con escote en V
        lb(x, z, a, 0f, 1.26f, 0f, 0.26f, 0.40f, 0.40f, L_TOP)
        lb(x, z, a, 0.14f, 1.38f, 0f, 0.03f, 0.18f, 0.16f, L_SKIN)
        // chaqueta de cuero abierta, brillante
        mat(0.55f, 24f, 0f)
        lb(x, z, a, 0.02f, 1.24f, 0.27f, 0.30f, 0.52f, 0.14f, L_LEATHER)
        lb(x, z, a, 0.02f, 1.24f, -0.27f, 0.30f, 0.52f, 0.14f, L_LEATHER)
        lb(x, z, a, -0.03f, 1.52f, 0f, 0.30f, 0.12f, 0.58f, L_LEATHER)
        lb(x, z, a, -ar, 1.2f, 0.38f, 0.17f, 0.62f, 0.17f, L_LEATHER)
        lb(x, z, a, ar, 1.2f, -0.38f, 0.17f, 0.62f, 0.17f, L_LEATHER)
        mat(0.12f, 12f, 0f)
        lb(x, z, a, -ar, 0.82f, 0.38f, 0.12f, 0.12f, 0.12f, L_SKIN)
        lb(x, z, a, ar, 0.82f, -0.38f, 0.12f, 0.12f, 0.12f, L_SKIN)
        // cabeza: cuello, cara, labios rojos, ojos, pelo largo oscuro y aretes dorados
        lb(x, z, a, 0f, 1.62f, 0f, 0.11f, 0.10f, 0.13f, L_SKIN)
        lb(x, z, a, 0f, 1.78f, 0f, 0.24f, 0.28f, 0.24f, L_SKIN)
        lb(x, z, a, 0.12f, 1.70f, 0f, 0.03f, 0.04f, 0.12f, L_LIPS)
        lb(x, z, a, 0.12f, 1.82f, 0.07f, 0.02f, 0.03f, 0.04f, GUN)
        lb(x, z, a, 0.12f, 1.82f, -0.07f, 0.02f, 0.03f, 0.04f, GUN)
        lb(x, z, a, 0f, 1.96f, 0f, 0.28f, 0.13f, 0.28f, L_HAIR)
        lb(x, z, a, -0.16f, 1.52f, 0f, 0.14f, 0.72f, 0.34f, L_HAIR)
        lb(x, z, a, 0f, 1.64f, 0.15f, 0.22f, 0.50f, 0.06f, L_HAIR)
        lb(x, z, a, 0f, 1.64f, -0.15f, 0.22f, 0.50f, 0.06f, L_HAIR)
        lb(x, z, a, 0f, 1.72f, 0.13f, 0.06f, 0.06f, 0.06f, L_GOLD)
        lb(x, z, a, 0f, 1.72f, -0.13f, 0.06f, 0.06f, 0.06f, L_GOLD)
        // corazon rosa flotante para encontrarla
        box(x, 2.75f + sin(t * 3f) * 0.1f, z, 0.32f, 0.32f, 0.32f, t * 90f, PINK, 1f)
    }

    private fun drawPed(q: Ped) {
        val yaw = -q.a * 57.29578f
        val sw = sin(q.walkT) * 0.2f
        mat(0.05f, 8f, 0f)
        lb(q.x, q.z, q.a, sw, 0.35f, 0.12f, 0.24f, 0.7f, 0.22f, DARKPANTS)
        lb(q.x, q.z, q.a, -sw, 0.35f, -0.12f, 0.24f, 0.7f, 0.22f, DARKPANTS)
        box(q.x, 1.0f, q.z, 0.4f, 0.7f, 0.6f, yaw, q.col, 0f)
        box(q.x, 1.6f, q.z, 0.4f, 0.4f, 0.4f, yaw, SKIN, 0f)
    }

    // ---------------- escenario ----------------
    private fun drawStatic(t: Float) {
        // skyline y colinas lejanas, difuminadas por la bruma
        setFogMax(0.82f)
        mat(0f, 1f, 0f)
        for (k in 0 until 44) {
            val ang = k * TWO_PI / 44f + 0.07f
            val rad = 640f + hash(k, 3) * 160f
            val h = 70f + hash(k, 5) * 190f
            val w = 36f + hash(k, 9) * 50f
            box(240f + cos(ang) * rad, h / 2f, 240f + sin(ang) * rad, w, h, w, -ang * 57.29578f, SKYLINE, 0f)
        }
        for (k in 0 until 12) {
            val ang = k * TWO_PI / 12f + 0.3f
            val rad = 980f + hash(k, 21) * 120f
            val h = 60f + hash(k, 23) * 90f
            box(240f + cos(ang) * rad, h / 2f - 4f, 240f + sin(ang) * rad, 320f, h, 260f, -ang * 57.29578f, HILL, 0f)
        }
        setFogMax(1f)

        // suelo y calles (asfalto con brillo de calle humeda)
        box(WORLD / 2f, -0.6f, WORLD / 2f, 3000f, 1f, 3000f, 0f, GRASS, 0f)
        mat(0.40f, 26f, 0f)
        box(WORLD / 2f, -0.05f, WORLD / 2f, WORLD + ROAD, 0.1f, WORLD + ROAD, 0f, ASPHALT, 0f)
        mat(0f, 1f, 0f)
        val seg = CELL - ROAD - 2f
        for (a in 0..NB) {
            for (b in 0 until NB) {
                val mid = b * CELL + CELL / 2f
                if (abs(mid - eyeX) < 260f && abs(a * CELL - eyeZ) < 260f)
                    box(mid, 0.06f, a * CELL, seg, 0.12f, 0.35f, 0f, LINE, 0f)
                if (abs(a * CELL - eyeX) < 260f && abs(mid - eyeZ) < 260f)
                    box(a * CELL, 0.06f, mid, 0.35f, 0.12f, seg, 0f, LINE, 0f)
            }
        }

        // farolas
        lampN = 0
        for (a in 0..NB) {
            val line = a * CELL
            for (b in 0 until NB * 2) {
                val pos = b * 30f + 15f
                val off = if ((a + b) % 2 == 0) 7.2f else -7.2f
                val sg = if (off > 0f) -1f else 1f
                // calle paralela al eje x
                if (abs(pos - eyeX) < 150f && abs(line + off - eyeZ) < 150f) lamp(pos, line + off, 0f, sg)
                // calle paralela al eje z
                if (abs(line + off - eyeX) < 150f && abs(pos - eyeZ) < 150f) lamp(line + off, pos, sg, 0f)
            }
        }

        // bloques
        val bs = CELL - ROAD
        for (i in 0 until NB) for (j in 0 until NB) {
            val cx = i * CELL + ROAD / 2f + bs / 2f
            val cz = j * CELL + ROAD / 2f + bs / 2f
            if (abs(cx - eyeX) > 290f || abs(cz - eyeZ) > 290f) continue
            val near = abs(cx - eyeX) < 120f && abs(cz - eyeZ) < 120f
            mat(0f, 1f, 0f)
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
                // cuerpo con ventanas encendidas / reflejos (se calculan en el shader)
                setSeed(k * 3.17f + 1.3f)
                mat(0.12f, 16f, 1f)
                box(cx, 0.3f + h / 2f, cz, bw[k], h, bd[k], 0f, bc[k], 0f)
                mat(0f, 1f, 0f)
                box(cx, 0.3f + h + 0.25f, cz, bw[k] + 0.6f, 0.5f, bd[k] + 0.6f, 0f, ROOF, 0f)
                // letrero de neon en una fachada
                if (hash(i + 13, j + 5) < 0.55f) {
                    val side = (hash(i + 1, j + 17) * 4f).toInt() % 4
                    val col = NEON[(hash(i + 7, j + 3) * 4f).toInt() % 4]
                    val y = 6f + hash(i + 9, j + 9) * 6f
                    val sh = (hash(i + 4, j + 8) - 0.5f) * 8f
                    when (side) {
                        0 -> box(cx + bw[k] / 2f + 0.3f, y, cz + sh, 0.35f, 2.2f, 6.5f, 0f, col, 1f)
                        1 -> box(cx - bw[k] / 2f - 0.3f, y, cz + sh, 0.35f, 2.2f, 6.5f, 0f, col, 1f)
                        2 -> box(cx + sh, y, cz + bd[k] / 2f + 0.3f, 6.5f, 2.2f, 0.35f, 0f, col, 1f)
                        else -> box(cx + sh, y, cz - bd[k] / 2f - 0.3f, 6.5f, 2.2f, 0.35f, 0f, col, 1f)
                    }
                }
                // palmeras en las esquinas de la acera
                if (near) {
                    palm(cx - bs / 2f + 2.2f, cz - bs / 2f + 2.2f)
                    palm(cx + bs / 2f - 2.2f, cz + bs / 2f - 2.2f)
                }
            }
        }
    }

    private fun lamp(x: Float, z: Float, dirX: Float, dirZ: Float) {
        mat(0.2f, 12f, 0f)
        box(x, 3.6f, z, 0.22f, 7.2f, 0.22f, 0f, POLE, 0f)
        box(x + dirX * 0.9f, 7.15f, z + dirZ * 0.9f, if (dirX != 0f) 1.8f else 0.18f, 0.18f, if (dirZ != 0f) 1.8f else 0.18f, 0f, POLE, 0f)
        box(x + dirX * 1.7f, 6.98f, z + dirZ * 1.7f, 0.7f, 0.16f, 0.7f, 0f, LAMP, 1f)
        if (lampN < lampX.size) {
            lampX[lampN] = x + dirX * 1.7f
            lampZ[lampN] = z + dirZ * 1.7f
            lampN++
        }
    }

    private fun palm(x: Float, z: Float) {
        if (abs(x - eyeX) > 100f || abs(z - eyeZ) > 100f) return
        mat(0f, 1f, 0f)
        box(x, 3.8f, z, 0.38f, 7f, 0.38f, 0f, TRUNK, 0f)
        for (k in 0 until 4) box(x, 7.4f, z, 4.4f, 0.14f, 0.7f, k * 45f, PALM, 0f)
        box(x, 7.7f, z, 0.9f, 0.7f, 0.9f, 0f, PALM, 0f)
    }

    /** Sombras suaves de edificios, resplandor de farolas, sombras de coches y haces de faros. */
    private fun drawBlended() {
        blendOn()
        mat(0f, 1f, 0f)
        setAlpha(0.30f)
        val bs = CELL - ROAD
        for (i in 0 until NB) for (j in 0 until NB) {
            if (isPark(i, j)) continue
            val cx = i * CELL + ROAD / 2f + bs / 2f
            val cz = j * CELL + ROAD / 2f + bs / 2f
            if (abs(cx - eyeX) > 220f || abs(cz - eyeZ) > 220f) continue
            val k = i * NB + j
            val len = min(bh[k] * 0.8f, 36f)
            box(cx, 0.45f, cz + len / 2f, bw[k], 0.02f, bd[k] + len, 0f, SHADOWC, 0f)
        }
        // resplandor calido bajo las farolas
        setAlpha(0.20f)
        for (k in 0 until lampN) box(lampX[k], 0.1f, lampZ[k], 9f, 0.02f, 9f, 0f, BEAM, 1f)
        // sombras bajo coches y personajes
        setAlpha(0.35f)
        for (c in game.cars) {
            if (abs(c.x - eyeX) < 160f && abs(c.z - eyeZ) < 160f) part(c, 0f, 0.05f, 0f, 4.8f, 0.02f, 2.4f, SHADOWC, 0f)
        }
        // haces de los faros
        setAlpha(0.15f)
        for (c in game.cars) {
            if (c.kind != 3 && abs(c.x - eyeX) < 110f && abs(c.z - eyeZ) < 110f) part(c, 7f, 0.08f, 0f, 9f, 0.02f, 3.8f, BEAM, 1f)
        }
        setAlpha(0.35f)
        val g = game
        if (g.intro || g.pcar == null) box(g.player.x, 0.05f, g.player.z, 1.0f, 0.02f, 1.0f, 0f, SHADOWC, 0f)
        for (q in g.peds) if (abs(q.x - eyeX) < 90f && abs(q.z - eyeZ) < 90f) box(q.x, 0.05f, q.z, 0.8f, 0.02f, 0.8f, 0f, SHADOWC, 0f)
        blendOff()
    }

    // ---------------- camara y fotograma ----------------
    private fun setupCamera() {
        val g = game
        val p = g.player
        if (g.intro) {
            val c = g.pcar
            val bx = c?.x ?: p.x
            val bz = (c?.z ?: p.z) + 4.8f
            val ang = PIF / 2f + 0.55f * sin(g.introT * 0.35f)
            eyeX = bx + cos(ang) * 6.2f
            eyeZ = bz + sin(ang) * 6.2f
            eyeY = 1.5f + 0.25f * sin(g.introT * 0.5f)
            tgtX = bx
            tgtY = 1.25f
            tgtZ = bz
            return
        }
        val onFoot = g.pcar == null
        val yaw = g.camYaw
        val pitch = g.camPitch
        val cdx = cos(yaw)
        val cdz = sin(yaw)
        val d = if (onFoot) 5.2f else 9f + abs(p.speed) * 0.12f
        val baseH = if (onFoot) 1.9f else 2.6f
        val horiz = d * cos(pitch)
        val vert = baseH + d * sin(pitch)
        var hd = horiz
        for (k in 0 until 6) {
            if (solid(p.x - cdx * hd, p.z - cdz * hd)) hd *= 0.72f else break
        }
        eyeX = p.x - cdx * hd
        eyeZ = p.z - cdz * hd
        eyeY = max(1.0f, vert * (0.5f + 0.5f * hd / max(0.01f, horiz)))
        tgtX = p.x + cdx * 2f
        tgtZ = p.z + cdz * 2f
        tgtY = if (onFoot) 1.5f else 1.2f
    }

    private fun drawSky() {
        var fx = tgtX - eyeX
        var fy = tgtY - eyeY
        var fz = tgtZ - eyeZ
        val fl = sqrt(fx * fx + fy * fy + fz * fz)
        fx /= fl; fy /= fl; fz /= fl
        var rx = -fz
        var rz = fx
        val rl = max(0.0001f, hypot(rx, rz))
        rx /= rl; rz /= rl
        val ux = -rz * fy
        val uy = rz * fx - rx * fz
        val uz = rx * fy
        val th = 0.57735f
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(skyProg)
        GLES20.glUniform3f(sFwd, fx, fy, fz)
        GLES20.glUniform3f(sRight, rx, 0f, rz)
        GLES20.glUniform3f(sUp, ux, uy, uz)
        GLES20.glUniform2f(sScale, th * aspect, th)
        GLES20.glUniform3f(sSun, -0.45f, 0.45f, -0.77f)
        skyBuf.position(0)
        GLES20.glVertexAttribPointer(aP, 2, GLES20.GL_FLOAT, false, 0, skyBuf)
        GLES20.glEnableVertexAttribArray(aP)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
        GLES20.glDisableVertexAttribArray(aP)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = min(0.05f, (now - last) / 1_000_000_000f)
        last = now
        game.update(dt)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val g = game
        val t = g.time
        setupCamera()
        Matrix.setLookAtM(view, 0, eyeX, eyeY, eyeZ, tgtX, tgtY, tgtZ, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        drawSky()

        GLES20.glUseProgram(prog)
        resetCache()
        GLES20.glUniformMatrix4fv(uVP, 1, false, vp, 0)
        GLES20.glUniform3f(uFog, FOG[0], FOG[1], FOG[2])
        GLES20.glUniform3f(uCam, eyeX, eyeY, eyeZ)
        setAlpha(1f)
        setFogMax(1f)
        setSeed(0f)
        vbuf.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, vbuf)
        GLES20.glEnableVertexAttribArray(aPos)
        vbuf.position(3)
        GLES20.glVertexAttribPointer(aNorm, 3, GLES20.GL_FLOAT, false, 24, vbuf)
        GLES20.glEnableVertexAttribArray(aNorm)

        drawStatic(t)
        drawBlended()

        val p = g.player
        // punto de mision: pilar luminoso + estrella giratoria en el suelo
        mat(0f, 1f, 0f)
        box(g.missionX, 25f, g.missionZ, 1.6f, 50f, 1.6f, 0f, YELLOW, 1f)
        box(g.missionX, 0.14f, g.missionZ, 12f, 0.2f, 12f, t * 60f, YELLOW, 1f)
        box(g.missionX, 0.16f, g.missionZ, 12f, 0.2f, 12f, t * 60f + 45f, YELLOW, 1f)

        for (pk in g.pickups) {
            if (abs(pk.x - p.x) < 200f && abs(pk.z - p.z) < 200f)
                box(pk.x, 1.1f, pk.z, 0.9f, 0.9f, 0.3f, t * 150f, GREEN, 1f)
        }
        for (cr in g.crates) {
            if (abs(cr.x - p.x) < 200f && abs(cr.z - p.z) < 200f) {
                box(cr.x, 0.9f, cr.z, 0.9f, 0.9f, 0.9f, t * 80f, CRATECOL[cr.w], 1f)
                box(cr.x, 1.7f, cr.z, 0.3f, 0.3f, 0.3f, -t * 120f, FLASHCOL, 1f)
            }
        }
        for (c in g.cars) {
            if (abs(c.x - eyeX) < 260f && abs(c.z - eyeZ) < 260f) drawCarBody(c)
        }
        for (q in g.peds) {
            if (abs(q.x - eyeX) < 200f && abs(q.z - eyeZ) < 200f) drawPed(q)
        }
        if (g.intro) {
            // la pareja protagonista, de pie junto al coche
            val c = g.pcar
            val bx = c?.x ?: p.x
            val bz = (c?.z ?: p.z) + 4.8f
            drawMan(bx + 0.55f, bz, PIF / 2f, 0f, 0f, -1, false)
            drawLucia(bx - 0.5f, bz, PIF / 2f, 0f, false, t)
        } else {
            if (g.pcar == null && g.wastedT <= 0f) drawMan(p.x, p.z, p.a, g.pWalk, abs(p.speed), g.curW, g.flash > 0f)
            if (!g.lucia.inCar && g.wastedT <= 0f) drawLucia(g.lucia.x, g.lucia.z, g.lucia.a, g.lucia.walkT, g.lucia.moving, t)
        }
        mat(0f, 1f, 0f)
        for (tr in g.tracers) {
            val dx = tr.x2 - tr.x1
            val dz = tr.z2 - tr.z1
            val len = hypot(dx, dz)
            if (len < 0.1f) continue
            val ang = atan2(dz, dx)
            box((tr.x1 + tr.x2) / 2f, 1.2f, (tr.z1 + tr.z2) / 2f, len, 0.07f, 0.07f, -ang * 57.29578f, TRACER, 1f)
        }
        for (q in g.parts) {
            box(q.x, q.y, q.z, q.size, q.size, q.size, 0f, q.col, 1f)
        }
    }
}
