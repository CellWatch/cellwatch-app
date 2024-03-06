package edu.gatech.cc.cellwatch.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.databinding.FragmentPostMeasureBinding
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

class PostMeasureFragment: Fragment() {
    private val TAG = this::class.simpleName
    private lateinit var binding: FragmentPostMeasureBinding
    private lateinit var model: MeasurementViewModel
    private val measurementRepository = CellWatchApp.measurementRepository
    private val fccSubmissionRepository = CellWatchApp.fccSubmissionRepository

    interface PostMeasureFragmentInteractionListener {
        fun onTakeAnotherMeasurementPressed()
        fun onBackToMapPressed()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentPostMeasureBinding.inflate(inflater, container, false)
        model = ViewModelProvider(requireActivity())[MeasurementViewModel::class.java]

        val interactionListener = if (context is PostMeasureFragmentInteractionListener) {
            context as PostMeasureFragmentInteractionListener
        } else {
            throw RuntimeException(context.toString() + " must implement PostMeasureFragmentInteractionListener")
        }

        val group = model.group ?: throw RuntimeException("PostMeasureFragment created without group in model")
        binding.item.setMeasurementGroup(group)

        binding.takeAnotherButton.setOnClickListener { interactionListener.onTakeAnotherMeasurementPressed() }
        binding.backToMapButton.setOnClickListener { interactionListener.onBackToMapPressed() }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.item.updateUploadTime(uploadMeasurements())
            } catch (t: Throwable) {
                Log.e(TAG, "failed to set uploaded text", t)
            }
        }

        return binding.root
    }

    private suspend fun uploadMeasurements(): Instant? {
        try {
            val uploadTime = measurementRepository.uploadMeasurements()
            fccSubmissionRepository.uploadFccSubmissions()
            return uploadTime
        } catch (e: Exception) {
            Log.d(TAG, "failed to upload measurements and submission", e)
            return null
        }
    }
}