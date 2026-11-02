package com.zwallet.app.assistant

import java.util.Calendar

/** Rentang waktu: start inklusif, end eksklusif. */
data class Period(val key: String, val label: String, val start: Long, val end: Long)

class PeriodParse(val periods: List<Period>, val usedTerakhir: Boolean)

object Periods {
    private val months = listOf(
        "januari", "februari", "maret", "april", "mei", "juni",
        "juli", "agustus", "september", "oktober", "november", "desember"
    )
    private val monthsCap = months.map { it.replaceFirstChar { c -> c.uppercase() } }
    private val shortMonths = mapOf(
        "jan" to 0, "feb" to 1, "mar" to 2, "apr" to 3, "jun" to 5, "jul" to 6,
        "agu" to 7, "agt" to 7, "agus" to 7, "sep" to 8, "sept" to 8, "okt" to 9, "nov" to 10, "des" to 11
    )
    private val units = setOf("hari", "minggu", "bulan")
    private val windowEnd = setOf("terakhir", "belakangan", "kebelakang", "lalu", "kemarin", "ini")
    private val past = setOf("lalu", "kemarin", "sebelumnya", "kmrn", "kemaren", "lewat")
    private val now = setOf("ini", "sekarang", "berjalan")
    private val allWords = setOf("semua", "seluruh", "sepanjang", "keseluruhan", "selama", "sejauh", "selamanya", "all")
    val monthNames: Set<String> get() = months.toSet() + shortMonths.keys

