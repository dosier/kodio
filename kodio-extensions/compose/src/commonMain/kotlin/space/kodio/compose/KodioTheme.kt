package space.kodio.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

abstract class KodioTheme {

    abstract fun icons(): KodioIcons

    @Composable
    abstract fun colors(): KodioColors

    @Composable
    abstract fun graphTheme(): AudioGraphTheme
}


class KodioIcons(
    val stopIcon: ImageVector,
    val playIcon: ImageVector,
    val micIcon: ImageVector,
    val retryIcon: ImageVector,
    val discardIcon: ImageVector,
    val sendIcon: ImageVector,
    val checkIcon: ImageVector,
    val warningIcon: ImageVector
)

class KodioColors(
    val buttonContainerColor: Color,
    val buttonContentColor: Color,
)