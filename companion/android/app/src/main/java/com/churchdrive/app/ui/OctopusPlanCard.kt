package com.churchdrive.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.churchdrive.app.ha.EntityState

// The dashboard's Octopus charging plan card: what Octopus has planned for the car it controls (Intelligent Octopus Go), with its own card
// so it sits under the Cars and the charger on the Home and Energy pages. Nothing at all when the house has no Octopus Intelligent device.

