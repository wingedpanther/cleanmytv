package io.github.wingedpanther.cleanmytv.home

import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.SystemClock
import android.service.dreams.DreamService
import android.util.Log
import android.view.View
import android.view.WindowManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/**
 * A fireplace drawn live: flames from warped noise, round logs with embers in their cracks, sparks, and
 * the fire's light on a brick hearth. OpenGL ES 2, so it runs on Android 9 boxes. It draws at 960x540
 * and drops to 640x360 if the box can't keep up; the TV scales it to the screen.
 */
class FireView(ctx: Context) : GLSurfaceView(ctx) {
    private val renderer = FireRenderer()

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        holder.setFixedSize(960, 540)
        renderer.onSlow = { post { holder.setFixedSize(640, 360) } }
    }
}

private class FireRenderer : GLSurfaceView.Renderer {
    var onSlow: () -> Unit = {}
    private var prog = 0
    private var w = 1
    private var h = 1
    private val start = SystemClock.elapsedRealtime()
    private var frames = 0
    private var since = 0L
    private var slowed = false
    private val quad = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer()
        .apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, compile(GLES20.GL_VERTEX_SHADER, "attribute vec2 a; void main(){ gl_Position = vec4(a, 0.0, 1.0); }"))
        GLES20.glAttachShader(prog, compile(GLES20.GL_FRAGMENT_SHADER, FIRE_SHADER))
        GLES20.glLinkProgram(prog)
        GLES20.glUseProgram(prog)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        w = width; h = height
        GLES20.glViewport(0, 0, w, h)
        since = SystemClock.elapsedRealtime(); frames = 0
    }

    override fun onDrawFrame(gl: GL10?) {
        val cfg = ConfigStore.config.value
        // Seconds since the scene opened, wrapped so the shader's numbers stay small on mediump-only GPUs.
        val t = ((SystemClock.elapsedRealtime() - start) % 600_000L) / 1000f
        GLES20.glUseProgram(prog)
        val a = GLES20.glGetAttribLocation(prog, "a")
        GLES20.glEnableVertexAttribArray(a)
        GLES20.glVertexAttribPointer(a, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(prog, "res"), w.toFloat(), h.toFloat())
        GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "t"), t)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "hgt"), cfg.fireHeight / 100f)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "warm"), cfg.fireWarmth / 100f)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "sparks"), if (cfg.fireSparks) 1f else 0f)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "hearth"), if (cfg.fireScene == "hearth") 1f else 0f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        // Under 22 frames a second after three seconds at full size: draw smaller from then on.
        frames++
        val now = SystemClock.elapsedRealtime()
        if (!slowed && now - since > 3000) {
            if (frames * 1000f / (now - since) < 22f && w > 700) { slowed = true; Log.i(TAG, "fireplace: ${frames * 1000 / (now - since)} fps at ${w}x$h, drawing smaller"); onSlow() }
            since = now; frames = 0
        }
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) Log.w(TAG, "fireplace shader: ${GLES20.glGetShaderInfoLog(s)}")
        return s
    }
}

/**
 * The crackle, made live: brown noise through a low-pass for the roar, a faint hiss, and short bursts of
 * filtered noise at random for the crackles, in little clusters, with now and then a bigger pop.
 */
class FireSound(private val volume: () -> Float, private val height: () -> Float) {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ play() }, "fire-sound").apply { start() }
    }

    fun stop() { running = false; thread?.join(500); thread = null }

    private class Burst(var left: Int, val decay: Float, var env: Float, val bright: Boolean) {
        var x1 = 0f; var y1 = 0f; var lp = 0f
    }

    private fun play() {
        val rate = 22050
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(min, 4096 * 2))
                .build()
        }.getOrElse { Log.w(TAG, "fireplace sound: $it"); return }
        track.play()
        val buf = ShortArray(1024)
        val rnd = Random(SystemClock.elapsedRealtime())
        var brown = 0f; var roarLp = 0f; var lastW = 0f; var roarGain = 0.45f; var roarTarget = 0.45f
        val bursts = ArrayList<Burst>()
        var n = 0
        while (running) {
            val vol = volume()
            val h = height()
            // About ten crackles a second at the default height, more for a bigger fire.
            val perSample = (6f + 6f * h) / rate
            for (i in buf.indices) {
                val w = rnd.nextFloat() * 2f - 1f
                brown = (brown + 0.02f * w) / 1.02f
                roarLp += 0.105f * (brown * 3.5f - roarLp)
                roarGain += (roarTarget - roarGain) * 0.0002f
                var s = roarLp * roarGain + (w - lastW) * 0.006f
                lastW = w
                if (rnd.nextFloat() < perSample) {
                    val cluster = if (rnd.nextFloat() < 0.25f) 2 + rnd.nextInt(4) else 1
                    repeat(cluster) {
                        val big = rnd.nextFloat() < 0.04f
                        val len = if (big) 0.03f + rnd.nextFloat() * 0.06f else 0.003f + rnd.nextFloat() * 0.012f
                        val samples = (len * rate).toInt().coerceAtLeast(8)
                        // Decay to a thousandth over the burst's length.
                        bursts += Burst(samples + it * rnd.nextInt(200, 700), exp(ln(0.001) / samples).toFloat(),
                            (if (big) 0.5f else 0.12f + rnd.nextFloat() * 0.25f) * (0.4f + rnd.nextFloat() * 0.6f), !big)
                    }
                }
                val it = bursts.iterator()
                while (it.hasNext()) {
                    val b = it.next()
                    if (b.left-- <= 0) { it.remove(); continue }
                    val x = rnd.nextFloat() * 2f - 1f
                    // Ticks: a high-pass for a dry click. Pops: lower and rounder.
                    b.y1 = x - b.x1 + (if (b.bright) 0.55f else 0.92f) * b.y1
                    b.x1 = x
                    val v = if (b.bright) b.y1 else { b.lp += 0.25f * (b.y1 - b.lp); b.lp * 2.2f }
                    s += v * b.env
                    b.env *= b.decay
                }
                buf[i] = (s * vol * 26000f).toInt().coerceIn(-32767, 32767).toShort()
            }
            // The roar breathes with the flames, a new level every quarter second or so.
            if (++n % 5 == 0) roarTarget = 0.35f + rnd.nextFloat() * 0.25f * h
            track.write(buf, 0, buf.size)
        }
        runCatching { track.stop() }
        track.release()
    }
}

