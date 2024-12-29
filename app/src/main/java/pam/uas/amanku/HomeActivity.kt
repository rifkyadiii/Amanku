package pam.uas.amanku

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.database
import pam.uas.amanku.databinding.ActivityHomeBinding
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import kotlinx.coroutines.*
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private var selectedImageUri: Uri? = null
    private var uploadedImageUrl: String? = null
    private lateinit var mapView: MapView
    private var selectedLocation: GeoPoint? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var searchLocationEditText: EditText
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var database: DatabaseReference
    private var permissionsGranted = false

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
        private const val PERMISSION_REQUEST_CODE = 123
        private const val PREFS_NAME = "MapPrefs"
        private const val ZOOM_LEVEL_KEY = "zoomLevel"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inisialisasi komponen
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        database = Firebase.database.reference
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mapView = binding.mapView
        searchLocationEditText = binding.searchLocationEditText

        // Setup UI
        setupMap()
        setupSearch()
        setupListeners()
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener { finish() }
        binding.uploadButton.setOnClickListener { checkPermissionsAndOpenChooser() }
        binding.submitButton.setOnClickListener { handleSubmit() }
    }

    private fun handleSubmit() {
        if (!isInternetAvailable(this)) {
            showErrorMessage("Tidak ada koneksi internet")
            return
        }

        if (!validateInput()) return

        binding.submitButton.isEnabled = false
        val laporan = createLaporanObject()
        simpanDataKeFirebase(laporan)
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            permissionsGranted = true
            return
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun validateInput(): Boolean {
        var isValid = true

        if (binding.namaPelaporEditText.text.isNullOrBlank()) {
            binding.namaPelaporEditText.error = "Nama pelapor harus diisi"
            isValid = false
        }

        if (binding.nomorPolisiEditText.text.isNullOrBlank()) {
            binding.nomorPolisiEditText.error = "Nomor polisi harus diisi"
            isValid = false
        }

        if (binding.merkTypeEditText.text.isNullOrBlank()) {
            binding.merkTypeEditText.error = "Merk/Type harus diisi"
            isValid = false
        }

        if (selectedImageUri == null) {
            showErrorMessage("Harap upload bukti gambar")
            isValid = false
        }

        if (selectedLocation == null) {
            showErrorMessage("Harap pilih lokasi pada peta")
            isValid = false
        }

        return isValid
    }

    private fun createLaporanObject(): Laporan {
        val namaPelapor = binding.namaPelaporEditText.text.toString().trim()
        val nomorPolisi = binding.nomorPolisiEditText.text.toString().trim()
        val merkType = binding.merkTypeEditText.text.toString().trim()
        var lokasi = binding.lokasiTextView.text.toString()

        // Hilangkan prefix "Lokasi: " jika ada
        if (lokasi.startsWith("Lokasi: ")) {
            lokasi = lokasi.removePrefix("Lokasi: ")
        }

        return Laporan(
            buktiImageUrl = uploadedImageUrl,
            namaPelapor = namaPelapor,
            nomorPolisi = nomorPolisi,
            merkType = merkType,
            lokasi = lokasi,
            latitude = selectedLocation?.latitude,
            longitude = selectedLocation?.longitude,
            userId = FirebaseAuth.getInstance().currentUser?.uid // tambahkan userId
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // Bersihkan resources
        mapView.onDetach()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Simpan state
        outState.putParcelable("selectedImageUri", selectedImageUri)
        outState.putString("uploadedImageUrl", uploadedImageUrl)
        selectedLocation?.let {
            outState.putDouble("selectedLat", it.latitude)
            outState.putDouble("selectedLon", it.longitude)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Restore state
        selectedImageUri = savedInstanceState.getParcelable("selectedImageUri")
        uploadedImageUrl = savedInstanceState.getString("uploadedImageUrl")
        val lat = savedInstanceState.getDouble("selectedLat", 0.0)
        val lon = savedInstanceState.getDouble("selectedLon", 0.0)
        if (lat != 0.0 && lon != 0.0) {
            selectedLocation = GeoPoint(lat, lon)
            addMarker(selectedLocation!!, "Lokasi Terpilih")
        }
    }

    private fun setupMap() {
        // Konfigurasi osmdroid
        Configuration.getInstance().userAgentValue = packageName
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Mengaktifkan rotasi
        val mRotationGestureOverlay = RotationGestureOverlay(mapView)
        mRotationGestureOverlay.isEnabled = true
        mapView.setMultiTouchControls(true)
        mapView.overlays.add(mRotationGestureOverlay)

        // Set initial zoom level
        val mapController = mapView.controller

        // Dapatkan zoom level terakhir dari SharedPreferences
        val lastZoomLevel = sharedPreferences.getFloat(ZOOM_LEVEL_KEY, 15.0f).toDouble() // Default 15.0
        mapController.setZoom(lastZoomLevel)

        // Mendapatkan lokasi saat ini
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                val currentLocation = location?.let { GeoPoint(it.latitude, it.longitude) }
                    ?: GeoPoint(-6.208763, 106.845599) // Lokasi default
                mapController.setCenter(currentLocation)
                setupTapListener(mapView)
            }
        } else {
            // Meminta izin lokasi jika belum diberikan
            requestLocationPermission()
        }
    }

    private fun setupTapListener(mapView: MapView) {
        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                mapView.overlays.removeAll { it is Marker }

                // Dapatkan instance dari mapController
                val mapController = mapView.controller

                selectedLocation = p
                addMarker(p, "Lokasi Terpilih")
                updateLocationTextView(p)
                mapController.setCenter(p)

                // Simpan zoom level saat ini ke SharedPreferences
                val editor = sharedPreferences.edit()
                editor.putFloat(ZOOM_LEVEL_KEY, mapView.zoomLevelDouble.toFloat())
                editor.apply()

                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                return false
            }
        }
        val eventsOverlay = MapEventsOverlay(mapEventsReceiver)
        mapView.overlays.add(0, eventsOverlay)
    }

    private fun setupSearch() {
        searchLocationEditText.setOnEditorActionListener { _, _, _ ->
            val searchText = searchLocationEditText.text.toString()
            if (searchText.isNotEmpty()) {
                searchLocation(searchText)
            }
            true
        }
    }

    private fun searchLocation(query: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val geocoder = Geocoder(this@HomeActivity, Locale.getDefault())
            try {
                val addresses = geocoder.getFromLocationName(query, 1)
                withContext(Dispatchers.Main) {
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val location = GeoPoint(address.latitude, address.longitude)
                        selectedLocation = location // Update lokasi terpilih
                        mapView.controller.setCenter(location)
                        mapView.controller.setZoom(15.0) // Sesuaikan zoom level
                        mapView.overlays.removeAll { it is Marker } // Hapus hanya marker
                        addMarker(location, query, true) // Tambahkan marker lokasi yang dicari
                        updateLocationTextView(location) // Update TextView
                    } else {
                        Toast.makeText(this@HomeActivity, "Lokasi tidak ditemukan", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    e.printStackTrace()
                    Toast.makeText(this@HomeActivity, "Gagal mencari lokasi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addMarker(geoPoint: GeoPoint, title: String, setCenter: Boolean = false) {
        val marker = Marker(mapView)
        marker.position = geoPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = title
        marker.showInfoWindow()
        mapView.overlays.add(marker) // Menambahkan marker ke overlay
        mapView.invalidate() // Refresh map

        if (setCenter) {
            mapView.controller.setCenter(geoPoint)
        }
    }

    private fun updateLocationTextView(geoPoint: GeoPoint) {
        if (isInternetAvailable(this)) {
            binding.lokasiTextView.text = "Lokasi: Sedang mencari alamat..."
            CoroutineScope(Dispatchers.IO).launch {
                var retryCount = 0
                val maxRetries = 3
                val retryDelay = 1000L // 1 detik
                var addressResult: String? = null // Variabel untuk menyimpan hasil

                while (retryCount < maxRetries && addressResult == null) {
                    try {
                        val geocoder = Geocoder(this@HomeActivity, Locale.getDefault())
                        val addresses = geocoder.getFromLocation(geoPoint.latitude, geoPoint.longitude, 1)

                        if (addresses != null && addresses.isNotEmpty()) {
                            val address = addresses[0]
                            addressResult = address.getAddressLine(0) // Simpan hasil ke variabel
                        } else {
                            Log.e("updateLocationTextView", "Alamat tidak ditemukan untuk GeoPoint: $geoPoint")
                            addressResult = "Lokasi: Tidak ditemukan"
                        }
                    } catch (e: IOException) {
                        Log.e("updateLocationTextView", "Error mendapatkan alamat dari GeoPoint: $geoPoint", e)
                        if (e.message == "grpc failed") {
                            retryCount++
                            Log.w("updateLocationTextView", "Percobaan ke-$retryCount gagal, mencoba lagi dalam $retryDelay ms...")
                            delay(retryDelay) // Tunggu sebentar sebelum mencoba lagi
                        } else {
                            addressResult = "Lokasi: Gagal mendapatkan alamat"
                            break // Keluar dari loop while jika error bukan grpc failed
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (addressResult != null) {
                        binding.lokasiTextView.text = "Lokasi: $addressResult"
                    } else {
                        binding.lokasiTextView.text = "Lokasi: Gagal mendapatkan alamat setelah beberapa kali percobaan"
                    }
                }
            }
        } else {
            Log.e("updateLocationTextView", "Tidak ada koneksi internet")
            binding.lokasiTextView.text = "Lokasi: Tidak ada koneksi internet"
        }
    }

    private fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openImageChooser()
            } else {
                showPermissionDeniedDialog()
            }
        }
    }

    // Bagian untuk menangani pemilihan dan upload gambar
    private fun checkPermissionsAndOpenChooser() {
        if (!isInternetAvailable(this)) {
            showErrorMessage("Tidak ada koneksi internet")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 ke atas: READ_MEDIA_IMAGES
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
                openImageChooser()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_IMAGES), PERMISSION_REQUEST_CODE)
            }
        } else {
            // Android 12 ke bawah: READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                openImageChooser()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
            }
        }
    }

    // Tambahkan dialog untuk permission yang ditolak
    private fun showPermissionDeniedDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Izin Diperlukan")
            .setMessage("Aplikasi memerlukan izin penyimpanan untuk mengakses galeri. Silakan berikan izin di pengaturan aplikasi.")
            .setPositiveButton("Buka Pengaturan") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Batal") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    // Fungsi untuk membuka pengaturan aplikasi
    private fun openAppSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", packageName, null)
        startActivity(intent)
    }

    private fun openImageChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        pickImageFromGallery.launch(intent)
    }

    private val pickImageFromGallery = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = result.data?.data
            selectedImageUri?.let { uri ->
                try {
                    val imageFile = compressImage(uri)
                    Glide.with(this).load(uri).into(binding.buktiImageView)
                    uploadImageToImgur(imageFile)
                } catch (e: Exception) {
                    showErrorMessage("Gagal memproses gambar: ${e.message}")
                }
            }
        }
    }

    private fun compressImage(imageUri: Uri): File {
        val inputStream = contentResolver.openInputStream(imageUri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        val compressedImageFile = File.createTempFile("compressed_", ".jpg", cacheDir)

        FileOutputStream(compressedImageFile).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        }

        return compressedImageFile
    }

    private fun getRealPathFromURI(contentUri: Uri): String {
        val proj = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = contentResolver.query(contentUri, proj, null, null, null)
        val columnIndex = cursor?.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        cursor?.moveToFirst()
        val path = cursor?.getString(columnIndex ?: 0) ?: ""
        cursor?.close()
        return path
    }

    private fun uploadImageToImgur(imageFile: File) {
        // Tambahkan state loading
        binding.uploadButton.isEnabled = false
        binding.submitButton.isEnabled = false

        try {
            // Tambahkan timeout untuk upload
            val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("image", imageFile.name, requestFile)

            RetrofitClient.instance.uploadImage(body).enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    binding.uploadButton.isEnabled = true
                    binding.submitButton.isEnabled = true

                    if (response.isSuccessful) {
                        try {
                            val responseBody = response.body()?.string()
                            val link = responseBody?.let { getImgurLinkFromResponse(it) }
                            if (link != null) {
                                uploadedImageUrl = link
                                showSuccessMessage("Gambar berhasil diupload")
                            } else {
                                showErrorMessage("Format response tidak valid")
                            }
                        } catch (e: Exception) {
                            showErrorMessage("Gagal memproses response: ${e.message}")
                        }
                    } else {
                        showErrorMessage("Upload gagal: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    binding.uploadButton.isEnabled = true
                    binding.submitButton.isEnabled = true
                    showErrorMessage("Koneksi gagal: ${t.message}")
                }
            })
        } catch (e: Exception) {
            binding.uploadButton.isEnabled = true
            binding.submitButton.isEnabled = true
            showErrorMessage("Error: ${e.message}")
        }
    }

    private fun showSuccessMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(getColor(R.color.success_color))
            .show()
    }

    private fun showErrorMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(getColor(R.color.error_color))
            .setAction("Coba Lagi") {
                if (selectedImageUri != null) {
                    selectedImageUri?.let { uri ->
                        val imageFile = compressImage(uri)
                        uploadImageToImgur(imageFile)
                    }
                }
            }
            .show()
    }

    private fun getImgurLinkFromResponse(response: String): String? {
        val regex = "\"link\":\"(.*?)\"".toRegex()
        val matchResult = regex.find(response)
        return matchResult?.groups?.get(1)?.value?.replace("\\/", "/")
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    private fun simpanDataKeFirebase(laporan: Laporan) {
        val laporanId = database.child("laporan").push().key ?: return // Generate unique ID
        val currentUser = FirebaseAuth.getInstance().currentUser
        val userId = currentUser?.uid ?: return // Get current user ID

        // Set user ID to the report
        val updatedLaporan = laporan.copy(userId = userId)

        database.child("laporan").child(laporanId).setValue(updatedLaporan)
            .addOnSuccessListener {
                Toast.makeText(this, "Laporan berhasil disimpan", Toast.LENGTH_SHORT).show()
                // Kosongkan form
                binding.buktiImageView.setImageResource(R.drawable.placeholder_image) // Reset gambar
                uploadedImageUrl = null
                binding.namaPelaporEditText.setText("")
                binding.nomorPolisiEditText.setText("")
                binding.merkTypeEditText.setText("")
                binding.lokasiTextView.text = "Lokasi: "

                // Reset selectedLocation dan hapus marker
                selectedLocation = null
                mapView.overlays.removeAll { it is Marker }
                mapView.invalidate()

                // Tampilkan SnackBar (lebih baik daripada Toast)
                Snackbar.make(binding.root, "Laporan berhasil disimpan", Snackbar.LENGTH_SHORT).show()

                // Kembali ke halaman sebelumnya
                finish()
            }
            .addOnFailureListener {    database = Firebase.database.reference
                Toast.makeText(this, "Gagal menyimpan laporan: ${it.message}", Toast.LENGTH_SHORT).show()
                Log.e("HomeActivity", "Gagal menyimpan laporan", it)
            }
    }
}