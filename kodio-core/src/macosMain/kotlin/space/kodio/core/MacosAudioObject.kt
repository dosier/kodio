package space.kodio.core

import kotlinx.cinterop.*
import platform.CoreAudio.*
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreFoundation.CFStringRefVar

@OptIn(ExperimentalForeignApi::class)
sealed class MacosAudioObject(
    val id: AudioDeviceID,
    private val scope: AudioObjectPropertyScope,
) {

    sealed class Device(id: AudioDeviceID, scope: AudioObjectPropertyScope) : MacosAudioObject(id = id, scope = scope) {


        val uid by lazy { getDeviceCFString(selector = kAudioDevicePropertyDeviceUID) }
        val name by lazy { getDeviceCFString(selector = kAudioDevicePropertyDeviceNameCFString) }

        fun getDeviceStreams(): List<Stream> =
            enumerateIds(kAudioDevicePropertyStreams)
                .map(::Stream)
    }

    class Input(id: AudioDeviceID) : Device(id = id, scope = kAudioObjectPropertyScopeInput)
    class Output(id: AudioDeviceID) : Device(id = id, scope = kAudioObjectPropertyScopeGlobal)

    class Stream(id: AudioDeviceID) : MacosAudioObject(id = id, scope = kAudioObjectPropertyScopeGlobal) {

        fun MemScope.getAvailableVirtualFormats(): List<AudioStreamRangedDescription> {
            val address: AudioObjectPropertyAddress = allocPropertyAddress(
                selector = kAudioStreamPropertyAvailableVirtualFormats,
                element = kAudioObjectPropertyElementMain
            )
            val size: UIntVar = allocAndGetPropertyDataSize(address)
            val count = size.value.toInt() / sizeOf<AudioStreamRangedDescription>().toInt()
            val formats: CArrayPointer<AudioStreamRangedDescription> = allocArray(count)
            getPropertyData(address, size, formats)
            return List(size = count) { idx ->
                formats[idx]
            }
        }

        fun getCurrentVirtualFormat(scope: MemScope): AudioStreamBasicDescription = with(scope) {
            val address: AudioObjectPropertyAddress = allocPropertyAddress(
                selector = kAudioStreamPropertyVirtualFormat,
                element = kAudioObjectPropertyElementMain
            )
            val size: UIntVar = alloc { value = sizeOf<AudioStreamRangedDescription>().toUInt() }
            val asbd = alloc<AudioStreamBasicDescription>()
            getPropertyData(address, size, asbd.ptr)
            asbd
        }
    }

    data object Global : MacosAudioObject(
        id = kAudioObjectSystemObject.convert(),
        scope = kAudioObjectPropertyScopeGlobal
    ) {
        fun enumerateDeviceIds() =
            enumerateIds(selector = kAudioHardwarePropertyDevices)
    }

    /**
     * Read a CFString-valued device property (UID or Name). Returns null on error.
     */
    @OptIn(ExperimentalForeignApi::class)
    fun getDeviceCFString(
        selector: AudioObjectPropertySelector,
    ): String? = memScoped {
        val address = allocPropertyAddress(
            selector = selector,
            element = kAudioObjectPropertyElementMain
        )
        val valueVar = alloc<CFStringRefVar>()
        val sizeVar = alloc<UIntVar>().apply { value = sizeOf<CFStringRefVar>().toUInt() }
        getPropertyData(address, sizeVar, valueVar.ptr)
        return cfStringToKString(valueVar.value ?: return null)
    }

    protected fun enumerateIds(selector: AudioObjectPropertySelector) = memScoped {
        val address: AudioObjectPropertyAddress = allocPropertyAddress(
            selector = selector,
            element = kAudioObjectPropertyElementMain
        )
        val size: UIntVar = allocAndGetPropertyDataSize(address)
        val count = size.value.toInt() / sizeOf<AudioObjectIDVar>().toInt()
        if (count > 0) {
            val ids: CArrayPointer<AudioObjectIDVar> = allocArray(count)
            getPropertyData(address, size, ids)
            List(size = count) { idx ->
                ids[idx]
            }
        } else
            emptyList()
    }

    protected fun MemScope.allocPropertyAddress(selector: AudioObjectPropertySelector, element: AudioObjectPropertyElement): AudioObjectPropertyAddress =
        alloc {
            mSelector = selector
            mScope = scope
            mElement = element
        }

    protected fun MemScope.allocAndGetPropertyDataSize(address: AudioObjectPropertyAddress): UIntVar {
        val dataSizeVar: UIntVar = alloc()
        runAndCheckOsStatus {
            AudioObjectGetPropertyDataSize(
                inObjectID = id,
                inAddress = address.ptr,
                inQualifierDataSize = 0u,
                inQualifierData = null,
                outDataSize = dataSizeVar.ptr
            )
        }
        return dataSizeVar
    }

    protected fun getPropertyData(
        address: AudioObjectPropertyAddress,
        size: UIntVar,
        out: CPointer<*>
    ) {
        runAndCheckOsStatus {
            AudioObjectGetPropertyData(
                inObjectID = id,
                inAddress = address.ptr,
                inQualifierDataSize = 0u,
                inQualifierData = null,
                ioDataSize = size.ptr,
                outData = out
            )
        }
    }
}
