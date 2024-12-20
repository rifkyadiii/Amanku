package pam.uas.amanku

import android.content.DialogInterface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
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

        laporanId = intent.getStringExtra("nomorPolisi") // Assuming you are passing nomorPolisi as ID
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
                    // Implement edit functionality here, e.g., open an edit activity
                    // val intent = Intent(this, EditLaporanActivity::class.java)
                    // intent.putExtra("laporanId", laporanId)
                    // startActivity(intent)
                    Toast.makeText(this, "Fitur ini belum diimplementasikan", Toast.LENGTH_SHORT).show()
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
        binding.textViewLokasi.text = "Lokasi: ${laporan.lokasi}"
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