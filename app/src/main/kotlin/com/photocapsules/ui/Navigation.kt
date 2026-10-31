package com.photocapsules.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.photocapsules.feature.academy.GlossaryScreen
import com.photocapsules.feature.ar.ArMode
import com.photocapsules.feature.ar.ArScreen
import com.photocapsules.feature.calculators.DofScreen
import com.photocapsules.feature.calculators.DofTableScreen
import com.photocapsules.feature.calculators.ExposureScreen
import com.photocapsules.feature.calculators.FocalMatchScreen
import com.photocapsules.feature.calculators.FovScreen
import com.photocapsules.feature.calculators.HyperfocalTableScreen
import com.photocapsules.feature.calculators.SpotStarsScreen
import com.photocapsules.feature.calculators.StarTrailsScreen
import com.photocapsules.feature.calculators.SubjectDistanceScreen
import com.photocapsules.feature.calculators.TimeLapseScreen
import com.photocapsules.feature.menu.MenuScreen
import com.photocapsules.feature.meteors.MeteorScreen
import com.photocapsules.feature.moon.MoonScreen
import com.photocapsules.feature.mystuff.BackupScreen
import com.photocapsules.feature.mystuff.PlansScreen
import com.photocapsules.feature.mystuff.PoisScreen
import com.photocapsules.feature.mystuff.SettingsScreen
import com.photocapsules.feature.planner.PlannerScreen
import com.photocapsules.feature.sun.SunScreen
import com.photocapsules.feature.timer.TimerScreen

object Routes {
    const val MENU = "menu"
    const val PLANNER = "planner"
    const val SUN = "sun"
    const val MOON = "moon"
    const val METEORS = "meteors"
    const val EXPOSURE = "exposure"
    const val DOF = "dof"
    const val FOV = "fov"
    const val DOF_TABLE = "doftable"
    const val HYPERFOCAL = "hyperfocal"
    const val SPOT_STARS = "spotstars"
    const val STAR_TRAILS = "startrails"
    const val TIME_LAPSE = "timelapse"
    const val TIMER = "timer"
    const val SUBJECT = "subject"
    const val FL_MATCH = "flmatch"
    const val PLANS = "plans"
    const val POIS = "pois"
    const val BACKUP = "backup"
    const val SETTINGS = "settings"
    const val GLOSSARY = "glossary"
    fun ar(mode: ArMode) = "ar/${mode.name}"
}

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    val go: (String) -> Unit = { nav.navigate(it) }
    NavHost(nav, startDestination = Routes.MENU) {
        composable(Routes.MENU) { MenuScreen(go) }
        composable(Routes.PLANNER) { PlannerScreen(back, go) }
        composable(Routes.SUN) { SunScreen(back, go) }
        composable(Routes.MOON) { MoonScreen(back, go) }
        composable(Routes.METEORS) { MeteorScreen(back, go) }
        composable(Routes.EXPOSURE) { ExposureScreen(back, go) }
        composable(Routes.DOF) { DofScreen(back, go) }
        composable(Routes.FOV) { FovScreen(back, go) }
        composable(Routes.DOF_TABLE) { DofTableScreen(back, go) }
        composable(Routes.HYPERFOCAL) { HyperfocalTableScreen(back, go) }
        composable(Routes.SPOT_STARS) { SpotStarsScreen(back, go) }
        composable(Routes.STAR_TRAILS) { StarTrailsScreen(back, go) }
        composable(Routes.TIME_LAPSE) { TimeLapseScreen(back, go) }
        composable(Routes.TIMER) { TimerScreen(back) }
        composable(Routes.SUBJECT) { SubjectDistanceScreen(back, go) }
        composable(Routes.FL_MATCH) { FocalMatchScreen(back) }
        composable(Routes.PLANS) { PlansScreen(back, go) }
        composable(Routes.POIS) { PoisScreen(back, go) }
        composable(Routes.BACKUP) { BackupScreen(back) }
        composable(Routes.SETTINGS) { SettingsScreen(back) }
        composable(Routes.GLOSSARY) { GlossaryScreen(back) }
        composable("ar/{mode}") { entry ->
            val mode = runCatching { ArMode.valueOf(entry.arguments?.getString("mode") ?: "") }.getOrDefault(ArMode.SUN_MOON)
            ArScreen(mode, back)
        }
    }
}

fun NavHostController.safeNavigate(route: String) = runCatching { navigate(route) }
