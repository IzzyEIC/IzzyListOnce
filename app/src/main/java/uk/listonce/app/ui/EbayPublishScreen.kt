package uk.listonce.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uk.listonce.app.AppViewModel
import uk.listonce.app.ebay.AspectDef
import uk.listonce.app.ebay.CategoryInfo
import uk.listonce.app.ebay.EbayCategory
import uk.listonce.app.model.Listing

/**
 * One screen that turns a ListOnce listing into a live eBay listing:
 * category → condition → required item specifics → price → publish.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EbayPublishScreen(vm: AppViewModel, listingId: String, onBack: () -> Unit) {
    val listings by vm.listings.collectAsState()
    val listing = listings.firstOrNull { it.id == listingId } ?: run {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf(listing.title) }
    var suggestions by remember { mutableStateOf<List<EbayCategory>>(emptyList()) }
    var category by remember {
        mutableStateOf(
            listing.ebay.categoryId.takeIf { it.isNotEmpty() }
                ?.let { EbayCategory(it, listing.ebay.categoryName, listing.ebay.categoryName) },
        )
    }
    var info by remember { mutableStateOf<CategoryInfo?>(null) }
    var condition by remember { mutableStateOf(listing.ebay.condition) }
    var aspects by remember { mutableStateOf(listing.ebay.aspects) }
    var price by remember { mutableStateOf(listing.price) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun run(label: String, block: suspend () -> Unit) {
        scope.launch {
            busy = label; error = null
            try { block() } catch (e: Exception) { error = e.message ?: e.toString() }
            busy = null
        }
    }

    fun loadSuggestions() = run("Szukam kategorii…") { suggestions = vm.ebay.suggestCategories(query) }

    fun pick(c: EbayCategory) = run("Wczytuję wymagania kategorii…") {
        category = c
        val i = vm.ebay.categoryInfo(c.id)
        info = i
        if (i.conditions.none { it.enum == condition }) {
            condition = vm.ebay.defaultCondition(listing.condition, i.conditions)
        }
        aspects = vm.ebay.prefillAspects(listing, i.aspects, aspects)
    }

    LaunchedEffect(Unit) {
        val c = category
        if (c != null) pick(c) else if (query.isNotBlank()) loadSuggestions()
    }

    val missing = info?.aspects.orEmpty().filter { it.required && aspects[it.name].isNullOrEmpty() }
    val canPublish = busy == null && category != null && info != null && condition.isNotEmpty() &&
        missing.isEmpty() && price.toDoubleOrNull() != null && listing.photos.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wystaw na eBay") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (listing.ebay.isLive) {
                LiveCard(vm, listing)
                return@Column
            }

            // --- 1. Category ---
            SectionTitle("1. Kategoria eBay")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Szukaj kategorii") }, modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { loadSuggestions() }, enabled = busy == null,
                    modifier = Modifier.padding(start = 8.dp)) { Text("Szukaj") }
            }
            suggestions.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { pick(c) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = category?.id == c.id, onClick = { pick(c) })
                    Column {
                        Text(c.name)
                        Text(c.path, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (suggestions.isEmpty() && category != null) {
                Text("Wybrana: ${category?.name}", style = MaterialTheme.typography.bodyMedium)
            }

            val i = info
            if (i != null) {
                HorizontalDivider()
                // --- 2. Condition ---
                SectionTitle("2. Stan (dozwolone w tej kategorii)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    i.conditions.forEach { c ->
                        FilterChip(
                            selected = condition == c.enum,
                            onClick = { condition = c.enum },
                            label = { Text(c.label) },
                        )
                    }
                }

                // --- 3. Item specifics ---
                HorizontalDivider()
                SectionTitle("3. Cechy przedmiotu")
                Text("Wymagane są oznaczone *. Pozostałe pomagają w wyszukiwaniu.",
                    style = MaterialTheme.typography.bodySmall)
                val shown = i.aspects.filter { it.required || aspects.containsKey(it.name) } +
                    i.aspects.filter { !it.required && !aspects.containsKey(it.name) }.take(6)
                shown.forEach { a ->
                    AspectField(a, aspects[a.name]?.firstOrNull().orEmpty()) { v ->
                        aspects = if (v.isBlank()) aspects - a.name else aspects + (a.name to listOf(v))
                    }
                }

                // --- 4. Price ---
                HorizontalDivider()
                SectionTitle("4. Cena (Kup teraz)")
                OutlinedTextField(
                    value = price, onValueChange = { price = it }, singleLine = true,
                    label = { Text("Cena £") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Wysyłka, płatność i zwroty według zasad wybranych w Ustawieniach → eBay.",
                    style = MaterialTheme.typography.bodySmall)
            }

            error?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            busy?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("  $it")
                }
            }
            if (missing.isNotEmpty()) {
                Text("Uzupełnij: " + missing.joinToString { it.name },
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (listing.photos.isEmpty()) {
                Text("eBay wymaga co najmniej jednego zdjęcia.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                enabled = canPublish,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val c = category ?: return@Button
                    run("Publikuję…") {
                        val chosen = listing.ebay.copy(
                            categoryId = c.id, categoryName = c.name,
                            condition = condition, aspects = aspects,
                        )
                        vm.publishToEbay(listing.id, chosen, price) { step -> busy = step }
                    }
                },
            ) { Text("Opublikuj na eBay") }
        }
    }
}

@Composable
private fun LiveCard(vm: AppViewModel, listing: Listing) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ogłoszenie jest na eBay ✓", fontWeight = FontWeight.Bold)
            Text("${listing.ebay.categoryName} · £${listing.price}")
            Button(onClick = { vm.launcher.openUrl(vm.ebay.listingUrl(listing.ebay.listingId)) }) {
                Text("Otwórz na eBay")
            }
            OutlinedButton(onClick = { vm.endOnEbay(listing.id) }) { Text("Zakończ ogłoszenie na eBay") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AspectField(a: AspectDef, value: String, onChange: (String) -> Unit) {
    val label = a.name + if (a.required) " *" else ""
    if (!a.freeText && a.values.isNotEmpty()) {
        var open by remember { mutableStateOf(false) }
        Column {
            OutlinedTextField(
                value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
                modifier = Modifier.fillMaxWidth().clickable { open = true },
                enabled = false,
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                a.values.take(200).forEach { v ->
                    DropdownMenuItem(text = { Text(v) }, onClick = { onChange(v); open = false })
                }
            }
        }
    } else {
        OutlinedTextField(
            value = value, onValueChange = onChange, singleLine = true,
            label = { Text(label) },
            supportingText = if (a.values.isNotEmpty()) {
                { Text("np. " + a.values.take(4).joinToString()) }
            } else null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
