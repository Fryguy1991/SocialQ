package business.listeners;

import android.bluetooth.BluetoothSocket;

/**
 * Listener method for when BT connection has been established
 */
public interface BluetoothConnectionListener {
    void onConnectionEstablished(BluetoothSocket socket);
//    void onConnectionFailed();
}
