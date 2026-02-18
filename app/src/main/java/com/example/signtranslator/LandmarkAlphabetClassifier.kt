package com.example.signtranslator

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.*

data class PredictionResult(val label: String?, val confidence: Float)

class LandmarkAlphabetClassifier {

    companion object {
        // Landmark indices
        private const val WRIST = 0
        private const val THUMB_CMC = 1; private const val THUMB_MCP = 2
        private const val THUMB_IP  = 3; private const val THUMB_TIP = 4
        private const val INDEX_MCP = 5; private const val INDEX_PIP = 6
        private const val INDEX_DIP = 7; private const val INDEX_TIP = 8
        private const val MIDDLE_MCP = 9; private const val MIDDLE_PIP = 10
        private const val MIDDLE_DIP = 11; private const val MIDDLE_TIP = 12
        private const val RING_MCP = 13; private const val RING_PIP = 14
        private const val RING_DIP = 15; private const val RING_TIP = 16
        private const val PINKY_MCP = 17; private const val PINKY_PIP = 18
        private const val PINKY_DIP = 19; private const val PINKY_TIP = 20

        private const val CONFIDENCE_THRESHOLD = 0.82f
        private const val EXTENDED_THRESHOLD = 0.55f
        private const val CURLED_THRESHOLD = 0.35f
    }

    // ──────────────────────────── geometry helpers ────────────────────────────

    private fun dist2D(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        return sqrt(dx * dx + dy * dy)
    }

