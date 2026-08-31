package com.kosd.log_inattendancesafeguard.ui.maps

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/**
 * Reusable OpenStreetMap composable that renders:
 *  - A center pin at [centerLat], [centerLon] (the office / geofence center)
 *  - A translucent circle of [radiusMeters] around the center
 *  - An optional user-position pin at [userLat], [userLon]
 *
 * Pass [interactive] = true for the admin location-picker (allows scroll/zoom),
 * false for read-only previews.
 */
@Composable
fun OsmGeofenceMap(
    centerLat: Double?,
    centerLon: Double?,
    radiusMeters: Double?,
    userLat: Double? = null,
    userLon: Double? = null,
    insideRadius: Boolean = true,
    interactive: Boolean = false,
    modifier: Modifier = Modifier,
    onMapTap: ((Double, Double) -> Unit)? = null
) {
    val context = LocalContext.current

    // Initialize osmdroid configuration once per process
    remember {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = context.packageName
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(interactive)
                setBuiltInZoomControls(false)
                isClickable = interactive
                isHorizontalMapRepetitionEnabled = false
                isVerticalMapRepetitionEnabled = false
                controller.setZoom(16.0)
            }
        },
        update = { map ->
            map.overlays.clear()

            val center = if (centerLat != null && centerLon != null) GeoPoint(centerLat, centerLon) else null
            val user = if (userLat != null && userLon != null) GeoPoint(userLat, userLon) else null

            // Center / office marker
            if (center != null) {
                val pin = Marker(map).apply {
                    position = center
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Office"
                }
                map.overlays.add(pin)

                // Geofence circle
                if (radiusMeters != null && radiusMeters > 0.0) {
                    val circle = Polygon().apply {
                        points = Polygon.pointsAsCircle(center, radiusMeters)
                        fillPaint.color = AndroidColor.argb(60, 33, 150, 243)   // translucent blue
                        outlinePaint.color = AndroidColor.rgb(33, 150, 243)
                        outlinePaint.strokeWidth = 4f
                    }
                    map.overlays.add(circle)
                }
            }

            // User position
            if (user != null) {
                val userPin = Marker(map).apply {
                    position = user
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = if (insideRadius) "You (inside)" else "You (outside)"
                }
                map.overlays.add(userPin)
            }

            // Camera positioning: prefer center, else user
            val focus = center ?: user
            if (focus != null) {
                map.controller.setCenter(focus)
                // Fit zoom roughly to radius
                val zoom = when {
                    radiusMeters == null   -> 16.0
                    radiusMeters > 1500    -> 13.0
                    radiusMeters > 500     -> 14.5
                    radiusMeters > 200     -> 15.5
                    else                   -> 16.5
                }
                map.controller.setZoom(zoom)
            }

            // Tap-to-pick (admin picker)
            if (onMapTap != null && interactive) {
                map.overlays.add(object : org.osmdroid.views.overlay.Overlay() {
                    override fun onSingleTapConfirmed(
                        e: android.view.MotionEvent,
                        mapView: MapView
                    ): Boolean {
                        val p = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt())
                        onMapTap(p.latitude, p.longitude)
                        return true
                    }
                })
            }

            map.invalidate()
        }
    )

    // Lifecycle hooks for MapView
    DisposableEffect(Unit) {
        onDispose { /* MapView garbage-collected with composition */ }
    }
}
