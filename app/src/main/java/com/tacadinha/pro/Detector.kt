package com.tacadinha.pro

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.sqrt

enum class BallType { CUE, SOLID, STRIPE, BLACK, UNKNOWN }
data class Ball(val x: Float, val y: Float, val r: Float, val type: BallType = BallType.UNKNOWN)
data class Pocket(val x: Float, val y: Float)
data class GameState(val cueBall: Ball?, val myBalls: List<Ball>, val opponentBalls: List<Ball>, val blackBall: Ball?, val pockets: List<Pocket>)

object Detector {
    private fun isGreen(r: Int, g: Int, b: Int) = g > 75 && g > r + 18 && g > b + 18
    private fun isWhite(r: Int, g: Int, b: Int) = r > 180 && g > 180 && b > 180
    private fun isBlack(r: Int, g: Int, b: Int) = r < 60 && g < 60 && b < 60
    private fun isBallColor(r: Int, g: Int, b: Int): Boolean {
        if (isWhite(r, g, b) || isGreen(r, g, b) || isBlack(r, g, b)) return false
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
        val sat = if (mx > 0) (mx - mn).toFloat() / mx else 0f
        return sat > 0.20f && mx > 50
    }

    fun analyze(bmp: Bitmap, playerMode: Int): GameState {
        val scale = 0.22f
        val sw = (bmp.width * scale).toInt().coerceAtLeast(1)
        val sh = (bmp.height * scale).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bmp, sw, sh, false)
        val inv = 1f / scale
        val pixels = IntArray(sw * sh)
        small.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        small.recycle()
        val visited = BooleanArray(sw * sh)
        val clusters = mutableListOf<BallCluster>()
        var minGX = sw; var maxGX = 0; var minGY = sh; var maxGY = 0

        for (idx in pixels.indices) {
            val p = pixels[idx]; val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
            val ix = idx % sw; val iy = idx / sw
            if (isGreen(r, g, b)) {
                if (ix < minGX) minGX = ix; if (ix > maxGX) maxGX = ix
                if (iy < minGY) minGY = iy; if (iy > maxGY) maxGY = iy
            }
            if (visited[idx]) continue
            val isW = isWhite(r, g, b); val isB = isBlack(r, g, b); val isC = isBallColor(r, g, b)
            if (!isW && !isB && !isC) continue
            if (!isOnTable(pixels, ix, iy, sw, sh)) continue
            val clusterPx = mutableListOf<Int>(); val q = ArrayDeque<Int>(); q.add(idx)
            val tr = r; val tg = g; val tb = b
            while (q.isNotEmpty() && clusterPx.size < 600) {
                val cur = q.removeFirst()
                if (cur < 0 || cur >= pixels.size || visited[cur]) continue
                val cp = pixels[cur]; val cr = Color.red(cp); val cg = Color.green(cp); val cb = Color.blue(cp)
                val match = when {
                    isW -> isWhite(cr, cg, cb)
                    isB -> isBlack(cr, cg, cb)
                    else -> (isBallColor(cr, cg, cb) || isWhite(cr, cg, cb)) && abs(cr-tr)<90 && abs(cg-tg)<90 && abs(cb-tb)<90
                }
                if (!match) continue
                visited[cur] = true; clusterPx.add(cur)
                val cx2 = cur % sw; val cy2 = cur / sw
                if (cx2+1<sw) q.add(cur+1); if (cx2-1>=0) q.add(cur-1)
                if (cy2+1<sh) q.add(cur+sw); if (cy2-1>=0) q.add(cur-sw)
            }
            if (clusterPx.size >= 5) {
                val cx = clusterPx.map { it % sw }.average().toFloat() * inv
                val cy = clusterPx.map { it / sw }.average().toFloat() * inv
                val er = (sqrt(clusterPx.size / Math.PI.toFloat()) * inv).coerceIn(8f, 22f)
                val type = when { isW -> BallType.CUE; isB -> BallType.BLACK; else -> classifyBall(pixels, clusterPx, sw) }
                clusters.add(BallCluster(cx, cy, er, type, clusterPx.size))
            }
        }

        val sx1=minGX*inv; val sx2=maxGX*inv; val sy1=minGY*inv; val sy2=maxGY*inv; val mx=(sx1+sx2)/2f
        val pockets = listOf(Pocket(sx1+20f,sy1+20f),Pocket(mx,sy1+10f),Pocket(sx2-20f,sy1+20f),Pocket(sx1+20f,sy2-20f),Pocket(mx,sy2-10f),Pocket(sx2-20f,sy2-20f))
        val cueBall = clusters.filter { it.type==BallType.CUE }.maxByOrNull { it.size }?.let { Ball(it.x,it.y,it.r,BallType.CUE) }
        val blackBall = clusters.filter { it.type==BallType.BLACK }.maxByOrNull { it.size }?.let { Ball(it.x,it.y,it.r,BallType.BLACK) }
        val solidBalls = clusters.filter { it.type==BallType.SOLID }.map { Ball(it.x,it.y,it.r,BallType.SOLID) }
        val stripeBalls = clusters.filter { it.type==BallType.STRIPE }.map { Ball(it.x,it.y,it.r,BallType.STRIPE) }
        val myBalls: List<Ball>; val opponentBalls: List<Ball>
        when (playerMode) {
            0 -> { myBalls=solidBalls; opponentBalls=stripeBalls }
            1 -> { myBalls=stripeBalls; opponentBalls=solidBalls }
            else -> { myBalls=if(solidBalls.size>=stripeBalls.size) solidBalls else stripeBalls; opponentBalls=if(solidBalls.size>=stripeBalls.size) stripeBalls else solidBalls }
        }
        return GameState(cueBall, myBalls, opponentBalls, blackBall, pockets)
    }

    private fun classifyBall(pixels: IntArray, cluster: List<Int>, w: Int): BallType {
        if (cluster.isEmpty()) return BallType.SOLID
        val cx = cluster.map { it % w }.average().toFloat(); val cy = cluster.map { it / w }.average().toFloat()
        var whiteNearCenter = 0; var total = 0
        for (idx in cluster) {
            val px = idx % w; val py = idx / w
            if (kotlin.math.hypot((px-cx), (py-cy)) > 2f) continue
            total++
            val p = pixels[idx]
            if (isWhite(Color.red(p), Color.green(p), Color.blue(p))) whiteNearCenter++
        }
        return if (total > 0 && whiteNearCenter.toFloat()/total > 0.25f) BallType.STRIPE else BallType.SOLID
    }

    private fun isOnTable(pixels: IntArray, x: Int, y: Int, w: Int, h: Int): Boolean {
        var gc = 0
        for (dy in -3..3 step 2) for (dx in -3..3 step 2) {
            val nx=x+dx; val ny=y+dy
            if (nx<0||ny<0||nx>=w||ny>=h) continue
            val p=pixels[ny*w+nx]
            if (isGreen(Color.red(p),Color.green(p),Color.blue(p))) gc++
        }
        return gc >= 2
    }

    private data class BallCluster(val x: Float, val y: Float, val r: Float, val type: BallType, val size: Int)
}
