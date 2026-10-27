package com.eddyizm.tempus.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.eddyizm.tempus.databinding.ItemAlbumCarouselBinding;
import com.eddyizm.tempus.glide.CustomGlideRequest;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.Preferences;

import java.util.Collections;
import java.util.List;

public class AlbumCarouselAdapter extends RecyclerView.Adapter<AlbumCarouselAdapter.ViewHolder> {
    private final ClickCallback click;
    private List<AlbumID3> albums;
    private boolean showArtist;

    public AlbumCarouselAdapter(ClickCallback click, boolean showArtist) {
        this.click = click;
        this.albums = Collections.emptyList();
        this.showArtist = showArtist;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAlbumCarouselBinding view = ItemAlbumCarouselBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AlbumID3 album = albums.get(position);

        // Releases already carry the year as their subtitle; only "Appears on" tiles add it to the name.
        if (showArtist && Preferences.getAlbumYearVisible() && album.getYear() != 0) {
            holder.item.albumNameLabel.setText(album.getName() + " (" + album.getYear() + ")");
        } else {
            holder.item.albumNameLabel.setText(album.getName());
        }

        String subtitle = showArtist ? album.getArtist() : album.getYear() != 0 ? String.valueOf(album.getYear()) : null;
        holder.item.artistNameLabel.setText(subtitle);
        holder.item.artistNameLabel.setVisibility(subtitle != null && !subtitle.isEmpty() ? View.VISIBLE : View.GONE);

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), album.getCoverArtId(), CustomGlideRequest.ResourceType.Album)
                .build()
                .into(holder.item.albumCoverImageView);
    }

    @Override
    public int getItemCount() {
        return albums.size();
    }

    public void setItems(List<AlbumID3> albums) {
        this.albums = albums;
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemAlbumCarouselBinding item;

        ViewHolder(ItemAlbumCarouselBinding item) {
            super(item.getRoot());
            this.item = item;

            itemView.setOnClickListener(v -> {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.ALBUM_OBJECT, albums.get(getBindingAdapterPosition()).strippedForNav());
                click.onAlbumClick(bundle);
            });

            itemView.setOnLongClickListener(v -> {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.ALBUM_OBJECT, albums.get(getBindingAdapterPosition()).strippedForNav());
                click.onAlbumLongClick(bundle);
                return true;
            });
        }
    }
}
