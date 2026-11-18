package io.github.wingedpanther.cleanmytv.home

import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * A file CleanMyTV sends in pieces, one `content call` each: the fireplace, theme pictures, slideshow pictures.
 * It is written as <name>.upload and renamed once its MD5 checks out. The TV may kill Home between two calls
 * to free memory (Sony, 2 GB boxes: #121), and with it the note of which file is coming, so the next piece
 * picks up the one .upload file left on disk instead.
 */
object Upload {
    private const val SUFFIX = ".upload"

    private fun open(dir: File) = dir.walk().filter { it.isFile && it.name.endsWith(SUFFIX) }.toList()

    /** Where [f] goes once it has all arrived. */
    fun target(f: File) = File(f.parentFile, f.name.removeSuffix(SUFFIX))

    /** Starts [target] afresh, as its .upload file. Half-sent files left under [dir] go, so [resume] only ever finds this one. */
    fun start(dir: File, target: File): File {
        open(dir).forEach { it.delete() }
        return File(target.parentFile, target.name + SUFFIX).also { it.writeBytes(ByteArray(0)) }
    }

    /** The .upload file under [dir] a killed Home left, when there is just one and [ok] takes the name it will get. */
    fun resume(dir: File, ok: (File) -> Boolean): File? = open(dir).singleOrNull()?.takeIf { ok(target(it)) }

    fun append(f: File, bytes: ByteArray, max: Int, tooBig: String) {
        if (f.length() + bytes.size > max) throw IllegalArgumentException(tooBig)
        f.appendBytes(bytes)
    }

    /** Swaps [f] in whole when its MD5 is [md5]. A damaged one is deleted, so a later call can't pick it up. */
    fun finish(f: File, md5: String?, what: String) {
        val got = DigestInputStream(f.inputStream(), MessageDigest.getInstance("MD5")).use { s ->
            val buf = ByteArray(64 * 1024)
            while (s.read(buf) >= 0) Unit
            s.messageDigest.digest().joinToString("") { "%02x".format(it) }
        }
        if (got != md5?.lowercase()) { f.delete(); throw IllegalArgumentException("$what upload is damaged (md5 $got)") }
        f.renameTo(target(f))
    }
}
