package org.example.project

import SystemAudioSystem
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
fun OutputDeviceListUi(
    onDeviceSelected: (AudioDevice.Output) -> Unit
) {
    var devices by remember { mutableStateOf(listOf<AudioDevice.Output>()) }
    LaunchedEffect(true) {
        devices = SystemAudioSystem.listOutputDevices()
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
