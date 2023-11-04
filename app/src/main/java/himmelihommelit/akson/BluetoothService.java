package himmelihommelit.akson;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static java.lang.Math.abs;
import static java.lang.System.arraycopy;

@SuppressLint("MissingPermission")
public class BluetoothService extends Service
{
    private BluetoothDevice goPro = null;
    private BluetoothAdapter bluetoothAdapter;
    public BluetoothGatt bluetoothGatt = null;
    public final static String CAMERA_CONNECTED =
            "akson.CAMERA_CONNECTED";
    public final static String CAMERA_DISCONNECTED =
            "akson.CAMERA_DISCONNECTED";
    public final static String DATA_AVAILABLE =
            "akson.DATA_AVAILABLE";
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTED = 2;

    private int connectionState;

    private final Binder binder = new LocalBinder();

    BluetoothGattCharacteristic command = null; // 0072
    BluetoothGattCharacteristic commandResponse = null; // 0073

    BluetoothGattCharacteristic settings = null; // 0074
    BluetoothGattCharacteristic settingsResponse = null; // 0075
    /*
    2: resolution
    3: fps
    59: auto power down
    108: video aspect ratio
    121: video digital lenses (wide etc.)
    122: photo digital lenses
    123: time lapse digital lenses
    128: media format
    134: anti-flicker (60/50 Hz)
    135: hypersmooth
    150: video horizon leveling
    151: photo horizon leveling
    162: max lens

     */
    BluetoothGattCharacteristic query = null; // 0076
    BluetoothGattCharacteristic queryResponse = null; // 0077

    private byte[] assembledPacket;
    private int remainingBytes = 0;
    private int assembledPacketPointer = -1;

    public int batteryRemaining = 0;
    public String sdCardRemaining = "00:00";
    public String recordingTime = "00:00";
    public boolean gpsLock = false;


    public BluetoothService()
    {
    }

