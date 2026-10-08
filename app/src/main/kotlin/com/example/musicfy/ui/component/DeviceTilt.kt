// DeviceTilt.kt

package com.example.musicfy.ui.component

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

// how far the card leans per degree the phone is tipped, and the most it ever leans
private const val Follow = 0.9f

// each reading pulls the resting position this much towards how the phone is held now, so the card
// answers a movement and then settles back level instead of staying leant while you sit still
private const val Recentre = 0.012f

// each reading moves the lean this much of the way to where it's heading: smooth, never laggy
private const val Smooth = 0.22f

/**
 * How far the phone has been tipped from the way it's being held, as a lean for a card to follow
 * (degrees, ready for graphicsLayer's rotationX / rotationY). Changes ~60 times a second, so read it
 * in a graphicsLayer or draw lambda only; nothing recomposes.
 */
@Stable
class DeviceTilt internal constructor() {
    /** for graphicsLayer.rotationX: the phone's top edge tipping towards or away from you */
    var rotationX by mutableFloatStateOf(0f)
        internal set

    /** for graphicsLayer.rotationY: the phone rolling left or right */
    var rotationY by mutableFloatStateOf(0f)
        internal set
}

/**
 * Listens to the game rotation vector (gyro and accelerometer, no compass, so it doesn't jump near
 * metal) while the screen is resumed, and only then. Phones without one get a card that just
 * doesn't lean: [DeviceTilt] stays at zero.
 */
@Composable
fun rememberDeviceTilt(maxDegrees: Float = 14f): DeviceTilt {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tilt = remember { DeviceTilt() }

    DisposableEffect(context, lifecycleOwner) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensors == null || sensor == null) return@DisposableEffect onDispose { }

        val matrix = FloatArray(9)
        val angles = FloatArray(3)
        var restPitch = Float.NaN
        var restRoll = Float.NaN

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, angles)
                val pitch = Math.toDegrees(angles[1].toDouble()).toFloat()
                val roll = Math.toDegrees(angles[2].toDouble()).toFloat()
                if (restPitch.isNaN()) {
                    restPitch = pitch
                    restRoll = roll
                }
                val dPitch = pitch - restPitch
                val dRoll = wrapDegrees(roll - restRoll)
                restPitch += dPitch * Recentre
                restRoll = wrapDegrees(restRoll + dRoll * Recentre)

                val targetX = (-dPitch * Follow).coerceIn(-maxDegrees, maxDegrees)
                val targetY = (dRoll * Follow).coerceIn(-maxDegrees, maxDegrees)
                tilt.rotationX += (targetX - tilt.rotationX) * Smooth
                tilt.rotationY += (targetY - tilt.rotationY) * Smooth
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        var listening = false
        fun listen(on: Boolean) {
            if (on == listening) return
            listening = on
            if (on) {
                // re-centre on however the phone is held when the screen comes back
                restPitch = Float.NaN
                sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            } else {
                sensors.unregisterListener(listener)
                tilt.rotationX = 0f
                tilt.rotationY = 0f
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> listen(true)
                Lifecycle.Event.ON_PAUSE -> listen(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) listen(true)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            listen(false)
        }
    }
    return tilt
}

private fun wrapDegrees(d: Float): Float {
    var x = d % 360f
    if (x > 180f) x -= 360f
    if (x < -180f) x += 360f
    return x
}
