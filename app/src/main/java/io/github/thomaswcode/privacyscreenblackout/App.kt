package io.github.thomaswcode.privacyscreenblackout

import android.app.Application
import io.github.thomaswcode.privacyscreenblackout.data.LayoutRepository
import io.github.thomaswcode.privacyscreenblackout.data.SettingsRepository
import io.github.thomaswcode.privacyscreenblackout.service.BlackoutController
import kotlinx.coroutines.MainScope

class App : Application() {

    val scope = MainScope()
    lateinit var layouts: LayoutRepository
        private set
    lateinit var settings: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        layouts = LayoutRepository(this, scope)
        settings = SettingsRepository(this, scope)
        BlackoutController.init(this)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
