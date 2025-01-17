package com.a2krocks.widget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.graphics.Color
import android.location.LocationManager
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.widget.RemoteViews
import com.a2krocks.widget.services.AlarmHandler
import com.a2krocks.widget.services.WeatherService
import com.a2krocks.widget.services.capitalizeWords
import com.a2krocks.widget.services.data.WeatherData
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.math.roundToInt

/**
 * Implementation of App Widget functionality.
 */
class OneplusWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDisabled(context: Context) {
        val alarmHandler = AlarmHandler(context)
        alarmHandler.cancelAlarmManager()
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        Log.d("WidgetService", "Widget is Starting")
        updateAppWidget(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    @SuppressLint("MissingPermission")
    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.oneplus_widget)
        val locationPreferences = context.getSharedPreferences(WeatherService.LOCATION_PREFERENCE, Context.MODE_PRIVATE)
        val lat = locationPreferences.getString(WeatherService.LOCATION_PREFERENCE_LATITUDE, null)
        val long = locationPreferences.getString(WeatherService.LOCATION_PREFERENCE_LONGITUDE, null)
        val isLocationSaved = lat != null && long != null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, 2000, 10f
        ) {

            val edit = locationPreferences.edit()
            edit.putString(WeatherService.LOCATION_PREFERENCE_LATITUDE, it.latitude.toString()).apply()
            edit.putString(WeatherService.LOCATION_PREFERENCE_LONGITUDE, it.longitude.toString()).apply()

            Log.d("location.get", "Got Location from GPS")
            Log.d("location.set.local", "Saved Location from GPS")
        }
        Log.d("location.get.local", "Got Location from Saved : $isLocationSaved")

        if (isLocationSaved) {
            val data = WeatherService().retrofitBuilder.getData(
                lat!!, long!!, context.getString(R.string.apiId)
            )
            data.enqueue(object : Callback<WeatherData> {
                override fun onResponse(call: Call<WeatherData>, response: Response<WeatherData>) {
                    if (response.isSuccessful) {
                        val weatherData = response.body()
                        if (weatherData != null) {
                            val temperature = weatherData.main.temp
                            val roundedTemperature = temperature.roundToInt()


                            val temperatureFormatted = "${roundedTemperature}℃"
                            val weatherDescription = weatherData.weather[0].description.capitalizeWords()

                            val spannable = SpannableString(temperatureFormatted)
                            spannable.setSpan(
                                ForegroundColorSpan(Color.WHITE),
                                1,
                                temperatureFormatted.length,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            spannable.setSpan(
                                RelativeSizeSpan(0.4f),
                                temperatureFormatted.length - 1,
                                temperatureFormatted.length,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )

                            views.setTextViewText(R.id.tempTextView, spannable)
                            views.setTextViewText(R.id.descTextView, weatherDescription)

                        }
                        Log.d("response.weatherData.isNull", (weatherData == null).toString())
                        Log.d("response.isSuccessful", response.isSuccessful.toString())

                    } else {
                        val errorBody = response.errorBody()
                        Log.d("response.isUnsuccessful", errorBody.toString())
                    }

                    updateAndTriggerAppWidgetUpdate(context, appWidgetManager, appWidgetId, views)
                }

                override fun onFailure(call: Call<WeatherData>, t: Throwable) {
                    Log.e("response.failure", t.stackTraceToString())
                    updateAndTriggerAppWidgetUpdate(context, appWidgetManager, appWidgetId, views)
                }

            })

        }

        updateAndTriggerAppWidgetUpdate(context, appWidgetManager, appWidgetId, views)
    }

    private fun updateAndTriggerAppWidgetUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, views: RemoteViews) {
        appWidgetManager.updateAppWidget(appWidgetId, views)
        val alarmHandler = AlarmHandler(context)
        alarmHandler.cancelAlarmManager()
        alarmHandler.setAlarmManager()
    }
}

