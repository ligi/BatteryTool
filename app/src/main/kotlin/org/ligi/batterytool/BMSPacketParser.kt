package org.ligi.batterytool

import org.ligi.batterytool.model.BatteryInfo
import java.nio.ByteBuffer

class BMSPacketParser {
    var currentPackage = ByteArray(0)

    var batteryInfo = BatteryInfo()

    class FramedPackage(
        val magic: Byte,
        val type: Byte,
        val size: Short,
        val payload: ByteArray,
        val checksum: Short,
        val terminal: Byte
    )

    fun parsePacket(value: ByteArray, onChange: () -> Unit) {
        if (value.first() == 0xd.toByte()) {
            currentPackage = value
        } else {
            currentPackage += value
        }

        if (currentPackage.last() == 0x77.toByte()) {
            val framedPackage = FramedPackage(
                magic = currentPackage.first(),
                type = currentPackage[1],
                size = ByteBuffer.wrap(currentPackage.copyOfRange(2, 4)).short,
                payload = currentPackage.copyOfRange(4, currentPackage.size - 3),
                checksum = ByteBuffer.wrap(
                    currentPackage.copyOfRange(
                        currentPackage.size - 3,
                        currentPackage.size - 1
                    )
                ).short,
                terminal = currentPackage.last()

            )
            when (framedPackage.type.toInt()) {
                3 -> {
                    val buffer = ByteBuffer.wrap(framedPackage.payload)

                    batteryInfo = batteryInfo.copy(
                        voltage = buffer.short.toUShort(),
                        current = buffer.short,
                        residualCapacity = buffer.short.toUShort(),
                        nominalCapacity = buffer.short.toUShort(),
                    )
                    onChange.invoke()
                }
                4 -> {
                    val cellList: MutableList<Short> = mutableListOf()
                    val buff = ByteBuffer.wrap(framedPackage.payload)

                    while (buff.hasRemaining()) {
                        cellList.add(buff.short)
                    }
                    batteryInfo = batteryInfo.copy(cellVoltages = cellList)
                    onChange.invoke()
                }
            }
            currentPackage = ByteArray(0)
        }

    }
}