package com.onelineaday.dailydiary.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {
    private var lastShakeTime: Long = 0
    private val shakeThreshold = 15f // Adjust sensitivity here
    private val shakeCooldown = 2000L // Min time between shakes (ms)

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        
        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH
        
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
        
        if (gForce > shakeThreshold / SensorManager.GRAVITY_EARTH) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTime > shakeCooldown) {
                lastShakeTime = now
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }
    
    fun register(context: Context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }
    
    fun unregister(context: Context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.unregisterListener(this)
    }
}
