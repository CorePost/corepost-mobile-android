package com.example.corepostemergencybutton

import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Log

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var statusText: TextView
    private lateinit var panicButton: Button
    private var timer: CountDownTimer? = null
    private lateinit var client: OkHttpClient

    // Кэшированные значения из /mobile/check
    private var emergencyState: Boolean = false
    private var needLockApproval: Boolean = false
    private var lockApprovalTimeSecond: Int = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("CorePostPrefs", MODE_PRIVATE)
        statusText = findViewById(R.id.statusText)
        panicButton = findViewById(R.id.panicButton)
        client = OkHttpClient()

        // Если настройки не заданы, переходим в экран настроек
        if (!prefs.contains("ip") || !prefs.contains("emergencyId")) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

        panicButton.setOnClickListener {
            if (!emergencyState) {
                // Если компьютер не заблокирован – посылаем запрос на блокировку
                sendLockRequest()
            } else {
                // Если уже заблокирован – посылаем запрос на разблокировку
                sendUnlockRequest()
            }
        }

        // Проверяем статус при запуске
        checkStatus()
    }

    private fun getBaseUrl(): String {
        val ip = prefs.getString("ip", "") ?: ""
        val port = prefs.getString("port", "8000") ?: "8000"
        return "http://$ip:$port"
    }

    private fun generateHeaders(emergencyId: String): Map<String, String> {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val signature = hmacSHA256(emergencyId, timestamp)
        return mapOf(
            "X-EmergencyId" to emergencyId,
            "X-Timestamp" to timestamp,
            "X-Signature" to signature
        )
    }

    private fun hmacSHA256(key: String, data: String): String {
        return try {
            val secretKeySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(secretKeySpec)
            val hashBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    private fun checkStatus() {
        val emergencyId = prefs.getString("emergencyId", "") ?: ""
        val url = "${getBaseUrl()}/mobile/check"
        val headers = generateHeaders(emergencyId)

        Log.d("CorePost", "Check URL: $url, headers: $headers")

        val request = Request.Builder()
            .url(url)
            .get()
            .apply {
                headers.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CorePost", "Check request failed", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Ошибка подключения к серверу: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("CorePost", "Check response (${response.code}): $responseBody")
                response.use {
                    if (!it.isSuccessful) {
                        runOnUiThread {
                            if (it.code == 401) {
                                Toast.makeText(this@MainActivity, "Неверный emergencyId", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this@MainActivity, "Ошибка: ${it.code}", Toast.LENGTH_LONG).show()
                            }
                        }
                        return
                    }
                    val json = responseBody
                    try {
                        val obj = JSONObject(json!!)
                        emergencyState = obj.getBoolean("emergencyState")
                        needLockApproval = obj.getBoolean("needLockApproval")
                        lockApprovalTimeSecond = obj.getInt("lockApprovalTimeSecond")

                        prefs.edit().apply {
                            putBoolean("emergencyState", emergencyState)
                            putBoolean("needLockApproval", needLockApproval)
                            putInt("lockApprovalTimeSecond", lockApprovalTimeSecond)
                            apply()
                        }
                        runOnUiThread { updateUI() }
                    } catch (e: Exception) {
                        Log.e("CorePost", "Check JSON parsing error", e)
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Ошибка парсинга ответа сервера", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        })
    }

    private fun sendLockRequest() {
        val emergencyId = prefs.getString("emergencyId", "") ?: ""
        val url = "${getBaseUrl()}/mobile/lock"
        val headers = generateHeaders(emergencyId)

        Log.d("CorePost", "Lock URL: $url, headers: $headers")

        val request = Request.Builder()
            .url(url)
            .post(RequestBody.create(null, ""))
            .apply {
                headers.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CorePost", "Lock request failed", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Ошибка подключения к серверу: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("CorePost", "Lock response (${response.code}): $responseBody")
                runOnUiThread {
                    when (response.code) {
                        200 -> {
                            emergencyState = true
                            prefs.edit().putBoolean("emergencyState", true).apply()
                            updateUI()
                            Toast.makeText(this@MainActivity, "Устройство заблокировано", Toast.LENGTH_SHORT).show()
                        }
                        201 -> {
                            startLockConfirmationTimer()
                            Toast.makeText(this@MainActivity, "Подтвердите блокировку - нажмите кнопку снова", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            Toast.makeText(this@MainActivity, "Ошибка: ${response.code}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        })
    }

    private fun sendUnlockRequest() {
        val emergencyId = prefs.getString("emergencyId", "") ?: ""
        val url = "${getBaseUrl()}/mobile/unlock"
        val headers = generateHeaders(emergencyId)

        Log.d("CorePost", "Unlock URL: $url, headers: $headers")

        val request = Request.Builder()
            .url(url)
            .post(RequestBody.create(null, ""))
            .apply {
                headers.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CorePost", "Unlock request failed", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Ошибка подключения к серверу: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("CorePost", "Unlock response (${response.code}): $responseBody")
                runOnUiThread {
                    when (response.code) {
                        200 -> {
                            emergencyState = false
                            prefs.edit().putBoolean("emergencyState", false).apply()
                            updateUI()
                            Toast.makeText(this@MainActivity, "Устройство разблокировано", Toast.LENGTH_SHORT).show()
                        }
                        403 -> {
                            Toast.makeText(this@MainActivity, "Вам не разрешено разблокировать компьютер. Обратитесь к администратору", Toast.LENGTH_LONG).show()
                        }
                        else -> {
                            Toast.makeText(this@MainActivity, "Ошибка: ${response.code}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        })
    }


    private fun startLockConfirmationTimer() {
        timer?.cancel()
        timer = object : CountDownTimer((lockApprovalTimeSecond * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                statusText.text = "Подтвердите блокировку - нажмите на кнопку снова (${millisUntilFinished / 1000})"
            }
            override fun onFinish() {
                updateUI() // По истечении времени возвращаем исходное состояние
            }
        }
        timer?.start()
    }

    private fun updateUI() {
        timer?.cancel()
        if (emergencyState) {
            statusText.text = "Компьютер помечен как украденный"
            panicButton.setBackgroundResource(R.drawable.button_pressed)
        } else {
            statusText.text = "Компьютер в порядке"
            panicButton.setBackgroundResource(R.drawable.button_normal)
        }
    }
}
