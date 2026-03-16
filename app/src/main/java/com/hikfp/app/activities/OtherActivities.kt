package com.hikfp.app.activities

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.hikfp.app.R
import com.hikfp.app.models.*
import com.hikfp.app.network.CloudPush
import com.hikfp.app.network.HikvisionApi
import com.hikfp.app.utils.Prefs
import kotlinx.coroutines.launch

// ─── Base ─────────────────────────────────────────────────────────────────────
abstract class BaseActivity : AppCompatActivity() {
    fun api() = Prefs.loadConnection()?.let { HikvisionApi(it) }
    fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    fun showResult(tv: TextView, xml: String, success: Boolean) {
        tv.text = if (success) "✓ Success\n\n$xml".take(600) else "✗ Failed\n\n$xml".take(600)
        tv.setTextColor(if (success) 0xFF00E676.toInt() else 0xFFFF4444.toInt())
    }
    override fun onSupportNavigateUp(): Boolean { onBackPressed(); return true }
}

// ─── Users ────────────────────────────────────────────────────────────────────
class UsersActivity : BaseActivity() {
    private var users = listOf<FpUser>()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_users)
        supportActionBar?.title = "Fingerprint Users"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<Button>(R.id.btnFetchUsers).setOnClickListener { fetchUsers() }
        findViewById<Button>(R.id.btnClearAll).setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Clear All Users")
                .setMessage("Delete ALL enrolled users from device?")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch {
                        val r = api()?.clearAllUsers()
                        showToast(if (r?.success == true) "All users cleared" else "Failed")
                        if (r?.success == true) fetchUsers()
                    }
                }.setNegativeButton("Cancel", null).show()
        }
    }

    private fun fetchUsers() {
        lifecycleScope.launch {
            val r = api()?.searchUsers() ?: run { showToast("No device connected"); return@launch }
            if (r.success) {
                users = api()!!.parseUsers(r.xml)
                renderList()
            } else showToast("Failed: ${r.error}")
        }
    }

    private fun renderList() {
        val lv = findViewById<ListView>(R.id.listUsers)
        val tv = findViewById<TextView>(R.id.tvEmpty)
        if (users.isEmpty()) { tv.visibility = View.VISIBLE; lv.visibility = View.GONE; return }
        tv.visibility = View.GONE; lv.visibility = View.VISIBLE
        val items = users.map { "${it.employeeNo}  ${it.name}  [${it.userType}]  Card:${it.cardNo.ifBlank{"—"}}" }
        lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        lv.setOnItemLongClickListener { _, _, pos, _ ->
            val u = users[pos]
            android.app.AlertDialog.Builder(this)
                .setTitle("Delete ${u.name}?")
                .setPositiveButton("Delete") { _, _ ->
                    lifecycleScope.launch {
                        val r = api()?.deleteUser(u.employeeNo)
                        showToast(if (r?.success == true) "Deleted" else "Failed")
                        if (r?.success == true) fetchUsers()
                    }
                }.setNegativeButton("Cancel", null).show()
            true
        }
    }
}

