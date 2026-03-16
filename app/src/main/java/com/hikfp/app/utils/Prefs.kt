package com.hikfp.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hikfp.app.models.*

object Prefs {
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("hik_prefs", Context.MODE_PRIVATE)
    }

    fun saveDevices(list: List<DeviceConnection>) =
        prefs.edit().putString("devices", gson.toJson(list)).apply()

    fun loadDevices(): List<DeviceConnection> {
        val json = prefs.getString("devices", null) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<DeviceConnection>>() {}.type) }
        catch (e: Exception) { emptyList() }
    }

    fun saveCloud(cfg: CloudConfig) =
        prefs.edit().putString("cloud", gson.toJson(cfg)).apply()

    fun loadCloud(): CloudConfig {
        val json = prefs.getString("cloud", null) ?: return CloudConfig()
        return try { gson.fromJson(json, CloudConfig::class.java) } catch (e: Exception) { CloudConfig() }
    }

    fun saveConnection(conn: DeviceConnection) =
        prefs.edit().putString("active_conn", gson.toJson(conn)).apply()

    fun loadConnection(): DeviceConnection? {
        val json = prefs.getString("active_conn", null) ?: return null
        return try { gson.fromJson(json, DeviceConnection::class.java) } catch (e: Exception) { null }
    }
}
