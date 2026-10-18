package uk.listonce.app.ebay

data class EbayCategory(val id: String, val name: String, val path: String)

/** One condition the category allows; [enum] is what the Inventory API expects. */
data class ConditionOption(val enum: String, val label: String)

data class AspectDef(
    val name: String,
    val required: Boolean,
    /** Allowed or suggested values; empty when eBay gives none. */
    val values: List<String>,
    /** False when eBay only accepts one of [values]. */
    val freeText: Boolean,
)

data class CategoryInfo(val conditions: List<ConditionOption>, val aspects: List<AspectDef>)

class EbayException(message: String) : Exception(message)
