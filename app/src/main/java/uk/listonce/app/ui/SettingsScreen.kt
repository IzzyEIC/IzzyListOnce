package uk.listonce.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.listonce.app.AppViewModel
import uk.listonce.app.model.FbGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    // Text fields keep their own state so typing isn't disturbed by async saves.
    var area by remember { mutableStateOf(s.defaultArea) }
    var signature by remember { mutableStateOf(s.signature) }
    var groupName by remember { mutableStateOf("") }
    var groupUrl by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia") },
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
            OutlinedTextField(
                value = area,
                onValueChange = { area = it; vm.updateSettings { st -> st.copy(defaultArea = it) } },
                label = { Text("Domyślna okolica (np. Leeds LS6)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = signature,
                onValueChange = { signature = it; vm.updateSettings { st -> st.copy(signature = it) } },
                label = { Text("Stopka do każdego opisu (opcjonalnie)") },
                placeholder = { Text("np. Smoke-free home. Check my other items!") },
                minLines = 2, modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            EbaySection(vm)

            HorizontalDivider()
            SectionTitle("Lokalne grupy na Facebooku")
            Text(
                "Wklej link do każdej grupy kupna/sprzedaży. Przy ogłoszeniu dostaniesz osobny przycisk dla każdej.",
                style = MaterialTheme.typography.bodySmall,
            )
            s.fbGroups.forEach { g ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(g.name)
                        Text(g.url, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    IconButton(onClick = { vm.updateSettings { it.copy(fbGroups = it.fbGroups - g) } }) {
                        Icon(Icons.Default.Delete, "Usuń grupę")
                    }
                }
            }
            OutlinedTextField(
                value = groupName, onValueChange = { groupName = it },
                label = { Text("Nazwa grupy") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = groupUrl, onValueChange = { groupUrl = it },
                label = { Text("Link (https://www.facebook.com/groups/…)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = groupName.isNotBlank() && groupUrl.startsWith("http"),
                onClick = {
                    val g = FbGroup(groupName.trim(), groupUrl.trim())
                    vm.updateSettings { it.copy(fbGroups = it.fbGroups + g) }
                    groupName = ""; groupUrl = ""
                },
            ) { Text("Dodaj grupę") }
        }
    }
}

@Composable
private fun EbaySection(vm: AppViewModel) {
    val s by vm.settings.collectAsState()
    val acc = s.ebay
    var server by remember { mutableStateOf(s.serverUrl) }
    var postcode by remember { mutableStateOf(acc.postcode) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    SectionTitle("eBay — wystawianie bezpośrednie")

    OutlinedTextField(
        value = server,
        onValueChange = { server = it; vm.updateSettings { st -> st.copy(serverUrl = it.trim().trimEnd('/')) } },
        label = { Text("Adres serwera ListOnce") },
        placeholder = { Text("https://listonce-ebay….workers.dev") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )

    if (!acc.connected) {
        Button(
            enabled = s.serverUrl.startsWith("https://"),
            onClick = { vm.launcher.openUrl(vm.ebay.connectUrl()) },
        ) { Text("Połącz z eBay") }
        Text(
            "Otworzy się logowanie eBay. Po zgodzie wrócisz automatycznie do ListOnce.",
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }

    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Połączono z eBay" + if (acc.env == "sandbox") " (SANDBOX — testowy)" else "",
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (acc.ready) "Konto gotowe do wystawiania ✓"
                else "Kliknij „Przygotuj konto”, żeby ustawić zasady wysyłki, płatności, zwrotów i lokalizację.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    OutlinedTextField(
        value = postcode, onValueChange = { postcode = it.uppercase() },
        label = { Text("Kod pocztowy, z którego wysyłasz") }, singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            enabled = !busy && postcode.isNotBlank(),
            onClick = {
                busy = true; result = null
                vm.ebaySetup(postcode.trim()) { err ->
                    busy = false
                    result = err?.let { "Błąd: $it" } ?: "Gotowe ✓"
                }
            },
        ) { Text(if (acc.ready) "Odśwież ustawienia konta" else "Przygotuj konto") }
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
    result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    TextButton(onClick = { vm.disconnectEbay() }) { Text("Rozłącz eBay") }
}
