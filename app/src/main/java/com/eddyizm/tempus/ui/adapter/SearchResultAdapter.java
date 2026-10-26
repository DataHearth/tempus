package com.eddyizm.tempus.ui.adapter;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.ItemSearchResultBinding;
import com.eddyizm.tempus.glide.CustomGlideRequest;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.subsonic.models.ArtistID3;
import com.eddyizm.tempus.util.Constants;
import com.google.android.material.shape.RelativeCornerSize;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.Collections;
import java.util.List;

/**
 * Search result rows for artists or albums. Each row names its kind, so an album never passes for a
 * song in a mixed result list.
 */
public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {
    private final ClickCallback click;
    private final boolean artists;

    private List<?> items = Collections.emptyList();

    private SearchResultAdapter(ClickCallback click, boolean artists) {
        this.click = click;
        this.artists = artists;
    }

    public static SearchResultAdapter forArtists(ClickCallback click) {
        return new SearchResultAdapter(click, true);
    }

    public static SearchResultAdapter forAlbums(ClickCallback click) {
        return new SearchResultAdapter(click, false);
    }

    public static String artistKind(Context context) {
        return context.getString(R.string.search_result_artist);
    }

    public static String albumKind(Context context, AlbumID3 album) {
        String detail = album.getYear() > 0
                ? context.getString(R.string.browse_meta_formatter, String.valueOf(album.getYear()), album.getArtist())
                : album.getArtist();
        return context.getString(R.string.search_result_album, detail);
    }

    public void setItems(List<?> items) {
        this.items = items != null ? items : Collections.emptyList();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSearchResultBinding view = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        if (artists) {
            view.searchResultCover.setShapeAppearanceModel(ShapeAppearanceModel.builder().setAllCornerSizes(new RelativeCornerSize(0.5f)).build());
        }
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Context context = holder.itemView.getContext();

        if (artists) {
            ArtistID3 artist = (ArtistID3) items.get(position);
            holder.item.searchResultTitle.setText(artist.getName());
            holder.item.searchResultSubtitle.setText(artistKind(context));
            CustomGlideRequest.Builder
                    .from(context, artist.getCoverArtId(), CustomGlideRequest.ResourceType.Artist)
                    .build()
                    .into(holder.item.searchResultCover);
        } else {
            AlbumID3 album = (AlbumID3) items.get(position);
            holder.item.searchResultTitle.setText(album.getName());
            holder.item.searchResultSubtitle.setText(albumKind(context, album));
            CustomGlideRequest.Builder
                    .from(context, album.getCoverArtId(), CustomGlideRequest.ResourceType.Album)
                    .build()
                    .into(holder.item.searchResultCover);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        final ItemSearchResultBinding item;

        ViewHolder(ItemSearchResultBinding item) {
            super(item.getRoot());
            this.item = item;

            itemView.setOnClickListener(v -> {
                Bundle bundle = bundleFor(getBindingAdapterPosition());
                if (bundle == null) return;
                if (artists) click.onArtistClick(bundle);
                else click.onAlbumClick(bundle);
            });

            itemView.setOnLongClickListener(v -> {
                Bundle bundle = bundleFor(getBindingAdapterPosition());
                if (bundle == null) return false;
                if (artists) click.onArtistLongClick(bundle);
                else click.onAlbumLongClick(bundle);
                return true;
            });
        }

        private Bundle bundleFor(int position) {
            if (position == RecyclerView.NO_POSITION) return null;

            Bundle bundle = new Bundle();
            if (artists) {
                bundle.putParcelable(Constants.ARTIST_OBJECT, ((ArtistID3) items.get(position)).strippedForNav());
                bundle.putBoolean(Constants.MEDIA_MIX, false);
                bundle.putBoolean(Constants.MEDIA_BEST_OF, false);
            } else {
                bundle.putParcelable(Constants.ALBUM_OBJECT, ((AlbumID3) items.get(position)).strippedForNav());
            }
            return bundle;
        }
    }
}
