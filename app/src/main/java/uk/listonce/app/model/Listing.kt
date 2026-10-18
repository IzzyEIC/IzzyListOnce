package uk.listonce.app.model

import kotlinx.serialization.Serializable
import java.util.UUID

enum class Condition(val label: String, val english: String) {
    NEW_WITH_TAGS("Nowy z metką", "New with tags"),
    NEW("Nowy bez metki", "New without tags"),
    VERY_GOOD("Bardzo dobry", "Very good"),
    GOOD("Dobry", "Good"),
    SATISFACTORY("Zadowalający", "Satisfactory"),
}

enum class Delivery(val label: String) {
    COLLECTION("Tylko odbiór osobisty"),
    POSTAGE("Tylko wysyłka"),
    BOTH("Odbiór lub wysyłka"),
}

enum class PostState(val label: String) {
    POSTED("Wystawione"),
    SOLD("Sprzedane tutaj"),
    REMOVED("Usunięte"),
}

@Serializable
data class Listing(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val description: String = "",
    /** Pounds as typed, e.g. "12.50". Ignored when [free] is true. */
    val price: String = "",
    val free: Boolean = false,
    val condition: Condition = Condition.GOOD,
    val category: String = "",
    val brand: String = "",
    val size: String = "",
    val colour: String = "",
    val delivery: Delivery = Delivery.BOTH,
    val postagePrice: String = "",
    val area: String = "",
    /** File names inside filesDir/photos. */
    val photos: List<String> = emptyList(),
    val targets: Set<Platform> = emptySet(),
    val posts: Map<Platform, PostState> = emptyMap(),
    val ebay: EbayListing = EbayListing(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isSold: Boolean get() = posts.values.any { it == PostState.SOLD }

    /** Platforms where the ad is still live — these need removing once it sells. */
    val stillLive: List<Platform>
        get() = posts.filterValues { it == PostState.POSTED }.keys.sortedBy { it.ordinal }
}

@Serializable
data class FbGroup(val name: String, val url: String)

@Serializable
data class Settings(
    val defaultArea: String = "",
    val fbGroups: List<FbGroup> = emptyList(),
    val signature: String = "",
    /** Adres serwera ListOnce (Cloudflare Worker), np. https://listonce-ebay.xyz.workers.dev */
    val serverUrl: String = "",
    val ebay: EbayAccount = EbayAccount(),
)

/** eBay-specific choices for one listing, plus the ids eBay gave it once published. */
@Serializable
data class EbayListing(
    val categoryId: String = "",
    val categoryName: String = "",
    /** eBay ConditionEnum, e.g. USED_EXCELLENT. */
    val condition: String = "",
    val aspects: Map<String, List<String>> = emptyMap(),
    val sku: String = "",
    val offerId: String = "",
    val listingId: String = "",
) {
    val isLive: Boolean get() = listingId.isNotEmpty()
}

@Serializable
data class EbayAccount(
    /** "sandbox" or "production" — reported by the server at connect time. */
    val env: String = "",
    val accessToken: String = "",
    val accessExpiresAt: Long = 0,
    val refreshToken: String = "",
    val refreshExpiresAt: Long = 0,
    val userName: String = "",
    val fulfillmentPolicyId: String = "",
    val paymentPolicyId: String = "",
    val returnPolicyId: String = "",
    val locationKey: String = "",
    val postcode: String = "",
) {
    val connected: Boolean get() = refreshToken.isNotEmpty()
    val ready: Boolean
        get() = connected && fulfillmentPolicyId.isNotEmpty() && paymentPolicyId.isNotEmpty() &&
            returnPolicyId.isNotEmpty() && locationKey.isNotEmpty()
}
