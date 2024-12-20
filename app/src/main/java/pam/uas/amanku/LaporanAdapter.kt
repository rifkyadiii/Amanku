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
        val laporan = listLaporan[position]
        holder.binding.textViewNomorPolisi.text = "Nomor Polisi: ${laporan.nomorPolisi}"
        holder.binding.textViewMerkType.text = "Merk/Type: ${laporan.merkType}"
        holder.binding.textViewLokasi.text = "Lokasi: ${laporan.lokasi}"

        // Menambahkan click listener ke item
        holder.itemView.setOnClickListener {
            onItemClick(laporan)
        }
    }

    override fun getItemCount(): Int {
        return listLaporan.size
    }

    // Metode untuk memperbarui listLaporan di adapter
    fun updateList(newList: List<Laporan>) {
        listLaporan = newList
        notifyDataSetChanged()
    }
}