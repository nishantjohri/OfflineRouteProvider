package net.osmand.plus.views;

import java.util.ArrayList;
import java.util.List;

import net.osmand.osm.LatLon;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.routing.RoutingHelper;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.FrameLayout.LayoutParams;

import com.osmand.android.R;
import com.osmand.model.MapPoi;

public class ContextMenuLayer extends OsmandMapLayer {

	public interface IContextMenuProvider {

		public void collectObjectsFromPoint(PointF point, List<Object> o);

		public LatLon getObjectLocation(Object o);

		public String getObjectDescription(Object o);

		public String getObjectName(Object o);

		public DialogInterface.OnClickListener getActionListener(
				List<String> actionsList, Object o);
	}

	private LatLon latLon;
	private IContextMenuProvider selectedContextProvider;
	private List<Object> selectedObjects = new ArrayList<Object>();

	private TextView textView;
	private DisplayMetrics dm;
	private OsmandMapTileView view;
	private int BASE_TEXT_SIZE = 170;
	private int SHADOW_OF_LEG = 5;

	private final MapActivity activity;
	private Drawable boxLeg;
	private float scaleCoefficient = 1;
	private Rect textPadding;
	private MapPoi poi;
	private boolean isLongPress;

	public ContextMenuLayer(MapActivity activity) {
		this.activity = activity;
	}

	@Override
	public void destroyLayer() {
	}

	@Override
	public void initLayer(OsmandMapTileView view) {
		this.view = view;
		dm = new DisplayMetrics();
		WindowManager wmgr = (WindowManager) view.getContext()
				.getSystemService(Context.WINDOW_SERVICE);
		wmgr.getDefaultDisplay().getMetrics(dm);
		scaleCoefficient = dm.density;
		if (Math.min(dm.widthPixels / (dm.density * 160), dm.heightPixels
				/ (dm.density * 160)) > 2.5f) {
			// large screen
			scaleCoefficient *= 1.5f;
		}
		BASE_TEXT_SIZE = (int) (BASE_TEXT_SIZE * scaleCoefficient);
		SHADOW_OF_LEG = (int) (SHADOW_OF_LEG * scaleCoefficient);

		boxLeg = view.getResources().getDrawable(R.drawable.box_leg);
		boxLeg.setBounds(0, 0, boxLeg.getMinimumWidth(), boxLeg
				.getMinimumHeight());

		textView = new TextView(view.getContext());
		LayoutParams lp = new LayoutParams(BASE_TEXT_SIZE,
				LayoutParams.WRAP_CONTENT);
		textView.setLayoutParams(lp);
		textView.setTextSize(15);
		textView.setTextColor(Color.argb(255, 0, 0, 0));
		textView.setMinLines(1);
		// textView.setMaxLines(15);
		textView.setGravity(Gravity.CENTER_HORIZONTAL);

		textView.setClickable(true);

		textView.setBackgroundDrawable(view.getResources().getDrawable(
				R.drawable.box_free));
		textPadding = new Rect();
		textView.getBackground().getPadding(textPadding);
	}

	@Override
	public void onDraw(Canvas canvas, RectF latLonBounds, RectF tilesRect,
			DrawSettings nightMode) {
		if (latLon != null) {
			int x = view.getRotatedMapXForPoint(latLon.getLatitude(), latLon
					.getLongitude());
			int y = view.getRotatedMapYForPoint(latLon.getLatitude(), latLon
					.getLongitude());

			int tx = x - boxLeg.getMinimumWidth() / 2;
			int ty = y - boxLeg.getMinimumHeight() + SHADOW_OF_LEG;
			canvas.translate(tx, ty);
			boxLeg.draw(canvas);
			canvas.translate(-tx, -ty);

			if (textView.getText().length() > 0) {
				canvas.translate(x - textView.getWidth() / 2, ty
						- textView.getBottom() + textPadding.bottom
						- textPadding.top);
				int c = textView.getLineCount();

				textView.draw(canvas);
				if (c == 0) {
					// special case relayout after on draw method
					layoutText();
					view.refreshMap();
				}
			}
		}
	}

	private void layoutText() {
		Rect padding = new Rect();
		if (textView.getLineCount() > 0) {
			textView.getBackground().getPadding(padding);
		}
		int w = BASE_TEXT_SIZE;
		int h = (int) ((textView.getPaint().getTextSize() + 4) * textView
				.getLineCount());

		textView.layout(0, -padding.bottom, w, h + padding.top);
	}