    public boolean initialize() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return bluetoothAdapter != null;
    }

    public void connect() {
        if (bluetoothAdapter == null) {
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0)
        {
            for (BluetoothDevice currentDevice : pairedDevices)
            {
                // gets the first GoPro
                if (currentDevice.getName().contains("GoPro"))
                {
                    goPro = currentDevice;
                    break;
                }
            }
        }
        if(goPro != null)
        {
            bluetoothGatt = goPro.connectGatt(this, true, bluetoothGattCallback);
        }
    }

    public void StartRecording()
    {
        if(command == null)
        {
            return;
        }
        command.setValue(new byte[] {3,1,1,1});
        bluetoothGatt.writeCharacteristic(command);
    }

    public void StopRecording()
    {
        if(command == null)
        {
            return;
        }
        command.setValue(new byte[] {3,1,1,0});
        bluetoothGatt.writeCharacteristic(command);
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void enableNotification(BluetoothGattCharacteristic characteristic)
    {
        bluetoothGatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        bluetoothGatt.writeDescriptor(descriptor);
    }

    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback()
    {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
        {
            if (newState == BluetoothProfile.STATE_CONNECTED)
            {
                // successfully connected to the GATT Server
                connectionState = STATE_CONNECTED;

                // Attempts to discover services after successful connection.
                bluetoothGatt.discoverServices();
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED)
            {
                // disconnected from the GATT Server
                connectionState = STATE_DISCONNECTED;
                broadcastUpdate(CAMERA_DISCONNECTED);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status)
        {
            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                List<BluetoothGattService> services = gatt.getServices();
                for(BluetoothGattService service : services)
                {
                    List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                    for(BluetoothGattCharacteristic characteristic : characteristics)
                    {
                        if(characteristic.getUuid().equals(UUID.fromString("b5f90072-aa8d-11e3-9046-0002a5d5c51b")))
                        {
                            command = characteristic;
                        }
                        else if (characteristic.getUuid().equals(UUID.fromString("b5f90073-aa8d-11e3-9046-0002a5d5c51b")))
                        {
                            commandResponse = characteristic;
                        }
                        else if (characteristic.getUuid().equals(UUID.fromString("b5f90074-aa8d-11e3-9046-0002a5d5c51b")))
                        {
                            settings = characteristic;
                        }
                        else if (characteristic.getUuid().equals(UUID.fromString("b5f90075-aa8d-11e3-9046-0002a5d5c51b")))
                        {
                            settingsResponse = characteristic;
                        }
                        else if (characteristic.getUuid().equals(UUID.fromString("b5f90076-aa8d-11e3-9046-0002a5d5c51b")))
                        {
                            query = characteristic;
                        }
                        else if (characteristic.getUuid().equals(UUID.fromString("b5f90077-aa8d-11e3-9046-0002a5d5c51b")))
                        {
                            queryResponse = characteristic;
                        }
                    }
                }
                enableNotification(commandResponse);
                broadcastUpdate(CAMERA_CONNECTED);
            }
        }

        @Override
        public void onCharacteristicRead(
                BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic,
                int status
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS)
            {
            }
        }

        @Override
        public void onCharacteristicWrite(
                BluetoothGatt bluetoothGatt,
                BluetoothGattCharacteristic characteristic,
                int status
        )
        {
            if (status != BluetoothGatt.GATT_SUCCESS)
            {
            }
        }

        @Override
        public void onCharacteristicChanged(
                BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic
        )
        {
            switch(characteristic.getUuid().toString())
            {
                case "b5f90073-aa8d-11e3-9046-0002a5d5c51b": // command response
                    break;
                case "b5f90075-aa8d-11e3-9046-0002a5d5c51b": // settings response
                    break;
                case "b5f90077-aa8d-11e3-9046-0002a5d5c51b": // query response
                    /* note that this assumes only one active query - for multiple
                       queries the notifications may be interleaved! */
                    accumulatePackets(characteristic.getValue());
                    break;
            }
        }

        @Override
        public void onDescriptorWrite(
                BluetoothGatt bluetoothGatt,
                BluetoothGattDescriptor bluetoothGattDescriptor,
                int status
        )
        {
            if(bluetoothGattDescriptor.getCharacteristic().getUuid().equals(UUID.fromString("b5f90073-aa8d-11e3-9046-0002a5d5c51b")))
            {
                enableNotification(settingsResponse);
            }
            if(bluetoothGattDescriptor.getCharacteristic().getUuid().equals(UUID.fromString("b5f90075-aa8d-11e3-9046-0002a5d5c51b")))
            {
                enableNotification(queryResponse);
            }
            if(bluetoothGattDescriptor.getCharacteristic().getUuid().equals(UUID.fromString("b5f90077-aa8d-11e3-9046-0002a5d5c51b")))
            {
                boolean success = query.setValue(new byte[] {5,0x53,13,35,68,70});
                success = bluetoothGatt.writeCharacteristic(query);
            }
        }
    };

    private void accumulatePackets(byte[] packet)
    {
        int header = abs(packet[0]);
        if(header < 20) // single-packet message
        {
            assembledPacket = new byte[header];
            arraycopy(packet, 1, assembledPacket, 0, header);
            decodePacket(assembledPacket);
        }
        else // multi-packet message
        {
            if(header == 32) // first packet
            {
                int size = packet[1];
                assembledPacket = new byte[size];
                assembledPacketPointer = 0;
                try
                {
                    arraycopy(packet, 2, assembledPacket, 0, 18);
                }
                catch (ArrayIndexOutOfBoundsException e)
                {
                    assembledPacketPointer = -1; // causes the follow-up packets to fail as well
                    return;
                }
                remainingBytes = size - 18; // first packet always contains payload of 18 bytes
                assembledPacketPointer = 18;
            }
            else
            {
                if(remainingBytes > 19) // more packets to come
                {
                    try
                    {
                        arraycopy(packet, 1, assembledPacket, assembledPacketPointer, 19);
                    }
                    catch (ArrayIndexOutOfBoundsException e)
                    {
                        assembledPacketPointer = -1; // causes the follow-up packets to fail as well
                        return;
                    }
                    assembledPacketPointer += 19;
                }
                else // final packet
                {
                    try
                    {
                        arraycopy(packet, 1, assembledPacket, assembledPacketPointer, remainingBytes);
                    }
                    catch (ArrayIndexOutOfBoundsException e)
                    {
                        assembledPacketPointer = -1;
                        return;
                    }
                    assembledPacketPointer = -1;
                    decodePacket(assembledPacket);
                }
            }
        }
    }

    private void decodePacket(byte[] packet)
    {
        int pointer = 0;
        while(pointer < packet.length)
        {
            int id = packet[pointer];
            switch(id)
            {
                case 13: // recording time [s]
                    int recTime = bytearrayToInt(packet, pointer+2, 4);
                    if(recTime == 0)
                    {
                        recordingTime = "";
                    }
                    else if(recTime >= 3600)
                    {
                        recordingTime = String.format("%d:%02d:%02d", recTime / 3600, (recTime % 3600) / 60, (recTime % 60));
                    }
                    else
                    {
                        recordingTime = String.format("%d:%02d", recTime / 60, (recTime % 60));
                    }
                    break;
                case 35: // remaining video time [min]
                    int remTime = bytearrayToInt(packet, pointer+2, 4);
                    sdCardRemaining = String.format("%d:%02d", remTime / 3600, (remTime % 3600) / 60);
                    break;
                case 68: // gps lock
                    gpsLock = packet[pointer + 2] != 0;break;
                case 70: // remaining battery %
                    batteryRemaining = packet[pointer+2];
                    break;
            }
            int len = packet[pointer+1];
            pointer += len+2;
            broadcastUpdate(DATA_AVAILABLE);
        }
    }

    private int bytearrayToInt(byte[] array, int pointer, int count)
    {
        int value = 0;
        for(int i=0; i<count; i++)
        {
            value += (array[pointer+i] & 0xFF) << ((count-i-1)*8);
        }
        return value;
    }
    @Override
    public IBinder onBind(Intent intent)
    {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    private void close() {
        if (bluetoothGatt == null) {
            return;
        }
        bluetoothGatt.close();
        bluetoothGatt = null;
    }

    class LocalBinder extends Binder {
        public BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
    }
}