package uk.listonce.app.ebay

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import uk.listonce.app.data.Store
import uk.listonce.app.model.Condition
import uk.listonce.app.model.EbayAccount
import uk.listonce.app.model.EbayListing
import uk.listonce.app.model.Listing
import uk.listonce.app.publish.AdText
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * eBay UK through the official REST Sell APIs, signed in as the user.
 * The app talks to eBay directly; only token exchange/refresh goes through the ListOnce server,
 * because that needs the app secret.
 */
class EbayClient(private val store: Store) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()

    private val account: EbayAccount get() = store.settings.value.ebay
    private val sandbox: Boolean get() = account.env == "sandbox"
    private val api: String get() = if (sandbox) "https://api.sandbox.ebay.com" else "https://api.ebay.com"
    private val media: String get() = if (sandbox) "https://apim.sandbox.ebay.com" else "https://apim.ebay.com"

    fun connectUrl(): String = "${store.settings.value.serverUrl}/ebay/auth/start"

    fun listingUrl(listingId: String): String =
        if (sandbox) "https://sandbox.ebay.co.uk/itm/$listingId" else "https://www.ebay.co.uk/itm/$listingId"

    // ---------------------------------------------------------------- sign-in

    suspend fun handleCallback(uri: Uri) {
        uri.getQueryParameter("error")?.let {
            throw EbayException(if (it == "declined") "Odmówiono zgody w eBay" else it)
        }
        val access = uri.getQueryParameter("access_token") ?: throw EbayException("Brak tokenu")
        val now = System.currentTimeMillis()
        store.updateSettings { s ->
            s.copy(
                ebay = s.ebay.copy(
                    env = uri.getQueryParameter("env") ?: "sandbox",
                    accessToken = access,
                    accessExpiresAt = now + (uri.getQueryParameter("expires_in")?.toLongOrNull() ?: 7200) * 1000,
                    refreshToken = uri.getQueryParameter("refresh_token").orEmpty(),
                    refreshExpiresAt = now +
                        (uri.getQueryParameter("refresh_token_expires_in")?.toLongOrNull() ?: 0) * 1000,
                ),
            )
        }
    }

    suspend fun disconnect() = store.updateSettings { it.copy(ebay = EbayAccount()) }

    private suspend fun token(): String {
        val acc = account
        if (!acc.connected) throw EbayException("Najpierw połącz konto eBay w Ustawieniach")
        if (acc.accessToken.isNotEmpty() && acc.accessExpiresAt - 120_000 > System.currentTimeMillis()) {
            return acc.accessToken
        }
        val body = buildJsonObject { put("refresh_token", acc.refreshToken) }.toString()
        val res = send(
            Request.Builder()
                .url("${store.settings.value.serverUrl}/ebay/token/refresh")
                .post(body.toRequestBody(jsonType))
                .build(),
        )
        val obj = res.body?.jsonObject ?: throw EbayException("Serwer nie odpowiedział")
        val access = obj.str("access_token")
            ?: throw EbayException("Nie udało się odświeżyć logowania eBay — połącz konto ponownie (${obj.str("error_description") ?: obj.str("error")})")
        val expires = obj["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 7200
        store.updateSettings {
            it.copy(ebay = it.ebay.copy(accessToken = access, accessExpiresAt = System.currentTimeMillis() + expires * 1000))
        }
        return access
    }

    // ---------------------------------------------------------------- account setup

    /**
     * One-time preparation: business policies switched on, a location to ship from, and
     * one fulfilment / payment / return policy (existing ones reused, simple defaults created).
     */
    suspend fun setup(postcode: String) {
        // Business policies must be enabled; "already opted in" errors are fine.
        runCatching {
            call("POST", "$api/sell/account/v1/program/opt_in",
                buildJsonObject { put("programType", "SELLING_POLICY_MANAGEMENT") })
        }

        val locationKey = "listonce-home"
        val existing = runCatching { call("GET", "$api/sell/inventory/v1/location/$locationKey") }.getOrNull()
        if (existing == null) {
            call("POST", "$api/sell/inventory/v1/location/$locationKey", buildJsonObject {
                putJsonObject("location") {
                    putJsonObject("address") {
                        put("postalCode", postcode)
                        put("country", "GB")
                    }
                }
                put("name", "ListOnce")
                put("merchantLocationStatus", "ENABLED")
                putJsonArray("locationTypes") { add("WAREHOUSE") }
            })
        }

        val fulfillment = firstPolicy("fulfillment_policy", "fulfillmentPolicies", "fulfillmentPolicyId")
            ?: createPolicy("fulfillment_policy", "fulfillmentPolicyId", buildJsonObject {
                put("name", "ListOnce – Royal Mail 2nd Class")
                put("marketplaceId", "EBAY_GB")
                putJsonArray("categoryTypes") { add(buildJsonObject { put("name", "ALL_EXCLUDING_MOTORS_VEHICLES") }) }
                putJsonObject("handlingTime") { put("value", 2); put("unit", "DAY") }
                putJsonArray("shippingOptions") {
                    add(buildJsonObject {
                        put("optionType", "DOMESTIC")
                        put("costType", "FLAT_RATE")
                        putJsonArray("shippingServices") {
                            add(buildJsonObject {
                                put("sortOrder", 1)
                                put("shippingCarrierCode", "RoyalMail")
                                put("shippingServiceCode", "UK_RoyalMailSecondClassStandard")
                                putJsonObject("shippingCost") { put("value", "3.50"); put("currency", "GBP") }
                            })
                        }
                    })
                }
                put("localPickup", true)
            })

        val payment = firstPolicy("payment_policy", "paymentPolicies", "paymentPolicyId")
            ?: createPolicy("payment_policy", "paymentPolicyId", buildJsonObject {
                put("name", "ListOnce – immediate payment")
                put("marketplaceId", "EBAY_GB")
                putJsonArray("categoryTypes") { add(buildJsonObject { put("name", "ALL_EXCLUDING_MOTORS_VEHICLES") }) }
                put("immediatePay", true)
            })

        val returns = firstPolicy("return_policy", "returnPolicies", "returnPolicyId")
            ?: createPolicy("return_policy", "returnPolicyId", buildJsonObject {
                put("name", "ListOnce – no returns")
                put("marketplaceId", "EBAY_GB")
                putJsonArray("categoryTypes") { add(buildJsonObject { put("name", "ALL_EXCLUDING_MOTORS_VEHICLES") }) }
                put("returnsAccepted", false)
            })

        store.updateSettings {
            it.copy(
                ebay = it.ebay.copy(
                    postcode = postcode,
                    locationKey = locationKey,
                    fulfillmentPolicyId = fulfillment,
                    paymentPolicyId = payment,
                    returnPolicyId = returns,
                ),
            )
        }
    }

    private suspend fun firstPolicy(path: String, listField: String, idField: String): String? {
        val res = call("GET", "$api/sell/account/v1/$path?marketplace_id=EBAY_GB")
        return res?.jsonObject?.get(listField)?.jsonArray?.firstOrNull()?.jsonObject?.str(idField)
    }

    private suspend fun createPolicy(path: String, idField: String, body: JsonObject): String =
        call("POST", "$api/sell/account/v1/$path", body)?.jsonObject?.str(idField)
            ?: throw EbayException("eBay nie utworzył zasad ($path)")

    // ---------------------------------------------------------------- category & specifics

    suspend fun suggestCategories(query: String): List<EbayCategory> {
        if (query.isBlank()) return emptyList()
        val q = URLEncoder.encode(query.take(100), "UTF-8")
        val res = call("GET", "$api/commerce/taxonomy/v1/category_tree/$TREE_GB/get_category_suggestions?q=$q")
        return res?.jsonObject?.get("categorySuggestions")?.jsonArray.orEmpty().mapNotNull { s ->
            val cat = s.jsonObject["category"]?.jsonObject ?: return@mapNotNull null
            val ancestors = s.jsonObject["categoryTreeNodeAncestors"]?.jsonArray.orEmpty()
                .mapNotNull { it.jsonObject.str("categoryName") }.reversed()
            val name = cat.str("categoryName") ?: return@mapNotNull null
            EbayCategory(cat.str("categoryId") ?: return@mapNotNull null, name, (ancestors + name).joinToString(" › "))
        }
    }

    suspend fun categoryInfo(categoryId: String): CategoryInfo {
        val aspectsRes = call("GET",
            "$api/commerce/taxonomy/v1/category_tree/$TREE_GB/get_item_aspects_for_category?category_id=$categoryId")
        val aspects = aspectsRes?.jsonObject?.get("aspects")?.jsonArray.orEmpty().mapNotNull { a ->
            val o = a.jsonObject
            val name = o.str("localizedAspectName") ?: return@mapNotNull null
            val c = o["aspectConstraint"]?.jsonObject
            AspectDef(
                name = name,
                required = c?.get("aspectRequired")?.jsonPrimitive?.contentOrNull == "true",
                values = o["aspectValues"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.str("localizedValue") },
                freeText = c?.str("aspectMode") != "SELECTION_ONLY",
            )
        }

        val condRes = call("GET",
            "$api/sell/metadata/v1/marketplace/EBAY_GB/get_item_condition_policies?filter=categoryIds:%7B$categoryId%7D")
        val conditions = condRes?.jsonObject?.get("itemConditionPolicies")?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("itemConditions")?.jsonArray.orEmpty().mapNotNull { c ->
                val id = c.jsonObject.str("conditionId") ?: return@mapNotNull null
                val enum = CONDITION_IDS[id] ?: return@mapNotNull null
                ConditionOption(enum, c.jsonObject.str("conditionDescription") ?: enum)
            }
            .ifEmpty { listOf(ConditionOption("NEW", "New"), ConditionOption("USED_GOOD", "Used")) }

        return CategoryInfo(conditions, aspects)
    }

    /** Closest allowed eBay condition to what was picked in the ListOnce form. */
    fun defaultCondition(c: Condition, allowed: List<ConditionOption>): String {
        val preferences = when (c) {
            Condition.NEW_WITH_TAGS -> listOf("NEW", "NEW_OTHER")
            Condition.NEW -> listOf("NEW_OTHER", "NEW", "LIKE_NEW")
            Condition.VERY_GOOD -> listOf("PRE_OWNED_EXCELLENT", "USED_EXCELLENT", "USED_VERY_GOOD", "LIKE_NEW")
            Condition.GOOD -> listOf("USED_GOOD", "USED_VERY_GOOD", "PRE_OWNED_GOOD", "PRE_OWNED_EXCELLENT", "USED_EXCELLENT")
            Condition.SATISFACTORY -> listOf("USED_ACCEPTABLE", "PRE_OWNED_FAIR", "USED_GOOD")
        }
        return preferences.firstOrNull { p -> allowed.any { it.enum == p } } ?: allowed.firstOrNull()?.enum.orEmpty()
    }

    /** Fills brand / size / colour from the ListOnce form where the category asks for them. */
    fun prefillAspects(
        listing: Listing,
        defs: List<AspectDef>,
        current: Map<String, List<String>>,
    ): Map<String, List<String>> {
        val out = current.toMutableMap()
        fun fill(names: List<String>, value: String) {
            if (value.isBlank()) return
            val def = defs.firstOrNull { d -> names.any { it.equals(d.name, ignoreCase = true) } } ?: return
            if (out.containsKey(def.name)) return
            val v = if (!def.freeText) def.values.firstOrNull { it.equals(value.trim(), ignoreCase = true) } else value.trim()
            if (v != null) out[def.name] = listOf(v)
        }
        fill(listOf("Brand"), listing.brand.ifBlank { "Unbranded" })
        fill(listOf("Size", "UK Size", "Size (Women's)", "Size (Men's)"), listing.size)
        fill(listOf("Colour", "Color", "Main Colour"), listing.colour)
        return out
    }

    // ---------------------------------------------------------------- publish

    suspend fun publish(listing: Listing, onStep: (String) -> Unit): EbayListing {
        val acc = account
        if (!acc.ready) throw EbayException("Przygotuj konto eBay w Ustawieniach")
        val e = listing.ebay
        val sku = e.sku.ifEmpty { "LO-" + listing.id.take(8) }

        val imageUrls = listing.photos.take(24).mapIndexed { i, name ->
            onStep("Wysyłam zdjęcia ${i + 1}/${listing.photos.size.coerceAtMost(24)}…")
            uploadImage(store.photoFile(name))
        }

        onStep("Tworzę przedmiot…")
        val settings = store.settings.value
        call("PUT", "$api/sell/inventory/v1/inventory_item/$sku", buildJsonObject {
            putJsonObject("availability") {
                putJsonObject("shipToLocationAvailability") { put("quantity", 1) }
            }
            put("condition", e.condition)
            if (!e.condition.startsWith("NEW")) {
                put("conditionDescription", "Condition: ${listing.condition.english}")
            }
            putJsonObject("product") {
                put("title", AdText.title(listing, uk.listonce.app.model.Platform.EBAY))
                put("description", AdText.description(listing, uk.listonce.app.model.Platform.EBAY, settings))
                putJsonObject("aspects") {
                    e.aspects.forEach { (k, vs) -> put(k, JsonArray(vs.map { JsonPrimitive(it) })) }
                }
                putJsonArray("imageUrls") { imageUrls.forEach { add(it) } }
            }
        })

        onStep("Tworzę ofertę…")
        val offerBody = buildJsonObject {
            put("sku", sku)
            put("marketplaceId", "EBAY_GB")
            put("format", "FIXED_PRICE")
            put("availableQuantity", 1)
            put("categoryId", e.categoryId)
            put("listingDescription", AdText.description(listing, uk.listonce.app.model.Platform.EBAY, settings))
            put("listingDuration", "GTC")
            put("merchantLocationKey", acc.locationKey)
            putJsonObject("listingPolicies") {
                put("fulfillmentPolicyId", acc.fulfillmentPolicyId)
                put("paymentPolicyId", acc.paymentPolicyId)
                put("returnPolicyId", acc.returnPolicyId)
            }
            putJsonObject("pricingSummary") {
                putJsonObject("price") { put("value", listing.price.trim()); put("currency", "GBP") }
            }
        }
        val offerId = if (e.offerId.isNotEmpty()) {
            call("PUT", "$api/sell/inventory/v1/offer/${e.offerId}", offerBody)
            e.offerId
        } else {
            call("POST", "$api/sell/inventory/v1/offer", offerBody)?.jsonObject?.str("offerId")
                ?: throw EbayException("eBay nie zwrócił numeru oferty")
        }

        onStep("Publikuję…")
        val listingId = call("POST", "$api/sell/inventory/v1/offer/$offerId/publish")
            ?.jsonObject?.str("listingId") ?: throw EbayException("eBay nie zwrócił numeru aukcji")

        return e.copy(sku = sku, offerId = offerId, listingId = listingId)
    }

    /** Ends the live listing; the offer and item stay, so it can be relisted later. */
    suspend fun withdraw(offerId: String) {
        if (offerId.isEmpty()) throw EbayException("To ogłoszenie nie było wystawione przez ListOnce")
        call("POST", "$api/sell/inventory/v1/offer/$offerId/withdraw")
    }

    /** eBay listing ids that appear in orders from the last 30 days. */
    suspend fun soldListingIds(): Set<String> {
        val since = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(java.time.ZoneOffset.UTC)
            .format(java.time.Instant.now().minus(java.time.Duration.ofDays(30)))
        val filter = URLEncoder.encode("creationdate:[$since..]", "UTF-8")
        val res = call("GET", "$api/sell/fulfillment/v1/order?filter=$filter&limit=200")
        return res?.jsonObject?.get("orders")?.jsonArray.orEmpty()
            .flatMap { it.jsonObject["lineItems"]?.jsonArray.orEmpty() }
            .mapNotNull { it.jsonObject.str("legacyItemId") }
            .toSet()
    }

    private suspend fun uploadImage(file: java.io.File): String {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("image", file.name, file.asRequestBody("image/jpeg".toMediaType()))
            .build()
        val res = send(
            Request.Builder()
                .url("$media/commerce/media/v1_beta/image/create_image_from_file")
                .header("Authorization", "Bearer ${token()}")
                .post(body)
                .build(),
        )
        res.throwIfError()
        res.body?.jsonObject?.str("imageUrl")?.let { return it }
        // Some responses only carry the location of the new image; ask for its URL.
        val location = res.location ?: throw EbayException("eBay nie przyjął zdjęcia")
        val img = send(Request.Builder().url(location).header("Authorization", "Bearer ${token()}").get().build())
        img.throwIfError()
        return img.body?.jsonObject?.str("imageUrl") ?: throw EbayException("Brak adresu zdjęcia z eBay")
    }

    // ---------------------------------------------------------------- HTTP plumbing

    private class Res(val code: Int, val body: JsonElement?, val location: String?, val raw: String)

    private suspend fun call(method: String, url: String, body: JsonElement? = null): JsonElement? {
        val reqBody = when {
            body != null -> body.toString().toRequestBody(jsonType)
            method == "POST" || method == "PUT" -> "".toRequestBody(jsonType)
            else -> null
        }
        val res = send(
            Request.Builder()
                .url(url)
                .method(method, reqBody)
                .header("Authorization", "Bearer ${token()}")
                .header("Content-Language", "en-GB")
                .header("Accept-Language", "en-GB")
                .header("X-EBAY-C-MARKETPLACE-ID", "EBAY_GB")
                .build(),
        )
        res.throwIfError()
        return res.body
    }

    private suspend fun send(request: Request): Res = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { r ->
            val raw = r.body?.string().orEmpty()
            val parsed = if (raw.isBlank()) null else runCatching { json.parseToJsonElement(raw) }.getOrNull()
            Res(r.code, parsed, r.header("Location"), raw)
        }
    }

    private fun Res.throwIfError() {
        if (code in 200..299) return
        val errors = (body as? JsonObject)?.get("errors")?.jsonArray.orEmpty().mapNotNull {
            val o = it.jsonObject
            o.str("longMessage") ?: o.str("message")
        }
        throw EbayException(errors.joinToString("\n").ifEmpty { "eBay HTTP $code ${raw.take(200)}" })
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    companion object {
        /** eBay's category tree id for the UK marketplace. */
        const val TREE_GB = "3"

        /** Numeric condition ids (Metadata API) → ConditionEnum (Inventory API). */
        val CONDITION_IDS = mapOf(
            "1000" to "NEW",
            "1500" to "NEW_OTHER",
            "1750" to "NEW_WITH_DEFECTS",
            "2750" to "LIKE_NEW",
            "2990" to "PRE_OWNED_EXCELLENT",
            "3000" to "USED_EXCELLENT",
            "3010" to "PRE_OWNED_FAIR",
            "4000" to "USED_VERY_GOOD",
            "5000" to "USED_GOOD",
            "6000" to "USED_ACCEPTABLE",
            "7000" to "FOR_PARTS_OR_NOT_WORKING",
        )
    }
}
