package com.photocapsules.feature.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photocapsules.feature.ar.ArMode
import com.photocapsules.ui.Routes
import com.photocapsules.ui.components.Card
import com.photocapsules.ui.components.InfoText
import com.photocapsules.ui.components.SectionTitle
import com.photocapsules.ui.components.ValueRow
import com.photocapsules.ui.theme.Palette

private data class PillItem(val label: String, val icon: ImageVector, val color: Color, val route: String)

private val pills = listOf(
    PillItem("Planner", Icons.Filled.Map, Color(0xFFE85D4A), Routes.PLANNER),
    PillItem("Sun", Icons.Filled.WbSunny, Color(0xFFFFB300), Routes.SUN),
    PillItem("Moon", Icons.Filled.NightsStay, Color(0xFF5C9DFF), Routes.MOON),
    PillItem("Exposure", Icons.Filled.Exposure, Color(0xFF8E6CFF), Routes.EXPOSURE),
    PillItem("DoF", Icons.Filled.CenterFocusStrong, Color(0xFF2EC4B6), Routes.DOF),
    PillItem("FoV", Icons.Filled.Crop, Color(0xFF3DDC84), Routes.FOV),
    PillItem("DoF Table", Icons.Filled.TableChart, Color(0xFF26A69A), Routes.DOF_TABLE),
    PillItem("Hyperfocal", Icons.Filled.GridOn, Color(0xFF00897B), Routes.HYPERFOCAL),
    PillItem("Night AR", Icons.Filled.BlurOn, Color(0xFF5E35B1), Routes.ar(ArMode.NIGHT)),
    PillItem("Meteor Showers", Icons.Filled.AutoAwesome, Color(0xFFEC407A), Routes.METEORS),
    PillItem("Star Trails", Icons.Filled.Toll, Color(0xFF3949AB), Routes.STAR_TRAILS),
    PillItem("Spot Stars", Icons.Filled.CameraAlt, Color(0xFF7E57C2), Routes.SPOT_STARS),
    PillItem("Time lapse", Icons.Filled.Timelapse, Color(0xFFFF7043), Routes.TIME_LAPSE),
    PillItem("Timer", Icons.Filled.Timer, Color(0xFFEF5350), Routes.TIMER),
    PillItem("Subject distance", Icons.Filled.Straighten, Color(0xFF66BB6A), Routes.SUBJECT),
    PillItem("Focal length match", Icons.Filled.CompareArrows, Color(0xFF29B6F6), Routes.FL_MATCH),
)

@Composable
fun MenuScreen(go: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(1) }
    Scaffold(
        containerColor = Palette.Background,
        bottomBar = {
            NavigationBar(containerColor = Palette.Surface) {
                val colors = NavigationBarItemDefaults.colors(selectedIconColor = Palette.Accent, selectedTextColor = Palette.Accent, indicatorColor = Palette.SurfaceHigh)
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Person, null) }, label = { Text("My Stuff") }, colors = colors)
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.Apps, null) }, label = { Text("Pills") }, colors = colors)
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.School, null) }, label = { Text("Academy") }, colors = colors)
            }
        },
    ) { padding ->
        when (tab) {
            0 -> MyStuffMenu(padding, go)
            1 -> PillsGrid(padding, go)
            else -> AcademyMenu(padding, go)
        }
    }
}

@Composable
private fun PillsGrid(padding: PaddingValues, go: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        Text("PhotoCapsules", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        LazyVerticalGrid(GridCells.Adaptive(96.dp), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(pills) { p ->
                Column(Modifier.clickable {
                    if (p.route.startsWith("ar/")) com.photocapsules.data.AppState.ar = com.photocapsules.data.ArRequest(com.photocapsules.data.AppState.sky.value)
                    go(p.route)
                }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(62.dp).background(p.color, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(p.icon, p.label, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(p.label, color = Palette.Text, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun MyStuffMenu(padding: PaddingValues, go: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("My Stuff", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
        Card {
            MenuRow(Icons.Filled.Bookmarks, "Plans", "Your saved photo plans") { go(Routes.PLANS) }
            MenuRow(Icons.Filled.Place, "Points of Interest", "Saved locations & nearby places") { go(Routes.POIS) }
            MenuRow(Icons.Filled.Backup, "Backup", "Export / import KMZ") { go(Routes.BACKUP) }
            MenuRow(Icons.Filled.Settings, "Settings", "Units, camera, map, AR…") { go(Routes.SETTINGS) }
        }
    }
}

@Composable
private fun AcademyMenu(padding: PaddingValues, go: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Academy", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
        Card { MenuRow(Icons.Filled.MenuBook, "Glossary & how-to", "Light types, azimuth, DoF, NPF rule, planning workflow…") { go(Routes.GLOSSARY) } }
        SectionTitle("Data sources")
        Card {
            ValueRow("Ephemerides", "Astronomy Engine (VSOP87/ELP)")
            ValueRow("Meteor showers", "IMO Working List 2026")
            ValueRow("Maps", "© OpenStreetMap contributors")
            ValueRow("Elevation / geocoding", "Open-Meteo, Nominatim")
            ValueRow("Nearby places", "Wikipedia GeoSearch")
        }
        InfoText("Calculations are verified against USNO, NASA eclipse catalogs, PyEphem and geographiclib in automated tests.")
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Palette.Accent)
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, color = Palette.Text, fontSize = 16.sp)
            Text(sub, color = Palette.TextDim, fontSize = 12.sp)
        }
    }
}
