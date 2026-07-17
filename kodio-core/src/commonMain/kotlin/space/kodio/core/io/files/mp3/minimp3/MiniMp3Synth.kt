/*
 * Pure-Kotlin port of lieff/minimp3 (https://github.com/lieff/minimp3).
 * Original work is dedicated to the public domain under CC0 1.0 Universal.
 */
package space.kodio.core.io.files.mp3.minimp3


internal object MiniMp3Synth {
    fun scalePcm(sample: Float): Short {
        if (sample >= 32766.5f) return 32767
        if (sample <= -32767.5f) return -32768
        var s16 = (sample + 0.5f).toInt()
        if (s16 < 0) s16 -= 1
        return s16.toShort()
    }

    private fun synthPair(pcm: ShortArray, pcmOff: Int, nch: Int, z: FloatArray, zOff: Int) {
        var a = (z[zOff + 14 * 64] - z[zOff + 0]) * 29f
        a += (z[zOff + 1 * 64] + z[zOff + 13 * 64]) * 213f
        a += (z[zOff + 12 * 64] - z[zOff + 2 * 64]) * 459f
        a += (z[zOff + 3 * 64] + z[zOff + 11 * 64]) * 2037f
        a += (z[zOff + 10 * 64] - z[zOff + 4 * 64]) * 5153f
        a += (z[zOff + 5 * 64] + z[zOff + 9 * 64]) * 6574f
        a += (z[zOff + 8 * 64] - z[zOff + 6 * 64]) * 37489f
        a += z[zOff + 7 * 64] * 75038f
        pcm[pcmOff] = scalePcm(a)

        val z2 = zOff + 2
        a = z[z2 + 14 * 64] * 104f
        a += z[z2 + 12 * 64] * 1567f
        a += z[z2 + 10 * 64] * 9727f
        a += z[z2 + 8 * 64] * 64019f
        a += z[z2 + 6 * 64] * -9975f
        a += z[z2 + 4 * 64] * -45f
        a += z[z2 + 2 * 64] * 146f
        a += z[z2 + 0 * 64] * -5f
        pcm[pcmOff + 16 * nch] = scalePcm(a)
    }

    fun dctII(grbuf: FloatArray, grOff: Int, n: Int) {
        val gSec = MiniMp3Tables.sec
        // t[4][8] stored flat as t[i*8 + j]
        val t = FloatArray(4 * 8)
        for (k in 0 until n) {
            val yBase = grOff + k
            for (i in 0 until 8) {
                val x0 = grbuf[yBase + i * 18]
                val x1 = grbuf[yBase + (15 - i) * 18]
                val x2 = grbuf[yBase + (16 + i) * 18]
                val x3 = grbuf[yBase + (31 - i) * 18]
                val t0 = x0 + x3
                val t1 = x1 + x2
                val t2 = (x1 - x2) * gSec[3 * i + 0]
                val t3 = (x0 - x3) * gSec[3 * i + 1]
                // x points to t[0]+i, so x[0]=t[i], x[8]=t[8+i], etc. with stride 1 in first dim of [4][8]?
                // C: for (x = t[0], i = 0; i < 8; i++, x++) { x[0]=...; x[8]=...; x[16]=...; x[24]=...; }
                // t is float t[4][8], so t[0][i], t[1][i], t[2][i], t[3][i]
                t[0 * 8 + i] = t0 + t1
                t[1 * 8 + i] = (t0 - t1) * gSec[3 * i + 2]
                t[2 * 8 + i] = t3 + t2
                t[3 * 8 + i] = (t3 - t2) * gSec[3 * i + 2]
            }
            for (row in 0 until 4) {
                var x0 = t[row * 8 + 0]
                var x1 = t[row * 8 + 1]
                var x2 = t[row * 8 + 2]
                var x3 = t[row * 8 + 3]
                var x4 = t[row * 8 + 4]
                var x5 = t[row * 8 + 5]
                var x6 = t[row * 8 + 6]
                var x7 = t[row * 8 + 7]
                var xt = x0 - x7; x0 += x7
                x7 = x1 - x6; x1 += x6
                x6 = x2 - x5; x2 += x5
                x5 = x3 - x4; x3 += x4
                x4 = x0 - x3; x0 += x3
                x3 = x1 - x2; x1 += x2
                t[row * 8 + 0] = x0 + x1
                t[row * 8 + 4] = (x0 - x1) * 0.70710677f
                x5 = x5 + x6
                x6 = (x6 + x7) * 0.70710677f
                x7 = x7 + xt
                x3 = (x3 + x4) * 0.70710677f
                x5 -= x7 * 0.198912367f
                x7 += x5 * 0.382683432f
                x5 -= x7 * 0.198912367f
                x0 = xt - x6; xt += x6
                t[row * 8 + 1] = (xt + x7) * 0.50979561f
                t[row * 8 + 2] = (x4 + x3) * 0.54119611f
                t[row * 8 + 3] = (x0 - x5) * 0.60134488f
                t[row * 8 + 5] = (x0 + x5) * 0.89997619f
                t[row * 8 + 6] = (x4 - x3) * 1.30656302f
                t[row * 8 + 7] = (xt - x7) * 2.56291556f
            }
            var y = yBase
            for (i in 0 until 7) {
                grbuf[y + 0 * 18] = t[0 * 8 + i]
                grbuf[y + 1 * 18] = t[2 * 8 + i] + t[3 * 8 + i] + t[3 * 8 + i + 1]
                grbuf[y + 2 * 18] = t[1 * 8 + i] + t[1 * 8 + i + 1]
                grbuf[y + 3 * 18] = t[2 * 8 + i + 1] + t[3 * 8 + i] + t[3 * 8 + i + 1]
                y += 4 * 18
            }
            grbuf[y + 0 * 18] = t[0 * 8 + 7]
            grbuf[y + 1 * 18] = t[2 * 8 + 7] + t[3 * 8 + 7]
            grbuf[y + 2 * 18] = t[1 * 8 + 7]
            grbuf[y + 3 * 18] = t[3 * 8 + 7]
        }
    }

