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

    private List<AppItem> displayedApps = new ArrayList<>();
    private OnSelectionChangedListener selectionChangedListener;
    private String searchQuery = "";
    private List<AppItem> allApps = new ArrayList<>();

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionChangedListener = listener;
    }

    public void setAppList(List<AppItem> apps, String query) {
        this.allApps = apps;
        this.searchQuery = query != null ? query.toLowerCase().trim() : "";
        filterAndDisplay();
    }

    public void setSearchQuery(String query) {
        this.searchQuery = query != null ? query.toLowerCase().trim() : "";
        filterAndDisplay();
    }

    private void filterAndDisplay() {
        displayedApps.clear();

        for (AppItem app : allApps) {
            boolean matchesSearch = searchQuery.isEmpty() ||
                    app.getAppName().toLowerCase().contains(searchQuery) ||
                    app.getPackageName().toLowerCase().contains(searchQuery);

            if (matchesSearch) {
                displayedApps.add(app);
            }
        }

        notifyDataSetChanged();
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(getSelectedCount());
        }
    }

    public List<AppItem> getSelectedApps() {
        List<AppItem> selected = new ArrayList<>();
        for (AppItem app : displayedApps) {
            if (app.isSelected()) {
                selected.add(app);
            }
        }
        return selected;
    }

    public void selectAll() {
        for (AppItem app : displayedApps) {
            app.setSelected(true);
        }
        notifyDataSetChanged();
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(getSelectedCount());
        }
    }

    public void deselectAll() {
        for (AppItem app : displayedApps) {
            app.setSelected(false);
        }
        notifyDataSetChanged();
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(0);
        }
    }

    public int getSelectedCount() {
        int count = 0;
        for (AppItem app : displayedApps) {
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
        AppItem app = displayedApps.get(position);
        holder.bind(app, position);
    }

    @Override
    public int getItemCount() {
        return displayedApps.size();
    }

    class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView imgIcon;
        TextView txtAppName;
        TextView txtPackageName;
        TextView txtAppType;
        CheckBox chkSelect;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            imgIcon = itemView.findViewById(R.id.imgAppIcon);
            txtAppName = itemView.findViewById(R.id.txtAppName);
            txtPackageName = itemView.findViewById(R.id.txtPackageName);
            txtAppType = itemView.findViewById(R.id.txtAppType);
            chkSelect = itemView.findViewById(R.id.chkSelect);
        }

        void bind(AppItem app, int position) {
            imgIcon.setImageDrawable(app.getIcon());
            txtAppName.setText(app.getAppName());
            txtPackageName.setText(app.getPackageName());
            chkSelect.setChecked(app.isSelected());

            if (app.isSystemApp()) {
                txtAppType.setText("سیستمی");
                txtAppType.setVisibility(View.VISIBLE);
                txtAppType.setBackgroundColor(0xFFFEF3C7);
                txtAppType.setTextColor(0xFFD97706);
            } else {
                txtAppType.setText("کاربر");
                txtAppType.setVisibility(View.VISIBLE);
                txtAppType.setBackgroundColor(0xFFD1FAE5);
                txtAppType.setTextColor(0xFF10B981);
            }

            chkSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                app.setSelected(isChecked);
                if (selectionChangedListener != null) {
                    selectionChangedListener.onSelectionChanged(getSelectedCount());
                }
            });

            itemView.setOnClickListener(v -> {
                chkSelect.setChecked(!chkSelect.isChecked());
            });
        }
    }
}