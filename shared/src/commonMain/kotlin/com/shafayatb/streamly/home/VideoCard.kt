package com.shafayatb.streamly.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shafayatb.streamly.core.designsystem.components.ChannelAvatar
import com.shafayatb.streamly.core.designsystem.components.DurationBadge
import com.shafayatb.streamly.core.designsystem.components.LiveBadge
import com.shafayatb.streamly.core.designsystem.components.VideoThumbnail
import com.shafayatb.streamly.core.presentation.asString
import org.jetbrains.compose.resources.stringResource
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.video_card_metadata
import streamly.shared.generated.resources.video_live

@Composable
fun VideoCard(
    video: VideoCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        VideoThumbnail(
            url = video.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val badgeModifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
            if (video.duration != null) {
                DurationBadge(text = video.duration, modifier = badgeModifier)
            } else {
                LiveBadge(text = stringResource(Res.string.video_live), modifier = badgeModifier)
            }
        }
        Row(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
            ChannelAvatar(channelName = video.channelName)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        Res.string.video_card_metadata,
                        video.channelName,
                        video.views.asString(),
                        video.age.asString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
