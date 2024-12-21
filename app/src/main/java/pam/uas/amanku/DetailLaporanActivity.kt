package pam.uas.amanku

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import pam.uas.amanku.databinding.ActivityDetailLaporanBinding

class DetailLaporanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailLaporanBinding
    private lateinit var database: DatabaseReference
    private var currentLaporan: Laporan? = null
    private var laporanId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailLaporanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance().getReference("laporan")

        binding.backButton.setOnClickListener {
            finish()
        }

        laporanId = intent.getStringExtra("nomorPolisi")
        if (laporanId != null) {
            loadLaporanDetail(laporanId!!)
        } else {
            Log.e("DetailLaporanActivity", "Nomor Polisi is null")
            finish()
        }

        binding.buttonHapus.setOnClickListener {
            currentLaporan?.let {
                if (isUserLaporanOwner(it)) {
                    showDeleteConfirmationDialog(it)
                } else {
                    Toast.makeText(this, "Anda tidak bisa menghapus laporan ini.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.buttonEdit.setOnClickListener {
            currentLaporan?.let {
                if (isUserLaporanOwner(it)) {
                    val intent = Intent(this, EditLaporanActivity::class.java).apply {
                        putExtra("LAPORAN_ID", laporanId)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Anda tidak bisa mengedit laporan ini.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadLaporanDetail(nomorPolisi: String) {
        database.orderByChild("nomorPolisi").equalTo(nomorPolisi).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (laporanSnapshot in snapshot.children) {
                        val laporan = laporanSnapshot.getValue(Laporan::class.java)
                        if (laporan != null) {
                            currentLaporan = laporan
                            laporan.laporanId = laporanSnapshot.key // Assign the key to laporanId
                            tampilkanDetailLaporan(laporan)
                            return // Hentikan loop setelah menemukan data
                        }
                    }
                } else {
                    Log.e("DetailLaporanActivity", "Laporan not found for nomorPolisi: $nomorPolisi")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DetailLaporanActivity", "Error: ${error.message}")
            }
        })
    }

    private fun tampilkanDetailLaporan(laporan: Laporan) {
        Glide.with(this)
            .load(laporan.buktiImageUrl)
            .placeholder(R.drawable.placeholder_image)
            .into(binding.imageViewBukti)

        binding.textViewNamaPelapor.text = "Nama Pelapor: ${laporan.namaPelapor}"
        binding.textViewNomorPolisi.text = "Nomor Polisi: ${laporan.nomorPolisi}"
        binding.textViewMerkType.text = "Merk/Type: ${laporan.merkType}"

        // Set lokasi text dengan style
        val lokasiText = "Lokasi: ${laporan.lokasi}"
        val hintText = "\n\n📍 Ketuk untuk membuka di Google Maps"
        val spannable = SpannableString("$lokasiText$hintText")

        // Style untuk hint text
        val hintColor = ContextCompat.getColor(this, R.color.purple_700) // Atau warna lain yang sesuai tema
        spannable.setSpan(
            ForegroundColorSpan(hintColor),
            lokasiText.length,
            spannable.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        // Style untuk ukuran text hint
        spannable.setSpan(
            RelativeSizeSpan(0.8f),
            lokasiText.length,
            spannable.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Tambahkan style italic untuk hint
        spannable.setSpan(
            StyleSpan(Typeface.ITALIC),
            lokasiText.length,
            spannable.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        binding.textViewLokasi.text = spannable
        binding.textViewLokasi.setTextIsSelectable(true)

        binding.textViewLokasi.setOnClickListener {
            laporan.latitude?.let { lat ->
                laporan.longitude?.let { lon ->
                    val uriString = "https://www.google.com/maps/search/?api=1&query=$lat,$lon"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
                    startActivity(intent)
                }
            } ?: Toast.makeText(this, "Koordinat lokasi tidak tersedia", Toast.LENGTH_SHORT).show()
        }

        setupMapView(laporan)

        if (!isUserLaporanOwner(laporan)) {
            binding.buttonEdit.visibility = View.GONE
            binding.buttonHapus.visibility = View.GONE
        } else {
            binding.buttonEdit.visibility = View.VISIBLE
            binding.buttonHapus.visibility = View.VISIBLE
        }
    }

    private fun setupMapView(laporan: Laporan) {
        val mapView = MapView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(200)
            )
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
        }

        binding.mapContainer.addView(mapView)

        laporan.latitude?.let { lat ->
            laporan.longitude?.let { lon ->
                val location = GeoPoint(lat, lon)
                mapView.controller.setCenter(location)
                mapView.controller.setZoom(15.0)

                val marker = Marker(mapView).apply {
                    position = location
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Lokasi Laporan"
                }
                mapView.overlays.add(marker)
                mapView.invalidate()
            }
        }
    }

    // Helper function untuk konversi dp ke pixel
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun isUserLaporanOwner(laporan: Laporan): Boolean {
        val currentUser = FirebaseAuth.getInstance().currentUser
        return currentUser != null && laporan.userId == currentUser.uid
    }

    private fun showDeleteConfirmationDialog(laporan: Laporan) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Laporan")
            .setMessage("Apakah Anda yakin ingin menghapus laporan ini?")
            .setPositiveButton("Hapus") { dialog, _ ->
                dialog.dismiss()
                hapusLaporan(laporan)
            }
            .setNegativeButton("Batal") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun hapusLaporan(laporan: Laporan) {
        laporan.laporanId?.let { id ->
            database.child(id).removeValue()
                .addOnSuccessListener {
                    Toast.makeText(this, "Laporan berhasil dihapus", Toast.LENGTH_SHORT).show()
                    finish() // Tutup activity setelah hapus
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Gagal menghapus laporan", Toast.LENGTH_SHORT).show()
                }
        }
    }
}