    private fun cal(t: Long): Calendar = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        timeInMillis = t
    }

    private fun startOfDay(c: Calendar): Calendar {
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c
    }

    private fun copy(c: Calendar): Calendar = c.clone() as Calendar

    fun isCurrent(p: Period, nowMs: Long): Boolean = nowMs >= p.start && nowMs < p.end

    fun monthKeyAt(nowMs: Long, offsetMonths: Int): String {
        val c = cal(nowMs)
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.add(Calendar.MONTH, offsetMonths)
        return "m:" + c.get(Calendar.YEAR) + "-" + (c.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
    }

    fun fromKey(key: String, nowMs: Long): Period {
        val today = startOfDay(cal(nowMs))
        when {
            key == "today" -> {
                val e = copy(today); e.add(Calendar.DAY_OF_YEAR, 1)
                return Period(key, "hari ini", today.timeInMillis, e.timeInMillis)
            }
            key == "yesterday" -> {
                val s = copy(today); s.add(Calendar.DAY_OF_YEAR, -1)
                return Period(key, "kemarin", s.timeInMillis, today.timeInMillis)
            }
            key == "week" || key == "lastweek" -> {
                val s = copy(today)
                val dow = (s.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Senin = 0
                s.add(Calendar.DAY_OF_YEAR, -dow)
                if (key == "lastweek") s.add(Calendar.DAY_OF_YEAR, -7)
                val e = copy(s); e.add(Calendar.DAY_OF_YEAR, 7)
                return Period(key, if (key == "week") "minggu ini" else "minggu lalu", s.timeInMillis, e.timeInMillis)
            }
            key == "month" || key == "lastmonth" -> {
                val s = copy(today); s.set(Calendar.DAY_OF_MONTH, 1)
                if (key == "lastmonth") s.add(Calendar.MONTH, -1)
                val e = copy(s); e.add(Calendar.MONTH, 1)
                return Period(key, if (key == "month") "bulan ini" else "bulan lalu", s.timeInMillis, e.timeInMillis)
            }
            key == "year" || key == "lastyear" -> {
                val s = copy(today); s.set(Calendar.DAY_OF_YEAR, 1)
                if (key == "lastyear") s.add(Calendar.YEAR, -1)
                val e = copy(s); e.add(Calendar.YEAR, 1)
                return Period(key, if (key == "year") "tahun ini" else "tahun lalu", s.timeInMillis, e.timeInMillis)
            }
            key == "all" -> return Period(key, "sepanjang waktu", 0L, Long.MAX_VALUE)
            key.startsWith("m:") -> {
                val parts = key.substring(2).split("-")
                val y = parts[0].toInt()
                val m = parts[1].toInt() - 1
                val s = copy(today)
                s.set(Calendar.DAY_OF_MONTH, 1); s.set(Calendar.YEAR, y); s.set(Calendar.MONTH, m)
                val e = copy(s); e.add(Calendar.MONTH, 1)
                return Period(key, "bulan " + monthsCap[m] + " " + y, s.timeInMillis, e.timeInMillis)
            }
            key.startsWith("n:") -> {
                val parts = key.split(":")
                val n = parts[1].toInt()
                val u = parts[2]
                val e = copy(today); e.add(Calendar.DAY_OF_YEAR, 1)
                val s = copy(e)
                when (u) {
                    "d" -> s.add(Calendar.DAY_OF_YEAR, -n)
                    "w" -> s.add(Calendar.DAY_OF_YEAR, -7 * n)
                    else -> s.add(Calendar.MONTH, -n)
                }
                val label = when (u) { "d" -> "$n hari terakhir"; "w" -> "$n minggu terakhir"; else -> "$n bulan terakhir" }
                return Period(key, label, s.timeInMillis, e.timeInMillis)
            }
        }
        return fromKey("month", nowMs)
    }

    /** Periode pembanding (sebelumnya) untuk fitur bandingin. */
    fun previous(p: Period, nowMs: Long): Period = when {
        p.key == "today" -> fromKey("yesterday", nowMs)
        p.key == "week" -> fromKey("lastweek", nowMs)
        p.key == "month" -> fromKey("lastmonth", nowMs)
        p.key == "lastmonth" -> fromKey(monthKeyAt(nowMs, -2), nowMs)
        p.key == "year" -> fromKey("lastyear", nowMs)
        p.key.startsWith("m:") -> {
            val parts = p.key.substring(2).split("-")
            var y = parts[0].toInt()
            var m = parts[1].toInt() - 1 - 1
            if (m < 0) { m = 11; y -= 1 }
            fromKey("m:" + y + "-" + (m + 1).toString().padStart(2, '0'), nowMs)
        }
        else -> {
            val len = p.end - p.start
            Period("prev:" + p.key, "periode sebelumnya", p.start - len, p.start)
        }
    }

    private fun monthKey(m: Int, year: Int?, nowMs: Long): String {
        val c = cal(nowMs)
        val cy = c.get(Calendar.YEAR)
        val cm = c.get(Calendar.MONTH)
        val y = year ?: if (m > cm) cy - 1 else cy
        return "m:" + y + "-" + (m + 1).toString().padStart(2, '0')
    }

    fun parseAll(t: List<String>, nowMs: Long): PeriodParse {
        val keys = LinkedHashSet<String>()
        var usedTerakhir = false
        val year = t.firstNotNullOfOrNull { s ->
            val n = s.toIntOrNull()
            if (n != null && s.length == 4 && n in 2000..2100) n else null
        }
        fun at(i: Int): String = if (i in t.indices) t[i] else ""

        var i = 0
        while (i < t.size) {
            val w = t[i]
            val nx = at(i + 1)
            val nn = at(i + 2)
            val n = w.toIntOrNull()
            var step = 1
            if (n != null && n in 1..366 && nx in units && nn in windowEnd) {
                val u = when (nx) { "hari" -> "d"; "minggu" -> "w"; else -> "m" }
                keys.add("n:$n:$u")
                if (nn == "terakhir") usedTerakhir = true
                step = 3
            } else {
                when {
                    w == "seminggu" || w == "sebulan" || w == "setahun" -> {
                        keys.add(if (w == "seminggu") "n:1:w" else if (w == "sebulan") "n:1:m" else "n:12:m")
                        if (nx == "terakhir") { usedTerakhir = true; step = 2 }
                    }
                    w == "hari" && nx == "ini" -> { keys.add("today"); step = 2 }
                    w == "kemarin" && at(i - 1) != "bulan" && at(i - 1) != "minggu" && at(i - 1) != "tahun" -> keys.add("yesterday")
                    w == "tadi" -> keys.add("today")
                    w == "minggu" && nx in past -> { keys.add("lastweek"); step = 2 }
                    w == "minggu" && nx in now -> { keys.add("week"); step = 2 }
                    w == "bulan" && nx in past -> { keys.add("lastmonth"); step = 2 }
                    w == "bulan" && nx in now -> { keys.add("month"); step = 2 }
                    w == "tahun" && nx in past -> { keys.add("lastyear"); step = 2 }
                    w == "tahun" && nx in now -> { keys.add("year"); step = 2 }
                    w in months -> keys.add(monthKey(months.indexOf(w), year, nowMs))
                    shortMonths.containsKey(w) && at(i - 1) == "bulan" -> keys.add(monthKey(shortMonths.getValue(w), year, nowMs))
                }
            }
            i += step
        }
        if (keys.isEmpty()) {
            when {
                t.any { it in allWords } -> keys.add("all")
                "minggu" in t -> keys.add("week")
                "bulan" in t -> keys.add("month")
                "tahun" in t -> keys.add("year")
            }
        }
        return PeriodParse(keys.map { fromKey(it, nowMs) }, usedTerakhir)
    }
}
