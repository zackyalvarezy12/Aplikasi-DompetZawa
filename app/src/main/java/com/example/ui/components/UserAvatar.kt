package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun UserAvatar(
    initials: String,
    avatarColorHex: Long,
    photoPath: String = "",
    photoUri: Uri? = null,
    size: Dp = 42.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    contentDescription: String = "Foto Profil",
    modifier: Modifier = Modifier
) {
    val photoModel: Any? = remember(photoUri, photoPath) {
        when {
            photoUri != null -> photoUri
            photoPath.isNotBlank() -> File(photoPath).takeIf { it.exists() }
            else -> null
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(avatarColorHex)),
        contentAlignment = Alignment.Center
    ) {
        if (photoModel != null) {
            AsyncImage(
                model = photoModel,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = initials.ifBlank { "DZ" },
                style = textStyle,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
