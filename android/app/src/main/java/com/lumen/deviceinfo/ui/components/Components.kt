package com.lumen.deviceinfo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumen.deviceinfo.ui.theme.LocalLumenColors
import com.lumen.deviceinfo.ui.theme.NumericStyle

/** Radio grande de tarjetas (ColorOS 17). */
val CardRadiusLarge = 28.dp
val CardRadiusMedium = 22.dp

/**
 * Fondo ambiental con blobs de color difusos (aqua/cielo/lavanda) mediante
 * degradados radiales. Se coloca detrás de todo el contenido.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val lumen = LocalLumenColors.current
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(lumen.blobAqua, Color.Transparent),
                    center = Offset(size.width * 0.08f, size.height * 0.06f),
                    radius = size.width * 0.75f
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width * 0.08f, size.height * 0.06f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(lumen.blobSky, Color.Transparent),
                    center = Offset(size.width * 0.95f, size.height * 0.22f),
                    radius = size.width * 0.8f
                ),
                radius = size.width * 0.8f,
                center = Offset(size.width * 0.95f, size.height * 0.22f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(lumen.blobLavender, Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.72f),
                    radius = size.width * 0.9f
                ),
                radius = size.width * 0.9f,
                center = Offset(size.width * 0.5f, size.height * 0.72f)
            )
        }
    }
}

/**
 * Tarjeta glass: superficie translúcida + borde claro + sombra difusa.
 * En API 31+ se podría añadir blur real, pero se ve bien también sin él.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = CardRadiusLarge,
    content: @Composable () -> Unit
) {
    val lumen = LocalLumenColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius),
        color = lumen.glassSurface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, lumen.glassBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

/** Etiqueta de sección: 11sp, mayúsculas, tracking amplio, color terciario. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    val lumen = LocalLumenColors.current
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = lumen.textTertiary,
        modifier = modifier.padding(start = 4.dp, bottom = 2.dp, top = 8.dp)
    )
}

/**
 * Fila de datos: etiqueta secundaria a la izquierda, valor en peso 600 con
 * cifras tabulares (`tnum`) a la derecha. Sin monoespaciada.
 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        )
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    androidx.compose.material3.HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    )
}

/**
 * Icono dentro de un contenedor circular tintado (ColorOS 17): fondo al 12–16 %
 * y trazo del color pleno.
 */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.46f)
        )
    }
}

@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    maxValue: Float = 100f
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        val gridColor = color.copy(alpha = 0.10f)
        listOf(0.25f, 0.5f, 0.75f).forEach { f ->
            drawLine(
                color = gridColor,
                start = Offset(0f, size.height * f),
                end = Offset(size.width, size.height * f),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f))
            )
        }
        if (values.size < 2) {
            drawLine(
                color = color.copy(alpha = 0.35f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
            return@Canvas
        }
        val max = maxValue.coerceAtLeast(1f)
        val stepX = size.width / (values.size - 1)
        val points = values.mapIndexed { i, v ->
            val y = size.height - (v.coerceIn(0f, max) / max) * (size.height - 6f) - 3f
            Offset(i * stepX, y)
        }

        val area = Path().apply {
            moveTo(points.first().x, size.height)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, size.height)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0.02f))
            )
        )

        for (i in 0 until points.size - 1) {
            drawLine(
                color = color,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
            drawCircle(color = color, radius = 2.2f, center = points[i])
        }
        drawCircle(color = color, radius = 3f, center = points.last())
    }
}

/** Fila con etiqueta, valor principal y una línea secundaria pequeña. */
@Composable
fun InfoRowSub(label: String, value: String, sub: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
