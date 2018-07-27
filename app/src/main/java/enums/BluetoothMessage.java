package enums;

/**
 * Enum representation for bluetooth message types
 */
public enum  BluetoothMessage {
    START_MESSAGE(0),
    END_MESSAGE(1),
    SEND_TRACK(2),
    SEND_QUEUE(3);

    private int mMessageId;

    BluetoothMessage(int id) {
        mMessageId = id;
    }

    public int getMessageId() {
        return mMessageId;
    }

    public static BluetoothMessage getBluetoothMessageById(int id) {
        for (BluetoothMessage message : BluetoothMessage.values()) {
            if (message.getMessageId() == id) {
                return message;
            }
        }
        return null;
    }
}
