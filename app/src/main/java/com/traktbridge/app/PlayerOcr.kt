package com.traktbridge.app

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale

data class OcrSuggestion(val title:String,val detail:String,val position:Long=-1,val duration:Long=0)
object OcrRules {
    private val time=Regex("^[-−]?\\d{1,3}:\\d{2}(:\\d{2})?$")
    private val episode=Regex("(?i)\\b(S\\d{1,3}\\s*E\\d{1,4}|season\\s+\\d+\\s*[,·•:-]?\\s*episode\\s+\\d+|episode\\s+\\d+)\\b")
    private val controls=Regex("(?i)^(pause|play|rewind|fast forward|next episode|skip intro|skip recap|subtitles|audio.*subtitles|playback speed|settings|x-ray|cast|back|close|more|ad|advertisement|[0-9]+ seconds?)$")
    private fun edge(l:ScreenLabel)=l.y in 0.0..1.0 && l.x in 0.0..1.0 && (l.y<=.30 || l.y>=.70 || l.x<=.20 || l.x>=.80)
    fun candidates(labels:List<ScreenLabel>):List<OcrSuggestion> {
        val region=labels.filter(::edge)
        val timers=region.filter { time.matches(it.text.trim()) }
        val timed=ContentRecognition.timing(timers+ScreenLabel("seekbar","",seek=true),true)
        val detail=region.firstNotNullOfOrNull { episode.find(it.text)?.value }.orEmpty()
        return region.filter { ContentRecognition.usable(it.text) && !controls.matches(it.text.trim()) && !time.matches(it.text.trim()) && it.text.split(Regex("\\s+")).size<=12 && it.h in .003..0.15 }
            .sortedWith(compareByDescending<ScreenLabel> { it.h }.thenBy { it.y })
            .distinctBy { it.text.trim().lowercase(Locale.ROOT) }.take(6)
            .map { OcrSuggestion(it.text.trim(),detail,timed?.position ?: -1,timed?.duration ?: 0) }
    }
}
object PlayerOcr {
    // The original window image is never saved. Only edge regions are submitted to the local recognizer.
    fun scan(bitmap:Bitmap,done:(Result<List<OcrSuggestion>>)->Unit) {
        val width=bitmap.width; val height=bitmap.height
        if(width<20 || height<20) { bitmap.recycle(); done(Result.success(emptyList())); return }
        val top=(height*.30).toInt()
        val side=(width*.20).toInt()
        val regions=listOf(Rect(0,0,width,top),Rect(0,height-top,width,height),Rect(0,top,side,height-top),Rect(width-side,top,width,height-top))
        val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val labels=mutableListOf<ScreenLabel>()
        fun finish(result:Result<List<OcrSuggestion>>) { recognizer.close(); bitmap.recycle(); done(result) }
        fun next(index:Int) {
            if(index==regions.size) { finish(Result.success(OcrRules.candidates(labels))); return }
            val r=regions[index]
            val crop=try { Bitmap.createBitmap(bitmap,r.left,r.top,r.width(),r.height()) } catch(e:Exception) { finish(Result.failure(e)); return }
            recognizer.process(InputImage.fromBitmap(crop,0)).addOnSuccessListener { text ->
                for(block in text.textBlocks) for(line in block.lines) {
                    val b=line.boundingBox ?: continue
                    labels.add(ScreenLabel("",line.text,x=(r.left+b.exactCenterX())/width.toDouble(),y=(r.top+b.exactCenterY())/height.toDouble(),w=b.width()/width.toDouble(),h=b.height()/height.toDouble()))
                }
                crop.recycle(); next(index+1)
            }.addOnFailureListener { e -> crop.recycle(); finish(Result.failure(e)) }
        }
        next(0)
    }
}
