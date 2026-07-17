/*
 * Pure-Kotlin port of lieff/minimp3 (https://github.com/lieff/minimp3).
 * Original work is dedicated to the public domain under CC0 1.0 Universal.
 */
package space.kodio.core.io.files.mp3.minimp3


internal object MiniMp3Layer3 {
    /** Flat g_scf_partitions[3][28] so MPEG-2 can index across rows (C pointer + k). */
    private val scfPartitionsFlat = intArrayOf(
        6, 5, 5, 5, 6, 5, 5, 5, 6, 5, 7, 3, 11, 10, 0, 0, 7, 7, 7, 0, 6, 6, 6, 3, 8, 8, 5, 0,
        8, 9, 6, 12, 6, 9, 9, 9, 6, 9, 12, 6, 15, 18, 0, 0, 6, 15, 12, 0, 6, 12, 9, 6, 6, 18, 9, 0,
        9, 9, 6, 12, 9, 9, 9, 9, 9, 9, 12, 6, 18, 18, 0, 0, 12, 12, 12, 0, 12, 9, 9, 6, 15, 12, 9, 0,
    )
    private val scfcDecode = intArrayOf(0, 1, 2, 3, 12, 5, 6, 7, 9, 10, 11, 13, 14, 15, 18, 19)
    private val mod = intArrayOf(5, 5, 4, 4, 5, 5, 4, 1, 4, 3, 1, 1, 5, 6, 6, 1, 4, 4, 4, 1, 4, 3, 1, 1)
    private val preamp = intArrayOf(1, 1, 1, 1, 2, 2, 3, 3, 3, 2)

    fun readSideInfo(bs: BitStream, gr: Array<L3GrInfo>, grOff: Int, hdr: ByteArray, hdrOff: Int): Int {
        val h1 = hdr[hdrOff + 1].toInt() and 0xFF
        val h2 = hdr[hdrOff + 2].toInt() and 0xFF
        val h3 = hdr[hdrOff + 3].toInt() and 0xFF
        val mpeg1 = (h1 and 0x8) != 0
        val mono = (h3 and 0xC0) == 0xC0
        var srIdx = ((h2 ushr 2) and 3) + (((h1 ushr 3) and 1) + ((h1 ushr 4) and 1)) * 3
        if (srIdx != 0) srIdx -= 1

        var grCount = if (mono) 1 else 2
        val mainDataBegin: Int
        var scfsi: Int
        if (mpeg1) {
            grCount *= 2
            mainDataBegin = bs.getBits(9)
            scfsi = bs.getBits(7 + grCount)
        } else {
            mainDataBegin = bs.getBits(8 + grCount) shr grCount
            scfsi = 0
        }

        var part23Sum = 0
        var gi = grOff
        do {
            if (mono) scfsi = scfsi shl 4
            val g = gr[gi]
            g.part23Length = bs.getBits(12)
            part23Sum += g.part23Length
            g.bigValues = bs.getBits(9)
            if (g.bigValues > 288) return -1
            g.globalGain = bs.getBits(8)
            g.scalefacCompress = bs.getBits(if (mpeg1) 4 else 9)
            g.sfbtab = MiniMp3Tables.scfLong
            g.sfbtabOff = srIdx * 23
            g.nLongSfb = 22
            g.nShortSfb = 0
            val tables: Int
            if (bs.getBits(1) != 0) {
                g.blockType = bs.getBits(2)
                if (g.blockType == 0) return -1
                g.mixedBlockFlag = bs.getBits(1)
                g.regionCount[0] = 7
                g.regionCount[1] = 255
                if (g.blockType == 2) {
                    scfsi = scfsi and 0x0F0F
                    if (g.mixedBlockFlag == 0) {
                        g.regionCount[0] = 8
                        g.sfbtab = MiniMp3Tables.scfShort
                        g.sfbtabOff = srIdx * 40
                        g.nLongSfb = 0
                        g.nShortSfb = 39
                    } else {
                        g.sfbtab = MiniMp3Tables.scfMixed
                        g.sfbtabOff = srIdx * 40
                        g.nLongSfb = if (mpeg1) 8 else 6
                        g.nShortSfb = 30
                    }
                }
                tables = bs.getBits(10) shl 5
                g.subblockGain[0] = bs.getBits(3)
                g.subblockGain[1] = bs.getBits(3)
                g.subblockGain[2] = bs.getBits(3)
            } else {
                g.blockType = 0
                g.mixedBlockFlag = 0
                tables = bs.getBits(15)
                g.regionCount[0] = bs.getBits(4)
                g.regionCount[1] = bs.getBits(3)
                g.regionCount[2] = 255
            }
            g.tableSelect[0] = tables ushr 10
            g.tableSelect[1] = (tables ushr 5) and 31
            g.tableSelect[2] = tables and 31
            g.preflag = if (mpeg1) bs.getBits(1) else if (g.scalefacCompress >= 500) 1 else 0
            g.scalefacScale = bs.getBits(1)
            g.count1Table = bs.getBits(1)
            g.scfsi = (scfsi ushr 12) and 15
            scfsi = scfsi shl 4
            gi++
            grCount--
        } while (grCount > 0)
        if (part23Sum + bs.pos > bs.limit + mainDataBegin * 8) return -1
        return mainDataBegin
    }

