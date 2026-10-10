package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.FlowCategory

@Composable
fun NetworkLogo(
    networkName: String,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val context = LocalContext.current

    val loadedBitmap = remember(networkName) {
        val lowerName = networkName.trim().lowercase().replace(" ", "").replace("-", "").replace("_", "")
        if (lowerName.isBlank()) return@remember null

        val logoFiles = try {
            context.assets.list("logos")?.filter { file ->
                file.endsWith(".png", ignoreCase = true) ||
                file.endsWith(".jpg", ignoreCase = true) ||
                file.endsWith(".jpeg", ignoreCase = true) ||
                file.endsWith(".webp", ignoreCase = true)
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val matchedFile = logoFiles.firstOrNull { fileName ->
            val brand = fileName.substringBeforeLast(".").lowercase().replace(" ", "").replace("-", "").replace("_", "")
            lowerName == brand || lowerName.contains(brand) || brand.contains(lowerName)
        }

        if (matchedFile != null) {
            try {
                context.assets.open("logos/$matchedFile").use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.size(size),
        tonalElevation = 1.dp,
        shadowElevation = 0.5.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loadedBitmap != null) {
                Image(
                    bitmap = loadedBitmap.asImageBitmap(),
                    contentDescription = networkName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.SignalCellularAlt,
                    contentDescription = networkName,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(size * 0.55f)
                )
            }
        }
    }
}

@Composable
fun ProviderLogo(
    flowName: String,
    category: FlowCategory,
    modifier: Modifier = Modifier,
    containerSize: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    shapeRadius: Dp = 12.dp
) {
    val context = LocalContext.current

    // Dynamically list files in assets/logos/ and extract brand keywords from logo filenames
    val loadedBitmap = remember(flowName) {
        val lowerName = flowName.lowercase()

        // 1. Get all files in assets/logos/
        val logoFiles = try {
            context.assets.list("logos")?.filter { file ->
                file.endsWith(".png", ignoreCase = true) ||
                file.endsWith(".jpg", ignoreCase = true) ||
                file.endsWith(".jpeg", ignoreCase = true) ||
                file.endsWith(".webp", ignoreCase = true)
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        // 2. Find any logo filename whose brand name is contained in flowName
        // E.g., "mtn.png" -> brand "mtn". If flowName contains "mtn", match "mtn.png".
        val matchedFile = logoFiles.firstOrNull { fileName ->
            val brandKeyword = fileName.substringBeforeLast(".").lowercase()
            brandKeyword.length >= 2 && lowerName.contains(brandKeyword)
        }

        // 3. Load bitmap if matched file found
        if (matchedFile != null) {
            try {
                context.assets.open("logos/$matchedFile").use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    val categoryIcon = when (category) {
        FlowCategory.MOBILE_MONEY -> Icons.Default.AccountBalanceWallet
        FlowCategory.BANK -> Icons.Default.AccountBalance
        FlowCategory.AIRTIME -> Icons.Default.PhonelinkRing
        FlowCategory.DATA -> Icons.Default.SignalCellularAlt
        FlowCategory.UTILITIES -> Icons.Default.FlashOn
        FlowCategory.CUSTOM -> Icons.Default.AutoAwesome
    }

    val iconBgColor = when (category) {
        FlowCategory.MOBILE_MONEY -> Color(0xFFE8DEF8)
        FlowCategory.BANK -> Color(0xFFD0E8F2)
        FlowCategory.AIRTIME -> Color(0xFFE0F2FE)
        FlowCategory.DATA -> Color(0xFFDCFCE7)
        FlowCategory.UTILITIES -> Color(0xFFFEF08A)
        FlowCategory.CUSTOM -> Color(0xFFF3E8FF)
    }

    val iconTint = when (category) {
        FlowCategory.MOBILE_MONEY -> Color(0xFF6750A4)
        FlowCategory.BANK -> Color(0xFF0D47A1)
        FlowCategory.AIRTIME -> Color(0xFF0284C7)
        FlowCategory.DATA -> Color(0xFF16A34A)
        FlowCategory.UTILITIES -> Color(0xFFCA8A04)
        FlowCategory.CUSTOM -> Color(0xFF9333EA)
    }

    Surface(
        shape = RoundedCornerShape(shapeRadius),
        color = iconBgColor,
        modifier = modifier.size(containerSize)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loadedBitmap != null) {
                Image(
                    bitmap = loadedBitmap.asImageBitmap(),
                    contentDescription = flowName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = category.displayName,
                    tint = iconTint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}
