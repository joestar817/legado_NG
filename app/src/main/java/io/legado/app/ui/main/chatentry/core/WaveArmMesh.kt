package io.legado.app.ui.main.chatentry.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class PetTriangle(
    val src: List<PetPoint>,
    val dst: List<PetPoint>,
    val matrix: FloatArray,
    val clipDst: List<PetPoint>,
)

data class PetArmMesh(val triangles: List<PetTriangle>, val bounds: PetRect)

/** Only the accepted, small right-elbow bend used by the wave action. */
object WaveArmMesh {
    private val rows = floatArrayOf(0f, .25f, .40f, .46f, .53f, .60f, .68f, .82f, 1f)
    private val columns = floatArrayOf(0f, 1f / 3f, 2f / 3f, 1f)

    fun build(layer: PetLayerDefinition, matrix: FloatArray, motion: PetMotion): PetArmMesh? {
        if (layer.id != "right-arm" || abs(motion.rightElbow) < .001f) return null
        require(matrix.size == 6)
        val elbow = PetPoint(layer.width * 55f / 108f, layer.height * 84f / 157f)
        val radians = motion.rightElbow * Math.PI / 180.0
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        val sources = Array(rows.size) { row ->
            Array(columns.size) { col -> PetPoint(columns[col] * layer.width, rows[row] * layer.height) }
        }
        val destinations = Array(rows.size) { row ->
            Array(columns.size) { col ->
                val source = sources[row][col]
                val weight = PetActionSampler.smooth(
                    ((source.y - elbow.y + layer.height * .09f) / (layer.height * .18f)).toDouble()
                ).toFloat()
                val dx = source.x - elbow.x
                val dy = source.y - elbow.y
                val rotatedX = elbow.x + c * dx - s * dy
                val rotatedY = elbow.y + s * dx + c * dy
                map(matrix, PetPoint(
                    source.x + (rotatedX - source.x) * weight,
                    source.y + (rotatedY - source.y) * weight,
                ))
            }
        }
        val triangles = ArrayList<PetTriangle>(48)
        for (row in 0 until rows.lastIndex) {
            for (col in 0 until columns.lastIndex) {
                val source = listOf(sources[row][col], sources[row][col + 1], sources[row + 1][col + 1], sources[row + 1][col])
                val destination = listOf(destinations[row][col], destinations[row][col + 1], destinations[row + 1][col + 1], destinations[row + 1][col])
                for (indices in listOf(intArrayOf(0, 1, 2), intArrayOf(0, 2, 3))) {
                    val src = indices.map { source[it] }
                    val dst = indices.map { destination[it] }
                    triangles.add(PetTriangle(src, dst, affine(src, dst), expandedClip(dst)))
                }
            }
        }
        val points = triangles.flatMap { it.dst }
        return PetArmMesh(triangles, PetRect(
            points.minOf { it.x }, points.minOf { it.y },
            points.maxOf { it.x }, points.maxOf { it.y },
        ))
    }

    fun hitTest(mesh: PetArmMesh, point: PetPoint, alphaAt: (Float, Float) -> Int): Boolean {
        for (triangle in mesh.triangles.asReversed()) {
            val a = triangle.dst[0]
            val b = triangle.dst[1]
            val c = triangle.dst[2]
            val denominator = (b.y - c.y) * (a.x - c.x) + (c.x - b.x) * (a.y - c.y)
            if (abs(denominator) < 1e-7f) continue
            val u = ((b.y - c.y) * (point.x - c.x) + (c.x - b.x) * (point.y - c.y)) / denominator
            val v = ((c.y - a.y) * (point.x - c.x) + (a.x - c.x) * (point.y - c.y)) / denominator
            val w = 1f - u - v
            if (u !in 0f..1f || v !in 0f..1f || w !in 0f..1f) continue
            val x = triangle.src[0].x * u + triangle.src[1].x * v + triangle.src[2].x * w
            val y = triangle.src[0].y * u + triangle.src[1].y * v + triangle.src[2].y * w
            if (alphaAt(x, y) > 32) return true
        }
        return false
    }

    private fun map(m: FloatArray, p: PetPoint) = PetPoint(m[0] * p.x + m[2] * p.y + m[4], m[1] * p.x + m[3] * p.y + m[5])

    private fun affine(src: List<PetPoint>, dst: List<PetPoint>): FloatArray {
        val p = src[0]
        val q = src[1]
        val r = src[2]
        val denominator = p.x.toDouble() * (q.y - r.y) + q.x.toDouble() * (r.y - p.y) + r.x.toDouble() * (p.y - q.y)
        fun row(a: Float, b: Float, c: Float): DoubleArray = doubleArrayOf(
            (a.toDouble() * (q.y - r.y) + b.toDouble() * (r.y - p.y) + c.toDouble() * (p.y - q.y)) / denominator,
            (a.toDouble() * (r.x - q.x) + b.toDouble() * (p.x - r.x) + c.toDouble() * (q.x - p.x)) / denominator,
            (a.toDouble() * (q.x * r.y - r.x * q.y) + b.toDouble() * (r.x * p.y - p.x * r.y) + c.toDouble() * (p.x * q.y - q.x * p.y)) / denominator,
        )
        val x = row(dst[0].x, dst[1].x, dst[2].x)
        val y = row(dst[0].y, dst[1].y, dst[2].y)
        return floatArrayOf(x[0].toFloat(), y[0].toFloat(), x[1].toFloat(), y[1].toFloat(), x[2].toFloat(), y[2].toFloat())
    }

    private fun expandedClip(points: List<PetPoint>): List<PetPoint> {
        val a = points[0]
        val b = points[1]
        val c = points[2]
        val orientation = if ((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x) >= 0f) 1f else -1f
        val normals = points.indices.map { i ->
            val p = points[i]
            val q = points[(i + 1) % 3]
            val length = hypot(q.x - p.x, q.y - p.y).coerceAtLeast(1e-7f)
            PetPoint(orientation * (q.y - p.y) / length, -orientation * (q.x - p.x) / length)
        }
        return points.mapIndexed { i, p ->
            val previous = normals[(i + 2) % 3]
            val next = normals[i]
            val denominator = maxOf(.02f, 1f + previous.x * next.x + previous.y * next.y)
            var dx = (previous.x + next.x) * .35f / denominator
            var dy = (previous.y + next.y) * .35f / denominator
            val extent = hypot(dx, dy)
            if (extent > 1.4f) {
                dx *= 1.4f / extent
                dy *= 1.4f / extent
            }
            PetPoint(p.x + dx, p.y + dy)
        }
    }
}
