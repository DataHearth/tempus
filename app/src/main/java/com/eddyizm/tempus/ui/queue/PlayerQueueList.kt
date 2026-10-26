package com.eddyizm.tempus.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eddyizm.tempus.R
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.util.Preferences
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState

data class QueueSongItem(
    val stableId: Any,
    val song: Child
)

@Composable
fun PlayerQueueList(
    items: List<QueueSongItem>,
    currentIndex: Int,
    isPlaying: Boolean,
    lazyListState: LazyListState,
    reorderableState: ReorderableLazyListState,
    itemContainerColor: Color,
    currentItemColor: Color,
    onTrackClick: (Int, Child) -> Unit,
    onRemoveTrack: (Int, QueueSongItem) -> Unit,
    modifier: Modifier = Modifier,
    downloadedIds: Set<String> = emptySet(),
    externalRefreshTrigger: Any? = null,
    onDragStarted: () -> Unit = {},
    onDragStopped: () -> Unit = {}
) {
    var hasHandledInitialScroll by remember { mutableStateOf(false) }

    // Only scroll to the active track initially upon opening the queue sheet
    LaunchedEffect(currentIndex, items.isNotEmpty()) {
        if (!hasHandledInitialScroll && currentIndex in items.indices) {
            hasHandledInitialScroll = true
            if (lazyListState.firstVisibleItemIndex != currentIndex) {
                lazyListState.scrollToItem(currentIndex)
            }
        }
    }

    val showRating = Preferences.showItemRating()

    if (items.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_playlist),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.player_queue_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        val bottomInsets = WindowInsets.systemBars
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            state = lazyListState,
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = bottomInsets.calculateBottomPadding() + 24.dp
            )
        ) {
            val hasCurrent = currentIndex in items.indices
            val queueRow: @Composable LazyItemScope.(Int, QueueSongItem) -> Unit = { index, queueItem ->
                ReorderableItem(
                    state = reorderableState,
                    key = queueItem.stableId
                ) { isDragging ->
                    val handleModifier = Modifier.draggableHandle(
                        onDragStarted = { onDragStarted() },
                        onDragStopped = { onDragStopped() }
                    )
                    val isCurrent = index == currentIndex
                    PlayerQueueItem(
                        index = index,
                        item = queueItem,
                        isCurrent = isCurrent,
                        isPlaying = if (isCurrent) isPlaying else false,
                        isPrior = currentIndex >= 0 && index < currentIndex,
                        isDragging = isDragging,
                        dragHandleModifier = handleModifier,
                        itemContainerColor = if (isCurrent) currentItemColor else itemContainerColor,
                        showRating = showRating,
                        downloadedIds = downloadedIds,
                        externalRefreshTrigger = externalRefreshTrigger,
                        onClick = onTrackClick,
                        onRemove = onRemoveTrack,
                        canDismiss = items.size > 1
                    )
                }
            }

            if (!hasCurrent) {
                itemsIndexed(items = items, key = { _, item -> item.stableId }) { index, item ->
                    queueRow(index, item)
                }
            } else {
                // Subheaders are not ReorderableItems, so drags skip over them; the
                // sheet maps moves back to song indices by key, not by list position.
                itemsIndexed(
                    items = items.subList(0, currentIndex),
                    key = { _, item -> item.stableId }
                ) { index, item ->
                    queueRow(index, item)
                }
                item(key = NOW_PLAYING_HEADER_KEY) {
                    QueueSubheader(stringResource(R.string.player_queue_now_playing))
                }
                item(key = items[currentIndex].stableId) {
                    queueRow(currentIndex, items[currentIndex])
                }
                if (currentIndex < items.lastIndex) {
                    item(key = NEXT_UP_HEADER_KEY) {
                        QueueSubheader(
                            stringResource(R.string.player_queue_next_up),
                            Modifier.padding(top = 12.dp)
                        )
                    }
                    itemsIndexed(
                        items = items.subList(currentIndex + 1, items.size),
                        key = { _, item -> item.stableId }
                    ) { offset, item ->
                        queueRow(currentIndex + 1 + offset, item)
                    }
                }
            }
        }
    }
}

private const val NOW_PLAYING_HEADER_KEY = "queue_header_now_playing"
private const val NEXT_UP_HEADER_KEY = "queue_header_next_up"

@Composable
private fun QueueSubheader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp)
    )
}
