package com.vypeensoft.friendtracker.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.vypeensoft.friendtracker.R;
import com.vypeensoft.friendtracker.model.GroupRoom;

import java.util.List;

public class GroupRoomAdapter extends RecyclerView.Adapter<GroupRoomAdapter.ViewHolder> {

    private List<GroupRoom> rooms;
    private OnRoomActionListener listener;

    public interface OnRoomActionListener {
        void onRoomSelected(int position);
        void onRoomDeleted(int position);
        void onRoomEdited(int position);
        void onRoomLongClicked(int position, android.view.View view);
    }

    public GroupRoomAdapter(List<GroupRoom> rooms, OnRoomActionListener listener) {
        this.rooms = rooms;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group_room, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GroupRoom room = rooms.get(position);
        holder.textName.setText(room.getName());
        holder.textRoomId.setText(room.getRoomId());
        holder.radioActive.setChecked(room.isActive());

        View.OnClickListener selectListener = v -> {
            if (listener != null) listener.onRoomSelected(position);
        };

        holder.itemView.setOnClickListener(selectListener);
        holder.radioActive.setOnClickListener(selectListener);

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onRoomLongClicked(position, v);
            return true;
        });

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onRoomEdited(position);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onRoomDeleted(position);
        });
    }

    @Override
    public int getItemCount() {
        return rooms.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        RadioButton radioActive;
        TextView textName, textRoomId;
        ImageButton btnDelete, btnEdit;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            radioActive = itemView.findViewById(R.id.radio_active);
            textName = itemView.findViewById(R.id.text_group_name);
            textRoomId = itemView.findViewById(R.id.text_room_id);
            btnDelete = itemView.findViewById(R.id.btn_delete);
            btnEdit = itemView.findViewById(R.id.btn_edit);
        }
    }
}
