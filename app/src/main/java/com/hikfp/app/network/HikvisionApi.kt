package com.hikfp.app.network

import com.hikfp.app.models.*

class HikvisionApi(private val conn: DeviceConnection) {

    // ── Device ────────────────────────────────────────────────
    suspend fun getDeviceInfo()    = NetworkManager.call(conn, "GET",  "/ISAPI/System/deviceInfo")
    suspend fun reboot()           = NetworkManager.call(conn, "PUT",  "/ISAPI/System/reboot", "<reboot/>")
    suspend fun getNetworkConfig() = NetworkManager.call(conn, "GET",  "/ISAPI/System/Network/interfaces/1")

    suspend fun updateDeviceInfo(name: String, location: String, tz: String) =
        NetworkManager.call(conn, "PUT", "/ISAPI/System/deviceInfo",
            "<DeviceInfo><deviceName>$name</deviceName><deviceLocation>$location</deviceLocation><timeZone>$tz</timeZone></DeviceInfo>")

    suspend fun setStaticIp(ip: String, mask: String, gw: String, dns: String) =
        NetworkManager.call(conn, "PUT", "/ISAPI/System/Network/interfaces/1",
            "<NetworkInterface><id>1</id><IPAddress><ipVersion>v4</ipVersion><addressingType>static</addressingType>" +
            "<ipAddress>$ip</ipAddress><subnetMask>$mask</subnetMask>" +
            "<DefaultGateway><ipAddress>$gw</ipAddress></DefaultGateway>" +
            "<PrimaryDNS><ipAddress>$dns</ipAddress></PrimaryDNS></IPAddress></NetworkInterface>")

    suspend fun setDhcp() =
        NetworkManager.call(conn, "PUT", "/ISAPI/System/Network/interfaces/1",
            "<NetworkInterface><id>1</id><IPAddress><ipVersion>v4</ipVersion><addressingType>dynamic</addressingType></IPAddress></NetworkInterface>")

    // ── Users ─────────────────────────────────────────────────
    suspend fun searchUsers(max: Int = 50, offset: Int = 0) =
        NetworkManager.call(conn, "POST", "/ISAPI/AccessControl/UserInfo/Search",
            "<UserInfoSearchCond><searchID>1</searchID><maxResults>$max</maxResults><searchResultPosition>$offset</searchResultPosition></UserInfoSearchCond>")

    suspend fun addUser(user: FpUser) =
        NetworkManager.call(conn, "POST", "/ISAPI/AccessControl/UserInfo/Record",
            buildAddUserXml(user))

    suspend fun modifyUser(user: FpUser) =
        NetworkManager.call(conn, "PUT", "/ISAPI/AccessControl/UserInfo/Modify",
            buildAddUserXml(user))

    suspend fun deleteUser(empNo: String) =
        NetworkManager.call(conn, "PUT", "/ISAPI/AccessControl/UserInfo/Delete",
            "<UserInfoDelCond><EmployeeNoList><employeeNo>$empNo</employeeNo></EmployeeNoList></UserInfoDelCond>")

    suspend fun clearAllUsers() =
        NetworkManager.call(conn, "PUT", "/ISAPI/AccessControl/ClearCards",
            "<ClearCardsCfg><mode>all</mode></ClearCardsCfg>")

    // ── Fingerprints ──────────────────────────────────────────
    suspend fun searchFingerprints() =
        NetworkManager.call(conn, "POST", "/ISAPI/AccessControl/FingerPrint/Search",
            "<FingerPrintSearchCond><searchID>1</searchID><maxResults>50</maxResults><searchResultPosition>0</searchResultPosition></FingerPrintSearchCond>")

    suspend fun uploadFingerprint(empNo: String, fingerIndex: Int, fingerType: String, data: String) =
        NetworkManager.call(conn, "PUT", "/ISAPI/AccessControl/FingerPrint/Record",
            "<FingerPrintCfg><employeeNo>$empNo</employeeNo><FingerPrintList><FingerPrint>" +
            "<fingerPrintID>${fingerIndex + 1}</fingerPrintID><fingerType>$fingerType</fingerType>" +
            "<fingerData>$data</fingerData></FingerPrint></FingerPrintList></FingerPrintCfg>")

    suspend fun deleteFingerprint(empNo: String, fingerIndex: Int) =
        NetworkManager.call(conn, "PUT", "/ISAPI/AccessControl/FingerPrint/Delete",
            "<FingerPrintDeleteCond><FingerPrintList><FingerPrint>" +
            "<employeeNo>$empNo</employeeNo><fingerPrintID>${fingerIndex + 1}</fingerPrintID>" +
            "</FingerPrint></FingerPrintList></FingerPrintDeleteCond>")

    // ── FP Reader Config ──────────────────────────────────────
    suspend fun getFpConfig() =
        NetworkManager.call(conn, "GET", "/ISAPI/AccessControl/CardReaderCfg/0")

    suspend fun setFpConfig(cfg: FpConfig) =
        NetworkManager.call(conn, "PUT", "/ISAPI/AccessControl/CardReaderCfg/0",
            "<CardReaderCfg><cardReaderNo>1</cardReaderNo>" +
            "<verifyMode>${cfg.authMode}</verifyMode>" +
            "<fingerPrintThreshold>${cfg.threshold}</fingerPrintThreshold>" +
            "<maxRetryTimes>${cfg.maxRetries}</maxRetryTimes>" +
            "<lockDuration>${cfg.lockDuration}</lockDuration>" +
            "<enableLiveFingerDetection>${cfg.lfd}</enableLiveFingerDetection>" +
            "<enable1NIdentification>${cfg.ident1N}</enable1NIdentification>" +
            "<enableDuress>${cfg.duress}</enableDuress>" +
            "<enableWiegand>${cfg.wiegand}</enableWiegand></CardReaderCfg>")