    private fun readScalefactors(
        scf: IntArray,
        istPos: IntArray,
        scfSize: IntArray,
        scfCount: IntArray,
        bitbuf: BitStream,
        scfsiIn: Int,
    ) {
        var scfO = 0
        var istO = 0
        var scfsi = scfsiIn
        var i = 0
        while (i < 4 && scfCount[i] != 0) {
            val cnt = scfCount[i]
            if ((scfsi and 8) != 0) {
                for (k in 0 until cnt) scf[scfO + k] = istPos[istO + k]
            } else {
                val bits = scfSize[i]
                if (bits == 0) {
                    for (k in 0 until cnt) {
                        scf[scfO + k] = 0
                        istPos[istO + k] = 0
                    }
                } else {
                    val maxScf = if (scfsi < 0) (1 shl bits) - 1 else -1
                    for (k in 0 until cnt) {
                        val s = bitbuf.getBits(bits)
                        istPos[istO + k] = if (s == maxScf) -1 else s
                        scf[scfO + k] = s
                    }
                }
            }
            istO += cnt
            scfO += cnt
            scfsi *= 2
            i++
        }
        scf[scfO] = 0
        scf[scfO + 1] = 0
        scf[scfO + 2] = 0
    }

    fun ldexpQ2(yIn: Float, expQ2In: Int): Float {
        var y = yIn
        var expQ2 = expQ2In
        do {
            val e = miniMp3Min(30 * 4, expQ2)
            y *= MiniMp3Tables.expfrac[e and 3] * (1 shl 30 shr (e shr 2)).toFloat()
            expQ2 -= e
        } while (expQ2 > 0)
        return y
    }

