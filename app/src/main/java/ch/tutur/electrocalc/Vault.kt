package ch.tutur.electrocalc

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val HIDDEN_PREFIX = ".ecv_"

private fun mimeFromName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "mp4" -> "video/mp4"
    "mp3" -> "audio/mpeg"
    "pdf" -> "application/pdf"
    "txt" -> "text/plain"
    "zip" -> "application/zip"
    else -> "application/octet-stream"
}

@Composable
fun SetPasswordScreen(store: VaultStore, onDone: () -> Unit) {
    val p1 = remember { mutableStateOf("") }
    val p2 = remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppHeader("Configuration", onBack = onDone)
        Text("Choisis un mot de passe secret. Le rechercher plus tard dans la barre ouvrira cet espace.")
        OutlinedTextField(
            value = p1.value, onValueChange = { p1.value = it },
            label = { Text("Mot de passe") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = p2.value, onValueChange = { p2.value = it },
            label = { Text("Confirmer") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            val a = p1.value
            msg = when {
                a.length < 4 -> "Au moins 4 caractères."
                a == "000" -> "Choisis autre chose que 000."
                a != p2.value -> "Les deux ne correspondent pas."
                else -> { store.setPassword(a); onDone(); "" }
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Enregistrer")
        }
        if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun VaultScreen(password: String, store: VaultStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var tree by remember { mutableStateOf(store.treeUri) }
    var clearFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var hiddenCount by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    fun refresh() {
        val t = tree
        val all = if (t != null) {
            DocumentFile.fromTreeUri(context, t)?.listFiles()?.toList() ?: emptyList()
        } else emptyList()
        clearFiles = all.filter { it.isFile && (it.name?.startsWith(".") == false) }
        hiddenCount = all.count { it.name?.startsWith(HIDDEN_PREFIX) == true }
    }

    LaunchedEffect(tree) { refresh() }

    val pickTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) { }
            store.treeUri = uri
            tree = uri
            status = "Dossier sélectionné."
        }
    }

    // Chiffre un fichier en clair de la clé, le cache (nom .ecv_…), supprime l'original.
    suspend fun encryptOne(dir: DocumentFile, doc: DocumentFile): Boolean = withContext(Dispatchers.IO) {
        try {
            val name = doc.name ?: "fichier"
            val data = context.contentResolver.openInputStream(doc.uri)!!.use { it.readBytes() }
            val blob = Crypto.encrypt(password, name, data)
            val out = dir.createFile(
                "application/octet-stream",
                HIDDEN_PREFIX + UUID.randomUUID().toString().replace("-", "")
            ) ?: return@withContext false
            context.contentResolver.openOutputStream(out.uri)!!.use { it.write(blob) }
            doc.delete()
            true
        } catch (e: Exception) {
            false
        }
    }

    // Déchiffre un .ecv_… et réécrit le fichier EN CLAIR sur la clé, supprime le .ecv_…
    suspend fun decryptOne(dir: DocumentFile, doc: DocumentFile): Boolean = withContext(Dispatchers.IO) {
        try {
            val blob = context.contentResolver.openInputStream(doc.uri)!!.use { it.readBytes() }
            val dec = Crypto.decrypt(password, blob)
            val out = dir.createFile(mimeFromName(dec.name), dec.name) ?: return@withContext false
            context.contentResolver.openOutputStream(out.uri)!!.use { it.write(dec.data) }
            doc.delete()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun encryptAll() {
        val t = tree ?: return
        if (busy) return
        scope.launch {
            busy = true
            val dir = DocumentFile.fromTreeUri(context, t)
            val targets = dir?.listFiles()?.filter { it.isFile && it.name?.startsWith(".") == false } ?: emptyList()
            if (dir == null || targets.isEmpty()) {
                status = "Aucun fichier en clair à chiffrer."
                busy = false
                return@launch
            }
            var done = 0; var fail = 0
            targets.forEachIndexed { i, doc ->
                status = "Chiffrement ${i + 1}/${targets.size}…"
                if (encryptOne(dir, doc)) done++ else fail++
            }
            status = "Chiffré et caché : $done" + if (fail > 0) " · échecs : $fail" else ""
            busy = false
            refresh()
        }
    }

    fun decryptAll() {
        val t = tree ?: return
        if (busy) return
        scope.launch {
            busy = true
            val dir = DocumentFile.fromTreeUri(context, t)
            val targets = dir?.listFiles()?.filter { it.name?.startsWith(HIDDEN_PREFIX) == true } ?: emptyList()
            if (dir == null || targets.isEmpty()) {
                status = "Aucun fichier chiffré."
                busy = false
                return@launch
            }
            var done = 0; var fail = 0
            targets.forEachIndexed { i, doc ->
                status = "Déchiffrement ${i + 1}/${targets.size}…"
                if (decryptOne(dir, doc)) done++ else fail++
            }
            status = "Déchiffré : $done" + if (fail > 0) " · échecs (mot de passe ?) : $fail" else ""
            busy = false
            refresh()
        }
    }

    fun hideOne(doc: DocumentFile) {
        val t = tree ?: return
        if (busy) return
        scope.launch {
            busy = true
            val dir = DocumentFile.fromTreeUri(context, t)
            if (dir == null) { busy = false; return@launch }
            status = "Chiffrement…"
            status = if (encryptOne(dir, doc)) "Caché : ${doc.name}" else "Erreur sur ${doc.name}"
            busy = false
            refresh()
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppHeader("Espace sécurisé", onBack = onBack)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (tree == null) "Aucun dossier choisi."
                    else "Dossier : ${tree?.lastPathSegment ?: tree.toString()}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "En clair : ${clearFiles.size}   ·   Cachés : $hiddenCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { pickTree.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Choisir le dossier (clé USB)") }
            }
        }

        Button(
            onClick = { encryptAll() },
            enabled = tree != null && !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("🔒  Tout chiffrer et cacher") }

        FilledTonalButton(
            onClick = { decryptAll() },
            enabled = tree != null && !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("🔓  Tout déchiffrer") }

        if (status.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(status, Modifier.padding(12.dp))
            }
        }

        SectionTitle("Fichiers en clair sur la clé (${clearFiles.size})")
        if (tree == null) {
            Text("Choisis d'abord le dossier de la clé.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (clearFiles.isEmpty()) {
            Text("Aucun fichier en clair. Tout est chiffré 🎉", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                "Touche un fichier pour le chiffrer seul (ou utilise « Tout chiffrer ») :",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            clearFiles.forEach { doc ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().clickable { hideOne(doc) }
                ) {
                    Text("📄  " + (doc.name ?: "?"), Modifier.padding(14.dp))
                }
            }
        }
    }
}
