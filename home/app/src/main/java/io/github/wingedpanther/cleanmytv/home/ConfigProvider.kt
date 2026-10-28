package io.github.wingedpanther.cleanmytv.home

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import org.json.JSONObject

/**
 * The desktop app's way in, over ADB:
 *   content call --uri content://io.github.wingedpanther.cleanmytv.home.config --method set --arg '{"focus":"none"}'
 *   content call --uri content://io.github.wingedpanther.cleanmytv.home.config --method get
 * Only the shell user (ADB) and root may call it, so other apps on the TV can't restyle Home.
 */
class ConfigProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val uid = Binder.getCallingUid()
        if (uid != SHELL_UID && uid != ROOT_UID && uid != Process.myUid()) {
            throw SecurityException("only ADB may change CleanMyTV Home")
        }
        val ctx = requireNotNull(context)
        val out = Bundle()
        when (method) {
            "get" -> { ConfigStore.load(ctx); out.putString("config", ConfigStore.config.value.toJson().toString()) }
            "set" -> {
                val json = runCatching { JSONObject(arg ?: "") }.getOrElse { throw IllegalArgumentException("arg must be a JSON object") }
                out.putString("config", ConfigStore.update(ctx, json).toJson().toString())
            }
            else -> throw IllegalArgumentException("unknown method: $method")
        }
        return out
    }

    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int = 0

    private companion object {
        const val SHELL_UID = 2000
        const val ROOT_UID = 0
    }
}
