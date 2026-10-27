package com.eddyizm.tempus.ui.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eddyizm.tempus.R

@Composable
fun PlayerQueueHeader(
    songCount: Int,
    totalDurationSeconds: Long,
    upcomingSongsCount: Int,
    isSyncEnabled: Boolean,
    onClearUpcoming: () -> Unit,
    onShuffleUpcoming: () -> Unit,
    onSaveToPlaylist: () -> Unit,
    onDownloadAll: () -> Unit,
    onLoadSavedQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val (hours, minutes, seconds) = remember(totalDurationSeconds) {
        Triple(
            totalDurationSeconds / 3600,
            (totalDurationSeconds % 3600) / 60,
            totalDurationSeconds % 60
        )
    }
    val countText = pluralStringResource(R.plurals.player_queue_song_count, songCount, songCount)
    val durationText = when {
        hours > 0 && minutes > 0 -> stringResource(R.string.player_queue_duration_hours_minutes, hours.toInt(), minutes.toInt())
        hours > 0 -> stringResource(R.string.player_queue_duration_hours_short, hours.toInt())
        minutes > 0 -> stringResource(R.string.duration_minutes_short, minutes.toInt())
        else -> stringResource(R.string.player_queue_duration_seconds_short, seconds.toInt())
    }
    val subtitle = if (songCount > 0) {
        "$countText · $durationText"
    } else {
        stringResource(R.string.player_queue_empty)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(width = 32.dp, height = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = QUEUE_MUTED_ALPHA),
                shape = RoundedCornerShape(2.dp)
            ) {}
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.player_queue_playing_next),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, letterSpacing = 0.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            IconButton(
                onClick = onShuffleUpcoming,
                enabled = upcomingSongsCount > 1,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shuffle),
                    contentDescription = stringResource(R.string.player_queue_shuffle_upcoming),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (upcomingSongsCount > 1) 1f else 0.38f)
                )
            }

            IconButton(
                onClick = onClearUpcoming,
                enabled = upcomingSongsCount > 0,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_clear_all),
                    contentDescription = stringResource(R.string.player_queue_clear),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (upcomingSongsCount > 0) 1f else 0.38f)
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.content_description_more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_queue_save_to_playlist)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_playlist_add),
                                contentDescription = null
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onSaveToPlaylist()
                        },
                        enabled = songCount > 0
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_queue_download_all)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_download),
                                contentDescription = null
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDownloadAll()
                        },
                        enabled = songCount > 0
                    )
                    if (isSyncEnabled) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.player_queue_load_queue)) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_bookmark_sync),
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onLoadSavedQueue()
                            }
                        )
                    }
                }
            }
        }
    }
}