    fun decodeScalefactors(
        hdr: ByteArray, hdrOff: Int,
        istPos: IntArray,
        bs: BitStream,
        gr: L3GrInfo,
        scf: FloatArray,
        ch: Int,
    ) {
        val partIdx = (if (gr.nShortSfb != 0) 1 else 0) + (if (gr.nLongSfb == 0) 1 else 0)
        var partitionBase = partIdx * 28
        val scfSize = IntArray(4)
        val iscf = IntArray(40)
        val scfShift = gr.scalefacScale + 1
        var scfsi = gr.scfsi
        val mpeg1 = (hdr[hdrOff + 1].toInt() and 0x8) != 0
        if (mpeg1) {
            val part = scfcDecode[gr.scalefacCompress]
            scfSize[0] = part ushr 2
            scfSize[1] = scfSize[0]
            scfSize[2] = part and 3
            scfSize[3] = scfSize[2]
        } else {
            val ist = if (((hdr[hdrOff + 3].toInt() and 0x10) != 0) && ch != 0) 1 else 0
            var sfc = gr.scalefacCompress shr ist
            var k = ist * 3 * 4
            while (sfc >= 0) {
                var modprod = 1
                for (i in 3 downTo 0) {
                    scfSize[i] = sfc / modprod % mod[k + i]
                    modprod *= mod[k + i]
                }
                sfc -= modprod
                k += 4
            }
            partitionBase += k
            scfsi = -16
        }
        val counts = IntArray(28) { i ->
            val idx = partitionBase + i
            if (idx < scfPartitionsFlat.size) scfPartitionsFlat[idx] else 0
        }
        readScalefactors(iscf, istPos, scfSize, counts, bs, scfsi)

        if (gr.nShortSfb != 0) {
            val sh = 3 - scfShift
            var i = 0
            while (i < gr.nShortSfb) {
                iscf[gr.nLongSfb + i + 0] += gr.subblockGain[0] shl sh
                iscf[gr.nLongSfb + i + 1] += gr.subblockGain[1] shl sh
                iscf[gr.nLongSfb + i + 2] += gr.subblockGain[2] shl sh
                i += 3
            }
        } else if (gr.preflag != 0) {
            for (i in 0 until 10) iscf[11 + i] += preamp[i]
        }

        val ms = ((hdr[hdrOff + 3].toInt() and 0xE0) == 0x60)
        val gainExp = gr.globalGain + BITS_DEQUANTIZER_OUT * 4 - 210 - if (ms) 2 else 0
        val gain = ldexpQ2((1 shl (MAX_SCFI / 4)).toFloat(), MAX_SCFI - gainExp)
        val n = gr.nLongSfb + gr.nShortSfb
        for (i in 0 until n) {
            scf[i] = ldexpQ2(gain, iscf[i] shl scfShift)
        }
    }

    fun pow43(xIn: Int): Float {
        var x = xIn
        var mult = 256
        if (x < 129) return MiniMp3Tables.pow43[16 + x]
        if (x < 1024) {
            mult = 16
            x = x shl 3
        }
        val sign = 2 * x and 64
        val frac = ((x and 63) - sign).toFloat() / ((x and 63.inv()) + sign).toFloat()
        return MiniMp3Tables.pow43[16 + ((x + sign) shr 6)] *
            (1f + frac * ((4f / 3) + frac * (2f / 9))) * mult.toFloat()
    }

