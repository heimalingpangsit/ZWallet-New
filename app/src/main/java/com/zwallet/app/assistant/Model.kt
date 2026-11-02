package com.zwallet.app.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Satu pesan dalam chat. ctx* dipakai supaya pertanyaan lanjutan ("kalau bulan lalu?") nyambung. */
data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val time: Long,
    val txIds: List<Long> = emptyList(),
    val chips: List<String> = emptyList(),
    val ctxIntent: String? = null,
    val ctxPeriod: String? = null,
    val ctxType: String? = null,
    val ctxCategory: String? = null
)

/** Konteks percakapan terakhir. */
data class Ctx(val intent: String, val period: String?, val type: String?, val category: String?)

/** Jawaban dari engine. */
data class Reply(
    val text: String,
    val txIds: List<Long> = emptyList(),
    val chips: List<String> = emptyList(),
    val ctx: Ctx? = null
)

object Fmt {
    private val loc = Locale("id", "ID")

    fun rupiah(v: Long): String {
        val s = NumberFormat.getCurrencyInstance(loc).format(abs(v)).replace(",00", "").replace(Regex("\\s"), "")
        return if (v < 0) "-$s" else s
    }

    fun signed(v: Long): String = (if (v >= 0) "+" else "-") + rupiah(abs(v))
    fun date(t: Long): String = SimpleDateFormat("d MMM yyyy", loc).format(Date(t))
    fun dateTime(t: Long): String = SimpleDateFormat("d MMM yyyy, HH:mm", loc).format(Date(t))
    fun full(t: Long): String = SimpleDateFormat("EEEE, d MMMM yyyy • HH:mm", loc).format(Date(t))
    fun dayFull(t: Long): String = SimpleDateFormat("EEEE, d MMM yyyy", loc).format(Date(t))
    fun hm(t: Long): String = SimpleDateFormat("HH:mm", loc).format(Date(t))
}

/** Riwayat chat disimpan lokal (SharedPreferences), tidak keluar dari perangkat. */
class ChatStore(context: Context) {
    private val prefs = context.getSharedPreferences("zwallet_chat", Context.MODE_PRIVATE)

    fun load(): List<ChatMessage> {
        val raw = prefs.getString("history", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChatMessage(
                    id = o.getLong("id"),
                    fromUser = o.getBoolean("u"),
                    text = o.getString("t"),
                    time = o.optLong("time", 0L),
                    txIds = longs(o.optJSONArray("tx")),
                    chips = strings(o.optJSONArray("chips")),
                    ctxIntent = o.optString("ci", "").ifEmpty { null },
                    ctxPeriod = o.optString("cp", "").ifEmpty { null },
                    ctxType = o.optString("ct", "").ifEmpty { null },
                    ctxCategory = o.optString("cc", "").ifEmpty { null }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(list: List<ChatMessage>) {
        val arr = JSONArray()
        list.takeLast(MAX_MESSAGES).forEach { m ->
            val o = JSONObject()
            o.put("id", m.id)
            o.put("u", m.fromUser)
            o.put("t", m.text)
            o.put("time", m.time)
            o.put("tx", JSONArray(m.txIds))
            o.put("chips", JSONArray(m.chips))
            o.put("ci", m.ctxIntent ?: "")
            o.put("cp", m.ctxPeriod ?: "")
            o.put("ct", m.ctxType ?: "")
            o.put("cc", m.ctxCategory ?: "")
            arr.put(o)
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove("history").apply()
    }

    private fun longs(a: JSONArray?): List<Long> =
        if (a == null) emptyList() else (0 until a.length()).map { a.getLong(it) }

    private fun strings(a: JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }

    companion object {
        const val MAX_MESSAGES = 200
    }
}