/** The fireplace full screen, from the Fireplace quick-settings icon or a remote button. Back closes it. */
class FireplaceActivity : Activity() {
    private var view: FireView? = null
    private val sound = FireSound({ ConfigStore.config.value.fireVolume / 100f }, { ConfigStore.config.value.fireHeight / 100f })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigStore.load(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = FireView(this).also { setContentView(it) }
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    override fun onResume() { super.onResume(); view?.onResume(); if (ConfigStore.config.value.fireSound) sound.start() }
    override fun onPause() { sound.stop(); view?.onPause(); super.onPause() }
}

/** The fireplace as the TV's screensaver, listed as "CleanMyTV Fireplace". Silent unless the owner turned sound on for it. */
class FireplaceDream : DreamService() {
    private var view: FireView? = null
    private val sound = FireSound({ ConfigStore.config.value.fireVolume / 100f }, { ConfigStore.config.value.fireHeight / 100f })

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ConfigStore.load(this)
        isInteractive = false
        isFullscreen = true
        view = FireView(this).also { setContentView(it) }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        view?.onResume()
        if (ConfigStore.config.value.fireSaverSound) sound.start()
    }

    override fun onDreamingStopped() {
        sound.stop()
        view?.onPause()
        super.onDreamingStopped()
    }
}

private const val FIRE_SHADER = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform vec2 res;
uniform float t;
uniform float hgt;
uniform float warm;
uniform float sparks;
uniform float hearth;

