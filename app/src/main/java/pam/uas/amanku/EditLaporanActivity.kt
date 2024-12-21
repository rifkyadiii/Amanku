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
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import pam.uas.amanku.databinding.ActivityHomeBinding
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
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
import pam.uas.amanku.databinding.ActivityEditLaporanBinding

class EditLaporanActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditLaporanBinding
    private var selectedImageUri: Uri? = null
    private var uploadedImageUrl: String? = null
    private lateinit var currentPhotoPath: String
    private lateinit var mapView: MapView
    private var selectedLocation: GeoPoint? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var searchLocationEditText: EditText
    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "MapPrefs"
    private val ZOOM_LEVEL_KEY = "zoomLevel"
    private lateinit var database: DatabaseReference
    private var laporanId: String = ""
    private lateinit var currentLaporanId: String

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditLaporanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance().getReference("laporan")

        binding.backButton.setOnClickListener {
            finish()
        }

        // Get laporanId from intent
        laporanId = intent.getStringExtra("LAPORAN_ID") ?: run {
            Toast.makeText(this, "ID Laporan tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeComponents()
        loadLaporanData()
        setupButtons()
    }

    private fun loadLaporanData() {
        database.orderByChild("nomorPolisi").equalTo(laporanId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (laporanSnapshot in snapshot.children) {
                            currentLaporanId = laporanSnapshot.key ?: continue
                            val laporan = laporanSnapshot.getValue(Laporan::class.java) ?: continue

                            // Fill form fields
                            binding.editNamaPelapor.setText(laporan.namaPelapor)
                            binding.editNomorPolisi.setText(laporan.nomorPolisi)
                            binding.editMerkType.setText(laporan.merkType)
                            binding.editLokasi.text = "Lokasi: ${laporan.lokasi}"

                            // Load image
                            laporan.buktiImageUrl?.let { url ->
                                uploadedImageUrl = url
                                Glide.with(this@EditLaporanActivity)
                                    .load(url)
                                    .placeholder(R.drawable.placeholder_image)
                                    .into(binding.editImageViewBukti)
                            }

                            // Set map location
                            if (laporan.latitude != null && laporan.longitude != null) {
                                selectedLocation = GeoPoint(laporan.latitude, laporan.longitude)
                                updateMapLocation(selectedLocation!!)
                            }
                            break
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@EditLaporanActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun initializeComponents() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mapView = binding.mapView
        searchLocationEditText = binding.searchLocationEditText

        setupMap()
        setupSearch()
    }

    private fun setupButtons() {
        binding.editUploadButton.setOnClickListener {
            checkPermissionsAndOpenChooser()
        }

        binding.buttonSimpanPerubahan.setOnClickListener {
            validateAndUpdateLaporan()
        }
    }

    private fun updateMapLocation(location: GeoPoint) {
        mapView.overlays.removeAll { it is Marker }
        val marker = Marker(mapView).apply {
            position = location
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Lokasi Laporan"
        }
        mapView.overlays.add(marker)
        mapView.controller.animateTo(location)
        mapView.controller.setZoom(15.0)
        mapView.invalidate()
    }

    private fun validateAndUpdateLaporan() {
        val updatedLaporan = Laporan(
            buktiImageUrl = uploadedImageUrl,
            namaPelapor = binding.editNamaPelapor.text.toString(),
            nomorPolisi = binding.editNomorPolisi.text.toString(),
            merkType = binding.editMerkType.text.toString(),
            lokasi = binding.editLokasi.text.toString().removePrefix("Lokasi: "),
            latitude = selectedLocation?.latitude,
            longitude = selectedLocation?.longitude,
            userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        )

        database.child(currentLaporanId).setValue(updatedLaporan)
            .addOnSuccessListener {
                Toast.makeText(this, "Laporan berhasil diperbarui", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, DaftarLaporanActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Gagal update: ${error.message}", Toast.LENGTH_SHORT).show()
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
            val geocoder = Geocoder(this@EditLaporanActivity, Locale.getDefault())
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
                        Toast.makeText(this@EditLaporanActivity, "Lokasi tidak ditemukan", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    e.printStackTrace()
                    Toast.makeText(this@EditLaporanActivity, "Gagal mencari lokasi", Toast.LENGTH_SHORT).show()
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
            binding.editLokasi.text = "Lokasi: Sedang mencari alamat..."
            CoroutineScope(Dispatchers.IO).launch {
                var retryCount = 0
                val maxRetries = 3
                val retryDelay = 1000L // 1 detik
                var addressResult: String? = null // Variabel untuk menyimpan hasil

                while (retryCount < maxRetries && addressResult == null) {
                    try {
                        val geocoder = Geocoder(this@EditLaporanActivity, Locale.getDefault())
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
                        binding.editLokasi.text = "Lokasi: $addressResult"
                    } else {
                        binding.editLokasi.text = "Lokasi: Gagal mendapatkan alamat setelah beberapa kali percobaan"
                    }
                }
            }
        } else {
            Log.e("updateLocationTextView", "Tidak ada koneksi internet")
            binding.editLokasi.text = "Lokasi: Tidak ada koneksi internet"
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
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                setupMap()
            } else {
                Toast.makeText(this, "Izin lokasi ditolak", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Bagian untuk menangani pemilihan dan upload gambar
    private fun checkPermissionsAndOpenChooser() {
        Dexter.withContext(this)
            .withPermissions(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    if (report.areAllPermissionsGranted()) {
                        openImageChooser()
                    } else {
                        Toast.makeText(this@EditLaporanActivity, "Izin diperlukan untuk mengakses kamera dan penyimpanan.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(permissions: List<PermissionRequest>, token: PermissionToken) {
                    token.continuePermissionRequest()
                }
            })
            .check()
    }

    private fun openImageChooser() {
        val options = arrayOf<CharSequence>("Ambil Foto", "Pilih dari Galeri", "Batal")
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Pilih Sumber Gambar")
        builder.setItems(options) { dialog, item ->
            when {
                options[item] == "Ambil Foto" -> dispatchTakePictureIntent()
                options[item] == "Pilih dari Galeri" -> {
                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    pickImageFromGallery.launch(intent) // Panggil launch() untuk menjalankan intent
                }
                options[item] == "Batal" -> dialog.dismiss()
            }
        }
        builder.show()
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            val photoFile: File? = try {
                createImageFile()
            } catch (ex: IOException) {
                null
            }
            photoFile?.also {
                val photoURI: Uri = FileProvider.getUriForFile(this, "pam.uas.amanku.fileprovider", it)
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                takePicture.launch(takePictureIntent)
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(java.util.Date())
        val storageDir: File? = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    private val pickImageFromGallery = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = result.data?.data
            selectedImageUri?.let { uri ->
                val imageFile = compressImage(uri)
                Glide.with(this).load(uri).into(binding.editImageViewBukti)
                uploadImageToImgur(imageFile)
            }
        }
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageFile = File(currentPhotoPath)
            selectedImageUri = Uri.fromFile(imageFile)
            Glide.with(this).load(selectedImageUri).into(binding.editImageViewBukti)
            uploadImageToImgur(imageFile)
        }
    }

    private fun compressImage(imageUri: Uri): File {
        val filePath = getRealPathFromURI(imageUri)
        val bitmap = BitmapFactory.decodeFile(filePath)
        val compressedImageFile = File.createTempFile("compressed_", ".jpg")
        val outputStream = FileOutputStream(compressedImageFile)

        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)

        outputStream.flush()
        outputStream.close()

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
        val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("image", imageFile.name, requestFile)

        RetrofitClient.instance.uploadImage(body).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    val responseBody = response.body()?.string()
                    val link = responseBody?.let { getImgurLinkFromResponse(it) }
                    uploadedImageUrl = link
                    Toast.makeText(this@EditLaporanActivity, "Gambar berhasil diupload: $link", Toast.LENGTH_SHORT).show()
                    Log.d("Upload", "Link: $link")
                } else {
                    Toast.makeText(this@EditLaporanActivity, "Upload gagal", Toast.LENGTH_SHORT).show()
                    Log.e("Upload", "Error: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Toast.makeText(this@EditLaporanActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("Upload", "Error: ${t.message}")
            }
        })
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

    private fun updateLaporanInFirebase(laporan: Laporan, laporanId: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val userId = currentUser?.uid ?: return

        // Verifikasi kepemilikan laporan
        database.child("laporan").child(laporanId).get().addOnSuccessListener { snapshot ->
            val existingLaporan = snapshot.getValue(Laporan::class.java)
            if (existingLaporan?.userId != userId) {
                Toast.makeText(this, "Anda tidak memiliki izin untuk mengedit laporan ini", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            // Update laporan dengan mempertahankan userId
            val updatedLaporan = laporan.copy(userId = userId)

            database.child("laporan").child(laporanId).setValue(updatedLaporan)
                .addOnSuccessListener {
                    Snackbar.make(binding.root, "Laporan berhasil diperbarui", Snackbar.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { error ->
                    Toast.makeText(this, "Gagal memperbarui laporan: ${error.message}", Toast.LENGTH_SHORT).show()
                    Log.e("EditLaporanActivity", "Gagal memperbarui laporan", error)
                }
        }.addOnFailureListener { error ->
            Toast.makeText(this, "Gagal memverifikasi kepemilikan laporan: ${error.message}", Toast.LENGTH_SHORT).show()
            Log.e("EditLaporanActivity", "Gagal memverifikasi kepemilikan", error)
        }
    }
}