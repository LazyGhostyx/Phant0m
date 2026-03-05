package xyz.lazyghosty.phant0m.manager.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import frb.phant0m.api.core.Phant0mSettings
import xyz.lazyghosty.phant0m.ui.theme.basePrimaryDefault
import xyz.lazyghosty.phant0m.ui.theme.toHexString
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
//    private val prefs = Phant0mSettings.getPreferences()

    val themeOptions = listOf("Follow System", "Dark Theme", "Light Theme")

    var isIgniteWhenRelogEnabled by mutableStateOf(
        Phant0mSettings.getEnableIgniteRelog()
    )
        private set

    var isActivateOnBootEnabled by mutableStateOf(
        Phant0mSettings.getStartOnBoot()
    )
        private set

    var isTcpModeEnabled by mutableStateOf(
        Phant0mSettings.getTcpMode()
    )
        private set

    var tcpPortInt: Int? by mutableStateOf(
        Phant0mSettings.getTcpPort()
    )
        private set

    var isDynamicColorEnabled by mutableStateOf(
        Phant0mSettings.getEnableDynamicColor()
    )
        private set

    var getAppThemeId by mutableIntStateOf(
        Phant0mSettings.getAppThemeId()
    )
        private set

    var isDeveloperModeEnabled by mutableStateOf(
        Phant0mSettings.getEnableDeveloperOptions()
    )
        private set

    var isWebDebuggingEnabled by mutableStateOf(
        Phant0mSettings.getEnableWebDebugging()
    )
        private set

    // fungsi toggle / set manual

    fun setIgniteWhenRelog(enabled: Boolean) {
        viewModelScope.launch {
            isIgniteWhenRelogEnabled = enabled
            Phant0mSettings.setEnableIgniteRelog(enabled)
        }
    }

    fun setActivateOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            isActivateOnBootEnabled = enabled
            Phant0mSettings.setStartOnBoot(enabled)
        }
    }

    fun setTcpMode(enabled: Boolean) {
        viewModelScope.launch {
            isTcpModeEnabled = enabled
            Phant0mSettings.setTcpMode(enabled)
        }
    }

    fun setTcpPort(port: Int?) {
        viewModelScope.launch {
            tcpPortInt = port
            Phant0mSettings.setTcpPort(port)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            isDynamicColorEnabled = enabled
            Phant0mSettings.setEnableDynamicColor(enabled)
        }
    }

    fun setAppTheme(themeId: Int) {
        viewModelScope.launch {
            getAppThemeId = themeId
            Phant0mSettings.setAppThemeId(themeId)
        }
    }

    fun setDeveloperOptions(enabled: Boolean) {
        viewModelScope.launch {
            isDeveloperModeEnabled = enabled
            Phant0mSettings.setEnableDeveloperOptions(enabled)
        }
    }

    fun setWebDebugging(enabled: Boolean) {
        viewModelScope.launch {
            isWebDebuggingEnabled = enabled
            Phant0mSettings.setEnableWebDebugging(enabled)
        }
    }

    var customPrimaryColorHex by mutableStateOf(
        Phant0mSettings.getCustomPrimaryColor() ?: basePrimaryDefault.toHexString()
    )
        private set

    fun setCustomPrimaryColor(hex: String) {
        viewModelScope.launch {
            customPrimaryColorHex = hex
            Phant0mSettings.setPrimaryColor(hex)
        }
    }

    fun removeCustomPrimaryColor() {
        viewModelScope.launch {
            customPrimaryColorHex = basePrimaryDefault.toHexString()
            Phant0mSettings.removePrimaryColor()
        }
    }

}