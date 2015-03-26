package com.neat.pinboard;

import android.bluetooth.BluetoothDevice;
import android.util.SparseArray;

import java.util.LinkedList;

/**
 * Created by SAM on 2015/3/26.
 */
public class UpdateDevice {
    final BluetoothDevice device;
    LinkedList<String> updateList;
    public UpdateDevice(BluetoothDevice device){
        this.device = device;
        updateList=new LinkedList<String>();
    }

    public BluetoothDevice getDevice(){
        return device;
    }
}
