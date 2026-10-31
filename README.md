# PhotoCapsules

PhotoCapsules is an Android app for planning landscape and astro photography, modelled on the tool set of PhotoPills. It works out where and when the Sun, Moon, Milky Way, meteor-shower radiants and eclipses will appear, and includes the usual photography calculators.

## Features
* **Planner**
  * Map (OpenStreetMap, topographic or satellite) with a Red Pin (observer) and a Black Pin (subject).
  * 11 info panels:
    * shadow;
    * pin-to-pin geodetics, including Earth curvature;
    * Sun/Moon position and rise/set times;
    * twilights, golden hour and blue hour;
    * Galactic Center visibility and Milky Way position;
    * eclipses and their phases;
    * meteor showers.
  * Map layers:
    * Sun and Moon (standard, path or light direction, plus size and geodesic azimuth lines);
    * Milky Way, meteor radiant, solar eclipse paths and lunar eclipse visibility;
    * twilight tint and shadow.
  * Map tools (FoV, DoF, Sun/Moon size), drone mode with composition grids, and a colour time bar.
  * **Find**: searches for when the Sun or Moon will be at a given azimuth and elevation.
  * Load/Save of plans and points of interest, horizon-height correction, custom altitudes, KMZ sharing and calendar alerts.
* **Sun / Moon**: daily events, elevation charts and information tables, monthly calendars, seasons, supermoons, perigees and apogees.
* **Meteor showers**: the IMO 2026 working list, live meteors per hour, the night's activity graph and a calendar by class.
* **Calculators**: Exposure (with ND filters and an EV table), DoF (classic, inverse and advanced CoC), FoV (classic and inverse), DoF table, hyperfocal table, Spot Stars (500 and NPF rules), Star Trails, Time-lapse, Subject distance, Focal-length match and Timer.
* **AR**: Sun/Moon AR, Night AR (Milky Way, celestial equator and poles, RA/Dec grid, star-trail pattern, radiants), DoF/hyperfocal ground lines, a FoV frame and a Spot Stars declination meter. AR is corrected for magnetic declination and supports visual calibration.
* **My Stuff**: plans, points of interest (including nearby Wikipedia places), KMZ backup and import, and settings.
* **Widgets**: Sun and light, Moon, Milky Way, and upcoming plans.

## Architecture
```
core/astro  pure Kotlin  – astronomy (vendored Astronomy Engine, MIT) + our domain API
core/photo  pure Kotlin  – photography calculators + camera database
core/geo    pure Kotlin  – WGS84 geodesy, terrain line of sight, coordinate formats
core/data   pure Kotlin  – plans/POIs repository, KML/KMZ
app         Android (Jetpack Compose, osmdroid, CameraX)
```
All numerical code lives in the pure-JVM modules, so it can be tested without the Android SDK. See `docs/PLAN.md` and `docs/RESEARCH.md`.

## Verification
The core tests (`./gradlew -PcoreOnly=true test`) compare results against independent sources:
* **PyEphem**: Sun/Moon positions, rise/set, twilights, moon phases, seasons, apsides and fixed stars at 12 locations, including polar and Southern Hemisphere sites and the date line.
* **USNO Astronomical Applications API**: rise/set/transit/twilight, 2026 moon phases and 2026 seasons.
* **NASA eclipse catalogs** (F. Espenak):
  * greatest-eclipse times, types and magnitudes, 2025–2030;
  * the path of totality for the 12 Aug 2026 total solar eclipse, checked at three points, with magnitude 1.0 at the published northern and southern limits.
* **IMO 2026 calendar**: meteor-shower peak dates.
* **geographiclib**: Vincenty inverse and direct geodesy.
* **Published photography formulas**: DOFMaster depth of field, Michaud's NPF rule and standard angle-of-view values.

`tools/verification/*.py` regenerate the golden vectors.

## Build
* **CI**: GitHub Actions builds `PhotoCapsules-debug.apk` and publishes it as the **PhotoCapsules-apk** artifact of each run.
* **Locally**: run `./gradlew :app:assembleDebug`. This needs the Android SDK and JDK 17.

## Data & credits
* Map tiles © OpenStreetMap contributors / OpenTopoMap / Esri.
* Elevation and time zones from Open-Meteo; geocoding from Open-Meteo and Nominatim.
* Ephemerides from Astronomy Engine by Don Cross (MIT).
* Meteor-shower data from the International Meteor Organization.