// ─── Enrol ────────────────────────────────────────────────────────────────────
class EnrolActivity : BaseActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_enrol)
        supportActionBar?.title = "Enrol Fingerprint User"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val fingers = arrayOf("Right Thumb","Right Index","Right Middle","Right Ring","Right Little",
                              "Left Thumb","Left Index","Left Middle","Left Ring","Left Little")
        val spFinger = findViewById<Spinner>(R.id.spinnerFinger)
        spFinger.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fingers)

        val types = arrayOf("normal","visitor","blackList","superUser")
        val spType = findViewById<Spinner>(R.id.spinnerUserType)
        spType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)

        val tvResp = findViewById<TextView>(R.id.tvResponse)

        // Add user
        findViewById<Button>(R.id.btnAddUser).setOnClickListener {
            val emp  = findViewById<EditText>(R.id.editEmpNo).text.toString().trim()
            val name = findViewById<EditText>(R.id.editName).text.toString().trim()
            if (emp.isEmpty() || name.isEmpty()) { showToast("Fill employee no and name"); return@setOnClickListener }
            val user = FpUser(
                employeeNo = emp, name = name,
                userType   = types[spType.selectedItemPosition],
                cardNo     = findViewById<EditText>(R.id.editCard).text.toString().trim(),
                validFrom  = findViewById<EditText>(R.id.editFrom).text.toString().trim().ifBlank{"2025-01-01"},
                validTo    = findViewById<EditText>(R.id.editTo).text.toString().trim().ifBlank{"2026-12-31"}
            )
            lifecycleScope.launch {
                val r = api()?.addUser(user) ?: run { showToast("No device connected"); return@launch }
                showResult(tvResp, r.xml, r.success)
                showToast(if (r.success) "User added" else "Failed: ${r.error}")
            }
        }

        // Upload FP
        findViewById<Button>(R.id.btnUploadFp).setOnClickListener {
            val emp  = findViewById<EditText>(R.id.editFpEmp).text.toString().trim()
            val data = findViewById<EditText>(R.id.editFpData).text.toString().trim()
            if (emp.isEmpty() || data.isEmpty()) { showToast("Fill employee no and template data"); return@setOnClickListener }
            lifecycleScope.launch {
                val r = api()?.uploadFingerprint(emp, spFinger.selectedItemPosition, "normalUser", data)
                    ?: run { showToast("No device connected"); return@launch }
                showResult(tvResp, r.xml, r.success)
                showToast(if (r.success) "Fingerprint uploaded" else "Failed: ${r.error}")
            }
        }

        // Delete FP
        findViewById<Button>(R.id.btnDeleteFp).setOnClickListener {
            val emp = findViewById<EditText>(R.id.editFpEmp).text.toString().trim()
            if (emp.isEmpty()) { showToast("Enter employee no"); return@setOnClickListener }
            lifecycleScope.launch {
                val r = api()?.deleteFingerprint(emp, spFinger.selectedItemPosition)
                    ?: run { showToast("No device connected"); return@launch }
                showResult(tvResp, r.xml, r.success)
            }
        }
    }
}

// ─── FpConfig ─────────────────────────────────────────────────────────────────
class FpConfigActivity : BaseActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_fp_config)
        supportActionBar?.title = "Fingerprint Configuration"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val modes = arrayOf("fingerPrint","card","cardOrFingerPrint","cardAndFingerPrint",
                            "fingerPrintOrPwd","fingerPrintAndPwd","cardOrPwd","cardAndPwd")
        val spMode = findViewById<Spinner>(R.id.spinnerMode)
        spMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("FP only","Card only","Card OR FP","Card AND FP","FP OR PIN","FP AND PIN","Card OR PIN","Card AND PIN"))

        val tvResp = findViewById<TextView>(R.id.tvResponse)

        findViewById<Button>(R.id.btnReadCfg).setOnClickListener {
            lifecycleScope.launch {
                val r = api()?.getFpConfig() ?: run { showToast("No device connected"); return@launch }
                showResult(tvResp, r.xml, r.success)
            }
        }

        findViewById<Button>(R.id.btnSaveCfg).setOnClickListener {
            val cfg = FpConfig(
                authMode    = modes[spMode.selectedItemPosition],
                threshold   = findViewById<EditText>(R.id.editThreshold).text.toString().toIntOrNull() ?: 50,
                maxRetries  = findViewById<EditText>(R.id.editRetries).text.toString().toIntOrNull() ?: 3,
                lockDuration= findViewById<EditText>(R.id.editLockDur).text.toString().toIntOrNull() ?: 30,
                lfd         = findViewById<Switch>(R.id.switchLFD).isChecked,
                ident1N     = findViewById<Switch>(R.id.switch1N).isChecked,
                duress      = findViewById<Switch>(R.id.switchDuress).isChecked,
                wiegand     = findViewById<Switch>(R.id.switchWiegand).isChecked
            )
            lifecycleScope.launch {
                val r = api()?.setFpConfig(cfg) ?: run { showToast("No device connected"); return@launch }
                showResult(tvResp, r.xml, r.success)
                showToast(if (r.success) "FP config saved" else "Failed")
            }
        }
    }
}

