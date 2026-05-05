package com.btdownloader.app;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.Configuration;
import com.frostwire.jlibtorrent.DHTEngine;
import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.Libtorrent;
import com.frostwire.jlibtorrent.Pair;
import com.frostwire.jlibtorrent.Session;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentStatus;
import com.frostwire.jlibtorrent.AddTorrentParams;
import com.frostwire.jlibtorrent.swig.libtorrent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * BT下载核心管理类
 */
public class BTDownloadManager {
    private static final String TAG = "BTDownloadManager";
    
    private SessionManager sessionManager;
    private final Context context;
    private final Handler mainHandler;
    private final List<TorrentTask> tasks;
    private DownloadListener listener;
    
    private File downloadDir;
    
    public interface DownloadListener {
        void onTaskAdded(TorrentTask task);
        void onTaskProgress(TorrentTask task);
        void onTaskCompleted(TorrentTask task);
        void onTaskError(TorrentTask task, String error);
    }
    
    public static class TorrentTask {
        public String id;
        public String name;
        public String savePath;
        public int state; // 0=等待中, 1=下载中, 2=完成, 3=错误
        public float progress;
        public long totalSize;
        public long downloadedSize;
        public long downloadSpeed;
        public long uploadSpeed;
        public int peers;
        public String errorMsg;
        public TorrentHandle handle;
        
        public TorrentTask(String id, String name, String savePath) {
            this.id = id;
            this.name = name;
            this.savePath = savePath;
            this.state = 0;
            this.progress = 0;
        }
    }
    