    fun huffman(dst: FloatArray, dstOff: Int, bs: BitStream, grInfo: L3GrInfo, scf: FloatArray, layer3grLimit: Int) {
        val tabs = MiniMp3Tables.huffmanTabs
        val tab32 = MiniMp3Tables.tab32
        val tab33 = MiniMp3Tables.tab33
        val tabindex = MiniMp3Tables.tabindex
        val linbitsArr = MiniMp3Tables.linbits
        val pow43 = MiniMp3Tables.pow43

        var one = 0f
        var ireg = 0
        var bigValCnt = grInfo.bigValues
        var sfbOff = grInfo.sfbtabOff
        val sfbTab = grInfo.sfbtab
        var scfOff = 0
        var dstI = dstOff

        var bsNextPtr = bs.bufOff + bs.pos / 8
        val buf = bs.buf
        fun u8(p: Int): Int = if (p in buf.indices) buf[p].toInt() and 0xFF else 0
        // C: uint32_t bs_cache = (((p[0]*256u+p[1])*256u+p[2])*256u+p[3]) << (pos & 7);
        var cache =
            ((((u8(bsNextPtr).toLong() * 256 + u8(bsNextPtr + 1)) * 256 + u8(bsNextPtr + 2)) * 256 +
                u8(bsNextPtr + 3)) shl (bs.pos and 7)) and 0xFFFFFFFFL
        var bsSh = (bs.pos and 7) - 8
        bsNextPtr += 4

        fun peekBits(n: Int): Int = ((cache ushr (32 - n)) and ((1L shl n) - 1)).toInt()
        fun flushBits(n: Int) {
            if (n <= 0) return
            cache = (cache shl n) and 0xFFFFFFFFL
            bsSh += n
        }
        fun checkBits() {
            while (bsSh >= 0) {
                cache = (cache or ((u8(bsNextPtr).toLong() shl bsSh) and 0xFFFFFFFFL)) and 0xFFFFFFFFL
                bsNextPtr++
                bsSh -= 8
            }
        }
        fun bsPos(): Int = (bsNextPtr - bs.bufOff) * 8 - 24 + bsSh

        while (bigValCnt > 0) {
            val tabNum = grInfo.tableSelect[ireg]
            var sfbCnt = grInfo.regionCount[ireg++]
            val codebookOff = tabindex[tabNum]
            val linbits = linbitsArr[tabNum]
            if (linbits != 0) {
                do {
                    val np = sfbTab[sfbOff++] / 2
                    var pairsToDecode = miniMp3Min(bigValCnt, np)
                    one = scf[scfOff++]
                    do {
                        var w = 5
                        var leaf = tabs[codebookOff + peekBits(w)].toInt()
                        while (leaf < 0) {
                            flushBits(w)
                            w = leaf and 7
                            leaf = tabs[codebookOff + peekBits(w) - (leaf shr 3)].toInt()
                        }
                        flushBits(leaf ushr 8)
                        repeat(2) {
                            val lsb = leaf and 0x0F
                            if (lsb == 15) {
                                val lin = peekBits(linbits)
                                flushBits(linbits)
                                checkBits()
                                val sign = if (cache.toInt() < 0) -1 else 1
                                dst[dstI] = one * pow43(lsb + lin) * sign.toFloat()
                            } else {
                                dst[dstI] = pow43[16 + lsb - 16 * ((cache ushr 31).toInt())] * one
                            }
                            flushBits(if (lsb != 0) 1 else 0)
                            dstI++
                            leaf = leaf shr 4
                        }
                        checkBits()
                        pairsToDecode--
                    } while (pairsToDecode > 0)
                    bigValCnt -= np
                    sfbCnt--
                } while (bigValCnt > 0 && sfbCnt >= 0)
            } else {
                do {
                    val np = sfbTab[sfbOff++] / 2
                    var pairsToDecode = miniMp3Min(bigValCnt, np)
                    one = scf[scfOff++]
                    do {
                        var w = 5
                        var leaf = tabs[codebookOff + peekBits(w)].toInt()
                        while (leaf < 0) {
                            flushBits(w)
                            w = leaf and 7
                            leaf = tabs[codebookOff + peekBits(w) - (leaf shr 3)].toInt()
                        }
                        flushBits(leaf ushr 8)
                        repeat(2) {
                            val lsb = leaf and 0x0F
                            dst[dstI] = pow43[16 + lsb - 16 * ((cache ushr 31).toInt())] * one
                            flushBits(if (lsb != 0) 1 else 0)
                            dstI++
                            leaf = leaf shr 4
                        }
                        checkBits()
                        pairsToDecode--
                    } while (pairsToDecode > 0)
                    bigValCnt -= np
                    sfbCnt--
                } while (bigValCnt > 0 && sfbCnt >= 0)
            }
        }

        var np = 1 - bigValCnt
        while (true) {
            val codebookCount1 = if (grInfo.count1Table != 0) tab33 else tab32
            var leaf = codebookCount1[peekBits(4)]
            if ((leaf and 8) == 0) {
                leaf = codebookCount1[(leaf ushr 3) + (((cache shl 4) and 0xFFFFFFFFL) ushr (32 - (leaf and 3))).toInt()]
            }
            flushBits(leaf and 7)
            if (bsPos() > layer3grLimit) break
            fun reloadScaleFactor() {
                np--
                if (np == 0) {
                    np = sfbTab[sfbOff++] / 2
                    if (np == 0) {
                        np = -999999 // break sentinel via outer
                        return
                    }
                    one = scf[scfOff++]
                }
            }
            fun deqCount1(s: Int) {
                if ((leaf and (128 shr s)) != 0) {
                    dst[dstI + s] = if (cache.toInt() < 0) -one else one
                    flushBits(1)
                }
            }
            reloadScaleFactor()
            if (np == -999999) break
            deqCount1(0)
            deqCount1(1)
            reloadScaleFactor()
            if (np == -999999) break
            deqCount1(2)
            deqCount1(3)
            checkBits()
            dstI += 4
        }
        bs.pos = layer3grLimit
    }

