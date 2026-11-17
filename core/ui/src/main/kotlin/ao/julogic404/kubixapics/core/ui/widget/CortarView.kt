package ao.julogic404.kubixapics.core.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min

/**
 * Vista de recorte interativa.
 *
 * Desenha a imagem ajustada ao espaco disponivel (fit-center) e uma moldura de
 * recorte arrastavel com 4 pegas nos cantos. O resultado e exposto em
 * coordenadas NORMALIZADAS (0..1) relativas a imagem, para que o recorte seja
 * independente da resolucao do ecra.
 *
 * Aspetos suportados: livre, 1:1, 16:9, 4:3 e circular (a moldura e desenhada
 * como circulo e o recorte final e mascarado).
 */
class CortarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Proporcao(val ratio: Float?, val label: String) {
        LIVRE(null, "Livre"),
        QUADRADO(1f, "1:1"),
        CIRCULO(1f, "Circulo"),
        PROPORCAO_16_9(16f / 9f, "16:9"),
        PROPORCAO_4_3(4f / 3f, "4:3")
    }

    private var bitmap: Bitmap? = null

    /** Retangulo da imagem no ecra (fit-center). */
    private val imageRect = RectF()

    /** Retangulo de recorte em coordenadas de ecra. */
    private val cropRect = RectF()

    var aspect: Proporcao = Proporcao.LIVRE
        set(value) {
            field = value
            applyAspect()
            invalidate()
        }

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99000000")
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val handleRadius = 18f
    private val touchSlop = 24f

    private enum class Arrasto { NENHUM, MOVER, SUPERIOR_ESQUERDO, SUPERIOR_DIREITO, INFERIOR_ESQUERDO, INFERIOR_DIREITO }

    private var drag = Arrasto.NENHUM
    private var lastX = 0f
    private var lastY = 0f

    fun setBitmap(bmp: Bitmap?) {
        bitmap = bmp
        cropRect.setEmpty()
        imageRect.setEmpty()
        if (width > 0 && height > 0) {
            layoutImage()
            resetCrop()
        }
        requestLayout()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutImage()
        if (cropRect.isEmpty) resetCrop()
    }

    private fun layoutImage() {
        val bmp = bitmap ?: return
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return
        val scale = min(viewW / bmp.width, viewH / bmp.height)
        val drawW = bmp.width * scale
        val drawH = bmp.height * scale
        val left = (viewW - drawW) / 2f
        val top = (viewH - drawH) / 2f
        imageRect.set(left, top, left + drawW, top + drawH)
    }

    private fun resetCrop() {
        if (imageRect.isEmpty) return
        val inset = min(imageRect.width(), imageRect.height()) * 0.08f
        cropRect.set(
            imageRect.left + inset,
            imageRect.top + inset,
            imageRect.right - inset,
            imageRect.bottom - inset
        )
        applyAspect()
        invalidate()
    }

    /** Ajusta o recorte ao aspeto escolhido, mantendo-o dentro da imagem. */
    private fun applyAspect() {
        if (imageRect.isEmpty) return
        val ratio = aspect.ratio ?: return
        val cx = cropRect.centerX()
        val cy = cropRect.centerY()
        var w = cropRect.width()
        var h = cropRect.height()
        if (w / h > ratio) w = h * ratio else h = w / ratio
        // Limitar ao tamanho da imagem
        if (w > imageRect.width()) { w = imageRect.width(); h = w / ratio }
        if (h > imageRect.height()) { h = imageRect.height(); w = h * ratio }
        var left = cx - w / 2f
        var top = cy - h / 2f
        left = left.coerceIn(imageRect.left, imageRect.right - w)
        top = top.coerceIn(imageRect.top, imageRect.bottom - h)
        cropRect.set(left, top, left + w, top + h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        if (imageRect.isEmpty) layoutImage()

        canvas.drawBitmap(bmp, null, imageRect, null)

        // Escurecer fora do recorte
        canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, dimPaint)
        canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, dimPaint)
        canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, dimPaint)

        if (aspect == Proporcao.CIRCULO) {
            val r = min(cropRect.width(), cropRect.height()) / 2f
            canvas.drawCircle(cropRect.centerX(), cropRect.centerY(), r, borderPaint)
        } else {
            canvas.drawRect(cropRect, borderPaint)
            // Grelha de terços
            val thirdW = cropRect.width() / 3f
            val thirdH = cropRect.height() / 3f
            for (i in 1..2) {
                canvas.drawLine(
                    cropRect.left + thirdW * i, cropRect.top,
                    cropRect.left + thirdW * i, cropRect.bottom, gridPaint
                )
                canvas.drawLine(
                    cropRect.left, cropRect.top + thirdH * i,
                    cropRect.right, cropRect.top + thirdH * i, gridPaint
                )
            }
        }

        // Pegas nos cantos
        canvas.drawCircle(cropRect.left, cropRect.top, handleRadius, handlePaint)
        canvas.drawCircle(cropRect.right, cropRect.top, handleRadius, handlePaint)
        canvas.drawCircle(cropRect.left, cropRect.bottom, handleRadius, handlePaint)
        canvas.drawCircle(cropRect.right, cropRect.bottom, handleRadius, handlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null) return false
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                drag = hitTest(x, y)
                lastX = x
                lastY = y
                // O CortarView vive dentro de um NestedScrollView: sem isto, arrastar a
                // moldura na vertical faz scroll da pagina em vez de mover o recorte.
                if (drag != Arrasto.NENHUM) parent?.requestDisallowInterceptTouchEvent(true)
                return drag != Arrasto.NENHUM
            }
            MotionEvent.ACTION_MOVE -> {
                if (drag == Arrasto.NENHUM) return false
                val dx = x - lastX
                val dy = y - lastY
                lastX = x
                lastY = y
                when (drag) {
                    Arrasto.MOVER -> moveCrop(dx, dy)
                    Arrasto.SUPERIOR_ESQUERDO -> resizeCrop(dx, dy, 0, 0)
                    Arrasto.SUPERIOR_DIREITO -> resizeCrop(dx, dy, 1, 0)
                    Arrasto.INFERIOR_ESQUERDO -> resizeCrop(dx, dy, 0, 1)
                    Arrasto.INFERIOR_DIREITO -> resizeCrop(dx, dy, 1, 1)
                    Arrasto.NENHUM -> Unit
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                drag = Arrasto.NENHUM
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun hitTest(x: Float, y: Float): Arrasto {
        val nearLeft = abs(x - cropRect.left) < touchSlop
        val nearRight = abs(x - cropRect.right) < touchSlop
        val nearTop = abs(y - cropRect.top) < touchSlop
        val nearBottom = abs(y - cropRect.bottom) < touchSlop

        return when {
            nearLeft && nearTop -> Arrasto.SUPERIOR_ESQUERDO
            nearRight && nearTop -> Arrasto.SUPERIOR_DIREITO
            nearLeft && nearBottom -> Arrasto.INFERIOR_ESQUERDO
            nearRight && nearBottom -> Arrasto.INFERIOR_DIREITO
            cropRect.contains(x, y) -> Arrasto.MOVER
            else -> Arrasto.NENHUM
        }
    }

    private fun moveCrop(dx: Float, dy: Float) {
        var newLeft = cropRect.left + dx
        var newTop = cropRect.top + dy
        newLeft = newLeft.coerceIn(imageRect.left, imageRect.right - cropRect.width())
        newTop = newTop.coerceIn(imageRect.top, imageRect.bottom - cropRect.height())
        cropRect.offsetTo(newLeft, newTop)
    }

    private fun resizeCrop(dx: Float, dy: Float, cornerX: Int, cornerY: Int) {
        val minSize = 48f
        var left = cropRect.left
        var top = cropRect.top
        var right = cropRect.right
        var bottom = cropRect.bottom

        if (cornerX == 0) left = (left + dx).coerceIn(imageRect.left, right - minSize)
        else right = (right + dx).coerceIn(left + minSize, imageRect.right)
        if (cornerY == 0) top = (top + dy).coerceIn(imageRect.top, bottom - minSize)
        else bottom = (bottom + dy).coerceIn(top + minSize, imageRect.bottom)

        cropRect.set(left, top, right, bottom)

        // Reaplicar aspeto se nao for livre
        val ratio = aspect.ratio
        if (ratio != null) {
            var w = cropRect.width()
            var h = cropRect.height()
            if (w / h > ratio) w = h * ratio else h = w / ratio
            if (cornerX == 0) cropRect.left = cropRect.right - w else cropRect.right = cropRect.left + w
            if (cornerY == 0) cropRect.top = cropRect.bottom - h else cropRect.bottom = cropRect.top + h
            // Garantir dentro da imagem
            if (cropRect.left < imageRect.left) cropRect.offset(imageRect.left - cropRect.left, 0f)
            if (cropRect.top < imageRect.top) cropRect.offset(0f, imageRect.top - cropRect.top)
            if (cropRect.right > imageRect.right) cropRect.offset(imageRect.right - cropRect.right, 0f)
            if (cropRect.bottom > imageRect.bottom) cropRect.offset(0f, imageRect.bottom - cropRect.bottom)
        }
    }

    /** Recorte normalizado (0..1) relativo a imagem. */
    fun getNormalizedCrop(): FloatArray {
        if (imageRect.isEmpty) return floatArrayOf(0f, 0f, 1f, 1f)
        val w = imageRect.width()
        val h = imageRect.height()
        return floatArrayOf(
            ((cropRect.left - imageRect.left) / w).coerceIn(0f, 1f),
            ((cropRect.top - imageRect.top) / h).coerceIn(0f, 1f),
            ((cropRect.right - imageRect.left) / w).coerceIn(0f, 1f),
            ((cropRect.bottom - imageRect.top) / h).coerceIn(0f, 1f)
        )
    }

    fun isCircle(): Boolean = aspect == Proporcao.CIRCULO
}