// ─── DeviceConfig ─────────────────────────────────────────────────────────────
class DeviceConfigActivity : BaseActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_device_config)
        supportActionBar?.title = "Device Configuration"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val tvResp = findViewById<TextView>(R.id.tvResponse)
        val switchDHCP = findViewById<Switch>(R.id.switchDHCP)
        switchDHCP.setOnCheckedChangeListener { _, checked ->
            listOf(R.id.editNewIp, R.id.editSubnet, R.id.editGateway, R.id.editDns).forEach {
                findViewById<EditText>(it).isEnabled = !checked
                findViewById<EditText>(it).alpha = if (checked) 0.4f else 1f
            }
        }

        findViewById<Button>(R.id.btnReadDev).setOnClickListener {
            lifecycleScope.launch {
                val r = api()?.getDeviceInfo() ?: run { showToast("No device connected"); return@launch }
                showResult(tvResp, r.xml, r.success)
            }
        }
        findViewById<Button>(R.id.btnSaveDev).setOnClickListener {
            lifecycleScope.launch {
                val r = api()?.updateDeviceInfo(
                    name     = findViewById<EditText>(R.id.editDevName).text.toString(),
                    location = findViewById<EditText>(R.id.editDevLoc).text.toString(),
                    tz       = "GMT+0:00:00"
                ) ?: run { showToast("No device connected"); return@launch }
                showResult(tvResp, r.xml, r.success)
                showToast(if (r.success) "Device info updated" else "Failed")
            }
        }
        findViewById<Button>(R.id.btnApplyNet).setOnClickListener {
            lifecycleScope.launch {
                val r = if (switchDHCP.isChecked) api()?.setDhcp()
                        else api()?.setStaticIp(
                            ip   = findViewById<EditText>(R.id.editNewIp).text.toString(),
                            mask = findViewById<EditText>(R.id.editSubnet).text.toString(),
                            gw   = findViewById<EditText>(R.id.editGateway).text.toString(),
                            dns  = findViewById<EditText>(R.id.editDns).text.toString()
                        )
                r?.let { showResult(tvResp, it.xml, it.success) } ?: showToast("No device connected")
            }
        }
        findViewById<Button>(R.id.btnReboot).setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Reboot?").setMessage("Reboot device now?")
                .setPositiveButton("Reboot") { _, _ ->
                    lifecycleScope.launch {
                        api()?.reboot(); showToast("Reboot command sent")
                    }
                }.setNegativeButton("Cancel", null).show()
        }
    }
}

// ─── AccessRules ──────────────────────────────────────────────────────────────
class AccessRulesActivity : BaseActivity() {
    private val selectedDays = mutableSetOf("Mon","Tue","Wed","Thu","Fri")

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_access_rules)
        supportActionBar?.title = "Access Rules"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val tvResp = findViewById<TextView>(R.id.tvResponse)

        // Day chips
        val dayChipIds = mapOf("Mon" to R.id.chipMon,"Tue" to R.id.chipTue,"Wed" to R.id.chipWed,
            "Thu" to R.id.chipThu,"Fri" to R.id.chipFri,"Sat" to R.id.chipSat,"Sun" to R.id.chipSun)
        dayChipIds.forEach { (day, id) ->
            val chip = findViewById<Chip>(id)
            chip.isChecked = day in selectedDays
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedDays.add(day) else selectedDays.remove(day)
            }
        }

        findViewById<Button>(R.id.btnReadDoor).setOnClickListener {
            lifecycleScope.launch {
                val r = api()?.getDoorConfig() ?: run { showToast("No device connected"); return@launch }
                showResult(tvResp, r.xml, r.success)
            }
        }
        findViewById<Button>(R.id.btnSaveDoor).setOnClickListener {
            lifecycleScope.launch {
                val r = api()?.setDoorConfig(
                    openDuration = findViewById<EditText>(R.id.editOpenDur).text.toString().toIntOrNull() ?: 5,
                    antiPassback = findViewById<Switch>(R.id.switchAPB).isChecked,
                    dotlAlarm    = findViewById<Switch>(R.id.switchDOTL).isChecked,
                    forcedAlarm  = findViewById<Switch>(R.id.switchForced).isChecked,
                    duressAlarm  = findViewById<Switch>(R.id.switchDuressAlarm).isChecked
                ) ?: run { showToast("No device connected"); return@launch }
                showResult(tvResp, r.xml, r.success)
                showToast(if (r.success) "Door config saved" else "Failed")
            }
        }
        findViewById<Button>(R.id.btnApplySched).setOnClickListener {
            val emp   = findViewById<EditText>(R.id.editSchedEmp).text.toString().trim()
            val start = findViewById<EditText>(R.id.editStartTime).text.toString().trim().ifBlank{"08:00"}
            val end   = findViewById<EditText>(R.id.editEndTime).text.toString().trim().ifBlank{"18:00"}
            if (emp.isEmpty()) { showToast("Enter employee no"); return@setOnClickListener }
            lifecycleScope.launch {
                val r = api()?.setSchedule(emp, selectedDays.toList(), start, end)
                    ?: run { showToast("No device connected"); return@launch }
                showResult(tvResp, r.xml, r.success)
                showToast(if (r.success) "Schedule applied" else "Failed")
            }
        }
    }
}

