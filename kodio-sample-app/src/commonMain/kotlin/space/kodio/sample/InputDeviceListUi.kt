package space.kodio.sample

import space.kodio.core.AudioDevice
import space.kodio.core.SystemAudioSystem
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun InputDeviceListUi(
    onDeviceSelected: (AudioDevice.Input) -> Unit
) {
    var devices by remember { mutableStateOf(listOf<AudioDevice.Input>()) }
    LaunchedEffect(true) {
        devices = SystemAudioSystem.listInputDevices()
    }
    Column {
        devices.forEach {
            TextButton(
                onClick = {
                    onDeviceSelected(it)
                }
            ) {
                Text(it.name)
            }
        }
    }
}
