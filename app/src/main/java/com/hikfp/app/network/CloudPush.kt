package com.hikfp.app.network

import com.google.gson.Gson
import com.hikfp.app.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class CloudPush {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class PushResult(val target: String, val success: Boolean, val ref: String = "", val error: String = "")

    suspend fun push(conn: DeviceConnection, cfg: CloudConfig,
                     users: List<FpUser>, events: List<AccessEvent>): List<PushResult> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<PushResult>()
            val payload = mapOf(
                "device_ip"    to conn.ip,
                "device_model" to conn.model,
                "network_mode" to conn.networkMode,
                "timestamp"    to System.currentTimeMillis(),
                "users"        to if (cfg.pushUsers) users else null,
                "events"       to if (cfg.pushEvents) events else null
            )
            val json = gson.toJson(payload)

            if (cfg.firebaseUrl.isNotBlank()) results.add(pushHttp(cfg.firebaseUrl, null, json, "Firebase"))
            if (cfg.customApiUrl.isNotBlank()) results.add(pushHttp(cfg.customApiUrl, cfg.customApiKey, json, "Custom API"))
            if (cfg.mqttBroker.isNotBlank()) results.add(pushMqtt(cfg, json))
            results
        }

    private fun pushHttp(url: String, apiKey: String?, json: String, name: String): PushResult {
        return try {
            val endpoint = if (name == "Firebase" && !url.endsWith(".json")) "$url.json" else url
            val req = Request.Builder().url(endpoint)
                .header("Content-Type", "application/json")
                .apply { if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey") }
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) PushResult(name, true, body.take(40))
            else PushResult(name, false, error = "HTTP ${resp.code}")
        } catch (e: Exception) { PushResult(name, false, error = e.message ?: "Error") }
    }

    private fun pushMqtt(cfg: CloudConfig, json: String): PushResult {
        return try {
            val id = "HikFP_${System.currentTimeMillis()}"
            val client = org.eclipse.paho.client.mqttv3.MqttClient(
                cfg.mqttBroker, id, org.eclipse.paho.client.mqttv3.persist.MemoryPersistence())
            val opts = org.eclipse.paho.client.mqttv3.MqttConnectOptions().apply {
                isCleanSession = true; connectionTimeout = 10
            }
            client.connect(opts)
            client.publish(cfg.mqttTopic, org.eclipse.paho.client.mqttv3.MqttMessage(json.toByteArray()).apply { qos = 1 })
            client.disconnect()
            PushResult("MQTT", true, "Published to ${cfg.mqttTopic}")
        } catch (e: Exception) { PushResult("MQTT", false, error = e.message ?: "MQTT error") }
    }
}
