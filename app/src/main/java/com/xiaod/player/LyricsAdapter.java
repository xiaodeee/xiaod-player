package com.xiaod.player;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class LyricsAdapter extends RecyclerView.Adapter<LyricsAdapter.VH> {

    private final List<LrcParser.Line> lines = new ArrayList<>();
    private int highlighted = -1;

    public void submit(List<LrcParser.Line> newLines) {
        lines.clear();
        if (newLines != null) {
            lines.addAll(newLines);
        }
        highlighted = -1;
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public int getHighlighted() {
        return highlighted;
    }

    public int findIndex(long positionMs) {
        return LrcParser.indexAt(lines, positionMs);
    }

    public void setHighlighted(int index) {
        if (highlighted == index) {
            return;
        }
        int old = highlighted;
        highlighted = index;
        if (old >= 0 && old < lines.size()) {
            notifyItemChanged(old);
        }
        if (index >= 0 && index < lines.size()) {
            notifyItemChanged(index);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView view = (TextView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_lyric, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        TextView view = holder.text;
        view.setText(lines.get(position).text);
        boolean active = position == highlighted;
        view.setTextColor(ContextCompat.getColor(view.getContext(),
                active ? R.color.accent : R.color.text_secondary));
        view.setTextSize(active ? 20f : 16f);
        view.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    public static class VH extends RecyclerView.ViewHolder {
        final TextView text;

        VH(@NonNull TextView itemView) {
            super(itemView);
            text = itemView;
        }
    }
}
