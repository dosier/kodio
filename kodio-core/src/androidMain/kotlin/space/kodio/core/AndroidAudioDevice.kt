package space.kodio.core

import android.media.AudioDeviceInfo

internal fun AudioDeviceInfo.toInputDevice(): AudioDevice.Input = AudioDevice.Input(
    id = this.id.toString(),
    name = this.productName.toString() + " (${getTypeName(type)})",
    formatSupport = AudioFormatSupport.Unknown // Android doesn't have a simple query API for this
)

internal fun AudioDeviceInfo.toOutputDevice(): AudioDevice.Output = AudioDevice.Output(
    id = this.id.toString(),
    name = this.productName.toString() + " (${getTypeName(type)})",
    formatSupport = AudioFormatSupport.Unknown
)

internal fun getTypeName(type: Int): String {
    return when (type) {
        0 -> "UNKNOWN"
        1 -> "BUILTIN_EARPIECE"
        2 -> "BUILTIN_SPEAKER"
        3 -> "WIRED_HEADSET"
        4 -> "WIRED_HEADPHONES"
        5 -> "LINE_ANALOG"
        6 -> "LINE_DIGITAL"
        7 -> "BLUETOOTH_SCO"
        8 -> "BLUETOOTH_A2DP"
        9 -> "HDMI"
        10 -> "HDMI_ARC"
        11 -> "USB_DEVICE"
        12 -> "USB_ACCESSORY"
        13 -> "DOCK"
        14 -> "FM"
        15 -> "BUILTIN_MIC"
        16 -> "FM_TUNER"
        17 -> "TV_TUNER"
        18 -> "TELEPHONY"
        19 -> "AUX_LINE"
        20 -> "IP"
        21 -> "BUS"
        22 -> "USB_HEADSET"
        23 -> "HEARING_AID"
        24 -> "BUILTIN_SPEAKER_SAFE"
        25 -> "REMOTE_SUBMIX"
        26 -> "BLE_HEADSET"
        27 -> "BLE_SPEAKER"
        29 -> "HDMI_EARC"
        30 -> "BLE_BROADCAST"
        31 -> "DOCK_ANALOG"
        else -> "Unknown Type"
    }
}