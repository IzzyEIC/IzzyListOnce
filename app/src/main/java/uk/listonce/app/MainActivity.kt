package uk.listonce.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import uk.listonce.app.ui.EditListingScreen
import uk.listonce.app.ui.ListingsScreen
import uk.listonce.app.ui.PublishScreen
import uk.listonce.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ListOnceTheme { ListOnceApp() } }
    }
}

@Composable
private fun ListOnceTheme(content: @Composable () -> Unit) {
    val teal = Color(0xFF00897B)
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF4DB6AC), secondary = Color(0xFFFFB74D))
    } else {
        lightColorScheme(primary = teal, secondary = Color(0xFFEF6C00))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun ListOnceApp() {
    val nav = rememberNavController()
    val vm: AppViewModel = viewModel()

    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            ListingsScreen(
                vm = vm,
                onNew = { nav.navigate("edit/new") },
                onOpen = { id -> nav.navigate("publish/$id") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("edit/{id}") { entry ->
            val id = entry.arguments?.getString("id")
            EditListingScreen(
                vm = vm,
                listingId = id.takeUnless { it == "new" },
                onBack = { nav.popBackStack() },
                onSaved = { savedId ->
                    nav.navigate("publish/$savedId") { popUpTo("list") }
                },
            )
        }
        composable("publish/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            PublishScreen(
                vm = vm,
                listingId = id,
                onBack = { nav.popBackStack("list", inclusive = false) },
                onEdit = { nav.navigate("edit/$id") },
            )
        }
        composable("settings") {
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
