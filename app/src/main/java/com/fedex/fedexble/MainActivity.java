package com.fedex.fedexble;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {
    TextView textView;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    int counter;
    volatile boolean stopWorker;
    Set<BluetoothDevice> pairedDevices;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button scanButton = (Button) findViewById(R.id.scan);
        Button printButton = (Button) findViewById(R.id.print);
        textView = (TextView) findViewById(R.id.display);

        //Send Button
        printButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    sendData("name");
                } catch (IOException ex) {
                    Log.e("sendButton", "Unable to send data");
                }
            }
        });

        //Close button
        scanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    Log.d("onCreate", "Finding available and known HC-05 pBeacons");
                    findBT("HC-05");;
                    Log.d("onCreate", "Connecting to BT device HC-05");
                    openBT();
                } catch (IOException ex) {
                }
            }
        });
    }

    void findBT(String target) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            textView.setText("No bluetooth adapter available");
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

        pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                Log.d("findBT", device.getName());
                if (device.getName().equals(target)) {
                    mmDevice = device;
                    break;
                }
            }
        }
        if(mmDevice == null){
            textView.setText("No Devices Found");
        }
    }

    void openBT() throws IOException {
        if(mmOutputStream != null || mmInputStream != null){
           closeBT();
           mmOutputStream = null;
           mmInputStream = null;
        }
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
        mmSocket.connect();
        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();

        if(mmOutputStream == null || mmInputStream == null){
            Log.w("openBT", "Unable to create stream with device");
            textView.setText("No devices found");
        } else {
            textView.setText("Devices found!");
            Log.d("openBT", "Found device");
        }

        beginListenForData();
    }

    void beginListenForData() {
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable() {
            public void run() {
                while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                    try {
                        int bytesAvailable = mmInputStream.available();
                        if (bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for (int i = 0; i < bytesAvailable; i++) {
                                byte b = packetBytes[i];
                                if (b == delimiter) {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable() {
                                        public void run() {
                                            Log.d("DATA", data);
                                            textView.setText(data);
                                        }
                                    });
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    } catch (IOException ex) {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }

    void sendData(String payload) throws IOException {
        String msg = payload;
        msg += "\n";
        if(mmOutputStream != null) {
            mmOutputStream.write(msg.getBytes());
            Log.d("sendData", "Transmitted BT payload:" + payload);
        } else {
            Toast toast = Toast.makeText(getApplicationContext(),
                    "Scan for devices",
                    Toast.LENGTH_SHORT);

            toast.show();
        }
    }

    void closeBT() throws IOException {
        stopWorker = true;
        mmOutputStream.close();
        mmInputStream.close();
        mmSocket.close();
    }
}
