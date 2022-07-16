package org.ligi.batterytool

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import java.math.BigDecimal

private const val PERMISSION_REQUEST_COARSE_LOCATION = 1

class MainActivity : AppCompatActivity() {

    private val viewModel by lazy {
        BatteryInfoViewModel(
            this,
            lifecycleScope
        )
    }

    private val startForResult = registerForActivityResult(StartActivityForResult()) {
        viewModel.checkLocationEnabledAndMaybeStartScan()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                builder.setTitle("This app needs location access")
                builder.setMessage("Please grant location access so this app can detect peripherals.")
                builder.setPositiveButton(android.R.string.ok, null)
                builder.setOnDismissListener {
                    requestPermissions(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        PERMISSION_REQUEST_COARSE_LOCATION
                    )
                }
                builder.show()
            }
        }

        setContent {
            when {
                !viewModel.adapterExists -> {
                    CenterInMaxSizeColumn {
                        Text(
                            text = "No Bluetooth found",
                            fontSize = 23.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                !viewModel.adapterEnabled -> {
                    Button(onClick = { viewModel.enableAdapter() }) {
                        Text("Enable bluetooth")
                    }
                }
                !viewModel.locationEnabled -> {
                    Button(onClick = {
                        startForResult.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }) {
                        CenterInMaxSizeColumn {
                            Text(
                                text = "Enable location to scan",
                                fontSize = 23.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                else -> {
                    if (viewModel.batteryInformation.isEmpty()) {
                        ScanningIndicator()
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            viewModel.batteryInformation.forEach { it ->
                                Text(it.key.name, modifier = Modifier.padding(8.dp))
                                it.value?.let { batteryInfo ->
                                    LargeText(
                                        batteryInfo.current?.toInt().format("A") +
                                                " @ " + batteryInfo.voltage?.toInt().format("V")
                                    )
                                    LargeText(
                                        batteryInfo.residualCapacity?.toInt().format("Ah") +
                                                "/" + batteryInfo.nominalCapacity?.toInt().format("Ah")
                                    )


                                    if (viewModel.showVoltages) {
                                        batteryInfo.cellVoltages.forEach {
                                            Text(
                                                it.toInt().format("V", 1000),
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    } else {
                                        Button(onClick = { viewModel.showVoltages = true }) {
                                            Text("Show Cell Voltages")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CenterInMaxSizeColumn(function:@Composable ColumnScope.() -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = function
        )
    }

    @Composable
    @Preview
    fun ScanningIndicator() {
        CenterInMaxSizeColumn {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                Text("Searching device(s)")
        }
    }

    @Composable
    @Preview
    fun LargeText(
        @PreviewParameter(LoremIpsum::class) currentAndVoltage: String
    ) {
        Text(
            currentAndVoltage,
            fontSize = 42.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        )
    }

    private fun Int?.format(unit: String, scaling: Int = 100) = (if (this == null) "?"
    else BigDecimal(this).divide(BigDecimal(scaling)).toString()) + unit
}

