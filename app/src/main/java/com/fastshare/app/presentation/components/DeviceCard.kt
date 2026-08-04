package com.fastshare.app.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Tablet
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fastshare.app.domain.model.DevicePlatform
import com.fastshare.app.domain.model.DeviceType
import com.fastshare.app.domain.model.DiscoveredDevice
import com.fastshare.app.domain.model.SignalStrength

/** Device row in the nearby list. Whole card is one touch target with a spoken description. */
@Composable
fun DeviceCard(
    device: DiscoveredDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val platformLabel = device.info.platform.label()
    val a11y = buildString {
        append(device.displayName)
        append(", ").append(platformLabel)
        if (device.isTrusted) append(", paired")
        append(", signal ").append(device.signalStrength.name.lowercase())
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = a11y }
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = device.info.deviceType.icon(device.info.platform),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    if (device.isTrusted) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Outlined.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    if (device.isFavorite) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Text(
                    text = "$platformLabel · ${device.info.ipAddress}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            SignalBars(device.signalStrength)
        }
    }
}

@Composable
private fun SignalBars(strength: SignalStrength, modifier: Modifier = Modifier) {
    val active = when (strength) {
        SignalStrength.EXCELLENT -> 4
        SignalStrength.GOOD -> 3
        SignalStrength.FAIR -> 2
        SignalStrength.WEAK -> 1
        SignalStrength.UNKNOWN -> 0
    }
    val tint = when (strength) {
        SignalStrength.EXCELLENT, SignalStrength.GOOD -> MaterialTheme.colorScheme.primary
        SignalStrength.FAIR -> MaterialTheme.colorScheme.tertiary
        SignalStrength.WEAK -> MaterialTheme.colorScheme.error
        SignalStrength.UNKNOWN -> MaterialTheme.colorScheme.outline
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(4) { index ->
            val on = index < active
            val alpha by animateFloatAsState(if (on) 1f else 0.22f, label = "bar$index")
            Box(
                Modifier
                    .width(3.dp)
                    .height((6 + index * 4).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tint.copy(alpha = alpha)),
            )
        }
    }
}

private fun DevicePlatform.label(): String = when (this) {
    DevicePlatform.ANDROID -> "Android"
    DevicePlatform.IOS -> "iOS"
    DevicePlatform.WINDOWS -> "Windows"
    DevicePlatform.MACOS -> "macOS"
    DevicePlatform.LINUX -> "Linux"
    DevicePlatform.WEB -> "Web"
    DevicePlatform.UNKNOWN -> "Device"
}

private fun DeviceType.icon(platform: DevicePlatform): ImageVector = when (this) {
    DeviceType.TABLET -> Icons.Outlined.Tablet
    DeviceType.DESKTOP -> Icons.Outlined.Computer
    DeviceType.LAPTOP -> Icons.Outlined.Laptop
    DeviceType.TV -> Icons.Outlined.Tv
    DeviceType.PHONE -> if (platform == DevicePlatform.IOS) Icons.Outlined.PhoneIphone else Icons.Outlined.PhoneAndroid
    else -> when (platform) {
        DevicePlatform.WINDOWS, DevicePlatform.LINUX -> Icons.Outlined.Computer
        DevicePlatform.MACOS -> Icons.Outlined.Laptop
        DevicePlatform.IOS -> Icons.Outlined.PhoneIphone
        else -> Icons.Outlined.PhoneAndroid
    }
}
