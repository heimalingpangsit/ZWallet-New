package com.zwallet.app.data

/**
 * Daftar bank / e-wallet untuk dialog "Tambah kartu".
 * assetId = nama file gambar kartu di app/src/main/assets/cards/<assetId>.png
 * Kalau assetId null, berarti belum ada gambar kartunya (tampil placeholder).
 */
data class BankPreset(val label: String, val assetId: String?)

object CardCatalog {
    val presets = listOf(
        BankPreset("BCA", "bca"),
        BankPreset("Mandiri", "mandiri"),
        BankPreset("BRI", "bri"),
        BankPreset("BNI", "bni"),
        BankPreset("CIMB Niaga", "cimb"),
        BankPreset("Danamon", "danamon"),
        BankPreset("Jenius", null),
        BankPreset("DANA", null),
        BankPreset("GoPay", null),
        BankPreset("OVO", null),
        BankPreset("ShopeePay", null),
        BankPreset("Lainnya", null)
    )

    /** Cari id gambar kartu dari nama bank yang tersimpan di database. */
    fun assetFor(bank: String): String? =
        presets.firstOrNull { it.label.equals(bank, ignoreCase = true) }?.assetId
}
