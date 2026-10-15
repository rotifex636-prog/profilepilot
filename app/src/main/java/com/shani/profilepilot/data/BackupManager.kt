package com.shani.profilepilot.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted export / import of the whole database + theme setting.
 *
 * HONEST SECURITY NOTES (shown to the user in the Backup screen):
 * - The password is NEVER stored anywhere. Only the user knows it.
 * - A wrong password means the backup CANNOT be opened — the data is
 *   unrecoverable. There is no back door and no "forgot password" flow.
 * - There is NO device lock / hardware binding: a backup restores on ANY
 *   Android 8+ phone as long as the correct password is entered.
 *
 * File format: MAGIC("PPBK1") || 16-byte salt || 12-byte IV || AES-256-GCM ciphertext
 * Key derivation: PBKDF2WithHmacSHA256, 100_000 iterations, 256-bit key.
 * AndroidX only (Room, DataStore) + org.json + javax.crypto — zero third-party SDKs.
 */
object BackupManager {

    data class ImportResult(val ok: Boolean, val message: String)

    private const val MAGIC = "PPBK1"
    private const val ITERATIONS = 100_000
    private const val SALT_LEN = 16
    private const val IV_LEN = 12

    suspend fun exportData(context: Context, uri: Uri, password: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.get(context)
                val root = JSONObject()
                root.put("app", "ProfilePilot")
                root.put("v", 1)
                root.put("accounts", JSONArray().apply {
                    db.accountDao().all().forEach { put(accountToJson(it)) }
                })
                root.put("folders", JSONArray().apply {
                    db.folderDao().all().forEach { put(folderToJson(it)) }
                })
                root.put("proxies", JSONArray().apply {
                    db.proxyDao().all().forEach { put(proxyToJson(it)) }
                })
                root.put(
                    "settings",
                    JSONObject().put("theme", SettingsManager.get(context).themeFlow.first())
                )

                val plain = root.toString().toByteArray(Charsets.UTF_8)
                val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
                val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
                val key = deriveKey(password, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
                val ct = cipher.doFinal(plain)

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(MAGIC.toByteArray(Charsets.UTF_8))
                    out.write(salt)
                    out.write(iv)
                    out.write(ct)
                } ?: return@withContext false
                true
            } catch (_: Exception) {
                false
            }
        }

    suspend fun importData(context: Context, uri: Uri, password: String): ImportResult =
        withContext(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext ImportResult(false, "Backup file nahi khuli")
                val magicLen = MAGIC.toByteArray(Charsets.UTF_8).size
                val headerLen = magicLen + SALT_LEN + IV_LEN
                if (bytes.size <= headerLen) {
                    return@withContext ImportResult(false, "Backup file kharab hai")
                }
                val magic = bytes.copyOfRange(0, magicLen).toString(Charsets.UTF_8)
                if (magic != MAGIC) {
                    return@withContext ImportResult(false, "Ye ProfilePilot ka backup nahi hai")
                }
                val salt = bytes.copyOfRange(magicLen, magicLen + SALT_LEN)
                val iv = bytes.copyOfRange(magicLen + SALT_LEN, headerLen)
                val ct = bytes.copyOfRange(headerLen, bytes.size)

                val key = deriveKey(password, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                val plain = cipher.doFinal(ct)
                val root = JSONObject(plain.toString(Charsets.UTF_8))
                if (root.optString("app") != "ProfilePilot") {
                    return@withContext ImportResult(false, "Ye ProfilePilot ka backup nahi hai")
                }

                val db = AppDatabase.get(context)
                var accountCount = 0
                var folderCount = 0
                var proxyCount = 0
                db.withTransaction {
                    db.accountDao().deleteAll()
                    db.folderDao().deleteAll()
                    db.proxyDao().deleteAll()

                    // Re-insert parents first and remap old ids -> new ids so
                    // account.folderId / account.proxyId links survive the restore.
                    val folderIdMap = mutableMapOf<Long, Long>()
                    root.optJSONArray("folders")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val f = jsonToFolder(arr.getJSONObject(i))
                            val newId = db.folderDao().insert(f.copy(id = 0))
                            folderIdMap[f.id] = newId
                            folderCount++
                        }
                    }
                    val proxyIdMap = mutableMapOf<Long, Long>()
                    root.optJSONArray("proxies")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val p = jsonToProxy(arr.getJSONObject(i))
                            val newId = db.proxyDao().insert(p.copy(id = 0))
                            proxyIdMap[p.id] = newId
                            proxyCount++
                        }
                    }
                    val accounts = mutableListOf<Account>()
                    root.optJSONArray("accounts")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val a = jsonToAccount(arr.getJSONObject(i))
                            accounts.add(
                                a.copy(
                                    id = 0,
                                    folderId = a.folderId?.let { folderIdMap[it] },
                                    proxyId = a.proxyId?.let { proxyIdMap[it] }
                                )
                            )
                        }
                    }
                    db.accountDao().insertAll(accounts)
                    accountCount = accounts.size
                }

                SessionManager.activeAccountId = null
                val theme = root.optJSONObject("settings")?.optString("theme")
                if (!theme.isNullOrEmpty()) {
                    SettingsManager.get(context).setTheme(theme)
                }
                ImportResult(
                    true,
                    "Backup restore ho gaya — $accountCount accounts, $folderCount folders, $proxyCount proxies"
                )
            } catch (_: AEADBadTagException) {
                ImportResult(false, "Galat password — backup nahi khula")
            } catch (_: Exception) {
                ImportResult(false, "Backup restore nahi ho saka — file kharab hai")
            }
        }

    private fun deriveKey(pw: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(pw.toCharArray(), salt, ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    // ---------- JSON mapping: every field is persisted, incl. cookie snapshots, badges, notes ----------

    private fun accountToJson(a: Account): JSONObject = JSONObject()
        .put("id", a.id)
        .put("name", a.name)
        .put("colorHex", a.colorHex)
        .put("emoji", a.emoji)
        .put("status", a.status)
        .put("folderId", if (a.folderId == null) JSONObject.NULL else a.folderId)
        .put("proxyId", if (a.proxyId == null) JSONObject.NULL else a.proxyId)
        .put("killSwitch", a.killSwitch)
        .put("deviceModel", a.deviceModel)
        .put("userAgent", a.userAgent)
        .put("locationMode", a.locationMode)
        .put("locationLat", a.locationLat)
        .put("locationLng", a.locationLng)
        .put("locationLabel", a.locationLabel)
        .put("startPage", a.startPage)
        .put("desktopMode", a.desktopMode)
        .put("darkMode", a.darkMode)
        .put("textZoom", a.textZoom)
        .put("dataSaver", a.dataSaver)
        .put("notes", a.notes)
        .put("cookiesJson", a.cookiesJson)
        .put("notifCount", a.notifCount)
        .put("notifSeenAt", a.notifSeenAt)
        .put("msgCount", a.msgCount)
        .put("msgSeenAt", a.msgSeenAt)
        .put("lastOpened", a.lastOpened)

    private fun jsonToAccount(o: JSONObject): Account = Account(
        id = o.optLong("id"),
        name = o.optString("name"),
        colorHex = o.optString("colorHex", "#1877F2"),
        emoji = o.optString("emoji"),
        status = o.optString("status", "ACTIVE"),
        folderId = if (o.isNull("folderId")) null else o.optLong("folderId"),
        proxyId = if (o.isNull("proxyId")) null else o.optLong("proxyId"),
        killSwitch = o.optBoolean("killSwitch", true),
        deviceModel = o.optString("deviceModel"),
        userAgent = o.optString("userAgent"),
        locationMode = o.optString("locationMode", "OFF"),
        locationLat = o.optDouble("locationLat", 0.0),
        locationLng = o.optDouble("locationLng", 0.0),
        locationLabel = o.optString("locationLabel"),
        startPage = o.optString("startPage", "https://m.facebook.com/"),
        desktopMode = o.optBoolean("desktopMode"),
        darkMode = o.optBoolean("darkMode"),
        textZoom = o.optInt("textZoom", 100),
        dataSaver = o.optBoolean("dataSaver"),
        notes = o.optString("notes"),
        cookiesJson = o.optString("cookiesJson", "{}"),
        notifCount = o.optInt("notifCount"),
        notifSeenAt = o.optLong("notifSeenAt"),
        msgCount = o.optInt("msgCount"),
        msgSeenAt = o.optLong("msgSeenAt"),
        lastOpened = o.optLong("lastOpened")
    )

    private fun folderToJson(f: Folder): JSONObject = JSONObject()
        .put("id", f.id)
        .put("name", f.name)
        .put("colorHex", f.colorHex)

    private fun jsonToFolder(o: JSONObject): Folder = Folder(
        id = o.optLong("id"),
        name = o.optString("name"),
        colorHex = o.optString("colorHex", "#F59E0B")
    )

    private fun proxyToJson(p: ProxyProfile): JSONObject = JSONObject()
        .put("id", p.id)
        .put("name", p.name)
        .put("type", p.type)
        .put("host", p.host)
        .put("port", p.port)
        .put("username", p.username)
        .put("password", p.password)

    private fun jsonToProxy(o: JSONObject): ProxyProfile = ProxyProfile(
        id = o.optLong("id"),
        name = o.optString("name"),
        type = o.optString("type", "HTTP"),
        host = o.optString("host"),
        port = o.optInt("port", 8080),
        username = o.optString("username"),
        password = o.optString("password")
    )
}


==========================================
