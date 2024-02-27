package edu.gatech.cc.cellwatch.ui.onboarding.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.gatech.cc.cellwatch.data.datastore.LocalDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

class OnboardingViewModel(private val localDataStore: LocalDataStore) : ViewModel() {
    private var deviceId = MutableLiveData<String>()
    private val fccPolicyAgreed = MutableLiveData<Boolean>()
    private val collectionMode = MutableLiveData<String>()
    private val userName = MutableLiveData<String>()
    private val phoneNumber = MutableLiveData<String>()
    private val email = MutableLiveData<String>()

    fun getDeviceId(): LiveData<String> {
//        viewModelScope.launch {
        runBlocking {
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
//        viewModelScope.launch {
        runBlocking {
          collectionMode.value = localDataStore.getCollectionMode.first()
        }
        return collectionMode
    }

    fun getFccPolicyAgreed(): LiveData<Boolean> {
//        viewModelScope.launch {
        runBlocking {
          fccPolicyAgreed.value = localDataStore.getFccPolicyAgreed.first()
        }
        return fccPolicyAgreed
    }

    fun getUserName(): LiveData<String> {
//        viewModelScope.launch {
        runBlocking {
            userName.value = localDataStore.getUserName.first()
        }
        return userName
    }

    fun getPhoneNumber(): LiveData<String> {
//        viewModelScope.launch {
        runBlocking {
            phoneNumber.value = localDataStore.getPhoneNumber.first()
        }
        return phoneNumber
    }

    fun getEmail(): LiveData<String> {
        viewModelScope.launch {
            email.value = localDataStore.getEmail.first()
        }
        return email
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

    fun setCollectionMode(mode: String) {
        collectionMode.value = mode
        viewModelScope.launch {
            localDataStore.saveCollectionMode(mode)
        }
    }

    fun agreeToFccPolicy(agreed: Boolean) {
        fccPolicyAgreed.value = agreed
        viewModelScope.launch {
            localDataStore.saveFccPolicyAgreed(agreed)
        }
    }

    fun setUserName(mode: String) {
        userName.value = mode
        viewModelScope.launch {
            localDataStore.saveUserName(mode)
        }
    }

    fun setPhoneNumber(mode: String) {
        phoneNumber.value = mode
        viewModelScope.launch {
            localDataStore.savePhoneNumber(mode)
        }
    }

    fun setEmail(mode: String) {
        email.value = mode
        viewModelScope.launch {
            localDataStore.saveEmail(mode)
        }
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