package com.aizwal.pandanclone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = MintNeon,
    secondary = TealAqua,
    background = Obsidian,
    surface = DeepSlate,
    onPrimary = Obsidian,
    onSecondary = Obsidian,
    onBackground = WhiteText,
    onSurface = WhiteText,
    surfaceVariant = DeepSlate,
    onSurfaceVariant = GreyText,
    error = AlertCoral,
    onError = Obsidian
)

@Composable
fun PandanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
