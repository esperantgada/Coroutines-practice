// * Copyright 2018, The Android Open Source Project
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel(val database: SleepDatabaseDao, application: Application) :
    AndroidViewModel(application) {

    private var viewModelJob = Job()  //Manage all coroutines


    override fun onCleared() {
        super.onCleared()
        /**Tell the job to cancel all coroutines**/
        viewModelJob.cancel()
    }

    //Determines what thread the coroutine will run on and knows about the job
    private var uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    private val toNight = MutableLiveData<SleepNight?>()  //Hold the current night

    private val nights = database.getAllNight()   //get all nights from the database

    /**Is visible when toNight is null**/
    val startButtonVisible = Transformations.map(toNight){
        it == null
    }

    /**Is visible when toNight is not null**/
    val stopButtonVisible = Transformations.map(toNight){
        it != null
    }

    /**Is visible when there are nights to clear**/
    val clearButtonVisible = Transformations.map(nights){
        it?.isNotEmpty()
    }

    /**Add this in XML to handle Buttons visibility**/
    /*android:enabled="@{sleepTrackerViewModel.startButtonVisible}"
    android:enabled="@{sleepTrackerViewModel.stopButtonVisible}"
    android:enabled="@{sleepTrackerViewModel.clearButtonVisible}"*/

    /**Show a SnackBar as a quick message to the user**/
    private val _showSnackbarEvent = MutableLiveData<Boolean?>()
            val showSnackbar : LiveData<Boolean?>
            get() = _showSnackbarEvent

    fun doneShowingSnackBar(){
        _showSnackbarEvent.value = false  /**Go to add an observer for this in its fragment**/
    }


    /**For navigating to sleepQualityFragment**/
    private val _navigateToSleepQuality = MutableLiveData<SleepNight>()
            val navigateToSleepQuality : LiveData<SleepNight>
            get() = _navigateToSleepQuality

    fun doneNavigation(){
        _navigateToSleepQuality.value = null
    }

    /**
     * Displaying data by using map transformation
     * Add :android:text="@{sleepTrackerViewModel.nightString}" XML
     */
    val nightString = Transformations.map(nights) { nights ->
       formatNights(nights, application.resources)
    }

    init {
        initializeTonight()
    }

    /**Get toNight from database without blocking the current thread**/
    private fun initializeTonight() {
        uiScope.launch {
            toNight.value = getTonightFromDataBase()
        }
    }

    private suspend fun getTonightFromDataBase(): SleepNight? {
        return withContext(Dispatchers.IO) {
            var night = database.getTonight()                 //Will return the last night
            if (night?.endTimeMilli != night?.startTimeMilli) {
                night = null
            }
            return@withContext night
        }
    }


    /**Insert a night into the database**/
    fun onStartTracking() {
        uiScope.launch {
            val newNight = SleepNight()
            insert(newNight)

            toNight.value = getTonightFromDataBase()
        }
    }

    private suspend fun insert(night: SleepNight) {
        withContext(Dispatchers.IO) { database.insert(night) }
    }


    fun onStopTracking() {
        uiScope.launch {
            val oldNight = toNight.value ?: return@launch  /**Return from the launch**/

            /**Update the night in the database to add the end time.**/
            oldNight.endTimeMilli = System.currentTimeMillis()
            update(oldNight)

            _navigateToSleepQuality.value = oldNight
        }
    }

    /**Update the database**/
    private suspend fun update(night: SleepNight) {
        withContext(Dispatchers.IO) {
            database.update(night)
        }
    }


    /**Clears  the database**/
    fun onClear() {
        uiScope.launch {
            clear()
            toNight.value = null
            _showSnackbarEvent.value = true
        }
    }

    private suspend fun clear() {
        withContext(Dispatchers.IO) {
            database.clear()
        }
    }

    /**
     * Add this in the XML to set up a click handler with dataBinding:
     * android:onClick="@{() -> sleepTrackerViewModel.FunctionName"
     * android:onClick="@{() -> sleepTrackerViewModel.onStopTracking()"
     * android:onClick="@{() -> sleepTrackerViewModel.onClear()"
     * android:onClick="@{() -> sleepTrackerViewModel.onStartTracking()"
     */
}