// ─── Events ───────────────────────────────────────────────────────────────────
class EventsActivity : BaseActivity() {
    private var events = listOf<AccessEvent>()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_events)
        supportActionBar?.title = "Access Events"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Default dates
        val today    = java.time.LocalDate.now().toString()
        val lastWeek = java.time.LocalDate.now().minusDays(7).toString()
        findViewById<EditText>(R.id.editFrom).setText("${lastWeek}T00:00:00")
        findViewById<EditText>(R.id.editTo).setText("${today}T23:59:59")

        val tvEvents = findViewById<TextView>(R.id.tvEvents)
        val tvCount  = findViewById<TextView>(R.id.tvCount)

        findViewById<Button>(R.id.btnFetch).setOnClickListener {
            val from = findViewById<EditText>(R.id.editFrom).text.toString()
            val to   = findViewById<EditText>(R.id.editTo).text.toString()
            lifecycleScope.launch {
                val r = api()?.searchEvents(from, to) ?: run { showToast("No device connected"); return@launch }
                if (r.success) {
                    events = api()!!.parseEvents(r.xml)
                    tvCount.text = "${events.size} events"
                    tvEvents.text = if (events.isEmpty()) "No events found"
                    else events.joinToString("\n\n") { "⏱ ${it.time}\n👤 ${it.name} (${it.employeeNo})\n${if(it.result=="pass") "✓ Pass" else "✗ Fail"}" }
                } else { tvEvents.text = "Error: ${r.error}" }
            }
        }

        findViewById<Button>(R.id.btnPush).setOnClickListener {
            if (events.isEmpty()) { showToast("Fetch events first"); return@setOnClickListener }
            val cfg  = Prefs.loadCloud()
            val conn = Prefs.loadConnection() ?: run { showToast("No device connected"); return@setOnClickListener }
            lifecycleScope.launch {
                val results = CloudPush().push(conn, cfg, emptyList(), events)
                showToast(results.joinToString(", ") { "${it.target}:${if(it.success)"✓" else "✗"}" })
            }
        }
    }
}

// ─── DoorControl ──────────────────────────────────────────────────────────────
class DoorControlActivity : BaseActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_door_control)
        supportActionBar?.title = "Door Control"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        findViewById<Button>(R.id.btnUnlock).setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Confirm Unlock").setMessage("Unlock door remotely now?")
                .setPositiveButton("Unlock") { _, _ ->
                    lifecycleScope.launch {
                        val r = api()?.remoteOpen() ?: run { showToast("No device connected"); return@launch }
                        showResult(tvStatus, r.xml, r.success)
                        showToast(if (r.success) "✓ Door unlocked!" else "Failed: ${r.error}")
                    }
                }.setNegativeButton("Cancel", null).show()
        }
        findViewById<Button>(R.id.btnLock).setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Confirm Lock").setMessage("Lock door remotely now?")
                .setPositiveButton("Lock") { _, _ ->
                    lifecycleScope.launch {
                        val r = api()?.remoteLock() ?: run { showToast("No device connected"); return@launch }
                        showResult(tvStatus, r.xml, r.success)
                        showToast(if (r.success) "✓ Door locked" else "Failed: ${r.error}")
                    }
                }.setNegativeButton("Cancel", null).show()
        }
        findViewById<Button>(R.id.btnStatus).setOnClickListener {
            lifecycleScope.launch {
                val r = api()?.getDoorStatus() ?: run { showToast("No device connected"); return@launch }
                showResult(tvStatus, r.xml, r.success)
            }
        }
    }
}

