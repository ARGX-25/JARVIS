package com.example.jarvis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.jarvis.ui.theme.JARVISTheme
import com.example.jarvis.ui.theme.JarvisColors

private const val MemoryTokenCap = 800

/** Compact header pill: memory loaded state and injected-token budget. */
@Composable
fun MemoryStatusConsole(
    isMemoryLoaded: Boolean,
    tokenEstimate: Int,
    modifier: Modifier = Modifier
) {
    val tokens = tokenEstimate.coerceAtLeast(0)
    val progress = (tokens.toFloat() / MemoryTokenCap).coerceIn(0f, 1f)
    val accent = if (isMemoryLoaded) JarvisColors.Cyan else JarvisColors.TextSecondary
    val pill = RoundedCornerShape(999.dp)

    Row(
        modifier = modifier
            .clip(pill)
            .background(JarvisColors.Midnight)
            .border(1.dp, JarvisColors.Outline, pill)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (isMemoryLoaded) "Memory $tokens/$MemoryTokenCap" else "Memory empty",
                color = JarvisColors.TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .width(88.dp)
                    .height(3.dp),
                color = accent,
                trackColor = JarvisColors.Outline
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF050B1E)
@Composable
private fun MemoryStatusConsoleLoadedPreview() {
    JARVISTheme {
        MemoryStatusConsole(isMemoryLoaded = true, tokenEstimate = 312, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF050B1E)
@Composable
private fun MemoryStatusConsoleEmptyPreview() {
    JARVISTheme {
        MemoryStatusConsole(isMemoryLoaded = false, tokenEstimate = 0, modifier = Modifier.padding(16.dp))
    }
}
