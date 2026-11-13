package com.photocapsules.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photocapsules.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Pop-up explanation for a control: shown on long-press (touch) or hover (mouse / stylus).
 * With no [text] the content is shown as it is.
 */
@Composable
fun Tip(text: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (text.isNullOrBlank()) {
        Box(modifier) { content() }
        return
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip(containerColor = Palette.SurfaceHigh, contentColor = Palette.Text) { Text(text, fontSize = 13.sp) } },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}

/** Small "i" button: tap (or hover) for a pop-up that explains the setting next to it. */
@Composable
fun InfoDot(title: String, text: String, modifier: Modifier = Modifier) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
        tooltip = { RichTooltip(title = { Text(title) }) { Text(text) } },
        state = state,
        modifier = modifier,
    ) {
        Icon(
            Icons.Outlined.Info, "About $title", tint = Palette.TextDim,
            modifier = Modifier.size(30.dp).clip(CircleShape).clickable { scope.launch { state.show() } }.padding(6.dp),
        )
    }
}

/** Shrinks the element slightly while it is pressed (tactile feedback for tiles and buttons). */
fun Modifier.pressScale(interaction: InteractionSource): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "pressScale")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Explanations shown in pop-ups, keyed by the label of the row, button or screen they belong to. */
object Help {
    data class Screen(val subtitle: String, val body: String)

    fun row(label: String): String? = rows[label]
    fun action(label: String): String? = actions[label]
    fun screen(title: String): Screen? = screens[title]

