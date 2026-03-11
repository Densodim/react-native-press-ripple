package com.margelo.nitro.pressripple

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.sqrt

/**
 * Pure Android Canvas view — renders the M3 Material ripple animation.
 *
 * No Expo, no Nitro. Owned by HybridPressRipple.
 * Call startRipple(x, y) from the UI thread to start a new animation.
 *
 * Optimizations:
 * - density cached in init — not recalculated per frame
 * - clipPath rebuilt only on size or borderRadius change (not every frame)
 * - ripplePaint.color set once in setRippleColor — only alpha changes per frame
 * - Single PropertyValuesHolder animator — one invalidate() per frame
 */
internal class PressRippleView(context: Context) : View(context) {

    // ── Cached density (immutable after init) ─────────────────────────────────

    private val density = resources.displayMetrics.density

    // ── Config state ─────────────────────────────────────────────────────────

    private var rippleColor: Int = Color.parseColor("#40000000")
    private var borderRadiusDp: Float = 0f

    // ── Animation state ──────────────────────────────────────────────────────

    private var currentRadius: Float = 0f
    private var currentAlpha: Int = 0
    private var rippleX: Float = 0f
    private var rippleY: Float = 0f
    private var maxRadius: Float = 0f
    private var activeAnimator: AnimatorSet? = null

    // ── Drawing ──────────────────────────────────────────────────────────────

    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val clipPath = Path()
    private val clipRect = RectF()
    private var clipPathDirty = true

    // ── Timing ───────────────────────────────────────────────────────────────

    companion object {
        private const val EXPAND_DURATION_MS = 350L
        private const val FADE_IN_DURATION_MS = 80L
        private const val FADE_OUT_DURATION_MS = 250L
        private const val FADE_OUT_DELAY_MS = 80L
        private const val PROP_RADIUS = "radius"
        private const val PROP_ALPHA = "alpha"
    }

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_HARDWARE, null)
        // Never intercept touches — purely visual overlay
        isClickable = false
        isFocusable = false
    }

    // ── Public setters (call from any thread — invalidate posts to UI) ────────

    fun setRippleColor(color: Int) {
        rippleColor = color
        ripplePaint.color = color
    }

    fun setBorderRadius(radiusDp: Int) {
        borderRadiusDp = radiusDp.toFloat()
        clipPathDirty = true
        invalidate()
    }

    /**
     * Start a ripple animation at (x, y) in dp.
     * Must be called from the UI thread.
     */
    fun startRipple(x: Float, y: Float) {
        rippleX = x * density
        rippleY = y * density
        maxRadius = calcMaxRadius(rippleX, rippleY, width.toFloat(), height.toFloat())
        startRippleAnimation()
    }

    // ── Size change ───────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildClipPath(w.toFloat(), h.toFloat())
    }

    private fun rebuildClipPath(w: Float, h: Float) {
        val radiusPx = borderRadiusDp * density
        clipRect.set(0f, 0f, w, h)
        clipPath.reset()
        if (radiusPx > 0f) {
            clipPath.addRoundRect(clipRect, radiusPx, radiusPx, Path.Direction.CW)
        }
        clipPathDirty = false
    }

    // ── Ripple animation ──────────────────────────────────────────────────────

    private fun startRippleAnimation() {
        activeAnimator?.cancel()
        currentRadius = 0f
        currentAlpha = 0

        val targetAlpha = Color.alpha(rippleColor)

        // Phase 1: expand + fade-in simultaneously (single animator → 1 invalidate/frame)
        val expandHolder = PropertyValuesHolder.ofFloat(PROP_RADIUS, 0f, maxRadius)
        val fadeInHolder = PropertyValuesHolder.ofInt(PROP_ALPHA, 0, targetAlpha)

        val expandFadeIn = ValueAnimator.ofPropertyValuesHolder(expandHolder, fadeInHolder).apply {
            duration = FADE_IN_DURATION_MS.coerceAtMost(EXPAND_DURATION_MS)
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                currentRadius = anim.getAnimatedValue(PROP_RADIUS) as Float
                currentAlpha = anim.getAnimatedValue(PROP_ALPHA) as Int
                invalidate()
            }
        }

        // Continue expanding after fade-in completes (alpha stays at peak)
        val expandOnly = ValueAnimator.ofFloat(
            FADE_IN_DURATION_MS.toFloat() / EXPAND_DURATION_MS * maxRadius,
            maxRadius
        ).apply {
            duration = EXPAND_DURATION_MS - FADE_IN_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                currentRadius = anim.animatedValue as Float
                invalidate()
            }
        }

        // Phase 2: fade out
        val fadeOut = ValueAnimator.ofInt(targetAlpha, 0).apply {
            duration = FADE_OUT_DURATION_MS
            startDelay = FADE_OUT_DELAY_MS
            addUpdateListener { anim ->
                currentAlpha = anim.animatedValue as Int
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentRadius = 0f
                    currentAlpha = 0
                    invalidate()
                }
            })
        }

        activeAnimator = AnimatorSet().apply {
            play(expandFadeIn)
            play(expandOnly).after(expandFadeIn)
            play(fadeOut).after(expandFadeIn)
            start()
        }
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (currentRadius <= 0f || currentAlpha <= 0) return

        if (clipPathDirty) rebuildClipPath(width.toFloat(), height.toFloat())
        if (borderRadiusDp > 0f) canvas.clipPath(clipPath)

        ripplePaint.alpha = currentAlpha
        canvas.drawCircle(rippleX, rippleY, currentRadius, ripplePaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        activeAnimator?.cancel()
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun calcMaxRadius(x: Float, y: Float, w: Float, h: Float): Float {
        val dx = maxOf(x, w - x)
        val dy = maxOf(y, h - y)
        return sqrt(dx * dx + dy * dy)
    }
}
