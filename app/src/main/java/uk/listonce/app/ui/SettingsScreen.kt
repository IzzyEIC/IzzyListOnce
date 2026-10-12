package uk.listonce.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.listonce.app.AppViewModel
import uk.listonce.app.model.FbGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    var s by remember { mutableStateOf(vm.settings.value) }
    var groupName by remember { mutableStateOf("") }
    var groupUrl by remember { mutableStateOf("") }

    fun update(new: uk.listonce.app.model.Settings) { s = new; vm.saveSettings(new) }

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
                value = s.defaultArea, onValueChange = { update(s.copy(defaultArea = it)) },
                label = { Text("Domyślna okolica (np. Leeds LS6)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.signature, onValueChange = { update(s.copy(signature = it)) },
                label = { Text("Stopka do każdego opisu (opcjonalnie)") },
                placeholder = { Text("np. Smoke-free home. Check my other items!") },
                minLines = 2, modifier = Modifier.fillMaxWidth(),
            )

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
                    IconButton(onClick = { update(s.copy(fbGroups = s.fbGroups - g)) }) {
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
                    update(s.copy(fbGroups = s.fbGroups + FbGroup(groupName.trim(), groupUrl.trim())))
                    groupName = ""; groupUrl = ""
                },
            ) { Text("Dodaj grupę") }
        }
    }
}