    private val rows = mapOf(
        // Lens and camera
        "Camera" to "The camera body sets the sensor size, which drives crop factor, circle of confusion and pixel pitch.",
        "Default camera" to "Camera used by default in every calculator and in the Planner's map tools.",
        "Focal length" to "The real focal length written on the lens, in millimetres (not the 35 mm equivalent).",
        "Focal length as 35 mm equivalent" to "The focal length that gives the same angle of view on a full-frame camera.",
        "Aperture" to "The f-number. Larger numbers (f/11, f/16) give more depth of field and need longer exposures.",
        "Teleconverter" to "Multiplies the focal length and the f-number (×1.4 costs one stop, ×2 costs two).",
        "Crop factor" to "Full-frame diagonal divided by this sensor's diagonal. Multiply the focal length by it for the 35 mm equivalent.",
        "Pixel pitch" to "Size of one pixel: sensor width divided by the horizontal pixel count. Smaller pixels show star trailing sooner.",
        // Depth of field
        "Subject distance" to "Distance from the camera to the point you focus on.",
        "Focus distance" to "Distance from the camera to the point you focus on.",
        "Hyperfocal distance" to "Focus here and everything from half this distance to infinity is acceptably sharp.",
        "Hyperfocal near limit" to "The nearest sharp distance when you focus at the hyperfocal distance (half of it).",
        "Near limit" to "Nearest distance that still looks acceptably sharp.",
        "Far limit" to "Farthest distance that still looks acceptably sharp (∞ once you focus at or beyond the hyperfocal distance).",
        "Total depth of field" to "Distance between the near and far limits of acceptable sharpness.",
        "In front of subject" to "How much of the depth of field lies between the camera and the focus point.",
        "Behind subject" to "How much of the depth of field lies beyond the focus point.",
        "Desired depth of field" to "The total depth of field you want; the calculator solves for the setting that gives it.",
        "Adjusted CoC" to "Circle of confusion recalculated for your print size, viewing distance and eyesight.",
        "Visual acuity" to "How fine a detail the viewer can resolve. Sharper eyesight needs a smaller circle of confusion.",
        "Viewing distance" to "How far the viewer stands from the print. Closer viewing needs a smaller circle of confusion.",
        "Print width" to "Width of the final print or screen image.",
        "Print height" to "Height of the final print or screen image.",
        // Field of view
        "Horizontal angle of view" to "Angle covered across the width of the frame.",
        "Vertical angle of view" to "Angle covered across the height of the frame.",
        "Diagonal angle of view" to "Angle covered corner to corner, the figure lens makers quote.",
        "Horizontal field" to "Width of the scene that fits in the frame at the subject distance.",
        "Vertical field" to "Height of the scene that fits in the frame at the subject distance.",
        "Diagonal field" to "Corner-to-corner size of the scene at the subject distance.",
        "Desired field of view" to "The scene size you want to fit; the calculator solves for focal length or distance.",
        "Subject width" to "Real width of the subject you want to fit in the frame.",
        "Subject height" to "Real height of the subject you want to fit in the frame.",
        "Limited by" to "Which side of the frame the subject fills first.",
        "Camera 2 focal length" to "Focal length on the second camera that gives the same framing.",
        "Camera 2 aperture" to "Aperture on the second camera that gives the same depth of field.",
        // Exposure
        "ND filter" to "Neutral-density filter on the lens. Each stop doubles the exposure time.",
        "Exposure value (EV₁₀₀)" to "Scene brightness on a log scale at ISO 100. One EV is one stop.",
        "Camera EV₁₀₀ (behind filter)" to "The brightness the sensor sees once the ND filter is on.",
        "Nearest camera setting" to "The closest value your camera dial offers, using the stop scale chosen in Settings.",
        "Exact" to "The mathematically exact result, before rounding to a camera dial value.",
        // Stars
        "Declination" to "Angle of the stars from the celestial equator. Stars near the poles (±90°) move slowest, so use the smallest |declination| in your frame.",
        "NPF rule" to "Longest exposure that keeps stars as points, from aperture, pixel pitch and focal length.",
        "500 rule" to "Old rule of thumb: 500 ÷ (crop × focal length) seconds. Too generous for modern sensors.",
        "Rotation angle" to "How far the stars turn around the pole during the exposure (15° per hour).",
        "Rotation rate" to "The sky turns 360° in one sidereal day: about 15.04° per hour.",
        "Exposure time" to "Total time the shutter is open.",
        // Time lapse
        "Shooting interval" to "Time between the start of one photo and the next.",
        "Clip length" to "Duration of the finished video.",
        "Event duration" to "How long the real-world event you are recording lasts.",
        "Frames per second" to "Playback rate of the finished video (24, 25 or 30 are common).",
        "Number of photos" to "Photos needed: clip length × frames per second.",
        "Image size" to "Average file size of one photo, to estimate the card space needed.",
        "Total memory" to "Card space the whole sequence will use.",
        // Sun and Moon
        "Azimuth" to "Compass direction, clockwise from north (90° east, 180° south, 270° west).",
        "Elevation" to "Angle above the horizon. Negative values are below it.",
        "Angular diameter" to "Apparent size of the disc in the sky, about half a degree for both Sun and Moon.",
        "Shadow ratio" to "Shadow length divided by the height of the object casting it.",
        "Time to rise" to "Time left until the next rise.",
        "Time to set" to "Time left until the next set.",
        "Daylight" to "Time between sunrise and sunset.",
        "Transit" to "The moment the body crosses the meridian and stands highest in the sky.",
        "Transit (solar noon)" to "The moment the Sun crosses the meridian and stands highest in the sky.",
        "Illumination" to "Fraction of the Moon's disc that is lit.",
        "Age" to "Days since the last new Moon.",
        "Distance" to "Distance from the Earth's centre to the body's centre.",
        // Planner dialogs
        "Start date" to "First day of the search.",
        "Date range" to "How far ahead of the start date to search.",
        "Azimuth tolerance" to "How far either side of the target azimuth still counts as a match.",
        "Elevation tolerance" to "How far above or below the target elevation still counts as a match.",
        "Use map centre as target" to "Aims at the point in the middle of the map, using its terrain height for the elevation.",
        "Point with AR (camera)" to "Point the phone at the spot in the sky you want; its direction becomes the search target.",
        "Map type" to "Street map, topographic map with contours, or satellite imagery.",
        "Map tool" to "Draws your field of view, depth of field or Sun/Moon size circle on the map.",
        "Sun layer" to "How the Sun is drawn: direction lines, its path across the sky, or the direction of its light.",
        "Moon layer" to "How the Moon is drawn: direction lines, its path across the sky, or the direction of its light.",
        "Show Sun size" to "Draws the Sun's apparent size at the Black Pin's distance.",
        "Show Moon size" to "Draws the Moon's apparent size at the Black Pin's distance.",
        "Show real (geodesic) azimuth lines" to "Draws direction lines as true great circles, which curve on the map over long distances.",
        "Milky Way" to "Shows the Milky Way arch and the direction of the Galactic Center.",
        "Meteor shower" to "Shows where the chosen shower's radiant is and how it moves through the night.",
        "Eclipse" to "Shows the path of a solar eclipse or where a lunar eclipse is visible.",
        "Twilights" to "Tints the map by the kind of light: day, golden hour, blue hour, twilight or night.",
        "Shadow" to "Draws the shadow of an object of the height you set in the Shadow panel.",
        "Reset map layers" to "Back to the defaults: Sun and Moon lines plus the twilight tint.",
        "Red Pin terrain" to "Ground height above sea level at the Red Pin (your shooting spot).",
        "Red Pin offset" to "Extra height above the ground: a building, a tower, a tripod on a roof.",
        "Black Pin terrain" to "Ground height above sea level at the Black Pin (your subject).",
        "Black Pin offset" to "Height of the subject above the ground, such as the top of a tower.",
        "Use automatic terrain altitudes" to "Drops your custom altitudes and looks the terrain up online again.",
        "Height above horizon" to "How much higher you are than the horizon you see. From high up, the Sun rises earlier and sets later.",
        "Horizon Pin altitude" to "Ground height at the Horizon Pin, the point where you see the horizon.",
        "Horizon dip" to "How far the visible horizon drops below eye level because of your height.",
        "Time zone" to "Time zone used to show times. Auto-detect looks it up for the Red Pin's location.",
        "Lock focal length (map handles only rotate)" to "Dragging the handles on the map then only turns the camera, without zooming.",
        "Desired Sun/Moon size" to "How big the Sun or Moon should look next to your subject; the circle shows where to stand.",
        "Drone altitude" to "Flying height above the ground; sets the area the camera covers.",
        "Ground area" to "Size of the ground covered by the frame with the camera pointing straight down.",
        "Composition grid" to "Overlay drawn inside the drone frame to help compose.",
        "Focus at Black Pin" to "Uses the distance to the Black Pin as the focus distance.",
        "Share plan (KMZ)" to "Sends the current pins, date and time as a file that opens in this app or Google Earth.",
        "Add alert to calendar" to "Creates a calendar event at the planned date and time.",
        "Open Red Pin in Maps" to "Opens the shooting spot in your maps app for directions.",
        "Copy coordinates" to "Copies the Red Pin's latitude and longitude.",
        "Geotagged photo" to "Moves the Red Pin to where a photo from your gallery was taken.",
        "Search address / place" to "Looks up a place by name and moves the Red Pin there.",
        "Latitude / longitude" to "Type coordinates in decimal degrees or degrees-minutes-seconds.",
        // Settings
        "Units" to "Metres or feet for distances and heights.",
        "Aperture / shutter / ISO stops" to "Whether dial values step in full, half or third stops, to match your camera.",
        "Coordinate format" to "Decimal degrees, degrees and minutes, or degrees, minutes and seconds.",
        "Azimuth reference" to "Show directions from true north (maps) or magnetic north (a hand compass).",
        "Device usage height" to "Height you hold the phone at, used to draw distances on the ground in AR.",
        "Elevation service" to "Online source for terrain heights, or off to enter them yourself.",
        "Supermoon definition" to "The rule that decides which full Moons count as supermoons.",
        "Time format" to "12-hour or 24-hour clock.",
        // Polar scope
        "Position on the dial" to "Where to put the star on the reticle circle, read like the hour hand of a clock.",
        "Hour angle" to "How far the star has turned past its highest point, in hours (24 h is a full turn).",
        "Local sidereal time" to "Star time at your longitude: the right ascension currently on the meridian.",
        "Distance from the pole" to "How far the star is from the true celestial pole. It is the radius of the reticle circle and changes slowly over the years.",
        "Next upper transit" to "Next time the star is straight above the pole (bottom of the circle in an inverting scope).",
        "Night vision (red)" to "Turns the screen red so it does not spoil your dark adaptation.",
    )

