package de.feuerwehr.einsatzapp.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import de.feuerwehr.einsatzapp.ui.theme.FeuerwehrRot

enum class MainTab(
    val label: String,
    val icon: ImageVector,
) {
    ALARMS("Einsätze", Icons.Default.List),
    SETTINGS("Einstellungen", Icons.Default.Settings),
}

@Composable
fun AppBottomBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        MainTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = FeuerwehrRot,
                    selectedTextColor = FeuerwehrRot,
                    indicatorColor = FeuerwehrRot.copy(alpha = 0.12f),
                ),
            )
        }
    }
}
