package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom

/**
 * Photos from a phone, without the computer: while the QR code is on screen, Home answers on the home Wi-Fi at
 * a one-off address. The phone's page shrinks the photo to the TV's size, as the desktop app does, and sends
 * it as the wallpaper or into the slideshow. Only that address works, only while the QR code shows, and only
 * pictures up to 4 MB get through. Home still asks for no storage permission.
 */
object PhoneUpload {
    private const val MAX_BYTES = 4 * 1024 * 1024
    private const val OPEN_MS = 10 * 60_000L

    private var server: ServerSocket? = null
    private val _note = MutableStateFlow("")
    /** What the last photo did, for the dialog on the TV. */
    val note: StateFlow<String> = _note

    /** Starts listening and returns the page's address, or null when the TV has no Wi-Fi or Ethernet address. */
    @Synchronized
    fun start(ctx: Context): String? {
        stop()
        val ip = lanAddress() ?: return null
        val token = ByteArray(9).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val s = ServerSocket(0)
        server = s
        _note.value = ""
        val app = ctx.applicationContext
        val until = System.currentTimeMillis() + OPEN_MS
        Thread({
            s.soTimeout = 1000
            while (!s.isClosed && System.currentTimeMillis() < until) {
                val c = try { s.accept() } catch (_: java.net.SocketTimeoutException) { continue } catch (_: SocketException) { break }
                runCatching { c.use { serve(app, it, token) } }.onFailure { Log.w(TAG, "phone upload: $it") }
            }
            runCatching { s.close() }
        }, "phone-upload").apply { isDaemon = true }.start()
        return "http://$ip:${s.localPort}/$token"
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    private fun lanAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    private fun serve(ctx: Context, c: Socket, token: String) {
        c.soTimeout = 20_000
        val input = BufferedInputStream(c.getInputStream())
        val request = line(input) ?: return
        val headers = generateSequence { line(input)?.takeIf { it.isNotEmpty() } }.toList()
        val (method, path) = request.split(' ').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
        val length = headers.firstOrNull { it.lowercase().startsWith("content-length:") }?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
        val out = c.getOutputStream()
        fun reply(code: String, type: String, body: String) {
            val bytes = body.toByteArray()
            out.write("HTTP/1.1 $code\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(bytes)
            out.flush()
        }
        when {
            method == "GET" && path == "/$token" -> reply("200 OK", "text/html", page(token))
            method == "POST" && (path == "/$token/wallpaper" || path == "/$token/slide") -> {
                if (length !in 1..MAX_BYTES) return reply("413 Too Large", "text/plain", tr("That photo is over 4 MB."))
                val f = File(ctx.cacheDir, "phone.jpg")
                f.outputStream().use { o -> copy(input, o, length) }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, bounds)
                if (bounds.outWidth <= 0) { f.delete(); return reply("415 Not a picture", "text/plain", tr("That isn't a picture the TV can show.")) }
                val said = if (path.endsWith("wallpaper")) {
                    Wallpapers.use(ctx, f); tr("It's on the TV as the wallpaper.")
                } else if (Slides.add(ctx, f)) {
                    tr("Added to the slideshow: {n} of {max}.", "n" to Slides.count(ctx), "max" to Slides.MAX)
                } else tr("The slideshow is full ({max}). Remove some in the desktop app first.", "max" to Slides.MAX)
                f.delete()
                _note.value = said
                reply("200 OK", "text/plain", said)
            }
            else -> reply("404 Not Found", "text/plain", tr("This address has closed. Open From your phone on the TV again."))
        }
    }

    private fun line(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            if (sb.length > 8192) return null
            sb.append(b.toChar())
        }
    }

    private fun copy(input: InputStream, out: java.io.OutputStream, length: Int) {
        val buf = ByteArray(16 * 1024)
        var left = length
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size, left))
            if (n < 0) break
            out.write(buf, 0, n)
            left -= n
        }
    }

    /** Text for a JavaScript string in single quotes. */
    private fun js(s: String) = s.replace("\\", "\\\\").replace("'", "\\'")

    /** The phone's page: pick a photo, and it's cropped to 1920x1080 and sent as a JPEG under 1 MB. */
    private fun page(token: String) = """<!doctype html>
<html lang="${if (I18n.language() == "ru") "ru" else "en"}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>CleanMyTV Home</title>
<style>
:root{color-scheme:dark}body{margin:0;font:16px/1.45 system-ui,-apple-system,sans-serif;background:#111a14;color:#f0ebe5}
main{max-width:520px;margin:0 auto;padding:28px 18px}h1{font-size:22px;margin:0 0 6px}p{color:#c9c1b6;margin:0 0 18px}
label{display:block;padding:14px 16px;border:1.5px solid #3a4a3b;border-radius:12px;margin:0 0 10px;cursor:pointer}
input[type=radio]{margin-right:10px}.go{display:block;width:100%;padding:16px;border:0;border-radius:12px;background:#4caf50;color:#0e140f;font:600 17px system-ui,sans-serif;margin-top:8px}
.go:disabled{opacity:.5}#note{margin-top:16px;min-height:24px}img{width:100%;border-radius:10px;margin-top:14px;display:none}
</style></head><body><main>
<h1>${tr("Send a photo to the TV")}</h1>
<p>${tr("It's cropped to the TV's shape and shrunk before it leaves your phone.")}</p>
<label><input type="radio" name="to" value="wallpaper" checked>${tr("As the wallpaper")}</label>
<label><input type="radio" name="to" value="slide">${tr("Into the slideshow")}</label>
<input id="file" type="file" accept="image/*" hidden>
<button class="go" id="pick">${tr("Choose a photo")}</button>
<img id="preview" alt="">
<div id="note"></div>
</main><script>
const note=document.getElementById('note'),file=document.getElementById('file'),pick=document.getElementById('pick'),img=document.getElementById('preview');
pick.onclick=()=>file.click();
file.onchange=async()=>{const f=file.files[0];if(!f)return;pick.disabled=true;note.textContent='${js(tr("Getting it ready…"))}';
try{const url=URL.createObjectURL(f);const im=new Image();im.src=url;await im.decode();
const c=document.createElement('canvas');c.width=1920;c.height=1080;const x=c.getContext('2d');
const s=Math.max(1920/im.naturalWidth,1080/im.naturalHeight),w=im.naturalWidth*s,h=im.naturalHeight*s;
x.fillStyle='#000';x.fillRect(0,0,1920,1080);x.drawImage(im,(1920-w)/2,(1080-h)/2,w,h);
let q=.88,b;do{b=await new Promise(r=>c.toBlob(r,'image/jpeg',q));q-=.08}while(b.size>1000000&&q>.4);
img.src=URL.createObjectURL(b);img.style.display='block';note.textContent='${js(tr("Sending…"))}';
const to=document.querySelector('input[name=to]:checked').value;
const r=await fetch('/$token/'+to,{method:'POST',headers:{'Content-Type':'image/jpeg'},body:b});
note.textContent=await r.text();}catch(e){note.textContent='${js(tr("That didn't work: {why}"))}'.replace('{why}',e.message)}
pick.disabled=false;file.value='';};
</script></body></html>"""
}
