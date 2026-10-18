package org.arkikeskus.launcher.data.smartspace

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.di.ApplicationScope
import java.net.URLEncoder
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Current conditions for the smartspace widget's weather slot. */
data class CurrentWeather(
    val temperatureC: Double,
    val weatherCode: Int,
    /** Reverse-geocoded locality ("Espoo"); null when no geocoder backend or no name resolves. */
    val city: String?,
    val fetchedAt: Long,
    /** The day's details and the hours and days ahead; null if that part of the reply was unusable. */
    val forecast: WeatherForecast? = null,
)

/** A place picked by name in Settings ▸ weather location. */
data class WeatherPlace(val name: String, val latitude: Double, val longitude: Double, val country: String = "") {
    fun serialize(): String = "$name\t$latitude\t$longitude"

    companion object {
        fun parse(raw: String): WeatherPlace? {
            val parts = raw.split('\t')
            if (parts.size != 3 || parts[0].isBlank()) return null
            return WeatherPlace(parts[0], parts[1].toDoubleOrNull() ?: return null, parts[2].toDoubleOrNull() ?: return null)
        }
    }
}

/** Everything beyond "now" that the weather day widget shows. Temperatures in °C, wind in km/h. */
data class WeatherForecast(
    val isDay: Boolean,
    val feelsLikeC: Double?,
    val windKmh: Double?,
    val humidity: Int?,
    val uvIndex: Double?,
    val highC: Double?,
    val lowC: Double?,
    /** Today's highest chance of rain, 0..100. */
    val rainChance: Int?,
    /** Local clock times, "HH:mm". */
    val sunrise: String?,
    val sunset: String?,
    val hours: List<HourForecast>,
    val days: List<DayForecast>,
)

data class HourForecast(val hour: Int, val temperatureC: Double, val weatherCode: Int, val rainChance: Int, val isDay: Boolean)

/** [date] is the local ISO date ("2026-10-07"). */
data class DayForecast(val date: String, val weatherCode: Int, val highC: Double, val lowC: Double, val rainChance: Int)

/**
 * Current weather from the keyless Open-Meteo API for the smartspace widget. Needs a (coarse)
 * location permission; without one it emits null and never touches the network. Results are cached
 * in memory and re-fetched at most every [REFRESH_INTERVAL_MS] (the widget refreshes on home resume).
 */
