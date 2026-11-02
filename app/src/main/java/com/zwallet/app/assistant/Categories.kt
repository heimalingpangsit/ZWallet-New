package com.zwallet.app.assistant

import com.zwallet.app.data.WalletTransaction

/**
 * Kategori ditebak dari keterangan transaksi (tanpa AI, murni kata kunci).
 * Contoh: "makan siang" -> Makan & Minuman, "isi bensin" -> Transportasi.
 */
object Categories {
    private val expense: Map<String, List<String>> = linkedMapOf(
        "Makan & Minuman" to listOf(
            "makan", "minum", "makanan", "minuman", "kopi", "jajan", "resto", "restoran", "warung", "warteg",
            "bakso", "nasi", "ayam", "mie", "mi", "sate", "snack", "camilan", "cafe", "kafe", "boba", "teh",
            "sarapan", "gofood", "grabfood", "shopeefood", "kfc", "mcd", "starbucks", "pizza", "burger",
            "seblak", "geprek", "lunch", "dinner", "roti", "jus", "es", "bukber", "kantin"
        ),
        "Transportasi" to listOf(
            "bensin", "bbm", "pertamax", "pertalite", "solar", "parkir", "tol", "ojek", "gojek", "grab", "ojol",
            "taksi", "taxi", "kereta", "krl", "mrt", "lrt", "busway", "transjakarta", "pesawat", "tiket",
            "travel", "servis", "service", "oli", "transportasi", "bengkel"
        ),
        "Belanja" to listOf(
            "belanja", "beli", "shopee", "tokopedia", "lazada", "tiktok", "blibli", "indomaret", "alfamart",
            "supermarket", "minimarket", "baju", "sepatu", "tas", "online", "ongkir", "sembako", "kosmetik",
            "skincare", "elektronik", "hp", "gadget", "barang", "olshop", "checkout", "groceries"
        ),
        "Tagihan & Utilitas" to listOf(
            "listrik", "pln", "token", "air", "pdam", "wifi", "internet", "indihome", "pulsa", "kuota", "paket",
            "tagihan", "cicilan", "kos", "kost", "kontrakan", "sewa", "asuransi", "bpjs", "pajak", "pbb",
            "angsuran", "kredit", "paylater", "utilitas"
        ),
        "Hiburan" to listOf(
            "nonton", "bioskop", "netflix", "spotify", "game", "gaming", "steam", "topup", "diamond", "youtube",
            "langganan", "konser", "wisata", "liburan", "hiburan", "karaoke", "disney", "vidio", "hobi"
        ),
        "Kesehatan" to listOf(
            "obat", "apotek", "dokter", "rs", "klinik", "vitamin", "kesehatan", "periksa", "medical", "gigi"
        ),
        "Pendidikan" to listOf(
            "sekolah", "kuliah", "spp", "ukt", "buku", "kursus", "les", "udemy", "pendidikan", "skripsi",
            "fotokopi", "print"
        ),
        "Sosial & Donasi" to listOf(
            "sedekah", "donasi", "zakat", "infaq", "infak", "sumbangan", "hadiah", "kado", "arisan", "sosial",
            "amal", "traktir"
        ),
        "Transfer" to listOf("transfer", "tf", "kirim", "setor", "tarik", "tunai")
    )

    private val income: Map<String, List<String>> = linkedMapOf(
        "Gaji & Penghasilan" to listOf(
            "gaji", "gajian", "bonus", "thr", "honor", "upah", "freelance", "proyek", "komisi", "lembur", "penghasilan"
        ),
        "Penjualan" to listOf("jual", "jualan", "dagang", "omzet", "laba", "untung"),
        "Kiriman & Hadiah" to listOf(
            "kiriman", "dikirim", "transfer", "tf", "hadiah", "angpao", "angpau", "saku", "ortu", "bapak", "ibu", "mama", "papa"
        ),
        "Cashback & Bunga" to listOf("cashback", "refund", "bunga", "dividen", "reward", "kembalian")
    )

    private const val OTHER = "Lainnya"

    /** Kata yang tidak dianggap filter kategori kalau muncul di pertanyaan (karena sudah berarti "pengeluaran"). */
    private val queryExclude = setOf("belanja", "beli", "bayar", "kirim", "jajan", "jual")

    fun words(note: String): List<String> =
        note.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    private fun score(words: List<String>, kws: List<String>): Int =
        kws.count { kw -> kw in words || (kw.length >= 5 && words.any { it.startsWith(kw) }) }

    fun of(type: String, note: String): String {
        val map = if (type == "INCOME") income else expense
        val w = words(note)
        var best: String? = null
        var bestScore = 0
        for ((name, kws) in map) {
            val s = score(w, kws)
            if (s > bestScore) { best = name; bestScore = s }
        }
        return best ?: OTHER
    }

    fun of(t: WalletTransaction): String = of(t.type, t.note)

    /** Deteksi kategori yang disebut di pertanyaan. */
    fun detect(tokens: Set<String>): String? {
        val list = tokens.toList()
        var best: String? = null
        var bestScore = 0
        for (map in listOf(expense, income)) {
            for ((name, kws) in map) {
                val s = score(list, kws.filter { it !in queryExclude })
                if (s > bestScore) { best = name; bestScore = s }
            }
        }
        return best
    }

    fun typeOf(name: String): String? = when {
        expense.containsKey(name) -> "EXPENSE"
        income.containsKey(name) -> "INCOME"
        else -> null
    }

    fun isKeyword(tok: String): Boolean =
        expense.values.any { tok in it } || income.values.any { tok in it }
}
