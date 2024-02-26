package edu.gatech.cc.cellwatch.ui.onboarding.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import edu.gatech.cc.cellwatch.data.datastore.LocalDataStore
import edu.gatech.cc.cellwatch.ui.measurement.viewmodels.MeasurementViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class OnboardingViewModel(private val localDataStore: LocalDataStore) : ViewModel() {
    private var deviceId = MutableLiveData<String>()
    private val privacyAgreed = MutableLiveData<Boolean>()
    private val collectionMode = MutableLiveData<String>()

    fun getDeviceId(): LiveData<String> {
        viewModelScope.launch {
            deviceId.value = localDataStore.getDeviceId.first()
            if (deviceId.value == "") {
                val newDeviceId = UUID.randomUUID().toString()
                deviceId.postValue(newDeviceId)
                localDataStore.saveDeviceId(newDeviceId)
            }
        }
        return deviceId
    }

    fun getCollectionMode(): LiveData<String> {
        return collectionMode
    }

    fun getPrivacyAgreed(): LiveData<Boolean> {
        return privacyAgreed
    }

    /**
     * Generate a new deviceId on first launch of app
     */
    fun generateDeviceId(): String {
        val newDeviceId = UUID.randomUUID().toString()
        viewModelScope.launch {
            localDataStore.saveDeviceId(newDeviceId)
        }
        return newDeviceId
    }

    fun collectionMode(mode: String) {
        collectionMode.value = mode
    }

    fun agreeToPrivacy(agreed: Boolean) {
        privacyAgreed.value = agreed
    }
}

class OnboardingViewModelFactory(private val localDataStore: LocalDataStore) : ViewModelProvider.Factory {
    override fun <T: ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OnboardingViewModel(localDataStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}