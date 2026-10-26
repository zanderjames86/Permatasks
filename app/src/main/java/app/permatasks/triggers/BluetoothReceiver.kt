package app.permatasks.triggers

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.permatasks.container
import app.permatasks.runAsync

data class PairedDevice(val name: String, val address: String)

object Bluetooth {
    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun paired(context: Context): List<PairedDevice> {
        if (!hasPermission(context)) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return runCatching { adapter.bondedDevices.map { PairedDevice(it.name ?: it.address, it.address) } }
            .getOrDefault(emptyList())
            .sortedBy { it.name.lowercase() }
    }
}

/**
 * ACL connect is one of the few broadcasts still delivered to manifest receivers,
 * so this fires even when the app isn't running — e.g. when you get into the car.
 */
class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        val address = device.address ?: return
        runAsync(context) { context.container.repo.fireBluetooth(address) }
    }
}