    private val actions = mapOf(
        "AR" to "Overlay this on the live camera view.",
        "Night AR" to "Camera view with the Milky Way, celestial poles and star-trail pattern.",
        "Planner" to "Open this place and time on the Planner map.",
        "Share" to "Send a text summary through another app.",
        "Timer" to "Start a countdown with this exposure time.",
        "Table" to "Show a table of values for other settings.",
        "EV table" to "Typical exposure values for common scenes.",
        "Interval table" to "Suggested shooting intervals for common subjects.",
        "Subject" to "Work out how far to stand to fit a subject in the frame.",
        "To DoF" to "Carry these settings over to the depth of field calculator.",
        "To FoV" to "Carry these settings over to the field of view calculator.",
        "Find" to "Search for the dates and times when the Sun or Moon will be at a chosen direction and height.",
        "Load" to "Go to an address, coordinates, a saved plan, a point of interest or a geotagged photo.",
        "Save" to "Save the pins, date and time as a plan, or the Red Pin as a point of interest.",
        "More" to "Date and time zone, altitudes, horizon correction, sharing and calendar alerts.",
        "GPS" to "Move the Red Pin to where you are now.",
        "Move pin" to "Slide the map under the crosshair, then tap Drop pin to place the Red Pin there.",
        "Drop pin" to "Place the Red Pin under the crosshair.",
        "Undo" to "Take back the last pin move.",
        "Redo" to "Repeat the pin move you just undid.",
        "Expand" to "Hide the info panels to give the map more room.",
        "Swap pins" to "Exchange the Red Pin (you) and the Black Pin (your subject).",
        "Extend lines" to "Draw the Sun and Moon direction lines much longer.",
        "Map type" to "Switch between street, topographic and satellite maps.",
        "Compass" to "Turn the map to match the direction you are facing.",
        "My position" to "Show or hide the blue dot for where you are.",
        "Layers" to "Choose what is drawn on the map: Sun, Moon, Milky Way, eclipses, shadows and tools.",
        "Toolbar" to "Show or hide the map tools.",
        "Black Pin" to "Add or remove the Black Pin, which marks your subject.",
    )

