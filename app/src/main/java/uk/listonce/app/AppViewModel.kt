package uk.listonce.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.listonce.app.data.Store
import uk.listonce.app.ebay.EbayClient
import uk.listonce.app.model.EbayListing
import uk.listonce.app.model.Listing
import uk.listonce.app.model.Platform
import uk.listonce.app.model.PlatformGroup
import uk.listonce.app.model.PostState
import uk.listonce.app.model.Settings
import uk.listonce.app.publish.Launcher

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    val launcher = Launcher(app)
    val ebay = EbayClient(store)
    val listings = store.listings
    val settings = store.settings

    /** One-off message shown as a snackbar (eBay connected, item sold on eBay…). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun messageShown() { _message.value = null }

    fun save(listing: Listing) = viewModelScope.launch { store.upsert(listing) }

    fun delete(id: String) = viewModelScope.launch { store.delete(id) }

    /** Settings change through a transform so eBay token refreshes in the background aren't overwritten. */
    fun updateSettings(change: (Settings) -> Settings) = viewModelScope.launch { store.updateSettings(change) }

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

    // --- eBay ---

    /** Called with listonce://ebay?... when the browser comes back from eBay sign-in. */
    fun handleEbayLink(uri: Uri) = viewModelScope.launch {
        _message.value = runCatching { ebay.handleCallback(uri) }
            .fold({ "Połączono z eBay ✓ Teraz kliknij „Przygotuj konto”." }, { "eBay: ${it.message}" })
    }

    fun ebaySetup(postcode: String, onDone: (String?) -> Unit) = viewModelScope.launch {
        onDone(runCatching { ebay.setup(postcode) }.exceptionOrNull()?.message)
    }

    fun disconnectEbay() = viewModelScope.launch { ebay.disconnect() }

    suspend fun publishToEbay(id: String, choice: EbayListing, price: String, onStep: (String) -> Unit) {
        val l = store.listing(id) ?: return
        val withPrice = l.copy(price = price.trim(), ebay = choice)
        store.upsert(withPrice)
        val published = ebay.publish(withPrice, onStep)
        val latest = store.listing(id) ?: return
        store.upsert(
            latest.copy(
                ebay = published,
                targets = latest.targets + Platform.EBAY,
                posts = latest.posts + (Platform.EBAY to PostState.POSTED),
            ),
        )
    }

    /** Ends the eBay listing (e.g. sold elsewhere) and marks it removed. */
    fun endOnEbay(id: String) = viewModelScope.launch {
        val l = store.listing(id) ?: return@launch
        runCatching { ebay.withdraw(l.ebay.offerId) }
            .onSuccess {
                val latest = store.listing(id) ?: return@onSuccess
                val posts = if (latest.posts[Platform.EBAY] == PostState.SOLD) latest.posts
                else latest.posts + (Platform.EBAY to PostState.REMOVED)
                store.upsert(latest.copy(ebay = latest.ebay.copy(listingId = ""), posts = posts))
                _message.value = "Zakończono ogłoszenie na eBay ✓"
            }
            .onFailure { _message.value = "eBay: ${it.message}" }
    }

    /** Looks for eBay orders of our live listings and marks them sold. */
    fun checkEbaySales() = viewModelScope.launch {
        if (!settings.value.ebay.connected) return@launch
        val live = listings.value.filter { it.ebay.isLive && it.posts[Platform.EBAY] == PostState.POSTED }
        if (live.isEmpty()) return@launch
        val sold = runCatching { ebay.soldListingIds() }.getOrElse { return@launch }
        live.filter { it.ebay.listingId in sold }.forEach { l ->
            val latest = store.listing(l.id) ?: return@forEach
            store.upsert(latest.copy(posts = latest.posts + (Platform.EBAY to PostState.SOLD)))
            val others = latest.stillLive.filter { it != Platform.EBAY }
            _message.value = "Sprzedane na eBay: ${l.title}" +
                if (others.isNotEmpty()) " — usuń z: ${others.joinToString { it.label }}" else ""
        }
    }
}
