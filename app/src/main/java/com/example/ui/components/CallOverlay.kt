package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.UserEntity
import com.example.ui.theme.LenoBlack
import com.example.ui.theme.LenoButtonDestructiveBg
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoWhite
import com.example.viewmodel.CallState

@Composable
fun CallOverlay(
    callState: CallState,
    onAcceptCall: () -> Unit,
    onDeclineCall: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSimulateAnswer: () -> Unit = {}
) {
    if (callState is CallState.Idle) return

    Dialog(
        onDismissRequest = { /* Prevent dismiss outside buttons */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LenoBlack.copy(alpha = 0.96f))
                .testTag("call_overlay_container"),
            contentAlignment = Alignment.Center
        ) {
            when (callState) {
                is CallState.Outgoing -> {
                    OutgoingCallView(
                        partner = callState.partner,
                        statusText = callState.statusText,
                        onEndCall = onEndCall
                    )
                }
                is CallState.Incoming -> {
                    IncomingCallView(
                        caller = callState.caller,
                        onAcceptCall = onAcceptCall,
                        onDeclineCall = onDeclineCall
                    )
                }
                is CallState.Connected -> {
                    ConnectedCallView(
                        partner = callState.partner,
                        durationSeconds = callState.durationSeconds,
                        isMuted = callState.isMuted,
                        isSpeakerOn = callState.isSpeakerOn,
                        onToggleMute = onToggleMute,
                        onToggleSpeaker = onToggleSpeaker,
                        onEndCall = onEndCall
                    )
                }
                CallState.Idle -> {}
            }
        }
    }
}

@Composable
private fun OutgoingCallView(
    partner: UserEntity,
    statusText: String,
    onEndCall: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Text(
                text = "Outgoing Voice Call",
                style = MaterialTheme.typography.titleMedium,
                color = LenoPrimary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = partner.displayName,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "@${partner.username} • ${partner.linoId}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        // Center Avatar with pulse
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(LenoPrimary.copy(alpha = 0.2f))
            )
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .border(3.dp, LenoPrimary, CircleShape)
            ) {
                LenoAvatar(
                    avatarUrl = partner.avatarUrl,
                    userId = partner.userId,
                    username = partner.username,
                    size = 140.dp
                )
            }
        }

        // Calling Status & Action buttons
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 40.dp)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // End Call button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        onClick = onEndCall,
                        shape = CircleShape,
                        color = LenoButtonDestructiveBg,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .size(68.dp)
                            .testTag("end_call_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "End Call",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun IncomingCallView(
    caller: UserEntity,
    onAcceptCall: () -> Unit,
    onDeclineCall: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseIncoming")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseIncomingScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Text(
                text = "Incoming Voice Call",
                style = MaterialTheme.typography.titleMedium,
                color = LenoPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = caller.displayName,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "@${caller.username} • ${caller.linoId}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        // Center Avatar
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(LenoPrimary.copy(alpha = 0.25f))
            )
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .border(3.dp, LenoPrimary, CircleShape)
            ) {
                LenoAvatar(
                    avatarUrl = caller.avatarUrl,
                    userId = caller.userId,
                    username = caller.username,
                    size = 140.dp
                )
            }
        }

        // Accept / Decline Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decline Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    onClick = onDeclineCall,
                    shape = CircleShape,
                    color = LenoButtonDestructiveBg,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(68.dp)
                        .testTag("decline_call_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Decline Call",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Decline",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            // Accept Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    onClick = onAcceptCall,
                    shape = CircleShape,
                    color = LenoPrimary,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(68.dp)
                        .testTag("accept_call_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Accept Call",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Accept",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ConnectedCallView(
    partner: UserEntity,
    durationSeconds: Int,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit
) {
    val formattedDuration = String.format("%02d:%02d", durationSeconds / 60, durationSeconds % 60)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header info
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = LenoPrimary.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.dp, LenoPrimary.copy(alpha = 0.4f)),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(LenoPrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Call Connected",
                        style = MaterialTheme.typography.labelMedium,
                        color = LenoPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = partner.displayName,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "@${partner.username} • ${partner.linoId}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = formattedDuration,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )
        }

        // Center Audio Visualizer & Avatar
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .border(3.dp, if (isMuted) LenoButtonDestructiveBg else LenoPrimary, CircleShape)
            ) {
                LenoAvatar(
                    avatarUrl = partner.avatarUrl,
                    userId = partner.userId,
                    username = partner.username,
                    size = 130.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Animated frequency bars
            AudioWaveformVisualizer(isMuted = isMuted)
        }

        // Call Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    onClick = onToggleMute,
                    shape = CircleShape,
                    color = if (isMuted) LenoButtonDestructiveBg.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isMuted) LenoButtonDestructiveBg else Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("mute_call_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = if (isMuted) LenoButtonDestructiveBg else Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isMuted) "Muted" else "Mute",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isMuted) LenoButtonDestructiveBg else Color.White.copy(alpha = 0.8f)
                )
            }

            // End Call Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    onClick = onEndCall,
                    shape = CircleShape,
                    color = LenoButtonDestructiveBg,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(68.dp)
                        .testTag("end_active_call_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "End",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            // Speaker Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    onClick = onToggleSpeaker,
                    shape = CircleShape,
                    color = if (isSpeakerOn) LenoPrimary.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSpeakerOn) LenoPrimary else Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("speaker_call_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute,
                            contentDescription = if (isSpeakerOn) "Speaker Off" else "Speaker On",
                            tint = if (isSpeakerOn) LenoPrimary else Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isSpeakerOn) "Speaker" else "Earpiece",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSpeakerOn) LenoPrimary else Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun AudioWaveformVisualizer(isMuted: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "b1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(350), RepeatMode.Reverse), label = "b2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "b3"
    )
    val bar4 by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(300), RepeatMode.Reverse), label = "b4"
    )
    val bar5 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "b5"
    )

    val heights = if (isMuted) {
        listOf(4.dp, 4.dp, 4.dp, 4.dp, 4.dp)
    } else {
        listOf(
            (bar1 * 28 + 6).dp,
            (bar2 * 36 + 8).dp,
            (bar3 * 44 + 10).dp,
            (bar4 * 32 + 8).dp,
            (bar5 * 24 + 6).dp
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(48.dp)
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(h)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isMuted) LenoButtonDestructiveBg.copy(alpha = 0.6f) else LenoPrimary)
            )
        }
    }
}