    public BTDownloadManager(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.tasks = new CopyOnWriteArrayList<>();
        
        // 设置下载目录
        downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir == null) {
            downloadDir = new File(context.getFilesDir(), "downloads");
        }
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
    }
    
    public void setListener(DownloadListener listener) {
        this.listener = listener;
    }
    
    public File getDownloadDir() {
        return downloadDir;
    }
    
    public List<TorrentTask> getTasks() {
        return new ArrayList<>(tasks);
    }
    
    public void initialize() {
        try {
            Libtorrent.initialize();
            
            sessionManager = new SessionManager();
            sessionManager.addListener(new AlertListener() {
                @Override
                public void alert(int type, String msg) {
                    handleAlert(type, msg);
                }
            });
            
            // 配置会话
            Configuration config = new Configuration();
            config.maxConnections(100);
            config.maxUploads(-1);
            config.uploadRateLimit(0);
            config.downloadRateLimit(0);
            
            sessionManager.start(config);
            Log.i(TAG, "BT下载引擎初始化成功");
        } catch (Exception e) {
            Log.e(TAG, "初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void destroy() {
        if (sessionManager != null) {
            for (TorrentTask task : tasks) {
                if (task.handle != null) {
                    try {
                        sessionManager.remove(task.handle);
                    } catch (Exception ignored) {}
                }
            }
            sessionManager.stop();
            sessionManager = null;
        }
    }
    
    /**
     * 从种子文件开始下载
     */
    public void startFromTorrentFile(File torrentFile) {
        new Thread(() -> {
            try {
                TorrentInfo ti = TorrentInfo.bdecode(torrentFile);
                startDownload(ti, torrentFile.getName().replace(".torrent", ""));
            } catch (Exception e) {
                notifyError(null, "种子文件解析失败: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 从磁力链接开始下载
     */
    public void startFromMagnetLink(String magnetLink) {
        new Thread(() -> {
            try {
                // 解析磁力链接获取名称
                String name = extractNameFromMagnet(magnetLink);
                
                AddTorrentParams params = AddTorrentParams.parseMagnetUri(magnetLink);
                
                TorrentTask task = new TorrentTask(
                    generateId(),
                    name != null ? name : "磁力下载",
                    downloadDir.getAbsolutePath()
                );
                task.state = 1;
                tasks.add(task);
                notifyTaskAdded(task);
                
                params.setSavePath(downloadDir.getAbsolutePath());
                sessionManager.download(params, new AlertListener() {
                    @Override
                    public void alert(int type, String msg) {
                        handleAlert(type, msg);
                    }
                });
                
            } catch (Exception e) {
                notifyError(null, "磁力链接解析失败: " + e.getMessage());
            }
        }).start();
    }
    
    private void startDownload(TorrentInfo ti, String suggestedName) {
        try {
            String taskId = generateId();
            String savePath = downloadDir.getAbsolutePath();
            
            TorrentTask task = new TorrentTask(taskId, suggestedName, savePath);
            task.state = 1;
            task.totalSize = ti.totalSize();
            tasks.add(task);
            notifyTaskAdded(task);
            
            AddTorrentParams params = AddTorrentParams.create().savePath(savePath).ti(ti);
            sessionManager.download(params, new AlertListener() {
                @Override
                public void alert(int type, String msg) {
                    handleAlert(type, msg);
                }
            });
            
        } catch (Exception e) {
            notifyError(null, "下载启动失败: " + e.getMessage());
        }
    }
    
    private void handleAlert(int type, String msg) {
        // 更新任务状态
        for (TorrentTask task : tasks) {
            if (task.handle != null) {
                try {
                    TorrentStatus status = task.handle.status();
                    
                    mainHandler.post(() -> {
                        task.state = status.state();
                        task.progress = status.progress();
                        task.downloadedSize = status.totalValid();
                        task.totalSize = status.total();
                        task.downloadSpeed = status.downloadRate();
                        task.uploadSpeed = status.uploadRate();
                        task.peers = status.numPeers();
                        
                        if (listener != null) {
                            if (status.isFinished()) {
                                task.state = 2;
                                listener.onTaskCompleted(task);
                            } else {
                                listener.onTaskProgress(task);
                            }
                        }
                    });
                    
                } catch (Exception ignored) {}
            }
        }
    }
    
    public void pauseTask(TorrentTask task) {
        if (task != null && task.handle != null) {
            try {
                task.handle.pause();
                task.state = 0;
            } catch (Exception e) {
                Log.e(TAG, "暂停失败: " + e.getMessage());
            }
        }
    }
    
    public void resumeTask(TorrentTask task) {
        if (task != null && task.handle != null) {
            try {
                task.handle.resume();
                task.state = 1;
            } catch (Exception e) {
                Log.e(TAG, "恢复失败: " + e.getMessage());
            }
        }
    }
    
    public void removeTask(TorrentTask task, boolean deleteFiles) {
        if (task != null) {
            try {
                if (task.handle != null) {
                    sessionManager.remove(task.handle);
                }
                tasks.remove(task);
                
                if (deleteFiles) {
                    File saveDir = new File(task.savePath, task.name);
                    if (saveDir.exists()) {
                        deleteRecursive(saveDir);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "删除失败: " + e.getMessage());
            }
        }
    }
    
    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
    
    private String extractNameFromMagnet(String magnet) {
        if (magnet == null) return null;
        String dn = "dn=";
        int start = magnet.indexOf(dn);
        if (start >= 0) {
            start += dn.length();
            int end = magnet.indexOf('&', start);
            if (end < 0) end = magnet.length();
            try {
                return java.net.URLDecoder.decode(magnet.substring(start, end), "UTF-8");
            } catch (Exception ignored) {}
        }
        return null;
    }
    
    private String generateId() {
        return String.valueOf(System.currentTimeMillis());
    }
    
    private void notifyTaskAdded(TorrentTask task) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onTaskAdded(task);
            }
        });
    }
    
    private void notifyError(TorrentTask task, String error) {
        mainHandler.post(() -> {
            if (task != null) {
                task.state = 3;
                task.errorMsg = error;
            }
            if (listener != null) {
                listener.onTaskError(task, error);
            }
        });
    }
}
