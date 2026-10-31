# PhotoCapsules: Implementation Plan

## Goals
* Match the PhotoPills tool set listed in `RESEARCH.md`.
* Use a modular architecture so features and optimizations can be added later.
* Every calculation is verified against independent sources.
* The APK is built by GitHub Actions and published as a workflow artifact.

## Architecture
```
PhotoCapsules/
├── core/astro   (pure Kotlin/JVM) ephemeris facade, sun/moon events, twilight & light types,
│                 moon phase/orientation, supermoon/apsides, seasons, Milky Way / galactic center,
│                 meteor showers (IMO data), eclipses (lunar & solar, local circumstances, map
│                 geometry), Find search, sky-projection math for AR.
│                 Engine: vendored Astronomy Engine (MIT, Don Cross) behind our own API.
├── core/photo   (pure Kotlin/JVM) exposure/EV, ND, aperture scales, CoC (incl. advanced),
│                 DoF (+inverse), FoV (+inverse), hyperfocal & DoF tables, spot stars (500/NPF),
│                 star trails, time lapse, subject distance, focal-length match, camera DB.
├── core/geo     (pure Kotlin/JVM) WGS84 geodesy (Vincenty inverse/direct), elevation angle
│                 with curvature+refraction, horizon dip, coordinate parsing/formatting
│                 (DDD/DMM/DMS), sun/moon size ⇄ distance.
├── core/data    (pure Kotlin/JVM) Plan/POI models, JSON repository, KML/KMZ export/import.
└── app          (Android, Jetpack Compose, Material 3)
      ui/theme, ui/components (value rows, pickers, time bar, moon/phase drawings)
      feature/menu, feature/planner (osmdroid map + overlays, panels, find, load/save),
      feature/sun, feature/moon, feature/meteors, feature/calculators/*, feature/ar (CameraX +
      rotation-vector sensor, Sun/Moon AR, Night AR, DoF/FoV AR), feature/timer (foreground
      service), feature/mystuff (plans, POIs, backup, settings), widget (AppWidgets),
      platform services (location, elevation, geocoding, timezone).
```
* **Pure modules**: all numerical logic lives in pure-JVM modules. They have no Android dependency, so they build and get unit-tested anywhere, including in this sandbox, where Google's Maven host is blocked. The Android app only renders their results.
* **Engine boundary**: the `core:astro` public API (`SkyCalculator`, `SunMoonEvents`, `MilkyWay`, `Eclipses`, `MeteorShowers`, `FindSearch`) hides the engine, so the engine can be swapped (e.g. for JPL DE ephemerides) without touching the UI.

## Free online services (all optional; app works offline)
| Purpose | Service |
|---|---|
| Map tiles | OpenStreetMap standard, OpenTopoMap, Esri World Imagery (via osmdroid) |
| Terrain elevation | Open-Meteo Elevation API (Copernicus DEM GLO-90) |
| Geocoding | Open-Meteo Geocoding + Nominatim (OSM) |
| Time zone auto-detect | Open-Meteo forecast API `timezone=auto` (fallback: manual/device) |
| Nearby POIs | Wikipedia GeoSearch API |
| Magnetic declination | Android `GeomagneticField` (WMM, offline) |

## Key definitions (match PhotoPills)
* **Sunrise/sunset**: upper limb, 34′ refraction. With "height above horizon", the horizon dip is applied.
* **Twilights**: civil −6°, nautical −12°, astronomical −18° (Sun centre, no refraction).
* **Golden hour**: Sun from −4° to +6°. **Blue hour**: −6° to −4°.
* **Galactic Center visibility**: GC (RA 17h45m40.04s, Dec −29°00′28.1″ J2000) above the horizon while the Sun is below −18°.
* **Supermoon**: a full Moon within ±1 day of a perigee, or Earth–Moon centre distance < 360,000 km (configurable).
* **Spot stars**:
  * 500 rule: t = 500/(crop·f).
  * NPF default (simplified, Michaud): t = (35N + 30p)/(f·cos δ).
  * NPF accurate (Michaud full, k = 1): t = (16.856N + 0.0997f + 13.713p)/(f·cos δ).
  * p = pixel pitch in µm = sensor width / horizontal pixels.
* **DoF**:
  * H = f²/(N·c) + f;
  * near = s(H−f)/(H+s−2f);
  * far = s(H−f)/(H−s), or ∞ when s ≥ H.
  * The CoC defaults to sensor diagonal / 1442 (0.030 mm for full frame).
* **Star trails**: 360° per sidereal day (86164.0905 s).

## Verification strategy
1. **Golden vectors**: `tools/verification/*.py` generates JSON vectors from independent libraries, which are committed to `core/*/src/test/resources`. Kotlin unit tests assert agreement within stated tolerances:
   * PyEphem (VSOP87/ELP2000) and astropy/ERFA for Sun/Moon positions, rise/set, twilights, phases and apsides;
   * geographiclib (Karney) for geodesy.
2. **Authoritative spot checks**: USNO Astronomical Applications API (rise/set/twilight, moon phases, seasons) and NASA/Espenak eclipse catalogs, recorded in tests with their source URLs.
3. **Photography formulas**: checked against published worked examples (DOFMaster formulas, Michaud's NPF page) and hand-derived values.
4. **CI**: GitHub Actions runs all core tests on every push, and the APK is built only if they pass.
5. **Independent audit loop**: fresh-context agents re-derive and test everything and probe edge cases (polar day/night, date line, DST transitions, leap years, the Southern Hemisphere, extreme focal lengths, s ≥ H). Fixes are applied and the audit is repeated until clean.

## Milestones
1. Scaffolding: Gradle multi-module, CI workflow, conditional Android build (`-PcoreOnly`).
2. Core engines with tests and golden vectors.
3. App shell: theme, Pills menu, settings, location.
4. Calculators: Exposure, DoF (+Inverse/Advanced), FoV (+Inverse), tables, Spot Stars, Star Trails, Time lapse, Timer, Subject distance, FL match.
5. Sun and Moon pills (info, calendar, seasons/distances).
6. Planner: map, pins, panels, time bar, layers, tools, find, load/save, more.
7. AR views (Sun/Moon, Night, DoF/FoV/Subject distance, Spot Stars declination).
8. Meteor Showers pill, eclipses, widgets, plans/POIs, KMZ backup.
9. Audit/fix iterations.

## Build
* `./gradlew -PcoreOnly=true test` runs the pure modules; it needs only Maven Central and the Gradle Plugin Portal.
* `./gradlew :app:assembleDebug` builds the full Android app and needs Google Maven + the Android SDK. CI does this and uploads `PhotoCapsules-debug.apk` as an artifact.
