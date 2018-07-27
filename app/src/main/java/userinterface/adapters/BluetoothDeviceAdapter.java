package userinterface.adapters;

import android.bluetooth.BluetoothDevice;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import chrisfry.socialq.R;
import userinterface.adapters.holders.BluetoothDeviceHolder;

/**
 * Adapter for Bluetooth device list
 */
public class BluetoothDeviceAdapter extends RecyclerView.Adapter implements
        BluetoothDeviceHolder.ItemSelectionListener {
    private List<BluetoothDevice> mBluetoothDevices = new ArrayList<>();

    private DeviceSelectionListener mBluetoothDeviceSelectionListener;

    public BluetoothDeviceAdapter() {
        super();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new BluetoothDeviceHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.queue_list_holder, parent, false));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        BluetoothDeviceHolder btHolder = (BluetoothDeviceHolder) holder;
        BluetoothDevice device = mBluetoothDevices.get(position);
        btHolder.setBluetoothDevice(device);

        // Display device name or address
        if (device.getName().isEmpty()) {
            btHolder.setDeviceName(device.getAddress());
        } else {
            btHolder.setDeviceName(device.getName());
        }

        // Listen for item selection
        btHolder.setItemSelectionListener(this);
    }

    @Override
    public int getItemCount() {
        return mBluetoothDevices.size();
    }

    public void updateDeviceList(List<BluetoothDevice> devices) {
        mBluetoothDevices = devices;
        notifyDataSetChanged();
    }

    @Override
    public void onItemSelected(BluetoothDevice device) {
        mBluetoothDeviceSelectionListener.onDeviceSelected(device);
    }


    public interface DeviceSelectionListener {
        void onDeviceSelected(BluetoothDevice device);
    }

    public void setDeviceSelectionListener(DeviceSelectionListener listener) {
        mBluetoothDeviceSelectionListener = listener;
    }
}
