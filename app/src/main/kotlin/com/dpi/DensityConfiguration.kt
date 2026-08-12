// densityconfigurationkt
// this thing is for density configuration

package com.dpi

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import timber.log.Timber
import kotlin.math.roundToInt

// configuration class for adjusting screen density dynamically applies density
internal class DensityConfiguration(
    private val densityScale: Float
) : ActivityLifecycleManager() {

    private var originalDensityDpi: Int = 0

    // applies the density scaling to the application context this method should be
    @SuppressLint("LogNotTimber")
    fun applyDensityScaling(context: Context) {
        if (densityScale == 1.0f) return

        try {
            onCreate()
            val resources = context.resources
            val config = Configuration(resources.configuration)
            originalDensityDpi = config.densityDpi
            updateDensityDpi(config, resources)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply configuration", e)
        }
    }

    // updates the density dpi in the configuration and applies it to resources
    private fun updateDensityDpi(config: Configuration, resources: Resources) {
        val newDensityDpi = (originalDensityDpi * densityScale).roundToInt()
        config.densityDpi = newDensityDpi
        Timber.tag(TAG).i("Updated densityDpi to: $newDensityDpi")
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    // reapply density scaling when an activity is created
    override fun onActivityCreated(activity: Activity) {
        applyDensityToActivity(activity)
    }

    // reapply density scaling when an activity is resumed
    override fun onActivityResumed(activity: Activity) {
        applyDensityToActivity(activity)
    }

    // reapply density scaling when an activity is started
    override fun onActivityStarted(activity: Activity) {
        applyDensityToActivity(activity)
    }

    // applies the density configuration to a specific activity's resources
    private fun applyDensityToActivity(activity: Activity) {
        try {
            updateDensityDpi(activity.resources.configuration, activity.resources)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to update density for activity")
        }
    }

    companion object {
        private val TAG = DensityConfiguration::class.java.simpleName
    }
}
