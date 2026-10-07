package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.LenoRepository

import androidx.compose.foundation.border
import com.example.ui.theme.LenoAvatarBorder
import com.example.ui.theme.LenoAvatarDefaultBg
import com.example.ui.theme.LenoAvatarIconText
import com.example.ui.theme.LenoLogoBackground

@Composable
fun LenoAvatar(
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    isOfficial: Boolean = false,
    contentDescription: String? = null,
    userId: String? = null,
    username: String? = null
) {
    val isOfficialUser = userId == LenoRepository.OFFICIAL_LENO_ID ||
            username?.equals("leno", ignoreCase = true) == true ||
            (isOfficial && (userId == LenoRepository.OFFICIAL_LENO_ID || username?.equals("leno", ignoreCase = true) == true))

    if (isOfficialUser) {
        // ALWAYS use the authentic official Leno logo for Official Leno account
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_official_leno_avatar),
                contentDescription = contentDescription ?: "Official Leno Logo",
                modifier = Modifier.size(size),
                contentScale = ContentScale.Fit
            )
        }
    } else {
        // Standard user avatar with smooth loading and graceful fallback
        val context = LocalContext.current
        val isDemoAvatar = avatarUrl?.contains("picsum.photos", ignoreCase = true) == true ||
                avatarUrl?.contains("seed/", ignoreCase = true) == true ||
                avatarUrl?.contains("demo", ignoreCase = true) == true

        val model = if (!avatarUrl.isNullOrBlank() && !isDemoAvatar) {
            ImageRequest.Builder(context)
                .data(avatarUrl)
                .crossfade(true)
                .build()
        } else {
            null
        }

        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription ?: "User Avatar",
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(1.dp, LenoAvatarBorder, CircleShape)
                    .background(LenoAvatarDefaultBg),
                contentScale = ContentScale.Crop
            )
        } else {
            val initial = (username?.trim()?.removePrefix("@")?.firstOrNull()
                ?: contentDescription?.trim()?.firstOrNull { it.isLetter() })?.uppercaseChar()

            Box(
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(1.dp, LenoAvatarBorder, CircleShape)
                    .background(LenoAvatarDefaultBg),
                contentAlignment = Alignment.Center
            ) {
                if (initial != null && initial != 'U') {
                    androidx.compose.material3.Text(
                        text = initial.toString(),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = LenoAvatarIconText,
                        fontSize = ((size.value * 0.45f).coerceAtLeast(10f)).sp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = contentDescription ?: "User Avatar",
                        tint = LenoAvatarIconText,
                        modifier = Modifier.size(size * 0.6f)
                    )
                }
            }
        }
    }
}