    private fun dist3D(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        val dz = a.z() - b.z()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * Angle (degrees) at joint B formed by A–B–C vectors.
     */
    private fun angleDeg(
        lm: List<NormalizedLandmark>, a: Int, b: Int, c: Int
    ): Float {
        val bx = lm[b].x(); val by = lm[b].y(); val bz = lm[b].z()
        val v1x = lm[a].x() - bx; val v1y = lm[a].y() - by; val v1z = lm[a].z() - bz
        val v2x = lm[c].x() - bx; val v2y = lm[c].y() - by; val v2z = lm[c].z() - bz
        val dot = v1x * v2x + v1y * v2y + v1z * v2z
        val mag = sqrt(v1x * v1x + v1y * v1y + v1z * v1z) *
                  sqrt(v2x * v2x + v2y * v2y + v2z * v2z)
        if (mag < 1e-6f) return 0f
        return Math.toDegrees(acos((dot / mag).coerceIn(-1f, 1f).toDouble())).toFloat()
    }

    /**
     * Finger curl score in [0,1].
     * 1.0 = fully extended/straight  |  0.0 = fully curled.
     * Uses angles at PIP and DIP joints, normalised to a 0–1 range.
     */
    private fun fingerCurl(
        lm: List<NormalizedLandmark>, mcp: Int, pip: Int, dip: Int, tip: Int
    ): Float {
        val pipAngle = angleDeg(lm, mcp, pip, dip)          // ~170° straight
        val dipAngle = angleDeg(lm, pip, dip, tip)           // ~170° straight
        val avgAngle = (pipAngle + dipAngle) / 2f
        // Map 180°→1.0  70°→0.0
        return ((avgAngle - 70f) / (180f - 70f)).coerceIn(0f, 1f)
    }

    /**
     * Thumb curl (uses CMC–MCP–IP–TIP chain).
     */
    private fun thumbCurl(lm: List<NormalizedLandmark>): Float {
        val mcpAngle = angleDeg(lm, THUMB_CMC, THUMB_MCP, THUMB_IP)
        val ipAngle  = angleDeg(lm, THUMB_MCP, THUMB_IP, THUMB_TIP)
        val avg = (mcpAngle + ipAngle) / 2f
        return ((avg - 70f) / (180f - 70f)).coerceIn(0f, 1f)
    }

    /**
     * How far the thumb tip is laterally abducted from the index-MCP line.
     * Returns a ratio relative to palm size (positive = abducted outward).
     */
    private fun thumbAbductionRatio(lm: List<NormalizedLandmark>, palmSize: Float): Float {
        return dist2D(lm[THUMB_TIP], lm[INDEX_MCP]) / palmSize.coerceAtLeast(0.01f)
    }

    private fun palmSize(lm: List<NormalizedLandmark>): Float =
        dist2D(lm[WRIST], lm[MIDDLE_MCP]).coerceAtLeast(0.01f)

    // ─────────────────────── convenient derived states ───────────────────────

    private data class HandState(
        val curl: FloatArray,          // [thumb, index, middle, ring, pinky]
        val ext: BooleanArray,          // extended?
        val curled: BooleanArray,       // fully curled?
        val palm: Float,
        val lm: List<NormalizedLandmark>
    ) {
        fun d(a: Int, b: Int): Float {
            val dx = lm[a].x() - lm[b].x()
            val dy = lm[a].y() - lm[b].y()
            return sqrt(dx * dx + dy * dy)
        }
    }

    private fun buildState(lm: List<NormalizedLandmark>): HandState {
        val palm = palmSize(lm)
        val curl = floatArrayOf(
            thumbCurl(lm),
            fingerCurl(lm, INDEX_MCP,  INDEX_PIP,  INDEX_DIP,  INDEX_TIP),
            fingerCurl(lm, MIDDLE_MCP, MIDDLE_PIP, MIDDLE_DIP, MIDDLE_TIP),
            fingerCurl(lm, RING_MCP,   RING_PIP,   RING_DIP,   RING_TIP),
            fingerCurl(lm, PINKY_MCP,  PINKY_PIP,  PINKY_DIP,  PINKY_TIP)
        )
        val ext    = BooleanArray(5) { curl[it] > EXTENDED_THRESHOLD }
        val curled = BooleanArray(5) { curl[it] < CURLED_THRESHOLD }
        return HandState(curl, ext, curled, palm, lm)
    }

    // ────────────────────────── letter classifiers ───────────────────────────

    /** Each classifier returns a confidence in [0,1] or null to skip. */
    private val classifiers: List<Pair<String, (HandState) -> Float?>> = listOf(

        // ── A: closed fist, thumb alongside (not tucked under), no fingers ext ──
        "A" to { s ->
            val fistScore = (1f - s.curl[1]) * 0.3f + (1f - s.curl[2]) * 0.3f +
                            (1f - s.curl[3]) * 0.2f + (1f - s.curl[4]) * 0.2f
            val thumbSide = if (s.d(THUMB_TIP, INDEX_PIP) < s.palm * 0.6f &&
                               s.curl[0] in 0.3f..0.75f) 0.3f else 0f
            val score = fistScore * 0.7f + thumbSide
            score.takeIf { !s.ext[1] && !s.ext[2] && !s.ext[3] && !s.ext[4] }
        },

        // ── B: all four fingers extended flat, thumb tucked across palm ─────────
        "B" to { s ->
            val fourExt = s.curl.drop(1).sumOf { it.toDouble() }.toFloat() / 4f
            val thumbIn = 1f - s.curl[0]
            val score   = fourExt * 0.75f + thumbIn * 0.25f
            score.takeIf { s.ext[1] && s.ext[2] && s.ext[3] && s.ext[4] && !s.ext[0] }
        },

        // ── C: all digits curved into C shape ────────────────────────────────────
        "C" to { s ->
            val midCurl = 0.45f..0.65f
            val allMid  = s.curl.all { it in midCurl }
            val thumbOk = s.curl[0] in 0.4f..0.7f
            val tipDist = s.d(THUMB_TIP, INDEX_TIP)
            val cGap    = if (tipDist in s.palm * 0.15f..s.palm * 0.55f) 0.3f else 0f
            if (allMid && thumbOk) (0.7f + cGap) else null
        },

        // ── D: index extended & curved, others curled, thumb touches middle ──────
        "D" to { s ->
            val indexUp  = s.ext[1]
            val otherIn  = !s.ext[2] && !s.ext[3] && !s.ext[4]
            val thumbMid = s.d(THUMB_TIP, MIDDLE_TIP) < s.palm * 0.45f
            if (indexUp && otherIn) (if (thumbMid) 0.9f else 0.75f) else null
        },

        // ── E: all fingers curled tight, thumb tucked under ──────────────────────
        "E" to { s ->
            val allCurled = s.curled.all { it }
            val score     = s.curl.sumOf { (1f - it).toDouble() }.toFloat() / 5f
            if (allCurled) score else null
        },

        // ── F: index+thumb circle, middle/ring/pinky extended ────────────────────
        "F" to { s ->
            val circle   = s.d(THUMB_TIP, INDEX_TIP) < s.palm * 0.25f
            val threeExt = s.ext[2] && s.ext[3] && s.ext[4]
            if (circle && threeExt && s.curled[1]) 0.88f else null
        },

        // ── G: index pointing sideways, thumb parallel (like pointing a gun sideways) ─
        "G" to { s ->
            val indexExt  = s.ext[1]
            val thumbExt  = s.curl[0] > 0.5f
            val othersIn  = !s.ext[2] && !s.ext[3] && !s.ext[4]
            // Thumb and index roughly parallel (lateral spread small)
            val spread    = s.d(THUMB_TIP, INDEX_TIP)
            val parallel  = spread < s.palm * 0.6f && spread > s.palm * 0.15f
            if (indexExt && thumbExt && othersIn && parallel) 0.85f else null
        },

        // ── H: index + middle extended horizontally, together ────────────────────
        "H" to { s ->
            val twoExt   = s.ext[1] && s.ext[2]
            val othersIn = !s.ext[3] && !s.ext[4]
            val close    = s.d(INDEX_TIP, MIDDLE_TIP) < s.palm * 0.35f
            if (twoExt && othersIn && close) 0.87f else null
        },

        // ── I: only pinky extended ───────────────────────────────────────────────
        "I" to { s ->
            val onlyPinky = s.ext[4] && !s.ext[1] && !s.ext[2] && !s.ext[3]
            if (onlyPinky) (0.7f + s.curl[4] * 0.3f) else null
        },

        // ── J: pinky extended + wrist rotation hint (treat as I variant) ─────────
        // (dynamic letter — approximated by pinky up + thumb extended)
        "J" to { s ->
            val pinkyUp  = s.ext[4] && !s.ext[1] && !s.ext[2] && !s.ext[3]
            val thumbOut = s.curl[0] > 0.55f
            if (pinkyUp && thumbOut) 0.80f else null
        },

        // ── K: index + middle up, thumb between them ─────────────────────────────
        "K" to { s ->
            val twoExt    = s.ext[1] && s.ext[2]
            val othersIn  = !s.ext[3] && !s.ext[4]
            val thumbMid  = s.d(THUMB_TIP, INDEX_PIP) < s.palm * 0.5f
            val spread    = s.d(INDEX_TIP, MIDDLE_TIP) > s.palm * 0.25f
            if (twoExt && othersIn && thumbMid && spread) 0.87f else null
        },

        // ── L: index up + thumb abducted, others curled ──────────────────────────
        "L" to { s ->
            val indexUp   = s.ext[1]
            val thumbOut  = thumbAbductionRatio(s.lm, s.palm) > 0.7f
            val othersIn  = !s.ext[2] && !s.ext[3] && !s.ext[4]
            if (indexUp && thumbOut && othersIn) 0.90f else null
        },

        // ── M: three fingers folded over thumb (index/middle/ring down) ──────────
        "M" to { s ->
            val threeDown = s.curled[1] && s.curled[2] && s.curled[3]
            val pinkyIn   = !s.ext[4]
            val thumbUnder = s.d(THUMB_TIP, INDEX_MCP) < s.palm * 0.45f
            if (threeDown && pinkyIn && thumbUnder) 0.83f else null
        },

        // ── N: index+middle folded over thumb ────────────────────────────────────
        "N" to { s ->
            val twoDown   = s.curled[1] && s.curled[2]
            val othersIn  = !s.ext[3] && !s.ext[4]
            val thumbUnder = s.d(THUMB_TIP, MIDDLE_PIP) < s.palm * 0.45f
            if (twoDown && othersIn && thumbUnder) 0.82f else null
        },

        // ── O: all digits form circle (O shape) ──────────────────────────────────
        "O" to { s ->
            val tipDist  = s.d(THUMB_TIP, INDEX_TIP)
            val allBent  = s.curl.all { it in 0.3f..0.75f }
            val oShape   = tipDist < s.palm * 0.3f
            if (allBent && oShape) 0.88f else null
        },

        // ── P: index pointing down, thumb out ────────────────────────────────────
        "P" to { s ->
            // Index points downward: tip.y > pip.y (y increases downward in image)
            val indexDown = s.lm[INDEX_TIP].y() > s.lm[INDEX_PIP].y()
            val indexExt  = s.ext[1]
            val thumbOut  = s.curl[0] > 0.5f
            val othersIn  = !s.ext[2] && !s.ext[3] && !s.ext[4]
            if (indexExt && indexDown && thumbOut && othersIn) 0.83f else null
        },

        // ── Q: index+thumb pointing downward ─────────────────────────────────────
        "Q" to { s ->
            val indexDown = s.lm[INDEX_TIP].y() > s.lm[INDEX_MCP].y()
            val thumbDown = s.lm[THUMB_TIP].y() > s.lm[THUMB_MCP].y()
            val othersIn  = !s.ext[2] && !s.ext[3] && !s.ext[4]
            if (indexDown && thumbDown && othersIn) 0.81f else null
        },

        // ── R: index+middle crossed ───────────────────────────────────────────────
        "R" to { s ->
            val twoExt   = s.ext[1] && s.ext[2]
            val othersIn = !s.ext[3] && !s.ext[4]
            // Crossed: x coords of tips are inverted relative to MCP order
            val crossed  = (s.lm[INDEX_TIP].x() - s.lm[MIDDLE_TIP].x()).absoluteValue < s.palm * 0.2f
            if (twoExt && othersIn && crossed) 0.84f else null
        },

        // ── S: closed fist with thumb over fingers ────────────────────────────────
        "S" to { s ->
            val allCurled = !s.ext[1] && !s.ext[2] && !s.ext[3] && !s.ext[4]
            val thumbOver = s.d(THUMB_TIP, INDEX_PIP) < s.palm * 0.5f &&
                            s.lm[THUMB_TIP].y() < s.lm[INDEX_PIP].y() + s.palm * 0.1f
            if (allCurled && thumbOver) 0.85f else null
        },

        // ── T: thumb tucked between index and middle ─────────────────────────────
        "T" to { s ->
            val allIn    = !s.ext[1] && !s.ext[2] && !s.ext[3] && !s.ext[4]
            val thumbBetween = s.d(THUMB_TIP, INDEX_PIP) < s.palm * 0.4f
            if (allIn && thumbBetween && !s.curled[0]) 0.83f else null
        },

        // ── U: index+middle extended side-by-side (close together) ───────────────
        "U" to { s ->
            val twoExt   = s.ext[1] && s.ext[2]
            val othersIn = !s.ext[3] && !s.ext[4]
            val close    = s.d(INDEX_TIP, MIDDLE_TIP) < s.palm * 0.30f
            val thumbIn  = !s.ext[0]
            if (twoExt && othersIn && close && thumbIn) 0.86f else null
        },

        // ── V: index+middle spread open (peace/victory) ──────────────────────────
        "V" to { s ->
            val twoExt   = s.ext[1] && s.ext[2]
            val othersIn = !s.ext[3] && !s.ext[4]
            val spread   = s.d(INDEX_TIP, MIDDLE_TIP) > s.palm * 0.30f
            if (twoExt && othersIn && spread) 0.88f else null
        },

        // ── W: index+middle+ring extended, spread ────────────────────────────────
        "W" to { s ->
            val threeExt = s.ext[1] && s.ext[2] && s.ext[3]
            val pinkyIn  = !s.ext[4]
            val spread   = s.d(INDEX_TIP, RING_TIP) > s.palm * 0.45f
            if (threeExt && pinkyIn && spread) 0.87f else null
        },

        // ── X: index hooked/crooked ───────────────────────────────────────────────
        "X" to { s ->
            val indexHooked = s.curl[1] in 0.35f..0.60f
            val othersIn    = !s.ext[2] && !s.ext[3] && !s.ext[4]
            val thumbIn     = !s.ext[0]
            if (indexHooked && othersIn && thumbIn) 0.82f else null
        },

        // ── Y: thumb + pinky extended, others curled ─────────────────────────────
        "Y" to { s ->
            val thumbOut = s.curl[0] > 0.55f
            val pinkyOut = s.ext[4]
            val midIn    = !s.ext[1] && !s.ext[2] && !s.ext[3]
            if (thumbOut && pinkyOut && midIn) 0.90f else null
        },

        // ── Z: index pointing, drawing Z (static ≈ index extended, others in) ────
        "Z" to { s ->
            val indexOut = s.ext[1]
            val othersIn = !s.ext[2] && !s.ext[3] && !s.ext[4]
            val thumbIn  = s.curl[0] < 0.5f
            if (indexOut && othersIn && thumbIn) 0.78f else null
        }
    )

    // ──────────────────────── fixed word gestures ────────────────────────────

    private val wordGestures: List<Pair<String, (HandState) -> Float?>> = listOf(

        // HELLO — open palm, all five digits extended, palm facing out
        "HELLO" to { s ->
            val allExt = s.ext.all { it }
            val spread = s.d(THUMB_TIP, PINKY_TIP) > s.palm * 1.0f
            if (allExt && spread) 0.92f else null
        },

        // THANK YOU — flat hand, fingers together, slight downward tilt
        "THANK_YOU" to { s ->
            val fourExt  = s.ext[1] && s.ext[2] && s.ext[3] && s.ext[4]
            val thumbOut = s.curl[0] > 0.4f
            val close    = s.d(INDEX_TIP, PINKY_TIP) < s.palm * 0.7f
            if (fourExt && thumbOut && close) 0.88f else null
        },

        // YES — fist with nodding wrist motion (static fist approximation)
        "YES" to { s ->
            val allCurled = s.curled.all { it }
            if (allCurled) 0.85f else null
        },

        // NO — index+middle extend and close together, tap gesture (static approx)
        "NO" to { s ->
            val twoExt   = s.ext[1] && s.ext[2]
            val othersIn = !s.ext[3] && !s.ext[4]
            val close    = s.d(INDEX_TIP, MIDDLE_TIP) < s.palm * 0.28f
            val thumbIn  = s.curl[0] < 0.5f
            if (twoExt && othersIn && close && thumbIn) 0.86f else null
        },

        // PLEASE — flat hand on chest (approximated: all extended, touching palm side)
        "PLEASE" to { s ->
            val allExt  = s.ext[1] && s.ext[2] && s.ext[3] && s.ext[4]
            val thumbIn = !s.ext[0]
            if (allExt && thumbIn) 0.82f else null
        },

        // SORRY — fist rotating on chest (fist approximation same as YES — use extra check)
        "SORRY" to { s ->
            val fist     = !s.ext[1] && !s.ext[2] && !s.ext[3] && !s.ext[4]
            val thumbOut = s.curl[0] > 0.5f       // differentiates from YES
            if (fist && thumbOut) 0.83f else null
        }
    )

    // ──────────────────────────── main classify ───────────────────────────────

    fun classify(
        landmarks: List<NormalizedLandmark>,
        handConfidence: Float
    ): PredictionResult {
        if (landmarks.size < 21 || handConfidence < 0.55f) {
            return PredictionResult(null, 0f)
        }

        val state = buildState(landmarks)

        // 1. Try word gestures first (they have higher priority)
        val wordCandidates = wordGestures.mapNotNull { (label, fn) ->
            fn(state)?.let { label to it }
        }

        // 2. Try letter classifiers
        val letterCandidates = classifiers.mapNotNull { (label, fn) ->
            fn(state)?.let { label to it }
        }

        val allCandidates = wordCandidates + letterCandidates
        if (allCandidates.isEmpty()) return PredictionResult(null, 0f)

        val best = allCandidates.maxByOrNull { it.second }!!

        return if (best.second >= CONFIDENCE_THRESHOLD) {
            PredictionResult(best.first, best.second)
        } else {
            PredictionResult(null, best.second)
        }
    }
}
