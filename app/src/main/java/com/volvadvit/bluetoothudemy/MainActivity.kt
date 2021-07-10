package com.volvadvit.bluetoothudemy

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.volvadvit.bluetoothudemy.databinding.ActivityMainBinding
import java.util.*
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var btAdapter: BluetoothAdapter
    private lateinit var newAdapter: BtDeviceAdapter
    private lateinit var pairAdapter: BtDeviceAdapter
    private lateinit var menuItem: MenuItem

    private var connectedDevice: BluetoothDevice? = null

    private val pairDeviceMap: MutableMap<String, String> = mutableMapOf() // Mac address to Name
    private val newDeviceMap: MutableMap<String, String> = mutableMapOf() // Mac address to Name

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action: String = intent?.action ?: return
            receiveHandler(action, intent)
        }
    }

    companion object {
        private const val MY_UUID = "feafaa8c-e0f6-11eb-ba80-0242ac130004"
        private const val BLUETOOTH_REQUEST_CODE = 1
        private const val LOCATION_REQUEST_CODE = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "Bluetooth Helper"

        checkBuildVersion()

        initBT()
        initRecyclers()
        binding.buttonStart.setOnClickListener {startSearchDevices()}
        binding.buttonStop.setOnClickListener {
            if (btAdapter.isDiscovering) {
                btAdapter.cancelDiscovery()
            }
        }
        binding.buttonConnect.setOnClickListener {
            connectToDevice()
        }
        Toast.makeText(this,
            "Click BT-sign above to activate Bluetooth", Toast.LENGTH_LONG).show()
    }

    private fun initBT() {
        if (!this::btAdapter.isInitialized) {
            btAdapter = BluetoothAdapter.getDefaultAdapter()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (connectedDevice != null) {
            ClientThread(connectedDevice!!).cancel()
        }
        unregisterReceiver(receiver)
    }

    private fun receiveHandler(action: String, intent: Intent) {
        when (action) {
            BluetoothDevice.ACTION_FOUND -> {
                val device: BluetoothDevice =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
                if (device.name != null && device.address != null) {
                    newDeviceMap[device.address] = device.name

                    val (addressList, nameList) = newDeviceMap.toList().unzip()
                    newAdapter = BtDeviceAdapter(nameList, addressList) {address ->
                        connectedDevice = btAdapter.getRemoteDevice(address)
                        binding.buttonConnect.visibility = View.VISIBLE
                    }
                    binding.recyclerViewNew.adapter = newAdapter
                }
            }

            BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                Log.d("!@#", "START SEARCH")
                binding.textView.text = "Searching..."
                getPairedDevices()
                binding.buttonStart.visibility = View.INVISIBLE
                binding.buttonStop.visibility = View.VISIBLE
            }

            BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                Log.d("!@#", "FINISH")
                binding.textView.text = "Finished"
                binding.buttonStop.visibility = View.INVISIBLE
                binding.buttonStart.visibility = View.VISIBLE
            }
        }
    }

    private fun checkBuildVersion() {
        if (Build.VERSION.SDK_INT >= 29) {
            if (!checkLocationPermission()) {
                object : AlertDialog.Builder(this) {}
                    .setTitle("For Android 10 and above...")
                    .setMessage("To access Bluetooth adapter, you need give location permission")
                    .setPositiveButton("Accept") { dialog, which ->
                        requestLocationPermission()
                    }
                    .setNegativeButton("Deny") { dialog, which ->
                        Toast.makeText(
                            this,
                            "You should give permission, click on BT sign", Toast.LENGTH_SHORT
                        ).show()
                    }.show()
            }
        }
    }

    private fun connectToDevice() {
        if (connectedDevice != null) {
            val clientRequest = ClientThread(connectedDevice!!)
            clientRequest.run()
        }
    }

    private fun initRecyclers() {
        val (addressList, nameList) = newDeviceMap.toList().unzip()
        newAdapter = BtDeviceAdapter(nameList, addressList){}
        binding.recyclerViewNew.adapter = newAdapter

        val (pairAddressList, pairNameList) = pairDeviceMap.toList().unzip()
        pairAdapter = BtDeviceAdapter(pairNameList, pairAddressList){}
        binding.recyclerViewPair.adapter = pairAdapter
    }

    private fun startSearchDevices() {
        if (this::btAdapter.isInitialized && btAdapter.isEnabled) {
            if (checkLocationPermission()) {

                val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
                filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)

                registerReceiver(receiver, filter)
                btAdapter.startDiscovery()
            } else {
                requestLocationPermission()
            }
        } else {
            Toast.makeText(this, "BT Adapter not worked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getPairedDevices() {
        thread {
            val pairedDevices: Set<BluetoothDevice>? = btAdapter.bondedDevices

            if (!pairedDevices?.isEmpty()!!) {
                pairedDevices.forEach { device ->
                    pairDeviceMap[device.address] = device.name
                }
                val (pairAddressList, pairNameList) = pairDeviceMap.toList().unzip()
                pairAdapter = BtDeviceAdapter(pairNameList, pairAddressList) { address ->
                    connectedDevice = btAdapter.getRemoteDevice(address)
                    binding.buttonConnect.visibility = View.VISIBLE
                }
                binding.recyclerViewPair.adapter = pairAdapter
                runOnUiThread { pairAdapter.notifyDataSetChanged() }
            } else {
                Toast.makeText(this, "No paired devices", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        menuItem = menu!!.findItem(R.id.bluetooth_switcher)
        setMenuIcon()
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.bluetooth_switcher) {
            checkBuildVersion()
            switchBtStatus()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setMenuIcon() {
        if (btAdapter.isEnabled && this::menuItem.isInitialized) {
            menuItem.setIcon(R.drawable.ic_bt_disable)
        } else if (!btAdapter.isEnabled && this::menuItem.isInitialized) {
            menuItem.setIcon(R.drawable.ic_bt_enable)
        }
    }

    private fun switchBtStatus() {
        if (btAdapter.isEnabled) {
            btAdapter.disable()
            menuItem.setIcon(R.drawable.ic_bt_enable)
        } else {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(intent, Companion.BLUETOOTH_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Companion.BLUETOOTH_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                setMenuIcon()
            }
        }
    }

    private fun requestLocationPermission() {
        if (!checkLocationPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION ,
                android.Manifest.permission.ACCESS_FINE_LOCATION),
                Companion.LOCATION_REQUEST_CODE
            )
        }
    }

    private fun checkLocationPermission() =
        ActivityCompat.checkSelfPermission(
           this,
           android.Manifest.permission.ACCESS_COARSE_LOCATION
       ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Companion.LOCATION_REQUEST_CODE) {
            permissions.forEach {
                if (it == android.Manifest.permission.ACCESS_COARSE_LOCATION ||
                    it == android.Manifest.permission.ACCESS_FINE_LOCATION) {
                    if (grantResults[permissions.indexOf(it)] != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this,
                            "You should give permission, click on BT sign", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private inner class ClientThread(device: BluetoothDevice) : Thread() {

        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(UUID.fromString(MY_UUID))
        }

         override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
             if (btAdapter.isDiscovering) {
                 btAdapter.cancelDiscovery()
             }

             try {
                 mmSocket?.let { socket ->
                    // Connect to the remote device through the socket. This call blocks
                    // until it succeeds or throws an exception.
                    socket.connect()

                    // The connection attempt succeeded. Perform work associated with
                    // the connection in a separate thread.

                    manageMyConnectedSocket(socket)
                }
             } catch (e: Exception) {
                 Log.e("Socket", e.message!!)
             }
         }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: Exception) {
                Log.e("Socket", "Could not close the client socket", e)
            }
        }
    }

    private fun manageMyConnectedSocket(socket: BluetoothSocket) {
        Log.d("!@#", socket.connectionType.toString())
    }
}
