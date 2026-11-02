package com.zwallet.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Kartu virtual yang dimiliki pengguna (simulasi, bukan kartu bank resmi). */
data class OwnedCard(val uid: String, val bankId: String, val variantId: String, val addedAt: Long)

/** Penyimpanan lokal (SharedPreferences) untuk daftar kartu dan kartu utama. */
class CardsStore(context: Context) {
    private val prefs = context.getSharedPreferences("zwallet_cards", Context.MODE_PRIVATE)

    fun loadCards(): List<OwnedCard> {
        val raw = prefs.getString("cards", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val v = CardCatalog.variant(o.getString("variantId"))
                    ?: CardCatalog.variantsOf(o.optString("bankId")).firstOrNull()
                    ?: return@mapNotNull null
                OwnedCard(o.getString("uid"), v.bankId, v.id, o.optLong("addedAt", 0L))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun loadPrimary(): String? = prefs.getString("primary", null)

    fun save(cards: List<OwnedCard>, primaryUid: String?) {
        val arr = JSONArray()
        cards.forEach { c ->
            val o = JSONObject()
            o.put("uid", c.uid)
            o.put("bankId", c.bankId)
            o.put("variantId", c.variantId)
            o.put("addedAt", c.addedAt)
            arr.put(o)
        }
        prefs.edit().putString("cards", arr.toString()).putString("primary", primaryUid).apply()
    }
}