    fun synth(xl: FloatArray, xlOff: Int, dst: ShortArray, dstOff: Int, nch: Int, lins: FloatArray, linsOff: Int) {
        val xrOff = xlOff + 576 * (nch - 1)
        val dstrOff = dstOff + (nch - 1)
        val dstlOff = dstOff
        val w = MiniMp3Tables.win
        var wIdx = 0
        val zlinOff = linsOff + 15 * 64

        lins[zlinOff + 4 * 15] = xl[xlOff + 18 * 16]
        lins[zlinOff + 4 * 15 + 1] = xl[xrOff + 18 * 16]
        lins[zlinOff + 4 * 15 + 2] = xl[xlOff + 0]
        lins[zlinOff + 4 * 15 + 3] = xl[xrOff + 0]
        lins[zlinOff + 4 * 31] = xl[xlOff + 1 + 18 * 16]
        lins[zlinOff + 4 * 31 + 1] = xl[xrOff + 1 + 18 * 16]
        lins[zlinOff + 4 * 31 + 2] = xl[xlOff + 1]
        lins[zlinOff + 4 * 31 + 3] = xl[xrOff + 1]

        synthPair(dst, dstrOff, nch, lins, linsOff + 4 * 15 + 1)
        synthPair(dst, dstrOff + 32 * nch, nch, lins, linsOff + 4 * 15 + 64 + 1)
        synthPair(dst, dstlOff, nch, lins, linsOff + 4 * 15)
        synthPair(dst, dstlOff + 32 * nch, nch, lins, linsOff + 4 * 15 + 64)

        for (i in 14 downTo 0) {
            val a = FloatArray(4)
            val b = FloatArray(4)
            lins[zlinOff + 4 * i] = xl[xlOff + 18 * (31 - i)]
            lins[zlinOff + 4 * i + 1] = xl[xrOff + 18 * (31 - i)]
            lins[zlinOff + 4 * i + 2] = xl[xlOff + 1 + 18 * (31 - i)]
            lins[zlinOff + 4 * i + 3] = xl[xrOff + 1 + 18 * (31 - i)]
            lins[zlinOff + 4 * (i + 16)] = xl[xlOff + 1 + 18 * (1 + i)]
            lins[zlinOff + 4 * (i + 16) + 1] = xl[xrOff + 1 + 18 * (1 + i)]
            lins[zlinOff + 4 * (i - 16) + 2] = xl[xlOff + 18 * (1 + i)]
            lins[zlinOff + 4 * (i - 16) + 3] = xl[xrOff + 18 * (1 + i)]

            fun load(k: Int, mode: Int) {
                val w0 = w[wIdx++]; val w1 = w[wIdx++]
                val vz = zlinOff + 4 * i - k * 64
                val vy = zlinOff + 4 * i - (15 - k) * 64
                for (j in 0 until 4) {
                    when (mode) {
                        0 -> {
                            b[j] = lins[vz + j] * w1 + lins[vy + j] * w0
                            a[j] = lins[vz + j] * w0 - lins[vy + j] * w1
                        }
                        1 -> {
                            b[j] += lins[vz + j] * w1 + lins[vy + j] * w0
                            a[j] += lins[vz + j] * w0 - lins[vy + j] * w1
                        }
                        2 -> {
                            b[j] += lins[vz + j] * w1 + lins[vy + j] * w0
                            a[j] += lins[vy + j] * w1 - lins[vz + j] * w0
                        }
                    }
                }
            }
            load(0, 0); load(1, 2); load(2, 1); load(3, 2); load(4, 1); load(5, 2); load(6, 1); load(7, 2)

            dst[dstrOff + (15 - i) * nch] = scalePcm(a[1])
            dst[dstrOff + (17 + i) * nch] = scalePcm(b[1])
            dst[dstlOff + (15 - i) * nch] = scalePcm(a[0])
            dst[dstlOff + (17 + i) * nch] = scalePcm(b[0])
            dst[dstrOff + (47 - i) * nch] = scalePcm(a[3])
            dst[dstrOff + (49 + i) * nch] = scalePcm(b[3])
            dst[dstlOff + (47 - i) * nch] = scalePcm(a[2])
            dst[dstlOff + (49 + i) * nch] = scalePcm(b[2])
        }
    }

    fun synthGranule(
        qmfState: FloatArray,
        grbuf: FloatArray,
        nbands: Int,
        nch: Int,
        pcm: ShortArray,
        pcmOff: Int,
        lins: FloatArray,
    ) {
        for (i in 0 until nch) {
            dctII(grbuf, 576 * i, nbands)
        }
        qmfState.copyInto(lins, 0, 0, 15 * 64)
        var i = 0
        while (i < nbands) {
            synth(grbuf, i, pcm, pcmOff + 32 * nch * i, nch, lins, i * 64)
            i += 2
        }
        if (nch == 1) {
            var j = 0
            while (j < 15 * 64) {
                qmfState[j] = lins[nbands * 64 + j]
                j += 2
            }
        } else {
            lins.copyInto(qmfState, 0, nbands * 64, nbands * 64 + 15 * 64)
        }
    }
}
