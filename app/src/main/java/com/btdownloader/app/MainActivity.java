package com.btdownloader.app;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

/**
 * 主界面 - 极简设计
 */
public class MainActivity extends AppCompatActivity implements BTDownloadManager.DownloadListener {
    
    private static final int REQUEST_PERMISSION = 100;
    private static final int REQUEST_MANAGE_STORAGE = 101;
    
    private BTDownloadManager downloadManager;
    private RecyclerView recyclerView;
    private TaskAdapter adapter;
    private TextView tvEmpty;
    private FloatingActionButton fabAdd;
    
    private final ActivityResultLauncher<String[]> permissionLauncher = 
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean allGranted = true;
            for (Boolean granted : result.values()) {
                if (!granted) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                initDownloadManager();
            } else {
                Toast.makeText(this, "需要存储权限才能下载文件", Toast.LENGTH_LONG).show();
            }
        });
    
    private final ActivityResultLauncher<Intent> manageStorageLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    initDownloadManager();
                } else {
                    Toast.makeText(this, "需要文件管理权限", Toast.LENGTH_LONG).show();
                }
            }
        });
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        checkPermissions();
        handleIntent(getIntent());
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }
    
    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        tvEmpty = findViewById(R.id.tvEmpty);
        fabAdd = findViewById(R.id.fabAdd);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TaskAdapter(this);
        recyclerView.setAdapter(adapter);
        
        fabAdd.setOnClickListener(v -> showAddDialog());
    }
    
    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要 MANAGE_EXTERNAL_STORAGE 权限
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    manageStorageLauncher.launch(intent);
                } catch (ActivityNotFoundException e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    manageStorageLauncher.launch(intent);
                }
                return;
            }
        } else {
            // Android 10 及以下
            String[] permissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            
            boolean allGranted = true;
            for (String p : permissions) {
                if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (!allGranted) {
                permissionLauncher.launch(permissions);
                return;
            }
        }
        
        initDownloadManager();
    }
    
    private void initDownloadManager() {
        downloadManager = new BTDownloadManager(this);
        downloadManager.setListener(this);
        downloadManager.initialize();
        updateTaskList();
    }
    
    private void handleIntent(Intent intent) {
        if (intent == null) return;
        
        String action = intent.getAction();
        Uri data = intent.getData();
        
        if (data != null) {
            String scheme = data.getScheme();
            
            if ("magnet".equals(scheme)) {
                // 磁力链接
                String magnetUri = data.toString();
                if (downloadManager != null) {
                    downloadManager.startFromMagnetLink(magnetUri);
                    Toast.makeText(this, "正在解析磁力链接...", Toast.LENGTH_SHORT).show();
                }
            } else if ("file".equals(scheme) || "content".equals(scheme)) {
                // 种子文件
                handleTorrentFile(intent);
            }
        }
    }
    
    private void handleTorrentFile(Intent intent) {
        Uri uri = intent.getData();
        if (uri == null) return;
        
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is != null) {
                File tempFile = new File(getCacheDir(), "temp.torrent");
                FileOutputStream fos = new FileOutputStream(tempFile);
                
                byte[] buffer = new byte[4096];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
                
                is.close();
                fos.close();
                
                if (downloadManager != null) {
                    downloadManager.startFromTorrentFile(tempFile);
                    Toast.makeText(this, "正在加载种子...", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "加载种子文件失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void showAddDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add, null);
        EditText etInput = dialogView.findViewById(R.id.etInput);
        Button btnFile = dialogView.findViewById(R.id.btnFile);
        Button btnMagnet = dialogView.findViewById(R.id.btnMagnet);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(dialogView)
            .create();
        
        btnFile.setOnClickListener(v -> {
            dialog.dismiss();
            openFilePicker();
        });
        
        btnMagnet.setOnClickListener(v -> {
            dialog.dismiss();
            String input = etInput.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "请输入磁力链接", Toast.LENGTH_SHORT).show();
            } else if (downloadManager != null) {
                downloadManager.startFromMagnetLink(input);
                Toast.makeText(this, "正在解析磁力链接...", Toast.LENGTH_SHORT).show();
            }
        });
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/x-bittorrent");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "选择种子文件"), 1);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            handleTorrentFile(data);
        }
    }
    
    private void updateTaskList() {
        if (downloadManager == null) return;
        List<BTDownloadManager.TorrentTask> tasks = downloadManager.getTasks();
        adapter.setTasks(tasks);
        tvEmpty.setVisibility(tasks.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(tasks.isEmpty() ? View.GONE : View.VISIBLE);
    }
    
    @Override
    public void onTaskAdded(BTDownloadManager.TorrentTask task) {
        runOnUiThread(this::updateTaskList);
    }
    
    @Override
    public void onTaskProgress(BTDownloadManager.TorrentTask task) {
        runOnUiThread(() -> adapter.notifyItemChanged(adapter.getTasks().indexOf(task)));
    }
    
    @Override
    public void onTaskCompleted(BTDownloadManager.TorrentTask task) {
        runOnUiThread(() -> {
            adapter.notifyItemChanged(adapter.getTasks().indexOf(task));
            Toast.makeText(this, "下载完成: " + task.name, Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public void onTaskError(BTDownloadManager.TorrentTask task, String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (downloadManager != null) {
            downloadManager.destroy();
        }
    }
}
