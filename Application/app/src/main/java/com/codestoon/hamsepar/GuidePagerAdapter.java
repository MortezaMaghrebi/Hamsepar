package com.codestoon.hamsepar;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class GuidePagerAdapter extends RecyclerView.Adapter<GuidePagerAdapter.GuideViewHolder> {

    private List<GuideItem> guideItems;

    public static class GuideItem {
        public String iconEmoji;  // تغییر: به جای imageResId
        public String title;
        public String description;

        public GuideItem(String iconEmoji, String title, String description) {
            this.iconEmoji = iconEmoji;
            this.title = title;
            this.description = description;
        }
    }

    public GuidePagerAdapter(List<GuideItem> guideItems) {
        this.guideItems = guideItems;
    }

    @NonNull
    @Override
    public GuideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_guide_page, parent, false);
        return new GuideViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GuideViewHolder holder, int position) {
        GuideItem item = guideItems.get(position);
        holder.txtIconGuide.setText(item.iconEmoji);
        holder.txtStepTitle.setText(item.title);
        holder.txtStepDescription.setText(item.description);
    }

    @Override
    public int getItemCount() {
        return guideItems.size();
    }

    static class GuideViewHolder extends RecyclerView.ViewHolder {
        TextView txtIconGuide, txtStepTitle, txtStepDescription;

        GuideViewHolder(@NonNull View itemView) {
            super(itemView);
            txtIconGuide = itemView.findViewById(R.id.txtIconGuide);
            txtStepTitle = itemView.findViewById(R.id.txtStepTitle);
            txtStepDescription = itemView.findViewById(R.id.txtStepDescription);
        }
    }
}