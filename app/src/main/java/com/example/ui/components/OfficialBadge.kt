package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LenoOfficialBadge
import com.example.ui.theme.LenoPrimaryLight

@Composable
fun OfficialVerifiedBadge(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = LenoOfficialBadge
) {
    Icon(
        imageVector = Icons.Default.Verified,
        contentDescription = "Verified Official Account",
        tint = tint,
        modifier = modifier
            .size(size)
            .testTag("official_verified_badge")
    )
}

@Composable
fun OfficialUserNameRow(
    name: String,
    isOfficial: Boolean,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    badgeSize: Dp = 18.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = textColor
        )
        if (isOfficial) {
            Spacer(modifier = Modifier.width(4.dp))
            OfficialVerifiedBadge(size = badgeSize)
        }
    }
}

@Composable
fun OfficialTagChip(
    modifier: Modifier = Modifier
) {
    Surface(
        color = LenoPrimaryLight,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OfficialVerifiedBadge(size = 12.dp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Official System Account",
                style = MaterialTheme.typography.labelSmall,
                color = LenoOfficialBadge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