    // ── Door ──────────────────────────────────────────────────
    suspend fun getDoorConfig() =
        NetworkManager.call(conn, "GET", "/ISAPI/AccessControl/Door/Cfg/0")

    suspend fun getDoorStatus() =
        NetworkManager.call(conn, "GET", "/ISAPI/AccessControl/Door/Status/0")

    suspend fun remoteOpen() =
        NetworkManager.call(conn, "PUT", "/ISAPI/AccessControl/Door/RemoteOpen/0",
            "<RemoteControlDoor><doorNo>1</doorNo></RemoteControlDoor>")

    suspend fun remoteLock() =
        NetworkManager.call(conn, "PUT", "/ISAPI/AccessControl/Door/Lock/0",
            "<RemoteControlDoor><doorNo>1</doorNo></RemoteControlDoor>")

    suspend fun setDoorConfig(openDuration: Int, antiPassback: Boolean,
                               dotlAlarm: Boolean, forcedAlarm: Boolean, duressAlarm: Boolean) =
        NetworkManager.call(conn, "PUT", "/ISAPI/AccessControl/Door/Cfg/0",
            "<DoorCfg><doorNo>1</doorNo>" +
            "<openDuration>$openDuration</openDuration>" +
            "<antiPassbackEnabled>$antiPassback</antiPassbackEnabled>" +
            "<enableDoorOpenTooLongAlarm>$dotlAlarm</enableDoorOpenTooLongAlarm>" +
            "<enableForcedAlarm>$forcedAlarm</enableForcedAlarm>" +
            "<enableDuressAlarm>$duressAlarm</enableDuressAlarm></DoorCfg>")

    // ── Schedule ──────────────────────────────────────────────
    suspend fun setSchedule(empNo: String, days: List<String>, start: String, end: String): ApiResult {
        val dayXml = days.joinToString("") {
            "<WeekPlanDay><dayofWeek>$it</dayofWeek><TimeRange><beginTime>${start}:00</beginTime><endTime>${end}:00</endTime></TimeRange></WeekPlanDay>"
        }
        return NetworkManager.call(conn, "PUT", "/ISAPI/AccessControl/UserRightWeekPlan/0",
            "<UserRightWeekPlan><employeeNo>$empNo</employeeNo><WeekPlanCfg><enable>true</enable><WeekPlanDays>$dayXml</WeekPlanDays></WeekPlanCfg></UserRightWeekPlan>")
    }

    // ── Events ────────────────────────────────────────────────
    suspend fun searchEvents(startTime: String, endTime: String, max: Int = 100) =
        NetworkManager.call(conn, "POST", "/ISAPI/AccessControl/AcsEvent/Search",
            "<AcsEventCond><searchID>1</searchID><maxResults>$max</maxResults><searchResultPosition>0</searchResultPosition>" +
            "<major>0</major><minor>0</minor>" +
            "<startTime>$startTime</startTime><endTime>$endTime</endTime></AcsEventCond>")

    // ── Helpers ───────────────────────────────────────────────
    private fun buildAddUserXml(user: FpUser) =
        "<UserInfo><employeeNo>${user.employeeNo}</employeeNo>" +
        "<n>${user.name}</n><userType>${user.userType}</userType>" +
        (if (user.cardNo.isNotBlank()) "<cardNo>${user.cardNo}</cardNo>" else "") +
        "<Valid><enable>true</enable>" +
        "<beginTime>${user.validFrom.ifBlank{"2025-01-01"}}T00:00:00</beginTime>" +
        "<endTime>${user.validTo.ifBlank{"2026-12-31"}}T23:59:59</endTime></Valid>" +
        "<doorRight>1</doorRight>" +
        "<RightPlan><doorNo>1</doorNo><planTemplateNo>1</planTemplateNo></RightPlan></UserInfo>"

    fun parseUsers(xml: String): List<FpUser> {
        val users = mutableListOf<FpUser>()
        xml.split("<UserInfo>").drop(1).forEach { block ->
            val emp  = block.substringAfter("<employeeNo>").substringBefore("</employeeNo>", "")
            val name = block.substringAfter("<n>").substringBefore("</n>", "")
            val card = block.substringAfter("<cardNo>").substringBefore("</cardNo>", "")
            if (emp.isNotBlank()) users.add(FpUser(employeeNo = emp, name = name, cardNo = card))
        }
        return users
    }

    fun parseEvents(xml: String): List<AccessEvent> {
        val events = mutableListOf<AccessEvent>()
        xml.split("<AcsEvent>").drop(1).forEach { block ->
            val emp    = block.substringAfter("<employeeNoString>").substringBefore("</employeeNoString>", "")
            val name   = block.substringAfter("<n>").substringBefore("</n>", "")
            val time   = block.substringAfter("<time>").substringBefore("</time>", "")
            val evType = block.substringAfter("<eventType>").substringBefore("</eventType>", "")
            val result = if (evType.contains("fail", ignoreCase = true)) "fail" else "pass"
            events.add(AccessEvent(employeeNo = emp, name = name, time = time, result = result))
        }
        return events
    }

    fun isSuccess(xml: String) =
        xml.contains("<statusCode>1</statusCode>") || xml.contains("<responseStatusStrg>OK</responseStatusStrg>")
}
