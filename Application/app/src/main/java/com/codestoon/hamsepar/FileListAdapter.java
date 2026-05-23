package com.codestoon.hamsepar;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.FileViewHolder> {

    private List<FileItem> files = new ArrayList<>();
    private OnFileActionListener listener;
    private Context context;

    public interface OnFileActionListener {
        void onOpen(String fileName);
        void onDelete(String fileName);
    }

    public void setListener(OnFileActionListener listener) {
        this.listener = listener;
    }

    public void setFiles(List<FileItem> files) {
        this.files = files;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        FileItem file = files.get(position);
        holder.bind(file, listener, context);
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView txtIcon, txtName, txtSize;
        ImageView imgDelete;  // تغییر از Button به ImageView

        FileViewHolder(@NonNull View itemView) {
            super(itemView);
            txtIcon = itemView.findViewById(R.id.txtFileIcon);
            txtName = itemView.findViewById(R.id.txtFileName);
            txtSize = itemView.findViewById(R.id.txtFileSize);
            imgDelete = itemView.findViewById(R.id.imgDelete);
        }

        void bind(FileItem file, OnFileActionListener listener, Context context) {
            txtIcon.setText(getFileIcon(file.getName()));
            txtName.setText(file.getName());
            txtSize.setText(formatSize(file.getSize()));

            // کلیک روی کل آیتم - باز کردن فایل
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onOpen(file.getName());
                } else {
                    openFileDirectly(context, file.getName());
                }
            });

            // کلیک روی آیکون حذف
            imgDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDelete(file.getName());
                }
            });
        }

        private String getFileIcon(String fileName) {
            String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
            switch (ext) {
                case "jpg": case "jpeg": case "png": case "gif": case "webp": return "🖼️";
                case "mp4": case "mkv": case "avi": case "3gp": return "🎬";
                case "mp3": case "wav": case "flac": case "aac": return "🎵";
                case "pdf": return "📕";
                case "doc": case "docx": return "📄";
                case "xls": case "xlsx": return "📊";
                case "ppt": case "pptx": return "📽️";
                case "txt": return "📝";
                case "zip": case "rar": case "7z": return "🗜️";
                case "apk": return "📦";
                default: return "📁";
            }
        }

        private String formatSize(long bytes) {
            if (bytes <= 0) return "0 B";
            long absoluteBytes = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
            CharacterIterator ci = new StringCharacterIterator("KMGTPE");
            double value = absoluteBytes;
            for (int i = 0; i < 5; i++) {
                if (value < 1024) {
                    return String.format(Locale.getDefault(), "%.1f %cB", value, ci.current());
                }
                value /= 1024;
                ci.next();
            }
            return String.format(Locale.getDefault(), "%.1f %cB", value, ci.current());
        }

        private void openFileDirectly(Context context, String fileName) {
            File sharedFolder = new File(context.getExternalFilesDir(null), "SharedFiles");
            File file = new File(sharedFolder, fileName);

            if (!file.exists()) {
                Toast.makeText(context, "فایل وجود ندارد", Toast.LENGTH_SHORT).show();
                return;
            }

            Uri fileUri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", file);

            String mimeType = getMimeType(fileName);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                context.startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(context, "برنامه‌ای برای باز کردن این فایل یافت نشد", Toast.LENGTH_LONG).show();
            }
        }

        private String getMimeType(String fileName) {
            String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);

            switch (extension) {
                case "jpg": case "jpeg": return "image/jpeg";
                case "png": return "image/png";
                case "gif": return "image/gif";
                case "webp": return "image/webp";
                case "mp4": return "video/mp4";
                case "mkv": return "video/x-matroska";
                case "avi": return "video/x-msvideo";
                case "3gp": return "video/3gpp";
                case "mp3": return "audio/mpeg";
                case "wav": return "audio/wav";
                case "flac": return "audio/flac";
                case "pdf": return "application/pdf";
                case "doc": return "application/msword";
                case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "xls": return "application/vnd.ms-excel";
                case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                case "ppt": return "application/vnd.ms-powerpoint";
                case "pptx": return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                case "txt": return "text/plain";
                case "zip": return "application/zip";
                case "apk": return "application/vnd.android.package-archive";
                default: return "*/*";
            }
        }
    }
}