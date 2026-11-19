package com.dengying.player.data.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HlsOfflineTest {

    @Test
    fun `rewrites segments keys and init map to local names`() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-TARGETDURATION:6
            #EXT-X-KEY:METHOD=AES-128,URI="https://keys.example.com/k1?token=abc"
            #EXT-X-MAP:URI="init.mp4?token=abc"
            #EXTINF:6,
            seg-1.ts?token=abc
            #EXTINF:6,
            https://cdn.example.com/path/seg-2.ts
        """.trimIndent()

        val plan = HlsOffline.plan(playlist, "https://cdn.example.com/path/index.m3u8")

        assertEquals(
            listOf(
                "https://keys.example.com/k1?token=abc",
                "https://cdn.example.com/path/init.mp4?token=abc",
                "https://cdn.example.com/path/seg-1.ts?token=abc",
                "https://cdn.example.com/path/seg-2.ts",
            ),
            plan.resources.map { it.url },
        )
        assertTrue(plan.localPlaylist.contains("URI=\"key0.key\""))
        assertTrue(plan.localPlaylist.contains("URI=\"map0.mp4\""))
        assertTrue(plan.localPlaylist.contains("seg0.ts"))
        assertTrue(plan.localPlaylist.contains("seg1.ts"))
        assertTrue(plan.localPlaylist.startsWith("#EXTM3U"))
    }

    @Test
    fun `same resource is downloaded once`() {
        val playlist = """
            #EXTM3U
            #EXTINF:6,
            same.ts
            #EXTINF:6,
            same.ts
        """.trimIndent()

        val plan = HlsOffline.plan(playlist, "https://cdn.example.com/a/index.m3u8")

        assertEquals(1, plan.resources.size)
        assertEquals("https://cdn.example.com/a/same.ts", plan.resources[0].url)
        assertEquals(2, plan.localPlaylist.lines().count { it == "seg0.ts" })
    }

    @Test
    fun `download storage rejects paths outside root`() {
        val root = kotlin.io.path.createTempDirectory("dy-download-test").toFile()
        try {
            DownloadStorage.init(root.absolutePath)

            assertTrue(DownloadStorage.writeBytes("v1_demo_0/part.bin", byteArrayOf(1, 2, 3)))
            assertTrue(DownloadStorage.exists("v1_demo_0/part.bin"))
            assertEquals(
                "file:///${root.absolutePath.replace('\\', '/')}/v1_demo_0/part.bin",
                DownloadStorage.fileUrl("v1_demo_0/part.bin"),
            )
            assertTrue(!DownloadStorage.writeBytes("../escape.bin", byteArrayOf(9)))
            assertTrue(!DownloadStorage.writeBytes("/absolute.bin", byteArrayOf(9)))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `task directory keeps only safe characters`() {
        assertEquals("v12_my_source_3", safeDownloadDir("V12_My-Source_3"))
    }
}
