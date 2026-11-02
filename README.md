# ZWallet 💳

Aplikasi Android pencatat saldo, pemasukan, dan pengeluaran per kartu/dompet. Dibangun dengan **Kotlin**, **Jetpack Compose**, dan **Room Database**. Data tersimpan lokal di perangkat (offline).

## Fitur

- Tambah kartu, rekening, atau dompet digital dengan saldo awal.
- Preset BCA, BRI, BNI, Mandiri, Jenius, DANA, GoPay, OVO, ShopeePay, dan lainnya.
- Catat pemasukan dan pengeluaran per kartu.
- Saldo diperbarui otomatis setelah transaksi.
- Validasi agar pengeluaran tidak melebihi saldo.
- Ringkasan total saldo, pemasukan, dan pengeluaran.
- Riwayat transaksi terbaru.
- Tidak perlu akun, server, atau koneksi internet.

## Tech stack

- Kotlin
- Android Jetpack Compose (Material 3)
- Room Database
- ViewModel + Kotlin Coroutines/Flow
- Gradle Kotlin DSL

## Kartu & gambar kartu

Di **Kartu tersimpan** (tab Wallet) tekan **+ Tambah**, pilih bank / e-wallet, dan gambar kartu bank tersebut langsung muncul otomatis. Gambar dibaca dari `app/src/main/assets/cards/<id>.png` (bca, mandiri, bri, bni, cimb, danamon). Daftar bank diatur di `data/CardCatalog.kt`.

## ZWallet Assistant (offline)

Tab **Assistant** adalah chat pintar yang berjalan sepenuhnya di dalam aplikasi: tanpa OpenAI/API, tanpa server AI, tanpa internet. Jawaban dihitung langsung dari data transaksi di perangkat.

- Paham bahasa sehari-hari ("duit gue abis berapa sih bulan ini?"), singkatan, dan typo kecil.
- Pertanyaan: saldo, total pemasukan/pengeluaran, transaksi terbesar/terkecil, kartu paling boros, kategori, jumlah transaksi, bandingin periode, analisis, prediksi akhir bulan, rata-rata, tips hemat, dan pencarian ("kapan terakhir gue beli kopi?").
- Periode: hari ini, kemarin, minggu/bulan/tahun ini & lalu, nama bulan, "7 hari terakhir", dll.
- Kategori ditebak dari keterangan transaksi (mis. "makan siang", "bensin"). Kode: `assistant/Categories.kt`.
- Pertanyaan lanjutan nyambung ("kalau bulan lalu?").
- Jawaban punya rincian transaksi yang bisa di-tap, ada quick prompt, dan riwayat chat tersimpan lokal (SharedPreferences).

Kode ada di `app/src/main/java/com/zwallet/app/assistant/` dan `ui/AssistantScreen.kt`.

## Menjalankan project

1. Download atau clone repository ini.
2. Buka **Android Studio**.
3. Pilih **Open** dan arahkan ke folder repository (folder yang berisi `settings.gradle.kts`).
4. Pastikan Android Studio menggunakan **JDK 17**.
5. Tunggu Gradle Sync selesai. Android SDK **Platform 35** diperlukan.
6. Jalankan aplikasi ke emulator atau perangkat Android.

## Build APK debug

Dari Android Studio: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

APK debug biasanya dibuat di:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Atau dari terminal (jika Gradle tersedia):

```bash
gradle assembleDebug
```

> Catatan: repository ini belum menyertakan Gradle Wrapper (`gradlew`). Build di GitHub Actions memakai Gradle 8.9 lewat `gradle/actions/setup-gradle`, jadi tidak perlu wrapper.

## Upload ke GitHub

Buat repository baru di GitHub (misalnya `ZWallet`), lalu jalankan perintah berikut dari terminal di folder project:

```bash
git init
git add .
git commit -m "Initial commit - ZWallet Android app"
git branch -M main
git remote add origin https://github.com/USERNAME/NAMA-REPO.git
git push -u origin main
```

Ganti URL remote dengan URL repository GitHub kamu.

## Catatan penting

- Aplikasi ini **bukan** aplikasi perbankan resmi dan tidak terhubung ke bank atau penyedia e-wallet.
- Semua transaksi dimasukkan secara manual; saldo yang tampil adalah catatan pribadi.
- Data disimpan di perangkat. Menghapus data aplikasi atau uninstall dapat menghilangkan data.
- Menghapus kartu juga menghapus transaksi terkait kartu tersebut.
- Untuk pengembangan berikutnya, pertimbangkan fitur ekspor/backup, edit/hapus transaksi, dan pengujian database.

## Credit

Dibuat oleh **JEPIN666X**.

## Lisensi

Belum ditentukan. Tambahkan file lisensi sebelum menerima kontribusi atau mendistribusikan project secara terbuka.
