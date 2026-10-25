package com.example.helloworldwidget

import org.json.JSONObject
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class Counter(
    val id: String,
    val name: String,
    val period: Int,
    val targetDate: String,
) {
    fun percentRemaining(): Double =
        ((dateEpochDay(targetDate) - todayEpochDay()).toDouble() / period) * 100.0

    fun daysRemaining(): Long = dateEpochDay(targetDate) - todayEpochDay()

    fun withTargetDate(date: String): Counter = copy(targetDate = date)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("period", period)
        .put("targetDate", targetDate)

    companion object {
        fun fromJson(json: JSONObject): Counter {
            val id = json.optString("id")
            val name = json.optString("name")
            val period = json.optInt("period", 0)
            val targetDate = json.optString("targetDate")
            require(id.isNotBlank() && name.isNotBlank() && period > 0) {
                "The server returned an invalid counter."
            }
            dateEpochDay(targetDate)
            return Counter(id, name, period, targetDate)
        }

        fun todayPlusDays(days: Int): String {
            val calendar = dateCalendar()
            calendar.add(Calendar.DAY_OF_MONTH, days)
            return dateFormat().format(calendar.time)
        }

        fun addDays(date: String, days: Int): String {
            val calendar = dateCalendar(date)
            calendar.add(Calendar.DAY_OF_MONTH, days)
            return dateFormat().format(calendar.time)
        }

        private fun dateEpochDay(date: String): Long {
            val parser = dateFormat()
            val position = ParsePosition(0)
            val parsed = parser.parse(date, position)
            require(parsed != null && position.index == date.length && parser.format(parsed) == date) {
                "The server returned an invalid target date."
            }
            val calendar = Calendar.getInstance().apply { time = parsed }
            return epochDay(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH),
            )
        }

        private fun todayEpochDay(): Long {
            val calendar = Calendar.getInstance()
            return epochDay(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH),
            )
        }

        private fun epochDay(year: Int, month: Int, day: Int): Long {
            val adjustedYear = year - if (month <= 2) 1 else 0
            val era = Math.floorDiv(adjustedYear, 400)
            val yearOfEra = adjustedYear - era * 400
            val adjustedMonth = month + if (month > 2) -3 else 9
            val dayOfYear = (153 * adjustedMonth + 2) / 5 + day - 1
            val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
            return era.toLong() * 146_097 + dayOfEra - 719_468
        }

        private fun dateFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            isLenient = false
        }

        private fun dateCalendar(date: String? = null): Calendar {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = if (date == null) {
                System.currentTimeMillis()
            } else {
                val parser = dateFormat()
                val position = ParsePosition(0)
                val parsed = parser.parse(date, position)
                require(parsed != null && position.index == date.length && parser.format(parsed) == date) {
                    "The target date must use YYYY-MM-DD format."
                }
                parsed.time
            }
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar
        }

    }
}
