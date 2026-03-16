package com.hikfp.app.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.hikfp.app.R
import com.hikfp.app.utils.Prefs

class MainActivity : AppCompatActivity() {

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        updateConnectionBar()

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home    -> showHome()
                R.id.nav_users   -> startActivity(Intent(this, UsersActivity::class.java))
                R.id.nav_config  -> startActivity(Intent(this, FpConfigActivity::class.java))
                R.id.nav_events  -> startActivity(Intent(this, EventsActivity::class.java))
                R.id.nav_door    -> startActivity(Intent(this, DoorControlActivity::class.java))
            }
            true
        }

        showHome()
    }

    private fun showHome() {
        // Quick-action buttons
        fun btn(id: Int, action: () -> Unit) =
            findViewById<android.widget.Button>(id)?.setOnClickListener { action() }

        btn(R.id.btnConnect)      { startActivity(Intent(this, ConnectActivity::class.java)) }
        btn(R.id.btnEnrol)        { startActivity(Intent(this, EnrolActivity::class.java)) }
        btn(R.id.btnFpConfig)     { startActivity(Intent(this, FpConfigActivity::class.java)) }
        btn(R.id.btnDevConfig)    { startActivity(Intent(this, DeviceConfigActivity::class.java)) }
        btn(R.id.btnAccessRules)  { startActivity(Intent(this, AccessRulesActivity::class.java)) }
        btn(R.id.btnEvents)       { startActivity(Intent(this, EventsActivity::class.java)) }
        btn(R.id.btnDoorControl)  { startActivity(Intent(this, DoorControlActivity::class.java)) }
        btn(R.id.btnCloud)        { startActivity(Intent(this, CloudSettingsActivity::class.java)) }
        btn(R.id.btnBridge)       { startActivity(Intent(this, NetworkBridgeActivity::class.java)) }
    }

    private fun updateConnectionBar() {
        val conn = Prefs.loadConnection()
        val tv = findViewById<TextView>(R.id.tvConnStatus)
        tv?.text = conn?.let { "Connected: ${it.label.ifBlank{it.ip}} · ${it.model} · ${it.networkMode}" }
            ?: "Not connected — tap Connect Device"
    }

    override fun onResume() {
        super.onResume()
        updateConnectionBar()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menuConnect -> { startActivity(Intent(this, ConnectActivity::class.java)); true }
            R.id.menuCloud   -> { startActivity(Intent(this, CloudSettingsActivity::class.java)); true }
            R.id.menuBridge  -> { startActivity(Intent(this, NetworkBridgeActivity::class.java)); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
