package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import org.json.JSONObject

object LocalAdb {
    private const val A_CNXN = 0x4e584e43
    private const val A_AUTH = 0x48545541
    private const val A_OPEN = 0x4e45504f
    private const val A_OKAY = 0x59414b4f
    private const val A_WRTE = 0x45545257
    private const val A_CLSE = 0x45534c43
    private const val ADB_AUTH_TOKEN = 1
    private const val ADB_AUTH_SIGNATURE = 2
    private const val ADB_AUTH_RSAPUBLICKEY = 3

    private val SHA1_DIGEST_INFO = byteArrayOf(
        0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
        0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
    )

    private val OEM_KEY_MAP = mapOf(
        "KEY_DISPLAY_OFF" to 293, "000c00ab" to 293,
        "KEY_FN_E" to 606, "000c00fd" to 606,
        "KEY_FN_F" to 608, "000c00fe" to 608,
        "KEY_NUMERIC_0" to 83, "000c009f" to 83
    )

    @Volatile var isConnected: Boolean = false
        private set
    @Volatile var lastError: String? = null
        private set

    @Volatile private var running = false

    private val klScanCodeMap = HashMap<Int, Int>()
    private val klUsageMap = HashMap<String, Int>()
    @Volatile private var klLoaded = false

    private fun cstr(s: String): ByteArray = s.toByteArray(Charsets.UTF_8) + byteArrayOf(0)

    fun ensureDaemon(context: Context) {
        if (running) return
        running = true
        val appCtx = context.applicationContext
        Thread({
            while (true) {
                try {
                    streamGetEvent(appCtx)
                } catch (e: Throwable) {
                    isConnected = false
                    lastError = e.message ?: e.toString()
                }
                try { Thread.sleep(4000L) } catch (_: InterruptedException) {}
            }
        }, "CMTV-LocalAdb").apply { isDaemon = true }.start()
    }

    private fun ensureKeyLayoutsLoaded() {
        if (klLoaded) return
        synchronized(this) {
            if (klLoaded) return
            loadKeyLayouts()
            klLoaded = true
        }
    }

    private fun loadKeyLayouts() {
        val dirs = listOf(
            File("/system/usr/keylayout"),
            File("/product/usr/keylayout"),
            File("/vendor/usr/keylayout"),
            File("/odm/usr/keylayout")
        )
        // 1. Generic.kl first as baseline
        for (d in dirs) {
            val generic = File(d, "Generic.kl")
            if (generic.isFile && generic.canRead()) {
                parseKlFile(generic)
                break
            }
        }
        // 2. Specific keylayouts
        for (d in dirs) {
            if (!d.isDirectory || !d.canRead()) continue
            val files = d.listFiles() ?: continue
            for (f in files) {
                if (f.isFile && f.name.endsWith(".kl") && f.name != "Generic.kl" && f.canRead()) {
                    parseKlFile(f)
                }
            }
        }
    }

