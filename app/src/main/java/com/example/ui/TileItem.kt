package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SoundTile
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TileItem(
    tile: SoundTile,
    fontSizeSp: Float,
    isSelectedForSwap: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDoubleTap: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(tile.color))
            .border(
                width = if (isSelectedForSwap) 4.dp else 0.dp,
                color = if (isSelectedForSwap) Color.White else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress,
                onDoubleClick = onDoubleTap
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tile.name,
            color = if (Color(tile.color).luminance() > 0.5f) Color.Black else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = fontSizeSp.sp,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Simple luminance calculation
fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}
