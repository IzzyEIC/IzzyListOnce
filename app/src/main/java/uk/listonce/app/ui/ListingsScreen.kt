package uk.listonce.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import uk.listonce.app.AppViewModel
import uk.listonce.app.model.Listing
import uk.listonce.app.model.PostState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingsScreen(
    vm: AppViewModel,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val listings by vm.listings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ListOnce") },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Ustawienia") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Nowe ogłoszenie") },
            )
        },
    ) { padding ->
        if (listings.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Wystaw przedmiot raz — ListOnce przeprowadzi Cię przez eBay, Vinted, " +
                        "Facebook, Gumtree, Preloved, Shpock i Nextdoor.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(listings, key = { it.id }) { ListingRow(vm, it, onClick = { onOpen(it.id) }) }
            }
        }
    }
}

@Composable
private fun ListingRow(vm: AppViewModel, listing: Listing, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val cover = listing.photos.firstOrNull()
            if (cover != null) {
                AsyncImage(
                    model = vm.store.photoFile(cover),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    listing.title.ifBlank { "(bez tytułu)" },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (listing.free) "Za darmo" else "£${listing.price}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val posted = listing.posts.count { it.value != PostState.REMOVED }
                val status = when {
                    listing.isSold && listing.stillLive.isNotEmpty() ->
                        "SPRZEDANE — usuń jeszcze z: " + listing.stillLive.joinToString { it.label }
                    listing.isSold -> "SPRZEDANE ✓"
                    else -> "Wystawione: $posted / ${listing.targets.size}"
                }
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (listing.isSold && listing.stillLive.isNotEmpty())
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
