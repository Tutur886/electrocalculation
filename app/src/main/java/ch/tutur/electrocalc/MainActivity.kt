package ch.tutur.electrocalc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction

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

    MaterialTheme(colorScheme = darkColorScheme()) {
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
        Text("ElectroCalc", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text("Rechercher un module…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered) { m ->
                Card(Modifier.fillMaxWidth().clickable { onOpenModule(m.id) }) {
                    Text(m.title, Modifier.padding(16.dp))
                }
            }
            if (filtered.isEmpty()) {
                item { Text("Aucun résultat.") }
            }
        }
    }
}

@Composable
private fun ModuleScreen(id: String, onBack: () -> Unit) {
    val m = MODULES.firstOrNull { it.id == id }
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Retour") }
        Text(m?.title ?: "Module", style = MaterialTheme.typography.headlineSmall)
        ModuleBody(id)
    }
}