// ─── CloudSettings ────────────────────────────────────────────────────────────
class CloudSettingsActivity : BaseActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_cloud_settings)
        supportActionBar?.title = "Cloud Push Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Load saved config
        val cfg = Prefs.loadCloud()
        findViewById<EditText>(R.id.editFbUrl).setText(cfg.firebaseUrl)
        findViewById<EditText>(R.id.editApiUrl).setText(cfg.customApiUrl)
        findViewById<EditText>(R.id.editApiKey).setText(cfg.customApiKey)
        findViewById<EditText>(R.id.editMqttBroker).setText(cfg.mqttBroker)
        findViewById<EditText>(R.id.editMqttTopic).setText(cfg.mqttTopic)
        findViewById<Switch>(R.id.switchPushUsers).isChecked   = cfg.pushUsers
        findViewById<Switch>(R.id.switchPushEvents).isChecked  = cfg.pushEvents
        findViewById<Switch>(R.id.switchAutoSync).isChecked    = cfg.autoSync

        val tvLog = findViewById<TextView>(R.id.tvPushLog)

        findViewById<Button>(R.id.btnSaveCloud).setOnClickListener {
            val newCfg = CloudConfig(
                firebaseUrl  = findViewById<EditText>(R.id.editFbUrl).text.toString().trim(),
                customApiUrl = findViewById<EditText>(R.id.editApiUrl).text.toString().trim(),
                customApiKey = findViewById<EditText>(R.id.editApiKey).text.toString().trim(),
                mqttBroker   = findViewById<EditText>(R.id.editMqttBroker).text.toString().trim(),
                mqttTopic    = findViewById<EditText>(R.id.editMqttTopic).text.toString().trim().ifBlank{"hikvision/fingerprints"},
                pushUsers    = findViewById<Switch>(R.id.switchPushUsers).isChecked,
                pushEvents   = findViewById<Switch>(R.id.switchPushEvents).isChecked,
                autoSync     = findViewById<Switch>(R.id.switchAutoSync).isChecked
            )
            Prefs.saveCloud(newCfg)
            showToast("Settings saved ✓")
        }

        findViewById<Button>(R.id.btnPushNow).setOnClickListener {
            val conn = Prefs.loadConnection() ?: run { showToast("No device connected"); return@setOnClickListener }
            val cloudCfg = Prefs.loadCloud()
            lifecycleScope.launch {
                tvLog.text = "Pushing..."
                val results = CloudPush().push(conn, cloudCfg, emptyList(), emptyList())
                tvLog.text = results.joinToString("\n") {
                    "${if(it.success)"✓" else "✗"} [${it.target}]  ${it.ref.ifBlank{it.error}}"
                }
            }
        }
    }
}

// ─── NetworkBridge ────────────────────────────────────────────────────────────
class NetworkBridgeActivity : BaseActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_network_bridge)
        supportActionBar?.title = "Cross-Network Bridge"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val code = """
// relay-server.js — deploy on any server reachable from both networks
// Install: npm install express node-fetch
// Run:     RELAY_API_KEY=yourkey node relay-server.js

const express = require('express');
const fetch   = require('node-fetch');
const app     = express();
const PORT    = process.env.PORT || 3000;
const API_KEY = process.env.RELAY_API_KEY || 'change-me';

app.use(express.json({ limit: '10mb' }));

app.use('/api/hik', (req, res, next) => {
    if (req.headers['x-api-key'] !== API_KEY)
        return res.status(401).json({ error: 'Unauthorized' });
    next();
});

app.post('/api/hik', async (req, res) => {
    const { ip, port, user, pass, method, endpoint, body } = req.body;
    const url  = "http://" + ip + ":" + (port||80) + endpoint;
    const auth = 'Basic ' + Buffer.from(user+':'+pass).toString('base64');
    try {
        const r = await fetch(url, {
            method: method || 'GET',
            headers: { 'Authorization': auth, 'Content-Type': 'application/xml' },
            body: (method !== 'GET' && body) ? body : undefined,
            timeout: 10000
        });
        res.status(r.status).send(await r.text());
    } catch(e) { res.status(500).json({ error: e.message }); }
});

app.listen(PORT, () => console.log('HikFP relay on :' + PORT));
        """.trimIndent()

        val tv = findViewById<TextView>(R.id.tvRelayCode)
        tv.text = code

        findViewById<Button>(R.id.btnCopyCode).setOnClickListener {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("relay", code))
            showToast("Relay server code copied!")
        }
    }
}
