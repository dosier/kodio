package space.kodio.sample

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeContent
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Workaround for FileKit bug: the DocumentPickerDelegate can receive both
 * `didPickDocumentAtURL` and `didPickDocumentsAtURLs` callbacks from iOS,
 * causing a double-resume crash. This implementation adds a `hasFinished`
 * guard (same pattern as FileKit PR #403 for PhPickerDelegate).
 */

private var activeDelegate: NSObject? = null

actual suspend fun pickFile(extensions: List<String>): PlatformFile? =
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            var hasFinished = false

            fun finish(file: PlatformFile?) {
                if (hasFinished) return
                hasFinished = true
                activeDelegate = null
                continuation.resume(file)
            }

            val utTypes = extensions.mapNotNull { ext ->
                UTType.typeWithFilenameExtension(ext)
            }.ifEmpty { listOf(UTTypeContent) }

            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = utTypes
            )

            val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                override fun documentPicker(
                    controller: UIDocumentPickerViewController,
                    didPickDocumentAtURL: NSURL,
                ) {
                    finish(PlatformFile(didPickDocumentAtURL))
                }

                override fun documentPicker(
                    controller: UIDocumentPickerViewController,
                    didPickDocumentsAtURLs: List<*>,
                ) {
                    val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                    finish(url?.let { PlatformFile(it) })
                }

                override fun documentPickerWasCancelled(
                    controller: UIDocumentPickerViewController,
                ) {
                    finish(null)
                }
            }

            activeDelegate = delegate
            picker.delegate = delegate
            picker.allowsMultipleSelection = false

            val topVC = findTopViewController()
            topVC?.presentViewController(picker, animated = true, completion = null)

            continuation.invokeOnCancellation {
                activeDelegate = null
                picker.dismissViewControllerAnimated(true, completion = null)
            }
        }
    }

private fun findTopViewController(): UIViewController? {
    val keyWindow = UIApplication.sharedApplication.windows
        .firstOrNull { (it as? platform.UIKit.UIWindow)?.isKeyWindow() == true }
        as? platform.UIKit.UIWindow
    var vc = keyWindow?.rootViewController
    while (vc?.presentedViewController != null) {
        vc = vc.presentedViewController
    }
    return vc
}
