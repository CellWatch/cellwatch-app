package edu.gatech.cc.cellwatch.ui.main

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.databinding.ActivityMeasureHistoryBinding
import kotlinx.coroutines.launch

class MeasureHistoryActivity : AppCompatActivity() {
    private val TAG = this::class.simpleName
    private lateinit var binding: ActivityMeasureHistoryBinding
    private lateinit var model: MeasureHistoryViewModel
    private lateinit var adapter: MeasurementAdapter
    private lateinit var createFile: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMeasureHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        model = ViewModelProvider(this)[MeasureHistoryViewModel::class.java]

        binding.navDrawer.setOnCloseListener { binding.root.closeDrawer(GravityCompat.START) }
        binding.navDrawer.setActiveActivity(this)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { binding.root.openDrawer(GravityCompat.START) }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = MeasurementAdapter()
        binding.recyclerView.adapter = adapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.groups.collect { adapter.setData(it) }
            }
        }
        model.loadGroups()

        createFile = registerForActivityResult(CreateExportFile()) { uri ->
            try {
                uri?.let { contentResolver.openFileDescriptor(it, "w") }?.use {
                    model.exportData(it.fileDescriptor)
                }
            } catch (e: Exception) {
                Log.e(TAG, "failed to write to file", e)
                Toast.makeText(this, R.string.export_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_measure_history, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.export -> {
                showExportDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showExportDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_confirmation, null)
        val messageView = dialogView.findViewById<TextView>(R.id.exportDialogMessage)
        messageView.movementMethod = LinkMovementMethod.getInstance()

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.export_button) { _, _ ->
                createFile.launch("cellwatch-measurements.json")
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }

    private class CreateExportFile : CreateDocument() {
        override fun createIntent(context: Context, input: String): Intent {
            return super.createIntent(context, input).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
        }
    }
}
