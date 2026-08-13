package com.egoglass.glasses.orientation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class GameRotationOrientationSource(context: Context) : SensorEventListener {
    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val tracker = RelativeOrientationTracker()
    private val quaternion = FloatArray(4)

    @Volatile
    var latest: RelativeOrientation? = null
        private set

    fun start(): Boolean {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: return false
        return sensorManager.registerListener(this, sensor, SAMPLE_PERIOD_US)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        latest = null
        tracker.reset()
    }

    fun reset() {
        latest = null
        tracker.reset()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR) return
        SensorManager.getQuaternionFromVector(quaternion, event.values)
        latest = tracker.update(
            w = quaternion[0].toDouble(),
            x = quaternion[1].toDouble(),
            y = quaternion[2].toDouble(),
            z = quaternion[3].toDouble(),
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val SAMPLE_PERIOD_US = 20_000
    }
}
