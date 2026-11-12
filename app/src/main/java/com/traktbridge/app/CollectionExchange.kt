package com.traktbridge.app
import org.json.JSONArray
import org.json.JSONObject
object CollectionExchange {
    fun parse(text:String):Pair<String,JSONArray> {
        require(text.length<=1_000_000)
        val root=JSONObject(text)
        require(root.getString("format")=="traktbridge-collection-v1")
        val name=root.getString("name").trim(); require(name.isNotBlank() && name.length<=80)
        val rows=root.getJSONArray("items"); require(rows.length()<=5000)
        val out=JSONArray(); val keys=HashSet<String>()
        for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i); val key=row.getString("key"); val label=row.getString("label")
            require(key.matches(Regex("(movie|show|episode):[1-9][0-9]{0,17}")) && label.isNotBlank() && label.length<=500)
            if(keys.add(key)) out.put(JSONObject().put("key",key).put("label",label))
        }
        return name to out
    }
}
