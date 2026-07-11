package com.lingqi.app.ui.particle

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.lingqi.app.meditation.BreathPhase
import com.lingqi.app.meditation.BreathState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos

class LingqiParticleView(context: Context) : GLSurfaceView(context) {
    private val particleRenderer = ParticleRenderer()

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(false)
        setRenderer(particleRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }

    fun setBreathState(state: BreathState?) {
        particleRenderer.breathState = state
    }

    fun setActive(active: Boolean) {
        particleRenderer.active = active
    }

    fun setPaused(paused: Boolean) {
        particleRenderer.paused = paused
    }
}

private class ParticleRenderer : GLSurfaceView.Renderer {
    @Volatile var breathState: BreathState? = null
    @Volatile var active: Boolean = false
    @Volatile var paused: Boolean = false
    private var program = 0
    private var width = 1
    private var height = 1
    private var lastNanos = 0L
    private var particleTime = 0f
    private var rotation = 0f
    private val particleCount = 200
    private val buffer: FloatBuffer = buildParticles()

    override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
    }

    override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, this.width, this.height)
    }

    override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val now = System.nanoTime()
        val delta = if (lastNanos == 0L) 0f else ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
        lastNanos = now
        val state = breathState
        val visual = visualState(state)
        if (visual.motionEnabled && !paused) {
            particleTime += delta * 0.1f
            rotation += delta * 0.006f
        }

        GLES20.glUseProgram(program)
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        val random = GLES20.glGetAttribLocation(program, "aRandom")
        buffer.position(0)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 7 * 4, buffer)
        buffer.position(3)
        GLES20.glEnableVertexAttribArray(random)
        GLES20.glVertexAttribPointer(random, 4, GLES20.GL_FLOAT, false, 7 * 4, buffer)

        uniform("uTime", particleTime)
        uniform("uSpread", visual.spread)
        uniform("uBaseSize", visual.size)
        uniform("uAspect", width.toFloat() / height)
        uniform("uFovScale", (1.0 / kotlin.math.tan(20.0 * PI / 360.0)).toFloat())
        uniform("uCameraDistance", 26f)
        uniform("uVerticalOffset", if (width < height) -0.32f else -0.22f)
        uniform("uRotation", rotation)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, particleCount)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisableVertexAttribArray(random)
    }

    private fun visualState(state: BreathState?): VisualState {
        val motionEnabled = !active || state == null || state.phase == BreathPhase.INHALE
        return VisualState(
            spread = particleSpread(state, active),
            size = particleBaseSize(state, active),
            motionEnabled = motionEnabled,
        )
    }

    private fun uniform(name: String, value: Float) {
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, name), value)
    }

    private fun createProgram(vertex: String, fragment: String): Int {
        fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            return shader
        }
        val vertexShader = compile(GLES20.GL_VERTEX_SHADER, vertex)
        val fragmentShader = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        return GLES20.glCreateProgram().also { value ->
            GLES20.glAttachShader(value, vertexShader)
            GLES20.glAttachShader(value, fragmentShader)
            GLES20.glLinkProgram(value)
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
        }
    }

    private fun buildParticles(): FloatBuffer {
        val values = FloatArray(particleCount * 7)
        val random = Random(478L)
        repeat(particleCount) { index ->
            var x: Float
            var y: Float
            var z: Float
            var length: Float
            do {
                x = random.nextFloat() * 2f - 1f
                y = random.nextFloat() * 2f - 1f
                z = random.nextFloat() * 2f - 1f
                length = x * x + y * y + z * z
            } while (length > 1f || length == 0f)
            val radius = Math.cbrt(random.nextDouble()).toFloat()
            val offset = index * 7
            values[offset] = x * radius
            values[offset + 1] = y * radius
            values[offset + 2] = z * radius
            values[offset + 3] = random.nextFloat()
            values[offset + 4] = random.nextFloat()
            values[offset + 5] = random.nextFloat()
            values[offset + 6] = random.nextFloat()
        }
        return ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }
    }

    private data class VisualState(val spread: Float, val size: Float, val motionEnabled: Boolean)

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec3 aPosition;
            attribute vec4 aRandom;
            uniform float uTime;
            uniform float uSpread;
            uniform float uBaseSize;
            uniform float uAspect;
            uniform float uFovScale;
            uniform float uCameraDistance;
            uniform float uVerticalOffset;
            uniform float uRotation;
            varying vec4 vRandom;
            void main() {
                vRandom = aRandom;
                vec3 pos = aPosition * uSpread;
                float rawZ = pos.z * 10.0;
                float depthLimit = uCameraDistance - 4.0;
                pos.z = rawZ / (1.0 + abs(rawZ) / depthLimit);
                pos.x += sin(uTime * aRandom.z + 6.28 * aRandom.w) * mix(0.1, 1.5, aRandom.x);
                pos.y += sin(uTime * aRandom.y + 6.28 * aRandom.x) * mix(0.1, 1.5, aRandom.w);
                pos.z += sin(uTime * aRandom.w + 6.28 * aRandom.y) * mix(0.1, 1.5, aRandom.z);
                float c = cos(uRotation);
                float s = sin(uRotation);
                pos.xy = mat2(c, -s, s, c) * pos.xy;
                float viewZ = uCameraDistance - pos.z;
                vec2 projected = pos.xy / max(viewZ, 0.01) * uFovScale;
                gl_Position = vec4(projected.x / uAspect, projected.y + uVerticalOffset, 0.0, 1.0);
                gl_PointSize = (uBaseSize * (1.0 + 0.12 * (aRandom.x - 0.5))) / max(viewZ, 0.01);
            }
        """
        private const val FRAGMENT_SHADER = """
            precision highp float;
            varying vec4 vRandom;
            void main() {
                vec2 uv = gl_PointCoord.xy;
                float distanceFromCenter = length(uv - vec2(0.5));
                if (distanceFromCenter > 0.5) discard;
                float shimmer = 0.08 * sin(uv.y + vRandom.y * 6.28);
                gl_FragColor = vec4(vec3(1.0 + shimmer), 1.0);
            }
        """
    }
}

internal fun particleSpread(state: BreathState?, active: Boolean): Float {
    if (!active || state == null) return 10f
    return when (state.phase) {
        BreathPhase.INHALE -> {
            val progress = state.progress
            val smooth = progress * progress * (3f - 2f * progress)
            10f - smooth * 4.8f
        }
        BreathPhase.HOLD -> 5.2f
        BreathPhase.EXHALE -> {
            val release = (0.5 - cos(PI * state.progress) / 2.0).toFloat()
            5.2f + release * 4.8f
        }
    }
}

internal fun particleBaseSize(state: BreathState?, active: Boolean): Float {
    if (!active || state == null) return 58f
    return when (state.phase) {
        BreathPhase.INHALE -> {
            val progress = state.progress
            val smooth = progress * progress * (3f - 2f * progress)
            58f + smooth * 18f
        }
        BreathPhase.HOLD -> 76f
        BreathPhase.EXHALE -> {
            val release = (0.5 - cos(PI * state.progress) / 2.0).toFloat()
            76f - release * 18f
        }
    }
}
