package de.glut.launcher.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

data class BtDevice(val name: String, val address: String, val bonded: Boolean)

/** Bluetooth ohne Amazons Einstellungen: ein-/ausschalten, suchen, koppeln, entkoppeln. */
@Suppress("DEPRECATION", "MissingPermission")
class BluetoothControl(context: Context, private val onChanged: () -> Unit) {
    private val app = context.applicationContext
    private val adapter: BluetoothAdapter? = (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val found = linkedMapOf<String, BluetoothDevice>()
    private var receiver: BroadcastReceiver? = null

    val available get() = adapter != null
    val enabled get() = adapter?.isEnabled == true
    val scanning get() = adapter?.isDiscovering == true

    fun setEnabled(on: Boolean) {
        if (on) adapter?.enable() else adapter?.disable()
    }

    fun devices(): List<BtDevice> {
        val bonded = adapter?.bondedDevices.orEmpty()
        val bondedAddresses = bonded.map { it.address }.toSet()
        return (bonded.map { it.toItem(true) } + found.values.filter { it.address !in bondedAddresses }.map { it.toItem(false) })
    }

    fun startScan() {
        val bt = adapter ?: return
        if (!bt.isEnabled) return
        if (receiver == null) {
            receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == BluetoothDevice.ACTION_FOUND) {
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)?.let { found[it.address] = it }
                    }
                    onChanged()
                }
            }
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        found.clear()
        if (bt.isDiscovering) bt.cancelDiscovery()
        bt.startDiscovery()
        onChanged()
    }

    fun stop() {
        adapter?.takeIf { it.isEnabled && it.isDiscovering }?.cancelDiscovery()
        receiver?.let { runCatching { app.unregisterReceiver(it) } }
        receiver = null
    }

    fun pair(address: String): Boolean {
        val device = found[address] ?: adapter?.getRemoteDevice(address) ?: return false
        adapter?.cancelDiscovery()
        return device.createBond()
    }

    /** removeBond ist nicht öffentlich, lässt sich aber für eigene Kopplungen aufrufen. */
    fun unpair(address: String): Boolean {
        val device = adapter?.bondedDevices?.firstOrNull { it.address == address } ?: return false
        return runCatching { device.javaClass.getMethod("removeBond").invoke(device) as Boolean }.getOrDefault(false)
    }

    private fun BluetoothDevice.toItem(bonded: Boolean) = BtDevice(name?.takeIf { it.isNotBlank() } ?: address, address, bonded)
}
