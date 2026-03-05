package xyz.lazyghosty.phant0m.manager.data

import android.content.ContentResolver
import android.provider.Settings
import frb.phant0m.api.Phant0m
import frb.phant0m.api.Phant0mPluginService
import xyz.lazyghosty.phant0m.R
import frb.phant0m.server.Environment
import frb.phant0m.shared.Phant0mApiConstant.server.TYPE_NEW_ENV


private val SettingsRepository.SettingType.alias: String
    get() = when (this) {
        SettingsRepository.SettingType.GLOBAL -> "global"
        SettingsRepository.SettingType.SECURE -> "secure"
        SettingsRepository.SettingType.SYSTEM -> "system"
        SettingsRepository.SettingType.ANDROID_PROP -> "android_properties"
        SettingsRepository.SettingType.PHANT0M_ENV -> "phant0m_environment"
    }

class SettingsRepository(private val contentResolver: ContentResolver) {

    enum class SettingType(val stringId: Int) {
        GLOBAL(R.string.global_settings),
        SECURE(R.string.secure_settings),
        SYSTEM(R.string.system_settings),
        ANDROID_PROP(R.string.android_properties),
        PHANT0M_ENV(R.string.phant0m_environment);
    }


    /** Ambil satu nilai berdasarkan key */
    fun getValue(type: SettingType, key: String): String? {
        return when (type) {
            SettingType.GLOBAL -> Settings.Global.getString(contentResolver, key)
            SettingType.SECURE -> Settings.Secure.getString(contentResolver, key)
            SettingType.SYSTEM -> Settings.System.getString(contentResolver, key)
            SettingType.ANDROID_PROP -> getAndroidProp(key)
            SettingType.PHANT0M_ENV -> getPhant0mEnv(key)
        }
    }

    /** Simpan / ubah nilai setting */
    fun putValue(type: SettingType, key: String, value: String): Boolean {
        return when (type) {
            SettingType.GLOBAL,
            SettingType.SECURE,
            SettingType.SYSTEM -> putSetting(type, key, value)

            SettingType.ANDROID_PROP -> setAndroidProp(key, value)
            SettingType.PHANT0M_ENV -> setPhant0mEnv(key, value)
        }
    }

    /** Hapus nilai setting (atau kosongkan) */
    fun deleteValue(type: SettingType, key: String): Boolean {
        return when (type) {
            SettingType.GLOBAL,
            SettingType.SECURE,
            SettingType.SYSTEM -> deleteSetting(type, key)

            SettingType.ANDROID_PROP -> setAndroidProp(key, "")
            SettingType.PHANT0M_ENV -> unsetPhant0mEnv(key)
        }
    }

    /** Ambil semua key-value untuk tipe tertentu */
    fun getAll(type: SettingType): Map<String, String> {
        return when (type) {
            SettingType.GLOBAL,
            SettingType.SECURE,
            SettingType.SYSTEM -> querySettings(type)
            SettingType.ANDROID_PROP -> getAllAndroidProps()
            SettingType.PHANT0M_ENV -> getAllLinuxEnv()
        }
    }


    // ======= Android Settings (GLOBAL, SECURE, SYSTEM) =======

    fun putSetting(type: SettingType, key: String, value: String): Boolean {
        val alias = type.alias

        return try {
            Phant0m.newProcess(arrayOf("settings", "put", alias, key, value)).waitFor() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }


    fun deleteSetting(type: SettingType, key: String): Boolean {
        val alias = type.alias

        return try {
            Phant0m.newProcess(arrayOf("settings", "delete", alias, key)).waitFor() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }


    private fun querySettings(type: SettingType): Map<String, String> {
        val alias = type.alias
        val map = mutableMapOf<String, String>()

        try {
            val process = Phant0m.newProcess(arrayOf("settings", "list", alias))
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    // Format baris biasanya: name=value
                    val splitIndex = line.indexOf('=')
                    if (splitIndex != -1) {
                        val name = line.substring(0, splitIndex).trim()
                        val value = line.substring(splitIndex + 1).trim()
                        map[name] = value
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return map
    }


    // ======= AndroidProp =======
    private fun getAndroidProp(key: String): String? {
        return try {
            val process = Phant0m.newProcess(arrayOf(Phant0mPluginService.RESETPROP, key))
            process.inputStream.bufferedReader().use { it.readText().trim() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun setAndroidProp(key: String, value: String): Boolean {
        return try {
            Phant0m.newProcess(arrayOf(Phant0mPluginService.RESETPROP, key, value)).waitFor() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getAllAndroidProps(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val process = Phant0m.newProcess(Phant0mPluginService.RESETPROP)
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    // Format output: [key]: [value]
                    val match = Regex("\\[(.+?)]: \\[(.*?)]").find(line)
                    if (match != null) {
                        val (key, value) = match.destructured
                        map[key] = value
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    // ======= Linux Env =======

    private fun getPhant0mEnv(key: String): String? = Phant0m.getEnvironment().envMap[key]


    private val envMap: Map<String, String>
        get() = Phant0m.getEnvironment(TYPE_NEW_ENV).envMap

    private fun setPhant0mEnv(key: String, value: String): Boolean {
        val newEnvMap = HashMap(envMap) // mutable
        newEnvMap[key] = value
        return try {
            Phant0m.setNewEnvironment(Environment(newEnvMap, true))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun unsetPhant0mEnv(key: String): Boolean {
        val newEnvMap = HashMap(envMap) // mutable
        newEnvMap.remove(key)
        return try {
            Phant0m.setNewEnvironment(Environment(newEnvMap, true))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getAllLinuxEnv(): Map<String, String> {
        return Phant0m.getEnvironment().envMap
    }
}
