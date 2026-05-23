package com.codestoon.hamsepar;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.FileViewHolder> {

    private List<FileItem> files = new ArrayList<>();
    private OnFileActionListener listener;

    public interface OnFileActionListener {
        void onDownload(String fileName);
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
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        FileItem file = files.get(position);
        holder.bind(file, listener);
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView txtIcon, txtName, txtSize;
        Button btnDownload, btnDelete;

        FileViewHolder(@NonNull View itemView) {
            super(itemView);
            txtIcon = itemView.findViewById(R.id.txtFileIcon);
            txtName = itemView.findViewById(R.id.txtFileName);
            txtSize = itemView.findViewById(R.id.txtFileSize);
            btnDownload = itemView.findViewById(R.id.btnDownload);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        void bind(FileItem file, OnFileActionListener listener) {
            txtIcon.setText(getFileIcon(file.getName()));
            txtName.setText(file.getName());
            txtSize.setText(formatSize(file.getSize()));

            btnDownload.setOnClickListener(v -> {
                if (listener != null) listener.onDownload(file.getName());
            });

            btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDelete(file.getName());
            });
        }

        private String getFileIcon(String fileName) {
            String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
            switch (ext) {
                case "jpg": case "jpeg": case "png": case "gif": return "🖼️";
                case "mp4": case "mkv": case "avi": return "🎬";
                case "mp3": case "wav": case "flac": return "🎵";
                case "pdf": return "📕";
                case "doc": case "docx": return "📄";
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
    }
}