package com.eddyizm.tempus.ui.queue

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.bumptech.glide.signature.ObjectKey
import com.eddyizm.tempus.R
import com.eddyizm.tempus.glide.CustomGlideRequest
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.ui.components.rememberDataSavingMode
import com.eddyizm.tempus.util.DownloadUtil
import com.eddyizm.tempus.util.ExternalAudioReader
import com.eddyizm.tempus.util.FavoriteRegistry
import com.eddyizm.tempus.util.MusicUtil
import com.eddyizm.tempus.util.Preferences
import com.eddyizm.tempus.util.RadioCoverArtDownloader

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerQueueItem(
    index: Int,
    item: QueueSongItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isPrior: Boolean,
    isDragging: Boolean,
    itemContainerColor: Color,
    showRating: Boolean,
    onClick: (Int, Child) -> Unit,
    onRemove: (Int, QueueSongItem) -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    downloadedIds: Set<String> = emptySet(),
    canDismiss: Boolean = true,
    externalRefreshTrigger: Any? = null
) {
    val context = LocalContext.current
    val textAlpha = if (isPrior) 0.45f else 1.0f

    val currentIndex by rememberUpdatedState(index)
    val currentOnRemove by rememberUpdatedState(onRemove)
    val currentItem by rememberUpdatedState(item)

    val downloadDirUri = remember { Preferences.getDownloadDirectoryUri() }
    val downloaderManager = remember(context) { DownloadUtil.getDownloadTracker(context) }

    val isDownloaded = remember(item.song.id, downloadedIds, externalRefreshTrigger) {
        if (downloadDirUri == null) {
            downloadedIds.contains(item.song.id) || downloaderManager?.isDownloaded(item.song.id) == true
        } else {
            ExternalAudioReader.getUri(item.song) != null
        }
    }
    val isStarred = remember(item.song.id, item.song.starred) {
        FavoriteRegistry.resolve(FavoriteRegistry.Kind.SONG, item.song.id, item.song.starred != null)
    }
    val rating = item.song.userRating ?: 0

    val durationStr = remember(item.song.duration) {
        MusicUtil.getReadableDurationString(item.song.duration, false)
    }
    val qualityStr = remember(item.song) {
        MusicUtil.getReadableAudioQualityString(item.song)
    }
    val subtitle = stringResource(
        R.string.song_subtitle_formatter,
        item.song.artist ?: "",
        durationStr,
        qualityStr
    ).trim()

    var hasTriggeredDismiss by remember(item.song.id) { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.4f })

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !isCurrent && canDismiss,
        onDismiss = { direction ->
            if (direction == SwipeToDismissBoxValue.EndToStart && !hasTriggeredDismiss) {
                hasTriggeredDismiss = true
                currentOnRemove(currentIndex, currentItem)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = if (isCurrent) 4.dp else 0.dp),
        backgroundContent = {
            if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart ||
                dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.player_queue_remove),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        },
        content = {
            val rowShape = RoundedCornerShape(if (isCurrent) 16.dp else 12.dp)

            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(rowShape)
                    .clickable { onClick(index, item.song) },
                color = itemContainerColor,
                shape = rowShape,
                tonalElevation = if (isDragging) 8.dp else 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(
                            start = 8.dp,
                            end = if (isCurrent) 4.dp else 0.dp,
                            top = if (isCurrent) 8.dp else 0.dp,
                            bottom = if (isCurrent) 8.dp else 0.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QueueArtwork(
                        coverArtId = item.song.coverArtId,
                        size = 160,
                        cornerRadius = if (isCurrent) 8.dp else 6.dp,
                        modifier = Modifier.size(if (isCurrent) 52.dp else 44.dp),
                        overlayContent = if (isCurrent) {
                            {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0x66000000)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    EqualizerBars(playing = isPlaying, color = Color.White)
                                }
                            }
                        } else null
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title and Subtitle
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = item.song.title ?: "",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = if (isCurrent) 15.sp else 14.sp,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                                letterSpacing = 0.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (isCurrent) Modifier.basicMarquee() else Modifier
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, letterSpacing = 0.sp),
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Metadata badges (Favorite, Rating, Download)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isStarred) {
                            Icon(
                                painter = painterResource(R.drawable.ic_favorite),
                                contentDescription = stringResource(R.string.player_queue_starred),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = textAlpha),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (showRating && rating > 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                repeat(5) { starIndex ->
                                    val isFilled = rating > starIndex
                                    Icon(
                                        painter = painterResource(if (isFilled) R.drawable.ic_star else R.drawable.ic_star_outlined),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = textAlpha),
                                        modifier = Modifier.size(8.dp)
                                    )
                                }
                            }
                        }

                        if (isDownloaded) {
                            Icon(
                                painter = painterResource(R.drawable.ic_download),
                                contentDescription = stringResource(R.string.player_queue_downloaded),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { onClick(index, item.song) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                                    contentDescription = stringResource(if (isPlaying) R.string.player_queue_pause else R.string.player_queue_play),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .then(dragHandleModifier),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_drag_handle),
                                    contentDescription = stringResource(R.string.player_queue_reorder),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = QUEUE_MUTED_ALPHA),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

private val EQ_BAR_PERIODS_MS = intArrayOf(900, 700, 1100)
private val EQ_BAR_OFFSETS_MS = intArrayOf(200, 500, 100)
private val EQ_BAR_PAUSED_HEIGHTS = floatArrayOf(0.6f, 0.9f, 0.45f)

@Composable
private fun EqualizerBars(playing: Boolean, color: Color) {
    val transition = rememberInfiniteTransition(label = "queueEq")
    Row(
        modifier = Modifier.size(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (bar in EQ_BAR_PERIODS_MS.indices) {
            val animated by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(EQ_BAR_PERIODS_MS[bar] / 2),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(EQ_BAR_OFFSETS_MS[bar])
                ),
                label = "queueEqBar$bar"
            )
            val fraction = if (playing) animated else EQ_BAR_PAUSED_HEIGHTS[bar]
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun QueueArtwork(
    coverArtId: String?,
    modifier: Modifier = Modifier,
    artworkUri: Uri? = null,
    size: Int = 160,
    cornerRadius: Dp = 0.dp,
    contentDescription: String? = null,
    @DrawableRes placeholderRes: Int = R.drawable.ic_placeholder_song,
    overlayContent: (@Composable () -> Unit)? = null
) {
    val placeholderContent: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(placeholderRes),
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
    val placeholder = placeholder(placeholderContent)
    val dataSaving = rememberDataSavingMode()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (coverArtId.isNullOrEmpty() && artworkUri == null) {
            placeholderContent()
        } else {
            GlideImage(
                model = artworkUri ?: CustomGlideRequest.createUrl(coverArtId, size),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                requestBuilderTransform = { request ->
                    var transformed = request
                        .signature(ObjectKey(coverArtId ?: artworkUri.toString()))
                        .diskCacheStrategy(CustomGlideRequest.DEFAULT_DISK_CACHE_STRATEGY)
                    if (dataSaving) {
                        transformed = transformed.onlyRetrieveFromCache(true)
                    }
                    if (artworkUri != null) {
                        transformed = RadioCoverArtDownloader.applyLocalFileSignature(transformed, artworkUri)
                    }
                    transformed
                },
                loading = placeholder,
                failure = placeholder,
            )
        }

        overlayContent?.invoke()
    }
}
