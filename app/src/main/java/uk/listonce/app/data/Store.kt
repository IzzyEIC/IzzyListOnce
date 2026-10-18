package uk.listonce.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uk.listonce.app.model.Listing
import uk.listonce.app.model.Settings
import java.io.File
import java.util.UUID

@Serializable
private data class StoreFile(
    val listings: List<Listing> = emptyList(),
    val settings: Settings = Settings(),
)

/** Everything lives in one small JSON file plus a photos folder in app storage. */
class Store(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(context.filesDir, "listonce.json")
    val photoDir = File(context.filesDir, "photos").apply { mkdirs() }

    private val initial = load()
    private val _listings = MutableStateFlow(initial.listings)
    private val _settings = MutableStateFlow(initial.settings)
    val listings: StateFlow<List<Listing>> = _listings.asStateFlow()
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private fun load(): StoreFile = runCatching {
        if (file.exists()) json.decodeFromString<StoreFile>(file.readText()) else StoreFile()
    }.getOrDefault(StoreFile())

    private suspend fun persist() = withContext(Dispatchers.IO) {
        val data = StoreFile(_listings.value, _settings.value)
        val tmp = File(file.parentFile, "listonce.json.tmp")
        tmp.writeText(json.encodeToString(data))
        tmp.renameTo(file)
    }

    fun listing(id: String): Listing? = _listings.value.firstOrNull { it.id == id }

    /** New listings go to the top; existing ones are updated in place. */
    suspend fun upsert(listing: Listing) {
        val current = _listings.value
        _listings.value = if (current.any { it.id == listing.id }) {
            current.map { if (it.id == listing.id) listing else it }
        } else {
            listOf(listing) + current
        }
        persist()
    }

    suspend fun delete(id: String) {
        listing(id)?.photos?.forEach { File(photoDir, it).delete() }
        _listings.value = _listings.value.filterNot { it.id == id }
        persist()
    }

    suspend fun saveSettings(settings: Settings) {
        _settings.value = settings
        persist()
    }

    suspend fun updateSettings(change: (Settings) -> Settings) = saveSettings(change(_settings.value))

    /** Copies a picked image into app storage so it survives the picker's temporary grant. */
    suspend fun importPhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val name = "${UUID.randomUUID()}.jpg"
            context.contentResolver.openInputStream(uri)!!.use { input ->
                File(photoDir, name).outputStream().use { input.copyTo(it) }
            }
            name
        }.getOrNull()
    }

    /** An empty file the camera can write into. */
    fun newCameraTarget(): Pair<String, Uri> {
        val name = "${UUID.randomUUID()}.jpg"
        return name to photoUri(name)
    }

    fun photoFile(name: String) = File(photoDir, name)

    fun photoUri(name: String): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", File(photoDir, name))

    /**
     * Copies photos into Pictures/ListOnce so they show up first in every portal's
     * own photo picker — the most reliable way to get them into apps without an API.
     */
    suspend fun exportToGallery(listing: Listing): Int = withContext(Dispatchers.IO) {
        var count = 0
        listing.photos.forEachIndexed { i, name ->
            val src = File(photoDir, name)
            if (!src.exists()) return@forEachIndexed
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "listonce_${listing.id.take(8)}_${i + 1}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ListOnce")
            }
            val target = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values,
            ) ?: return@forEachIndexed
            context.contentResolver.openOutputStream(target)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
                count++
            }
        }
        count
    }
}
