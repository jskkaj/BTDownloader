package com.btdownloader.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 下载任务列表适配器
 */
public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {
    
    private final Context context;
    private List<BTDownloadManager.TorrentTask> tasks;
    private BTDownloadManager downloadManager;
    
    public TaskAdapter(Context context) {
        this.context = context;
        this.tasks = new ArrayList<>();
    }
    
    public void setDownloadManager(BTDownloadManager manager) {
        this.downloadManager = manager;
    }
    
    public void setTasks(List<BTDownloadManager.TorrentTask> tasks) {
        this.tasks = tasks != null ? tasks : new ArrayList<>();
        notifyDataSetChanged();
    }
    
    public List<BTDownloadManager.TorrentTask> getTasks() {
        return tasks;
    }
    
    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        BTDownloadManager.TorrentTask task = tasks.get(position);
        holder.bind(task);
    }
    
    @Override
    public int getItemCount() {
        return tasks.size();
    }
    
    class TaskViewHolder extends RecyclerView.ViewHolder {
        
        TextView tvName;
        TextView tvProgress;
        TextView tvSpeed;
        TextView tvInfo;
        ProgressBar progressBar;
        Button btnPause;
        Button btnDelete;
        
        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvProgress = itemView.findViewById(R.id.tvProgress);
            tvSpeed = itemView.findViewById(R.id.tvSpeed);
            tvInfo = itemView.findViewById(R.id.tvInfo);
            progressBar = itemView.findViewById(R.id.progressBar);
            btnPause = itemView.findViewById(R.id.btnPause);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
        
        void bind(BTDownloadManager.TorrentTask task) {
            tvName.setText(task.name);
            tvProgress.setText(String.format(Locale.getDefault(), "%.1f%%", task.progress * 100));
            
            progressBar.setProgress((int) (task.progress * 100));
            
            // 下载速度
            if (task.downloadSpeed > 0) {
                tvSpeed.setText(formatSpeed(task.downloadSpeed));
            } else {
                tvSpeed.setText("");
            }
            
            // 状态信息
            String stateText = getStateText(task);
            String sizeText = formatSize(task.downloadedSize) + " / " + formatSize(task.totalSize);
            if (task.peers > 0) {
                sizeText += " | " + task.peers + " peers";
            }
            tvInfo.setText(stateText + " | " + sizeText);
            
            // 按钮状态
            if (task.state == 1) {
                btnPause.setText("暂停");
            } else {
                btnPause.setText("继续");
            }
            
            btnPause.setOnClickListener(v -> {
                if (downloadManager != null) {
                    if (task.state == 1) {
                        downloadManager.pauseTask(task);
                    } else {
                        downloadManager.resumeTask(task);
                    }
                }
            });
            
            btnDelete.setOnClickListener(v -> {
                if (downloadManager != null) {
                    downloadManager.removeTask(task, true);
                }
            });
        }
        
        private String getStateText(BTDownloadManager.TorrentTask task) {
            switch (task.state) {
                case 0: return "已暂停";
                case 1: return "下载中";
                case 2: return "已完成";
                case 3: return "错误";
                default: return "等待中";
            }
        }
        
        private String formatSize(long size) {
            if (size <= 0) return "0 B";
            String[] units = {"B", "KB", "MB", "GB", "TB"};
            int i = 0;
            double d = size;
            while (d >= 1024 && i < units.length - 1) {
                d /= 1024;
                i++;
            }
            return String.format(Locale.getDefault(), "%.1f %s", d, units[i]);
        }
        
        private String formatSpeed(long speed) {
            if (speed <= 0) return "";
            String[] units = {"B/s", "KB/s", "MB/s", "GB/s"};
            int i = 0;
            double d = speed;
            while (d >= 1024 && i < units.length - 1) {
                d /= 1024;
                i++;
            }
            return String.format(Locale.getDefault(), "↓ %.1f %s", d, units[i]);
        }
    }
}
