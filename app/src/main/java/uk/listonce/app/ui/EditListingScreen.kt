package uk.listonce.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import uk.listonce.app.AppViewModel
import uk.listonce.app.model.Condition
import uk.listonce.app.model.Delivery
import uk.listonce.app.model.Listing
import uk.listonce.app.model.Platform
import uk.listonce.app.model.PlatformGroup

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditListingScreen(
    vm: AppViewModel,
    listingId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var draft by remember {
        mutableStateOf(
            listingId?.let { vm.store.listing(it) }
                ?: Listing(
                    area = vm.settings.value.defaultArea,
                    targets = Platform.entries.filter { it.active }.toSet(),
                ),
        )
    }
    var pendingCamera by rememberSaveable { mutableStateOf<String?>(null) }

    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20),
    ) { uris ->
        scope.launch {
            val names = vm.importPhotos(uris)
            draft = draft.copy(photos = draft.photos + names)
        }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val name = pendingCamera
        if (ok && name != null) draft = draft.copy(photos = draft.photos + name)
        pendingCamera = null
    }

    val canSave = draft.title.isNotBlank() && (draft.free || draft.price.isNotBlank()) &&
        draft.targets.any { it.active }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (listingId == null) "Nowe ogłoszenie" else "Edycja") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") }
                },
                actions = {
                    TextButton(
                        enabled = canSave,
                        onClick = { vm.save(draft); onSaved(draft.id) },
                    ) { Text("Dalej") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // --- Photos ---
            SectionTitle("Zdjęcia (pierwsze = okładka)")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(draft.photos, key = { it }) { name ->
                    Box {
                        AsyncImage(
                            model = vm.store.photoFile(name),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)),
                        )
                        IconButton(
                            onClick = { draft = draft.copy(photos = draft.photos - name) },
                            modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                        ) { Icon(Icons.Default.Close, "Usuń zdjęcie", tint = MaterialTheme.colorScheme.onPrimary) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Default.PhotoLibrary, null); Text("  Galeria")
                }
                OutlinedButton(onClick = {
                    val (name, uri) = vm.store.newCameraTarget()
                    pendingCamera = name
                    takePhoto.launch(uri)
                }) {
                    Icon(Icons.Default.PhotoCamera, null); Text("  Aparat")
                }
            }

            // --- Basics ---
            OutlinedTextField(
                value = draft.title, onValueChange = { draft = draft.copy(title = it) },
                label = { Text("Tytuł (po angielsku)") }, singleLine = true,
                supportingText = { Text("${draft.title.length} znaków · eBay obcina do 80, Vinted do 60") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.description, onValueChange = { draft = draft.copy(description = it) },
                label = { Text("Opis") }, minLines = 4, modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft.price, onValueChange = { draft = draft.copy(price = it) },
                    label = { Text("Cena £") }, singleLine = true, enabled = !draft.free,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Text("  Za darmo ")
                Switch(checked = draft.free, onCheckedChange = { draft = draft.copy(free = it) })
            }

            SectionTitle("Stan")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Condition.entries.forEach { c ->
                    FilterChip(
                        selected = draft.condition == c,
                        onClick = { draft = draft.copy(condition = c) },
                        label = { Text(c.label) },
                    )
                }
            }

            OutlinedTextField(
                value = draft.category, onValueChange = { draft = draft.copy(category = it) },
                label = { Text("Kategoria (np. Women > Jackets)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.brand, onValueChange = { draft = draft.copy(brand = it) },
                    label = { Text("Marka") }, singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.size, onValueChange = { draft = draft.copy(size = it) },
                    label = { Text("Rozmiar") }, singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.colour, onValueChange = { draft = draft.copy(colour = it) },
                    label = { Text("Kolor") }, singleLine = true, modifier = Modifier.weight(1f),
                )
            }

            SectionTitle("Dostawa")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Delivery.entries.forEach { d ->
                    FilterChip(
                        selected = draft.delivery == d,
                        onClick = { draft = draft.copy(delivery = d) },
                        label = { Text(d.label) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (draft.delivery != Delivery.COLLECTION) {
                    OutlinedTextField(
                        value = draft.postagePrice, onValueChange = { draft = draft.copy(postagePrice = it) },
                        label = { Text("Wysyłka £") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = draft.area, onValueChange = { draft = draft.copy(area = it) },
                    label = { Text("Okolica / kod pocztowy") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            // --- Where it goes ---
            HorizontalDivider()
            SectionTitle("Gdzie wystawić?")
            PlatformGroup.entries.forEach { group ->
                val inGroup = Platform.entries.filter { it.group == group }
                val activeInGroup = inGroup.filter { it.active }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(group.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    if (activeInGroup.size > 1) {
                        TextButton(onClick = {
                            val all = draft.targets.containsAll(activeInGroup)
                            draft = draft.copy(
                                targets = if (all) draft.targets - activeInGroup.toSet() else draft.targets + activeInGroup,
                            )
                        }) { Text(if (draft.targets.containsAll(activeInGroup)) "Odznacz" else "Wszystkie") }
                    }
                }
                inGroup.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = p.active && p in draft.targets,
                            enabled = p.active,
                            onCheckedChange = { on ->
                                draft = draft.copy(targets = if (on) draft.targets + p else draft.targets - p)
                            },
                        )
                        Text(
                            p.label + if (p.active) "" else "  · wkrótce",
                            color = if (p.active) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                }
            }

            Button(
                enabled = canSave,
                onClick = { vm.save(draft); onSaved(draft.id) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Zapisz i wystaw (${draft.targets.count { it.active }})") }
            if (!canSave) {
                Text(
                    "Uzupełnij tytuł, cenę i wybierz co najmniej jeden portal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}
