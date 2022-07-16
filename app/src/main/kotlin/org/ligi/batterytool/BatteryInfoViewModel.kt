package org.ligi.batterytool

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.BLUETOOTH_SERVICE
import android.content.Context.LOCATION_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.ViewModel
import com.juul.kable.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.komputing.khex.extensions.hexToByteArray
import org.komputing.khex.model.HexString
import org.ligi.batterytool.model.*
import java.util.*

class BatteryInfoViewModel(
    ctx: Context,
    private val scope: LifecycleCoroutineScope,
) :
    ViewModel() {

    private val btAdapter = (ctx.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val locationManager = ctx.getSystemService(LOCATION_SERVICE) as LocationManager

    var batteryInformation by mutableStateOf(mapOf<DeviceInfo, BatteryInfo?>())
    var showVoltages by mutableStateOf(false)
    val adapterExists = btAdapter != null
    var adapterEnabled by mutableStateOf(btAdapter?.isEnabled == true)
    var locationEnabled by mutableStateOf(false)

    private var scannerJob: Job? = null

    fun checkLocationEnabledAndMaybeStartScan() {
        locationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        scannerJob?.cancel()
        scannerJob = Scanner {
            filters = listOf(Filter.Service(UUID.fromString(BMS_SERVICE_UUID)))
        }.advertisements.onEach {
            val deviceInfo = DeviceInfo(name = it.name ?: "", address = it.address)
            if (!batteryInformation.containsKey(deviceInfo)) {
                batteryInformation = batteryInformation.toMutableMap().apply {
                    this[deviceInfo] = null
                }
                scope.launch {

                    btAdapter?.getRemoteDevice(it.address)?.let { device ->
                        scope.peripheral(device).connect(scope, deviceInfo)
                    }
                }
            }
        }.launchIn(scope)
    }


    private val bStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothAdapter.ACTION_STATE_CHANGED == action) {
                val stateExtra = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                adapterEnabled = stateExtra == BluetoothAdapter.STATE_ON
                if (adapterEnabled) checkLocationEnabledAndMaybeStartScan()
            }
        }
    }

    init {
        btAdapter?.let { _ ->
            checkLocationEnabledAndMaybeStartScan()
            ctx.registerReceiver(
                bStateReceiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            )
        }
    }

    private suspend fun Peripheral.connect(scope: LifecycleCoroutineScope, deviceInfo: DeviceInfo) {
        var dataRequestJob: Job? = null
        val parser = BMSPacketParser()
        try {
            state.collect {
                connect()

                if (it is State.Connected) {

                    observe(characteristicOf(BMS_SERVICE_UUID, BMS_READ_CHAR))
                        .onEach { data ->
                            parser.parsePacket(data) {
                                val copy = batteryInformation.toMutableMap()
                                copy[deviceInfo] = parser.batteryInfo
                                batteryInformation = copy
                            }
                        }.launchIn(scope)

                    dataRequestJob = scope.launch { poll() }
                }
                if (it is State.Disconnected) {
                    dataRequestJob?.cancel()
                }
            }
        } catch (e: IOException) {
            connect(scope, deviceInfo)
        } catch (e: BluetoothDisabledException) {
            // we need to wait for bt enabled and start a new scan then
        }
    }

    private suspend fun Peripheral.poll() {
        while (state.value == State.Connected) {
            try {
                if (showVoltages) {

                    write(
                        characteristicOf(BMS_SERVICE_UUID, BMS_WRITE_CHAR),
                        HexString("DDA50400FFFC77").hexToByteArray()
                    )
                    delay(200)
                }

                write(
                    characteristicOf(BMS_SERVICE_UUID, BMS_WRITE_CHAR),
                    HexString("DDA50300FFFD77").hexToByteArray()
                )
            } catch (ioe: IOException) {
                // no direct action - we will re-connect
            }
            delay(200)
        }
    }

    fun enableAdapter() = btAdapter?.enable()

}