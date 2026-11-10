package io.github.giannoccarol.corollalauncher.apps

import io.github.giannoccarol.corollalauncher.apps.ProjectionApps.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectionAppsTest {

    private val ordinary = listOf(
        Candidate("com.spotify.music", "Spotify"),
        Candidate("com.android.settings", "Settings"),
        Candidate("com.headunit.radio", "Radio"),
    )

    @Test
    fun findsCommonHeadUnitApps() {
        for (app in listOf(
            Candidate("com.zjinnova.zlink", "ZLink"),
            Candidate("com.syu.carlink", "Phone Link"),
            Candidate("com.autolink.app", "AutoLink"),
            Candidate("com.carlinkit.box", "Carlinkit"),
            Candidate("net.easyconn", "EasyConnection"),
            Candidate("com.vendor.player", "Android Auto"),
        )) {
            assertEquals(app.label, 3, ProjectionApps.find(ordinary + app))
        }
    }

    @Test
    fun ignoresGooglesPhoneSideApp() {
        val apps = ordinary + Candidate("com.google.android.projection.gearhead", "Android Auto")
        assertNull(ProjectionApps.find(apps))
    }

    @Test
    fun labelMatchBeatsPackageOnlyMatch() {
        val apps = listOf(
            Candidate("com.vendor.tlinkhelper", "Helper"),
            Candidate("com.vendor.box", "CarPlay"),
        )
        assertEquals(1, ProjectionApps.find(apps))
    }

    @Test
    fun vaguePackageNamesNeedALabel() {
        assertNull(ProjectionApps.find(ordinary + Candidate("com.vendor.screenmirror", "Cast")))
    }

    @Test
    fun nothingToFind() {
        assertNull(ProjectionApps.find(ordinary))
        assertNull(ProjectionApps.find(emptyList()))
    }
}