    private fun parseKlFile(file: File) {
        runCatching {
            file.useLines { lines ->
                for (raw in lines) {
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) continue
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 3 && parts[0] == "key") {
                        if (parts[1] == "usage" && parts.size >= 4) {
                            val usageStr = parts[2].removePrefix("0x").removePrefix("0X").lowercase()
                            val aName = parts[3]
                            val code = androidKeyNameToCode(aName)
                            if (code != android.view.KeyEvent.KEYCODE_UNKNOWN) {
                                klUsageMap[usageStr] = code
                            }
                        } else {
                            val scanCode = if (parts[1].startsWith("0x", ignoreCase = true)) {
                                parts[1].substring(2).toIntOrNull(16)
                            } else {
                                parts[1].toIntOrNull()
                            }
                            val aName = parts[2]
                            val code = androidKeyNameToCode(aName)
                            if (scanCode != null && code != android.view.KeyEvent.KEYCODE_UNKNOWN) {
                                klScanCodeMap[scanCode] = code
                            }
                        }
                    }
                }
            }
        }
    }

    private fun androidKeyNameToCode(name: String): Int {
        val aName = if (name.startsWith("KEYCODE_")) name else "KEYCODE_$name"
        val code = android.view.KeyEvent.keyCodeFromString(aName)
        if (code != android.view.KeyEvent.KEYCODE_UNKNOWN) return code
        return android.view.KeyEvent.keyCodeFromString(name)
    }

    data class ParsedKey(val code: Int, val name: String)

    private fun parseKeyEvent(line: String): ParsedKey? {
        if (line.isEmpty()) return null
        // Immediately ignore high-frequency sensor and non-key event streams
        if (line.contains("EV_MSC") || line.contains("EV_REL") || line.contains("EV_ABS") || line.contains("EV_SYN") ||
            line.contains(" 0004 ") || line.contains(" 0002 ") || line.contains(" 0003 ") || line.contains(" 0000 ")) {
            return null
        }

        val parts = line.split(Regex("\\s+"))
        val type: String
        val codeName: String
        val value: String

        if (parts.size >= 4 && (parts[0].startsWith("/dev/input/") || parts[0].endsWith(":"))) {
            type = parts[1]
            codeName = parts[2]
            value = parts[3]
        } else if (parts.size >= 3) {
            type = parts[0]
            codeName = parts[1]
            value = parts[2]
        } else {
            return null
        }

        val isKey = type == "EV_KEY" || type == "0001" || type == "1"
        if (!isKey) return null
        val isDown = value == "DOWN" || value == "00000001" || value == "1"
        if (!isDown) return null

        ensureKeyLayoutsLoaded()

        // 1. Exact match in OEM_KEY_MAP
        val oemCode = OEM_KEY_MAP[codeName] ?: OEM_KEY_MAP[codeName.lowercase()]
        if (oemCode != null) {
            return ParsedKey(oemCode, codeName)
        }

        // 2. Exact match in parsed .kl usage map (HID usage codes like 000c00ab)
        val usageCode = klUsageMap[codeName.lowercase()]
        if (usageCode != null && usageCode != android.view.KeyEvent.KEYCODE_UNKNOWN) {
            return ParsedKey(usageCode, codeName)
        }

        // 3. Android KeyEvent.keyCodeFromString("KEYCODE_" + ...)
        if (codeName.startsWith("KEY_")) {
            val aName = "KEYCODE_" + codeName.removePrefix("KEY_")
            val c = android.view.KeyEvent.keyCodeFromString(aName)
            if (c != android.view.KeyEvent.KEYCODE_UNKNOWN) {
                return ParsedKey(c, codeName)
            }
        }

        // 4. Resolve Linux scan code
        val scanCode = LinuxKeyCodes.getScanCode(codeName)
            ?: codeName.removePrefix("0x").removePrefix("0X").toIntOrNull(16)
            ?: codeName.toIntOrNull()

        if (scanCode != null) {
            val klCode = klScanCodeMap[scanCode]
            if (klCode != null && klCode != android.view.KeyEvent.KEYCODE_UNKNOWN) {
                return ParsedKey(klCode, codeName)
            }
            // Deterministic positive integer fallback for unmapped OEM buttons: 1000 + scanCode
            return ParsedKey(1000 + scanCode, codeName)
        }

        // 5. Deterministic fallback for unknown symbol
        val hashFallback = 1000 + (codeName.hashCode() and 0x7fff)
        return ParsedKey(hashFallback, codeName)
    }

    private fun streamGetEvent(context: Context) {
        val kp = loadOrCreateKeyPair(context)
        try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress("127.0.0.1", 5555), 3000)
                sock.soTimeout = 20000
                val inp = sock.getInputStream()
                val out = sock.getOutputStream()

                sendPacket(out, A_CNXN, 0x01000000, 4096, cstr("host::"))
                var msg = readPacket(inp)

                if (msg.cmd == A_AUTH && msg.arg0 == ADB_AUTH_TOKEN) {
                    val sig = signToken(kp, msg.data)
                    sendPacket(out, A_AUTH, ADB_AUTH_SIGNATURE, 0, sig)
                    msg = readPacket(inp)
                    if (msg.cmd == A_AUTH) {
                        val pub = encodeAndroidPubKey(kp.public as RSAPublicKey)
                        sendPacket(out, A_AUTH, ADB_AUTH_RSAPUBLICKEY, 0, pub)
                        msg = readPacket(inp)
                    }
                }
                if (msg.cmd != A_CNXN) {
                    isConnected = false
                    lastError = "Authentication rejected by local adbd"
                    return
                }

                // Filter getevent to monitor only EV_KEY capable nodes and dynamically watch for hotplugged devices (e.g. Bluetooth remote connecting after boot)
                val getEventCmd = "killall -9 getevent 2>/dev/null; " +
                    "seen=\"\"; " +
                    "while true; do " +
                    "cur=\"\"; for s in \$seen; do if [ -c \"\$s\" ]; then cur=\"\$cur \$s\"; fi; done; seen=\"\$cur\"; " +
                    "for d in \$(getevent -p 2>/dev/null | while read -r l; do case \"\$l\" in \"add device \"*) dev=\"\${l##* }\"; k=0 ;; *\"KEY (0001)\"*) if [ \"\$k\" = \"0\" ]; then echo \"\$dev\"; k=1; fi ;; esac; done); do " +
                    "case \" \$seen \" in *\" \$d \"*) ;; *) seen=\"\$seen \$d\"; (while [ -c \"\$d\" ]; do getevent -l \"\$d\" 2>/dev/null; sleep 1; done) & ;; esac; done; " +
                    "sleep 2; done"

                sendPacket(out, A_OPEN, 1, 0, cstr("shell:$getEventCmd"))
                val reply = readPacket(inp)
                if (reply.cmd != A_OKAY) {
                    isConnected = false
                    lastError = "Failed to open getevent shell stream"
                    return
                }

                val localId = reply.arg1
                isConnected = true
                lastError = null
                sock.soTimeout = 0

                val lineBuf = StringBuilder()
                var lastFireTime = 0L
                var lastKeyCode = -1

                while (true) {
                    val pkt = readPacket(inp)
                    when (pkt.cmd) {
                        A_WRTE -> {
                            sendPacket(out, A_OKAY, pkt.arg1, pkt.arg0, ByteArray(0))
                            if (pkt.arg1 != localId) continue
                            val chunk = String(pkt.data, Charsets.UTF_8)
                            lineBuf.append(chunk)
                            var nl = lineBuf.indexOf('\n')
                            while (nl >= 0) {
                                val line = lineBuf.substring(0, nl).trim()
                                lineBuf.delete(0, nl + 1)

                                val parsed = parseKeyEvent(line)
                                if (parsed != null) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastFireTime > 250L || lastKeyCode != parsed.code) {
                                        lastFireTime = now
                                        lastKeyCode = parsed.code
                                        KeyLog.add(parsed.code, parsed.name)
                                        handleMappedKey(context, parsed.code)
                                    }
                                }
                                nl = lineBuf.indexOf('\n')
                            }
                        }
                        A_CLSE -> {
                            sendPacket(out, A_CLSE, pkt.arg1, pkt.arg0, ByteArray(0))
                            if (pkt.arg1 == localId) {
                                isConnected = false
                                return
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            isConnected = false
            lastError = e.message ?: e.toString()
            throw e
        }
    }

    private fun handleMappedKey(context: Context, keyCode: Int) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val svc = KeyService.running ?: context
            ConfigStore.load(svc)
            val cfg = ConfigStore.config.value
            if (!cfg.buttonsOn) return@post
            val b = cfg.buttons.firstOrNull { it.code == keyCode } ?: return@post

            Log.i("CleanMyTV", "Executing mapped button: code=$keyCode, action=${b.action}, app=${b.app}")
            runCatching {
                when (b.action) {
                    "app" -> b.app?.let { pkg ->
                        val pm = svc.packageManager
                        val launch = (pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg))
                            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (launch != null) svc.startActivity(launch)
                        else Log.w("CleanMyTV", "Could not find launch intent for package: $pkg")
                    }
                    "settings" -> svc.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "home" -> svc.startActivity(Intent(svc, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    "back" -> KeyService.running?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    "inputs", "panel", "themes" -> svc.startActivity(
                        Intent(svc, MainActivity::class.java).putExtra("open", b.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    )
                    "fireplace" -> svc.startActivity(Intent(svc, FireplaceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "music" -> ConfigStore.update(svc, JSONObject().put("visualiser", !cfg.visualiser))
                    "next_wallpaper" -> ThemePack.next(svc)
                    else -> quickActions()[b.action]?.let { a ->
                        resolve(svc, a)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { svc.startActivity(it) }
                    }
                }
            }.onFailure {
                Log.e("CleanMyTV", "Error executing mapped action for code $keyCode: $it", it)
            }
        }
    }

    private fun signToken(kp: KeyPair, token: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, kp.private)
        return cipher.doFinal(SHA1_DIGEST_INFO + token)
    }

    private fun loadOrCreateKeyPair(context: Context): KeyPair {
        val privFile = File(context.filesDir, "adbkey.pk8")
        val pubFile = File(context.filesDir, "adbkey.pub.der")
        if (privFile.exists() && pubFile.exists()) {
            val kf = KeyFactory.getInstance("RSA")
            val priv = kf.generatePrivate(PKCS8EncodedKeySpec(privFile.readBytes()))
            val pub = kf.generatePublic(X509EncodedKeySpec(pubFile.readBytes()))
            return KeyPair(pub, priv)
        }
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        privFile.writeBytes(kp.private.encoded)
        pubFile.writeBytes(kp.public.encoded)
        return kp
    }

    private fun encodeAndroidPubKey(pub: RSAPublicKey): ByteArray {
        val words = 64
        val b32 = BigInteger.ONE.shiftLeft(32)
        val n = pub.modulus
        val r = BigInteger.ONE.shiftLeft(2048)
        val rr = r.multiply(r).mod(n)
        val n0inv = n.mod(b32).modInverse(b32).negate().mod(b32).toLong()

        val buf = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(words)
        buf.putInt(n0inv.toInt())
        var tmpN = n
        for (i in 0 until words) {
            val qr = tmpN.divideAndRemainder(b32)
            buf.putInt(qr[1].toInt())
            tmpN = qr[0]
        }
        var tmpRr = rr
        for (i in 0 until words) {
            val qr = tmpRr.divideAndRemainder(b32)
            buf.putInt(qr[1].toInt())
            tmpRr = qr[0]
        }
        buf.putInt(pub.publicExponent.toInt())
        val b64 = Base64.encodeToString(buf.array(), Base64.NO_WRAP)
        return cstr("$b64 cleanmytv@local")
    }

    private class Packet(val cmd: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    @Synchronized
    private fun sendPacket(out: OutputStream, cmd: Int, arg0: Int, arg1: Int, data: ByteArray) {
        val sum = data.fold(0) { acc, b -> acc + (b.toInt() and 0xff) }
        val hdr = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        hdr.putInt(cmd)
        hdr.putInt(arg0)
        hdr.putInt(arg1)
        hdr.putInt(data.size)
        hdr.putInt(sum)
        hdr.putInt(cmd xor -1)
        out.write(hdr.array())
        if (data.isNotEmpty()) out.write(data)
        out.flush()
    }

    private fun readPacket(inp: InputStream): Packet {
        val hdrBytes = ByteArray(24)
        var off = 0
        while (off < 24) {
            val r = inp.read(hdrBytes, off, 24 - off)
            if (r < 0) throw java.io.EOFException()
            off += r
        }
        val hdr = ByteBuffer.wrap(hdrBytes).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = hdr.int
        val arg0 = hdr.int
        val arg1 = hdr.int
        val len = hdr.int
        hdr.int
        hdr.int
        val data = ByteArray(len)
        var dOff = 0
        while (dOff < len) {
            val r = inp.read(data, dOff, len - dOff)
            if (r < 0) throw java.io.EOFException()
            dOff += r
        }
        return Packet(cmd, arg0, arg1, data)
    }
}
