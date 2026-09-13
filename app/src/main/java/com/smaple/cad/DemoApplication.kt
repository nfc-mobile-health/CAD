/**
 * Application class responsible for Hilt DI initialization across the app lifecycle.
 */
package com.smaple.cad
import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DemoApplication : Application()