float hash(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float noise(vec2 p) {
  vec2 i = floor(p), f = fract(p);
  vec2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}
float fbm(vec2 p) {
  float v = 0.0, a = 0.5;
  for (int i = 0; i < 5; i++) { v += a * noise(p); p = p * 2.03 + vec2(1.7, 9.2); a *= 0.5; }
  return v;
}
vec3 fireColour(float k) {
  k = clamp(k, 0.0, 1.0);
  vec3 c = vec3(1.0, 0.25 + warm * 0.08, 0.04) * smoothstep(0.0, 0.35, k);
  c = mix(c, vec3(1.0, 0.55 + warm * 0.1, 0.12), smoothstep(0.3, 0.65, k));
  c = mix(c, vec3(1.0, 0.86, 0.55 - warm * 0.2), smoothstep(0.6, 0.9, k));
  c = mix(c, vec3(1.0, 0.97, 0.88), smoothstep(0.88, 1.0, k));
  return c;
}
float logDist(vec2 p, vec2 a, vec2 b, float r) {
  vec2 pa = p - a, ba = b - a;
  float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
  return length(pa - ba * h) - r;
}
void main() {
  vec2 uv = gl_FragCoord.xy / res;
  float aspect = res.x / res.y;
  vec2 p = vec2((uv.x - 0.5) * aspect, uv.y);
  float boxW = hearth > 0.5 ? 0.62 : 2.0;
  vec2 bp = vec2(p.x / boxW, (uv.y - 0.12) / 0.62);
  float inBox = hearth > 0.5 ? step(abs(bp.x), 1.0) * step(0.0, bp.y) * step(bp.y, 1.0) : 1.0;
  float flick = 0.82 + 0.18 * fbm(vec2(t * 1.7, 3.1));

  float spread = hearth > 0.5 ? 0.46 : 0.5;
  float y = uv.y - 0.19;
  float colH = 0.34 * hgt * (0.45 + 0.9 * fbm(vec2(p.x * 2.6 + 3.0, t * 0.45)));
  vec2 q = vec2(p.x * 3.4, y * 2.1 - t * 2.7);
  vec2 warp = vec2(fbm(q * 0.9 + vec2(0.0, t * 0.4)), fbm(q * 0.9 + vec2(4.1, t * 0.5)));
  float n = fbm(q * 1.6 + warp * 1.6);
  float shape = 1.0 - y / colH;
  float flame = shape * 1.15 + (n - 0.5) * 1.5 * (1.0 - shape * 0.35);
  flame *= smoothstep(spread, spread * 0.35, abs(p.x)) * smoothstep(-0.05, 0.03, y);
  flame = clamp(flame, 0.0, 1.0) * flick;

  float d1 = logDist(p, vec2(-0.36, 0.17), vec2(0.32, 0.2), 0.045);
  float d2 = logDist(p, vec2(-0.3, 0.215), vec2(0.38, 0.165), 0.04);
  float d3 = logDist(p, vec2(-0.12, 0.25), vec2(0.18, 0.27), 0.03);
  float d = min(min(d1, d2), d3);
  float r = d == d1 ? 0.045 : (d == d2 ? 0.04 : 0.03);
  float wood = smoothstep(0.004, -0.004, d);
  float core = clamp(-d / r, 0.0, 1.0);
  float grain = fbm(vec2(p.x * 9.0, p.y * 70.0));
  float bark = (0.035 + 0.06 * grain) * (0.45 + 0.55 * sqrt(core));
  float cracks = fbm(vec2(p.x * 38.0, p.y * 140.0) + vec2(0.0, t * 0.08));
  float ember = smoothstep(0.7, 0.8, cracks) * (1.0 - core * 0.7) * (0.5 + 0.5 * noise(vec2(p.x * 14.0, t * 2.4)));
  float rim = smoothstep(0.35, 0.0, core) * smoothstep(0.1, 0.3, flame + 0.2) * flick;
  vec3 logCol = vec3(bark, bark * 0.62, bark * 0.42) + fireColour(0.45) * rim * 0.35;
  logCol = mix(logCol, fireColour(0.62 + ember * 0.3) * 1.25, ember);
  float bed = smoothstep(0.018, 0.0, abs(uv.y - 0.132)) * smoothstep(0.42, 0.08, abs(p.x)) * (0.35 + 0.65 * fbm(vec2(p.x * 24.0, t * 0.6)));

  vec3 col = vec3(0.0);
  float glowDist = length(vec2(p.x * 0.8, (uv.y - 0.3) * 1.2));
  float light = flick * exp(-glowDist * 2.4) * (0.55 + 0.25 * hgt);
  if (hearth > 0.5) {
    vec2 bk = vec2(p.x * 9.0, uv.y * 18.0);
    bk.x += step(1.0, mod(floor(bk.y), 2.0)) * 0.5;
    vec2 cell = fract(bk);
    float mortar = smoothstep(0.0, 0.06, cell.x) * smoothstep(1.0, 0.94, cell.x) * smoothstep(0.0, 0.1, cell.y) * smoothstep(1.0, 0.9, cell.y);
    vec3 brick = mix(vec3(0.05, 0.035, 0.03), vec3(0.32, 0.12, 0.07) * (0.7 + 0.3 * hash(floor(bk))), mortar);
    col = brick * (0.06 + light * 1.6);
    vec3 back = vec3(0.09, 0.05, 0.03) * (0.2 + light * 2.2 * (1.0 - bp.y * 0.6));
    col = mix(col, back, inBox);
    col *= mix(1.0, 0.5, smoothstep(0.74, 0.8, uv.y));
    col = mix(col, vec3(0.12, 0.09, 0.07) * (0.15 + light * 1.2), step(uv.y, 0.11));
  } else {
    col = vec3(0.12, 0.05, 0.02) * light * 1.4;
  }
  col += fireColour(pow(flame, 1.15)) * smoothstep(0.02, 0.3, flame) * 1.1 * inBox;
  col += fireColour(0.55) * bed * 0.8 * inBox;
  col = mix(col, logCol, wood);

  if (sparks > 0.5) {
    for (int i = 0; i < 20; i++) {
      float fi = float(i);
      float life = 2.2 + hash(vec2(fi, 1.0)) * 1.6;
      float age = mod(t + hash(vec2(fi, 2.0)) * life, life) / life;
      vec2 sp = vec2((hash(vec2(fi, 3.0)) - 0.5) * 0.5 + sin(t * 1.3 + fi) * 0.04 * age, 0.22 + age * 0.62 * hgt);
      sp.x += (noise(vec2(fi * 3.1, t * 0.5)) - 0.5) * 0.25 * age;
      float s = smoothstep(0.006, 0.0, length(p - sp)) * (1.0 - age) * step(hash(vec2(fi, floor(t / life + fi))), 0.7);
      col += fireColour(0.85) * s * 1.4 * inBox;
    }
  }
  col += fireColour(0.5) * 0.18 * flick * exp(-glowDist * 5.0) * inBox;
  col += (hash(gl_FragCoord.xy + t) - 0.5) * 0.015;
  col *= smoothstep(1.25, 0.35, length((uv - 0.5) * vec2(aspect * 0.8, 1.0)));
  gl_FragColor = vec4(pow(max(col, 0.0), vec3(0.92)), 1.0);
}
"""
