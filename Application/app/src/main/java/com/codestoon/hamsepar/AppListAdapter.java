package com.codestoon.hamsepar;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.AppViewHolder> {

    private List<AppItem> appList = new ArrayList<>();
    private OnAppSelectionListener listener;

    public interface OnAppSelectionListener {
        void onSelectionChanged(int selectedCount);
    }

    public void setListener(OnAppSelectionListener listener) {
        this.listener = listener;
    }

    public void setAppList(List<AppItem> apps) {
        this.appList = apps;
        notifyDataSetChanged();
    }

    public List<AppItem> getSelectedApps() {
        List<AppItem> selected = new ArrayList<>();
        for (AppItem app : appList) {
            if (app.isSelected()) {
                selected.add(app);
            }
        }
        return selected;
    }

    public void selectAll() {
        for (AppItem app : appList) {
            app.setSelected(true);
        }
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged(getSelectedCount());
    }

    public void deselectAll() {
        for (AppItem app : appList) {
            app.setSelected(false);
        }
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged(0);
    }

    public int getSelectedCount() {
        int count = 0;
        for (AppItem app : appList) {
            if (app.isSelected()) count++;
        }
        return count;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppItem app = appList.get(position);
        holder.bind(app);
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView imgIcon;
        TextView txtAppName;
        TextView txtPackageName;
        CheckBox chkSelect;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            imgIcon = itemView.findViewById(R.id.imgAppIcon);
            txtAppName = itemView.findViewById(R.id.txtAppName);
            txtPackageName = itemView.findViewById(R.id.txtPackageName);
            chkSelect = itemView.findViewById(R.id.chkSelect);
        }

        void bind(AppItem app) {
            imgIcon.setImageDrawable(app.getIcon());
            txtAppName.setText(app.getAppName());
            txtPackageName.setText(app.getPackageName());
            chkSelect.setChecked(app.isSelected());

            chkSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                app.setSelected(isChecked);
                if (listener != null) listener.onSelectionChanged(getSelectedCount());
            });

            itemView.setOnClickListener(v -> {
                chkSelect.setChecked(!chkSelect.isChecked());
            });
        }
    }
}