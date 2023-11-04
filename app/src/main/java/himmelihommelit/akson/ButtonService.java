package himmelihommelit.akson;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.Locale;

public class ButtonService extends Service implements View.OnClickListener, View.OnLongClickListener, TextToSpeech.OnInitListener
{
    private WindowManager mWindowManager;
    private View mWidgetView;
    private View backgroundView;

    private BluetoothService bluetoothService;

    public final static String RECORD =
            "akson.RECORD";

    private boolean bluetoothConnected = false;

    TextToSpeech tts;

    boolean recording = false;
    private View buttonView;

    private View settingsButtonView;

    ImageView button;

    int previousBatteryLevel = 0;
    boolean batteryDraining = false;
    int batteryRemainingWarned = 0;

    public ButtonService()
    {
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service)
        {
            bluetoothService = ((BluetoothService.LocalBinder) service).getService();
            if (bluetoothService != null)
            {
                if (bluetoothService.initialize())
                {
                    bluetoothService.connect();
                }
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName name)
        {
            bluetoothService = null;
        }
    };
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    public class Binder extends android.os.Binder {
        public ButtonService getService() {
            return ButtonService.this;
        }
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        tts = new TextToSpeech(this, this);
        mWidgetView = LayoutInflater.from(this).inflate(R.layout.layout_right, null);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mWidgetView, params);

        backgroundView = mWidgetView.findViewById(R.id.background);
        buttonView = mWidgetView.findViewById(R.id.button);

        buttonView.setOnClickListener(this);
        button = mWidgetView.findViewById(R.id.button);

        settingsButtonView = mWidgetView.findViewById(R.id.settingsButton);
        settingsButtonView.setOnLongClickListener(this);

        backgroundView.setVisibility(View.VISIBLE);
        startService(new Intent(ButtonService.this, BluetoothService.class));
        Intent gattServiceIntent = new Intent(this, BluetoothService.class);
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        registerReceiver(broadcastReceiver, makeGattUpdateIntentFilter());

        mWidgetView.findViewById(R.id.relativeLayoutParent).setOnTouchListener(new View.OnTouchListener()
        {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event)
            {

                switch (event.getAction())
                {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_UP:
                        backgroundView.setVisibility(View.VISIBLE);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        mWindowManager.updateViewLayout(mWidgetView, params);
                        return true;
                }
                return false;
            }
        });

    }

    @Override
    public void onDestroy()
    {
        tts.stop();
        tts.shutdown();
        unregisterReceiver(broadcastReceiver);
        unbindService(serviceConnection);
        super.onDestroy();
        if (mWidgetView != null) mWindowManager.removeView(mWidgetView);
    }

    @Override
    public void onClick(View v)
    {
        toggleRecording();
    }

    private void toggleRecording()
    {
        if(bluetoothConnected)
        {
            if (!recording)
            {
                recording = true;
                button.setImageResource(R.drawable.button_red);
                bluetoothService.StartRecording();
                MediaPlayer music = MediaPlayer.create(this, R.raw.up);
                music.start();
            }
            else
            {
                recording = false;
                button.setImageResource(R.drawable.button_green);
                bluetoothService.StopRecording();
                MediaPlayer music = MediaPlayer.create(this, R.raw.down);
                music.start();
            }
        }
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();
            if (BluetoothService.CAMERA_CONNECTED.equals(action))
            {
                button.setImageResource(R.drawable.button_green);
                bluetoothConnected = true;
            }
            else if (BluetoothService.CAMERA_DISCONNECTED.equals(action))
            {
                button.setImageResource(R.drawable.button_gray);
                bluetoothConnected = false;
            }
            else if(RECORD.equals(action))
            {
                toggleRecording();
            }
            else if(BluetoothService.DATA_AVAILABLE.equals(action))
            {
                TextView rec = (TextView) mWidgetView.findViewById(R.id.recordingTime);
                rec.setText(bluetoothService.recordingTime);
                TextView gps = (TextView) mWidgetView.findViewById(R.id.gpsLock);
                gps.setTextColor(bluetoothService.gpsLock ? Color.GREEN : Color.RED);
                int batteryLevel = bluetoothService.batteryRemaining;
                TextView bat = (TextView) mWidgetView.findViewById(R.id.batteryRemaining);
                bat.setText(batteryLevel + " %");
                TextView sd = (TextView) mWidgetView.findViewById(R.id.sdCardRemaining);
                sd.setText(bluetoothService.sdCardRemaining);
                // fix state if we start the app while the camera is recording
                if(!recording && rec.getText() != "")
                {
                    recording = true;
                    button.setImageResource(R.drawable.button_red);
                }
                if(batteryLevel > previousBatteryLevel)
                {
                    batteryDraining = false;
                }
                else if(batteryLevel < previousBatteryLevel)
                {
                    batteryDraining = true;
                }
                previousBatteryLevel = batteryLevel;
                if(batteryDraining && batteryLevel <= 20 && batteryLevel%5 == 0 && batteryRemainingWarned != batteryLevel)
                {
                    batteryRemainingWarned = batteryLevel;
                    tts.speak("kameran akkua jäljellä " + batteryLevel + " prosenttia", TextToSpeech.QUEUE_FLUSH, null, null);
                }
            }
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothService.CAMERA_CONNECTED);
        intentFilter.addAction(BluetoothService.CAMERA_DISCONNECTED);
        intentFilter.addAction(BluetoothService.DATA_AVAILABLE);
        intentFilter.addAction(RECORD);
        return intentFilter;
    }

    @Override
    public void onInit(int status)
    {
        if (status == TextToSpeech.SUCCESS)
        {
            int result = tts.setLanguage(Locale.getDefault());
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED)
            {
                tts = null;
            }
        }
    }

    @Override
    public boolean onLongClick(View view)
    {
        bluetoothService.stopSelf();
        stopSelf();
        return true;
    }
}