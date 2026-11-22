package ch.tutur.electrocalc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

private sealed class Screen {
    object Home : Screen()
    data class Mod(val id: String) : Screen()
    object SetPwd : Screen()
    data class Vault(val password: String) : Screen()
}

@Composable
private fun App() {
    val context = LocalContext.current
    val store = remember { VaultStore(context) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var query by remember { mutableStateOf("") }

    MaterialTheme(colorScheme = ElecColorScheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val s = screen) {
                is Screen.Home -> HomeScreen(
                    query = query,
                    onQuery = { query = it },
                    onSubmit = {
                        val q = query.trim()
                        if (!store.isPasswordSet && q == "000") {
                            screen = Screen.SetPwd
                        } else if (store.isPasswordSet && store.checkPassword(q)) {
                            screen = Screen.Vault(q)
                        }
                    },
                    onOpenModule = { screen = Screen.Mod(it) }
                )
                is Screen.Mod -> ModuleScreen(s.id) { screen = Screen.Home }
                is Screen.SetPwd -> SetPasswordScreen(store) { screen = Screen.Home; query = "" }
                is Screen.Vault -> VaultScreen(s.password, store) { screen = Screen.Home; query = "" }
            }
        }
    }
}

@Composable
private fun TagPill(tag: String) {
    Box(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            tag,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HomeScreen(
    query: String,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onOpenModule: (String) -> Unit
) {
    val q = query.trim().lowercase()
    val filtered = if (q.isEmpty()) MODULES
    else MODULES.filter { m ->
        m.title.lowercase().contains(q) || m.keywords.any { it.contains(q) }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppHeader("ElectroCalc", subtitle = "Boîte à outils électronique")
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text("Rechercher un module…") },
            leadingIcon = { Text("🔍") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered) { m ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth().clickable { onOpenModule(m.id) }
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TagPill(m.tag)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            m.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        "Aucun résultat.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleScreen(id: String, onBack: () -> Unit) {
    val m = MODULES.firstOrNull { it.id == id }
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AppHeader(m?.title ?: "Module", onBack = onBack)
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                ModuleBody(id)
            }
        }
    }
}
