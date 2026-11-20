package dev.foldterm.launcher.shell

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Offline unit conversion: "5 km in mi", "350 f to c", "2 cups in ml". */
object Units {
    private data class U(val dim: String, val factor: Double)

    // factor converts to the dimension's base unit (m, kg, l, m/s, byte, s, m², W? not needed)
    private val table: Map<String, U> = buildMap {
        fun add(dim: String, factor: Double, vararg names: String) = names.forEach { put(it, U(dim, factor)) }
        add("len", 0.001, "mm", "millimeter", "millimeters", "millimetre", "millimetres")
        add("len", 0.01, "cm", "centimeter", "centimeters", "centimetre", "centimetres")
        add("len", 1.0, "m", "meter", "meters", "metre", "metres")
        add("len", 1000.0, "km", "kilometer", "kilometers", "kilometre", "kilometres", "kms")
        add("len", 0.0254, "in", "inch", "inches", "\"")
        add("len", 0.3048, "ft", "foot", "feet", "'")
        add("len", 0.9144, "yd", "yard", "yards")
        add("len", 1609.344, "mi", "mile", "miles")
        add("len", 1852.0, "nmi", "nauticalmile")
        add("mass", 0.001, "g", "gram", "grams")
        add("mass", 1.0, "kg", "kilo", "kilos", "kilogram", "kilograms")
        add("mass", 0.028349523125, "oz", "ounce", "ounces")
        add("mass", 0.45359237, "lb", "lbs", "pound", "pounds")
        add("mass", 6.35029318, "st", "stone", "stones")
        add("mass", 1000.0, "t", "tonne", "tonnes")
        add("vol", 0.001, "ml", "milliliter", "milliliters", "millilitre", "millilitres")
        add("vol", 1.0, "l", "liter", "liters", "litre", "litres")
        add("vol", 0.00492892159375, "tsp", "teaspoon", "teaspoons")
        add("vol", 0.01478676478125, "tbsp", "tablespoon", "tablespoons")
        add("vol", 0.25, "cup", "cups") // metric cup
        add("vol", 0.0295735295625, "floz", "fl.oz")
        add("vol", 0.473176473, "pt", "pint", "pints")
        add("vol", 3.785411784, "gal", "gallon", "gallons")
        add("speed", 1.0, "m/s", "mps")
        add("speed", 1 / 3.6, "kmh", "km/h", "kph")
        add("speed", 0.44704, "mph")
        add("speed", 0.514444, "kn", "knot", "knots")
        add("data", 1.0, "b", "byte", "bytes")
        add("data", 1024.0, "kb", "kib")
        add("data", 1024.0 * 1024, "mb", "mib")
        add("data", 1024.0 * 1024 * 1024, "gb", "gib")
        add("data", 1024.0 * 1024 * 1024 * 1024, "tb", "tib")
        add("time", 1.0, "s", "sec", "secs", "second", "seconds")
        add("time", 60.0, "min", "mins", "minute", "minutes")
        add("time", 3600.0, "h", "hr", "hrs", "hour", "hours")
        add("time", 86400.0, "d", "day", "days")
        add("time", 604800.0, "wk", "week", "weeks")
        add("area", 1.0, "m2", "sqm")
        add("area", 0.09290304, "ft2", "sqft")
        add("area", 4046.8564224, "acre", "acres")
        add("area", 10000.0, "ha", "hectare", "hectares")
    }
    private val temps = mapOf(
        "c" to "c", "°c" to "c", "celsius" to "c", "f" to "f", "°f" to "f", "fahrenheit" to "f", "k" to "k", "kelvin" to "k",
    )

    private val pattern = Regex("^(-?[\\d.,]+)\\s*([a-z°/'\".0-9]+?)\\s+(?:in|to|as|->|=)\\s+([a-z°/'\".0-9]+)$", RegexOption.IGNORE_CASE)

    data class Result(val value: Double, val from: String, val to: String) {
        override fun toString() = "${Calc.format(value)} $to"
    }

    /** Converts when [q] looks like "<number> <unit> in <unit>"; null otherwise. */
    fun convert(q: String): Result? {
        val m = pattern.matchEntire(q.trim()) ?: return null
        val n = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val a = m.groupValues[2].lowercase()
        val b = m.groupValues[3].lowercase()
        val ta = temps[a]; val tb = temps[b]
        if (ta != null && tb != null) {
            val c = when (ta) { "c" -> n; "f" -> (n - 32) * 5 / 9; else -> n - 273.15 }
            val out = when (tb) { "c" -> c; "f" -> c * 9 / 5 + 32; else -> c + 273.15 }
            return Result(round(out), a, "°${tb.uppercase()}".replace("°K", "K"))
        }
        val ua = table[a] ?: return null
        val ub = table[b] ?: return null
        if (ua.dim != ub.dim) return null
        return Result(round(n * ua.factor / ub.factor), a, b)
    }

    private fun round(v: Double) = Math.round(v * 10000.0) / 10000.0

    private val currencyPattern = Regex("^(-?[\\d.,]+)\\s*([a-z]{3})\\s+(?:in|to)\\s+([a-z]{3})$", RegexOption.IGNORE_CASE)

    /** Parses "100 usd in aud" into (amount, FROM, TO). */
    fun currencyQuery(q: String): Triple<Double, String, String>? {
        val m = currencyPattern.matchEntire(q.trim()) ?: return null
        val n = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val a = m.groupValues[2].uppercase(); val b = m.groupValues[3].uppercase()
        if (table.containsKey(a.lowercase()) || table.containsKey(b.lowercase())) return null
        return Triple(n, a, b)
    }

    /** "tokyo" → current time there, using the IANA zone list (city part of the id). */
    fun timeIn(place: String, now: ZonedDateTime = ZonedDateTime.now()): Pair<String, ZonedDateTime>? {
        val q = place.trim().lowercase().replace(' ', '_')
        if (q.isEmpty()) return null
        val zones = ZoneId.getAvailableZoneIds()
        val id = zones.firstOrNull { it.substringAfterLast('/').lowercase() == q }
            ?: zones.firstOrNull { it.lowercase() == q }
            ?: ALIASES[q]
            ?: zones.firstOrNull { it.substringAfterLast('/').lowercase().startsWith(q) }
            ?: return null
        return id to now.withZoneSameInstant(ZoneId.of(id))
    }

    private val ALIASES = mapOf(
        "nyc" to "America/New_York", "new_york" to "America/New_York", "sf" to "America/Los_Angeles",
        "san_francisco" to "America/Los_Angeles", "la" to "America/Los_Angeles", "seattle" to "America/Los_Angeles",
        "beijing" to "Asia/Shanghai", "delhi" to "Asia/Kolkata", "mumbai" to "Asia/Kolkata", "india" to "Asia/Kolkata",
        "uk" to "Europe/London", "utc" to "UTC", "gmt" to "UTC", "japan" to "Asia/Tokyo", "brisbane" to "Australia/Brisbane",
        "canberra" to "Australia/Sydney", "wellington" to "Pacific/Auckland", "nz" to "Pacific/Auckland",
    )

    fun formatTime(z: ZonedDateTime, is24: Boolean): String =
        z.format(DateTimeFormatter.ofPattern(if (is24) "EEE HH:mm" else "EEE h:mm a"))
}
