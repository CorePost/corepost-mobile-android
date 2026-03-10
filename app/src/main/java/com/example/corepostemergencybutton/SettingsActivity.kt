package com.example.corepostemergencybutton

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText

class SettingsActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var ipEdit: EditText
    private lateinit var portEdit: EditText
    private lateinit var emergencyIdEdit: EditText
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("CorePostPrefs", MODE_PRIVATE)
        ipEdit = findViewById(R.id.editIp)
        portEdit = findViewById(R.id.editPort)
        emergencyIdEdit = findViewById(R.id.editEmergencyId)
        saveButton = findViewById(R.id.buttonSave)

        ipEdit.setText(prefs.getString("ip", ""))
        portEdit.setText(prefs.getString("port", "8000"))
        emergencyIdEdit.setText(prefs.getString("emergencyId", ""))

        saveButton.setOnClickListener {
            val ip = ipEdit.text.toString().trim()
            val port = portEdit.text.toString().trim()
            val emergencyId = emergencyIdEdit.text.toString().trim()
            if (ip.isNotEmpty() && emergencyId.isNotEmpty()) {
                prefs.edit().apply {
                    putString("ip", ip)
                    putString("port", port)
                    putString("emergencyId", emergencyId)
                    apply()
                }
                finish()
            }
        }
    }
}
