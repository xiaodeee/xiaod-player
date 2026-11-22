package com.xiaod.player;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.VH> {

    public interface OnSongClickListener {
        void onSongClick(int position);
    }

    private final List<Song> data = new ArrayList<>();
    private final OnSongClickListener listener;
    private int currentIndex = -1;

    public SongAdapter(OnSongClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<Song> songs) {
        data.clear();
        if (songs != null) {
            data.addAll(songs);
        }
        currentIndex = -1;
        notifyDataSetChanged();
    }

    public Song getItem(int position) {
        return data.get(position);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int index) {
        if (currentIndex == index) {
            return;
        }
        int old = currentIndex;
        currentIndex = index;
        if (old >= 0 && old < data.size()) {
            notifyItemChanged(old);
        }
        if (index >= 0 && index < data.size()) {
            notifyItemChanged(index);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_song, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Song song = data.get(position);
        holder.title.setText(song.title);
        holder.info.setText(song.artist + " · " + song.album);
        holder.duration.setText(formatDuration(song.duration));

        boolean isCurrent = position == currentIndex;
        holder.indicator.setVisibility(isCurrent ? View.VISIBLE : View.INVISIBLE);
        holder.title.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
                isCurrent ? R.color.accent : R.color.text_primary));

        holder.itemView.setOnClickListener(v -> {
            int clicked = holder.getAdapterPosition();
            if (listener != null && clicked != RecyclerView.NO_POSITION) {
                listener.onSongClick(clicked);
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static String formatDuration(long ms) {
        long totalSeconds = Math.max(0, ms / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes >= 60) {
            long hours = minutes / 60;
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes % 60, seconds);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    public static class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView info;
        final TextView duration;
        final View indicator;

        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_song_title);
            info = itemView.findViewById(R.id.tv_song_info);
            duration = itemView.findViewById(R.id.tv_song_duration);
            indicator = itemView.findViewById(R.id.v_indicator);
        }
    }
}
