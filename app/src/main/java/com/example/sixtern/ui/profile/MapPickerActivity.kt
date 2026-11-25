package com.example.sixtern.ui.profile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.sixtern.R
import com.example.sixtern.databinding.ActivityMapPickerBinding
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Map picker offline-tanpa-API-key: WebView + Leaflet + OpenStreetMap.
 * Ketuk peta -> marker pindah -> lintang/bujur dikirim balik via JavascriptInterface.
 * Plus tombol "Lokasi saya" (GPS) untuk centering cepat.
 */
class MapPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapPickerBinding
    private var lat: Double = Double.NaN
    private var lng: Double = Double.NaN

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) centerToMyLocation()
        else Toast.makeText(this, getString(R.string.location_denied), Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
        lng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN)
        if (lat.isNaN() || lng.isNaN()) {
            // Default: Medan (USU) agar peta langsung berguna.
            lat = -6.0; lng = 106.0
            // Medan tepatnya; fallback bila intent kosong.
            lat = 3.5952; lng = 98.6722
        }
        renderCoord()
        binding.mapWeb.settings.javaScriptEnabled = true
        binding.mapWeb.settings.domStorageEnabled = true
        binding.mapWeb.webViewClient = WebViewClient()
        binding.mapWeb.addJavascriptInterface(Bridge(), "AndroidBridge")
        binding.mapWeb.loadDataWithBaseURL(null, html(lat, lng), "text/html", "utf-8", null)
        binding.myLocationButton.setOnClickListener { requestMyLocation() }
        binding.saveButton.setOnClickListener {
            if (lat.isNaN() || lng.isNaN()) {
                Toast.makeText(this, "Ketuk peta dulu untuk memilih titik", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val out = Intent().putExtra(EXTRA_LAT, lat).putExtra(EXTRA_LNG, lng)
            setResult(RESULT_OK, out)
            finish()
        }
    }

    private fun renderCoord() {
        binding.coordText.text = if (lat.isNaN() || lng.isNaN()) "-" else
            getString(R.string.map_tap_hint, "%.6f".format(lat), "%.6f".format(lng))
    }

    private fun requestMyLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            centerToMyLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun centerToMyLocation() {
        try {
            val client = LocationServices.getFusedLocationProviderClient(this)
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        lat = loc.latitude; lng = loc.longitude
                        renderCoord()
                        binding.mapWeb.evaluateJavascript(
                            "setPoint($lat, $lng);", null
                        )
                    }
                }
        } catch (e: SecurityException) {
            Toast.makeText(this, getString(R.string.location_denied), Toast.LENGTH_SHORT).show()
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onPick(plat: Double, plng: Double) {
            runOnUiThread {
                lat = plat; lng = plng
                renderCoord()
            }
        }
    }

    private fun html(lat: Double, lng: Double): String = """
<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'/>
<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>
<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>
<style>html,body,#m{height:100%;margin:0} #m{border-radius:12px}</style></head>
<body><div id='m'></div><script>
var map = L.map('m').setView([$lat, $lng], 15);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'OpenStreetMap'}).addTo(map);
var marker = L.marker([$lat, $lng],{draggable:true}).addTo(map);
function send(p){ AndroidBridge.onPick(p.lat, p.lng); }
map.on('click', function(e){ marker.setLatLng(e.latlng); send(e.latlng); });
marker.on('dragend', function(){ send(marker.getLatLng()); });
function setPoint(a,b){ map.setView([a,b],16); marker.setLatLng([a,b]); }
</script></body></html>
""".trimIndent()

    override fun onDestroy() {
        binding.mapWeb.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"
    }
}
