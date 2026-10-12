package uk.listonce.app.publish

import uk.listonce.app.model.Delivery
import uk.listonce.app.model.Listing
import uk.listonce.app.model.Platform
import uk.listonce.app.model.PlatformGroup
import uk.listonce.app.model.Settings

/** Builds the copy-ready text for one listing on one portal. */
object AdText {

    fun title(listing: Listing, platform: Platform): String {
        val base = listing.title.trim()
        val max = platform.titleMax ?: return base
        return if (base.length <= max) base else base.take(max - 1).trimEnd() + "…"
    }

    fun price(listing: Listing, platform: Platform): String =
        if (listing.free || platform.group == PlatformGroup.FREE) "0" else listing.price.trim()

    fun description(listing: Listing, platform: Platform, settings: Settings): String {
        val lines = mutableListOf<String>()
        lines += listing.description.trim()
        lines += ""

        // Portals with structured fields (eBay, Vinted) ask for these separately,
        // but repeating them in the text helps buyers who only skim.
        lines += "Condition: ${listing.condition.english}"
        if (listing.brand.isNotBlank()) lines += "Brand: ${listing.brand.trim()}"
        if (listing.size.isNotBlank()) lines += "Size: ${listing.size.trim()}"
        if (listing.colour.isNotBlank()) lines += "Colour: ${listing.colour.trim()}"

        val area = listing.area.ifBlank { settings.defaultArea }.trim()
        when (listing.delivery) {
            Delivery.COLLECTION -> lines += "Collection only" + if (area.isNotEmpty()) " from $area" else ""
            Delivery.POSTAGE -> lines += postageLine(listing)
            Delivery.BOTH -> {
                lines += "Collection" + (if (area.isNotEmpty()) " from $area" else "") + " or " +
                    postageLine(listing).replaceFirstChar { it.lowercase() }
            }
        }
        if (settings.signature.isNotBlank()) {
            lines += ""
            lines += settings.signature.trim()
        }
        return lines.joinToString("\n").trim()
    }

    /**
     * Group-style portals (FB groups, Nextdoor, the free ones) have a single text box,
     * so title, price and details go into one post.
     */
    fun post(listing: Listing, platform: Platform, settings: Settings): String {
        val header = if (listing.free || platform.group == PlatformGroup.FREE) {
            "FREE: ${listing.title.trim()}"
        } else {
            "${listing.title.trim()} – £${listing.price.trim()}"
        }
        return header + "\n\n" + description(listing, platform, settings)
    }

    fun usesSinglePost(platform: Platform) =
        platform == Platform.FACEBOOK_GROUPS || platform == Platform.NEXTDOOR ||
            platform.group == PlatformGroup.FREE

    private fun postageLine(listing: Listing): String =
        if (listing.postagePrice.isBlank()) "Postage available"
        else "Postage £${listing.postagePrice.trim()}"
}
