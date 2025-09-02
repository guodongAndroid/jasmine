package com.guodong.android.jasmine.recipe

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.guodong.android.jasmine.Jasmine
import com.guodong.android.jasmine.core.exception.IllegalJasmineStateException
import com.guodong.android.jasmine.core.listener.IClientListener
import com.guodong.android.jasmine.core.listener.IJasmineCallback
import com.guodong.android.jasmine.recipe.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity(), IJasmineCallback, IClientListener {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val binding by lazy { ActivityMainBinding.inflate(LayoutInflater.from(this)) }

    private lateinit var jasmine: Jasmine

    private var clientCount = AtomicInteger(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        copySSL()
        refreshClientCount()

        binding.start.setOnClickListener {
            binding.start.isEnabled = false
            jasmine = Jasmine.Builder()
                .apply {
                    port(binding.mqttPort.text.toString().safeToInt(1883))
                }.apply {
                    enableSSL(binding.mqttSslSwitch.isChecked)

                    if (binding.mqttSslSwitch.isChecked) {
                        sslPort(binding.mqttSslPort.text.toString().safeToInt(1884))
                        websocketSSLPort(binding.websocketSslPort.text.toString().safeToInt(8084))

                        enableTwoWayAuth(binding.mqttSslTwoWaySwitch.isChecked)
                        if (binding.mqttSslTwoWaySwitch.isChecked) {
                            caCertFile(File("$filesDir/ssl/ca.crt"))
                        }

                        serverCertFile(File("$filesDir/ssl/server.crt"))
                        privateKeyFile(File("$filesDir/ssl/server-pkcs8.key"))
                    }
                }.apply {
                    enableWebsocket(binding.websocketSwitch.isChecked)

                    if (binding.websocketSwitch.isChecked) {
                        websocketPort(binding.websocketPort.text.toString().safeToInt(8083))

                        binding.websocketPath.text?.toString()?.let {
                            if (!it.startsWith("/")) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Path 请以 / 开头",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@let
                            }

                            websocketPath(it)
                        }
                    }
                }
                .jasmineCallback(this)
                .clientListener(this)
                .start()
        }

        binding.stop.setOnClickListener {
            binding.stop.isEnabled = false
            jasmine.stop()
        }

        binding.mqttSslSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.websocketSslPort.isEnabled = binding.websocketSwitch.isChecked && isChecked
            binding.mqttSslTwoWaySwitch.isEnabled = isChecked
            binding.mqttSslPort.isEnabled = isChecked
        }

        binding.websocketSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.websocketPort.isEnabled = isChecked
            binding.websocketPath.isEnabled = isChecked
            binding.websocketSslPort.isEnabled = binding.mqttSslSwitch.isChecked && isChecked
        }
    }

    override fun onStarting(jasmine: Jasmine) {

    }

    override fun onStarted(jasmine: Jasmine) {
        lifecycleScope.launch {
            binding.start.isEnabled = false
            binding.stop.isEnabled = true

            binding.mqttPort.isEnabled = false
            binding.mqttSslSwitch.isEnabled = false
            binding.mqttSslTwoWaySwitch.isEnabled = false
            binding.mqttSslPort.isEnabled = false

            binding.websocketSwitch.isEnabled = false
            binding.websocketPort.isEnabled = false
            binding.websocketSslPort.isEnabled = false
            binding.websocketPath.isEnabled = false

            binding.state.text = getString(R.string.running)
        }
    }

    override fun onStartFailure(jasmine: Jasmine, cause: Throwable) {
        Log.e(TAG, "onStartFailure", cause)

        if (cause !is IllegalJasmineStateException) {
            lifecycleScope.launch {
                binding.start.isEnabled = true
            }
        }
    }

    override fun onStopping(jasmine: Jasmine) {

    }

    override fun onStopped(jasmine: Jasmine) {
        lifecycleScope.launch {
            binding.start.isEnabled = true
            binding.stop.isEnabled = false

            binding.mqttPort.isEnabled = true
            binding.mqttSslSwitch.isEnabled = true
            binding.mqttSslTwoWaySwitch.isEnabled = binding.mqttSslSwitch.isChecked
            binding.mqttSslPort.isEnabled = binding.mqttSslSwitch.isChecked

            binding.websocketSwitch.isEnabled = true
            binding.websocketPort.isEnabled = binding.websocketSwitch.isChecked
            binding.websocketSslPort.isEnabled =
                binding.mqttSslSwitch.isChecked && binding.websocketSwitch.isChecked
            binding.websocketPath.isEnabled = binding.websocketSwitch.isChecked

            binding.state.text = getString(R.string.hello_jasmine)
        }
    }

    override fun onStopFailure(jasmine: Jasmine, cause: Throwable) {
        Log.e(TAG, "onStopFailure", cause)

        if (cause !is IllegalJasmineStateException) {
            lifecycleScope.launch {
                binding.stop.isEnabled = true
            }
        }
    }

    override fun onClientOnline(clientId: String, username: String) {
        clientCount.incrementAndGet()
        refreshClientCount()
    }

    override fun onClientOffline(
        clientId: String,
        username: String,
        reason: Int,
        cause: Throwable?
    ) {
        clientCount.decrementAndGet()
        refreshClientCount()
    }

    private fun refreshClientCount() {
        runOnUiThread {
            binding.tvClientCount.text = getString(R.string.text_client_counts, clientCount.get())
        }
    }

    private fun copySSL() {
        lifecycleScope.launch {
            val ssl = withContext(Dispatchers.IO) { copyAssetSSL() }

            binding.mqttSslSwitch.isEnabled = ssl

            Toast.makeText(
                this@MainActivity,
                "Copy SSL ${if (ssl) "succeed" else "failed"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}