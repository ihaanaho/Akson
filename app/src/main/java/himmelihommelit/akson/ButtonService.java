package himmelihommelit.akson;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.preference.PreferenceManager;
import java.util.Locale;

public class ButtonService extends Service implements View.OnClickListener, View.OnLongClickListener, TextToSpeech.OnInitListener
{
    private WindowManager mWindowManager;
    private View widgetView;

    WindowManager.LayoutParams params;
    private View backgroundView;

    private BluetoothService bluetoothService;

    public final static String RECORD = "akson.RECORD";

    public final static String UPDATE_UI = "akson.UPDATE_UI";

    private boolean bluetoothConnected = false;

    TextToSpeech tts;

    boolean recording = false;

    ImageView button;

    int batteryRemainingWarned = 0;

    SharedPreferences pref;

    public ButtonService()
    {
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
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
            initUi();
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

    private void initUi()
    {
        pref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor prefEditor = pref.edit();
        int orientation = this.getResources().getConfiguration().orientation;
        int widgetOrientation;
        if(orientation == Configuration.ORIENTATION_PORTRAIT)
        {
            widgetOrientation = pref.getInt("verticalOrientation", 2);
        }
        else
        {
            widgetOrientation = pref.getInt("horizontalOrientation", 2);
        }
        switch(widgetOrientation)
        {
            case 0:
                widgetView = LayoutInflater.from(this).inflate(R.layout.layout_up, null);
                break;
            case 1:
                widgetView = LayoutInflater.from(this).inflate(R.layout.layout_left, null);
                break;
            case 2:
                widgetView = LayoutInflater.from(this).inflate(R.layout.layout_right, null);
                break;
            case 3:
                widgetView = LayoutInflater.from(this).inflate(R.layout.layout_down, null);
                break;
        }
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);


        if(orientation == Configuration.ORIENTATION_PORTRAIT)
        {
            params.x = pref.getInt("XOnVerticalScreen", 0);
            params.y = pref.getInt("YOnVerticalScreen", 0);
        }
        else
        {
            params.x = pref.getInt("XOnHorizontalScreen", 0);
            params.y = pref.getInt("YOnHorizontalScreen", 0);
        }
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(widgetView, params);

        backgroundView = widgetView.findViewById(R.id.background);

        button = widgetView.findViewById(R.id.button);
        button.setOnClickListener(this);
        button.setOnLongClickListener(this);

        updateUi();
        updateMode();
        backgroundView.setVisibility(View.VISIBLE);

        widgetView.findViewById(R.id.relativeLayoutParent).setOnTouchListener(new View.OnTouchListener()
        {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                if(!pref.getBoolean("lockPosition", false))
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
                            mWindowManager.updateViewLayout(widgetView, params);

                            int orientation = ButtonService.this.getResources().getConfiguration().orientation;
                            if (orientation == Configuration.ORIENTATION_PORTRAIT)
                            {
                                prefEditor.putInt("XOnVerticalScreen", params.x);
                                prefEditor.putInt("YOnVerticalScreen", params.y);
                            }
                            else
                            {
                                prefEditor.putInt("XOnHorizontalScreen", params.x);
                                prefEditor.putInt("YOnHorizontalScreen", params.y);
                            }
                            prefEditor.apply();
                            return true;
                    }
                }
                return false;
            }
        });
    }

    private void updateUi()
    {
        TextView rec = widgetView.findViewById(R.id.recordingTime);
        if(bluetoothService.mode.equals("video"))
        {
            rec.setText(bluetoothService.recordingTime);
        }
        else if(bluetoothService.mode.equals("timelapse"))
        {
            if(recording)
            {
                rec.setText(getString(R.string.camera_mode_timelapse));
            }
            else
            {
                rec.setText("");
            }
        }
        TextView gps = widgetView.findViewById(R.id.gpsLock);
        gps.setTextColor(bluetoothService.gpsLock ? Color.GREEN : Color.RED);
        int batteryLevel = bluetoothService.batteryRemaining;
        TextView bat = widgetView.findViewById(R.id.batteryRemaining);
        bat.setText(String.format("%d %%", batteryLevel));
        TextView sd = widgetView.findViewById(R.id.sdCardRemaining);
        sd.setText(bluetoothService.sdCardRemaining);
    }

    private void updateMode()
    {
        checkModeLock();
        if(recording)
        {
            button.setImageResource(R.drawable.button_red);
        }
        else
        {
            switch (bluetoothService.mode)
            {
                case "video":
                    button.setImageResource(R.drawable.button_green);
                    break;
                case "timelapse":
                    button.setImageResource(R.drawable.button_amber_timelapse);
                    break;
                case "photo":
                    button.setImageResource(R.drawable.button_amber_photo);
                    break;
                default:
                    button.setImageResource(R.drawable.button_gray);
            }
        }
    }

    private void checkModeLock()
    {
        if(pref.getBoolean("cameraModeLock", false) && !bluetoothService.mode.equals(""))
        {
            if(!bluetoothService.mode.equals(pref.getString("cameraMode", "video")))
            {
                bluetoothService.SetMode(pref.getString("cameraMode", "video"));
            }
        }
    }

    private void checkBatteryLevelWarning()
    {
        int batteryLevel = bluetoothService.batteryRemaining;
        if(bluetoothService.charging)
        {
            batteryRemainingWarned = 0;
        }
        else if(batteryLevel <= 20 && batteryLevel%5 == 0 && batteryRemainingWarned != batteryLevel)
        {
            batteryRemainingWarned = batteryLevel;
            String batteryWarning = pref.getString("batteryWarningText", getResources().getString(R.string.battery_warning_default)).replace("#%", batteryLevel + "%");
            tts.speak(batteryWarning, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        tts = new TextToSpeech(this, this);

        startService(new Intent(ButtonService.this, BluetoothService.class));
        Intent gattServiceIntent = new Intent(this, BluetoothService.class);
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        registerReceiver(broadcastReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        if (intent.getAction().equals("STOP"))
        {
            bluetoothService.stopSelf();
            stopForeground(true);
            stopSelfResult(startId);
        }
        return START_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        mWindowManager.removeView(widgetView);
        super.onConfigurationChanged(newConfig);
        initUi();
    }

    @Override
    public void onDestroy()
    {
        tts.stop();
        tts.shutdown();
        unregisterReceiver(broadcastReceiver);
        unbindService(serviceConnection);
        super.onDestroy();
        if (widgetView != null) mWindowManager.removeView(widgetView);
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
                bluetoothService.StartRecording();
                String ringtonePath = pref.getString("startRecordingSoundPath", null);
                if(ringtonePath != null)
                {
                    Uri ringtoneUri = Uri.parse(ringtonePath);
                    MediaPlayer music = MediaPlayer.create(this, ringtoneUri);
                    music.start();
                }
            }
            else
            {
                bluetoothService.StopRecording();
                String ringtonePath = pref.getString("endRecordingSoundPath", null);
                if(ringtonePath != null)
                {
                    Uri ringtoneUri = Uri.parse(ringtonePath);
                    MediaPlayer music = MediaPlayer.create(this, ringtoneUri);
                    music.start();
                }
            }
        }
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();
            if (BluetoothService.CAMERA_CONNECTED.equals(action) ||
                    BluetoothService.RECORDING_STOPPED.equals(action))
            {
                switch(bluetoothService.mode)
                {
                    case "video":
                        button.setImageResource(R.drawable.button_green);
                        break;
                    case "timelapse":
                        button.setImageResource(R.drawable.button_amber_timelapse);
                        break;
                    case "photo":
                        button.setImageResource(R.drawable.button_amber_photo);
                        break;
                }
                bluetoothConnected = true;
                recording = false;
            }
            else if (BluetoothService.RECORDING_STARTED.equals(action))
            {
                button.setImageResource(R.drawable.button_red);
                bluetoothConnected = true;
                recording = true;
            }
            else if (BluetoothService.CAMERA_DISCONNECTED.equals(action))
            {
                button.setImageResource(R.drawable.button_gray);
                bluetoothConnected = false;
                recording = false;
            }
            else if(RECORD.equals(action))
            {
                toggleRecording();
            }
            else if(UPDATE_UI.equals(action))
            {
                mWindowManager.removeView(widgetView);
                initUi();
                bluetoothService.SetMode(pref.getString("cameraMode", "video"));
            }
            else if(BluetoothService.DATA_AVAILABLE.equals(action))
            {
                updateUi();
                checkBatteryLevelWarning();
            }
            else if(BluetoothService.MODE_CHANGED.equals(action))
            {
                updateMode();
            }
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothService.CAMERA_CONNECTED);
        intentFilter.addAction(BluetoothService.RECORDING_STARTED);
        intentFilter.addAction(BluetoothService.RECORDING_STOPPED);
        intentFilter.addAction(BluetoothService.CAMERA_DISCONNECTED);
        intentFilter.addAction(BluetoothService.DATA_AVAILABLE);
        intentFilter.addAction(BluetoothService.MODE_CHANGED);
        intentFilter.addAction(RECORD);
        intentFilter.addAction(UPDATE_UI);
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
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        return true;
    }
}