package ch.tutur.electrocalc

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private fun queryName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) return c.getString(0) }
    return null
}

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

private fun decryptedDir(context: Context): File = File(context.cacheDir, "decrypted")

private fun wipeClear(context: Context) {
    decryptedDir(context).deleteRecursively()
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
        TextButton(onClick = onDone) { Text("← Retour") }
        Text("Configuration de l'espace sécurisé", style = MaterialTheme.typography.headlineSmall)
        Text("Choisis un mot de passe secret. Le rechercher plus tard dans la barre ouvrira l'espace sécurisé.")
        OutlinedTextField(
            value = p1.value, onValueChange = { p1.value = it },
            label = { Text("Mot de passe") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = p2.value, onValueChange = { p2.value = it },
            label = { Text("Confirmer") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true, modifier = Modifier.fillMaxWidth()
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
    var binFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }

    // La copie en clair ne vit que dans le cache privé de l'app : on l'efface à l'entrée
    // et à la sortie du coffre. Rien de clair n'est jamais écrit sur la clé USB.
    DisposableEffect(Unit) {
        wipeClear(context)
        onDispose { wipeClear(context) }
    }

    fun refresh() {
        val t = tree
        val all = if (t != null) {
            DocumentFile.fromTreeUri(context, t)?.listFiles()?.toList() ?: emptyList()
        } else emptyList()
        clearFiles = all.filter { it.isFile && it.name?.endsWith(".bin") == false }
        binFiles = all.filter { it.name?.endsWith(".bin") == true }
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

    // Chiffre un fichier en clair déjà présent sur la clé, puis supprime l'original.
    fun hideKeyFile(doc: DocumentFile) {
        val t = tree ?: return
        scope.launch {
            status = "Chiffrement…"
            val res = withContext(Dispatchers.IO) {
                try {
                    val name = doc.name ?: "fichier"
                    val data = context.contentResolver.openInputStream(doc.uri)!!.use { it.readBytes() }
                    val blob = Crypto.encrypt(password, name, data)
                    val dir = DocumentFile.fromTreeUri(context, t)!!
                    val out = dir.createFile(
                        "application/octet-stream",
                        UUID.randomUUID().toString().replace("-", "") + ".bin"
                    ) ?: return@withContext "Impossible de créer le fichier chiffré."
                    context.contentResolver.openOutputStream(out.uri)!!.use { it.write(blob) }
                    val deleted = doc.delete()
                    if (deleted) "Caché, original effacé : $name"
                    else "Caché : $name (⚠ original non supprimé)"
                } catch (e: Exception) {
                    "Erreur : ${e.message}"
                }
            }
            status = res
            refresh()
        }
    }

    // Import depuis le téléphone : chiffre vers la clé, puis tente d'effacer l'original.
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val t = tree
        if (uri == null) return@rememberLauncherForActivityResult
        if (t == null) { status = "Choisis d'abord le dossier (clé USB)."; return@rememberLauncherForActivityResult }
        scope.launch {
            status = "Chiffrement…"
            val res = withContext(Dispatchers.IO) {
                try {
                    val name = queryName(context, uri) ?: "fichier"
                    val data = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    val blob = Crypto.encrypt(password, name, data)
                    val dir = DocumentFile.fromTreeUri(context, t)!!
                    val out = dir.createFile(
                        "application/octet-stream",
                        UUID.randomUUID().toString().replace("-", "") + ".bin"
                    ) ?: return@withContext "Impossible de créer le fichier chiffré."
                    context.contentResolver.openOutputStream(out.uri)!!.use { it.write(blob) }
                    val deleted = try {
                        DocumentsContract.deleteDocument(context.contentResolver, uri)
                    } catch (_: Exception) { false }
                    if (deleted) "Importé et chiffré : $name (original effacé)"
                    else "Importé et chiffré : $name (original du téléphone à effacer à la main)"
                } catch (e: Exception) {
                    "Erreur : ${e.message}"
                }
            }
            status = res
            refresh()
        }
    }

    // Déchiffre dans le cache privé, ouvre le fichier, ne laisse rien sur la clé.
    fun openBin(doc: DocumentFile) {
        scope.launch {
            status = "Déchiffrement…"
            val file = withContext(Dispatchers.IO) {
                try {
                    val blob = context.contentResolver.openInputStream(doc.uri)!!.use { it.readBytes() }
                    val dec = Crypto.decrypt(password, blob)
                    val dir = decryptedDir(context)
                    dir.mkdirs()
                    val f = File(dir, dec.name)
                    f.writeBytes(dec.data)
                    f
                } catch (e: Exception) {
                    null
                }
            }
            if (file == null) {
                status = "Échec (mauvais mot de passe ou fichier corrompu)."
                return@launch
            }
            try {
                val uri = FileProvider.getUriForFile(
                    context, context.packageName + ".fileprovider", file
                )
                val view = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeFromName(file.name))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(view, "Ouvrir avec")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                status = "Ouvert : ${file.name} (copie en clair effacée à la sortie du coffre)"
            } catch (e: Exception) {
                status = "Aucune application pour ouvrir ${file.name}."
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = { wipeClear(context); onBack() }) { Text("← Retour") }
        Text("Espace sécurisé", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (tree == null) "Aucun dossier choisi."
            else "Dossier : ${tree?.lastPathSegment ?: tree.toString()}",
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedButton(onClick = { pickTree.launch(null) }, modifier = Modifier.fillMaxWidth()) {
            Text("Choisir le dossier (clé USB)")
        }
        OutlinedButton(
            onClick = { pickFile.launch(arrayOf("*/*")) },
            enabled = tree != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Importer un fichier (téléphone)")
        }

        if (status.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text(status, Modifier.padding(12.dp))
            }
        }

        Text("Fichiers en clair sur la clé (${clearFiles.size})", style = MaterialTheme.typography.titleMedium)
        if (tree == null) {
            Text("Choisis d'abord le dossier de la clé.")
        } else if (clearFiles.isEmpty()) {
            Text("Aucun. Copie un fichier sur la clé, puis touche-le ici pour le chiffrer.")
        } else {
            Text("Touche un fichier pour le chiffrer (l'original est effacé) :", style = MaterialTheme.typography.bodySmall)
            clearFiles.forEach { doc ->
                Card(Modifier.fillMaxWidth().clickable { hideKeyFile(doc) }) {
                    Text("🔒  " + (doc.name ?: "?"), Modifier.padding(14.dp))
                }
            }
        }

        Text("Fichiers cachés (${binFiles.size})", style = MaterialTheme.typography.titleMedium)
        if (binFiles.isEmpty()) {
            Text("Aucun fichier caché pour l'instant.")
        } else {
            Text("Touche un fichier pour l'ouvrir (déchiffré en mémoire, jamais écrit sur la clé) :", style = MaterialTheme.typography.bodySmall)
            binFiles.forEach { doc ->
                Card(Modifier.fillMaxWidth().clickable { openBin(doc) }) {
                    Text("🔓  " + (doc.name ?: "?"), Modifier.padding(14.dp))
                }
            }
        }
    }
}
