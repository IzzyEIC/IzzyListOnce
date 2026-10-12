package uk.listonce.app.model

enum class PlatformGroup(val label: String) {
    SELL("Sprzedaż"),
    LOCAL("Lokalne"),
    FREE("Za darmo (opcjonalnie)"),
}

/**
 * Every portal ListOnce can post to. None of them (except eBay, later) offers a
 * public listing API for private sellers, so each one is driven semi-automatically:
 * we open its app or web page and hand the user ready-made text and photos.
 */
enum class Platform(
    val label: String,
    val group: PlatformGroup,
    val packageName: String?,
    /** Page where a new listing is started when the app isn't installed. */
    val createUrl: String,
    val titleMax: Int? = null,
    val tip: String = "",
) {
    EBAY(
        "eBay", PlatformGroup.SELL, "com.ebay.mobile",
        "https://www.ebay.co.uk/sl/sell", titleMax = 80,
        tip = "Tytuł max 80 znaków. Wybierz 'Buy it now' albo aukcję.",
    ),
    VINTED(
        "Vinted", PlatformGroup.SELL, "fr.vinted",
        "https://www.vinted.co.uk/items/new", titleMax = 60,
        tip = "Głównie ubrania, dodatki i rzeczy dla dzieci. Bez opłat dla sprzedającego.",
    ),
    FACEBOOK_MARKETPLACE(
        "Facebook Marketplace", PlatformGroup.SELL, "com.facebook.katana",
        "https://www.facebook.com/marketplace/create/item", titleMax = 100,
    ),
    GUMTREE(
        "Gumtree", PlatformGroup.SELL, "com.gumtree.android",
        "https://www.gumtree.com/postad", titleMax = 100,
    ),
    PRELOVED(
        "Preloved", PlatformGroup.SELL, "uk.co.preloved",
        "https://www.preloved.co.uk/adverts/new", titleMax = 100,
    ),
    SHPOCK(
        "Shpock", PlatformGroup.SELL, "com.shpock.android",
        "https://www.shpock.com/en-gb/sell", titleMax = 70,
    ),
    FACEBOOK_GROUPS(
        "Lokalne grupy FB", PlatformGroup.LOCAL, "com.facebook.katana",
        "https://www.facebook.com/groups/feed/",
        tip = "Dodaj swoje grupy w Ustawieniach. Wtedy każda otworzy się osobnym przyciskiem.",
    ),
    NEXTDOOR(
        "Nextdoor", PlatformGroup.LOCAL, "com.nextdoor",
        "https://nextdoor.co.uk/for_sale_and_free/",
        tip = "Sekcja 'For Sale & Free'.",
    ),
    FREEGLE(
        "Freegle", PlatformGroup.FREE, "org.ilovefreegle.direct",
        "https://www.ilovefreegle.org/give",
    ),
    FREECYCLE(
        "Freecycle", PlatformGroup.FREE, "org.freecycle.app",
        "https://www.freecycle.org/",
    ),
    OLIO(
        "Olio", PlatformGroup.FREE, "com.olioex.android",
        "https://olioapp.com/",
        tip = "Oddawanie rzeczy niespożywczych w okolicy.",
    ),
}
