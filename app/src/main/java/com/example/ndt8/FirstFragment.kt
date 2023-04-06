package com.example.ndt8

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.ndt8.databinding.FragmentFirstBinding
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.*
import kotlin.concurrent.thread


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {
    private val TAG = this::class.simpleName
    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textviewFirst.movementMethod = ScrollingMovementMethod()

        binding.buttonFirst.setOnClickListener {
            thread {
                toggleButton(false)

                try {
                    runTestSequence()
                } catch (e: Exception) {
                    Log.e(TAG, "unexpected error running test sequence", e)
                    writeMessage("unexpected error running test sequence: ${e.localizedMessage}")
                }

                toggleButton(true)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun runTestSequence() {
        var measurementId: String? = null
        writeMessage("RUNNING TEST SEQUENCE with measurement id $measurementId")

        val client = OkHttpClient.Builder().build()

        writeMessage("selecting server")

        // use local server for testing
//        measurementId = UUID.randomUUID().toString()
//        val server = Ndt8LocateServer("10.0.2.2", null, mapOf(
//            "ws:///ndt/v8/download" to "ws://10.0.2.2:8080/ndt/v8/download",
//            "ws:///ndt/v8/upload" to "ws://10.0.2.2:8080/ndt/v8/upload",
//        ))

        // use real M-Lab server
        val server = selectServer(client, "https://locate-dot-mlab-staging.appspot.com/v2/nearest/")

        writeMessage("selected server ${server.machine} in ${server.location}")
        runTest(client, server, measurementId, Ndt8TestDirection.DOWNLOAD)
        runTest(client, server, measurementId, Ndt8TestDirection.UPLOAD)
    }

    private fun runTest(
        client: OkHttpClient,
        server: Ndt8LocateServer,
        measurementId: String?,
        direction: Ndt8TestDirection
    ) {
        val dir = if (direction == Ndt8TestDirection.DOWNLOAD) "download" else "upload"
        writeMessage("running $dir test")
        val result = try {
            val test = Ndt8TestComponent(client, server, measurementId, direction)
            runBlocking {
                launch {
                    test.progress.consumeEach {
                        Log.d(TAG, "got progress $it")
                        writeMessage("progress: $it")
                    }
                }

                test.run()
            }
        } catch (t: Throwable) {
            Log.d(TAG, "$dir test failed", t)
            writeMessage("$dir test failed: ${t.localizedMessage}")
            return
        }

        Log.i(TAG, "$dir test complete: $result")
        writeMessage("$dir test complete: ${if (result.success) "success" else "failure"}; warmup ${result.warmupMetrics}; active ${result.activeMetrics}")
    }

    fun toggleButton(enabled: Boolean) {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            binding.buttonFirst.setText(if (enabled) R.string.measure else R.string.measuring)
            binding.buttonFirst.isEnabled = enabled
        }
    }

    fun writeMessage(m: String) {
        val handler = Handler(Looper.getMainLooper())
        handler.post { binding.textviewFirst.append("\n> $m") }
    }
}