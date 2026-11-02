package com.zwallet.app.assistant

import java.util.Locale
import kotlin.math.abs

/** Normalisasi bahasa sehari-hari / slang Indonesia + pencocokan kata toleran typo. */
object Nlp {
    private val alias: Map<String, String> = buildMap {
        fun a(to: String, vararg from: String) { from.forEach { put(it, to) } }
        a("saya", "gue", "gw", "gua", "gwe", "gweh", "ane", "aku", "gwa")
        a("kamu", "lo", "lu", "elu", "loe", "anda")
        a("berapa", "brp", "brapa", "berapaan", "brpa", "berapaa")
        a("bulan", "bln")
        a("minggu", "mgg", "mggu", "pekan", "mingguan")
        a("tahun", "thn", "taun")
        a("hari", "hr", "hri")
        a("kemarin", "kmrn", "kemaren", "kmaren")
        a("tadi", "tdi")
        a("sekarang", "skrg", "skarang", "skg")
        a("terus", "trs")
        a("yang", "yg")
        a("dengan", "dgn", "dg")
        a("sama", "sm")
        a("tidak", "gak", "ga", "nggak", "ngga", "enggak", "gk", "tdk", "engga")
        a("sudah", "udh", "udah", "sdh", "dah")
        a("habis", "abis", "ngabisin", "menghabiskan", "dihabiskan", "kehabisan", "abisin", "ngabisinnya")
        a("uang", "duit", "fulus", "cuan", "money", "doku", "duwit")
        a("besar", "gede", "gedhe", "gde", "bsr", "gedee")
        a("kecil", "kcil", "kcl")
        a("paling", "plg")
        a("banyak", "bnyk", "bnyak")
        a("transaksi", "trx", "trans")
        a("keluar", "ngeluarin", "ngeluarkan", "mengeluarkan", "dikeluarin", "dikeluarkan", "keluarin", "keluarkan", "ngluarin")
        a("pengeluaran", "pengeluarn", "pengeluarang")
        a("pemasukan", "pemasukn", "pemasukkan")
        a("dapat", "dapet", "dpt", "dapetin")
        a("kapan", "kpn")
        a("apa", "apaan", "apaa")
        a("rekening", "rek")
        a("bisa", "bs")
        a("hemat", "ngehemat", "menghemat", "berhemat")
        a("belanja", "belanjaan")
        a("jajan", "jajanan")
        a("makasih", "makasi", "mksh", "tengkyu", "trims", "tq", "thx", "terimakasih")
    }

    fun tokens(raw: String): List<String> {
        val cleaned = raw.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()
        if (cleaned.isEmpty()) return emptyList()
        return cleaned.split(" ").filter { it.isNotEmpty() }.map { norm(it) }
    }

    private fun norm(t: String): String {
        alias[t]?.let { return it }
        var w = t
        if (w.length > 6 && w.endsWith("nya")) w = w.dropLast(3)
        return alias[w] ?: w
    }

    fun lev(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[b.length]
    }

    fun fuzzy(t: String, k: String): Boolean {
        if (t == k) return true
        if (k.length < 6 || t.length < 6) return false
        if (abs(t.length - k.length) > 2) return false
        return lev(t, k) <= (if (k.length >= 9) 2 else 1)
    }
}

/** Query helper: cek kata kunci (exact + typo) dan frasa. */
class Q(val tokens: List<String>) {
    val set: Set<String> = tokens.toSet()

    fun has(vararg kws: String): Boolean =
        kws.any { k -> k in set || (k.length >= 6 && tokens.any { Nlp.fuzzy(it, k) }) }

    fun phrase(p: String): Boolean {
        val w = p.split(" ")
        if (w.size > tokens.size) return false
        for (i in 0..(tokens.size - w.size)) {
            var ok = true
            for (j in w.indices) {
                if (tokens[i + j] != w[j]) { ok = false; break }
            }
            if (ok) return true
        }
        return false
    }
}