    fun midsideStereo(left: FloatArray, leftOff: Int, n: Int) {
        val rightOff = leftOff + 576
        for (i in 0 until n) {
            val a = left[leftOff + i]
            val b = left[rightOff + i]
            left[leftOff + i] = a + b
            left[rightOff + i] = a - b
        }
    }

    private fun intensityStereoBand(left: FloatArray, leftOff: Int, n: Int, kl: Float, kr: Float) {
        for (i in 0 until n) {
            left[leftOff + i + 576] = left[leftOff + i] * kr
            left[leftOff + i] = left[leftOff + i] * kl
        }
    }

    private fun stereoTopBand(right: FloatArray, rightOff: Int, sfb: IntArray, sfbOff: Int, nbands: Int, maxBand: IntArray) {
        maxBand[0] = -1; maxBand[1] = -1; maxBand[2] = -1
        var r = rightOff
        for (i in 0 until nbands) {
            val len = sfb[sfbOff + i]
            var k = 0
            while (k < len) {
                if (right[r + k] != 0f || right[r + k + 1] != 0f) {
                    maxBand[i % 3] = i
                    break
                }
                k += 2
            }
            r += len
        }
    }

    private fun stereoProcess(
        left: FloatArray, leftOffIn: Int,
        istPos: IntArray,
        sfb: IntArray, sfbOff: Int,
        hdr: ByteArray, hdrOff: Int,
        maxBand: IntArray,
        mpeg2Sh: Int,
    ) {
        val pan = MiniMp3Tables.pan
        val mpeg1 = (hdr[hdrOff + 1].toInt() and 0x8) != 0
        val maxPos = if (mpeg1) 7 else 64
        var leftOff = leftOffIn
        var i = 0
        while (sfb[sfbOff + i] != 0) {
            val ipos = istPos[i]
            if (i > maxBand[i % 3] && ipos < maxPos) {
                val s = if ((hdr[hdrOff + 3].toInt() and 0x20) != 0) Float.fromBits(0x3fb504f3) else 1f
                val kl: Float
                val kr: Float
                if (mpeg1) {
                    kl = pan[2 * ipos]
                    kr = pan[2 * ipos + 1]
                } else {
                    var klV = 1f
                    var krV = ldexpQ2(1f, (ipos + 1) shr 1 shl mpeg2Sh)
                    if ((ipos and 1) != 0) {
                        klV = krV
                        krV = 1f
                    }
                    kl = klV
                    kr = krV
                }
                intensityStereoBand(left, leftOff, sfb[sfbOff + i], kl * s, kr * s)
            } else if ((hdr[hdrOff + 3].toInt() and 0x20) != 0) {
                midsideStereo(left, leftOff, sfb[sfbOff + i])
            }
            leftOff += sfb[sfbOff + i]
            i++
        }
    }

    fun intensityStereo(left: FloatArray, leftOff: Int, istPos: IntArray, gr: L3GrInfo, grNext: L3GrInfo, hdr: ByteArray, hdrOff: Int) {
        val maxBand = IntArray(3)
        val nSfb = gr.nLongSfb + gr.nShortSfb
        val maxBlocks = if (gr.nShortSfb != 0) 3 else 1
        stereoTopBand(left, leftOff + 576, gr.sfbtab, gr.sfbtabOff, nSfb, maxBand)
        if (gr.nLongSfb != 0) {
            val m = miniMp3Max(miniMp3Max(maxBand[0], maxBand[1]), maxBand[2])
            maxBand[0] = m; maxBand[1] = m; maxBand[2] = m
        }
        val mpeg1 = (hdr[hdrOff + 1].toInt() and 0x8) != 0
        for (i in 0 until maxBlocks) {
            val defaultPos = if (mpeg1) 3 else 0
            val itop = nSfb - maxBlocks + i
            val prev = itop - maxBlocks
            istPos[itop] = if (maxBand[i] >= prev) defaultPos else istPos[prev]
        }
        stereoProcess(left, leftOff, istPos, gr.sfbtab, gr.sfbtabOff, hdr, hdrOff, maxBand, grNext.scalefacCompress and 1)
    }

