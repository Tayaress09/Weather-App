package fr.iut.kotlin.androidproject

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity(), View.OnClickListener, LocationListener {

    private var URL_TIME_TEXT = "http://worldtimeapi.org/api/timezone/Europe/paris"
    private lateinit var tvDate : TextView
    private lateinit var btDate : Button
    private lateinit var lvWeatherInfo : ListView
    private lateinit var locationManager: LocationManager
    private val locationPermissionCode = 2
    private lateinit var alertDialog: AlertDialog
    private var loadData = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvDate = findViewById(R.id.tvDate)
        btDate = findViewById(R.id.btDate)
        btDate.setOnClickListener(this)
        lvWeatherInfo = findViewById(R.id.weatherList)

        getLocation()
    }

    override fun onClick(v: View?) {
        when(v?.id) {
            R.id.btDate -> {
                HttpConnectServerAsyncTask().execute(URL_TIME_TEXT, tvDate)
            }
        }
    }

    private fun getLocation() {
        //Vérifier si la localisation est activée
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationPermissionCode)
        } else {
            setProgressDialog()
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 100F, this)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onLocationChanged(location: Location) {
        if (!loadData) {
            runLocationAsyncTask(location)
        }
        Toast.makeText(this, "Latitude: " + location.latitude + " , Longitude: " + location.longitude, Toast.LENGTH_LONG).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()
                getLocation()
            }
            else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun runLocationAsyncTask(location: Location) {
        HttpOpenDataAsyncTask().execute(location, lvWeatherInfo, alertDialog, this)
        loadData = true
    }

    @SuppressLint("SetTextI18n")
    fun setProgressDialog() {
        val loading: AlertDialog.Builder = AlertDialog.Builder(this)
        loading.setCancelable(false)
        val inflater: LayoutInflater = this.getLayoutInflater()
        loading.setView(inflater.inflate(R.layout.custom_dialog_loading, null))

        // Displaying the dialog
        alertDialog = loading.create()
        alertDialog.show()
    }

}