/** Daftar kata kunci intent (sudah dalam bentuk ternormalisasi). */
object Kw {
    val EXP = arrayOf(
        "pengeluaran", "keluar", "habis", "jajan", "boros", "spend", "spending", "expense",
        "terpakai", "dipakai", "belanja", "bayar", "dibayar"
    )
    val INC = arrayOf(
        "pemasukan", "pendapatan", "masuk", "gaji", "gajian", "dapat", "income", "diterima",
        "penerimaan", "penghasilan", "thr", "bonus"
    )
    val COMPARE = arrayOf(
        "banding", "bandingin", "dibanding", "dibandingkan", "bandingkan", "perbandingan",
        "versus", "vs", "selisih", "naik", "turun", "dibandingin", "membandingkan"
    )
    val ANALYSIS = arrayOf(
        "analisis", "analisa", "ringkasan", "rangkuman", "laporan", "evaluasi", "summary", "overview",
        "gambaran", "review", "rekap", "rekapan", "kesimpulan", "kondisi", "menganalisis"
    )
    val PREDICT = arrayOf("prediksi", "perkiraan", "proyeksi", "estimasi", "ramalan", "memprediksi", "forecast")
    val ADVICE = arrayOf("tips", "tip", "saran", "hemat", "irit", "ngirit", "nabung", "menabung", "tabungan", "rekomendasi")
    val AVG = arrayOf("rata", "rataan", "average", "avg", "harian")
    val LARGE = arrayOf("terbesar", "besar", "tertinggi", "termahal", "mahal", "maksimal", "maksimum", "max")
    val SMALL = arrayOf("terkecil", "kecil", "termurah", "terendah", "minimum")
    val GREET = arrayOf("halo", "hai", "hi", "hello", "hey", "hei", "pagi", "siang", "sore", "malam", "woi", "oy", "p", "assalamualaikum", "yo", "hallo", "helo", "tes", "test")
    val THANKS_STRONG = arrayOf("makasih", "thanks", "thank")
    val THANKS_WEAK = arrayOf("mantap", "keren", "oke", "ok", "sip", "siap", "okay", "bagus")
    val HELP = arrayOf("bantuan", "help", "panduan", "fitur", "menu", "tutorial")

    private val stop = setOf(
        "saya", "kamu", "yang", "di", "ke", "dari", "itu", "ini", "apa", "sih", "dong", "deh", "ya", "nih", "aja",
        "saja", "banget", "dan", "atau", "dengan", "untuk", "buat", "pada", "tuh", "kok", "tapi", "kalau", "kalo",
        "jadi", "sudah", "tidak", "mau", "bisa", "tolong", "coba", "please", "plis", "mana", "berapa", "bulan",
        "minggu", "hari", "tahun", "lalu", "terakhir", "total", "jumlah", "semua", "kategori", "transaksi", "saldo",
        "kartu", "uang", "sisa", "paling", "banyak", "sama", "gimana", "bagaimana", "kenapa", "kapan", "punya",
        "ada", "lagi", "terus", "sekarang", "kemarin", "tadi", "akhir", "tiap", "per", "seluruh", "rekening", "bank",
        "dompet", "sebesar", "sebanyak", "kasih", "liat", "lihat", "tampilkan", "tinggal", "rincian", "detail",
        "riwayat", "daftar", "terbaru", "berapa", "kali", "tanggal", "lari", "kemana", "dimana", "cashflow", "arus", "kas"
    )

    private val allKw: Set<String> = setOf(
        *EXP, *INC, *COMPARE, *ANALYSIS, *PREDICT, *ADVICE, *AVG, *LARGE, *SMALL, *GREET,
        *THANKS_STRONG, *THANKS_WEAK, *HELP
    )

    /** Kata yang tidak pernah dianggap sebagai "kata kunci pencarian transaksi". */
    val ignored: Set<String> by lazy { stop + allKw + Periods.monthNames }
}
