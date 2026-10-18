package uk.listonce.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.listonce.app.AppViewModel
import uk.listonce.app.model.Listing
import uk.listonce.app.model.Platform
import uk.listonce.app.model.PlatformGroup
import uk.listonce.app.model.PostState
import uk.listonce.app.model.Settings
import uk.listonce.app.publish.AdText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishScreen(
    vm: AppViewModel,
    listingId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onEbay: () -> Unit,
) {
    val listings by vm.listings.collectAsState()
    val settings by vm.settings.collectAsState()
    val listing = listings.firstOrNull { it.id == listingId }
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var soldOn by remember { mutableStateOf<Platform?>(null) }

    if (listing == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(listing.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") }
                },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edytuj") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Usuń") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Dla każdego portalu: 1) otwórz go, 2) wklej tytuł/opis/cenę przyciskami poniżej, " +
                            "3) dodaj zdjęcia z albumu „ListOnce”, 4) opublikuj i oznacz tutaj jako wystawione.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = {
                            vm.exportToGallery(listing) { n ->
                                Toast.makeText(context, "Zapisano $n zdjęć w Pictures/ListOnce", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = listing.photos.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Zapisz zdjęcia do galerii (raz na ogłoszenie)") }
                }
            }

            val targets = listing.targets.filter { it.active }.sortedBy { it.ordinal }
            items(targets, key = { it.name }) { platform ->
                PlatformCard(
                    vm, listing, settings, platform,
                    onSold = {
                        vm.setState(listing.id, platform, PostState.SOLD)
                        soldOn = platform
                    },
                    onEbay = onEbay,
                )
            }

            val freeActive = Platform.entries.any { it.group == PlatformGroup.FREE && it.active }
            if (freeActive && !listing.isSold && listing.targets.none { it.group == PlatformGroup.FREE }) {
                item {
                    OutlinedButton(onClick = { vm.giveAway(listing.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Nie sprzedało się? Oddaj za darmo (Freegle, Freecycle, Olio)")
                    }
                }
            }
        }
    }

    soldOn?.let { platform ->
        SoldDialog(vm, listing, platform, onDismiss = { soldOn = null })
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Usunąć ogłoszenie z ListOnce?") },
            text = { Text("Ogłoszenia na portalach zostaną — usuń je tam ręcznie.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(listing.id) }) { Text("Usuń") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Anuluj") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlatformCard(
    vm: AppViewModel,
    listing: Listing,
    settings: Settings,
    platform: Platform,
    onSold: () -> Unit,
    onEbay: () -> Unit,
) {
    val launcher = vm.launcher
    val state = listing.posts[platform]
    val installed = launcher.isInstalled(platform)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                PostState.POSTED -> MaterialTheme.colorScheme.primaryContainer
                PostState.SOLD -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(platform.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    state?.label ?: if (installed) "aplikacja ✓" else "przez www",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (platform.tip.isNotBlank()) {
                Text(platform.tip, style = MaterialTheme.typography.bodySmall)
            }

            // eBay connected through the API: one button does everything.
            if (platform == Platform.EBAY && settings.ebay.ready) {
                Button(onClick = onEbay, modifier = Modifier.fillMaxWidth()) {
                    Text(if (listing.ebay.isLive) "Ogłoszenie na eBay — szczegóły" else "Wystaw automatycznie na eBay")
                }
                if (state == PostState.POSTED) {
                    OutlinedButton(onClick = onSold) { Text("Sprzedane tutaj") }
                }
                return@Column
            }

            // Step 1: open
            if (platform == Platform.FACEBOOK_GROUPS && settings.fbGroups.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    settings.fbGroups.forEach { g ->
                        FilledTonalButton(onClick = { launcher.openUrl(g.url) }) { Text(g.name) }
                    }
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { launcher.open(platform) }) {
                        Text(if (installed) "Otwórz aplikację" else "Otwórz stronę")
                    }
                    if (installed) {
                        OutlinedButton(onClick = { launcher.openWeb(platform) }) { Text("Strona www") }
                    }
                }
            }

            // Step 2: copy text
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (AdText.usesSinglePost(platform)) {
                    AssistChip(
                        onClick = { launcher.copy("Post", AdText.post(listing, platform, settings)) },
                        label = { Text("Kopiuj cały post") },
                    )
                    AssistChip(
                        onClick = { launcher.copy("Tytuł", AdText.title(listing, platform)) },
                        label = { Text("Tytuł") },
                    )
                } else {
                    AssistChip(
                        onClick = { launcher.copy("Tytuł", AdText.title(listing, platform)) },
                        label = { Text("Tytuł") },
                    )
                    AssistChip(
                        onClick = { launcher.copy("Opis", AdText.description(listing, platform, settings)) },
                        label = { Text("Opis") },
                    )
                    AssistChip(
                        onClick = { launcher.copy("Cena", AdText.price(listing, platform)) },
                        label = { Text("Cena") },
                    )
                }
                if (listing.photos.isNotEmpty()) {
                    AssistChip(
                        onClick = {
                            launcher.sharePhotos(
                                platform,
                                listing.photos.map { vm.store.photoUri(it) },
                                AdText.post(listing, platform, settings),
                            )
                        },
                        label = { Text("Wyślij zdjęcia") },
                    )
                }
            }

            // Step 3: track status
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    null -> Button(onClick = { vm.setState(listing.id, platform, PostState.POSTED) }) {
                        Text("Oznacz: wystawione")
                    }
                    PostState.POSTED -> {
                        Button(onClick = onSold) {
                            Text("Sprzedane tutaj")
                        }
                        OutlinedButton(onClick = { vm.setState(listing.id, platform, PostState.REMOVED) }) {
                            Text("Usunięte")
                        }
                    }
                    else -> TextButton(onClick = { vm.setState(listing.id, platform, null) }) { Text("Cofnij") }
                }
            }
        }
    }
}

/** After a sale, walk through every other portal where the ad is still up. */
@Composable
private fun SoldDialog(vm: AppViewModel, listing: Listing, soldOn: Platform, onDismiss: () -> Unit) {
    val live = listing.stillLive.filter { it != soldOn }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sprzedane na ${soldOn.label} 🎉") },
        text = {
            if (live.isEmpty()) {
                Text("Nigdzie indziej nie ma już aktywnego ogłoszenia.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Usuń ogłoszenie z pozostałych portali, żeby nikt nie kupił go drugi raz:")
                    live.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.label, modifier = Modifier.weight(1f))
                            if (p == Platform.EBAY && listing.ebay.isLive) {
                                // Listed through the API — ListOnce can end it itself.
                                TextButton(onClick = { vm.endOnEbay(listing.id) }) { Text("Zakończ automatycznie") }
                            } else {
                                TextButton(onClick = { vm.launcher.open(p) }) { Text("Otwórz") }
                                TextButton(onClick = { vm.setState(listing.id, p, PostState.REMOVED) }) { Text("Usunięte ✓") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Gotowe") } },
    )
}
