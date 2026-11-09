package com.example.warehouseapp.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.warehouseapp.domain.UserProfile
import com.example.warehouseapp.ui.admin.AdminScreen
import com.example.warehouseapp.ui.admin.AdminViewModel
import com.example.warehouseapp.ui.admin.ProductEditorScreen
import com.example.warehouseapp.ui.catalog.*
import com.example.warehouseapp.ui.components.AchievementToast
import com.example.warehouseapp.ui.people.CollectorsScreen
import com.example.warehouseapp.ui.people.PeopleViewModel
import com.example.warehouseapp.ui.people.UserProfileScreen
import com.example.warehouseapp.ui.profile.ProfileScreen
import com.example.warehouseapp.ui.routes.RouteDetailScreen
import com.example.warehouseapp.ui.routes.RoutesHomeScreen
import com.example.warehouseapp.ui.routes.RoutesViewModel
import kotlinx.coroutines.flow.merge

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val baseTabs = listOf(
    Tab("catalog", "Каталог", Icons.Outlined.Inventory2, Icons.Rounded.Inventory2),
    Tab("routes", "Маршруты", Icons.Outlined.Route, Icons.Rounded.Route),
    Tab("people", "Сборщики", Icons.Outlined.Groups, Icons.Rounded.Groups),
    Tab("profile", "Профиль", Icons.Outlined.AccountCircle, Icons.Rounded.AccountCircle),
)
private val adminTab = Tab("admin", "Админ", Icons.Outlined.AdminPanelSettings, Icons.Rounded.AdminPanelSettings)

@Composable
fun MainApp(session: SessionViewModel, user: UserProfile, openRouteId: String?, onRouteOpened: () -> Unit) {
    val nav = rememberNavController()
    val catalog: CatalogViewModel = viewModel()
    val routesVm: RoutesViewModel = viewModel()
    val people: PeopleViewModel = viewModel()
    val admin: AdminViewModel = viewModel()
    val snackbar = remember { SnackbarHostState() }
    val toast by session.achievementToast.collectAsStateWithLifecycle()
    val tabs = if (user.isAdmin) baseTabs + adminTab else baseTabs

    LaunchedEffect(Unit) {
        merge(session.messages, admin.messages).collect { snackbar.showSnackbar(it) }
    }
    val routeErr by routesVm.error.collectAsStateWithLifecycle()
    LaunchedEffect(routeErr) { routeErr?.let { snackbar.showSnackbar(it); routesVm.consumeError() } }

    // Переход из уведомления к нужному маршруту
    LaunchedEffect(openRouteId) {
        if (!openRouteId.isNullOrBlank()) {
            nav.navigate("routes") { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true }
            nav.navigate("routes/$openRouteId")
            onRouteOpened()
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val showBar = tabs.any { t -> dest?.route == t.route }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(showBar, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabs.forEach { t ->
                        val selected = dest?.hierarchy?.any { it.route?.substringBefore('/')?.substringBefore('?') == t.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) t.selectedIcon else t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Box(Modifier.padding(pad).consumeWindowInsets(pad)) {
            AppNavHost(nav, session, user, catalog, routesVm, people, admin)
            AchievementToast(toast, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
        }
    }
}

@Composable
private fun AppNavHost(
    nav: NavHostController,
    session: SessionViewModel,
    user: UserProfile,
    catalog: CatalogViewModel,
    routesVm: RoutesViewModel,
    people: PeopleViewModel,
    admin: AdminViewModel,
) {
    val achievements by session.achievements.collectAsStateWithLifecycle()
    val idArg = listOf(navArgument("id") { type = NavType.StringType })
    NavHost(
        nav, startDestination = "catalog",
        enterTransition = { fadeIn() + slideInHorizontally { it / 8 } },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() + slideOutHorizontally { it / 8 } },
    ) {
        composable("catalog") {
            CatalogHomeScreen(catalog, onCategory = { nav.navigate("catalog/cat/$it") }, onProduct = { nav.navigate("product/$it") })
        }
        composable("catalog/cat/{id}", idArg) { e ->
            BrandsScreen(catalog, e.arguments?.getString("id").orEmpty(), nav::popBackStack) { nav.navigate("catalog/brand/$it") }
        }
        composable("catalog/brand/{id}", idArg) { e ->
            SubcategoriesScreen(catalog, e.arguments?.getString("id").orEmpty(), nav::popBackStack) { nav.navigate("catalog/sub/$it") }
        }
        composable("catalog/sub/{id}", idArg) { e ->
            val sub = e.arguments?.getString("id").orEmpty()
            ProductsScreen(catalog, sub, user.isAdmin, nav::popBackStack,
                onProduct = { nav.navigate("product/$it") }, onAdd = { nav.navigate("productEdit?sub=$sub") })
        }
        composable("product/{id}", idArg) { e ->
            val id = e.arguments?.getString("id").orEmpty()
            ProductDetailScreen(id, catalog, routesVm, session, user.isAdmin, nav::popBackStack, onEdit = { nav.navigate("productEdit?id=$id") })
        }
        composable(
            "productEdit?id={id}&sub={sub}",
            listOf(
                navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("sub") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { e ->
            if (!user.isAdmin) { LaunchedEffect(Unit) { nav.popBackStack() }; return@composable }
            val id = e.arguments?.getString("id")
            ProductEditorScreen(
                id, e.arguments?.getString("sub"), catalog, admin,
                onDeleted = {
                    // После удаления карточка товара уже не нужна — выходим и из неё
                    nav.popBackStack()
                    if (nav.currentDestination?.route == "product/{id}") nav.popBackStack()
                },
                onClose = { nav.popBackStack() },
            )
        }
        composable("routes") { RoutesHomeScreen(routesVm) { nav.navigate("routes/$it") } }
        composable("routes/{id}", idArg) { e ->
            RouteDetailScreen(routesVm, e.arguments?.getString("id").orEmpty(), nav::popBackStack) { nav.navigate("product/$it") }
        }
        composable("people") { CollectorsScreen(people, user) { uid -> if (uid == user.uid) nav.navigate("profile") { launchSingleTop = true } else nav.navigate("people/$uid") } }
        composable("people/{id}", idArg) { e ->
            UserProfileScreen(people, e.arguments?.getString("id").orEmpty(), achievements, nav::popBackStack)
        }
        composable("profile") { ProfileScreen(session, user) }
        composable("admin") {
            if (!user.isAdmin) { LaunchedEffect(Unit) { nav.navigate("catalog") }; return@composable }
            AdminScreen(admin, catalog, routesVm, people, session,
                onAddProduct = { nav.navigate("productEdit?sub=$it") },
                onEditProduct = { nav.navigate("productEdit?id=$it") })
        }
    }
}
