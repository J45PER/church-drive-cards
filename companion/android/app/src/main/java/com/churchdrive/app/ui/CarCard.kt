package com.churchdrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.text.style.TextOverflow
import com.churchdrive.app.ha.CallService
import com.churchdrive.app.ha.data
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.churchdrive.app.ha.EntityState
import com.churchdrive.app.ha.Registry
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// The dashboard's Car card: each car's battery, range and charging, from the car's own integration.
// Some cars (Stellantis) only send new figures when something happens, so while a car's plugged in and its figures
// are over 20 minutes old the card says when they're from, and Refresh presses the car's wake-up button.

/** The car battery's colour, the dashboard's rule: red under 20%, orange under 40%, else [ok] (the Car card's teal); grey when unknown. */
fun carBatteryColour(level: Double?, ok: Color): Color = when {
    level == null -> Color.Gray
    level < 20 -> CliveColours.Bad.colour
    level < 40 -> CliveColours.Warn.colour
    else -> ok
}

private fun reading(e: EntityState?): String =
    e?.state?.toDoubleOrNull()?.let { "${Math.round(it)} ${e.str("unit_of_measurement").orEmpty()}".trim() }.orEmpty()

