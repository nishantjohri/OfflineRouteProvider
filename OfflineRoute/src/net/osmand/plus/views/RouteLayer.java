package net.osmand.plus.views;

import java.util.ArrayList;
import java.util.List;

import net.osmand.OsmLogUtil;
import net.osmand.osm.MapUtils;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.routing.RoutingHelper;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.location.Location;
import android.util.Log;

public class RouteLayer extends OsmandMapLayer {

	private OsmandMapTileView view;

	private final RoutingHelper helper;
	private Rect boundsRect;
	private RectF tileRect;
	private List<Location> points = new ArrayList<Location>();
	private Paint paint;
	private Path path;
	private boolean iSRouteForItinerary = false;

	public RouteLayer(RoutingHelper helper) {
		this.helper = helper;
	}

	private void initUI() {
		boundsRect = new Rect(0, 0, view.getWidth(), view.getHeight());
		tileRect = new RectF();
		paint = new Paint();
		paint.setColor(Color.BLUE);
		paint.setStyle(Style.STROKE);
		paint.setStrokeWidth(14);
		paint.setAlpha(150);
		paint.setAntiAlias(true);
		paint.setStrokeCap(Cap.ROUND);
		paint.setStrokeJoin(Join.ROUND);
		path = new Path();
	}

	@Override
	public void initLayer(OsmandMapTileView view) {
		this.view = view;
		initUI();
	}

	@Override
	public void onDraw(Canvas canvas, RectF latLonBounds, RectF tilesRect,
			DrawSettings nightMode) {
		path.reset();
		if (helper.hasPointsToShow()) {
			long time = System.currentTimeMillis();
			int w = view.getWidth();
			int h = view.getHeight();
			if (helper.getCurrentLocation() != null
					&& view.isPointOnTheRotatedMap(helper.getCurrentLocation()
							.getLatitude(), helper.getCurrentLocation()
							.getLongitude())) {
				boundsRect = new Rect(-w / 2, -h, 3 * w / 2, h);
			} else {
				boundsRect = new Rect(0, 0, w, h);
			}
			view.calculateTileRectangle(boundsRect, view.getCenterPointX(),
					view.getCenterPointY(), view.getXTile(), view.getYTile(),
					tileRect);
			double topLatitude = MapUtils.getLatitudeFromTile(view.getZoom(),
					tileRect.top);
			double leftLongitude = MapUtils.getLongitudeFromTile(
					view.getZoom(), tileRect.left);
			double bottomLatitude = MapUtils.getLatitudeFromTile(
					view.getZoom(), tileRect.bottom);
			double rightLongitude = MapUtils.getLongitudeFromTile(view
					.getZoom(), tileRect.right);
			double lat = topLatitude - bottomLatitude + 0.1;
			double lon = rightLongitude - leftLongitude + 0.1;
			helper.fillLocationsToShow(topLatitude + lat, leftLongitude - lon,
					bottomLatitude - lat, rightLongitude + lon, points);
			if ((System.currentTimeMillis() - time) > 80) {
				Log
						.e(
								OsmLogUtil.TAG,
								"Calculate route layer " + (System.currentTimeMillis() - time)); //$NON-NLS-1$
			}

			if (points != null && points.size() > 0) {
				// Adding end point
				SharedPreferences sharedPreference = view.getApplication()
						.getApplicationContext().getSharedPreferences(
								MapActivity.EGUIDE_PREFS, 0);
				String route_to = sharedPreference.getString(
						MapActivity.PREF_ROUTE_TO, null);

				if (route_to != null && !route_to.trim().equals("")) {
					Location endPoint = new Location("map");
					endPoint.setLatitude(Double.parseDouble(route_to
							.split(MapActivity.LAT_LON_SEPARATOR)[0]));
					endPoint.setLongitude(Double.parseDouble(route_to
							.split(MapActivity.LAT_LON_SEPARATOR)[1]));
					points.add(endPoint);
				}

				int px = view.getMapXForPoint(points.get(0).getLongitude());
				int py = view.getMapYForPoint(points.get(0).getLatitude());
				path.moveTo(px, py);
				for (int i = 1; i < points.size(); i++) {
					Location o = points.get(i);
					int x = view.getMapXForPoint(o.getLongitude());
					int y = view.getMapYForPoint(o.getLatitude());
					// if (i == 1) {
					// pathBearing = (float) (Math.atan2(y - py, x - px) /
					// Math.PI * 180);
					// }
					path.lineTo(x, y);
				}
				canvas.drawPath(path, paint);
			}
		} else if (iSRouteForItinerary) {
			if (points != null && points.size() > 0) {
				int px = view.getMapXForPoint(points.get(0).getLongitude());
				int py = view.getMapYForPoint(points.get(0).getLatitude());
				path.moveTo(px, py);
				for (int i = 1; i < points.size(); i++) {
					Location o = points.get(i);
					int x = view.getMapXForPoint(o.getLongitude());
					int y = view.getMapYForPoint(o.getLatitude());
					// if (i == 1) {
					// pathBearing = (float) (Math.atan2(y - py, x - px) /
					// Math.PI * 180);
					// }
					path.lineTo(x, y);
				}
				canvas.drawPath(path, paint);
			}
		}
	}

	public RoutingHelper getHelper() {
		return helper;
	}

	// to show further direction
	public Path getPath() {
		return path;
	}

	@Override
	public void destroyLayer() {

	}

	@Override
	public boolean drawInScreenPixels() {
		return false;
	}

	@Override
	public boolean onLongPressEvent(PointF point) {
		return false;
	}

	@Override
	public boolean onSingleTap(PointF point) {
		return false;
	}

	/**
	 * @param points
	 *            the points to set
	 */
	public void setPoints(List<Location> points, boolean iSRouteForItinerary) {
		if (points != null && points.size() > 0) {
			this.points = new ArrayList<Location>(points);
		} else {
			if (this.points != null) {
				this.points.clear();
			}
		}
		this.iSRouteForItinerary = iSRouteForItinerary;
	}
}
