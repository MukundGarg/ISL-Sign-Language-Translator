package com.example.signtranslator

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainApplication : Application() {

    lateinit var runAnywhereHelper: RunAnywhereHelper
        private set

    override fun onCreate() {
        super.onCreate()
        runAnywhereHelper = RunAnywhereHelper(this)
        CoroutineScope(Dispatchers.Main).launch {
            runAnywhereHelper.initialize()
        }
    }
}
