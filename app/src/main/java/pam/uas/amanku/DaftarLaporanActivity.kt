package pam.uas.amanku

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
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
    private lateinit var originalList: List<Laporan>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDaftarLaporanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance().getReference("laporan")

        setupRecyclerView()
        setupTabs()

        if (binding.tabLayout.selectedTabPosition == 0) {
            loadAllReports()
        } else {
            loadMyReports()
        }

        setupSearch()
        setupButtons()

        binding.aboutButton.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        adapter = LaporanAdapter(listLaporan) { laporan ->
            startActivity(Intent(this, DetailLaporanActivity::class.java).apply {
                putExtra("nomorPolisi", laporan.nomorPolisi)
            })
        }
        binding.recyclerViewLaporan.apply {
            adapter = this@DaftarLaporanActivity.adapter
            layoutManager = LinearLayoutManager(this@DaftarLaporanActivity)
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> loadAllReports()
                    1 -> loadMyReports()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun loadReports(userId: String? = null) {
        val query = if (userId != null) {
            database.orderByChild("userId").equalTo(userId)
        } else {
            database
        }

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempList = mutableListOf<Laporan>()
                snapshot.children.forEach {
                    it.getValue(Laporan::class.java)?.let { laporan ->
                        tempList.add(laporan)
                    }
                }
                originalList = tempList
                listLaporan.clear()
                listLaporan.addAll(originalList)
                adapter.notifyDataSetChanged()
                updateEmptyStateVisibility()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DaftarLaporanActivity", "Error: ${error.message}")
                showErrorSnackbar("Gagal memuat data.")
            }
        })
    }

    private fun loadAllReports() {
        loadReports()
    }

    private fun loadMyReports() {
        val currentUser = FirebaseAuth.getInstance().currentUser?.uid
        loadReports(currentUser)
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false

            override fun onQueryTextChange(newText: String?): Boolean {
                filterData(newText)
                return true
            }
        })
    }

    private fun filterData(query: String?) {
        if (::originalList.isInitialized) {
            val filteredList = if (query.isNullOrBlank()) {
                originalList
            } else {
                originalList.filter { laporan ->
                    laporan.nomorPolisi?.contains(query, ignoreCase = true) == true ||
                            laporan.merkType?.contains(query, ignoreCase = true) == true ||
                            laporan.lokasi?.contains(query, ignoreCase = true) == true
                }
            }
            listLaporan.clear()
            listLaporan.addAll(filteredList)
            adapter.notifyDataSetChanged()
            updateEmptyStateVisibility(filteredList)
        } else {
            Log.e("DaftarLaporanActivity", "originalList not initialized")
        }
    }

    private fun setupButtons() {
        binding.buttonBuatLaporan.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
        }

        binding.logoutButton.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    private fun updateEmptyStateVisibility(filteredList: List<Laporan>? = null) {
        val currentList = filteredList ?: listLaporan
        if (currentList.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            when (binding.tabLayout.selectedTabPosition) {
                0 -> {
                    binding.emptyImage.setImageResource(R.drawable.ic_empty_data)
                    binding.emptyText.text = "Tidak ada laporan yang tersedia."
                }
                1 -> {
                    binding.emptyImage.setImageResource(R.drawable.ic_empty_data)
                    binding.emptyText.text = "Anda belum membuat laporan."
                }
            }
            binding.recyclerViewLaporan.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerViewLaporan.visibility = View.VISIBLE
        }
    }

    private fun showErrorSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}