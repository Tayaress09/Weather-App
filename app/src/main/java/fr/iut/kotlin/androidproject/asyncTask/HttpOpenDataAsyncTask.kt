package fr.iut.kotlin.androidproject.asyncTask

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.location.Location
import android.os.AsyncTask
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.annotation.RequiresApi
import fr.iut.kotlin.androidproject.R
import fr.iut.kotlin.androidproject.WeatherData
import fr.iut.kotlin.androidproject.WeatherListAdapter
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class HttpOpenDataAsyncTask : AsyncTask<Any, Void, String>() {

    private val host : String = "https://public.opendatasoft.com/api/records/1.0/search/?dataset=arome-0025-enriched&q=&rows=100&sort=-forecast"
    private lateinit var location : Location
    private lateinit var tvCommune : TextView
    private lateinit var alertDialog: AlertDialog
    private lateinit var adapter: WeatherListAdapter
    private lateinit var weatherList : MutableList<WeatherData>

    override fun doInBackground(vararg params: Any?): String {
        adapter = params[0] as WeatherListAdapter
        weatherList = params[1] as MutableList<WeatherData>
        location = params[2] as Location
        alertDialog = params[3] as AlertDialog
        tvCommune = params[4] as TextView

        val finalHost = host + "&geofilter.distance=" + location.latitude + "," + location.longitude + ",1500"
        val url = URL(finalHost)
        val urlConnection = url.openConnection() as HttpURLConnection
        Log.e("APPLOG", finalHost)

        var text = ""
        if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
            val input = BufferedReader(InputStreamReader(urlConnection.inputStream))
            text = input.readLine()
            input.close()
        }
        urlConnection.disconnect()
        return text
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("SetTextI18n", "SimpleDateFormat")
    override fun onPostExecute(result: String?) {
        val nhits = JSONObject(result).getString("nhits")
        Log.e("APPLOG", "nb Data : $nhits")
        val records = JSONObject(result).getJSONArray("records")

        val weatherDataList : MutableList<WeatherAllData> = mutableListOf()
        var hasCommune = false
        //Commence à 1 car la 1ère data est incomplète
        for (i in 1 until 40) {
            val item = records.getJSONObject(i).getJSONObject("fields")

            var date = ""
            if (item.has("forecast"))
                date = item.getString("forecast")

            var temperature = ""
            if (item.has("2_metre_temperature"))
                temperature = BigDecimal(item.getString("2_metre_temperature")).setScale(1, RoundingMode.HALF_EVEN).toString()

            var humidity = ""
            if (item.has("relative_humidity"))
                humidity = BigDecimal(item.getString("relative_humidity")).setScale(1, RoundingMode.HALF_EVEN).toString()

            var precipitation = ""
            if (item.has("total_water_precipitation"))
                precipitation = BigDecimal(item.getString("total_water_precipitation")).setScale(1, RoundingMode.HALF_EVEN).toString()

            var windSpeed = ""
            if (item.has("wind_speed"))
                windSpeed = BigDecimal(item.getString("wind_speed")).setScale(1, RoundingMode.HALF_EVEN).toString()

            var solarRadiation = ""
            if (item.has("surface_net_solar_radiation"))
                solarRadiation = BigDecimal(item.getString("surface_net_solar_radiation")).setScale(0, RoundingMode.HALF_EVEN).toString()

            val wData = WeatherAllData(date, temperature, humidity, precipitation, windSpeed, solarRadiation)
            weatherDataList.add(wData)
            Log.e("APPLOG", wData.toString())

            if (!hasCommune && item.has("commune")) {
                tvCommune.visibility = View.VISIBLE
                tvCommune.text = item.getString("commune").toString()
                hasCommune = true
            }
        }

        val nightList : MutableList<WeatherAllData> = mutableListOf()       //0
        val morningList : MutableList<WeatherAllData> = mutableListOf()     //1
        val afternoonList : MutableList<WeatherAllData> = mutableListOf()   //2
        val eveningList : MutableList<WeatherAllData> = mutableListOf()     //3

        var firstVal = true
        for (i in 0 until weatherDataList.size) {
            when (getTimeZone(weatherDataList[i].date)) {
                0 -> {
                    if (!firstVal && getTimeZone(weatherDataList[i-1].date) != getTimeZone(weatherDataList[i].date))
                        addWeatherData(eveningList, "Soir")
                    nightList.add(weatherDataList[i])
                }
                1 -> {
                    if (!firstVal && getTimeZone(weatherDataList[i-1].date) != getTimeZone(weatherDataList[i].date))
                        addWeatherData(nightList, "Nuit")
                    morningList.add(weatherDataList[i])
                }
                2 -> {
                    if (!firstVal && getTimeZone(weatherDataList[i-1].date) != getTimeZone(weatherDataList[i].date))
                        addWeatherData(morningList, "Matin")
                    afternoonList.add(weatherDataList[i])
                }
                3 -> {
                    if (!firstVal && getTimeZone(weatherDataList[i-1].date) != getTimeZone(weatherDataList[i].date))
                        addWeatherData(afternoonList, "Après-midi")
                    eveningList.add(weatherDataList[i])
                }
            }
            firstVal = false
        }
        alertDialog.dismiss()
        adapter.notifyDataSetChanged()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getTimeZone(sDateUTC : String): Int {
        val cutDate = sDateUTC.substringBefore('+')
        val date = LocalDateTime.parse(cutDate).atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()

        return when (date.hour) {
            in 8..12 -> 1       //Morning
            in 13..17 -> 2      //Afternoon
            in 18..22 -> 3      //Evening
            else -> 0           //Night
        }
    }

    //total_water_precipitation : 10ème de pouces/heure
    //x / 40 * 24 et ça donne des cm
    private fun addWeatherData(list : MutableList<WeatherAllData>, period: String) {

        if (list.size > 0) {
            var temp = 0.0
            var minTemp = list[0].temperature.toDouble()
            var maxTemp = list[0].temperature.toDouble()
            var windSpeed = 0.0

            for (i in 0 until list.size) {
                temp += list[i].temperature.toDouble()
                if (list[i].temperature.toDouble() < minTemp)
                    minTemp = list[i].temperature.toDouble()
                else if (list[i].temperature.toDouble() > maxTemp)
                    maxTemp = list[i].temperature.toDouble()

                windSpeed += list[i].windSpeed.toDouble()
            }

            val solarRadiation = (list[list.size-1].solarRadiation.toDouble() - list[0].solarRadiation.toDouble()) / list.size
            val precipitation = ((list[list.size-1].precipitation.toDouble() - list[0].precipitation.toDouble()) / list.size) / 4 * 24

            val icon: Int = when {
                solarRadiation > 2000000 -> R.drawable.ic_sun
                solarRadiation > 800000 && precipitation > 0.2 -> R.drawable.ic_sun_rain
                solarRadiation > 800000 -> R.drawable.ic_sun_cloud
                solarRadiation > 100000 && precipitation > 1 -> R.drawable.ic_double_rain
                solarRadiation > 100000 && precipitation > 0.2 -> R.drawable.ic_simple_rain
                solarRadiation > 100000 -> R.drawable.ic_simple_cloud
                else -> R.drawable.ic_moon_cloud
            }

            weatherList.add(
                WeatherData(
                    list[list.size-1].date.substringBefore('T') + " " + period,
                    icon,
                    BigDecimal(temp / list.size).setScale(1, RoundingMode.HALF_EVEN).toDouble(),
                    BigDecimal(minTemp).setScale(0, RoundingMode.HALF_EVEN).toInt() -1,
                    BigDecimal(maxTemp).setScale(0, RoundingMode.HALF_EVEN).toInt() +1,
                    list[list.size - 1].precipitation.toDouble() - list[0].precipitation.toDouble(),
                    windSpeed / list.size
                )
            )
            list.clear()
        }
    }

}

data class WeatherAllData(var date: String, var temperature: String, var humidity: String, var precipitation: String, var windSpeed: String, var solarRadiation: String)