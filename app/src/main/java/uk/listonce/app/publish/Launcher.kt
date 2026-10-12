package uk.listonce.app.publish

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import uk.listonce.app.model.Platform

/** Hands text, photos and the right app/page over to the user, one portal at a time. */
class Launcher(private val context: Context) {

    fun isInstalled(platform: Platform): Boolean {
        val pkg = platform.packageName ?: return false
        return context.packageManager.getLaunchIntentForPackage(pkg) != null
    }

    fun copy(label: String, text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        // Android 13+ shows its own clipboard confirmation.
        if (android.os.Build.VERSION.SDK_INT < 33) {
            Toast.makeText(context, "Skopiowano: $label", Toast.LENGTH_SHORT).show()
        }
    }

    /** Opens the portal's app if installed, otherwise its "new listing" page. */
    fun open(platform: Platform) {
        val launch = platform.packageName?.let { context.packageManager.getLaunchIntentForPackage(it) }
        if (launch != null) {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            openUrl(platform.createUrl)
        }
    }

    fun openWeb(platform: Platform) = openUrl(platform.createUrl)

    fun openUrl(url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Brak przeglądarki", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shares photos (and the ad text) straight into the portal's app when it accepts
     * shares; otherwise falls back to the system share sheet.
     */
    fun sharePhotos(platform: Platform, photos: List<Uri>, text: String) {
        if (photos.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(photos))
            putExtra(Intent.EXTRA_TEXT, text)
            clipData = ClipData.newRawUri("photo", photos.first()).also { clip ->
                photos.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val direct = Intent(intent).setPackage(platform.packageName)
        val canDirect = platform.packageName != null &&
            context.packageManager.queryIntentActivities(direct, 0).isNotEmpty()
        val toStart = if (canDirect) direct else Intent.createChooser(intent, "Wyślij zdjęcia do…")
        context.startActivity(toStart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
