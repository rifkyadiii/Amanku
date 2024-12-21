package pam.uas.amanku

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import pam.uas.amanku.databinding.ItemLaporanBinding

class LaporanAdapter(private var listLaporan: List<Laporan>, private val onItemClick: (Laporan) -> Unit) : RecyclerView.Adapter<LaporanAdapter.LaporanViewHolder>() {

    class LaporanViewHolder(val binding: ItemLaporanBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LaporanViewHolder {
        val binding = ItemLaporanBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LaporanViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LaporanViewHolder, position: Int) {
        with(holder.binding) {
            listLaporan[position].let { laporan ->
                textViewNomorPolisi.text = "Nomor Polisi: ${laporan.nomorPolisi}"
                textViewMerkType.text = "Merk/Type: ${laporan.merkType}"
                textViewLokasi.text = "Lokasi: ${laporan.lokasi}"
                root.setOnClickListener { onItemClick(laporan) }
            }
        }
    }

    override fun getItemCount() = listLaporan.size

    fun updateList(newList: List<Laporan>) {
        listLaporan = newList
        notifyDataSetChanged()
    }

    fun filterList(query: String) {
        val filteredList = if (query.isEmpty()) {
            listLaporan
        } else {
            listLaporan.filter { laporan ->
                laporan.nomorPolisi?.contains(query, true) == true ||
                        laporan.merkType?.contains(query, true) == true ||
                        laporan.lokasi?.contains(query, true) == true
            }
        }
        updateList(filteredList)
    }
}