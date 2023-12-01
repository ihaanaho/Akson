package himmelihommelit.akson;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

public class PermissionsActivity extends Activity implements View.OnClickListener
{
        private boolean overlayPermissionGranted = false;
        private boolean bluetoothPermissionGranted = false;

        @Override
        protected void onCreate(Bundle savedInstanceState)
        {
                super.onCreate(savedInstanceState);
                checkPermissions();
        }

        @Override
        protected void onResume()
        {
                super.onResume();
                checkPermissions();
        }

        private void checkPermissions()
        {
                checkOverlayPermission();
                checkBluetoothPermission();
                if(!overlayPermissionGranted || !bluetoothPermissionGranted)
                {
                        setContentView(R.layout.activity_main);
                }
                else
                {
                        startService();
                }
        }
        public void checkOverlayPermission()
        {
                if (Settings.canDrawOverlays(this))
                {
                        overlayPermissionGranted = true;
                }
        }

        public void checkBluetoothPermission()
        {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
                {
                        bluetoothPermissionGranted = true;
                }
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, int[] grantResults)
        {
                if (grantResults.length > 0 && permissions.length==grantResults.length && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                        bluetoothPermissionGranted = true;
                }
                else
                {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                        intent.addCategory(Intent.CATEGORY_DEFAULT);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        Toast.makeText(this, getString(R.string.toast_nearby_devices), Toast.LENGTH_LONG).show();
                }
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }

        private void startService()
        {
                Intent intent = new Intent(PermissionsActivity.this, ButtonService.class);
                intent.setAction("START");
                startService(intent);
                finish();
        }

        @Override
        public void onClick(View view)
        {
                if(!bluetoothPermissionGranted)
                {
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
                }
                if(!overlayPermissionGranted)
                {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                        startActivity(intent);
                        Toast.makeText(this, getString(R.string.toast_overlay), Toast.LENGTH_LONG).show();
                }
        }
}