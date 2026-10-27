package com.eddyizm.tempus.ui.adapter;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.ItemLibraryGenreBinding;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.subsonic.models.Genre;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.MusicUtil;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class GenreAdapter extends RecyclerView.Adapter<GenreAdapter.ViewHolder> {
    private final ClickCallback click;

    private List<Genre> genres;

    public GenreAdapter(ClickCallback click) {
        this.click = click;
        this.genres = Collections.emptyList();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLibraryGenreBinding view = ItemLibraryGenreBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Genre genre = genres.get(position);

        holder.item.genreLabel.setText(genre.getGenre());
        holder.item.cardView.setCardBackgroundColor(tintOf(genre.getGenre(), isNight(holder.itemView)));
        holder.item.genreMetaLabel.setText(holder.itemView.getContext().getResources()
                .getQuantityString(R.plurals.player_queue_song_count, genre.getSongCount(), genre.getSongCount()));
    }

    /**
     * The server gives a genre no colour, so its name picks a stable hue for the design's tinted card.
     */
    private static int tintOf(String name, boolean night) {
        int hue = Math.floorMod(name != null ? name.toLowerCase(Locale.ROOT).hashCode() : 0, 360);
        return night ? YearAdapter.oklch(0.30, 0.05, hue) : YearAdapter.oklch(0.93, 0.035, hue);
    }

    private static boolean isNight(View view) {
        int mode = view.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public int getItemCount() {
        return genres.size();
    }

    public Genre getItem(int position) {
        return genres.get(position);
    }

    public void setItems(List<Genre> genres) {
        this.genres = genres;
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemLibraryGenreBinding item;

        ViewHolder(ItemLibraryGenreBinding item) {
            super(item.getRoot());

            this.item = item;

            itemView.setOnClickListener(v -> onClick());
        }

        private void onClick() {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.MEDIA_BY_GENRE, Constants.MEDIA_BY_GENRE);
            bundle.putParcelable(Constants.GENRE_OBJECT, genres.get(getBindingAdapterPosition()));

            click.onGenreClick(bundle);
        }
    }
}
