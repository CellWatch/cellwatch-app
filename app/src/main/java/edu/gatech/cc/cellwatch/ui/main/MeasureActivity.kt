package edu.gatech.cc.cellwatch.ui.main

import android.app.AlertDialog
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.databinding.ActivityMeasureBinding
import kotlinx.coroutines.launch

class MeasureActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMeasureBinding
    private lateinit var model: MeasureViewModel
    private var backPressedCallback: OnBackPressedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMeasureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.navDrawer.setOnCloseListener { binding.root.closeDrawer(GravityCompat.START) }
        binding.navDrawer.setActiveActivity(this)
        binding.toolbar.setDrawerLayout(binding.root)

        model = ViewModelProvider(this)[MeasureViewModel::class.java]
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { handleMeasureStateUpdate(it.progress) }
            }
        }
    }

    private fun handleMeasureStateUpdate(progress: MeasureViewModel.MeasureProgress) {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

        when (progress) {
            MeasureViewModel.MeasureProgress.PRE -> if (currentFragment !is PreMeasureFragment) {
                supportFragmentManager.commit { replace(R.id.fragment_container, PreMeasureFragment()) }
                binding.toolbar.isVisible = true
                backPressedCallback?.remove()
            }
            MeasureViewModel.MeasureProgress.NOT_CELLULAR -> {
                AlertDialog.Builder(this)
                    .setMessage(R.string.not_cellular_warning)
                    .setPositiveButton(R.string.not_cellular_proceed) { dialog, _ ->
                        dialog.dismiss()
                        model.startMeasurement()
                    }
                    .setNegativeButton(R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                        model.cancel()
                    }
                    .show()
            }
            else -> if (currentFragment !is MeasureFragment){
                backPressedCallback = onBackPressedDispatcher.addCallback { /* do nothing! */ }
                supportFragmentManager.commit { replace(R.id.fragment_container, MeasureFragment()) }
                binding.toolbar.isVisible = false
            }
        }
    }
}