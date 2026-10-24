package com.lumen.deviceinfo.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SensorReader(private val context: Context) {

    data class SensorMeta(
        val name: String,
        val vendor: String,
        val typeName: String,
        val range: String,
        val resolution: String,
        val power: String,
        val type: Int
    )

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val _sensors = MutableStateFlow<List<SensorMeta>>(emptyList())
    val sensors: StateFlow<List<SensorMeta>> = _sensors

    private val _liveValues = MutableStateFlow<Map<Int, String>>(emptyMap())
    val liveValues: StateFlow<Map<Int, String>> = _liveValues

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val values = event.values
            if (values.isEmpty()) return
            val text = values.joinToString(", ") { v -> formatValue(v) }
            _liveValues.value = _liveValues.value + (event.sensor.type to text)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun loadSensorList() {
        try {
            val list = sm.getSensorList(Sensor.TYPE_ALL)
            _sensors.value = list.map { s ->
                SensorMeta(
                    name = s.name.ifBlank { NA },
                    vendor = s.vendor.ifBlank { NA },
                    typeName = sensorTypeName(s.type),
                    range = if (s.maximumRange > 0) formatValue(s.maximumRange) else NA,
                    resolution = if (s.resolution > 0) formatValue(s.resolution) else NA,
                    power = if (s.power > 0) "%.2f mA".format(s.power) else NA,
                    type = s.type
                )
            }
        } catch (e: Exception) {
            _sensors.value = emptyList()
        }
    }

    fun startListening() {
        try {
            val types = listOf(
                Sensor.TYPE_ACCELEROMETER,
                Sensor.TYPE_GYROSCOPE,
                Sensor.TYPE_MAGNETIC_FIELD,
                Sensor.TYPE_LIGHT,
                Sensor.TYPE_PROXIMITY,
                Sensor.TYPE_PRESSURE,
                Sensor.TYPE_AMBIENT_TEMPERATURE,
                Sensor.TYPE_RELATIVE_HUMIDITY
            )
            types.forEach { t ->
                val s = sm.getDefaultSensor(t)
                if (s != null) sm.registerListener(listener, s, SensorManager.SENSOR_DELAY_UI)
            }
        } catch (e: Exception) { }
    }

    fun stopListening() {
        try {
            sm.unregisterListener(listener)
        } catch (e: Exception) { }
    }

    private fun formatValue(v: Float): String {
        val abs = kotlin.math.abs(v)
        return when {
            abs == 0f -> "0"
            abs >= 1000 -> "%.0f".format(v)
            abs >= 100 -> "%.1f".format(v)
            abs >= 1 -> "%.2f".format(v)
            else -> "%.3f".format(v)
        }
    }

    private fun sensorTypeName(type: Int): String = when (type) {
        Sensor.TYPE_ACCELEROMETER -> "Acelerómetro"
        Sensor.TYPE_GYROSCOPE -> "Giroscopio"
        Sensor.TYPE_MAGNETIC_FIELD -> "Magnetómetro"
        Sensor.TYPE_LIGHT -> "Luz"
        Sensor.TYPE_PROXIMITY -> "Proximidad"
        Sensor.TYPE_PRESSURE -> "Presión"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "Temperatura"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "Humedad"
        Sensor.TYPE_GRAVITY -> "Gravedad"
        Sensor.TYPE_LINEAR_ACCELERATION -> "Acel. lineal"
        Sensor.TYPE_ROTATION_VECTOR -> "Rotación"
        Sensor.TYPE_STEP_COUNTER -> "Pasos"
        Sensor.TYPE_STEP_DETECTOR -> "Detector de pasos"
        Sensor.TYPE_SIGNIFICANT_MOTION -> "Movimiento"
        Sensor.TYPE_GAME_ROTATION_VECTOR -> "Rotación juego"
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "Rotación geomagnética"
        Sensor.TYPE_ORIENTATION -> "Orientación"
        else -> "Tipo $type"
    }
}
