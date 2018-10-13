package com.chrisfry.socialq.userinterface.adapters.holders;

import android.bluetooth.BluetoothDevice;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import com.chrisfry.socialq.R;

/**
 * List holder for bluetooth devices
 */
public class BluetoothDeviceHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

    private BluetoothDevice mDevice;

    // UI elements
    private TextView mDeviceName;
    private View mParentView;

    private ItemSelectionListenerBluetooth mItemSelectionListener;

    public BluetoothDeviceHolder(View itemView) {
        super(itemView);

        mParentView = itemView.findViewById(R.id.ll_device_holder);
        mParentView.setOnClickListener(this);

        mDeviceName = (TextView) itemView.findViewById(R.id.tv_device_name);
    }

    public void setDeviceName(String deviceName) {
        mDeviceName.setText(deviceName);
    }

    public void setBluetoothDevice(BluetoothDevice device) {
        mDevice = device;
    }

    @Override
    public void onClick(View view) {
        // TODO: probably a better way to switch between selected or not
        if (mItemSelectionListener != null) {
            if (mParentView.getContentDescription().equals("not_selected")) {
                mParentView.setContentDescription("selected");
                mParentView.setBackgroundColor(mParentView.getResources().getColor(R.color.Active_Button_Color));
                mDeviceName.setTextColor(mParentView.getResources().getColor(R.color.White));
                mItemSelectionListener.onItemSelected(mDevice);
            } else {
                mParentView.setContentDescription("not_selected");
                mParentView.setBackgroundColor(mParentView.getResources().getColor(R.color.White));
                mDeviceName.setTextColor(mParentView.getResources().getColor(R.color.Gray));
                mItemSelectionListener.onItemSelected(null);
            }
        }
    }

    public interface ItemSelectionListenerBluetooth {
        void onItemSelected(BluetoothDevice device);
    }

    public void setItemSelectionListener(ItemSelectionListenerBluetooth listener) {
        mItemSelectionListener = listener;
    }
}