@Singleton
class WeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
) {
    private val _weather = MutableStateFlow<CurrentWeather?>(null)
    val weather: StateFlow<CurrentWeather?> = _weather.asStateFlow()

    private val fetching = AtomicBoolean(false)

    // After a failed attempt (offline, no location fix) the next automatic one waits a while,
    // longer each time, so a phone without signal isn't woken for the network on every visit home.
    @Volatile private var failures = 0
    @Volatile private var failedAt = 0L

    private fun noteResult(ok: Boolean) {
        _unavailable.value = !ok
        if (ok) {
            failures = 0
        } else {
            failures = (failures + 1).coerceAtMost(8)
            failedAt = android.os.SystemClock.elapsedRealtime()
        }
    }

    /** True after an attempt that produced no weather (no network, no location fix, bad reply). */
    private val _unavailable = MutableStateFlow(false)
    val unavailable: StateFlow<Boolean> = _unavailable.asStateFlow()

    /** The place chosen in Settings, which replaces the device's location; null = use the device's. */
    @Volatile
    private var place: WeatherPlace? = null

    init {
        scope.launch {
            runCatching {
                settingsRepository.weatherLocation.collect { raw ->
                    val chosen = WeatherPlace.parse(raw)
                    if (chosen != place) {
                        place = chosen
                        failures = 0
                        // The cached weather belongs to the previous place.
                        _weather.value = null
                        refresh()
                    }
                }
            }
        }
    }

    /** True when weather can be fetched: a place is set in Settings, or location is allowed. */
    fun hasPermission(): Boolean = place != null || hasLocationPermission()

    private fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun isBatterySaverActive(): Boolean = runCatching {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        powerManager?.isPowerSaveMode == true
    }.getOrDefault(false)

    fun refresh(force: Boolean = false) {
        if (!hasPermission()) {
            _weather.value = null
            return
        }
        val cached = _weather.value
        val interval = if (isBatterySaverActive()) REFRESH_INTERVAL_MS * 2 else REFRESH_INTERVAL_MS
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < interval) return
        if (!force && failures > 0) {
            val wait = (RETRY_BASE_MS shl (failures - 1)).coerceAtMost(REFRESH_INTERVAL_MS)
            if (android.os.SystemClock.elapsedRealtime() - failedAt < wait) return
        }
        if (!fetching.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            val startedFor = place
            try {
                val fixed = startedFor
                if (fixed != null) {
                    val fetched = runCatching { fetch(fixed.latitude, fixed.longitude) }
                        .onFailure { Log.w(TAG, "Weather fetch failed: ${it.javaClass.simpleName}") }
                        .getOrNull()
                    noteResult(fetched != null)
                    // Dropped if the place was changed while this was on its way.
                    if (fetched != null && place == fixed) _weather.value = fetched.copy(city = fixed.name)
                    return@launch
                }
                val location = lastKnownLocation()
                if (location == null) {
                    // No fix yet: say so instead of showing some other town's weather.
                    Log.d(TAG, "No location fix for weather")
                    noteResult(false)
                    return@launch
                }
                // Rounded for the network query — ~1 km is all weather needs, and the exact fix
                // never leaves the device. The LOCAL geocoder gets the exact fix; see cityName().
                val exactLat = location.latitude
                val exactLon = location.longitude
                val lat = Math.round(exactLat * 100) / 100.0
                val lon = Math.round(exactLon * 100) / 100.0
                val fetched = runCatching { fetch(lat, lon) }
                    .onFailure { Log.w(TAG, "Weather fetch failed: ${it.javaClass.simpleName}") }
                    .getOrNull()
                noteResult(fetched != null)
                // Dropped if a place was typed in while this was on its way.
                if (fetched != null && place == null) _weather.value = fetched.copy(city = resolveCity(exactLat, exactLon, lat, lon))
            } finally {
                fetching.set(false)
                // The place changed during the fetch: its own refresh() was turned away, so run it now.
                if (place != startedFor) refresh()
            }
        }
    }

    /** A cached fix is plenty for weather — no active location request, no battery cost. */
    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        
        // Reading the last fixes only reads cached values: no provider is woken up.
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }
        // Newest fix wins regardless of provider — provider order alone let a stale network fix
        // beat a fresh GPS one and pin the weather to a previous town.
        return providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /** App-identifying User-Agent WITHOUT device details — Android's default Dalvik UA names the
     *  device model and OS build, which has no business going to third-party weather services. */
    private val userAgent: String by lazy {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        "Folark/$version"
    }

    private fun fetch(lat: Double, lon: Double): CurrentWeather? {
        val url = URL(
            // The whole address is formatted, not just its last piece: the brackets matter.
            (
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f" +
                    "&current=temperature_2m,weather_code,is_day,apparent_temperature,wind_speed_10m,relative_humidity_2m" +
                    "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max" +
                    "&timezone=auto&forecast_days=6"
                ).format(Locale.US, lat, lon),
        )
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.setRequestProperty("User-Agent", userAgent)
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val current = root.getJSONObject("current")
            CurrentWeather(
                temperatureC = current.getDouble("temperature_2m"),
                weatherCode = current.getInt("weather_code"),
                city = null,
                fetchedAt = System.currentTimeMillis(),
                // The forecast is a bonus: a reply without it still gives the current conditions.
                forecast = runCatching { parseForecast(root, current) }.getOrNull(),
            )
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Looks [query] up with Open-Meteo's keyless place search and returns the best match, or null
     * when nothing matches or the network fails. Only the typed text and the interface language are sent.
     */
    suspend fun findPlace(query: String): WeatherPlace? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(
                "https://geocoding-api.open-meteo.com/v1/search?count=1&format=json&language=%s&name=%s"
                    .format(Locale.US, Locale.getDefault().language, URLEncoder.encode(query.trim(), "UTF-8")),
            )
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.setRequestProperty("User-Agent", userAgent)
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val first = JSONObject(body).optJSONArray("results")?.optJSONObject(0) ?: return@runCatching null
                WeatherPlace(
                    name = first.getString("name").replace('\t', ' '),
                    latitude = first.getDouble("latitude"),
                    longitude = first.getDouble("longitude"),
                    country = first.optString("country"),
                )
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private fun JSONObject.doubleOrNull(name: String): Double? = if (isNull(name)) null else optDouble(name).takeUnless { it.isNaN() }

    private fun parseForecast(root: JSONObject, current: JSONObject): WeatherForecast {
        val hourly = root.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val temps = hourly.getJSONArray("temperature_2m")
        val codes = hourly.getJSONArray("weather_code")
        val rain = hourly.optJSONArray("precipitation_probability")
        val day = hourly.optJSONArray("is_day")
        // Times are local ISO ("2026-10-07T14:00"), so they compare as text: start at this hour.
        val nowHour = current.optString("time").take(13) + ":00"
        val hours = buildList {
            for (i in 0 until times.length()) {
                val time = times.getString(i)
                if (time < nowHour || temps.isNull(i)) continue
                add(
                    HourForecast(
                        hour = time.substring(11, 13).toInt(),
                        temperatureC = temps.getDouble(i),
                        weatherCode = codes.optInt(i, 3),
                        rainChance = rain?.optInt(i, 0) ?: 0,
                        isDay = (day?.optInt(i, 1) ?: 1) == 1,
                    ),
                )
                if (size >= MAX_HOURS) break
            }
        }
        val daily = root.getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        val dCodes = daily.getJSONArray("weather_code")
        val highs = daily.getJSONArray("temperature_2m_max")
        val lows = daily.getJSONArray("temperature_2m_min")
        val dRain = daily.optJSONArray("precipitation_probability_max")
        val days = buildList {
            for (i in 0 until dates.length()) {
                if (highs.isNull(i) || lows.isNull(i)) continue
                add(DayForecast(dates.getString(i), dCodes.optInt(i, 3), highs.getDouble(i), lows.getDouble(i), dRain?.optInt(i, 0) ?: 0))
            }
        }
        fun clock(name: String): String? = daily.optJSONArray(name)?.optString(0)?.takeIf { it.length >= 16 }?.substring(11, 16)
        return WeatherForecast(
            isDay = current.optInt("is_day", 1) == 1,
            feelsLikeC = current.doubleOrNull("apparent_temperature"),
            windKmh = current.doubleOrNull("wind_speed_10m"),
            humidity = current.doubleOrNull("relative_humidity_2m")?.toInt(),
            uvIndex = daily.optJSONArray("uv_index_max")?.let { if (it.length() > 0 && !it.isNull(0)) it.getDouble(0) else null },
            highC = days.firstOrNull()?.highC,
            lowC = days.firstOrNull()?.lowC,
            rainChance = days.firstOrNull()?.rainChance,
            sunrise = clock("sunrise"),
            sunset = clock("sunset"),
            hours = hours,
            days = days,
        )
    }

    /** Last successfully geocoded name + the rounded area it belongs to: a transient geocoder
     *  failure must not blank a name the user was already seeing, but a clearly different area
     *  must not keep showing the previous town. Guarded by the [fetching] gate (single writer). */
    private var lastCity: String? = null
    private var lastCityAreaKey: String? = null
    private var lastCityAtMs = 0L
    private var lastCityLocale: Locale? = null

    private fun resolveCity(exactLat: Double, exactLon: Double, roundedLat: Double, roundedLon: Double): String? {
        val areaKey = "%.2f,%.2f".format(Locale.US, roundedLat, roundedLon)
        // Same area, same language, looked up in the last hours: the name can't have changed, so
        // the geocoder (a network request) isn't asked again.
        val locale = Locale.getDefault()
        if (lastCity != null && areaKey == lastCityAreaKey && locale == lastCityLocale &&
            System.currentTimeMillis() - lastCityAtMs < CITY_REUSE_MS
        ) return lastCity
        val fresh = cityName(exactLat, exactLon) ?: networkCityName(roundedLat, roundedLon)
        if (fresh != null) {
            lastCity = fresh
            lastCityAreaKey = areaKey
            lastCityAtMs = System.currentTimeMillis()
            lastCityLocale = locale
            return fresh
        }
        // Same rounded area AND recent enough: a transient failure must not blank the name, but a
        // dead geocoder must not pin a stale name forever either.
        val cacheAlive = System.currentTimeMillis() - lastCityAtMs < CITY_CACHE_MAX_AGE_MS
        return if (areaKey == lastCityAreaKey && cacheAlive) lastCity else null
    }

    /** Locality via the platform Geocoder — works worldwide on devices with a geocoder backend
     *  (localized names); null elsewhere. Checks several results and fields: older OEM backends
     *  (e.g. Samsung A40) may leave locality empty and put the usable name in another field.
     *  The sync call is fine on this IO thread. Logs never include coordinates. */
    private fun cityName(lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) {
            Log.d(TAG, "Reverse geocoding skipped: no geocoder backend")
            return null
        }
        return try {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 3).orEmpty()
            if (addresses.isEmpty()) {
                Log.d(TAG, "Reverse geocoding returned no addresses")
                return null
            }
            val name = addresses.firstNotNullOfOrNull { a ->
                PlaceNames.preferred(listOf(a.locality, a.subLocality, a.subAdminArea, a.adminArea, a.featureName))
            }
            if (name == null) Log.d(TAG, "Reverse geocoding found no usable name field")
            name
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocoding failed: ${e.javaClass.simpleName}")
            null
        }
    }

    /** Network reverse-geocode fallback for devices whose platform Geocoder has no usable backend
     *  (the Samsung A40 symptom): BigDataCloud's keyless client API. Client-side calls with the
     *  device's own current fix are exactly its permitted use. Sends only the SAME 2-decimal
     *  rounded coordinates the weather query already sends — the exact fix never leaves the device.
     *  Reached at most once per 30-min weather refresh, and only when the local geocoder failed. */
    private fun networkCityName(roundedLat: Double, roundedLon: Double): String? {
        val url = URL(
            "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=%.2f&longitude=%.2f&localityLanguage=%s"
                .format(Locale.US, roundedLat, roundedLon, Locale.getDefault().language),
        )
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.setRequestProperty("User-Agent", userAgent)
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            PlaceNameFallback.parseCity(body).also {
                if (it == null) Log.d(TAG, "Network reverse geocoding found no usable name")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network reverse geocoding failed: ${e.javaClass.simpleName}")
            null
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 30L * 60 * 1000
        /** First wait after a failed attempt; doubles with each further failure, up to the refresh interval. */
        private const val RETRY_BASE_MS = 60L * 1000
        private const val CITY_CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000
        private const val CITY_REUSE_MS = 6L * 60 * 60 * 1000
        private const val MAX_HOURS = 24
        private const val TAG = "WeatherRepo"
    }
}