	public void setLocation(LatLon loc, String description) {
		latLon = loc;
		if (latLon != null) {
			if (description == null || description.length() == 0) {
				description = view.getContext().getString(
						R.string.point_on_map, latLon.getLatitude(),
						latLon.getLongitude());
			}
			textView.setText(description);
		} else {
			textView.setText(""); //$NON-NLS-1$
		}
		layoutText();
	}

	@Override
	public boolean onLongPressEvent(PointF point) {
		if (pressedInTextView(point.x, point.y)) {
			setLocation(null, ""); //$NON-NLS-1$
			view.refreshMap();
			return true;
		}

		isLongPress = true;

		selectedContextProvider = null;
		selectedObjects.clear();
		for (OsmandMapLayer l : view.getLayers()) {
			if (l instanceof ContextMenuLayer.IContextMenuProvider) {
				((ContextMenuLayer.IContextMenuProvider) l)
						.collectObjectsFromPoint(point, selectedObjects);
				if (!selectedObjects.isEmpty()) {
					selectedContextProvider = (IContextMenuProvider) l;
					break;
				}
			}
		}

		LatLon latLon = view.getLatLonFromScreenPoint(point.x, point.y);
		StringBuilder description = new StringBuilder();

		if (!selectedObjects.isEmpty()) {
			if (selectedObjects.size() > 1) {
				description.append("1. ");
			}
			description.append(selectedContextProvider
					.getObjectDescription(selectedObjects.get(0)));
			for (int i = 1; i < selectedObjects.size(); i++) {
				description.append("\n" + (i + 1) + ". ").append(
						selectedContextProvider
								.getObjectDescription(selectedObjects.get(i)));
			}
			LatLon l = selectedContextProvider
					.getObjectLocation(selectedObjects.get(0));
			if (l != null) {
				latLon = l;
			}
		}
		setLocation(latLon, description.toString());
		view.refreshMap();
		return true;
	}

	@Override
	public boolean drawInScreenPixels() {
		return true;
	}

	public boolean pressedInTextView(float px, float py) {
		if (latLon != null) {
			Rect bs = textView.getBackground().getBounds();
			int x = (int) (px - view.getRotatedMapXForPoint(latLon
					.getLatitude(), latLon.getLongitude()));
			int y = (int) (py - view.getRotatedMapYForPoint(latLon
					.getLatitude(), latLon.getLongitude()));
			x += bs.width() / 2;
			y += bs.height() + boxLeg.getMinimumHeight() - SHADOW_OF_LEG;
			if (bs.contains(x, y)) {
				return true;
			}
		}
		return false;
	}

	public String getSelectedObjectName() {
		if (!selectedObjects.isEmpty() && selectedContextProvider != null) {
			return selectedContextProvider.getObjectName(selectedObjects);
		}
		return null;
	}

	@Override
	public boolean onSingleTap(PointF point) {
		if (pressedInTextView(point.x, point.y)) {
			// activity.loadPoiDetails(poiId);
			if (isLongPress) {
				poi = null;
				isLongPress = false;
			}
			showNflickMenuOption();
			return true;
		} else {
			setLocation(null, "");
			view.refreshMap();
		}

		/*
		 * if (pressedInTextView(point.x, point.y)) { if
		 * (!selectedObjects.isEmpty()) { showContextMenuForSelectedObjects(); }
		 * else { activity.contextMenuPoint(latLon.getLatitude(),
		 * latLon.getLongitude()); } return true; }
		 */
		return false;
	}

