package uk.listonce.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import uk.listonce.app.ui.EbayPublishScreen
import uk.listonce.app.ui.EditListingScreen
import uk.listonce.app.ui.ListingsScreen
import uk.listonce.app.ui.PublishScreen
import uk.listonce.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleLink(intent)
        setContent { ListOnceTheme { ListOnceApp(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.checkEbaySales()
    }

    private fun handleLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "listonce" && uri.host == "ebay") {
            vm.handleEbayLink(uri)
            intent.data = null // don't handle again on rotation
        }
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
private fun ListOnceApp(vm: AppViewModel) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val message by vm.message.collectAsState()

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.messageShown()
        }
    }

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
                onEbay = { nav.navigate("ebay/$id") },
            )
        }
        composable("ebay/{id}") { entry ->
            EbayPublishScreen(
                vm = vm,
                listingId = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
