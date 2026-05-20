package com.vypeensoft.friendtracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.vypeensoft.friendtracker.adapter.GroupRoomAdapter;
import com.vypeensoft.friendtracker.model.GroupRoom;
import com.vypeensoft.friendtracker.util.SettingsPersistenceManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class GroupsRoomsActivity extends AppCompatActivity implements GroupRoomAdapter.OnRoomActionListener {

    public static final String KEY_MATRIX_ROOMS = "matrix_rooms";

    private RecyclerView recyclerView;
    private GroupRoomAdapter adapter;
    private List<GroupRoom> roomList;
    private FloatingActionButton btnAdd;
    private Gson gson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_groups_rooms);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        gson = new Gson();
        loadRooms();

        recyclerView = findViewById(R.id.recycler_rooms);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GroupRoomAdapter(roomList, this);
        recyclerView.setAdapter(adapter);

        btnAdd = findViewById(R.id.btn_add_room);
        btnAdd.setOnClickListener(v -> showRoomDialog(null, null));
    }

    private void loadRooms() {
        SharedPreferences prefs = getSharedPreferences(MapSettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_MATRIX_ROOMS, "");
        if (json.isEmpty()) {
            roomList = new ArrayList<>();
        } else {
            Type type = new TypeToken<ArrayList<GroupRoom>>() {}.getType();
            roomList = gson.fromJson(json, type);
        }
    }

    private void saveRooms() {
        SharedPreferences prefs = getSharedPreferences(MapSettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String json = gson.toJson(roomList);
        prefs.edit().putString(KEY_MATRIX_ROOMS, json).apply();
        SettingsPersistenceManager.exportSettings(this);
    }

    private void showRoomDialog(GroupRoom roomToEdit, Integer position) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_room, null);
        EditText editName = view.findViewById(R.id.edit_room_name);
        EditText editId = view.findViewById(R.id.edit_room_id);

        boolean isEdit = roomToEdit != null;
        if (isEdit) {
            editName.setText(roomToEdit.getName());
            editId.setText(roomToEdit.getRoomId());
        }

        new AlertDialog.Builder(this)
                .setTitle(isEdit ? "Edit Group/Room" : "Add New Group/Room")
                .setView(view)
                .setPositiveButton(isEdit ? "Update" : "Add", (dialog, which) -> {
                    String name = editName.getText().toString().trim();
                    String id = editId.getText().toString().trim();
                    if (!name.isEmpty() && !id.isEmpty()) {
                        if (isEdit) {
                            roomList.get(position).setName(name);
                            roomList.get(position).setRoomId(id);
                        } else {
                            boolean isFirst = roomList.isEmpty();
                            roomList.add(new GroupRoom(name, id, isFirst));
                        }
                        saveRooms();
                        adapter.notifyDataSetChanged();
                    } else {
                        Toast.makeText(this, "Please enter both name and ID", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onRoomSelected(int position) {
        for (int i = 0; i < roomList.size(); i++) {
            roomList.get(i).setActive(i == position);
        }
        saveRooms();
        adapter.notifyDataSetChanged();
        Toast.makeText(this, "Active room updated", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRoomEdited(int position) {
        showRoomDialog(roomList.get(position), position);
    }

    @Override
    public void onRoomLongClicked(int position, View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenu().add("Edit");
        popup.getMenu().add("Copy");
        popup.getMenu().add("Delete");

        popup.setOnMenuItemClickListener(item -> {
            if ("Edit".equals(item.getTitle())) {
                onRoomEdited(position);
                return true;
            } else if ("Copy".equals(item.getTitle())) {
                copyRoom(position);
                return true;
            } else if ("Delete".equals(item.getTitle())) {
                onRoomDeleted(position);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void copyRoom(int position) {
        GroupRoom original = roomList.get(position);
        String baseName = original.getName();
        
        // Remove existing suffix if any to get the real base name
        // Pattern: " - [0-9]+" or "-[0-9]+"
        if (baseName.matches(".*-\\d+$")) {
            baseName = baseName.substring(0, baseName.lastIndexOf('-'));
        }

        String newName = "";
        int suffix = 1;
        boolean unique = false;
        while (!unique) {
            newName = String.format("%s-%02d", baseName, suffix);
            unique = true;
            for (GroupRoom room : roomList) {
                if (room.getName().equalsIgnoreCase(newName)) {
                    unique = false;
                    suffix++;
                    break;
                }
            }
        }

        roomList.add(new GroupRoom(newName, original.getRoomId(), false));
        saveRooms();
        adapter.notifyDataSetChanged();
        Toast.makeText(this, "Room copied", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRoomDeleted(int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Room")
                .setMessage("Are you sure you want to delete this room?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    boolean wasActive = roomList.get(position).isActive();
                    roomList.remove(position);
                    if (wasActive && !roomList.isEmpty()) {
                        roomList.get(0).setActive(true);
                    }
                    saveRooms();
                    adapter.notifyDataSetChanged();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
