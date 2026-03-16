package com.hikfp.app.models

data class DeviceConnection(
    val id: String = java.util.UUID.randomUUID().toString(),
    val label: String = "",
    val ip: String = "",
    val port: Int = 80,
    val username: String = "admin",
    val password: String = "",
    val model: String = "DS-K1T671MF",
    val networkMode: String = "LOCAL",
    val relayUrl: String = "",
    val relayApiKey: String = "",
    val useTls: Boolean = false
) {
    val scheme get() = if (useTls) "https" else "http"
    val deviceUrl get() = "$scheme://$ip:$port"
    val effectiveUrl get() = if (networkMode == "RELAY" && relayUrl.isNotBlank()) relayUrl else deviceUrl
}

data class FpUser(
    val employeeNo: String = "",
    val name: String = "",
    val userType: String = "normal",
    val cardNo: String = "",
    val validFrom: String = "",
    val validTo: String = "",
    val status: String = "active"
)

data class AccessEvent(
    val employeeNo: String = "",
    val name: String = "",
    val time: String = "",
    val authType: String = "fingerPrint",
    val result: String = "pass"
)

data class FpConfig(
    val authMode: String = "fingerPrint",
    val threshold: Int = 50,
    val maxRetries: Int = 3,
    val lockDuration: Int = 30,
    val lfd: Boolean = true,
    val ident1N: Boolean = true,
    val duress: Boolean = false,
    val wiegand: Boolean = true
)

data class CloudConfig(
    val firebaseUrl: String = "",
    val customApiUrl: String = "",
    val customApiKey: String = "",
    val mqttBroker: String = "",
    val mqttTopic: String = "hikvision/fingerprints",
    val pushUsers: Boolean = true,
    val pushEvents: Boolean = true,
    val autoSync: Boolean = false
)

data class ApiResult(
    val success: Boolean,
    val xml: String = "",
    val error: String = ""
)
