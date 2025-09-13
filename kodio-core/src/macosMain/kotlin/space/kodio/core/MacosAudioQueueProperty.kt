package space.kodio.core

import kotlinx.cinterop.CVariable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.value
import platform.AudioToolbox.AudioQueuePropertyID
import platform.AudioToolbox.kAudioQueueProperty_CurrentDevice
import platform.AudioToolbox.kAudioQueueProperty_IsRunning
import platform.CoreFoundation.CFStringRefVar
import platform.darwin.UInt32
import platform.darwin.UInt32Var

@OptIn(ExperimentalForeignApi::class)
sealed class MacosAudioQueueProperty<V : CVariable, T>(val id: AudioQueuePropertyID) {

    abstract fun readValue(variable: V): T

    abstract fun alloc(scope: MemScope, value: T): V

    data object IsRunning : MacosAudioQueueProperty<UInt32Var, UInt32>(kAudioQueueProperty_IsRunning) {

        override fun readValue(variable: UInt32Var): UInt32 =
            variable.value

        override fun alloc(scope: MemScope, value: UInt32): UInt32Var =
            scope.alloc<UInt32Var>().apply { this.value = value }
    }

    data object CurrentDevice : MacosAudioQueueProperty<CFStringRefVar, String>(kAudioQueueProperty_CurrentDevice) {

        override fun readValue(variable: CFStringRefVar): String =
            memScoped { cfStringToKString(variable.value!!) }

        override fun alloc(scope: MemScope, value: String): CFStringRefVar =
            scope.alloc<CFStringRefVar>().apply { this.value = value.toCFString() }
    }
}