package dev.one.launcher

import dev.one.launcher.data.GitHubAsset
import dev.one.launcher.data.GitHubRelease
import dev.one.launcher.data.compareVersions
import dev.one.launcher.data.pickApkAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleasesTest {

    @Test
    fun compareVersionsStripsVAndAndroidSuffix() {
        // SoH-style tags vs the installed versionName.
        assertTrue(compareVersions("v9.3.1", "9.3.0") > 0)
        assertEquals(0, compareVersions("v9.3.1", "9.3.1"))
        assertTrue(compareVersions("9.3.0", "v9.3.1") < 0)
        // 2Ship-style tag: the "-android.2" suffix is not part of the core.
        assertEquals(0, compareVersions("v5.0.1-android.2", "5.0.1"))
        assertTrue(compareVersions("v5.0.2-android.1", "5.0.1") > 0)
        // Missing segments count as zero.
        assertEquals(0, compareVersions("9.3", "9.3.0"))
        assertTrue(compareVersions("9.3.1", "9.3") > 0)
    }

    @Test
    fun pickApkPrefersAndroidNamedApk() {
        val release = GitHubRelease(
            tag_name = "v9.3.1",
            assets = listOf(
                GitHubAsset("soh-9.3.1-windows.zip", "https://example.invalid/a.zip", 10),
                GitHubAsset("soh-9.3.1.apk", "https://example.invalid/b.apk", 20),
                GitHubAsset("soh-9.3.1-android.apk", "https://example.invalid/c.apk", 30)
            )
        )
        assertEquals("soh-9.3.1-android.apk", pickApkAsset(release)?.name)
    }

    @Test
    fun pickApkFallsBackToFirstApk() {
        val release = GitHubRelease(
            tag_name = "v2.0.0",
            assets = listOf(
                GitHubAsset("starship-v2.0.0.apk", "https://example.invalid/a.apk", 20)
            )
        )
        assertEquals("starship-v2.0.0.apk", pickApkAsset(release)?.name)
    }

    @Test
    fun pickApkReturnsNullWhenReleaseHasNoApk() {
        // Zelda64Recompiled / Zelda3 shape: PC-first releases, no Android APK.
        val release = GitHubRelease(
            tag_name = "v1.2.2",
            assets = listOf(
                GitHubAsset("zelda64recompiled-linux.zip", "https://example.invalid/a.zip", 10),
                GitHubAsset("zelda64recompiled-windows.zip", "https://example.invalid/b.zip", 10)
            )
        )
        assertNull(pickApkAsset(release))
        assertNull(pickApkAsset(GitHubRelease(tag_name = "v1.0.0", assets = emptyList())))
    }
}
