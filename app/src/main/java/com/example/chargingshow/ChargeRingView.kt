package com.example.chargingshow

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Uygulama logosu: şarj halkası + yıldırım.
 * Logo, 108x108'lik bir tuval üzerinde çizilir (ic_launcher_foreground ile aynı geometri).
 *  - progress  : halkanın dolu kısmı (0..1)
 *  - boltScale : yıldırımın boyutu (animasyon için)
 */
class ChargeRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var progress: Float = 0.75f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var boltScale: Float = 1f
        set(value) {
            field = value
            invalidate()
        }

    private val ringRect = RectF(30f, 30f, 78f, 78f)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.parseColor("#33FFFFFF")
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#00E5A0")
    }

    private val boltFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFD60A")
    }

    private val boltStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#FFD60A")
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            54f, 54f, 30f,
            intArrayOf(Color.parseColor("#4000E5A0"), Color.parseColor("#0000E5A0")),
            null,
            Shader.TileMode.CLAMP
        )
    }

    private val boltPath = Path().apply {
        moveTo(56.8f, 37.2f)
        lineTo(47f, 55.4f)
        lineTo(53.3f, 55.4f)
        lineTo(49.8f, 70.8f)
        lineTo(61.7f, 50.5f)
        lineTo(55.4f, 50.5f)
        close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = min(width, height) / 60f
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(s, s)
        canvas.translate(-54f, -54f)

        canvas.drawCircle(54f, 54f, 30f, glowPaint)
        canvas.drawArc(ringRect, 0f, 360f, false, trackPaint)
        if (progress > 0f) {
            canvas.drawArc(ringRect, -90f, 360f * progress, false, arcPaint)
        }

        canvas.save()
        canvas.scale(boltScale, boltScale, 54f, 54f)
        canvas.drawPath(boltPath, boltFill)
        canvas.drawPath(boltPath, boltStroke)
        canvas.restore()

        canvas.restore()
    }
}
