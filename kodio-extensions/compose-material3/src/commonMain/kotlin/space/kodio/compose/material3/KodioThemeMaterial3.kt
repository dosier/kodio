package space.kodio.compose.material3

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import space.kodio.compose.AudioGraphTheme
import space.kodio.compose.KodioColors
import space.kodio.compose.KodioIcons
import space.kodio.compose.KodioTheme
import space.kodio.compose.material3.icons.DeleteForever
import space.kodio.compose.material3.icons.Mic
import space.kodio.compose.material3.icons.Stop

object KodioThemeMaterial3 : KodioTheme() {


    override fun icons(): KodioIcons {
        return KodioIcons(
            stopIcon = Icons.Default.Stop,
            playIcon = Icons.Default.PlayArrow,
            micIcon = Icons.Default.Mic,
            retryIcon = Icons.Default.Refresh,
            discardIcon =  Icons.Default.DeleteForever,
            sendIcon = Icons.AutoMirrored.Filled.Send,
            checkIcon = Icons.Default.Check,
            warningIcon = Icons.Default.Warning,
        )
    }

    @Composable
    override fun colors(): KodioColors {
        val buttonColors = ButtonDefaults.buttonColors()
        return KodioColors(
            buttonContainerColor = buttonColors.containerColor,
            buttonContentColor =  buttonColors.contentColor,
        )
    }

    @Composable
    override fun graphTheme(): AudioGraphTheme {
        return AudioGraphTheme.default(
            brush = Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.onPrimary,
                    MaterialTheme.colorScheme.onSecondary,
                    MaterialTheme.colorScheme.onTertiary
                )
            )
        )
    }
}