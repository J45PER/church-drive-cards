package com.churchdrive.app.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import org.json.JSONArray
import org.json.JSONObject

/** The page the map is drawn from: Leaflet with satellite and road names, red pins for [results], indigo for [chosen]. */
fun placeMapHtml(results: List<ShoppingPlace>, chosen: List<ShoppingPlace>): String {
    fun arr(l: List<ShoppingPlace>) = JSONArray(l.map { it.toJson() }).toString().replace("</", "<\\/")
    return """
<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>html,body,#m{margin:0;height:100%;background:#2a2f2a}.pin{background:none;border:none}.pin svg{width:28px;height:38px;margin:-36px 0 0 -14px}
.use{margin-top:8px;padding:8px 12px;border:0;border-radius:10px;background:#5c6bc0;color:#fff;font-size:14px}</style></head>
<body><div id="m"></div><script>
var results=${arr(results)},chosen=${arr(chosen)};
var map=L.map('m',{attributionControl:true}).setView([35,33],5);map.attributionControl.setPrefix(false);
var E='https://server.arcgisonline.com/ArcGIS/rest/services/';
L.tileLayer(E+'World_Imagery/MapServer/tile/{z}/{y}/{x}',{maxNativeZoom:19,maxZoom:20,attribution:'Imagery © Esri'}).addTo(map);
L.tileLayer(E+'Reference/World_Transportation/MapServer/tile/{z}/{y}/{x}',{maxNativeZoom:19,maxZoom:20,zIndex:5}).addTo(map);
L.tileLayer(E+'Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}',{maxNativeZoom:19,maxZoom:20,zIndex:5}).addTo(map);
function pin(c){return L.divIcon({className:'pin',html:'<svg viewBox="0 0 30 40"><path d="M15 0C6.7 0 0 6.6 0 14.8 0 25.9 15 40 15 40s15-14.1 15-25.2C30 6.6 23.3 0 15 0z" fill="'+c+'"/><circle cx="15" cy="15" r="5.5" fill="#fff"/></svg>'});}
var all=[];
chosen.forEach(function(p){L.marker([p.lat,p.lon],{icon:pin('#5c6bc0')}).addTo(map);all.push([p.lat,p.lon]);});
results.forEach(function(p,n){
  var d=document.createElement('div'),b=document.createElement('b');b.textContent=p.name;d.appendChild(b);
  d.appendChild(document.createElement('br'));d.appendChild(document.createTextNode(p.address||''));
  var u=document.createElement('button');u.className='use';u.textContent='Use this place';u.onclick=function(){Android.pick(n);};
  d.appendChild(document.createElement('br'));d.appendChild(u);
  L.marker([p.lat,p.lon],{icon:pin('#ea4335'),zIndexOffset:500}).addTo(map).bindPopup(d);all.push([p.lat,p.lon]);
});
if(all.length)map.fitBounds(all,{padding:[30,30],maxZoom:16});
</script></body></html>"""
}

private class PickBridge(private val onPick: (Int) -> Unit) {
    @JavascriptInterface
    fun pick(index: Int) {
        Handler(Looper.getMainLooper()).post { onPick(index) }
    }
}

/** A map with the search [results] as red pins (tap one, then "Use this place") and the [chosen] places as indigo pins. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlaceMap(results: List<ShoppingPlace>, chosen: List<ShoppingPlace>, onPick: (ShoppingPlace) -> Unit) {
    val html = placeMapHtml(results, chosen)
    val latest = remember { arrayOf(results) }
    latest[0] = results
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(12.dp)),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                addJavascriptInterface(PickBridge { n -> latest[0].getOrNull(n)?.let(onPick) }, "Android")
                tag = ""
            }
        },
        update = { view ->
            if (view.tag != html) {
                view.tag = html
                view.loadDataWithBaseURL("https://unpkg.com/", html, "text/html", "utf-8", null)
            }
        },
    )
}
