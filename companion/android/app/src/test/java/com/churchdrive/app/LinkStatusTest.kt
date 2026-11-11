package com.churchdrive.app

import com.churchdrive.app.ha.ConnectionState
import com.churchdrive.app.ha.LinkStatus
import com.churchdrive.app.ha.NO_CONNECTION_AFTER_MS
import com.churchdrive.app.ha.PillKind
import com.churchdrive.app.ha.pillKind
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkStatusTest {
    private val up = LinkStatus(ConnectionState.Connected, statesLoaded = true)

    @Test fun settledLineShowsNoPill() = assertEquals(PillKind.None, pillKind(up, 0))

    @Test fun connectingThenNoConnection() {
        val down = LinkStatus(ConnectionState.Connecting)
        assertEquals(PillKind.Connecting, pillKind(down, 0))
        assertEquals(PillKind.Connecting, pillKind(down, NO_CONNECTION_AFTER_MS - 1))
        assertEquals(PillKind.NoConnection, pillKind(down, NO_CONNECTION_AFTER_MS))
        assertEquals(PillKind.NoConnection, pillKind(LinkStatus(ConnectionState.Disconnected), NO_CONNECTION_AFTER_MS))
    }

    @Test fun updatingWhileStatesLoadOrRefreshing() {
        assertEquals(PillKind.Updating, pillKind(up.copy(statesLoaded = false), 0))
        assertEquals(PillKind.Updating, pillKind(up.copy(refreshing = true), 0))
    }

    @Test fun restartingBeatsNoConnectionAndLastsUntilItemsLoad() {
        val down = LinkStatus(ConnectionState.Disconnected, restarting = true)
        assertEquals(PillKind.Restarting, pillKind(down, NO_CONNECTION_AFTER_MS * 5))
        assertEquals(PillKind.Restarting, pillKind(up.copy(statesLoaded = false, restarting = true), 0))
        // Back and loaded: nothing to say, even if the flag has not cleared yet.
        assertEquals(PillKind.None, pillKind(up.copy(restarting = true), 0))
    }

    @Test fun failedSignInHasItsOwnScreen() {
        assertEquals(PillKind.None, pillKind(LinkStatus(ConnectionState.AuthFailed, restarting = true), 99_000))
    }
}
