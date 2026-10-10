package com.example.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.navigation.Screen
import com.example.ui.theme.MtnYellow

data class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val BottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard.route, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(Screen.FlowLibrary.route, "Flows", Icons.Filled.AccountTree, Icons.Outlined.AccountTree),
    BottomNavItem(Screen.Templates.route, "Templates", Icons.Filled.GridView, Icons.Outlined.GridView),
    BottomNavItem(Screen.Settings.route, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun AppBottomNavigation(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    language: String = "English"
) {
    val items = listOf(
        BottomNavItem(Screen.Dashboard.route, com.example.data.model.AppStrings.navHome(language), Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavItem(Screen.FlowLibrary.route, com.example.data.model.AppStrings.navLibrary(language), Icons.Filled.AccountTree, Icons.Outlined.AccountTree),
        BottomNavItem(Screen.Templates.route, com.example.data.model.AppStrings.navTemplates(language), Icons.Filled.GridView, Icons.Outlined.GridView),
        BottomNavItem(Screen.Settings.route, com.example.data.model.AppStrings.navSettings(language), Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.DarkBackground ||
            MaterialTheme.colorScheme.surface == com.example.ui.theme.DarkSurface

    val navContainerColor = if (isDark) com.example.ui.theme.DarkSurface else Color.White
    val navTextColor = if (isDark) Color.White else Color.Black
    val unselectedIconColor = if (isDark) Color.White else Color.Black
    val topOutlineColor = if (isDark) Color(0xFF38383A) else Color(0xFF747475)

    NavigationBar(
        modifier = modifier
            .testTag("app_bottom_navigation")
            .drawWithContent {
                drawContent()
                drawLine(
                    color = topOutlineColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            },
        containerColor = navContainerColor,
        contentColor = navTextColor,
        tonalElevation = NavigationBarDefaults.Elevation
    ) {
        items.forEach { item ->
            val isSelected = currentRoute == item.route
            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        onNavigate(item.route)
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.title
                    )
                },
                label = {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = navTextColor
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    unselectedIconColor = unselectedIconColor,
                    selectedTextColor = navTextColor,
                    unselectedTextColor = navTextColor,
                    indicatorColor = MtnYellow
                )
            )
        }
    }
}
