package com.fastshare.app.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fastshare.app.core.util.formatBytes
import com.fastshare.app.core.util.formatDuration
import com.fastshare.app.core.util.formatPercent
import com.fastshare.app.core.util.formatSpeed
import com.fastshare.app.domain.model.TransferDirection
import com.fastshare.app.domain.model.TransferSession
import com.fastshare.app.domain.model.TransferState

/** Live transfer card: name, size, speed, percent, ETA, plus pause/resume/cancel. */
@Composable
fun TransferProgressCard(
    session: TransferSession,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(session.fraction, tween(240), label = "progress")
    val directionLabel = if (session.direction == TransferDirection.SEND) "Sending to" else "Receiving from"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "$directionLabel ${session.peer.deviceName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = session.currentItemName ?: "${session.items.size} items",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                }
                when {
                    session.state == TransferState.PAUSED -> IconButton(onClick = onResume) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = "Resume transfer")
                    }
                    session.state.isActive -> IconButton(onClick = onPause) {
                        Icon(Icons.Outlined.Pause, contentDescription = "Pause transfer")
                    }
                }
                if (!session.state.isTerminal) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Cancel, contentDescription = "Cancel transfer")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .semantics { contentDescription = "Progress ${session.fraction.formatPercent()}" },
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${session.transferredBytes.formatBytes()} / ${session.totalBytes.formatBytes()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = session.fraction.formatPercent(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (session.state.isActive) {
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = session.bytesPerSecond.formatSpeed(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "${session.etaMillis.formatDuration()} left",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            session.error?.let { error ->
                Spacer(Modifier.height(6.dp))
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
