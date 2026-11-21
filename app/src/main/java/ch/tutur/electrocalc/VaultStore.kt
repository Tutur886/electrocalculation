package ch.tutur.electrocalc

import android.content.Context
import android.net.Uri
import android.util.Base64

/** Petit stockage local : empreinte du mot de passe + dossier (clé USB) choisi. */
class VaultStore(context: Context) {
    private val p = context.getSharedPreferences("ec_prefs", Context.MODE_PRIVATE)

    val isPasswordSet: Boolean
        get() = p.contains("pwd_hash")

    fun setPassword(password: String) {
        val salt = Crypto.randomBytes(16)
        p.edit()
            .putString("pwd_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pwd_hash", Crypto.hashPassword(password, salt))
            .apply()
    }

    fun checkPassword(candidate: String): Boolean {
        if (candidate.isEmpty()) return false
        val saltB64 = p.getString("pwd_salt", null) ?: return false
        val hash = p.getString("pwd_hash", null) ?: return false
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        return Crypto.hashPassword(candidate, salt) == hash
    }

    var treeUri: Uri?
        get() = p.getString("tree_uri", null)?.let { Uri.parse(it) }
        set(value) {
            p.edit().putString("tree_uri", value?.toString()).apply()
        }
}
