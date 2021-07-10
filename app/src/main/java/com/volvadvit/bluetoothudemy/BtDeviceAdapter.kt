package com.volvadvit.bluetoothudemy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.recyclerview.widget.RecyclerView

class BtDeviceAdapter(private val nameList: List<String>,
                      private val addressList: List<String>,
                      private val listener: (String) -> Unit) :
    RecyclerView.Adapter<BtDeviceAdapter.BtDeviceViewHolder>() {

    class BtDeviceViewHolder(item: View): RecyclerView.ViewHolder(item) {
        val deviceName: TextView = item.findViewById(R.id.item_device_name)
        val deviceAddress: TextView = item.findViewById(R.id.item_device_address)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        BtDeviceViewHolder(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false))

    override fun onBindViewHolder(holder: BtDeviceViewHolder, position: Int) {
        holder.deviceName.text = nameList[position]
        holder.deviceAddress.text = addressList[position]

        holder.itemView.setOnClickListener {
            listener(
                it.findViewById<TextView>(R.id.item_device_address).text.toString(),
            )
        }
    }

    override fun getItemCount() = nameList.size
}
