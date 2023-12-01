package himmelihommelit.akson;

import androidx.preference.PreferenceManager;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

public class SettingsActivity extends Activity
{
    Spinner verticalOrientation;
    Spinner horizontalOrientation;
    CheckBox positionLock;

    Spinner cameraMode;
    CheckBox cameraModeLock;
    TextView startSound;
    TextView endSound;
    EditText batteryWarning;

    SharedPreferences pref;
    SharedPreferences.Editor prefEditor;

    public final static int REQUEST_CODE_START_SOUND = 1;
    public final static int REQUEST_CODE_END_SOUND = 2;
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        verticalOrientation = findViewById(R.id.verticalOrientation);
        horizontalOrientation = findViewById(R.id.horizontalOrientation);
        positionLock = findViewById(R.id.positionLock);
        cameraMode = findViewById(R.id.camera_mode);
        cameraModeLock = findViewById(R.id.camera_mode_lock);
        batteryWarning = findViewById(R.id.battery_warning);
        startSound = findViewById(R.id.sound_start);
        endSound = findViewById(R.id.sound_end);

        pref = PreferenceManager.getDefaultSharedPreferences(this);
        prefEditor = pref.edit();

        verticalOrientation.setSelection(pref.getInt("verticalOrientation", 2));
        horizontalOrientation.setSelection(pref.getInt("horizontalOrientation", 2));
        positionLock.setChecked(pref.getBoolean("lockPosition", false));
        cameraMode.setSelection(pref.getInt("cameraModePosition", 0));
        cameraModeLock.setChecked(pref.getBoolean("cameraModeLock", false));
        startSound.setText(pref.getString("startRecordingSound", getResources().getString(R.string.sound_title_default)));
        endSound.setText(pref.getString("endRecordingSound", getResources().getString(R.string.sound_title_default)));
        batteryWarning.setText(pref.getString("batteryWarningText", getResources().getString(R.string.battery_warning_default)));
    }

    public void onQuit(View view)
    {
        Intent intent = new Intent(SettingsActivity.this, ButtonService.class);
        intent.setAction("STOP");
        startService(intent);
        finish();
    }

    public void onStartSoundEdit(View view)
    {
        Intent intent=new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra( RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
        startActivityForResult(intent, REQUEST_CODE_START_SOUND);
    }

    public void onEndSoundEdit(View view)
    {
        Intent intent=new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra( RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
        startActivityForResult(intent, REQUEST_CODE_END_SOUND);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK)
        {
            Uri ringtoneUri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            String ringtoneTitle;
            String ringtonePath;
            if(ringtoneUri == null)
            {
                // user pressed back without selecting a ringtone - clear ringtone
                ringtoneTitle = getResources().getString(R.string.sound_title_default);
                ringtonePath = null;
            }
            else
            {
                Ringtone ringtone = RingtoneManager.getRingtone(this, ringtoneUri);
                ringtoneTitle = ringtone.getTitle(this);
                ringtonePath = ringtoneUri.toString();
            }
            switch (requestCode)
            {
                case REQUEST_CODE_START_SOUND:
                    startSound.setText(ringtoneTitle);
                    prefEditor.putString("startRecordingSound", ringtoneTitle);
                    if(ringtonePath == null)
                    {
                        prefEditor.remove("startRecordingSoundPath");
                    }
                    else
                    {
                        prefEditor.putString("startRecordingSoundPath", ringtonePath);
                    }
                    break;
                case REQUEST_CODE_END_SOUND:
                    endSound.setText(ringtoneTitle);
                    prefEditor.putString("endRecordingSound", ringtoneTitle);
                    if(ringtonePath == null)
                    {
                        prefEditor.remove("endRecordingSoundPath");
                    }
                    else
                    {
                        prefEditor.putString("endRecordingSoundPath", ringtonePath);
                    }
                    break;
                default:
                    break;
            }
            prefEditor.apply();
        }
    }

    @Override
    public void onBackPressed()
    {
        prefEditor.putInt("verticalOrientation", verticalOrientation.getSelectedItemPosition());
        prefEditor.putInt("horizontalOrientation", horizontalOrientation.getSelectedItemPosition());
        prefEditor.putString("batteryWarningText", batteryWarning.getText().toString());
        prefEditor.putBoolean("lockPosition", positionLock.isChecked());
        prefEditor.putString("cameraMode", cameraMode.getSelectedItem().toString());
        prefEditor.putInt("cameraModePosition", cameraMode.getSelectedItemPosition());
        prefEditor.putBoolean("cameraModeLock", cameraModeLock.isChecked());
        prefEditor.apply();
        final Intent intent = new Intent(ButtonService.UPDATE_UI);
        sendBroadcast(intent);
        finish();
    }
}