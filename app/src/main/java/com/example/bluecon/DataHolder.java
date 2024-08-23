package com.example.bluecon;

public class DataHolder {
    private static String bluetoothAddress;

    public static void setBluetoothAddress(String address) {
        bluetoothAddress = address;
    }

    public static String getBluetoothAddress() {
        return bluetoothAddress;
    }
}
