package com.codestoon.hamsepar;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ClientListAdapter extends RecyclerView.Adapter<ClientListAdapter.ClientViewHolder> {

    private List<ClientItem> clients = new ArrayList<>();

    public void setClients(List<ClientItem> clients) {
        this.clients = clients;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ClientViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_client, parent, false);
        return new ClientViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ClientViewHolder holder, int position) {
        ClientItem client = clients.get(position);
        holder.bind(client);
    }

    @Override
    public int getItemCount() {
        return clients.size();
    }

    static class ClientViewHolder extends RecyclerView.ViewHolder {
        TextView txtName, txtIp, txtOs;

        ClientViewHolder(@NonNull View itemView) {
            super(itemView);
            txtName = itemView.findViewById(R.id.txtClientName);
            txtIp = itemView.findViewById(R.id.txtClientIp);
            txtOs = itemView.findViewById(R.id.txtClientOs);
        }

        void bind(ClientItem client) {
            txtName.setText("👤 " + client.getName());
            txtIp.setText("📡 " + client.getIp());
            txtOs.setText("💻 " + client.getOs());
        }
    }
}