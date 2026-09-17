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

import androidx.compose.runtime.remember

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
    val isLight = remember(tile.color) {
        val c = Color(tile.color)
        (0.299f * c.red + 0.587f * c.green + 0.114f * c.blue) > 0.5f
    }
    
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
            color = if (isLight) Color.Black else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = fontSizeSp.sp,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        if (tile.playbackSpeed != 1.0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "${String.format(java.util.Locale.US, "%.1f", tile.playbackSpeed)}x",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
