package com.hikfp.app.activities

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hikfp.app.R
import com.hikfp.app.models.DeviceConnection
import com.hikfp.app.network.HikvisionApi
import com.hikfp.app.utils.Prefs
import kotlinx.coroutines.launch

class ConnectActivity : AppCompatActivity() {

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_connect)
        supportActionBar?.title = "Connect Device"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val existing = Prefs.loadConnection()
        existing?.let {
            findViewById<EditText>(R.id.editIp).setText(it.ip)
            findViewById<EditText>(R.id.editPort).setText(it.port.toString())
            findViewById<EditText>(R.id.editUser).setText(it.username)
            findViewById<EditText>(R.id.editLabel).setText(it.label)
            // Set relay fields
            if (it.networkMode == "RELAY") {
                (findViewById<RadioGroup>(R.id.radioGroupMode))
                    .check(R.id.radioRelay)
                toggleRelayFields(true)
                findViewById<EditText>(R.id.editRelayUrl).setText(it.relayUrl)
                findViewById<EditText>(R.id.editRelayKey).setText(it.relayApiKey)
            }
        }

        // Model spinner
        val models = arrayOf(
            "DS-K1T671MF","DS-K1T341BMFW","DS-K1T671TMF",
            "DS-K1T804MF","DS-K1T321MFW","DS-K1T8003MF","DS-K1T680MF","Generic"
        )
        val spinner = findViewById<Spinner>(R.id.spinnerModel)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, models)
        existing?.let { conn -> spinner.setSelection(models.indexOf(conn.model).coerceAtLeast(0)) }

        // Network mode toggle
        findViewById<RadioGroup>(R.id.radioGroupMode).setOnCheckedChangeListener { _, id ->
            toggleRelayFields(id == R.id.radioRelay)
        }

        // TLS toggle → auto-set port
        findViewById<Switch>(R.id.switchTls).setOnCheckedChangeListener { _, checked ->
            if (checked) findViewById<EditText>(R.id.editPort).setText("443")
            else          findViewById<EditText>(R.id.editPort).setText("80")
        }

        // Connect button
        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            val ip    = findViewById<EditText>(R.id.editIp).text.toString().trim()
            val port  = findViewById<EditText>(R.id.editPort).text.toString().toIntOrNull() ?: 80
            val user  = findViewById<EditText>(R.id.editUser).text.toString().trim()
            val pass  = findViewById<EditText>(R.id.editPass).text.toString()
            val label = findViewById<EditText>(R.id.editLabel).text.toString().trim()
            val tls   = findViewById<Switch>(R.id.switchTls).isChecked
            val model = spinner.selectedItem.toString()
            val isRelay = findViewById<RadioButton>(R.id.radioRelay).isChecked
            val relayUrl = findViewById<EditText>(R.id.editRelayUrl).text.toString().trim()
            val relayKey = findViewById<EditText>(R.id.editRelayKey).text.toString().trim()

            if (ip.isEmpty()) { showToast("Enter device IP"); return@setOnClickListener }
            if (user.isEmpty()) { showToast("Enter username"); return@setOnClickListener }
            if (pass.isEmpty()) { showToast("Enter password"); return@setOnClickListener }
            if (isRelay && relayUrl.isEmpty()) { showToast("Enter relay URL"); return@setOnClickListener }

            val conn = DeviceConnection(
                ip = ip, port = port, username = user, password = pass,
                label = label.ifBlank { "$ip — $model" }, model = model,
                networkMode = if (isRelay) "RELAY" else "LOCAL",
                relayUrl = relayUrl, relayApiKey = relayKey, useTls = tls
            )

            val statusTv = findViewById<TextView>(R.id.tvStatus)
            statusTv.text = "Connecting..."

            lifecycleScope.launch {
                val api    = HikvisionApi(conn)
                val result = api.getDeviceInfo()
                if (result.success) {
                    Prefs.saveConnection(conn)
                    val devList = Prefs.loadDevices().toMutableList()
                    devList.removeAll { it.ip == conn.ip }
                    devList.add(0, conn)
                    Prefs.saveDevices(devList)
                    statusTv.text = "✓ Connected! Device info retrieved."
                    showToast("Connected successfully!")
                    finish()
                } else {
                    statusTv.text = "✗ Failed: ${result.error.ifBlank { result.xml.take(100) }}"
                }
            }
        }
    }

    private fun toggleRelayFields(show: Boolean) {
        val v = if (show) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutRelayFields).visibility = v
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    override fun onSupportNavigateUp(): Boolean { onBackPressed(); return true }
}
