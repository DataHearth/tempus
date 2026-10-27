package com.eddyizm.tempus.ui.adapter;

import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.eddyizm.tempus.databinding.ItemHomeYearBinding;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.util.Constants;

import java.util.Collections;
import java.util.List;

public class YearAdapter extends RecyclerView.Adapter<YearAdapter.ViewHolder> {
    private final ClickCallback click;

    private List<Integer> years;

    public YearAdapter(ClickCallback click) {
        this.click = click;
        this.years = Collections.emptyList();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHomeYearBinding view = ItemHomeYearBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        int year = years.get(position);

        holder.item.yearLabel.setText(Integer.toString(year));
        holder.item.yearArt.setBackground(decadeArt(year));
    }

    private static final int[] DECADE_HUES = {285, 25, 140, 210, 55};

    private static ShapeDrawable decadeArt(int year) {
        int hue = DECADE_HUES[Math.floorMod((2020 - year) / 10, DECADE_HUES.length)];
        int dark = oklch(0.46, 0.09, hue);
        int light = oklch(0.80, 0.11, (hue + 35) % 360);

        // Hard-stop diagonal at the design's 195deg CSS angle: dark from the top edge, light towards the bottom left.
        ShapeDrawable art = new ShapeDrawable(new RectShape());
        art.setShaderFactory(new ShapeDrawable.ShaderFactory() {
            @Override
            public Shader resize(int width, int height) {
                double angle = Math.toRadians(195);
                float dx = (float) Math.sin(angle), dy = (float) -Math.cos(angle);
                float half = (float) (Math.abs(width * Math.sin(angle)) + Math.abs(height * Math.cos(angle))) / 2f;
                float cx = width / 2f, cy = height / 2f;
                return new LinearGradient(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half,
                        new int[]{dark, dark, light, light}, new float[]{0f, 0.54f, 0.54f, 1f}, Shader.TileMode.CLAMP);
            }
        });
        return art;
    }

    static int oklch(double lightness, double chroma, double hueDegrees) {
        double h = Math.toRadians(hueDegrees);
        double a = chroma * Math.cos(h), b = chroma * Math.sin(h);
        double l = Math.pow(lightness + 0.3963377774 * a + 0.2158037573 * b, 3);
        double m = Math.pow(lightness - 0.1055613458 * a - 0.0638541728 * b, 3);
        double s = Math.pow(lightness - 0.0894841775 * a - 1.2914855480 * b, 3);
        int red = srgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s);
        int green = srgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s);
        int blue = srgb(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int srgb(double linear) {
        double c = linear <= 0.0031308 ? 12.92 * linear : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
        return (int) Math.round(Math.max(0, Math.min(1, c)) * 255);
    }

    @Override
    public int getItemCount() {
        return years.size();
    }

    public Integer getItem(int position) {
        return years.get(position);
    }

    public void setItems(List<Integer> years) {
        this.years = years;
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemHomeYearBinding item;

        ViewHolder(ItemHomeYearBinding item) {
            super(item.getRoot());

            this.item = item;

            itemView.setOnClickListener(v -> onClick());
        }

        public void onClick() {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.MEDIA_BY_YEAR, Constants.MEDIA_BY_YEAR);
            bundle.putInt("year_object", years.get(getBindingAdapterPosition()));

            click.onYearClick(bundle);
        }
    }
}
