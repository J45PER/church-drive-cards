package com.churchdrive.app.ha

/** What the app knows about its line to the house at one moment: the four things the status pill is drawn from. */
data class LinkStatus(
    val connection: ConnectionState = ConnectionState.Disconnected,
    /** True once Home Assistant has sent every entity's state on the current line. */
    val statesLoaded: Boolean = false,
    /** True while a refresh (pull down, or the app checking its line after being away) waits for an answer. */
    val refreshing: Boolean = false,
    /** Home Assistant told us it is shutting down or starting up, and its entities have not all come back yet. */
    val restarting: Boolean = false,
)

/** What the pill at the top of every Church Drive app says. */
enum class PillKind { None, Connecting, Updating, NoConnection, Restarting }

/** How long the line may be down before "Connecting" turns into "No connection" (a short blip is not worth a warning). */
const val NO_CONNECTION_AFTER_MS = 6_000L

/**
 * Which pill the line calls for. [offlineForMs] is how long the line has been down (0 while it is up).
 * A failed sign-in has its own screen, so it shows no pill. Restarting wins over No connection: when the house said it was restarting,
 * being unreachable is expected, and the pill should say so rather than alarm.
 */
fun pillKind(s: LinkStatus, offlineForMs: Long): PillKind = when {
    s.connection == ConnectionState.AuthFailed -> PillKind.None
    s.restarting && (s.connection != ConnectionState.Connected || !s.statesLoaded) -> PillKind.Restarting
    s.connection != ConnectionState.Connected -> if (offlineForMs >= NO_CONNECTION_AFTER_MS) PillKind.NoConnection else PillKind.Connecting
    !s.statesLoaded || s.refreshing -> PillKind.Updating
    else -> PillKind.None
}
