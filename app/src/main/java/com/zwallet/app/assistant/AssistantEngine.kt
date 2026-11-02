package com.zwallet.app.assistant

import com.zwallet.app.data.WalletCard
import com.zwallet.app.data.WalletTransaction
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

object Quick {
    val prompts = listOf(
        "Analisis keuangan bulan ini",
        "Pengeluaran terbesar bulan ini",
        "Duit gue abis berapa sih bulan ini?",
        "Kartu mana yang paling boros?",
        "Bandingin pengeluaran bulan ini sama bulan lalu",
        "Berapa sisa saldo gue?",
        "Gue paling banyak belanja di kategori apa?",
        "Pemasukan bulan ini berapa?",
        "Transaksi terbesar gue apa?",
        "Berapa total transaksi minggu ini?",
        "Prediksi pengeluaran akhir bulan",
        "Kasih tips hemat dong"
    )
}

/**
 * ZWallet Assistant: mesin tanya-jawab offline. Tidak memakai API, server, atau internet.
 * Memahami pertanyaan lewat normalisasi slang + kata kunci + toleransi typo, lalu menghitung
 * jawaban langsung dari data transaksi di perangkat.
 */
class AssistantEngine(
    private val cards: List<WalletCard>,
    private val txs: List<WalletTransaction>,
    private val nickname: String = "",
    private val now: Long = System.currentTimeMillis()
) {
    private val cardById = cards.associateBy { it.id }
    private val genericCardWords = setOf("kartu", "rekening", "bank", "dompet", "wallet", "ewallet", "utama", "saya", "virtual")
    private val noteWords: Set<String> = txs.flatMap { Categories.words(it.note) }.toSet()
    private val leads = setOf("kalau", "kalo", "terus", "lalu", "dan", "gimana", "bagaimana", "yang", "kalau")
    private val dataIntents = setOf("GREET", "THANKS", "HELP")

    private data class P(
        val tokens: List<String>,
        val q: Q,
        val periods: List<Period>,
        val usedTerakhir: Boolean,
        val type: String?,
        val both: Boolean,
        val cardIds: Set<Long>,
        val category: String?,
        val terms: List<String>
    ) {
        val period: Period? get() = periods.firstOrNull()
    }

    private class Resolved(val period: Period, val note: String)

    // ------------------------------------------------------------------ util

    private fun rp(v: Long) = Fmt.rupiah(v)
    private fun pick(vararg s: String): String = s[Random.nextInt(s.size)]
    private fun cardName(id: Long): String = cardById[id]?.name ?: "kartu terhapus"
    private fun ids(list: List<WalletTransaction>): List<Long> = list.map { it.id }.take(100)
    private fun pct(a: Long, b: Long): Int = if (b <= 0L) 0 else (a * 100.0 / b).roundToInt()
    private fun describe(t: WalletTransaction) = "\"${t.note}\" lewat ${cardName(t.cardId)} pada ${Fmt.date(t.createdAt)}"

    private fun termMatch(w: String, t: String): Boolean =
        w == t || (t.length >= 4 && w.startsWith(t)) || (w.length >= 4 && t.startsWith(w))

    private fun matchesTerms(tx: WalletTransaction, terms: List<String>): Boolean {
        val w = Categories.words(tx.note)
        return terms.any { t -> w.any { termMatch(it, t) } }
    }

    private fun select(p: Period, type: String?, a: P): List<WalletTransaction> = txs.filter { t ->
        t.createdAt >= p.start && t.createdAt < p.end &&
            (type == null || t.type == type) &&
            (a.cardIds.isEmpty() || t.cardId in a.cardIds) &&
            (a.category == null || Categories.of(t) == a.category) &&
            (a.terms.isEmpty() || matchesTerms(t, a.terms))
    }

    private fun scope(a: P): String {
        val parts = mutableListOf<String>()
        if (a.cardIds.isNotEmpty()) parts.add("kartu " + a.cardIds.mapNotNull { cardById[it]?.name }.distinct().joinToString(", "))
        if (a.category != null) parts.add("kategori ${a.category}")
        if (a.terms.isNotEmpty()) parts.add("\"" + a.terms.joinToString(" ") + "\"")
        return if (parts.isEmpty()) "" else " (" + parts.joinToString(" • ") + ")"
    }

    private fun resolve(a: P, defKey: String, type: String?): Resolved {
        a.period?.let { return Resolved(it, "") }
        val d = Periods.fromKey(defKey, now)
        val all = Periods.fromKey("all", now)
        if (defKey != "all" && select(d, type, a).isEmpty() && select(all, type, a).isNotEmpty()) {
            return Resolved(all, "Belum ada data ${d.label}, jadi aku pakai seluruh data kamu ya.\n\n")
        }
        return Resolved(d, "")
    }

    private fun daysIn(per: Period): Int {
        val dayMs = 86_400_000L
        val tomorrow = Periods.fromKey("today", now).end
        val s = if (per.key == "all") (txs.minOfOrNull { it.createdAt } ?: now) else per.start
        val e = minOf(per.end, tomorrow)
        return maxOf(1L, (e - s + dayMs - 1) / dayMs).toInt()
    }

    private fun categoryMap(list: List<WalletTransaction>): Map<String, Long> =
        list.groupBy { Categories.of(it) }.mapValues { en -> en.value.sumOf { it.amount } }

    private fun categoryTotals(list: List<WalletTransaction>): List<Pair<String, Long>> =
        categoryMap(list).toList().sortedByDescending { it.second }

    private fun chips(vararg s: String): List<String> = s.toList()

    // ------------------------------------------------------------------ parsing

    private fun parse(input: String): P {
        val tokens = Nlp.tokens(input)
        val q = Q(tokens)
        val pp = Periods.parseAll(tokens, now)
        val exp = q.has(*Kw.EXP)
        val inc = q.has(*Kw.INC)
        val cardWords = HashSet<String>()
        val cardIds = HashSet<Long>()
        for (c in cards) {
            val words = (Nlp.tokens(c.name) + Nlp.tokens(c.bank)).filter { it.length >= 3 && it !in genericCardWords }
            cardWords.addAll(words)
            if (words.any { it in q.set }) cardIds.add(c.id)
        }
        val category = if (q.has(*Kw.ANALYSIS)) null else Categories.detect(q.set)
        var type: String? = when {
            exp && !inc -> "EXPENSE"
            inc && !exp -> "INCOME"
            else -> null
        }
        if (type == null && !(exp && inc) && category != null) type = Categories.typeOf(category)
        val terms: List<String> = if (category != null) emptyList() else tokens.filter { t ->
            t.length >= 3 && t.toIntOrNull() == null && t !in Kw.ignored && t !in cardWords &&
                !Categories.isKeyword(t) && noteWords.any { w -> termMatch(w, t) }
        }.distinct()
        return P(tokens, q, pp.periods, pp.usedTerakhir, type, exp && inc, cardIds, category, terms)
    }

    private fun detectIntent(p: P): String? {
        val q = p.q
        val n = p.tokens.size
        if (n == 0) return null
        val exp = q.has(*Kw.EXP)
        val inc = q.has(*Kw.INC)
        val dataWord = exp || inc || q.has("saldo", "transaksi", "kartu", "analisis", "analisa", "uang", "sisa", "kategori")

        if (!dataWord && (q.has(*Kw.THANKS_STRONG) || q.phrase("terima kasih") || (n <= 2 && q.has(*Kw.THANKS_WEAK)))) return "THANKS"
        if (!dataWord && n <= 4 && q.has(*Kw.GREET)) return "GREET"
        if (q.has(*Kw.HELP) || q.phrase("bisa apa")) return "HELP"
        if (q.has(*Kw.COMPARE)) return "COMPARE"
        if (q.has(*Kw.ANALYSIS) || (q.has("keuangan") && !q.has(*Kw.ADVICE))) return "ANALYSIS"
        if (q.has(*Kw.PREDICT) || q.phrase("akhir bulan")) return "PREDICT"
        if (q.has("kartu", "rekening", "bank", "dompet") && q.has("mana", "paling", "terbesar", "terkecil") && p.cardIds.isEmpty()) return "CARD_RANK"
        if (q.has(*Kw.ADVICE)) return "ADVICE"
        if (q.has(*Kw.AVG)) return "AVERAGE"
        if (q.has("kapan", "tanggal") && q.has("paling", "terbesar", "boros") && !q.has("terakhir")) return "TOP_DAY"
        if (q.has(*Kw.SMALL)) return "SMALLEST"
        if (q.has(*Kw.LARGE)) return "LARGEST"
        if (q.has("kategori") || q.has("kemana", "larinya", "lari") ||
            (q.has("buat", "untuk", "dimana") && q.has("apa", "mana")) ||
            (q.has("paling") && q.has("banyak") && q.has("apa", "mana"))
        ) return "CATEGORY"
        if (q.has("transaksi", "kali") && q.has("berapa", "jumlah", "total", "banyak")) return "COUNT"
        if (q.has("saldo", "balance", "sisa", "tinggal") ||
            (q.has("uang") && q.has("berapa", "punya", "sekarang") && !exp && !inc)
        ) return "BALANCE"
        if (q.has("kapan") && (p.terms.isNotEmpty() || p.category != null)) return "SEARCH"
        if (q.has("terakhir", "terbaru", "riwayat", "history", "daftar", "list", "tampilkan", "tunjukin", "tunjukkan", "rincian", "detail", "lihat", "liat") && !p.usedTerakhir) return "RECENT"
        if (exp || inc || q.has("cashflow", "arus", "kas")) return "TOTAL"
        if (p.terms.isNotEmpty() || p.category != null) return "SEARCH"
        if (q.has("transaksi")) return "RECENT"
        return null
    }

    private fun hasModifiers(p: P): Boolean =
        p.periods.isNotEmpty() || p.type != null || p.both || p.cardIds.isNotEmpty() || p.category != null || p.terms.isNotEmpty()

    // ------------------------------------------------------------------ entry

    fun answer(input: String, ctx: Ctx?): Reply {
        var p = parse(input)
        var intent = detectIntent(p)
        val first = p.tokens.firstOrNull() ?: ""
        val weak = intent == "TOTAL" || intent == "SEARCH" || intent == "RECENT"
        val followUp = (intent == null && hasModifiers(p)) || (first in leads && p.tokens.size <= 7 && weak)
        if (ctx != null && followUp) {
            intent = ctx.intent
            var np = p
            val cp = ctx.period
            if (np.periods.isEmpty() && cp != null) np = np.copy(periods = listOf(Periods.fromKey(cp, now)))
            val ct = ctx.type
            if (np.type == null && !np.both && ct != null) np = np.copy(type = ct)
            val cc = ctx.category
            if (np.category == null && cc != null) np = np.copy(category = cc)
            p = np
        }
        if (intent == null) return fallback()
        if (txs.isEmpty() && cards.isEmpty() && intent !in dataIntents) return emptyData()
        return when (intent) {
            "GREET" -> greet()
            "THANKS" -> thanks()
            "HELP" -> help()
            "BALANCE" -> balance(p)
            "TOTAL" -> total(p)
            "LARGEST" -> extreme(p, true)
            "SMALLEST" -> extreme(p, false)
            "CARD_RANK" -> cardRank(p)
            "COUNT" -> count(p)
            "COMPARE" -> compare(p)
            "CATEGORY" -> category(p)
            "ANALYSIS" -> analysis(p)
            "RECENT" -> recent(p)
            "AVERAGE" -> average(p)
            "PREDICT" -> predict(p)
            "ADVICE" -> advice(p)
            "TOP_DAY" -> topDay(p)
            "SEARCH" -> search(p)
            else -> fallback()
        }
    }

    // ------------------------------------------------------------------ small talk

    private fun greet(): Reply {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val sal = when {
            h < 11 -> "Selamat pagi"
            h < 15 -> "Selamat siang"
            h < 18 -> "Selamat sore"
            else -> "Selamat malam"
        }
        val name = if (nickname.isBlank()) "" else ", $nickname"
        return Reply(
            "$sal$name! 👋 Aku **ZWallet Assistant**. Tanya apa aja soal pemasukan, pengeluaran, saldo, atau kartumu. Aku jawab dari data di HP ini, tanpa internet.",
            chips = chips("Analisis keuangan bulan ini", "Berapa sisa saldo gue?", "Pengeluaran terbesar bulan ini")
        )
    }

    private fun thanks(): Reply = Reply(
        pick("Sama-sama! 😊 Ada lagi yang mau dicek?", "Siap! Kalau butuh cek keuangan lagi, tinggal tanya ya.", "Oke, senang bisa bantu! 🙌"),
        chips = chips("Analisis keuangan bulan ini", "Kasih tips hemat dong")
    )

    private fun help(): Reply = Reply(
        "Aku bisa bantu ngecek keuanganmu, contohnya:\n" +
            "• **Saldo**: \"Berapa sisa saldo gue?\"\n" +
            "• **Total**: \"Pemasukan bulan ini berapa?\", \"Duit gue abis berapa sih minggu ini?\"\n" +
            "• **Terbesar / terkecil**: \"Transaksi terbesar gue apa?\"\n" +
            "• **Kartu**: \"Kartu mana yang paling boros?\"\n" +
            "• **Kategori**: \"Gue paling banyak belanja di kategori apa?\"\n" +
            "• **Bandingin**: \"Bandingin pengeluaran bulan ini sama bulan lalu\"\n" +
            "• **Analisis & prediksi**: \"Analisis keuangan bulan ini\", \"Prediksi pengeluaran akhir bulan\"\n" +
            "• **Cari**: \"Kapan terakhir gue beli kopi?\", \"Pengeluaran makan bulan ini\"\n" +
            "• **Tips**: \"Kasih tips hemat dong\"\n\n" +
            "Bahasa santai boleh, typo kecil juga aman. Tap transaksi di jawabanku untuk lihat detailnya.",
        chips = chips("Analisis keuangan bulan ini", "Kartu mana yang paling boros?", "Transaksi terbesar gue apa?")
    )

    private fun fallback(): Reply = Reply(
        "Hmm, aku belum nangkep maksudmu 😅. Coba tanya soal saldo, pemasukan, pengeluaran, kategori, atau kartu. Misalnya:\n" +
            "• \"Duit gue abis berapa bulan ini?\"\n• \"Transaksi terbesar gue apa?\"\n• \"Bandingin pengeluaran bulan ini sama bulan lalu\"",
        chips = Quick.prompts.shuffled().take(3)
    )

    private fun emptyData(): Reply = Reply(
        "Datanya masih kosong nih. Tambahkan kartu dan catat beberapa transaksi dulu di tab **Wallet**, nanti aku bisa langsung analisis. 💳",
        chips = chips("Apa yang bisa kamu lakukan?")
    )

    // ------------------------------------------------------------------ BALANCE

    private fun balance(a: P): Reply {
        if (cards.isEmpty()) return Reply("Kamu belum punya kartu. Tambahkan dulu di tab Wallet ya.", chips = chips("Apa yang bisa kamu lakukan?"))
        val list = if (a.cardIds.isEmpty()) cards else cards.filter { it.id in a.cardIds }
        val total = list.sumOf { it.balance }
        val sb = StringBuilder()
        if (list.size == 1) {
            sb.append("Saldo **${list[0].name}** sekarang **${rp(total)}**.")
        } else {
            sb.append(pick("Total saldo kamu sekarang", "Sisa saldo kamu saat ini", "Kalau dijumlah, saldo kamu")).append(" **${rp(total)}** di ${list.size} kartu:")
            list.sortedByDescending { it.balance }.forEach { sb.append("\n• ${it.name}: ${rp(it.balance)}") }
        }
        val m = Periods.fromKey("month", now)
        val inc = txs.filter { it.createdAt >= m.start && it.createdAt < m.end && it.type == "INCOME" && (a.cardIds.isEmpty() || it.cardId in a.cardIds) }.sumOf { it.amount }
        val exp = txs.filter { it.createdAt >= m.start && it.createdAt < m.end && it.type == "EXPENSE" && (a.cardIds.isEmpty() || it.cardId in a.cardIds) }.sumOf { it.amount }
        if (inc > 0 || exp > 0) sb.append("\n\nBulan ini: masuk ${rp(inc)}, keluar ${rp(exp)} (selisih ${Fmt.signed(inc - exp)}).")
        return Reply(
            sb.toString(),
            chips = chips("Kartu mana yang paling boros?", "Analisis keuangan bulan ini", "Prediksi pengeluaran akhir bulan"),
            ctx = Ctx("BALANCE", null, null, null)
        )
    }

    // ------------------------------------------------------------------ TOTAL

    private fun total(a: P): Reply {
        val r = resolve(a, "month", a.type)
        val per = r.period
        val sc = scope(a)
        val inc = select(per, "INCOME", a)
        val exp = select(per, "EXPENSE", a)
        val sumI = inc.sumOf { it.amount }
        val sumE = exp.sumOf { it.amount }
        val sb = StringBuilder(r.note)
        when (a.type) {
            "EXPENSE" -> {
                if (exp.isEmpty()) {
                    sb.append(pick("Belum ada pengeluaran tercatat ${per.label}$sc. Aman! 😄", "Nggak ada pengeluaran ${per.label}$sc. Dompet aman 👌"))
                } else {
                    sb.append("Pengeluaranmu ${per.label}$sc: **${rp(sumE)}** dari ${exp.size} transaksi.")
                    sb.append("\nRata-rata ${rp(sumE / exp.size)} per transaksi")
                    val days = daysIn(per)
                    if (days > 1) sb.append(" dan sekitar ${rp(sumE / days)} per hari")
                    sb.append(".")
                    if (a.category == null) {
                        val cats = categoryTotals(exp)
                        if (cats.isNotEmpty()) sb.append("\nKategori terbesar: **${cats[0].first}** (${rp(cats[0].second)}, ${pct(cats[0].second, sumE)}%).")
                    }
                    val big = exp.maxByOrNull { it.amount }
                    if (big != null) sb.append("\nTerbesar: **${rp(big.amount)}** untuk ${describe(big)}.")
                }
            }
            "INCOME" -> {
                if (inc.isEmpty()) {
                    sb.append("Belum ada pemasukan tercatat ${per.label}$sc.")
                } else {
                    sb.append("Pemasukanmu ${per.label}$sc: **${rp(sumI)}** dari ${inc.size} transaksi.")
                    sb.append("\nRata-rata ${rp(sumI / inc.size)} per transaksi.")
                    val big = inc.maxByOrNull { it.amount }
                    if (big != null) sb.append("\nTerbesar: **${rp(big.amount)}** untuk ${describe(big)}.")
                }
            }
            else -> {
                if (inc.isEmpty() && exp.isEmpty()) {
                    sb.append("Belum ada transaksi ${per.label}$sc.")
                } else {
                    sb.append("Arus kas ${per.label}$sc:")
                    sb.append("\n• Pemasukan: **${rp(sumI)}** (${inc.size} transaksi)")
                    sb.append("\n• Pengeluaran: **${rp(sumE)}** (${exp.size} transaksi)")
                    sb.append("\n• Selisih: **${Fmt.signed(sumI - sumE)}**")
                    sb.append(if (sumI >= sumE) "\nMasih surplus, mantap 👍" else "\nPengeluaran lebih besar dari pemasukan, hati-hati ya ⚠️")
                }
            }
        }
        val shown = when (a.type) { "EXPENSE" -> exp; "INCOME" -> inc; else -> inc + exp }
        return Reply(
            sb.toString(), ids(shown.sortedByDescending { it.amount }),
            chips("Gue paling banyak belanja di kategori apa?", "Bandingin pengeluaran bulan ini sama bulan lalu", "Kartu mana yang paling boros?"),
            Ctx("TOTAL", per.key, a.type, a.category)
        )
    }

    // ------------------------------------------------------------------ LARGEST / SMALLEST

    private fun extreme(a: P, largest: Boolean): Reply {
        val r = resolve(a, "all", a.type)
        val per = r.period
        val sc = scope(a)
        val list = select(per, a.type, a)
        val kind = when (a.type) { "EXPENSE" -> "Pengeluaran"; "INCOME" -> "Pemasukan"; else -> "Transaksi" }
        val adj = if (largest) "terbesar" else "terkecil"
        val intent = if (largest) "LARGEST" else "SMALLEST"
        if (list.isEmpty()) {
            return Reply(r.note + "Aku belum nemu ${kind.lowercase()} ${per.label}$sc.", ctx = Ctx(intent, per.key, a.type, a.category))
        }
        val sorted = if (largest) list.sortedByDescending { it.amount } else list.sortedBy { it.amount }
        val top = sorted.first()
        val jenis = if (a.type == null) (if (top.type == "INCOME") " (pemasukan)" else " (pengeluaran)") else ""
        val sb = StringBuilder(r.note)
        sb.append("$kind $adj ${per.label}$sc: **${rp(top.amount)}**$jenis untuk ${describe(top)}.")
        if (sorted.size > 1) sb.append("\n\nIni urutan berikutnya, tap untuk lihat detail 👇")
        return Reply(
            sb.toString(), ids(sorted.take(6)),
            chips("Kartu mana yang paling boros?", "Gue paling banyak belanja di kategori apa?", "Analisis keuangan bulan ini"),
            Ctx(intent, per.key, a.type, a.category)
        )
    }

    // ------------------------------------------------------------------ CARD RANK

    private fun cardRank(a: P): Reply {
        val q = a.q
        val asc = q.has("hemat", "irit", "kecil", "terkecil", "sedikit")
        val basis = when {
            q.has("saldo", "balance", "isi", "sisa") -> "BAL"
            a.type == "INCOME" -> "INC"
            else -> "EXP"
        }
        if (cards.isEmpty()) return Reply("Kamu belum punya kartu.", chips = chips("Apa yang bisa kamu lakukan?"))
        if (basis == "BAL") {
            val sorted = if (asc) cards.sortedBy { it.balance } else cards.sortedByDescending { it.balance }
            val sum = cards.sumOf { it.balance }
            val sb = StringBuilder("Kartu dengan saldo ${if (asc) "paling kecil" else "paling besar"}: **${sorted[0].name}** (${rp(sorted[0].balance)}).\n")
            sorted.take(8).forEachIndexed { i, c -> sb.append("\n${i + 1}. ${c.name}: ${rp(c.balance)} (${pct(c.balance, sum)}%)") }
            return Reply(sb.toString(), chips = chips("Berapa sisa saldo gue?", "Analisis keuangan bulan ini"), ctx = Ctx("CARD_RANK", null, null, null))
        }
        val ty = if (basis == "INC") "INCOME" else "EXPENSE"
        val r = resolve(a, "month", ty)
        val per = r.period
        val list = select(per, ty, a)
        val noun = if (ty == "EXPENSE") "pengeluaran" else "pemasukan"
        if (list.isEmpty()) return Reply(r.note + "Belum ada $noun ${per.label}, jadi belum bisa dibandingkan antar kartu.", ctx = Ctx("CARD_RANK", per.key, ty, null))
        val groups = list.groupBy { it.cardId }.map { it.key to it.value }
            .sortedBy { en -> if (asc) en.second.sumOf { it.amount } else -en.second.sumOf { it.amount } }
        val sum = list.sumOf { it.amount }
        val lead = groups[0]
        val leadSum = lead.second.sumOf { it.amount }
        val sb = StringBuilder(r.note)
        val verdict = when {
            ty == "EXPENSE" && !asc -> "paling boros"
            ty == "EXPENSE" -> "paling hemat"
            !asc -> "paling banyak pemasukan"
            else -> "paling sedikit pemasukan"
        }
        sb.append("${per.label.replaceFirstChar { it.uppercase() }}, kartu yang $verdict adalah **${cardName(lead.first)}** dengan total $noun **${rp(leadSum)}** (${pct(leadSum, sum)}% dari semua).\n")
        groups.take(8).forEachIndexed { i, g ->
            val s = g.second.sumOf { it.amount }
            sb.append("\n${i + 1}. ${cardName(g.first)}: ${rp(s)} (${g.second.size} transaksi)")
        }
        return Reply(
            sb.toString(), ids(lead.second.sortedByDescending { it.amount }.take(6)),
            chips("Gue paling banyak belanja di kategori apa?", "Bandingin pengeluaran bulan ini sama bulan lalu", "Analisis keuangan bulan ini"),
            Ctx("CARD_RANK", per.key, ty, null)
        )
    }

    // ------------------------------------------------------------------ COUNT

    private fun count(a: P): Reply {
        val r = resolve(a, "month", a.type)
        val per = r.period
        val sc = scope(a)
        val list = select(per, a.type, a)
        val ctx = Ctx("COUNT", per.key, a.type, a.category)
        if (list.isEmpty()) return Reply(r.note + "Belum ada transaksi ${per.label}$sc.", ctx = ctx)
        val inc = list.count { it.type == "INCOME" }
        val exp = list.count { it.type == "EXPENSE" }
        val sb = StringBuilder(r.note)
        sb.append("Total ada **${list.size} transaksi** ${per.label}$sc")
        if (a.type == null) sb.append(": $inc pemasukan dan $exp pengeluaran.") else sb.append(".")
        val days = daysIn(per)
        if (days > 1) sb.append("\nRata-rata sekitar ${"%.1f".format(list.size.toDouble() / days)} transaksi per hari.")
        val byDay = list.groupBy { Fmt.dayFull(it.createdAt) }.maxByOrNull { it.value.size }
        if (byDay != null && byDay.value.size > 1) sb.append("\nPaling ramai: ${byDay.key} (${byDay.value.size} transaksi).")
        return Reply(
            sb.toString(), ids(list.sortedByDescending { it.createdAt }),
            chips("Pengeluaran terbesar bulan ini", "Analisis keuangan bulan ini", "Kartu mana yang paling boros?"), ctx
        )
    }

    // ------------------------------------------------------------------ COMPARE

    private fun compare(a: P): Reply {
        val ps = a.periods
        val pair: Pair<Period, Period> = when {
            ps.size >= 2 -> if (ps[0].start >= ps[1].start) Pair(ps[0], ps[1]) else Pair(ps[1], ps[0])
            ps.size == 1 -> Pair(ps[0], Periods.previous(ps[0], now))
            else -> Pair(Periods.fromKey("month", now), Periods.fromKey("lastmonth", now))
        }
        val cur = pair.first
        val prev = pair.second
        val types = if (a.both) listOf("EXPENSE", "INCOME") else listOf(a.type ?: "EXPENSE")
        val sb = StringBuilder()
        val shown = mutableListOf<WalletTransaction>()
        var any = false
        for (ty in types) {
            val c = select(cur, ty, a)
            val pv = select(prev, ty, a)
            val cs = c.sumOf { it.amount }
            val pS = pv.sumOf { it.amount }
            val noun = if (ty == "EXPENSE") "Pengeluaran" else "Pemasukan"
            if (c.isEmpty() && pv.isEmpty()) {
                sb.append("$noun: belum ada data di ${cur.label} maupun ${prev.label}.\n\n")
                continue
            }
            any = true
            sb.append("**$noun** ${cur.label} vs ${prev.label}\n")
            sb.append("• ${cur.label}: **${rp(cs)}** (${c.size} transaksi)\n")
            sb.append("• ${prev.label}: **${rp(pS)}** (${pv.size} transaksi)\n")
            val diff = cs - pS
            if (pS == 0L) {
                sb.append("Belum ada pembanding di ${prev.label}.\n")
            } else if (diff == 0L) {
                sb.append("Hasilnya sama persis.\n")
            } else {
                val pc = (abs(diff) * 100.0 / pS).roundToInt()
                val up = diff > 0
                val comment = if (ty == "EXPENSE") {
                    if (up) pick("Lebih boros nih 😅", "Lagi naik, hati-hati ya") else pick("Lebih hemat 👏", "Mantap, makin irit")
                } else {
                    if (up) pick("Bagus, pemasukan naik 🎉", "Mantap, makin banyak yang masuk") else "Pemasukan lagi turun."
                }
                sb.append("Selisih: **${if (up) "naik" else "turun"} ${rp(abs(diff))} (${if (up) "+" else "-"}$pc%)**. $comment\n")
            }
            if (Periods.isCurrent(cur, now) && !Periods.isCurrent(prev, now) && pS > 0) {
                val dC = daysIn(cur)
                val dP = daysIn(prev)
                if (dC < dP) sb.append("Karena ${cur.label} belum selesai (baru $dC hari), rata-rata per hari: ${rp(cs / dC)} vs ${rp(pS / dP)}.\n")
            }
            if (ty == "EXPENSE") {
                val cc = categoryMap(c)
                val pc2 = categoryMap(pv)
                val deltas = (cc.keys + pc2.keys).map { it to ((cc[it] ?: 0L) - (pc2[it] ?: 0L)) }
                val upD = deltas.maxByOrNull { it.second }
                val downD = deltas.minByOrNull { it.second }
                if (upD != null && upD.second > 0) sb.append("Kenaikan terbesar: **${upD.first}** (+${rp(upD.second)})\n")
                if (downD != null && downD.second < 0) sb.append("Penurunan terbesar: **${downD.first}** (-${rp(abs(downD.second))})\n")
            }
            sb.append("\n")
            shown.addAll(c.sortedByDescending { it.amount }.take(5))
        }
        val ctx = Ctx("COMPARE", cur.key, if (a.both) null else (a.type ?: "EXPENSE"), a.category)
        if (!any) return Reply(sb.toString().trim(), ctx = ctx, chips = chips("Analisis keuangan bulan ini"))
        return Reply(
            sb.toString().trim(), ids(shown),
            chips("Gue paling banyak belanja di kategori apa?", "Kasih tips hemat dong", "Analisis keuangan bulan ini"), ctx
        )
    }

    // ------------------------------------------------------------------ CATEGORY

    private fun category(a: P): Reply {
        val ty = if (a.type == "INCOME") "INCOME" else "EXPENSE"
        val r = resolve(a, "month", ty)
        val per = r.period
        val list = select(per, ty, a)
        val noun = if (ty == "EXPENSE") "pengeluaran" else "pemasukan"
        val ctx = Ctx("CATEGORY", per.key, ty, null)
        if (list.isEmpty()) return Reply(r.note + "Belum ada $noun ${per.label} untuk dikelompokkan.", ctx = ctx)
        val cats = categoryTotals(list)
        val sum = list.sumOf { it.amount }
        val sb = StringBuilder(r.note)
        sb.append(
            if (ty == "EXPENSE") "${per.label.replaceFirstChar { it.uppercase() }}, kamu paling banyak ngeluarin duit di **${cats[0].first}**: ${rp(cats[0].second)} (${pct(cats[0].second, sum)}% dari total ${rp(sum)}).\n"
            else "${per.label.replaceFirstChar { it.uppercase() }}, sumber pemasukan terbesarmu **${cats[0].first}**: ${rp(cats[0].second)} (${pct(cats[0].second, sum)}% dari total ${rp(sum)}).\n"
        )
        cats.take(6).forEach { sb.append("\n• ${it.first}: ${rp(it.second)} (${pct(it.second, sum)}%)") }
        val other = cats.firstOrNull { it.first == "Lainnya" }
        if (other != null && pct(other.second, sum) >= 50) {
            sb.append("\n\n💡 Banyak transaksi belum punya keterangan yang jelas. Tulis keterangan seperti \"makan siang\" atau \"bensin\" supaya kategorinya lebih akurat.")
        }
        val topCat = cats[0].first
        return Reply(
            sb.toString(), ids(list.filter { Categories.of(it) == topCat }.sortedByDescending { it.amount }.take(8)),
            chips("Bandingin pengeluaran bulan ini sama bulan lalu", "Kasih tips hemat dong", "Pengeluaran terbesar bulan ini"), ctx
        )
    }

    // ------------------------------------------------------------------ ANALYSIS

    private fun analysis(a: P): Reply {
        val r = resolve(a, "month", null)
        val per = r.period
        val inc = select(per, "INCOME", a)
        val exp = select(per, "EXPENSE", a)
        val ctx = Ctx("ANALYSIS", per.key, null, null)
        if (inc.isEmpty() && exp.isEmpty()) {
            return Reply(r.note + "Belum ada transaksi ${per.label} untuk dianalisis.", ctx = ctx, chips = chips("Berapa sisa saldo gue?"))
        }
        val sumI = inc.sumOf { it.amount }
        val sumE = exp.sumOf { it.amount }
        val net = sumI - sumE
        val days = daysIn(per)
        val sb = StringBuilder(r.note)
        sb.append("📊 **Analisis keuangan ${per.label}**\n")
        sb.append("\n• Pemasukan: **${rp(sumI)}** (${inc.size} transaksi)")
        sb.append("\n• Pengeluaran: **${rp(sumE)}** (${exp.size} transaksi)")
        sb.append("\n• Selisih: **${Fmt.signed(net)}**")
        if (sumI > 0) sb.append(" (tabungan ${((net * 100.0) / sumI).roundToInt()}% dari pemasukan)")
        if (exp.isNotEmpty()) {
            sb.append("\n• Rata-rata pengeluaran: ${rp(sumE / days)} per hari")
            val cats = categoryTotals(exp)
            sb.append("\n• Kategori terbesar: ${cats[0].first} (${rp(cats[0].second)}, ${pct(cats[0].second, sumE)}%)")
            val topCard = exp.groupBy { it.cardId }.maxByOrNull { en -> en.value.sumOf { it.amount } }
            if (topCard != null && cards.size > 1) sb.append("\n• Kartu paling boros: ${cardName(topCard.key)} (${rp(topCard.value.sumOf { it.amount })})")
            val big = exp.maxByOrNull { it.amount }
            if (big != null) sb.append("\n• Pengeluaran terbesar: ${rp(big.amount)} untuk \"${big.note}\"")
        }
        if (per.key != "all") {
            val prev = Periods.previous(per, now)
            val pe = select(prev, "EXPENSE", a)
            val ps = pe.sumOf { it.amount }
            if (ps > 0 && sumE > 0) {
                val useAvg = Periods.isCurrent(per, now) && !Periods.isCurrent(prev, now)
                val curV = if (useAvg) sumE / days else sumE
                val prevV = if (useAvg) ps / daysIn(prev) else ps
                val ch = ((curV - prevV) * 100.0 / prevV).roundToInt()
                if (ch != 0) sb.append("\n• Dibanding ${prev.label}: pengeluaran${if (useAvg) " (per hari)" else ""} ${if (ch > 0) "naik" else "turun"} ${abs(ch)}%")
            }
        }
        if (per.key == "month") {
            val cal = Calendar.getInstance()
            val dom = cal.get(Calendar.DAY_OF_MONTH)
            val dim = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            if (dom < dim && sumE > 0) sb.append("\n• Proyeksi akhir bulan: sekitar ${rp(sumE / dom * dim)} jika pola ini berlanjut")
        }
        sb.append("\n\n")
        val rate = if (sumI > 0) (net * 100.0 / sumI) else null
        when {
            sumI > 0 && net < 0 -> sb.append("⚠️ Pengeluaranmu melebihi pemasukan ${per.label}. Coba tahan pengeluaran yang nggak mendesak.")
            rate != null && rate >= 30 -> sb.append("👏 Keren! Kamu berhasil menyisihkan lebih dari 30% pemasukan.")
            rate != null && rate >= 10 -> sb.append("👍 Lumayan, kamu masih bisa menyisihkan sebagian pemasukan. Coba naikkan pelan-pelan.")
            rate != null -> sb.append("🤏 Sisa pemasukanmu tipis. Coba cek kategori terbesar untuk dihemat.")
            sumE > 0 -> sb.append("ℹ️ Belum ada pemasukan tercatat ${per.label}, jadi aku belum bisa hitung tingkat tabunganmu.")
        }
        return Reply(
            sb.toString(), ids(exp.sortedByDescending { it.amount }.take(5)),
            chips("Bandingin pengeluaran bulan ini sama bulan lalu", "Gue paling banyak belanja di kategori apa?", "Kasih tips hemat dong"), ctx
        )
    }

    // ------------------------------------------------------------------ RECENT

    private fun recent(a: P): Reply {
        val r = resolve(a, "all", a.type)
        val per = r.period
        val sc = scope(a)
        val list = select(per, a.type, a).sortedByDescending { it.createdAt }
        val ctx = Ctx("RECENT", per.key, a.type, a.category)
        if (list.isEmpty()) return Reply(r.note + "Belum ada transaksi ${per.label}$sc.", ctx = ctx)
        val kind = when (a.type) { "EXPENSE" -> "pengeluaran"; "INCOME" -> "pemasukan"; else -> "transaksi" }
        val last = list.first()
        val sb = StringBuilder(r.note)
        sb.append("Ada ${list.size} $kind ${per.label}$sc. Yang terbaru: **${rp(last.amount)}** untuk ${describe(last)}.")
        if (list.size > 1) sb.append("\n\nTap untuk lihat detail 👇")
        return Reply(
            sb.toString(), ids(list.take(10)),
            chips("Transaksi terbesar gue apa?", "Analisis keuangan bulan ini"), ctx
        )
    }

    // ------------------------------------------------------------------ AVERAGE

    private fun average(a: P): Reply {
        val r = resolve(a, "month", a.type)
        val per = r.period
        val sc = scope(a)
        val exp = select(per, "EXPENSE", a)
        val inc = select(per, "INCOME", a)
        val ctx = Ctx("AVERAGE", per.key, a.type, a.category)
        if (exp.isEmpty() && inc.isEmpty()) return Reply(r.note + "Belum ada transaksi ${per.label}$sc.", ctx = ctx)
        val days = daysIn(per)
        val sb = StringBuilder(r.note)
        sb.append("Rata-rata ${per.label}$sc (selama $days hari):")
        if (a.type != "INCOME" && exp.isNotEmpty()) {
            val s = exp.sumOf { it.amount }
            sb.append("\n• Pengeluaran: **${rp(s / days)}** per hari, ${rp(s / exp.size)} per transaksi")
        }
        if (a.type != "EXPENSE" && inc.isNotEmpty()) {
            val s = inc.sumOf { it.amount }
            sb.append("\n• Pemasukan: **${rp(s / days)}** per hari, ${rp(s / inc.size)} per transaksi")
        }
        return Reply(
            sb.toString(), ids((if (a.type == "INCOME") inc else exp).sortedByDescending { it.amount }.take(5)),
            chips("Prediksi pengeluaran akhir bulan", "Analisis keuangan bulan ini"), ctx
        )
    }

    // ------------------------------------------------------------------ PREDICT

    private fun predict(a: P): Reply {
        val per = Periods.fromKey("month", now)
        val exp = select(per, "EXPENSE", a.copy(periods = emptyList()))
        val inc = select(per, "INCOME", a.copy(periods = emptyList()))
        val ctx = Ctx("PREDICT", "month", null, null)
        val sumE = exp.sumOf { it.amount }
        if (exp.isEmpty()) return Reply("Belum ada pengeluaran bulan ini, jadi belum ada pola untuk diprediksi.", ctx = ctx)
        val cal = Calendar.getInstance()
        val dom = cal.get(Calendar.DAY_OF_MONTH)
        val dim = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val perDay = sumE / dom
        val projected = perDay * dim
        val sb = StringBuilder()
        sb.append("Sampai hari ke-$dom bulan ini kamu sudah ngeluarin **${rp(sumE)}** (rata-rata ${rp(perDay)} per hari).")
        sb.append("\nKalau polanya sama, total pengeluaran akhir bulan sekitar **${rp(projected)}**, tambahan ${rp(perDay * (dim - dom))} lagi di ${dim - dom} hari tersisa.")
        val sumI = inc.sumOf { it.amount }
        if (sumI > 0) {
            val left = sumI - projected
            sb.append("\nDibanding pemasukan bulan ini (${rp(sumI)}), perkiraan sisanya ${Fmt.signed(left)}.")
        }
        val bal = cards.sumOf { it.balance }
        if (perDay > 0 && cards.isNotEmpty()) sb.append("\nDengan saldo ${rp(bal)} sekarang, uangmu cukup sekitar **${bal / perDay} hari** kalau pengeluaran segitu terus.")
        sb.append("\n\n(Ini perkiraan sederhana dari rata-rata harian, bukan jaminan.)")
        return Reply(
            sb.toString(), ids(exp.sortedByDescending { it.amount }.take(5)),
            chips("Kasih tips hemat dong", "Gue paling banyak belanja di kategori apa?"), ctx
        )
    }

    // ------------------------------------------------------------------ ADVICE

    private fun advice(a: P): Reply {
        val per = Periods.fromKey("month", now)
        val base = a.copy(periods = emptyList(), terms = emptyList(), category = null, cardIds = emptySet())
        var exp = select(per, "EXPENSE", base)
        var inc = select(per, "INCOME", base)
        var label = per.label
        if (exp.isEmpty()) {
            val all = Periods.fromKey("all", now)
            exp = select(all, "EXPENSE", base); inc = select(all, "INCOME", base); label = "seluruh data"
        }
        val ctx = Ctx("ADVICE", null, null, null)
        if (exp.isEmpty()) {
            return Reply(
                "Belum ada pengeluaran yang bisa kubaca, jadi tips umum dulu ya:\n• Catat setiap pengeluaran, sekecil apa pun.\n• Sisihkan tabungan di awal bulan, bukan dari sisa.\n• Pisahkan kartu untuk kebutuhan dan keinginan.",
                ctx = ctx, chips = chips("Apa yang bisa kamu lakukan?")
            )
        }
        val sumE = exp.sumOf { it.amount }
        val sumI = inc.sumOf { it.amount }
        val cats = categoryTotals(exp)
        val sb = StringBuilder("Tips hemat berdasarkan data $label:\n")
        val top = cats.firstOrNull { it.first != "Lainnya" } ?: cats[0]
        sb.append("\n1. **Pantau ${top.first}.** Ini kategori terbesarmu (${rp(top.second)}, ${pct(top.second, sumE)}%). Potong 10% saja bisa hemat sekitar ${rp(top.second / 10)}.")
        val big = exp.maxByOrNull { it.amount }
        if (big != null && big.amount * 100 >= sumE * 25) sb.append("\n2. **Satu transaksi besar** \"${big.note}\" (${rp(big.amount)}) memakan ${pct(big.amount, sumE)}% total pengeluaran. Cek apakah bisa dijadwal ulang atau dicicil.")
        else sb.append("\n2. **Tetapkan batas harian** sekitar ${rp(sumE / daysIn(if (label == "seluruh data") Periods.fromKey("all", now) else per))} per hari, sesuai rata-rata kamu saat ini.")
        if (sumI > 0) {
            val rate = (sumI - sumE) * 100.0 / sumI
            if (rate < 20) sb.append("\n3. **Target tabungan 20%.** Saat ini kamu menyisihkan ${rate.roundToInt()}%. Coba sisihkan ${rp(sumI / 5)} di awal periode.")
            else sb.append("\n3. **Pertahankan!** Tabunganmu ${rate.roundToInt()}% dari pemasukan, sudah di atas target 20%.")
        } else sb.append("\n3. **Catat pemasukan** juga supaya aku bisa hitung tingkat tabunganmu.")
        val small = exp.filter { it.amount < 50_000 }
        if (small.size >= 5) sb.append("\n4. **Waspada pengeluaran kecil.** Ada ${small.size} transaksi di bawah Rp50.000 dengan total ${rp(small.sumOf { it.amount })}. Sering kali ini yang diam-diam menggerus saldo.")
        val other = cats.firstOrNull { it.first == "Lainnya" }
        if (other != null && pct(other.second, sumE) >= 50) sb.append("\n\n💡 Banyak transaksi belum berketerangan jelas, jadi saran di atas bisa lebih tajam kalau keteranganmu lebih spesifik.")
        return Reply(
            sb.toString(), ids(exp.sortedByDescending { it.amount }.take(5)),
            chips("Analisis keuangan bulan ini", "Prediksi pengeluaran akhir bulan"), ctx
        )
    }

    // ------------------------------------------------------------------ TOP DAY

    private fun topDay(a: P): Reply {
        val ty = if (a.type == "INCOME") "INCOME" else "EXPENSE"
        val r = resolve(a, "month", ty)
        val per = r.period
        val list = select(per, ty, a)
        val ctx = Ctx("TOP_DAY", per.key, ty, a.category)
        val noun = if (ty == "EXPENSE") "pengeluaran" else "pemasukan"
        if (list.isEmpty()) return Reply(r.note + "Belum ada $noun ${per.label}.", ctx = ctx)
        val byDay = list.groupBy { Fmt.dayFull(it.createdAt) }
        val best = byDay.maxByOrNull { en -> en.value.sumOf { it.amount } } ?: return Reply("Belum ada data.", ctx = ctx)
        val s = best.value.sumOf { it.amount }
        return Reply(
            r.note + "Hari dengan $noun terbesar ${per.label} adalah **${best.key}**: ${rp(s)} dari ${best.value.size} transaksi.",
            ids(best.value.sortedByDescending { it.amount }),
            chips("Gue paling banyak belanja di kategori apa?", "Analisis keuangan bulan ini"), ctx
        )
    }

    // ------------------------------------------------------------------ SEARCH

    private fun search(a: P): Reply {
        val r = resolve(a, "all", a.type)
        val per = r.period
        val sc = scope(a)
        val list = select(per, a.type, a).sortedByDescending { it.createdAt }
        val ctx = Ctx("SEARCH", per.key, a.type, a.category)
        if (list.isEmpty()) return Reply(r.note + "Aku nggak nemu transaksi$sc ${per.label}.", ctx = ctx, chips = chips("Transaksi terbesar gue apa?"))
        val sb = StringBuilder(r.note)
        if (a.q.has("kapan")) {
            val last = list.first()
            sb.append("Terakhir kali: **${Fmt.dayFull(last.createdAt)}**, ${rp(last.amount)} untuk ${describe(last)}.")
            if (list.size > 1) sb.append("\nTotalnya ada ${list.size} transaksi$sc.")
        } else {
            val inc = list.filter { it.type == "INCOME" }.sumOf { it.amount }
            val exp = list.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            sb.append("Ketemu **${list.size} transaksi**$sc ${per.label}.")
            if (exp > 0) sb.append("\n• Pengeluaran: ${rp(exp)}")
            if (inc > 0) sb.append("\n• Pemasukan: ${rp(inc)}")
        }
        return Reply(
            sb.toString(), ids(list.take(15)),
            chips("Gue paling banyak belanja di kategori apa?", "Analisis keuangan bulan ini"), ctx
        )
    }
}
