package com.guodong.android.jasmine.recipe

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.guodong.android.jasmine.Jasmine
import com.guodong.android.jasmine.core.listener.IJasmineCallback
import com.guodong.android.jasmine.recipe.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), IJasmineCallback {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val binding by lazy { ActivityMainBinding.inflate(LayoutInflater.from(this)) }

    private lateinit var jasmine: Jasmine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.start.setOnClickListener {
            binding.start.isEnabled = false
            jasmine = Jasmine.Builder()
                .apply {
                    binding.mqttPort.text?.toString()?.let {
                        port(it.safeToInt(1883))
                    }
                }
                .enableWebsocket(binding.websocketSwitch.isChecked)
                .apply {
                    if (binding.websocketSwitch.isChecked) {
                        binding.websocketPort.text?.toString()?.let {
                            websocketPort(it.safeToInt(8083))
                        }

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
                .start()
        }

        binding.stop.setOnClickListener {
            jasmine.stop()
        }

        binding.websocketSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.websocketPort.isEnabled = isChecked
            binding.websocketPath.isEnabled = isChecked
        }
    }

    override fun onStarted(jasmine: Jasmine) {
        lifecycleScope.launch {
            binding.start.isEnabled = false
            binding.stop.isEnabled = true
            binding.websocketSwitch.isEnabled = false
            binding.state.text = getString(R.string.running)
        }
    }

    override fun onStartFailure(jasmine: Jasmine, cause: Throwable) {
        Log.e(TAG, "onStartFailure", cause)
        lifecycleScope.launch {
            binding.start.isEnabled = true
        }
    }

    override fun onStopped(jasmine: Jasmine) {
        lifecycleScope.launch {
            binding.start.isEnabled = true
            binding.stop.isEnabled = false
            binding.websocketSwitch.isEnabled = true
            binding.state.text = getString(R.string.hello_jasmine)
        }
    }

    override fun onStopFailure(jasmine: Jasmine, cause: Throwable) {
        Log.e(TAG, "onStopFailure", cause)
        lifecycleScope.launch {
            binding.stop.isEnabled = true
        }
    }
}