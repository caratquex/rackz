package com.rackz.stackmyrackz.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

val RacksShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Material 3 Expressive shape primitives (Sunny, Cookie, and Square tension helpers).
 */
object ExpressiveShapes {
    /** 8-pointed soft sunburst polygon inspired by MaterialShapes.Sunny. */
    val Sunny: Shape = ScallopedPolygonShape(
        lobes = 8,
        amplitude = 0.13f,
        cuspPower = 1.0f,
        phaseRadians = -PI.toFloat() / 2f
    )

    /** 10-pointed soft sunburst matching the Racks launcher icon outer silhouette. */
    val Sunny10: Shape = ScallopedPolygonShape(
        lobes = 10,
        amplitude = 0.12f,
        cuspPower = 1.0f,
        phaseRadians = -PI.toFloat() / 2f
    )

    /** 9-lobed scalloped cookie shape inspired by MaterialShapes.Cookie9Sided. */
    val Cookie9Sided: Shape = ScallopedPolygonShape(
        lobes = 9,
        amplitude = 0.15f,
        cuspPower = 1.65f,
        phaseRadians = -PI.toFloat() / 2f
    )

    /** 4-lobed clover/cookie shape inspired by MaterialShapes.Cookie4Sided. */
    val Cookie4Sided: Shape = ScallopedPolygonShape(
        lobes = 4,
        amplitude = 0.16f,
        cuspPower = 1.5f,
        phaseRadians = PI.toFloat() / 4f
    )

    /** Crisp rounded square for M3 round-vs-square shape tension. */
    val SoftSquare: Shape = RoundedCornerShape(percent = 28)

    /**
     * Paired stat-card shape: generous outer corners (26.dp) with crisp square inner seam (10.dp).
     */
    fun splitPairCardShape(
        isStart: Boolean,
        outerRadius: Dp = 26.dp,
        innerRadius: Dp = 10.dp
    ): Shape = if (isStart) {
        RoundedCornerShape(
            topStart = outerRadius,
            bottomStart = outerRadius,
            topEnd = innerRadius,
            bottomEnd = innerRadius
        )
    } else {
        RoundedCornerShape(
            topStart = innerRadius,
            bottomStart = innerRadius,
            topEnd = outerRadius,
            bottomEnd = outerRadius
        )
    }

    /**
     * M3 Expressive Segmented List item shape: large outer group corners (24.dp) and
     * crisp square inner corners (6.dp) between adjacent items.
     */
    fun segmentedItemShape(
        index: Int,
        count: Int,
        outerRadius: Dp = 24.dp,
        innerRadius: Dp = 6.dp
    ): Shape {
        if (count <= 1) return RoundedCornerShape(outerRadius)
        val topRadius = if (index == 0) outerRadius else innerRadius
        val bottomRadius = if (index == count - 1) outerRadius else innerRadius
        return RoundedCornerShape(
            topStart = topRadius,
            topEnd = topRadius,
            bottomStart = bottomRadius,
            bottomEnd = bottomRadius
        )
    }

    /** Cycles through M3 Expressive shapes for category swatches. */
    fun categorySwatchShape(index: Int): Shape = when (index % 4) {
        0 -> Sunny
        1 -> Cookie9Sided
        2 -> SoftSquare
        else -> Cookie4Sided
    }
}

private class ScallopedPolygonShape(
    private val lobes: Int,
    private val amplitude: Float,
    private val cuspPower: Float,
    private val phaseRadians: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxRadius = min(size.width, size.height) / 2f
        val steps = (lobes * 36).coerceAtLeast(180)
        val twoPi = (2.0 * PI).toFloat()

        val path = Path()
        for (i in 0 until steps) {
            val theta = (i.toFloat() / steps.toFloat()) * twoPi
            val wave = ((1f + cos(lobes * theta + phaseRadians)) * 0.5f).coerceIn(0f, 1f)
            val indentation = (1f - wave).pow(cuspPower)
            val radius = maxRadius * (1f - amplitude * indentation)
            val x = cx + radius * cos(theta)
            val y = cy + radius * sin(theta)
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        return Outline.Generic(path)
    }
}
