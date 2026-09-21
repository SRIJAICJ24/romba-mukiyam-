package com.rombamukiyam.vault.feature.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.domain.model.VaultCategory
import com.rombamukiyam.vault.feature.alerts.AlertsScreen
import com.rombamukiyam.vault.feature.credential.ChangePasswordScreen
import com.rombamukiyam.vault.feature.credential.CredentialDetailScreen
import com.rombamukiyam.vault.feature.credential.PolicyScreen
import com.rombamukiyam.vault.feature.health.PasswordHealthScreen
import com.rombamukiyam.vault.feature.credential.CredentialFormScreen
import com.rombamukiyam.vault.feature.family.FamilyScreen
import com.rombamukiyam.vault.feature.home.HomeScreen
import com.rombamukiyam.vault.feature.settings.SettingsScreen
import com.rombamukiyam.vault.feature.vault.SearchScreen
import com.rombamukiyam.vault.feature.vault.TrashScreen
import com.rombamukiyam.vault.feature.vault.VaultListScreen

/**
 * Routes carry only opaque ids — never secrets. There are no deep links into the app.
 */
object Routes {
    const val HOME = "home"
    const val FAMILY = "family"
    const val VAULT = "vault?category={category}&owner={owner}"
    const val ALERTS = "alerts"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val TRASH = "trash"
    const val ITEM = "item/{id}"
    const val ITEM_EDIT = "item/{id}/edit"
    const val ITEM_NEW = "item/new?category={category}&owner={owner}"
    const val ITEM_CHANGE_PASSWORD = "item/{id}/change-password"
    const val ITEM_POLICY = "item/{id}/policy"
    const val HEALTH = "health"

    fun vault(category: VaultCategory? = null, owner: String? = null) = "vault?category=${category?.name ?: ""}&owner=${owner ?: ""}"
    fun item(id: String) = "item/$id"
    fun itemEdit(id: String) = "item/$id/edit"
    fun itemChangePassword(id: String) = "item/$id/change-password"
    fun itemPolicy(id: String) = "item/$id/policy"
    fun itemNew(category: VaultCategory? = null, owner: String? = null) = "item/new?category=${category?.name ?: ""}&owner=${owner ?: ""}"
}

private data class TopLevel(val route: String, val labelRes: Int, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    TopLevel(Routes.FAMILY, R.string.nav_family, Icons.Filled.People),
    TopLevel(Routes.VAULT, R.string.nav_vault, Icons.Filled.Lock),
    TopLevel(Routes.ALERTS, R.string.nav_alerts, Icons.Filled.Notifications),
    TopLevel(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

@Composable
fun MainShell(safeMode: Boolean, onLock: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = topLevel.any { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    topLevel.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = { nav.navigateTopLevel(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    safeMode = safeMode,
                    onSearch = { nav.navigate(Routes.SEARCH) },
                    onOpenCategory = { nav.navigate(Routes.vault(category = it)) },
                    onOpenOwner = { nav.navigate(Routes.vault(owner = it)) },
                    onOpenItem = { nav.navigate(Routes.item(it)) },
                    onAdd = { nav.navigate(Routes.itemNew()) },
                    onOpenAlerts = { nav.navigateTopLevel(Routes.ALERTS) },
                    onOpenBackup = { nav.navigateTopLevel(Routes.SETTINGS) },
                    onOpenHealth = { nav.navigate(Routes.HEALTH) },
                )
            }
            composable(Routes.FAMILY) { FamilyScreen(safeMode = safeMode, onOpenOwner = { nav.navigate(Routes.vault(owner = it)) }) }
            composable(
                Routes.VAULT,
                arguments = listOf(
                    navArgument("category") { type = NavType.StringType; defaultValue = "" },
                    navArgument("owner") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val category = entry.arguments?.getString("category")?.takeIf { it.isNotEmpty() }?.let { VaultCategory.fromName(it) }
                val owner = entry.arguments?.getString("owner")?.takeIf { it.isNotEmpty() }
                VaultListScreen(
                    initialCategory = category,
                    initialOwner = owner,
                    safeMode = safeMode,
                    onOpenItem = { nav.navigate(Routes.item(it)) },
                    onAdd = { cat -> nav.navigate(Routes.itemNew(cat, owner)) },
                    onOpenTrash = { nav.navigate(Routes.TRASH) },
                    onSearch = { nav.navigate(Routes.SEARCH) },
                )
            }
            composable(Routes.ALERTS) { AlertsScreen(onOpenItem = { nav.navigate(Routes.item(it)) }) }
            composable(Routes.SETTINGS) { SettingsScreen(safeMode = safeMode, onLock = onLock) }
            composable(Routes.SEARCH) { SearchScreen(onBack = { nav.popBackStack() }, onOpenItem = { nav.navigate(Routes.item(it)) }) }
            composable(Routes.TRASH) { TrashScreen(safeMode = safeMode, onBack = { nav.popBackStack() }, onOpenItem = { nav.navigate(Routes.item(it)) }) }
            composable(Routes.ITEM, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                CredentialDetailScreen(
                    itemId = id,
                    safeMode = safeMode,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.itemEdit(id)) },
                    onChangePassword = { nav.navigate(Routes.itemChangePassword(id)) },
                    onEditPolicy = { nav.navigate(Routes.itemPolicy(id)) },
                )
            }
            composable(Routes.ITEM_CHANGE_PASSWORD, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                ChangePasswordScreen(itemId = id, onBack = { nav.popBackStack() })
            }
            composable(Routes.ITEM_POLICY, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                PolicyScreen(itemId = id, onBack = { nav.popBackStack() })
            }
            composable(Routes.HEALTH) { PasswordHealthScreen(onBack = { nav.popBackStack() }, onOpenItem = { nav.navigate(Routes.item(it)) }) }
            composable(Routes.ITEM_EDIT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                CredentialFormScreen(itemId = id, presetCategory = null, presetOwner = null, onDone = { nav.popBackStack() })
            }
            composable(
                Routes.ITEM_NEW,
                arguments = listOf(
                    navArgument("category") { type = NavType.StringType; defaultValue = "" },
                    navArgument("owner") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val category = entry.arguments?.getString("category")?.takeIf { it.isNotEmpty() }?.let { VaultCategory.fromName(it) }
                val owner = entry.arguments?.getString("owner")?.takeIf { it.isNotEmpty() }
                CredentialFormScreen(
                    itemId = null,
                    presetCategory = category,
                    presetOwner = owner,
                    onDone = { nav.popBackStack() },
                    onCreated = { newId ->
                        nav.popBackStack()
                        nav.navigate(Routes.item(newId))
                    },
                )
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