    private val screens = mapOf(
        "Sun" to Screen("Sunrise, sunset, twilights and golden hour",
            "Shows where the Sun is and every light event of the day for the place and date at the top.\n\n• Tap the date card to change place, date or time; the arrows move one day.\n• Drag across the chart to scrub through the day.\n• Calendar lists rise and set for a whole month; Seasons gives equinoxes and solstices."),
        "Moon" to Screen("Phase, rise and set, supermoons",
            "Shows the Moon's phase, position and events for the place and date at the top.\n\n• The picture shows the Moon as it is tilted in your sky.\n• Calendar shows the phase for each day of a month.\n• Distances lists perigees, apogees and supermoons for the year."),
        "Meteor Showers" to Screen("What is active tonight and how many to expect",
            "Lists the showers of the IMO working list with their peak dates.\n\n• Meteors per hour is scaled for the height of the radiant at your location; it assumes a dark sky.\n• The chart shows how the rate changes through the night.\n• Open a shower in Night AR to see where its radiant is."),
        "Exposure" to Screen("Equivalent exposures and ND filters",
            "Take a test shot that is exposed correctly, enter its settings, then change what you need (a slower shutter, an ND filter). The calculator gives the setting that keeps the same exposure.\n\nChoose what to solve for at the top: shutter speed, aperture or ISO."),
        "Depth of field" to Screen("What is sharp, from where to where",
            "Enter camera, focal length, aperture and focus distance to get the near and far limits of sharpness and the hyperfocal distance.\n\n• Inverse solves for the setting that gives a depth of field you choose.\n• Advanced adapts the circle of confusion to your print size and viewing distance."),
        "Field of view" to Screen("How much of the scene fits in the frame",
            "Gives the angle of view of a lens and the size of the scene it covers at a distance.\n\nInverse works out the focal length or distance needed to fit a scene of a given size."),
        "DoF table" to Screen("Depth of field for a lens at a glance",
            "Depth of field for one focal length across apertures (columns) and focus distances (rows). Useful as a quick reference in the field."),
        "Hyperfocal table" to Screen("Hyperfocal distance for every lens and aperture",
            "Hyperfocal distance across focal lengths (rows) and apertures (columns). Focus at that distance and everything from half of it to infinity is sharp."),
        "Spot Stars" to Screen("Longest exposure before stars trail",
            "Gives the longest shutter time that keeps stars as sharp points for your camera and lens.\n\n• NPF is the modern rule; the 500 rule is shown for comparison.\n• Use AR to measure the declination of the part of the sky you are framing."),
        "Star Trails" to Screen("Trail length for an exposure time",
            "Converts between exposure time and how far the stars turn around the pole. The drawing previews the trail pattern."),
        "Time lapse" to Screen("Interval, number of photos and card space",
            "Choose what to solve for: the shooting interval, the length of the final clip, or how long you can record. The rest follows from the frame rate."),
        "Timer" to Screen("Countdown for long exposures",
            "Counts down in the background with a notification and vibrates when time is up. Calculators can send their exposure time here."),
        "Subject distance" to Screen("How far to stand to fit a subject",
            "Enter the size of the subject to get the closest distance at which it fits in the frame with your lens."),
        "Focal length match" to Screen("Same picture on a different camera",
            "Gives the focal length and aperture on a second camera that reproduce the framing and depth of field of the first."),
        "Polar scope" to Screen("Polar-align an equatorial mount or star tracker",
            "Shows where Polaris (north) or σ Octantis (south) must sit on the circle of your polar scope's reticle right now.\n\n1. Level the mount and aim its polar axis roughly at the pole.\n2. Look through the polar scope and find the star.\n3. Turn the mount's altitude and azimuth bolts until the star sits on the circle at the position shown here.\n\nMost polar scopes show the sky upside down, which is the default view. Switch to Naked eye to see the sky as it looks without a scope."),
        "Plans" to Screen("Your saved Planner setups",
            "Each plan stores the pins, date, time and notes. Tap one to open it in the Planner."),
        "Points of Interest" to Screen("Saved locations",
            "Places you saved, plus notable places near you from Wikipedia. Tap one to open it in the Planner."),
        "Backup" to Screen("Export and import as KMZ",
            "Creates a Google Earth KMZ file with all plans and points of interest, and imports KMZ or KML files."),
        "Settings" to Screen("Units, camera, map and AR", "Preferences used throughout the app. Tap the i next to a setting for details."),
    )
}
