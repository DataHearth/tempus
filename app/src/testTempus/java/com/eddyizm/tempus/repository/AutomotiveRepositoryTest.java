package com.eddyizm.tempus.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.media.browse.MediaBrowser;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.session.MediaConstants;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.subsonic.models.AlbumWithSongsID3;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.DiscTitle;
import com.eddyizm.tempus.subsonic.models.InternetRadioStation;
import com.eddyizm.tempus.util.ConstantsAA;
import com.eddyizm.tempus.util.MappingUtil;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AutomotiveRepositoryTest {

    private static InternetRadioStation station(String name) {
        return new InternetRadioStation(null, name, null, null, null, null);
    }

    private static List<String> names(List<InternetRadioStation> stations) {
        return stations.stream().map(InternetRadioStation::getName).collect(Collectors.toList());
    }

    private static MediaItem mediaItemWithDisc(int disc) {
        return new MediaItem.Builder()
                .setMediaId("track-" + disc)
                .setMediaMetadata(
                        new MediaMetadata.Builder()
                                .setDiscNumber(disc)
                                .build())
                .build();
    }

    @Test
    public void localRadiosShownWhenServerHasNone() {
        // #810: a server with zero radio stations must not hide the user's locally-added radios.
        List<InternetRadioStation> result = AutomotiveRepository.mergeAndSortRadioStations(
                new ArrayList<>(),
                Arrays.asList(station("Local FM"), station("Another Local")));

        assertEquals(Arrays.asList("Another Local", "Local FM"), names(result));
    }

    @Test
    public void serverAndLocalRadiosAreMergedAndSortedCaseInsensitively() {
        List<InternetRadioStation> result = AutomotiveRepository.mergeAndSortRadioStations(
                Arrays.asList(station("zeta"), station("alpha")),
                Arrays.asList(station("Beta")));

        assertEquals(Arrays.asList("alpha", "Beta", "zeta"), names(result));
    }

    @Test
    public void nullStationNamesSortFirstWithoutCrashing() {
        List<InternetRadioStation> result = AutomotiveRepository.mergeAndSortRadioStations(
                Arrays.asList(station("b")),
                Arrays.asList(station(null), station("a")));

        assertEquals(Arrays.asList(null, "a", "b"), names(result));
    }

    @Test
    public void multiDiscAlbumGroupsTracksByDiscNumber() {
        List<MediaItem> mediaItems = Arrays.asList(
                mediaItemWithDisc(1),
                mediaItemWithDisc(2)
        );

        Context context = mock(Context.class);

        when(context.getString(R.string.disc_titleless, "1")).thenReturn("Disc 1");
        when(context.getString(R.string.disc_titleless, "2")).thenReturn("Disc 2");

        var result = AutomotiveRepository.getMultiDiscTitles(context, new ArrayList<>(), mediaItems);

        assertEquals("Disc 1", result.get(1));
        assertEquals("Disc 2", result.get(2));
    }

    @Test
    public void singleDiscAlbumDoesNotGroupTracks() {
        List<MediaItem> mediaItems = Arrays.asList(
                mediaItemWithDisc(1),
                mediaItemWithDisc(1)
        );

        var result = AutomotiveRepository.getMultiDiscTitles(
                mock(Context.class),
                new ArrayList<>(),
                mediaItems);

        assertTrue(result.isEmpty());
    }

    @Test
    public void multiDiscAlbumUsesDiscTitles() {
        List<MediaItem> mediaItems = Arrays.asList(
                mediaItemWithDisc(1),
                mediaItemWithDisc(2)
        );

        List<DiscTitle> discTitles = Arrays.asList(
                new DiscTitle(1, "Dawn to Dusk"),
                new DiscTitle(2, "Twilight to Starlight")
        );

        Context context = mock(Context.class);

        when(context.getString(R.string.disc_titlefull, "1", "Dawn to Dusk"))
                .thenReturn("Disc 1 - Dawn to Dusk");
        when(context.getString(R.string.disc_titlefull, "2", "Twilight to Starlight"))
                .thenReturn("Disc 2 - Twilight to Starlight");

        var result = AutomotiveRepository.getMultiDiscTitles(context, discTitles, mediaItems);

        assertEquals("Disc 1 - Dawn to Dusk", result.get(1));
        assertEquals("Disc 2 - Twilight to Starlight", result.get(2));
    }

    private static List<String> repeat(String name, int times) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < times; i++) names.add(name + i);
        return names;
    }

    @Test
    public void letterKeyFoldsAccentsAndPutsNonLettersUnderHash() {
        assertEquals("E", AutomotiveRepository.letterKey("Édith Piaf"));
        assertEquals("A", AutomotiveRepository.letterKey("abba"));
        assertEquals("#", AutomotiveRepository.letterKey("10cc"));
        assertEquals("#", AutomotiveRepository.letterKey(""));
        assertEquals("#", AutomotiveRepository.letterKey(null));
    }

    @Test
    public void lettersMergeIntoRangesOfAtLeastSixItems() {
        List<String> names = new ArrayList<>();
        names.addAll(repeat("A", 2));
        names.addAll(repeat("B", 2));
        names.addAll(repeat("C", 3));
        names.addAll(repeat("D", 6));
        names.addAll(repeat("G", 4));
        names.addAll(repeat("M", 3));

        List<String> titles = AutomotiveRepository.letterRangeTitles(names);

        assertEquals(names.size(), titles.size());
        assertEquals("A–C", titles.get(0));
        assertEquals("A–C", titles.get(6));
        assertEquals("D", titles.get(7));
        assertEquals("G–M", titles.get(13));
        assertEquals("G–M", titles.get(19));
    }

    @Test
    public void smallTrailingRangeJoinsThePreviousOne() {
        List<String> names = new ArrayList<>();
        names.addAll(repeat("A", 6));
        names.addAll(repeat("Z", 2));

        assertEquals(Collections.nCopies(8, "A–Z"), AutomotiveRepository.letterRangeTitles(names));
    }

    @Test
    public void rangesGrowWithTheLibrary() {
        List<String> names = new ArrayList<>();
        for (char letter = 'A'; letter <= 'Z'; letter++) names.addAll(repeat(String.valueOf(letter), 10));

        long groups = AutomotiveRepository.letterRangeTitles(names).stream().distinct().count();

        assertTrue(groups <= 8);
        assertTrue(groups >= 6);
    }

    @Test
    public void groupByLetterRangeSortsByFoldedLetter() {
        List<MediaItem> items = Arrays.asList(titled("zed"), titled("Édith"), titled("10cc"), titled("alpha"));

        List<String> titles = AutomotiveRepository.groupByLetterRange(items).stream()
                .map(item -> item.mediaMetadata.title.toString())
                .collect(Collectors.toList());

        assertEquals(Arrays.asList("10cc", "alpha", "Édith", "zed"), titles);
    }

    @Test
    public void everyNameFallsInExactlyOneLetterRange() {
        List<String> names = new ArrayList<>(Arrays.asList("10cc", "Édith", "zed"));
        for (char letter = 'A'; letter <= 'Z'; letter++) names.addAll(repeat(String.valueOf(letter), 30));

        Map<String, Integer> ranges = AutomotiveRepository.letterRanges(names);

        assertEquals(names.size(), ranges.values().stream().mapToInt(Integer::intValue).sum());
        for (String name : names) {
            long matching = ranges.keySet().stream().filter(range -> AutomotiveRepository.inLetterRange(name, range)).count();
            assertEquals(name, 1, matching);
        }
        for (Map.Entry<String, Integer> range : ranges.entrySet()) {
            long inRange = names.stream().filter(name -> AutomotiveRepository.inLetterRange(name, range.getKey())).count();
            assertEquals(range.getKey(), (long) range.getValue(), inRange);
        }
    }

    @Test
    public void letterRangesFollowTheAlphabet() {
        List<String> names = new ArrayList<>();
        for (char letter = 'Z'; letter >= 'A'; letter--) names.addAll(repeat(String.valueOf(letter), 30));
        names.addAll(repeat("1", 30));

        List<String> ranges = new ArrayList<>(AutomotiveRepository.letterRanges(names).keySet());

        assertTrue(ranges.get(0).startsWith("#"));
        assertTrue(ranges.get(ranges.size() - 1).endsWith("Z"));
        assertTrue(ranges.size() <= 8);
    }

    @Test
    public void singleLetterRangeHoldsOnlyThatLetter() {
        assertTrue(AutomotiveRepository.inLetterRange("Mogwai", "M"));
        assertFalse(AutomotiveRepository.inLetterRange("Nirvana", "M"));
        assertTrue(AutomotiveRepository.inLetterRange("10cc", "#–C"));
        assertTrue(AutomotiveRepository.inLetterRange("Édith", "D–F"));
    }

    @Test
    public void episodeWithAResumePointIsPartiallyPlayedEvenIfFinishedBefore() {
        assertEquals(MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED,
                AutomotiveRepository.completionStatus(60_000L, true));
    }

    @Test
    public void finishedEpisodeIsFullyPlayed() {
        assertEquals(MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED,
                AutomotiveRepository.completionStatus(null, true));
    }

    @Test
    public void untouchedEpisodeIsNotPlayed() {
        assertEquals(MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED,
                AutomotiveRepository.completionStatus(null, false));
        assertEquals(MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED,
                AutomotiveRepository.completionStatus(0L, false));
    }

    private static MediaItem titled(String title) {
        return new MediaItem.Builder()
                .setMediaId(title)
                .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build())
                .build();
    }
}
