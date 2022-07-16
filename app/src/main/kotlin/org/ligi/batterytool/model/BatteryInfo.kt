package org.ligi.batterytool.model

data class BatteryInfo(
    var cellVoltages: MutableList<Short> = mutableListOf(),
    var voltage: UShort? = null,
    var current: Short? = null,
    var residualCapacity: UShort? = null,
    var nominalCapacity: UShort? = null,
)