    fun reorder(grbuf: FloatArray, grOff: Int, scratch: FloatArray, sfb: IntArray, sfbOff: Int) {
        var src = grOff
        var dst = 0
        var sOff = sfbOff
        while (true) {
            val len = sfb[sOff]
            if (len == 0) break
            for (i in 0 until len) {
                scratch[dst++] = grbuf[src + 0 * len + i]
                scratch[dst++] = grbuf[src + 1 * len + i]
                scratch[dst++] = grbuf[src + 2 * len + i]
            }
            // C: for (i=0;i<len;i++,src++) { *dst++=src[0*len]; ...} then src += 2*len after loop's src+=len from increments
            // Actually: src starts, inner does src++ each i, so after inner src += len, then sfb+=3, src += 2*len
            // Total src advance per iteration: len + 2*len = 3*len
            src += 3 * len
            sOff += 3
        }
        scratch.copyInto(grbuf, grOff, 0, dst)
    }

    fun antialias(grbuf: FloatArray, grOffIn: Int, nbandsIn: Int) {
        var nbands = nbandsIn
        var grOff = grOffIn
        val aa0 = MiniMp3Tables.aa0
        val aa1 = MiniMp3Tables.aa1
        while (nbands > 0) {
            for (i in 0 until 8) {
                val u = grbuf[grOff + 18 + i]
                val d = grbuf[grOff + 17 - i]
                grbuf[grOff + 18 + i] = u * aa0[i] - d * aa1[i]
                grbuf[grOff + 17 - i] = u * aa1[i] + d * aa0[i]
            }
            nbands--
            grOff += 18
        }
    }

    private fun dct39(y: FloatArray) {
        var s0 = y[0]; var s2 = y[2]; var s4 = y[4]; var s6 = y[6]; var s8 = y[8]
        var t0 = s0 + s6 * 0.5f
        s0 -= s6
        var t4 = (s4 + s2) * Float.fromBits(0x3f708fb2)
        var t2 = (s8 + s2) * Float.fromBits(0x3f441b7d)
        s6 = (s4 - s8) * Float.fromBits(0x3e31d0d4)
        s4 += s8 - s2
        s2 = s0 - s4 * 0.5f
        y[4] = s4 + s0
        s8 = t0 - t2 + s6
        s0 = t0 - t4 + t2
        s4 = t0 + t4 - s6
        var s1 = y[1]; var s3 = y[3]; var s5 = y[5]; var s7 = y[7]
        s3 *= Float.fromBits(0x3f5db3d7)
        t0 = (s5 + s1) * Float.fromBits(0x3f7c1c5c)
        t4 = (s5 - s7) * Float.fromBits(0x3eaf1d44)
        t2 = (s1 + s7) * Float.fromBits(0x3f248dbb)
        s1 = (s1 - s5 - s7) * Float.fromBits(0x3f5db3d7)
        s5 = t0 - s3 - t2
        s7 = t4 - s3 - t0
        s3 = t4 + s3 - t2
        y[0] = s4 - s7
        y[1] = s2 + s1
        y[2] = s0 - s3
        y[3] = s8 + s5
        y[5] = s8 - s5
        y[6] = s0 + s3
        y[7] = s2 - s1
        y[8] = s4 + s7
    }

