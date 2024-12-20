package pam.uas.amanku

data class Laporan(
    var laporanId: String? = null, // ID unik untuk laporan
    val buktiImageUrl: String? = null,
    val namaPelapor: String? = null,
    val nomorPolisi: String? = null,
    val merkType: String? = null,
    val lokasi: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    var userId: String? = null // ID pengguna yang membuat laporan
)