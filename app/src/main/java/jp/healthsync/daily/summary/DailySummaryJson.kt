package jp.healthsync.daily.summary

import java.math.BigDecimal

/** Compact JSON with explicit nulls and stable key order for `health-daily/v1`. */
object DailySummaryJson {
    fun encode(summary: DailySummary): String {
        val json = JsonMap()
        json.string("schema", summary.schema)
        json.string("date", summary.date)
        json.string("timezone", summary.timezone)
        json.long("steps", summary.steps)
        json.double("distance_m", summary.distanceM)
        json.double("active_kcal", summary.activeKcal)
        json.double("total_kcal", summary.totalKcal)
        json.nested(
            "sleep",
            summary.sleep?.let { sleep ->
                JsonMap().apply {
                    string("start", sleep.start)
                    string("end", sleep.end)
                    int("total_min", sleep.totalMin)
                    int("deep_min", sleep.deepMin)
                    int("light_min", sleep.lightMin)
                    int("rem_min", sleep.remMin)
                    int("awake_min", sleep.awakeMin)
                }
            },
        )
        json.nested(
            "heart_rate",
            summary.heartRate?.let { heart ->
                JsonMap().apply {
                    int("resting", heart.resting)
                    double("avg", heart.avg)
                    int("min", heart.min)
                    int("max", heart.max)
                }
            },
        )
        json.double("hrv_rmssd_ms", summary.hrvRmssdMs)
        json.array(
            "exercise",
            summary.exercise?.map { session ->
                JsonMap().apply {
                    string("type", session.type)
                    string("start", session.start)
                    string("end", session.end)
                    int("duration_min", session.durationMin)
                    string("title", session.title)
                }
            },
        )
        json.double("weight_kg", summary.weightKg)
        json.double("body_fat_pct", summary.bodyFatPct)
        json.nested(
            "nutrition",
            summary.nutrition?.let { nutrition ->
                JsonMap().apply {
                    double("kcal", nutrition.kcal)
                    double("protein_g", nutrition.proteinG)
                    double("fat_g", nutrition.fatG)
                    double("carbs_g", nutrition.carbsG)
                }
            },
        )
        json.double("hydration_ml", summary.hydrationMl)
        json.stringArray("sources", summary.sources)
        return json.toString()
    }
}

internal class JsonMap {
    private val parts = mutableListOf<String>()

    fun string(name: String, value: String?) = add(name, value?.let(::quote) ?: "null")

    fun long(name: String, value: Long?) = add(name, value?.toString() ?: "null")

    fun int(name: String, value: Int?) = add(name, value?.toString() ?: "null")

    fun double(name: String, value: Double?) = add(name, value?.let(::formatNumber) ?: "null")

    fun nested(name: String, child: JsonMap?) = add(name, child?.toString() ?: "null")

    fun array(name: String, children: List<JsonMap>?) {
        val raw = children?.joinToString(prefix = "[", postfix = "]", separator = ",") { it.toString() }
        add(name, raw ?: "null")
    }

    fun stringArray(name: String, values: List<String>) {
        add(name, values.joinToString(prefix = "[", postfix = "]", separator = ",") { quote(it) })
    }

    private fun add(name: String, raw: String) {
        parts += quote(name) + ":" + raw
    }

    override fun toString(): String = parts.joinToString(prefix = "{", postfix = "}", separator = ",")
}

internal fun quote(value: String): String {
    val out = StringBuilder(value.length + 2)
    out.append('"')
    for (ch in value) {
        when (ch) {
            '\\' -> out.append("\\\\")
            '"' -> out.append("\\\"")
            '\b' -> out.append("\\b")
            '\u000C' -> out.append("\\f")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> if (ch.code < 0x20) {
                out.append("\\u")
                out.append(ch.code.toString(16).padStart(4, '0'))
            } else {
                out.append(ch)
            }
        }
    }
    out.append('"')
    return out.toString()
}

internal fun formatNumber(value: Double): String {
    return BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
}