    private fun imdct36(grbuf: FloatArray, grOffIn: Int, overlap: FloatArray, ovOffIn: Int, window: FloatArray, nbands: Int) {
        val twid9 = MiniMp3Tables.twid9
        var grOff = grOffIn
        var ovOff = ovOffIn
        val co = FloatArray(9)
        val si = FloatArray(9)
        repeat(nbands) {
            co[0] = -grbuf[grOff + 0]
            si[0] = grbuf[grOff + 17]
            for (i in 0 until 4) {
                si[8 - 2 * i] = grbuf[grOff + 4 * i + 1] - grbuf[grOff + 4 * i + 2]
                co[1 + 2 * i] = grbuf[grOff + 4 * i + 1] + grbuf[grOff + 4 * i + 2]
                si[7 - 2 * i] = grbuf[grOff + 4 * i + 4] - grbuf[grOff + 4 * i + 3]
                co[2 + 2 * i] = -(grbuf[grOff + 4 * i + 3] + grbuf[grOff + 4 * i + 4])
            }
            dct39(co)
            dct39(si)
            si[1] = -si[1]; si[3] = -si[3]; si[5] = -si[5]; si[7] = -si[7]
            for (i in 0 until 9) {
                val ovl = overlap[ovOff + i]
                val sum = co[i] * twid9[9 + i] + si[i] * twid9[0 + i]
                overlap[ovOff + i] = co[i] * twid9[0 + i] - si[i] * twid9[9 + i]
                grbuf[grOff + i] = ovl * window[0 + i] - sum * window[9 + i]
                grbuf[grOff + 17 - i] = ovl * window[9 + i] + sum * window[0 + i]
            }
            grOff += 18
            ovOff += 9
        }
    }

    private fun idct3(x0: Float, x1: Float, x2: Float, dst: FloatArray) {
        val m1 = x1 * Float.fromBits(0x3f5db3d7)
        val a1 = x0 - x2 * 0.5f
        dst[1] = x0 + x2
        dst[0] = a1 + m1
        dst[2] = a1 - m1
    }

    private fun imdct12(x: FloatArray, xOff: Int, dst: FloatArray, dstOff: Int, overlap: FloatArray, ovOff: Int) {
        val twid3 = MiniMp3Tables.twid3
        val co = FloatArray(3)
        val si = FloatArray(3)
        idct3(-x[xOff + 0], x[xOff + 6] + x[xOff + 3], x[xOff + 12] + x[xOff + 9], co)
        idct3(x[xOff + 15], x[xOff + 12] - x[xOff + 9], x[xOff + 6] - x[xOff + 3], si)
        si[1] = -si[1]
        for (i in 0 until 3) {
            val ovl = overlap[ovOff + i]
            val sum = co[i] * twid3[3 + i] + si[i] * twid3[0 + i]
            overlap[ovOff + i] = co[i] * twid3[0 + i] - si[i] * twid3[3 + i]
            dst[dstOff + i] = ovl * twid3[2 - i] - sum * twid3[5 - i]
            dst[dstOff + 5 - i] = ovl * twid3[5 - i] + sum * twid3[2 - i]
        }
    }

    private fun imdctShort(grbuf: FloatArray, grOffIn: Int, overlap: FloatArray, ovOffIn: Int, nbandsIn: Int) {
        var nbands = nbandsIn
        var grOff = grOffIn
        var ovOff = ovOffIn
        val tmp = FloatArray(18)
        while (nbands > 0) {
            grbuf.copyInto(tmp, 0, grOff, grOff + 18)
            overlap.copyInto(grbuf, grOff, ovOff, ovOff + 6)
            imdct12(tmp, 0, grbuf, grOff + 6, overlap, ovOff + 6)
            imdct12(tmp, 1, grbuf, grOff + 12, overlap, ovOff + 6)
            imdct12(tmp, 2, overlap, ovOff, overlap, ovOff + 6)
            nbands--
            ovOff += 9
            grOff += 18
        }
    }

    private fun changeSign(grbuf: FloatArray, grOffIn: Int) {
        var grOff = grOffIn + 18
        var b = 0
        while (b < 32) {
            var i = 1
            while (i < 18) {
                grbuf[grOff + i] = -grbuf[grOff + i]
                i += 2
            }
            b += 2
            grOff += 36
        }
    }