	private void showNflickMenuOption() {
		Builder builder = new AlertDialog.Builder(view.getContext());
		Resources res = activity.getResources();
		String[] items = res.getStringArray(R.array.routing_options);
		String[] tempItem = new String[2];
		if (poi == null) {
			tempItem[0] = items[1];
			tempItem[1] = items[2];
			items = tempItem;
		}
		builder.setItems(items, new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int position) {
				SharedPreferences sharedPreference = activity
						.getApplicationContext().getSharedPreferences(
								MapActivity.EGUIDE_PREFS, 0);
				if (poi == null) {
					position = position + 1;
				}
				if (position == 0) {
					activity.loadPoiDetails(poi);
				} else if (position == 1) {// setting start point
					clearRoute();
					LatLon startPoint = null;
					if (poi == null) {
						startPoint = latLon;
					} else {
						startPoint = new LatLon(poi.getLatitude(), poi
								.getLongitude());
					}

					sharedPreference.edit().putString(
							MapActivity.PREF_ROUTE_FROM,
							startPoint.getLatitude()
									+ MapActivity.LAT_LON_SEPARATOR
									+ startPoint.getLongitude()).commit();

					// Clearing destination point if it is same as source point
					String route_from = sharedPreference.getString(
							MapActivity.PREF_ROUTE_FROM, null);
					String route_to = sharedPreference.getString(
							MapActivity.PREF_ROUTE_TO, null);
					if (route_from != null && route_to != null
							&& route_from.equals(route_to)) {
						activity.getMapLayers().getNavigationLayer()
								.setPointToNavigate(null);
						sharedPreference.edit().putString(
								MapActivity.PREF_ROUTE_TO, null).commit();
						;
					}
					activity.getMapLayers().getNavigationLayer()
							.setPointFromNavigate(startPoint);
				} else if (position == 2) {// setting target point
					clearRoute();
					LatLon endPoint = null;
					if (poi == null) {
						endPoint = latLon;
					} else {
						endPoint = new LatLon(poi.getLatitude(), poi
								.getLongitude());
					}

					sharedPreference.edit().putString(
							MapActivity.PREF_ROUTE_TO,
							endPoint.getLatitude()
									+ MapActivity.LAT_LON_SEPARATOR
									+ endPoint.getLongitude()).commit();

					// Clearing source point if it is same as target point
					String route_from = sharedPreference.getString(
							MapActivity.PREF_ROUTE_FROM, null);
					String route_to = sharedPreference.getString(
							MapActivity.PREF_ROUTE_TO, null);
					if (route_from != null && route_to != null
							&& route_from.equals(route_to)) {
						sharedPreference.edit().putString(
								MapActivity.PREF_ROUTE_FROM, null).commit();
						;
					}
					activity.getMapLayers().getNavigationLayer()
							.setPointToNavigate(endPoint);
				}
				setLocation(null, "");
				view.refreshMap();
			}
		});
		builder.show();
	}

	private void clearRoute() {
		RoutingHelper routingHelper = activity.getRoutingHelper();
		if (routingHelper != null && routingHelper.isRouteCalculated()) {
			OsmandSettings settings = view.getApplication().getSettings();
			settings.clearPointFromNavigate();
			activity.removeNflickRoutePoint();
			activity.navigateToPoint(null);
			activity.getMapLayers().getNavigationLayer().setPointFromNavigate(
					null);
			routingHelper.setFinalAndCurrentLocation(null, routingHelper
					.getCurrentLocation(), routingHelper.getCurrentGPXRoute());
			routingHelper.clearCurrentRoute(null);
			activity.getMapLayers().getRouteLayer().setPoints(null, false);
		}
	}

	private void showContextMenuForSelectedObjects() {
		if (selectedObjects.size() > 1) {
			Builder builder = new AlertDialog.Builder(view.getContext());
			String[] d = new String[selectedObjects.size()];
			int i = 0;
			for (Object o : selectedObjects) {
				d[i++] = selectedContextProvider.getObjectDescription(o);
			}
			builder.setItems(d, new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					// single selection at 0
					selectedObjects.set(0, selectedObjects.get(which));
					ArrayList<String> l = new ArrayList<String>();
					OnClickListener listener = selectedContextProvider
							.getActionListener(l, selectedObjects.get(0));
					activity.contextMenuPoint(latLon.getLatitude(), latLon
							.getLongitude(), l, listener);
				}
			});
			builder.show();
		} else {
			ArrayList<String> l = new ArrayList<String>();
			OnClickListener listener = selectedContextProvider
					.getActionListener(l, selectedObjects.get(0));
			activity.contextMenuPoint(latLon.getLatitude(), latLon
					.getLongitude(), l, listener);
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (latLon != null) {
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				if (pressedInTextView(event.getX(), event.getY())) {
					textView.setPressed(true);
					view.refreshMap();
				}
			}
		}
		if (event.getAction() == MotionEvent.ACTION_UP
				|| event.getAction() == MotionEvent.ACTION_CANCEL) {
			if (textView.isPressed()) {
				textView.setPressed(false);
				view.refreshMap();
			}
		}
		return false;
	}

	public void setSelectedObject(Object toShow) {
		selectedObjects.clear();
		if (toShow == null) {
			selectedContextProvider = null;
		} else {
			for (OsmandMapLayer l : view.getLayers()) {
				if (l instanceof ContextMenuLayer.IContextMenuProvider) {
					String des = ((ContextMenuLayer.IContextMenuProvider) l)
							.getObjectDescription(toShow);
					if (des != null) {
						selectedContextProvider = (IContextMenuProvider) l;
						selectedObjects.add(toShow);
					}
				}
			}
		}
	}

	public void setPoi(MapPoi poi) {
		this.poi = poi;
	}
}
