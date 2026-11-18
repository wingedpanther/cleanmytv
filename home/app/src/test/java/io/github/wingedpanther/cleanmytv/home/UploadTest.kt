package io.github.wingedpanther.cleanmytv.home

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class UploadTest {
    private val dir: File = Files.createTempDirectory("upload").toFile()
    private val names = setOf("fireplace.mp4", "crackle.m4a")
    private val isFire = { f: File -> f.parentFile == dir && f.name in names }

    @After fun clean() { dir.deleteRecursively() }

    private fun md5(b: ByteArray) = MessageDigest.getInstance("MD5").digest(b).joinToString("") { "%02x".format(it) }

    @Test fun `a Home killed between two pieces picks the file up from disk and finishes it`() {
        val data = ByteArray(5000) { it.toByte() }
        val f = Upload.start(dir, File(dir, "fireplace.mp4"))
        Upload.append(f, data.copyOfRange(0, 3000), 1 shl 20, "too big")
        // The process is gone and so is the note of which file was coming.
        val back = Upload.resume(dir, isFire)
        assertEquals(f, back)
        Upload.append(back!!, data.copyOfRange(3000, 5000), 1 shl 20, "too big")
        Upload.finish(back, md5(data), "fireplace")
        assertArrayEquals(data, File(dir, "fireplace.mp4").readBytes())
        assertFalse(f.exists())
        assertNull(Upload.resume(dir, isFire))
    }

    @Test fun `a piece lost with the process fails the MD5 and the half file goes`() {
        val data = ByteArray(5000) { it.toByte() }
        val f = Upload.start(dir, File(dir, "crackle.m4a"))
        Upload.append(f, data.copyOfRange(0, 3000), 1 shl 20, "too big")
        val back = Upload.resume(dir, isFire)!!
        val e = runCatching { Upload.finish(back, md5(data), "fireplace") }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException && e.message!!.startsWith("fireplace upload is damaged"))
        assertFalse(f.exists())
        assertFalse(File(dir, "crackle.m4a").exists())
    }

    @Test fun `only one half file, with a name Home takes, is picked up`() {
        assertNull(Upload.resume(dir, isFire))
        File(dir, "notes.txt.upload").writeText("x")
        assertNull(Upload.resume(dir, isFire))
        File(dir, "notes.txt.upload").delete()
        File(dir, "fireplace.mp4.upload").writeText("x")
        File(dir, "crackle.m4a.upload").writeText("x")
        assertNull(Upload.resume(dir, isFire))
    }

    @Test fun `starting a file clears half files an earlier upload left`() {
        File(dir, "crackle.m4a.upload").writeText("old")
        val f = Upload.start(dir, File(dir, "fireplace.mp4"))
        assertFalse(File(dir, "crackle.m4a.upload").exists())
        assertEquals(f, Upload.resume(dir, isFire))
        assertEquals(0L, f.length())
    }

    @Test fun `pictures in a theme's own folder are found too`() {
        val theme = File(dir, "aurora").apply { mkdirs() }
        File(theme, "0.jpg").writeText("done")
        val f = Upload.start(dir, File(theme, "1.jpg"))
        assertEquals(f, Upload.resume(dir) { it == File(theme, "1.jpg") })
        assertNull(Upload.resume(dir) { it == File(theme, "2.jpg") })
    }

    @Test fun `a piece past the limit is refused`() {
        val f = Upload.start(dir, File(dir, "fireplace.mp4"))
        Upload.append(f, ByteArray(8), 10, "too big")
        val e = runCatching { Upload.append(f, ByteArray(3), 10, "too big") }.exceptionOrNull()
        assertEquals("too big", e?.message)
        assertEquals(8L, f.length())
    }
}