    fun imdctGr(grbuf: FloatArray, grOff: Int, overlap: FloatArray, blockType: Int, nLongBands: Int) {
        val w0 = MiniMp3Tables.mdctWindow0
        val w1 = MiniMp3Tables.mdctWindow1
        var gOff = grOff
        var oOff = 0
        if (nLongBands != 0) {
            imdct36(grbuf, gOff, overlap, oOff, w0, nLongBands)
            gOff += 18 * nLongBands
            oOff += 9 * nLongBands
        }
        if (blockType == SHORT_BLOCK_TYPE) {
            imdctShort(grbuf, gOff, overlap, oOff, 32 - nLongBands)
        } else {
            val win = if (blockType == STOP_BLOCK_TYPE) w1 else w0
            imdct36(grbuf, gOff, overlap, oOff, win, 32 - nLongBands)
        }
    }

    fun saveReservoir(h: Mp3dec, s: Mp3decScratch) {
        var pos = (s.bs.pos + 7) / 8
        var remains = s.bs.limit / 8 - pos
        if (remains > MAX_BITRESERVOIR_BYTES) {
            pos += remains - MAX_BITRESERVOIR_BYTES
            remains = MAX_BITRESERVOIR_BYTES
        }
        if (remains > 0) {
            s.maindata.copyInto(h.reservBuf, 0, pos, pos + remains)
        }
        h.reserv = remains
    }

    fun restoreReservoir(h: Mp3dec, bs: BitStream, s: Mp3decScratch, mainDataBegin: Int): Boolean {
        val frameBytes = (bs.limit - bs.pos) / 8
        val bytesHave = miniMp3Min(h.reserv, mainDataBegin)
        val srcOff = miniMp3Max(0, h.reserv - mainDataBegin)
        h.reservBuf.copyInto(s.maindata, 0, srcOff, srcOff + bytesHave)
        bs.buf.copyInto(s.maindata, bytesHave, bs.bufOff + bs.pos / 8, bs.bufOff + bs.pos / 8 + frameBytes)
        s.bs.init(s.maindata, 0, bytesHave + frameBytes)
        return h.reserv >= mainDataBegin
    }

    fun decode(h: Mp3dec, s: Mp3decScratch, grInfo: Array<L3GrInfo>, grOff: Int, nch: Int) {
        for (ch in 0 until nch) {
            val layer3grLimit = s.bs.pos + grInfo[grOff + ch].part23Length
            decodeScalefactors(h.header, 0, s.istPos[ch], s.bs, grInfo[grOff + ch], s.scf, ch)
            huffman(s.grbuf, ch * 576, s.bs, grInfo[grOff + ch], s.scf, layer3grLimit)
        }
        if ((h.header[3].toInt() and 0x10) != 0) {
            intensityStereo(s.grbuf, 0, s.istPos[1], grInfo[grOff], grInfo[grOff + 1], h.header, 0)
        } else if ((h.header[3].toInt() and 0xE0) == 0x60) {
            midsideStereo(s.grbuf, 0, 576)
        }
        for (ch in 0 until nch) {
            val g = grInfo[grOff + ch]
            var aaBands = 31
            val mySr = Mp3Hdr.from(h.header, 0).mySampleRate()
            val nLongBands = (if (g.mixedBlockFlag != 0) 2 else 0) shl (if (mySr == 2) 1 else 0)
            if (g.nShortSfb != 0) {
                aaBands = nLongBands - 1
                reorder(s.grbuf, ch * 576 + nLongBands * 18, s.syn, g.sfbtab, g.sfbtabOff + g.nLongSfb)
            }
            antialias(s.grbuf, ch * 576, aaBands)
            imdctGr(s.grbuf, ch * 576, h.mdctOverlap[ch], g.blockType, nLongBands)
            changeSign(s.grbuf, ch * 576)
        }
    }
}
