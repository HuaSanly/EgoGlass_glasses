package com.egoglass.glasses.platform.android

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.egoglass.glasses.sensors.ImuCapabilities
import com.egoglass.glasses.sensors.ImuSample
import com.egoglass.glasses.sensors.ImuSensorDescriptor
import com.egoglass.glasses.sensors.ImuSensorType
import com.egoglass.glasses.sensors.ImuSource
import com.egoglass.glasses.sensors.ImuSourceListener

internal const val REQUESTED_SAMPLING_PERIOD_US = 10_000
private const val TAG = "EgoGlassImu"
private const val SAMPLE_LOG_INTERVAL = 500L

fun createAndroidImuSource(context: Context): ImuSource = AndroidImuSource(
    context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager,
)

internal class AndroidImuSource(
    private val sensorManager: SensorManager,
) : ImuSource {
    private val lock = Any()
    private val requestedSensors = listOf(
        ImuSensorType.ACCELEROMETER to Sensor.TYPE_ACCELEROMETER,
        ImuSensorType.GYROSCOPE to Sensor.TYPE_GYROSCOPE,
    )
    private val sensorTypesByAndroidType = requestedSensors.associate { (sensorType, type) ->
        type to sensorType
    }
    private val sequenceNumbers = mutableMapOf<ImuSensorType, Long>()

    private var generation = 0L
    private var listener: ImuSourceListener? = null
    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null
    private var sensorEventListener: SensorEventListener? = null

    override fun start(listener: ImuSourceListener) {
        val runGeneration: Long
        val handler: Handler
        synchronized(lock) {
            if (this.listener != null) return
            generation += 1
            runGeneration = generation
            this.listener = listener
            sequenceNumbers.clear()
            sensorThread = HandlerThread("egoglass-imu").also(HandlerThread::start)
            handler = Handler(requireNotNull(sensorThread).looper)
            sensorHandler = handler
            sensorEventListener = RunSensorEventListener(runGeneration, listener)
        }
        handler.post {
            val eventListener = currentEventListener(runGeneration, listener) ?: return@post
            registerSensors(
                runGeneration,
                listener,
                eventListener,
                handler,
            )
        }
    }

    override fun stop() {
        val handler: Handler?
        val thread: HandlerThread?
        val eventListener: SensorEventListener?
        synchronized(lock) {
            if (listener == null && sensorThread == null) return
            generation += 1
            listener = null
            sequenceNumbers.clear()
            handler = sensorHandler
            thread = sensorThread
            eventListener = sensorEventListener
            sensorHandler = null
            sensorThread = null
            sensorEventListener = null
        }
        if (handler != null && thread != null && eventListener != null) {
            handler.post {
                sensorManager.unregisterListener(eventListener)
                thread.quitSafely()
                Log.i(TAG, "imu_state=stopped")
            }
        } else {
            eventListener?.let(sensorManager::unregisterListener)
            thread?.quitSafely()
        }
    }

    private fun onSensorChanged(
        runGeneration: Long,
        runListener: ImuSourceListener,
        event: SensorEvent,
    ) {
        val sensorType = sensorTypesByAndroidType[event.sensor.type] ?: return
        val sequenceNumber: Long
        synchronized(lock) {
            if (generation != runGeneration || listener !== runListener) return
            sequenceNumber = sequenceNumbers.getOrDefault(sensorType, 0)
            sequenceNumbers[sensorType] = sequenceNumber + 1
        }
        if (event.values.size < 3) {
            val detail = "${sensorType.wireValue} returned fewer than three values"
            Log.w(TAG, "imu_sample_rejected=$detail")
            runListener.onImuError(detail)
            return
        }
        val sample = ImuSample(
            sensorType = sensorType,
            sequenceNumber = sequenceNumber,
            sensorEventMonotonicNs = event.timestamp,
            receivedAtElapsedRealtimeNs = SystemClock.elapsedRealtimeNanos(),
            accuracy = event.accuracy.coerceIn(-1, 3),
            values = event.values.take(3).map(Float::toDouble),
        )
        runListener.onSample(sample)
        if ((sequenceNumber + 1) % SAMPLE_LOG_INTERVAL == 0L) {
            Log.i(
                TAG,
                "imu_sensor=${sensorType.wireValue} samples=${sequenceNumber + 1}",
            )
        }
    }

    private fun registerSensors(
        runGeneration: Long,
        runListener: ImuSourceListener,
        eventListener: SensorEventListener,
        handler: Handler,
    ) {
        if (!isCurrent(runGeneration, runListener)) return
        val descriptors = mutableListOf<ImuSensorDescriptor>()
        val missing = mutableListOf<ImuSensorType>()
        requestedSensors.forEach { (sensorType, androidSensorType) ->
            check(sensorType.androidSensorType == androidSensorType)
            val sensor = sensorManager.getDefaultSensor(androidSensorType)
            val registered = sensor != null && sensorManager.registerListener(
                eventListener,
                sensor,
                REQUESTED_SAMPLING_PERIOD_US,
                0,
                handler,
            )
            if (sensor == null || !registered) {
                missing += sensorType
                Log.w(TAG, "imu_sensor=${sensorType.wireValue} available=false")
            } else {
                descriptors += sensor.toDescriptor(sensorType)
                Log.i(
                    TAG,
                    "imu_sensor=${sensorType.wireValue} available=true " +
                        "min_delay_us=${sensor.minDelay} max_delay_us=${sensor.maxDelay}",
                )
            }
        }
        if (!isCurrent(runGeneration, runListener)) {
            sensorManager.unregisterListener(eventListener)
            return
        }
        runListener.onCapabilities(
            ImuCapabilities(
                requestedSamplingPeriodUs = REQUESTED_SAMPLING_PERIOD_US,
                sensors = descriptors,
                missingSensorTypes = missing,
            )
        )
        Log.i(TAG, "imu_state=started registered_sensors=${descriptors.size}")
    }

    private fun isCurrent(runGeneration: Long, runListener: ImuSourceListener): Boolean =
        synchronized(lock) {
            generation == runGeneration && listener === runListener
        }

    private fun currentEventListener(
        runGeneration: Long,
        runListener: ImuSourceListener,
    ): SensorEventListener? = synchronized(lock) {
        sensorEventListener.takeIf {
            generation == runGeneration && listener === runListener
        }
    }

    private inner class RunSensorEventListener(
        private val runGeneration: Long,
        private val runListener: ImuSourceListener,
    ) : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            this@AndroidImuSource.onSensorChanged(runGeneration, runListener, event)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun Sensor.toDescriptor(sensorType: ImuSensorType) = ImuSensorDescriptor(
        sensorType = sensorType,
        name = name,
        vendor = vendor,
        version = version,
        resolution = resolution.toDouble(),
        maxRange = maximumRange.toDouble(),
        minDelayUs = minDelay.coerceAtLeast(0),
        maxDelayUs = maxDelay.coerceAtLeast(0),
        isWakeUp = isWakeUpSensor,
    )
}
