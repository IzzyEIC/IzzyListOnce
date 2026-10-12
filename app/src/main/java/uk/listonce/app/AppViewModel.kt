package uk.listonce.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import uk.listonce.app.data.Store
import uk.listonce.app.model.Listing
import uk.listonce.app.model.Platform
import uk.listonce.app.model.PlatformGroup
import uk.listonce.app.model.PostState
import uk.listonce.app.model.Settings
import uk.listonce.app.publish.Launcher

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    val launcher = Launcher(app)
    val listings = store.listings
    val settings = store.settings

    fun save(listing: Listing) = viewModelScope.launch { store.upsert(listing) }

    fun delete(id: String) = viewModelScope.launch { store.delete(id) }

    fun saveSettings(s: Settings) = viewModelScope.launch { store.saveSettings(s) }

    fun setState(id: String, platform: Platform, state: PostState?) = viewModelScope.launch {
        val l = store.listing(id) ?: return@launch
        val posts = if (state == null) l.posts - platform else l.posts + (platform to state)
        store.upsert(l.copy(posts = posts))
    }

    /** "Didn't sell" path: switch the item to free and add the give-away portals. */
    fun giveAway(id: String) = viewModelScope.launch {
        val l = store.listing(id) ?: return@launch
        val freeOnes = Platform.entries.filter { it.group == PlatformGroup.FREE }
        store.upsert(l.copy(free = true, targets = l.targets + freeOnes))
    }

    suspend fun importPhotos(uris: List<Uri>): List<String> = uris.mapNotNull { store.importPhoto(it) }

    fun exportToGallery(listing: Listing, onDone: (Int) -> Unit) = viewModelScope.launch {
        onDone(store.exportToGallery(listing))
    }
}
