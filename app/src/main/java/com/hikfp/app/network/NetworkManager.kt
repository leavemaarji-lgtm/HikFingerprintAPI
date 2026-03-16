package com.hikfp.app.network

import android.util.Base64
import com.hikfp.app.models.ApiResult
import com.hikfp.app.models.DeviceConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

object NetworkManager {

    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(c: Array<X509Certificate>, a: String) {}
        override fun checkServerTrusted(c: Array<X509Certificate>, a: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private val client: OkHttpClient by lazy {
        val sc = SSLContext.getInstance("SSL")
        sc.init(null, arrayOf(trustAll), java.security.SecureRandom())
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .sslSocketFactory(sc.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    suspend fun call(
        conn: DeviceConnection,
        method: String,
        endpoint: String,
        body: String? = null
    ): ApiResult = withContext(Dispatchers.IO) {
        return@withContext when (conn.networkMode) {
            "RELAY" -> callViaRelay(conn, method, endpoint, body)
            else    -> callDirect(conn, method, endpoint, body)
        }
    }

    private fun callDirect(conn: DeviceConnection, method: String, endpoint: String, body: String?): ApiResult {
        return try {
            val url  = "${conn.deviceUrl}$endpoint"
            val cred = Base64.encodeToString("${conn.username}:${conn.password}".toByteArray(), Base64.NO_WRAP)
            val req  = Request.Builder()
                .url(url)
                .header("Authorization", "Basic $cred")
                .header("Content-Type", "application/xml")
                .method(method, if (method == "GET") null else (body ?: "").toRequestBody("application/xml".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            val xml  = resp.body?.string() ?: ""
            ApiResult(resp.isSuccessful, xml)
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "Unknown error")
        }
    }

    private fun callViaRelay(conn: DeviceConnection, method: String, endpoint: String, body: String?): ApiResult {
        return try {
            val escaped = body?.replace("\"", "\\\"")?.replace("\n", "\\n") ?: ""
            val json = """{"ip":"${conn.ip}","port":"${conn.port}","user":"${conn.username}","pass":"${conn.password}","method":"$method","endpoint":"$endpoint","body":"$escaped"}"""
            val req = Request.Builder()
                .url(conn.relayUrl)
                .header("Content-Type", "application/json")
                .apply { if (conn.relayApiKey.isNotBlank()) header("X-API-Key", conn.relayApiKey) }
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            val xml  = resp.body?.string() ?: ""
            ApiResult(resp.isSuccessful, xml)
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "Relay error")
        }
    }
}
