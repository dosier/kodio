package space.kodio.sample.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object SampleIcons {

    val PlayArrow: ImageVector by lazy {
        ImageVector.Builder(
            name = "PlayArrow",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 5f)
                lineTo(8f, 19f)
                lineTo(19f, 12f)
                close()
            }
        }.build()
    }

    val Pause: ImageVector by lazy {
        ImageVector.Builder(
            name = "Pause",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 19f)
                horizontalLineTo(10f)
                verticalLineTo(5f)
                horizontalLineTo(6f)
                verticalLineTo(19f)
                close()
                moveTo(14f, 5f)
                verticalLineTo(19f)
                horizontalLineTo(18f)
                verticalLineTo(5f)
                horizontalLineTo(14f)
                close()
            }
        }.build()
    }

    val Stop: ImageVector by lazy {
        ImageVector.Builder(
            name = "Stop",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 6f)
                horizontalLineTo(18f)
                verticalLineTo(18f)
                horizontalLineTo(6f)
                close()
            }
        }.build()
    }

    val Delete: ImageVector by lazy {
        ImageVector.Builder(
            name = "Delete",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 19f)
                curveTo(6f, 20.1f, 6.9f, 21f, 8f, 21f)
                horizontalLineTo(16f)
                curveTo(17.1f, 21f, 18f, 20.1f, 18f, 19f)
                verticalLineTo(7f)
                horizontalLineTo(6f)
                verticalLineTo(19f)
                close()
                moveTo(19f, 4f)
                horizontalLineTo(15.5f)
                lineTo(14.5f, 3f)
                horizontalLineTo(9.5f)
                lineTo(8.5f, 4f)
                horizontalLineTo(5f)
                verticalLineTo(6f)
                horizontalLineTo(19f)
                verticalLineTo(4f)
                close()
            }
        }.build()
    }

    val Mic: ImageVector by lazy {
        ImageVector.Builder(
            name = "Mic",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 14f)
                curveTo(13.66f, 14f, 14.99f, 12.66f, 14.99f, 11f)
                lineTo(15f, 5f)
                curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
                curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
                verticalLineTo(11f)
                curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f)
                close()
                moveTo(17.3f, 11f)
                curveTo(17.3f, 14f, 14.76f, 16.1f, 12f, 16.1f)
                curveTo(9.24f, 16.1f, 6.7f, 14f, 6.7f, 11f)
                horizontalLineTo(5f)
                curveTo(5f, 14.41f, 7.72f, 17.23f, 11f, 17.72f)
                verticalLineTo(21f)
                horizontalLineTo(13f)
                verticalLineTo(17.72f)
                curveTo(16.28f, 17.24f, 19f, 14.42f, 19f, 11f)
                horizontalLineTo(17.3f)
                close()
            }
        }.build()
    }

    val Folder: ImageVector by lazy {
        ImageVector.Builder(
            name = "Folder",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10f, 4f)
                horizontalLineTo(4f)
                curveTo(2.9f, 4f, 2.01f, 4.9f, 2.01f, 6f)
                lineTo(2f, 18f)
                curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
                horizontalLineTo(20f)
                curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
                verticalLineTo(8f)
                curveTo(22f, 6.9f, 21.1f, 6f, 20f, 6f)
                horizontalLineTo(12f)
                lineTo(10f, 4f)
                close()
            }
        }.build()
    }

    val Schedule: ImageVector by lazy {
        ImageVector.Builder(
            name = "Schedule",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(11.99f, 2f)
                curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
                curveTo(2f, 17.52f, 6.47f, 22f, 11.99f, 22f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                curveTo(22f, 6.48f, 17.52f, 2f, 11.99f, 2f)
                close()
                moveTo(12f, 20f)
                curveTo(7.58f, 20f, 4f, 16.42f, 4f, 12f)
                curveTo(4f, 7.58f, 7.58f, 4f, 12f, 4f)
                curveTo(16.42f, 4f, 20f, 7.58f, 20f, 12f)
                curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
                close()
                moveTo(12.5f, 7f)
                horizontalLineTo(11f)
                verticalLineTo(13f)
                lineTo(16.25f, 16.15f)
                lineTo(17f, 14.92f)
                lineTo(12.5f, 12.25f)
                verticalLineTo(7f)
                close()
            }
        }.build()
    }

    val Payments: ImageVector by lazy {
        ImageVector.Builder(
            name = "Payments",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 14f)
                verticalLineTo(6f)
                curveTo(19f, 4.9f, 18.1f, 4f, 17f, 4f)
                horizontalLineTo(3f)
                curveTo(1.9f, 4f, 1f, 4.9f, 1f, 6f)
                verticalLineTo(14f)
                curveTo(1f, 15.1f, 1.9f, 16f, 3f, 16f)
                horizontalLineTo(17f)
                curveTo(18.1f, 16f, 19f, 15.1f, 19f, 14f)
                close()
                moveTo(10f, 13f)
                curveTo(7.79f, 13f, 6f, 11.21f, 6f, 9f)
                curveTo(6f, 6.79f, 7.79f, 5f, 10f, 5f)
                curveTo(12.21f, 5f, 14f, 6.79f, 14f, 9f)
                curveTo(14f, 11.21f, 12.21f, 13f, 10f, 13f)
                close()
                moveTo(10f, 7f)
                curveTo(8.9f, 7f, 8f, 7.9f, 8f, 9f)
                curveTo(8f, 10.1f, 8.9f, 11f, 10f, 11f)
                curveTo(11.1f, 11f, 12f, 10.1f, 12f, 9f)
                curveTo(12f, 7.9f, 11.1f, 7f, 10f, 7f)
                close()
                moveTo(21f, 18f)
                horizontalLineTo(5f)
                verticalLineTo(17f)
                horizontalLineTo(19f)
                verticalLineTo(7f)
                horizontalLineTo(21f)
                curveTo(22.1f, 7f, 23f, 7.9f, 23f, 9f)
                verticalLineTo(16f)
                curveTo(23f, 17.1f, 22.1f, 18f, 21f, 18f)
                close()
            }
        }.build()
    }
}
