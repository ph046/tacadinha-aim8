package com.tacadinha.pro

import kotlin.math.*

data class Shot(val angleRad: Double, val targetBall: Ball, val pocket: Pocket, val power: Float, val confidence: Float)

object ShotCalculator {
    fun bestShot(state: GameState): Shot? {
        val cue = state.cueBall ?: return null
        val allBalls = state.myBalls + state.opponentBalls + listOfNotNull(state.blackBall)
        val targets = if (state.myBalls.isEmpty()) listOfNotNull(state.blackBall) else state.myBalls
        val candidates = mutableListOf<Shot>()
        for (target in targets) for (pocket in state.pockets) {
            val shot = evaluate(cue, target, pocket, allBalls) ?: continue
            candidates.add(shot)
        }
        return candidates.filter { it.confidence > 0 }.maxByOrNull { it.confidence }
    }

    private fun evaluate(cue: Ball, target: Ball, pocket: Pocket, allBalls: List<Ball>): Shot? {
        val tpx=pocket.x-target.x; val tpy=pocket.y-target.y
        val tpDist=hypot(tpx,tpy); if (tpDist<5f) return null
        val nx=tpx/tpDist; val ny=tpy/tpDist
        val gx=target.x-nx*(cue.r+target.r); val gy=target.y-ny*(cue.r+target.r)
        val cgDist=hypot(gx-cue.x,gy-cue.y); if (cgDist<5f) return null
        val angle=atan2((gy-cue.y).toDouble(),(gx-cue.x).toDouble())
        val blocked=allBalls.filter{it!=target}.any{o->segDist(o.x,o.y,cue.x,cue.y,gx,gy)<(cue.r+o.r)*0.85f}
        val tBlocked=allBalls.filter{it!=target}.any{o->segDist(o.x,o.y,target.x,target.y,pocket.x,pocket.y)<(target.r+o.r)*0.85f}
        if (blocked||tBlocked) return null
        val confidence=((2000f-(cgDist+tpDist))/2000f).coerceAtLeast(0f)
        val power=(cgDist/800f).coerceIn(0.3f,0.95f)
        return Shot(angle,target,pocket,power,confidence)
    }

    private fun segDist(px:Float,py:Float,x1:Float,y1:Float,x2:Float,y2:Float):Float {
        val dx=x2-x1;val dy=y2-y1;val l2=dx*dx+dy*dy
        if(l2<0.001f) return hypot(px-x1,py-y1)
        val t=((px-x1)*dx+(py-y1)*dy).div(l2).coerceIn(0f,1f)
        return hypot(px-x1-t*dx,py-y1-t*dy)
    }
}
