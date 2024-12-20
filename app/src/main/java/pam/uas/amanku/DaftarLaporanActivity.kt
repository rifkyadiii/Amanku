package pam.uas.amanku

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import pam.uas.amanku.databinding.ActivityDaftarLaporanBinding

class DaftarLaporanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDaftarLaporanBinding
    private lateinit var database: DatabaseReference
    private val listLaporan = mutableListOf<Laporan>()
    private lateinit var adapter: LaporanAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDaftarLaporanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance().getReference("laporan")

        adapter = LaporanAdapter(listLaporan) { laporan ->
            val intent = Intent(this, DetailLaporanActivity::class.java)
            intent.putExtra("nomorPolisi", laporan.nomorPolisi)
            startActivity(intent)
        }
        binding.recyclerViewLaporan.adapter = adapter
        binding.recyclerViewLaporan.layoutManager = LinearLayoutManager(this)

        setupSearch()
        loadData()
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false // Handle di onQueryTextChange
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterData(newText)
                return true
            }
        })
    }

    private fun loadData() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                listLaporan.clear()
                for (laporanSnapshot in snapshot.children) {
                    val laporan = laporanSnapshot.getValue(Laporan::class.java)
                    laporan?.let { listLaporan.add(it) }
                }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DaftarLaporanActivity", "Error: ${error.message}")
            }
        })
    }

    private fun filterData(query: String?) {
        val filteredList = if (query.isNullOrBlank()) {
            listLaporan // Jika query kosong, tampilkan semua data
        } else {
            listLaporan.filter { laporan ->
                laporan.nomorPolisi?.contains(query, ignoreCase = true) == true ||
                        laporan.merkType?.contains(query, ignoreCase = true) == true ||
                        laporan.lokasi?.contains(query, ignoreCase = true) == true
            }
        }
        adapter.updateList(filteredList)
    }
}