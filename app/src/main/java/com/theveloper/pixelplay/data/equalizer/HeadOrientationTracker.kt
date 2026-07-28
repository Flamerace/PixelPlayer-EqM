// HeadOrientationTracker.kt
package com.theveloper.pixelplay.data.equalizer

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.PI

/**
 * Reads the device's accelerometer + magnetometer to compute a compass heading (yaw), and
 * reports it relative to whatever heading was current the last time [calibrateForward] was
 * called — i.e. "point the phone the way you're facing and calibrate" defines yaw = 0, which is
 * the reference [SurroundImagingEngine]'s placements are calibrated against.
 *
 * This class only reads sensors and reports an angle — it does no audio processing. Wire
 * [onYawChanged] to `DynamicBassProcessor.setHeadYaw(...)` from whatever owns both (a
 * Service/ViewModel), so this stays reusable outside the audio pipeline. No special runtime
 * permission is needed for these two sensors.
 *
 * Important real-world caveat: a magnetometer measures the *phone's* heading, not your head's.
 * This only tracks head rotation correctly if the phone is held/mounted in a fixed orientation
 * relative to your head while listening (e.g. propped in front of you) — it can't distinguish
 * "you turned your head" from "you rotated the phone in your hand." Also worth knowing:
 * TYPE_ROTATION_VECTOR is generally a cleaner fused signal than raw magnetometer + accelerometer
 * (less noisy near metal/magnetic interference); this uses the raw sensors as requested, with a
 * low-pass on the output to tame jitter.
 */
class HeadOrientationTracker(
    context: Context,
    private val onYawChanged: (yawRadians: Float) -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var haveGravity = false
    private var haveGeomagnetic = false

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var rawYaw = 0f
    private var smoothedYaw = 0f
    private var referenceYaw = 0f

    // This is a control-rate signal (SurroundImagingEngine only samples it once per audio
    // buffer), so smoothing costs nothing in responsiveness that actually matters, and tames
    // raw-magnetometer jitter noticeably.
    private val smoothing = 0.85f
        set(value) {
            field = value.coerceIn(0.1f, 1.0f)
        }

    fun start() {
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /** Call while facing the calibration reference direction to define that heading as yaw = 0. */
    fun calibrateForward() {
        referenceYaw = rawYaw
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, 3)
                haveGravity = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                haveGeomagnetic = true
            }
            else -> return
        }
        if (!haveGravity || !haveGeomagnetic) return
        if (!SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) return

        SensorManager.getOrientation(rotationMatrix, orientation)
        rawYaw = orientation[0] // radians, [-PI, PI], 0 = magnetic north, positive = clockwise/east

        smoothedYaw += (1f - smoothing) * wrap(rawYaw - smoothedYaw)
        smoothedYaw = wrap(smoothedYaw)

        onYawChanged(wrap(smoothedYaw - referenceYaw))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* no-op */ }

    private fun wrap(angle: Float): Float {
        val twoPi = (2 * PI).toFloat()
        var a = angle
        while (a > PI.toFloat()) a -= twoPi
        while (a < -PI.toFloat()) a += twoPi
        return a
    }
}
