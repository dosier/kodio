package space.kodio.core

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSError

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun <T> runErrorCatching(block: (CPointer<ObjCObjectVar<NSError?>>) -> T): Result<T> {
    memScoped {
        val errorVar = alloc<ObjCObjectVar<NSError?>>()
        val result = block(errorVar.ptr)
        val errorValue = errorVar.value
        return if (errorValue != null)
            Result.failure(IosException(errorValue))
        else
            Result.success(result)
    }
}

internal class IosException(error: NSError) : Exception(error.localizedDescription)