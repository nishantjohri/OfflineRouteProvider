package net.osmand.plus.activities;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import net.osmand.Algoritms;
import net.osmand.GPXUtilities;
import net.osmand.GPXUtilities.GPXFile;
import net.osmand.GPXUtilities.WptPt;
import net.osmand.OsmLogUtil;
import net.osmand.Version;
import net.osmand.data.MapTileDownloader.DownloadRequest;
import net.osmand.data.MapTileDownloader.IMapDownloaderCallback;
import net.osmand.map.IMapLocationListener;
import net.osmand.osm.LatLon;
import net.osmand.plus.BusyIndicator;
import net.osmand.plus.FavouritesDbHelper;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.ResourceManager;
import net.osmand.plus.activities.search.SearchActivity;
import net.osmand.plus.routing.RouteAnimation;
import net.osmand.plus.routing.RouteProvider.GPXRouteParams;
import net.osmand.plus.routing.RouteProvider.RouteService;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.views.AnimateDraggingMapThread;
import net.osmand.plus.views.MapTileLayer;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.plus.views.PointLocationLayer;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.j256.ormlite.android.apptools.OrmLiteBaseActivity;
import com.osmand.android.R;
import com.osmand.model.DatabaseHelper;
import com.osmand.model.MapPoi;
//public class MapActivity extends TrackedActivity implements
//IMapLocationListener, SensorEventListener {
public class MapActivity extends OrmLiteBaseActivity<DatabaseHelper> implements
		IMapLocationListener, SensorEventListener {

	public static final double DEFAULT_MAP_LAT = 1.296276;//Singapore
	public static final double DEFAULT_MAP_LON = 103.844032;
	
	// nFlick code
	public static final String REQUEST_MAP_ACTION = "requestAction";
	public static final String VIEW_ITINERARY = "ViewItinerary";
	public static final String REQUEST_MAP_GETTING_THERE = "GettingThere";
	public static final String ITINERARY_POI_LIST = "PoiList";
	public static final String ROUTE_POI = "RoutePoi";
	public static final String ROUTE_FROM = "RouteFrom";
	public static final String CURRENT_LOCATION = "CurrentLocation";
	public static final String ROUTE_TO = "RouteTo";
	public static final String RETURN_POI_ID = "returnPoiId";
	private LatLon hotelLocation, startLatLon;
	private TextView fromSearchView, bySearchView;
	private LinearLayout mapHeading1, mapHeading2;
	// Intent Extras
	public static final String EXTRA_POI_ID = "poiId";
	// Intent Actions
	public static final String ACTION_VIEW_FOR_ITINERARY = "viewForItinerary";
	public static final String ACTION_VIEW_FOR_SEARCH = "viewForSearch";
	public static final String CLASS_NAME_OF_POI_DETAILS = "poi_details_class_name";

	private static final String GPS_STATUS_ACTIVITY = "com.eclipsim.gpsstatus2.GPSStatus"; //$NON-NLS-1$
	private static final String GPS_STATUS_COMPONENT = "com.eclipsim.gpsstatus2"; //$NON-NLS-1$

	// stupid error but anyway hero 2.1 : always lost gps signal (temporarily
	// unavailable) for timeout = 2000
	private static final int GPS_TIMEOUT_REQUEST = 0;
	private static final int GPS_DIST_REQUEST = 0;
	// use only gps (not network) for 12 seconds
	private static final int USE_ONLY_GPS_INTERVAL = 12000;

	private static final int SHOW_POSITION_MSG_ID = 7;
	private static final int SHOW_POSITION_DELAY = 2500;
	private static final float ACCURACY_FOR_GPX_AND_ROUTING = 50;

	private static final int AUTO_FOLLOW_MSG_ID = 8;
	private static final int LOST_LOCATION_MSG_ID = 10;
	private static final long LOST_LOCATION_CHECK_DELAY = 20000;

	private long lastTimeAutoZooming = 0;

	private long lastTimeGPSLocationFixed = 0;

	/** Called when the activity is first created. */
	private OsmandMapTileView mapView;
	final private MapActivityActions mapActions = new MapActivityActions(this);
	private EditingPOIActivity poiActions;
	final private MapActivityLayers mapLayers = new MapActivityLayers(this);

	private SavingTrackHelper savingTrackHelper;
	private LiveMonitoringHelper liveMonitoringHelper;
	private RoutingHelper routingHelper;

	private boolean sensorRegistered = false;

	// Notification status
	private NotificationManager mNotificationManager;
	private int APP_NOTIFICATION_ID;
	// handler to show/hide trackball position and to link map with delay
	private Handler uiHandler = new Handler();
	// Current screen orientation
	private int currentScreenOrientation;
	//
	private Dialog progressDlg = null;
	// App settings
	private OsmandSettings settings;
	// Store previous map rotation settings for rotate button
	private Integer previousMapRotate = null;

	private RouteAnimation routeAnimation = new RouteAnimation();

	private boolean isMapLinkedToLocation = false;
	private ProgressDialog startProgressDialog;
	private List<DialogProvider> dialogProviders = new ArrayList<DialogProvider>(
			2);
	public List<Location> locations;
	public static final String PREF_ROUTE_FROM = "route_from";
	public static final String PREF_ROUTE_TO = "route_to";
	public static final String EGUIDE_PREFS = "eGuidePrefs";
	public static final String LAT_LON_SEPARATOR = "_";

	public static final String EXTRA_POI_NAME = "poi_name";
	private SharedPreferences sharedPreference;
	private static Class<Activity> poiDetailClass;
	public static boolean IS_NFLICK_SEARCH = false;
	private EditText mapSearchView;
	private ArrayList<MapPoi> searchedMapPois = new ArrayList<MapPoi>();
	private final int MAX_ZOOM = 21;
	private final int MIN_ZOOM = 7;
	
	private Notification getNotification() {
		Intent notificationIndent = new Intent(this, MapActivity.class);
		notificationIndent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		Notification notification = new Notification(R.drawable.icon, "", //$NON-NLS-1$
				System.currentTimeMillis());
		notification.setLatestEventInfo(this, Version.getAppName(this),
				getString(R.string.go_back_to_osmand), PendingIntent
						.getActivity(this, 0, notificationIndent,
								PendingIntent.FLAG_UPDATE_CURRENT));
		return notification;
	}

	public void removeNflickRoutePoint() {
		sharedPreference.edit().putString(MapActivity.PREF_ROUTE_FROM, null)
				.commit();
		sharedPreference.edit().putString(MapActivity.PREF_ROUTE_TO, null)
				.commit();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		settings = getMyApplication().getSettings();
		sharedPreference = getApplicationContext().getSharedPreferences(
				MapActivity.EGUIDE_PREFS, 0);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		// Full screen is not used here
		// getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
		// WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.main);
		settings.APPLICATION_MODE.set(ApplicationMode.CAR);
		/*mapSearchView = (EditText) findViewById(R.id.mapSearchView);
		fromSearchView = (TextView) findViewById(R.id.fromSearchView);
		bySearchView = (TextView) findViewById(R.id.bySearchView);
		mapHeading1 = (LinearLayout) findViewById(R.id.mapHeading1);
		mapHeading2 = (LinearLayout) findViewById(R.id.mapHeading2);*/
		
		// Toast.makeText(this, R.string.compass_enable_msg,
		// Toast.LENGTH_LONG).show();
		// Toast.makeText(this, R.string.compass_enable_msg,
		// Toast.LENGTH_LONG).show();
		// Toast.makeText(this, R.string.compass_enable_msg,
		// Toast.LENGTH_LONG).show();

		startProgressDialog = new ProgressDialog(this);
		startProgressDialog.setCancelable(true);
		((OsmandApplication) getApplication())
				.checkApplicationIsBeingInitialized(this, startProgressDialog);
		// Do some action on close
		startProgressDialog
				.setOnDismissListener(new DialogInterface.OnDismissListener() {
					@Override
					public void onDismiss(DialogInterface dialog) {
						OsmandApplication app = ((OsmandApplication) getApplication());
						if (settings.MAP_VECTOR_DATA.get()
								&& app.getResourceManager().getRenderer()
										.isEmpty()) {
							Toast.makeText(MapActivity.this,
									getString(R.string.no_vector_map_loaded),
									Toast.LENGTH_LONG).show();
						}
					}
				});
		parseLaunchIntentLocation();

		mapView = (OsmandMapTileView) findViewById(R.id.osmMapView);
		mapView.setZoom(14);
		mapView.setLatLon(DEFAULT_MAP_LAT, DEFAULT_MAP_LON);
		mapView
				.setTrackBallDelegate(new OsmandMapTileView.OnTrackBallListener() {
					@Override
					public boolean onTrackBallEvent(MotionEvent e) {
						showAndHideMapPosition();
						return MapActivity.this.onTrackballEvent(e);
					}

				});
		poiActions = new EditingPOIActivity(this);
		getMyApplication().getResourceManager().getMapTileDownloader()
				.addDownloaderCallback(new IMapDownloaderCallback() {
					@Override
					public void tileDownloaded(DownloadRequest request) {
						if (request != null && !request.error
								&& request.fileToSave != null) {
							ResourceManager mgr = getMyApplication()
									.getResourceManager();
							mgr.tileDownloaded(request);
						}
						if (request == null || !request.error) {
							mapView.tileDownloaded(request);
						}
					}
				});

		savingTrackHelper = new SavingTrackHelper(this);
		liveMonitoringHelper = new LiveMonitoringHelper(this);
		LatLon pointToNavigate = settings.getPointToNavigate();

		routingHelper = getMyApplication().getRoutingHelper();
		// This situtation could be when navigation suddenly crashed and after
		// restarting
		// it tries to continue the last route
		if (settings.FOLLOW_THE_ROUTE.get()
				&& !routingHelper.isRouteCalculated()) {
			restoreRoutingMode(pointToNavigate);
		}

		mapView.setMapLocationListener(this);
		mapLayers.createLayers(mapView);
		Location location = null;
		if (!settings.isLastKnownMapLocation()) {
			// show first time when application ran
			LocationManager service = (LocationManager) getSystemService(LOCATION_SERVICE);
			for (String provider : service.getAllProviders()) {
				try {
					Location loc = service.getLastKnownLocation(provider);
					if (location == null) {
						location = loc;
					} else if (loc != null
							&& location.getTime() < loc.getTime()) {
						location = loc;
					}
				} catch (IllegalArgumentException e) {
					Log.d(OsmLogUtil.TAG, "Location provider not available"); //$NON-NLS-1$
				}
			}
			if (location != null) {
				mapView.setLatLon(location.getLatitude(), location
						.getLongitude());
				mapView.setZoom(14);
			}
		}

		addDialogProvider(mapActions);
		addDialogProvider(poiActions);
		addDialogProvider(mapLayers.getOsmBugsLayer());

		/**************************** nFlick Code *********************************************/
		settings.USE_INTERNET_TO_DOWNLOAD_TILES.set(false);
		settings.ROUTER_SERVICE.set(RouteService.OSMAND);
		settings.MAP_VECTOR_DATA.set(true);
		// settings.ROTATE_MAP.set(OsmandSettings.ROTATE_MAP_COMPASS);
		// settings.ROTATE_MAP.set(OsmandSettings.ROTATE_MAP_NONE);

		if (getIntent() != null && getIntent().getExtras() != null) {
			routingHelper.setFinalAndCurrentLocation(null, routingHelper
					.getCurrentLocation(), routingHelper.getCurrentGPXRoute());
			routingHelper.clearCurrentRoute(null);
			String requestMapAction = getIntent().getExtras().getString(
					REQUEST_MAP_ACTION);
			if (requestMapAction != null) {
				settings.setPoiFilterForMap(null);
				mapLayers.getPoiMapLayer().setFilter(null);
				if (requestMapAction.equals(VIEW_ITINERARY)) {
					mapHeading2.setVisibility(View.GONE);
					mapHeading1.setVisibility(View.VISIBLE);
					
					Bundle b = getIntent().getExtras();
					String className = b.getString(CLASS_NAME_OF_POI_DETAILS);
					try {
						poiDetailClass = (Class<Activity>) Class
								.forName(className);
					} catch (ClassNotFoundException e1) {
						e1.printStackTrace();
					}
					ArrayList<MapPoi> pois = b
							.getParcelableArrayList(ITINERARY_POI_LIST);
					showItineraryPois(pois);
				} else if (requestMapAction.equals(ROUTE_POI)) {
					Bundle b = getIntent().getExtras();
					LatLon startLatLon = (LatLon) b.getSerializable(ROUTE_FROM);
					LatLon endPoint = (LatLon) b.getSerializable(ROUTE_TO);
					Location startPoint = new Location("map");
					startPoint.setLatitude(startLatLon.getLatitude());
					startPoint.setLongitude(startLatLon.getLongitude());
					mapView.setLatLon(startLatLon.getLatitude(), startLatLon.getLongitude());
					//navigateToPoint(startPoint, endPoint);
					mapActions.getDirections(startPoint, endPoint);
				}else if(requestMapAction.equals(REQUEST_MAP_GETTING_THERE)){
					mapHeading1.setVisibility(View.GONE);
					mapHeading2.setVisibility(View.VISIBLE);
					//hide the search layout
					Bundle b = getIntent().getExtras();
					//startLatLon is the current location
					if(b.getSerializable(CURRENT_LOCATION) != null){
						startLatLon = (LatLon) b.getSerializable(CURRENT_LOCATION);
					}
					
					if(startLatLon == null && location != null){
						startLatLon = new LatLon(location.getLatitude(), location.getLongitude());
					}
					
					long poiId = b.getLong(EXTRA_POI_ID);
					ArrayList<MapPoi> pois = new ArrayList<MapPoi>();
					/*try {
						Poi poi = getHelper().getPoiDao().queryForId(poiId);
						MapPoi mapPoi = new MapPoi(poiId, poi.getName(), poi.getLatitude(), poi.getLongitude(), 1);
						pois.add(mapPoi);
						//if startLatLon is null that menas user has not selected hotel
						
						List<Itinerary> queryForAll = getHelper().getItineraryDao().queryForAll();
						if(queryForAll != null && queryForAll.size() > 0){
							Itinerary itinerary = queryForAll.get(0);
							Long hotelId = itinerary.getHotelId();
							if(hotelId != null && hotelId > -1){
								Poi hotel = getHelper().getPoiDao().queryForId(hotelId);
								hotelLocation = new LatLon(hotel.getLatitude(), hotel.getLongitude());
							}
						}
					} catch (SQLException e1) {
						e1.printStackTrace();
					}*/
					
					initGettingHere(pois, startLatLon);
				}
			}
		}
		/************************** End Of nFlick Code ***********************************************/
	}
	
	private void initGettingHere(ArrayList<MapPoi> pois, LatLon fromLocation){
		if(pois == null || pois.size() <= 0){
			return;
		}
		settings.APPLICATION_MODE.set(ApplicationMode.CAR);
		routingHelper.setAppMode(ApplicationMode.CAR);
		
		//setting for route end point
		MapPoi poi = pois.get(0);
		sharedPreference.edit().putString(MapActivity.PREF_ROUTE_TO,
				poi.getLatitude()
						+ MapActivity.LAT_LON_SEPARATOR
						+ poi.getLongitude()).commit();
		
		if(fromLocation != null){
			sharedPreference.edit().putString(MapActivity.PREF_ROUTE_FROM,
				fromLocation.getLatitude()
						+ MapActivity.LAT_LON_SEPARATOR
						+ fromLocation.getLongitude()).commit();
		}
		
		//setting popup to show that place
		settings.SHOW_POI_OVER_MAP.set(true);
		mapLayers.getPoiMapLayer().setPois(pois, true);
		mapLayers.getContextMenuLayer().setLocation(new LatLon(poi.getLatitude(), poi.getLongitude()),"\n"+poi.getName()+"\n");
		settings.setMapLocationToShow(poi.getLatitude(), poi.getLongitude(), 15);
		mapView.setLatLon(poi.getLatitude(), poi.getLongitude());
	}

	/**
	 * This method is used by nFlick app
	 * 
	 * @param startPoint
	 * @param endPoint
	 */
	public void navigateToPoint(Location startPoint, LatLon endPoint) {
		Toast.makeText(this, R.string.calculating_route, Toast.LENGTH_LONG)
				.show();
		settings.ROUTER_SERVICE.set(RouteService.OSMAND);
		settings.FOLLOW_THE_ROUTE.set(false);
		settings.FOLLOW_THE_GPX_ROUTE.set(null);
		routingHelper.setFollowingMode(false);
		//updateApplicationModeSettings();
		mapView.refreshMap(true);
		settings.setMapLocationToShow(startPoint.getLatitude(), startPoint
				.getLongitude(), 15);

		mapLayers.getNavigationLayer()
				.setPointFromNavigate(
						new LatLon(startPoint.getLatitude(), startPoint
								.getLongitude()));
		mapLayers.getNavigationLayer().setPointToNavigate(endPoint);
		sharedPreference.edit().putString(
				MapActivity.PREF_ROUTE_FROM,
				startPoint.getLatitude() + MapActivity.LAT_LON_SEPARATOR
						+ startPoint.getLongitude()).commit();
		sharedPreference.edit().putString(
				MapActivity.PREF_ROUTE_TO,
				endPoint.getLatitude() + MapActivity.LAT_LON_SEPARATOR
						+ endPoint.getLongitude()).commit();

		settings.setPointFromNavigate(startPoint.getLatitude(), startPoint
				.getLongitude(), null);
		settings.setPointToNavigate(endPoint.getLatitude(), endPoint
				.getLongitude(), null);

		Toast.makeText(this, R.string.calculating_route, Toast.LENGTH_LONG)
				.show();
		routingHelper.setFinalAndCurrentLocation(endPoint, startPoint,
				routingHelper.getCurrentGPXRoute());
		mapView.setLatLon(startPoint.getLatitude(), startPoint.getLongitude());
	}

	@Override
	public void onBackPressed() {
		routingHelper.clearCurrentRoute(settings.getPointToNavigate());
		settings.clearPointFromNavigate();
		settings.clearPointToNavigate();
		Intent intent = new Intent();
		intent.putExtra(RETURN_POI_ID, "-1");
		//setResult(RESULT_OK, intent);
		//finish();
		
		if (getParent() == null) {
	        setResult(Activity.RESULT_OK, intent);
	    } else {
	        getParent().setResult(Activity.RESULT_OK, intent);
	    }
		super.onBackPressed();
	}

	public void loadPoiDetails(MapPoi poi) {
		Intent i = new Intent(MapActivity.this, poiDetailClass);
		i.putExtra(EXTRA_POI_ID, poi.getPoiId());
		i.putExtra(EXTRA_POI_NAME, poi.getName());
		i.setAction(ACTION_VIEW_FOR_ITINERARY);
		startActivity(i);
		finish();
	}

	private void showItineraryPois(ArrayList<MapPoi> pois) {
		if (pois != null && pois.size() > 0) {
			settings.SHOW_POI_OVER_MAP.set(true);
			makeUniqueMapPoiList(pois);
			mapLayers.getPoiMapLayer().setPois(pois, true);
			MapPoi poi = pois.get(0);
			settings.setMapLocationToShow(poi.getLatitude(),
					poi.getLongitude(), 15);
			locations = new ArrayList<Location>();
			Location location = null;
			for (MapPoi poiObj : pois) {
				location = new Location("map");
				location.setLatitude(poiObj.getLatitude());
				location.setLongitude(poiObj.getLongitude());
				locations.add(location);
			}
			mapLayers.getRouteLayer().setPoints(locations, true);
			mapView.setLatLon(poi.getLatitude(), poi.getLongitude());
		}
	}
	
	public void makeUniqueMapPoiList(List<MapPoi> mapPois){
		if(mapPois != null && mapPois.size() > 0){
			for (int i = 0; i < mapPois.size(); i++) {				
				for (int j = i+1 ; j < mapPois.size(); j++) {
					if(mapPois.get(i).equals(mapPois.get(j))){
						String displayLabel = mapPois.get(i).getDisplaySequenceNumber();
						displayLabel = displayLabel + ","+ (j+1);
						mapPois.get(i).setDisplaySequenceNumber(displayLabel);
						mapPois.get(j).setDuplicate(true);
					}
				}				
			}
			
			/*for(int i = 0 ; i < mapPois.size() ; i++){
				MapPoi mapPoi = mapPois.get(i);
				if(mapPoi.isDuplicate()){
					mapPois.remove(i);
					--i;
				}
			}*/
		}
	}

	/**
	 * This method is used for nFlick
	 */
	public void showRoute() {
		String route_from = sharedPreference.getString(PREF_ROUTE_FROM, null);
		String route_to = sharedPreference.getString(PREF_ROUTE_TO, null);

		if (route_from == null || route_from.equals("")
				|| route_from.split(LAT_LON_SEPARATOR).length != 2) {
			Toast.makeText(this, R.string.route_from_empty, Toast.LENGTH_SHORT)
					.show();
			return;
		}

		if ((route_to == null || route_to.equals("") || route_from
				.split(LAT_LON_SEPARATOR).length != 2)
				&& settings.getPointToNavigate() == null) {
			Toast.makeText(this, R.string.route_to_empty, Toast.LENGTH_SHORT)
					.show();
			return;
		}
		Location startPoint = new Location("map");
		startPoint.setLatitude(Double.parseDouble(route_from
				.split(LAT_LON_SEPARATOR)[0]));
		startPoint.setLongitude(Double.parseDouble(route_from
				.split(LAT_LON_SEPARATOR)[1]));

		LatLon endPoint = null;
		if (route_to == null) {
			endPoint = settings.getPointToNavigate();
		} else {
			endPoint = new LatLon(Double.parseDouble(route_to
					.split(LAT_LON_SEPARATOR)[0]), Double.parseDouble(route_to
					.split(LAT_LON_SEPARATOR)[1]));
		}
		if(mapHeading2 != null && mapHeading2.getVisibility() == View.GONE){
			mapActions.getDirections(startPoint, endPoint);
		}else{
			navigateToPoint(startPoint, endPoint);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (settings.MAP_SCREEN_ORIENTATION.get() != getRequestedOrientation()) {
			setRequestedOrientation(settings.MAP_SCREEN_ORIENTATION.get());
			// can't return from this method we are not sure if activity will be
			// recreated or not
		}
		mapLayers.getNavigationLayer().setPointFromNavigate(
				settings.getPointFromNavigate());
		mapLayers.getNavigationLayer().setPointToNavigate(
				settings.getPointToNavigate());
		currentScreenOrientation = getWindow().getWindowManager()
				.getDefaultDisplay().getOrientation();

		// for voice navigation
		if (settings.AUDIO_STREAM_GUIDANCE.get() != null) {
			setVolumeControlStream(settings.AUDIO_STREAM_GUIDANCE.get());
		} else {
			setVolumeControlStream(AudioManager.STREAM_MUSIC);
		}

		mapLayers.updateMapSource(mapView, null);
		updateApplicationModeSettings();

		mapLayers
				.getPoiMapLayer()
				.setFilter(
						settings
								.getPoiFilterForMap((OsmandApplication) getApplication()));

		mapLayers.getMapInfoLayer().getBackToLocation().setEnabled(false);
		// by default turn off causing unexpected movements due to network
		// establishing
		// best to show previous location
		setMapLinkedToLocation(false);

		// if destination point was changed try to recalculate route
		if (routingHelper.isFollowingMode()
				&& !Algoritms.objectEquals(settings.getPointToNavigate(),
						routingHelper.getFinalLocation())) {
			routingHelper.setFinalAndCurrentLocation(settings
					.getPointToNavigate(), routingHelper.getCurrentLocation(),
					routingHelper.getCurrentGPXRoute());
		}

		LocationManager service = (LocationManager) getSystemService(LOCATION_SERVICE);
		try {
			service.requestLocationUpdates(LocationManager.GPS_PROVIDER,
					GPS_TIMEOUT_REQUEST, GPS_DIST_REQUEST, gpsListener);
		} catch (IllegalArgumentException e) {
			Log.d(OsmLogUtil.TAG, "GPS location provider not available"); //$NON-NLS-1$
		}
		// try to always ask for network provide : it is faster way to find
		// location
		try {
			service.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
					GPS_TIMEOUT_REQUEST, GPS_DIST_REQUEST, networkListener);
		} catch (IllegalArgumentException e) {
			Log.d(OsmLogUtil.TAG, "Network location provider not available"); //$NON-NLS-1$
		}

		if (settings != null && settings.isLastKnownMapLocation()) {
			LatLon l = settings.getLastKnownMapLocation();
			mapView.setLatLon(l.getLatitude(), l.getLongitude());
			mapView.setZoom(settings.getLastKnownMapZoom());
		}

		settings.MAP_ACTIVITY_ENABLED.set(true);
		checkExternalStorage();
		showAndHideMapPosition();

		LatLon cur = new LatLon(mapView.getLatitude(), mapView.getLongitude());
		LatLon latLonToShow = settings.getAndClearMapLocationToShow();
		String mapLabelToShow = settings.getAndClearMapLabelToShow();
		Object toShow = settings.getAndClearObjectToShow();
		if (mapLabelToShow != null && latLonToShow != null) {
			mapLayers.getContextMenuLayer().setSelectedObject(toShow);
			mapLayers.getContextMenuLayer().setLocation(latLonToShow,
					mapLabelToShow);
		}
		if (latLonToShow != null && !latLonToShow.equals(cur)) {
			mapView.getAnimatedDraggingThread().startMoving(
					latLonToShow.getLatitude(), latLonToShow.getLongitude(),
					settings.getMapZoomToShow(), true);

		}

		View progress = mapLayers.getMapInfoLayer().getProgressBar();
		if (progress != null) {
			getMyApplication().getResourceManager().setBusyIndicator(
					new BusyIndicator(this, progress));
		}

		getMyApplication().getDaynightHelper().onMapResume();
		mapView.refreshMap(true);
	}

	private void notRestoreRoutingMode() {
		boolean changed = settings.APPLICATION_MODE
				.set(ApplicationMode.DEFAULT);
		updateApplicationModeSettings();
		routingHelper.clearCurrentRoute(null);
		mapView.refreshMap(changed);
	}

	private void restoreRoutingMode(final LatLon pointToNavigate) {
		final String gpxPath = settings.FOLLOW_THE_GPX_ROUTE.get();
		if (pointToNavigate == null && gpxPath == null) {
			notRestoreRoutingMode();
		} else {
			Builder builder = new AlertDialog.Builder(MapActivity.this);
			builder.setMessage(R.string.continue_follow_previous_route);
			builder.setPositiveButton(R.string.default_buttons_yes,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							AsyncTask<String, Void, GPXFile> task = new AsyncTask<String, Void, GPXFile>() {
								@Override
								protected GPXFile doInBackground(
										String... params) {
									if (gpxPath != null) {
										// Reverse also should be stored ?
										GPXFile f = GPXUtilities.loadGPXFile(
												MapActivity.this, new File(
														gpxPath), false);
										if (f.warning != null) {
											return null;
										}
										return f;
									} else {
										return null;
									}
								}

								@Override
								protected void onPostExecute(GPXFile result) {
									final GPXRouteParams gpxRoute = result == null ? null
											: new GPXRouteParams(result, false);
									LatLon endPoint = pointToNavigate != null ? pointToNavigate
											: gpxRoute.getLastPoint();
									Location startPoint = gpxRoute == null ? null
											: gpxRoute.getStartPointForRoute();
									if (endPoint == null) {
										notRestoreRoutingMode();
									} else {
										routingHelper.setFollowingMode(true);
										routingHelper
												.setFinalAndCurrentLocation(
														endPoint, startPoint,
														gpxRoute);
										getMyApplication()
												.showDialogInitializingCommandPlayer(
														MapActivity.this);
									}
								}
							};
							task.execute(gpxPath);

						}
					});
			builder.setNegativeButton(R.string.default_buttons_no,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							notRestoreRoutingMode();
						}
					});
			builder.show();
		}

	}

	OsmandApplication getMyApplication() {
		return ((OsmandApplication) getApplication());
	}

	private void addDialogProvider(DialogProvider dp) {
		dialogProviders.add(dp);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		for (DialogProvider dp : dialogProviders) {
			dialog = dp.onCreateDialog(id);
			if (dialog != null) {
				return dialog;
			}
		}
		if (id == OsmandApplication.PROGRESS_DIALOG) {
			return startProgressDialog;
		}
		return null;
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		super.onPrepareDialog(id, dialog);
		for (DialogProvider dp : dialogProviders) {
			dp.onPrepareDialog(id, dialog);
		}
	}

	public void changeZoom(int newZoom) {
		if(newZoom <= MAX_ZOOM && newZoom >= MIN_ZOOM){
			boolean changeLocation = settings.AUTO_ZOOM_MAP.get();
			mapView.getAnimatedDraggingThread().startZooming(newZoom,
					changeLocation);
			showAndHideMapPosition();
		}
	}

	public void backToMainMenu() {
		final Dialog dlg = new Dialog(this, R.style.Dialog_Fullscreen);
		final View menuView = (View) getLayoutInflater().inflate(R.layout.menu,
				null);
		menuView.setBackgroundColor(Color.argb(200, 150, 150, 150));
		dlg.setContentView(menuView);
		MainMenuActivity.onCreateMainMenu(dlg.getWindow(), this);
		Animation anim = new Animation() {
			@Override
			protected void applyTransformation(float interpolatedTime,
					Transformation t) {
				ColorDrawable colorDraw = ((ColorDrawable) menuView
						.getBackground());
				colorDraw.setAlpha((int) (interpolatedTime * 200));
			}
		};
		anim.setDuration(700);
		anim.setInterpolator(new AccelerateInterpolator());
		menuView.setAnimation(anim);

		View showMap = dlg.findViewById(R.id.MapButton);
		showMap.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dlg.dismiss();
			}
		});
		View settingsButton = dlg.findViewById(R.id.SettingsButton);
		settingsButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				final Intent settings = new Intent(MapActivity.this,
						SettingsActivity.class);
				MapActivity.this.startActivity(settings);
				dlg.dismiss();
			}
		});

		View favouritesButton = dlg.findViewById(R.id.FavoritesButton);
		favouritesButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				final Intent favorites = new Intent(MapActivity.this,
						FavouritesActivity.class);
				favorites.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
				MapActivity.this.startActivity(favorites);
				dlg.dismiss();
			}
		});

		View closeButton = dlg.findViewById(R.id.CloseButton);
		closeButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dlg.dismiss();

				getMyApplication().closeApplication();
				// 1. Work for almost all cases when user open apps from main
				// menu
				Intent newIntent = new Intent(MapActivity.this,
						MainMenuActivity.class);
				newIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				newIntent.putExtra(MainMenuActivity.APP_EXIT_KEY,
						MainMenuActivity.APP_EXIT_CODE);
				startActivity(newIntent);
				// 2. good analogue but user will come back to the current
				// activity onResume()
				// so application is not reloaded !!!
				// moveTaskToBack(true);
				// 3. bad results if user comes from favorites
				// MapActivity.this.setResult(MainMenuActivity.APP_EXIT_CODE);
				// MapActivity.this.finish();
			}
		});

		View searchButton = dlg.findViewById(R.id.SearchButton);
		searchButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				final Intent search = new Intent(MapActivity.this,
						SearchActivity.class);
				LatLon loc = getMapLocation();
				search.putExtra(SearchActivity.SEARCH_LAT, loc.getLatitude());
				search.putExtra(SearchActivity.SEARCH_LON, loc.getLongitude());
				// causes wrong position caching:
				// search.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
				search.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				MapActivity.this.startActivity(search);
				dlg.dismiss();
			}
		});
		menuView.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				dlg.dismiss();
			}
		});

		dlg.show();
		// Intent newIntent = new Intent(MapActivity.this,
		// MainMenuActivity.class);
		// newIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		// startActivity(newIntent);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			// some application/hardware needs that back button reacts on key
			// up, so
			// that they could do some key combinations with it...
			// Victor : doing in that way doesn't close dialog properly!
			// return true;
		}/*
		 * else if (keyCode == KeyEvent.KEYCODE_SEARCH && event.getRepeatCount()
		 * == 0) { Intent newIntent = new Intent(MapActivity.this,
		 * SearchActivity.class); // causes wrong position caching:
		 * newIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); LatLon loc
		 * = getMapLocation(); newIntent.putExtra(SearchActivity.SEARCH_LAT,
		 * loc.getLatitude()); newIntent.putExtra(SearchActivity.SEARCH_LON,
		 * loc.getLongitude()); startActivity(newIntent);
		 * newIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); return true; }
		 */else if (!routingHelper.isFollowingMode()) {
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP
					&& event.getRepeatCount() == 0) {
				if (mapView.isZooming()) {
					changeZoom(mapView.getZoom() + 2);
				} else {
					changeZoom(mapView.getZoom() + 1);
				}
				return true;
			} else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
					&& event.getRepeatCount() == 0) {
				changeZoom(mapView.getZoom() - 1);
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	public void setMapLocation(double lat, double lon) {
		mapView.setLatLon(lat, lon);
		locationChanged(lat, lon, this);
	}

	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_MOVE
				&& settings.USE_TRACKBALL_FOR_MOVEMENTS.get()) {
			float x = event.getX();
			float y = event.getY();
			LatLon l = mapView.getLatLonFromScreenPoint(mapView
					.getCenterPointX()
					+ x * 15, mapView.getCenterPointY() + y * 15);
			setMapLocation(l.getLatitude(), l.getLongitude());
			return true;
		}
		return super.onTrackballEvent(event);
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	protected void setProgressDlg(Dialog progressDlg) {
		this.progressDlg = progressDlg;
	}

	protected Dialog getProgressDlg() {
		return progressDlg;
	}

	@Override
	protected void onStop() {
		if (routingHelper.isFollowingMode()) {
			mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			mNotificationManager.notify(APP_NOTIFICATION_ID, getNotification());
		}
		if (progressDlg != null) {
			progressDlg.dismiss();
			progressDlg = null;
		}
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		savingTrackHelper.close();
		routeAnimation.close();
		if (mNotificationManager != null) {
			mNotificationManager.cancel(APP_NOTIFICATION_ID);
		}
		getMyApplication().getResourceManager().getMapTileDownloader()
				.removeDownloaderCallback(mapView);
	}

	private void registerUnregisterSensor(Location location) {
		boolean currentShowingAngle = settings.SHOW_VIEW_ANGLE.get();
		int currentMapRotation = settings.ROTATE_MAP.get();
		boolean show = (currentShowingAngle && location != null)
				|| currentMapRotation == OsmandSettings.ROTATE_MAP_COMPASS;
		// show point view only if gps enabled
		if (sensorRegistered && !show) {
			Log.d(OsmLogUtil.TAG, "Disable sensor"); //$NON-NLS-1$
			((SensorManager) getSystemService(SENSOR_SERVICE))
					.unregisterListener(this);
			sensorRegistered = false;
			mapLayers.getLocationLayer().setHeading(null);
		} else if (!sensorRegistered && show) {
			Log.d(OsmLogUtil.TAG, "Enable sensor"); //$NON-NLS-1$
			SensorManager sensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
			Sensor s = sensorMgr.getDefaultSensor(Sensor.TYPE_ORIENTATION);
			if (s != null) {
				sensorMgr.registerListener(this, s,
						SensorManager.SENSOR_DELAY_UI);
			}
			sensorRegistered = true;
		}
	}

	public void backToLocationImpl() {
		mapLayers.getMapInfoLayer().getBackToLocation().setEnabled(false);
		PointLocationLayer locationLayer = mapLayers.getLocationLayer();
		if (!isMapLinkedToLocation()) {
			setMapLinkedToLocation(true);
			if (locationLayer.getLastKnownLocation() != null) {
				Location lastKnownLocation = locationLayer
						.getLastKnownLocation();
				AnimateDraggingMapThread thread = mapView
						.getAnimatedDraggingThread();
				int fZoom = mapView.getZoom() < 14 ? 14 : mapView.getZoom();
				thread.startMoving(lastKnownLocation.getLatitude(),
						lastKnownLocation.getLongitude(), fZoom, false);
			}
		}
		if (locationLayer.getLastKnownLocation() == null) {
			Toast.makeText(this, R.string.unknown_location, Toast.LENGTH_LONG)
					.show();
		}
	}

	private void updateSpeedBearing(Location location) {
		// For network/gps it's bad way (not accurate). It's widely used for
		// testing purposes
		// possibly keep using only for emulator case
		PointLocationLayer locationLayer = mapLayers.getLocationLayer();
		if (isRunningOnEmulator()
				&& locationLayer.getLastKnownLocation() != null
				&& location != null) {
			if (locationLayer.getLastKnownLocation().distanceTo(location) > 3) {
				float d = location.distanceTo(locationLayer
						.getLastKnownLocation());
				long time = location.getTime()
						- locationLayer.getLastKnownLocation().getTime();
				float speed;
				if (time == 0) {
					speed = 0;
				} else {
					speed = ((float) d * 1000) / time;
				}
				// Be aware only for emulator ! code is incorrect in case of
				// airplane
				if (speed > 100) {
					speed = 100;
				}
				location.setSpeed(speed);
			}
		}
		if (locationLayer.getLastKnownLocation() != null && location != null
				&& location.hasBearing()) {
			if (locationLayer.getLastKnownLocation().distanceTo(location) > 10
					&& !isRunningOnEmulator()) {
				location.setBearing(locationLayer.getLastKnownLocation()
						.bearingTo(location));
			}
		}
	}

	public void setLocation(Location location) {
		if (Log.isLoggable(OsmLogUtil.TAG, Log.DEBUG)) {
			Log.d(OsmLogUtil.TAG, "Location changed " + location.getProvider()); //$NON-NLS-1$
		}
		if (location != null) {
			// write only with 50 meters accuracy
			if (!location.hasAccuracy()
					|| location.getAccuracy() < ACCURACY_FOR_GPX_AND_ROUTING) {
				if (settings.SAVE_TRACK_TO_GPX.get()) {
					savingTrackHelper.insertData(location.getLatitude(),
							location.getLongitude(), location.getAltitude(),
							location.getSpeed(), location.getAccuracy(),
							location.getTime(), settings);
					if (settings.SHOW_CURRENT_GPX_TRACK.get()) {
						WptPt pt = new GPXUtilities.WptPt(location
								.getLatitude(), location.getLongitude(),
								location.getTime(), location.getAltitude(),
								location.getSpeed(), location.getAccuracy());
						mapLayers.getGpxLayer().addTrackPoint(pt);
					}
				}
			}
			if (settings.LIVE_MONITORING.get()) {
				liveMonitoringHelper.insertData(location.getLatitude(),
						location.getLongitude(), location.getAltitude(),
						location.getSpeed(), location.getAccuracy(), location
								.getTime(), settings);
			}
		}

		registerUnregisterSensor(location);
		updateSpeedBearing(location);
		mapLayers.getLocationLayer().setLastKnownLocation(location);
		if (routingHelper.isFollowingMode()) {
			if (location == null || !location.hasAccuracy()
					|| location.getAccuracy() < ACCURACY_FOR_GPX_AND_ROUTING) {
				// Update routing position
				routingHelper.setCurrentLocation(location);
				// Check with delay that gps location is not lost
				if (location != null && routingHelper.getLeftDistance() > 0) {
					Message msg = Message.obtain(uiHandler, new Runnable() {
						@Override
						public void run() {
							if (routingHelper.getLeftDistance() > 0
									&& settings.MAP_ACTIVITY_ENABLED.get()) {
								routingHelper.getVoiceRouter()
										.gpsLocationLost();
							}
						}
					});
					msg.what = LOST_LOCATION_MSG_ID;
					uiHandler.removeMessages(LOST_LOCATION_MSG_ID);
					uiHandler
							.sendMessageDelayed(msg, LOST_LOCATION_CHECK_DELAY);
				}
			}
		}

		if (location != null) {
			if (isMapLinkedToLocation()) {
				if (settings.AUTO_ZOOM_MAP.get() && location.hasSpeed()) {
					int z = defineZoomFromSpeed(location.getSpeed(), mapView
							.getZoom());
					if (mapView.getZoom() != z && !mapView.mapIsAnimating()) {
						long now = System.currentTimeMillis();
						// prevent ui hysterisis (check time interval for
						// autozoom)
						if (Math.abs(mapView.getZoom() - z) > 1
								|| (lastTimeAutoZooming - now) > 6500) {
							lastTimeAutoZooming = now;
							mapView.setZoom(z);
						}
					}
				}
				int currentMapRotation = settings.ROTATE_MAP.get();
				if (location.hasBearing()
						&& currentMapRotation == OsmandSettings.ROTATE_MAP_BEARING) {
					mapView.setRotate(-location.getBearing());
				}
				mapView.setLatLon(location.getLatitude(), location
						.getLongitude());
			} else {
				if (!mapLayers.getMapInfoLayer().getBackToLocation()
						.isEnabled()) {
					mapLayers.getMapInfoLayer().getBackToLocation().setEnabled(
							true);
				}
			}
		} else {
			if (mapLayers.getMapInfoLayer().getBackToLocation().isEnabled()) {
				mapLayers.getMapInfoLayer().getBackToLocation().setEnabled(
						false);
			}
		}
		// When location is changed we need to refresh map in order to show
		// movement!
		mapView.refreshMap();
	}

	public int defineZoomFromSpeed(float speed, int currentZoom) {
		speed *= 3.6;
		if (speed < 4) {
			return currentZoom;
		} else if (speed < 33) {
			// less than 33 - show 17
			return 17;
		} else if (speed < 53) {
			return 16;
		} else if (speed < 83) {
			return 15;
		}
		// more than 80 - show 14 (it is slow)
		return 14;
	}

	public void navigateToPoint(LatLon point) {
		if (point != null) {
			settings.setPointToNavigate(point.getLatitude(), point
					.getLongitude(), null);
		} else {
			settings.clearPointToNavigate();
		}
		routingHelper.setFinalAndCurrentLocation(point, routingHelper
				.getCurrentLocation(), routingHelper.getCurrentGPXRoute());
		mapLayers.getNavigationLayer().setPointToNavigate(point);
	}

	public Location getLastKnownLocation() {
		return mapLayers.getLocationLayer().getLastKnownLocation();
	}

	public LatLon getMapLocation() {
		return new LatLon(mapView.getLatitude(), mapView.getLongitude());
	}

	public LatLon getPointToNavigate() {
		return mapLayers.getNavigationLayer().getPointToNavigate();
	}

	public RoutingHelper getRoutingHelper() {
		return routingHelper;
	}

	private boolean isRunningOnEmulator() {
		if (Build.DEVICE.equals("generic")) { //$NON-NLS-1$ 
			return true;
		}
		return false;
	}

	private boolean useOnlyGPS() {
		return (routingHelper != null && routingHelper.isFollowingMode())
				|| (System.currentTimeMillis() - lastTimeGPSLocationFixed) < USE_ONLY_GPS_INTERVAL
				|| isRunningOnEmulator();
	}

	// Working with location listeners
	private LocationListener networkListener = new LocationListener() {

		@Override
		public void onLocationChanged(Location location) {
			// double check about use only gps
			// that strange situation but it could happen?
			if (!useOnlyGPS()) {
				setLocation(location);
			}
		}

		@Override
		public void onProviderDisabled(String provider) {
			if (!useOnlyGPS()) {
				setLocation(null);
			}
		}

		@Override
		public void onProviderEnabled(String provider) {
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
			if (LocationProvider.OUT_OF_SERVICE == status && !useOnlyGPS()) {
				setLocation(null);
			}
		}
	};

	private LocationListener gpsListener = new LocationListener() {
		@Override
		public void onLocationChanged(Location location) {
			if (location != null) {
				lastTimeGPSLocationFixed = location.getTime();
			}
			setLocation(location);
		}

		@Override
		public void onProviderDisabled(String provider) {
			LocationManager service = (LocationManager) getSystemService(LOCATION_SERVICE);
			if (!useOnlyGPS()
					&& service
							.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
				Location loc = service
						.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
				if (loc != null
						&& (System.currentTimeMillis() - loc.getTime()) < USE_ONLY_GPS_INTERVAL) {
					setLocation(loc);
				}
			} else {
				setLocation(null);
			}
		}

		@Override
		public void onProviderEnabled(String provider) {
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
			if (LocationProvider.TEMPORARILY_UNAVAILABLE == status) {
				if (routingHelper.isFollowingMode()
						&& routingHelper.getLeftDistance() > 0) {
					routingHelper.getVoiceRouter().gpsLocationLost();
				}
			} else if (LocationProvider.OUT_OF_SERVICE == status) {
				if (routingHelper.isFollowingMode()
						&& routingHelper.getLeftDistance() > 0) {
					routingHelper.getVoiceRouter().gpsLocationLost();
				}
			} else if (LocationProvider.AVAILABLE == status) {
				// Do not remove right now network listener
				// service.removeUpdates(networkListener);
			}

		}
	};

	@Override
	protected void onPause() {
		super.onPause();
		LocationManager service = (LocationManager) getSystemService(LOCATION_SERVICE);
		service.removeUpdates(gpsListener);
		service.removeUpdates(networkListener);

		SensorManager sensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
		sensorMgr.unregisterListener(this);
		sensorRegistered = false;

		getMyApplication().getDaynightHelper().onMapPause();

		settings.setLastKnownMapLocation((float) mapView.getLatitude(),
				(float) mapView.getLongitude());
		AnimateDraggingMapThread animatedThread = mapView
				.getAnimatedDraggingThread();
		if (animatedThread.isAnimating() && animatedThread.getTargetZoom() != 0) {
			settings.setMapLocationToShow(animatedThread.getTargetLatitude(),
					animatedThread.getTargetLongitude(), animatedThread
							.getTargetZoom());
		}

		settings.setLastKnownMapZoom(mapView.getZoom());
		settings.MAP_ACTIVITY_ENABLED.set(false);
		getMyApplication().getResourceManager().interruptRendering();
		getMyApplication().getResourceManager().setBusyIndicator(null);
	}

	public void updateApplicationModeSettings() {
		boolean currentShowingAngle = settings.SHOW_VIEW_ANGLE.get();
		int currentMapRotation = settings.ROTATE_MAP.get();
		if (currentMapRotation == OsmandSettings.ROTATE_MAP_NONE) {
			mapView.setRotate(0);
		}
		if (!currentShowingAngle) {
			mapLayers.getLocationLayer().setHeading(null);
		}
		routingHelper.setAppMode(settings.getApplicationMode());
		mapView.setMapPosition(settings.POSITION_ON_MAP.get());
		registerUnregisterSensor(getLastKnownLocation());
		mapLayers.getMapInfoLayer().applyTheme();
		mapLayers.updateLayers(mapView);
		mapLayers.updateMapSource(mapView, settings.MAP_TILE_SOURCES);
		getMyApplication().getDaynightHelper().setDayNightMode(
				settings.DAYNIGHT_MODE.get());
	}

	public void switchRotateMapMode() {
		if (settings.ROTATE_MAP.get() != OsmandSettings.ROTATE_MAP_COMPASS) {
			previousMapRotate = settings.ROTATE_MAP.get();
			settings.ROTATE_MAP.set(OsmandSettings.ROTATE_MAP_COMPASS);
		} else if (previousMapRotate != null) {
			settings.ROTATE_MAP.set(previousMapRotate);
		} else {
			// settings.ROTATE_MAP.set(settings.ROTATE_MAP.getProfileDefaultValue());
			settings.ROTATE_MAP.set(OsmandSettings.ROTATE_MAP_NONE);
		}
		registerUnregisterSensor(getLastKnownLocation());
		if (settings.ROTATE_MAP.get() != OsmandSettings.ROTATE_MAP_COMPASS) {
			mapView.setRotate(0);
		}
		mapView.refreshMap();
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			// some application/hardware needs that back button reacts on key
			// up, so
			// that they could do some key combinations with it...
			// Android 1.6 doesn't have onBackPressed() method it should be
			// finish instead!
			// onBackPressed();
			// return true;
		} else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
			contextMenuPoint(mapView.getLatitude(), mapView.getLongitude());
			return true;
		} else
		// Parrot device has only dpad left and right
		if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
			changeZoom(mapView.getZoom() - 1);
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
			changeZoom(mapView.getZoom() + 1);
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}

	public void checkExternalStorage() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			// ok
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			Toast.makeText(this, R.string.sd_mounted_ro, Toast.LENGTH_LONG)
					.show();
		} else {
			Toast.makeText(this, R.string.sd_unmounted, Toast.LENGTH_LONG)
					.show();
		}
	}

	public void showAndHideMapPosition() {
		mapView.setShowMapPosition(true);
		Message msg = Message.obtain(uiHandler, new Runnable() {
			@Override
			public void run() {
				if (mapView.isShowMapPosition()) {
					mapView.setShowMapPosition(false);
					mapView.refreshMap();
				}
			}

		});
		msg.what = SHOW_POSITION_MSG_ID;
		uiHandler.removeMessages(SHOW_POSITION_MSG_ID);
		uiHandler.sendMessageDelayed(msg, SHOW_POSITION_DELAY);
	}

	@Override
	public void locationChanged(double newLatitude, double newLongitude,
			Object source) {
		// when user start dragging
		if (mapLayers.getLocationLayer().getLastKnownLocation() != null) {
			setMapLinkedToLocation(false);
			if (!mapLayers.getMapInfoLayer().getBackToLocation().isEnabled()) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mapLayers.getMapInfoLayer().getBackToLocation()
								.setEnabled(true);
					}
				});
			}
		}
	}

	public OsmandMapTileView getMapView() {
		return mapView;
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// Attention : sensor produces a lot of events & can hang the system
		float val = event.values[0];
		if (currentScreenOrientation == 1) {
			val += 90;
		}
		if (settings.ROTATE_MAP.get() == OsmandSettings.ROTATE_MAP_COMPASS) {
			mapView.setRotate(-val);
		}
		if (settings.SHOW_VIEW_ANGLE.get().booleanValue()) {
			if (mapLayers.getLocationLayer().getHeading() == null
					|| Math
							.abs(mapLayers.getLocationLayer().getHeading()
									- val) > 10) {
				mapLayers.getLocationLayer().setHeading(val);
			}
		}

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.map_menu, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean val = super.onPrepareOptionsMenu(menu);

		MenuItem navigateToPointMenu = menu
				.findItem(R.id.map_navigate_to_point);
		if (navigateToPointMenu != null) {
			if (settings.getPointToNavigate() != null) {
				navigateToPointMenu.setTitle((routingHelper.isRouteCalculated()
						|| routingHelper.isFollowingMode() || routingHelper
						.isRouteBeingCalculated()) ? R.string.stop_routing
						: R.string.stop_navigation);
				navigateToPointMenu.setVisible(true);
			} else {
				navigateToPointMenu.setVisible(false);
			}
		}
		MenuItem muteMenu = menu.findItem(R.id.map_mute);
		muteMenu.setVisible(false);
		/*
		 * if(muteMenu != null){ if (routingHelper.getFinalLocation() != null &&
		 * routingHelper.isFollowingMode()) {
		 * muteMenu.setTitle(routingHelper.getVoiceRouter().isMute() ?
		 * R.string.menu_mute_on : R.string.menu_mute_off);
		 * muteMenu.setVisible(true); } else { muteMenu.setVisible(false); } }
		 */
		MenuItem directions = menu.findItem(R.id.map_get_directions);
		if (routingHelper.isRouteCalculated()) {
			directions.setTitle(R.string.show_route);
		} else {
			directions.setTitle(R.string.get_directions);
		}

		MenuItem animateMenu = menu.findItem(R.id.map_animate_route);

		if (animateMenu != null) {
			if (settings.TEST_ANIMATE_ROUTING.get()) {
				animateMenu
						.setTitle(routeAnimation.isRouteAnimating() ? R.string.animate_route_off
								: R.string.animate_route);
				animateMenu.setVisible("1".equals(Secure.getString(
						getContentResolver(), Secure.ALLOW_MOCK_LOCATION))
						&& settings.getPointToNavigate() != null
						&& routingHelper.isRouteCalculated());
				animateMenu.setVisible(true);
			} else {
				animateMenu.setVisible(false);
			}
		}
		return val;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();

		if (itemId == R.id.map_show_settings) {
			final Intent intentSettings = new Intent(MapActivity.this,
					SettingsActivity.class);
			startActivity(intentSettings);
			return true;
		} else if (itemId == R.id.map_where_am_i) {
			backToLocationImpl();
			return true;
		} else if (itemId == R.id.map_show_gps_status) {
			startGpsStatusIntent();
			return true;
		} else if (itemId == R.id.map_get_directions) {
			if (routingHelper.isRouteCalculated()) {
				Intent intent = new Intent(MapActivity.this,
						ShowRouteInfoActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivityForResult(intent, 100);
				// mapActions.aboutRoute();
			} else {
				showRoute();
				mapLayers.getContextMenuLayer().setLocation(null, "");
				// mapView.refreshMap();
				/*
				 * Location loc = getLastKnownLocation(); if (loc != null) {
				 * mapActions.getDirections(loc.getLatitude(),
				 * loc.getLongitude(), true); } else {
				 * mapActions.getDirections(mapView.getLatitude(),
				 * mapView.getLongitude(), true); }
				 */
			}
			return true;
		} else if (itemId == R.id.map_layers) {
			mapLayers.openLayerSelectionDialog(mapView);
			return true;
		} else if (itemId == R.id.map_specify_point) {
			if (IS_NFLICK_SEARCH) {
				startActivity(new Intent(MapActivity.this,
						MapSearchActivity.class));
			} else {
				// next 2 lines replaced for Issue 493, replaced by new 3 lines
				// NavigatePointActivity dlg = new NavigatePointActivity(this);
				// dlg.showDialog();
				Intent newIntent = new Intent(MapActivity.this,
						SearchActivity.class);
				// causes wrong position caching:
				// newIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
				LatLon mapLoc = getMapLocation();
				newIntent.putExtra(SearchActivity.SEARCH_LAT, mapLoc
						.getLatitude());
				newIntent.putExtra(SearchActivity.SEARCH_LON, mapLoc
						.getLongitude());
				newIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(newIntent);
			}
			return true;
		} else if (itemId == R.id.map_mute) {
			routingHelper.getVoiceRouter().setMute(
					!routingHelper.getVoiceRouter().isMute());
			return true;
		} else if (itemId == R.id.map_navigate_to_point) {
			clearRoute();
			mapLayers.getRouteLayer().setPoints(locations, true);
			/*if (mapLayers.getNavigationLayer().getPointToNavigate() != null) {
				if (routingHelper.isRouteCalculated()
						|| routingHelper.isFollowingMode()
						|| routingHelper.isRouteBeingCalculated()) {
					routingHelper.setFinalAndCurrentLocation(null,
							routingHelper.getCurrentLocation(),
							routingHelper.getCurrentGPXRoute());
				} else {
					navigateToPoint(null);
				}
			} else {
				navigateToPoint(new LatLon(mapView.getLatitude(),
						mapView.getLongitude()));
			}*/
			mapView.refreshMap();
			 
			return true;
		} else if (itemId == R.id.map_show_point_options) {
			contextMenuPoint(mapView.getLatitude(), mapView.getLongitude());
			return true;
		} else if (itemId == R.id.map_animate_route) {
			// animate moving on route
			routeAnimation.startStopRouteAnimation(routingHelper, this);
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	private void startGpsStatusIntent() {
		Intent intent = new Intent();
		intent.setComponent(new ComponentName(GPS_STATUS_COMPONENT,
				GPS_STATUS_ACTIVITY));
		ResolveInfo resolved = getPackageManager().resolveActivity(intent,
				PackageManager.MATCH_DEFAULT_ONLY);
		if (resolved != null) {
			startActivity(intent);
		} else {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(getString(R.string.gps_status_app_not_found));
			builder.setPositiveButton(getString(R.string.default_buttons_yes),
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							Intent intent = new Intent(Intent.ACTION_VIEW, Uri
									.parse("market://search?q=pname:"
											+ GPS_STATUS_COMPONENT));
							try {
								startActivity(intent);
							} catch (ActivityNotFoundException e) {
							}
						}
					});
			builder.setNegativeButton(getString(R.string.default_buttons_no),
					null);
			builder.show();
		}
	}

	protected void parseLaunchIntentLocation() {
		Intent intent = getIntent();
		if (intent != null && intent.getData() != null) {
			Uri data = intent.getData();
			if ("http".equalsIgnoreCase(data.getScheme())
					&& "download.osmand.net".equals(data.getHost())
					&& "/go".equals(data.getPath())) {
				String lat = data.getQueryParameter("lat");
				String lon = data.getQueryParameter("lon");
				if (lat != null && lon != null) {
					try {
						double lt = Double.parseDouble(lat);
						double ln = Double.parseDouble(lon);
						settings
								.setLastKnownMapLocation((float) lt, (float) ln);
						String zoom = data.getQueryParameter("z");
						if (zoom != null) {
							settings
									.setLastKnownMapZoom(Integer.parseInt(zoom));
						}
					} catch (NumberFormatException e) {
					}
				}
			}
		}
	}

	public FavouritesDbHelper getFavoritesHelper() {
		return getMyApplication().getFavorites();
	}

	public void contextMenuPoint(final double latitude, final double longitude) {
		contextMenuPoint(latitude, longitude, null, null);
	}

	public void contextMenuPoint(final double latitude, final double longitude,
			List<String> additionalItems,
			final DialogInterface.OnClickListener additionalActions) {
		Builder builder = new AlertDialog.Builder(this);
		final int sizeAdditional = additionalActions == null
				|| additionalItems == null ? 0 : additionalItems.size();
		List<String> actions = new ArrayList<String>();
		if (sizeAdditional > 0) {
			actions.addAll(additionalItems);
		}
		final int[] contextMenuStandardActions = new int[] {
				R.string.context_menu_item_navigate_point,
				R.string.context_menu_item_show_route,
				R.string.context_menu_item_search,
				R.string.context_menu_item_add_favorite,
				R.string.context_menu_item_share_location,
				R.string.context_menu_item_create_poi,
				R.string.context_menu_item_add_waypoint,
				R.string.context_menu_item_open_bug,
				// MapTileLayer menu actions
				R.string.context_menu_item_update_map,
				R.string.context_menu_item_download_map };
		int actionsToUse = (mapView.getMainLayer() instanceof MapTileLayer) ? contextMenuStandardActions.length
				: contextMenuStandardActions.length - 2;
		for (int j = 0; j < actionsToUse; j++) {
			actions
					.add(getResources()
							.getString(contextMenuStandardActions[j]));
		}

		builder.setItems(actions.toArray(new String[actions.size()]),
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (which < sizeAdditional) {
							additionalActions.onClick(dialog, which);
							return;
						}
						int standardId = contextMenuStandardActions[which
								- sizeAdditional];
						if (standardId == R.string.context_menu_item_navigate_point) {
							navigateToPoint(new LatLon(latitude, longitude));
						} else if (standardId == R.string.context_menu_item_show_route) {
							mapActions
									.getDirections(latitude, longitude, false);
						} else if (standardId == R.string.context_menu_item_search) {
							Intent intent = new Intent(MapActivity.this,
									SearchActivity.class);
							intent
									.putExtra(SearchActivity.SEARCH_LAT,
											latitude);
							intent.putExtra(SearchActivity.SEARCH_LON,
									longitude);
							intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
							startActivity(intent);
						} else if (standardId == R.string.context_menu_item_add_favorite) {
							mapActions.addFavouritePoint(latitude, longitude);
						} else if (standardId == R.string.context_menu_item_share_location) {
							mapActions.shareLocation(latitude, longitude,
									mapView.getZoom());
						} else if (standardId == R.string.context_menu_item_create_poi) {
							getPoiActions().showCreateDialog(latitude,
									longitude);
						} else if (standardId == R.string.context_menu_item_add_waypoint) {
							mapActions.addWaypoint(latitude, longitude);
						} else if (standardId == R.string.context_menu_item_open_bug) {
							mapLayers.getOsmBugsLayer().openBug(latitude,
									longitude);
						} else if (standardId == R.string.context_menu_item_update_map) {
							mapActions.reloadTile(mapView.getZoom(), latitude,
									longitude);
						} else if (standardId == R.string.context_menu_item_download_map) {
							DownloadTilesDialog dlg = new DownloadTilesDialog(
									MapActivity.this,
									(OsmandApplication) getApplication(),
									mapView);
							dlg.openDialog();
						}
					}
				});
		builder.create().show();
	}

	public MapActivityActions getMapActions() {
		return mapActions;
	}

	public EditingPOIActivity getPoiActions() {
		return poiActions;
	}

	public MapActivityLayers getMapLayers() {
		return mapLayers;
	}

	public SavingTrackHelper getSavingTrackHelper() {
		return savingTrackHelper;
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	public static void launchMapActivityMoveToTop(Activity activity) {
		Intent newIntent = new Intent(activity, MapActivity.class);
		newIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
		activity.startActivity(newIntent);
	}

	private boolean isMapLinkedToLocation() {
		return isMapLinkedToLocation;
	}

	public void setMapLinkedToLocation(boolean isMapLinkedToLocation) {
		if (!isMapLinkedToLocation) {
			int autoFollow = settings.AUTO_FOLLOW_ROUTE.get();
			// try without AUTO_FOLLOW_ROUTE_NAV (see forum discussion 'Simplify
			// our navigation preference menu')
			// if(autoFollow > 0 && (!settings.AUTO_FOLLOW_ROUTE_NAV.get() ||
			// routingHelper.isFollowingMode())){
			if (autoFollow > 0 && routingHelper.isFollowingMode()) {
				uiHandler.removeMessages(AUTO_FOLLOW_MSG_ID);
				Message msg = Message.obtain(uiHandler, new Runnable() {
					@Override
					public void run() {
						if (settings.MAP_ACTIVITY_ENABLED.get()) {
							Toast.makeText(MapActivity.this,
									R.string.auto_follow_location_enabled,
									Toast.LENGTH_SHORT).show();
							backToLocationImpl();
						}
					}
				});
				msg.what = AUTO_FOLLOW_MSG_ID;
				uiHandler.sendMessageDelayed(msg, autoFollow * 1000);
			}
		}
		this.isMapLinkedToLocation = isMapLinkedToLocation;
	}

	public void clearRoute() {
		settings.clearPointFromNavigate();
		removeNflickRoutePoint();
		navigateToPoint(null);
		mapLayers.getNavigationLayer().setPointFromNavigate(null);
		routingHelper.setFinalAndCurrentLocation(null, routingHelper
				.getCurrentLocation(), routingHelper.getCurrentGPXRoute());
		routingHelper.clearCurrentRoute(null);
	}
	
	public void btnMapSearchClicked(View v){
		searchAndDisplayPoiResults();
	}
	
	private void searchAndDisplayPoiResults(){
		String searchText = mapSearchView.getText().toString().trim();
		if(!searchText.equals("")){
			searchedMapPois.clear();
			String query = "";
			searchText = searchText.replace("'", "''");
			DatabaseHelper dbHelper = getHelper();
			query = "Select * from Poi ";
			query += " where name like '%" + searchText
					+ "%'";
			/*query += " where name like '%" + searchText
					+ "%' or description like '%" + searchText + "%' ";
			query += " order by name asc";*/
			SQLiteDatabase db = dbHelper.getReadableDatabase();
			Cursor cursor = db.rawQuery(query, null);
			if(cursor != null){
				MapPoi mapPoi = null;
				int sequence = 1;
				while (cursor.moveToNext()) {
					mapPoi = new MapPoi(cursor.getLong(cursor.getColumnIndex("id")),
							cursor.getString(cursor.getColumnIndex("name")),
							cursor.getDouble(cursor.getColumnIndex("latitude")),
							cursor.getDouble(cursor.getColumnIndex("longitude")),
							sequence++);
					searchedMapPois.add(mapPoi);
				}
				cursor.close();
			}
			
			//searched poi displaying on map
			if(searchedMapPois.size() > 0){
				mapLayers.getRouteLayer().setPoints(null, true);
				MapPoi mapPoi = searchedMapPois.get(0);
				settings.SHOW_POI_OVER_MAP.set(true);
				mapLayers.getContextMenuLayer().setLocation(new LatLon(mapPoi.getLatitude(), mapPoi.getLongitude()), "\n"+mapPoi.getName()+"\n");
				mapLayers.getPoiMapLayer().setPois(searchedMapPois, true);
				mapView.setLatLon(mapPoi.getLatitude(), mapPoi.getLongitude());
				//mapView.refreshMap();
			}else{
				searchedMapPois.clear();
				mapLayers.getContextMenuLayer().setLocation(null, "");
				mapLayers.getPoiMapLayer().setPois(searchedMapPois, true);
				mapView.refreshMap();
				Toast.makeText(this, "No result found.", Toast.LENGTH_SHORT).show();
			}
		}else{
			Toast.makeText(this, "No result found.", Toast.LENGTH_SHORT).show();
		}
	}
	
	private void openFromDialog(){
		Builder builder = new AlertDialog.Builder(this);
		String[] items = null;
		
		if(hotelLocation != null){
			items = new String[]{"Current Location", "My Hotel"};
		}else{
			items = new String[]{"Current Location"};
		}		
		final int size = items.length;
		builder.setTitle("From");
		builder.setItems(items, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int location) {
				if(size == 2 && location == 1){
					startLatLon = hotelLocation;
					fromSearchView.setText("My Hotel");
				}else{
					fromSearchView.setText("Current Location");
				}
				if(startLatLon != null){
					sharedPreference.edit().putString(MapActivity.PREF_ROUTE_FROM,
						startLatLon.getLatitude()
								+ MapActivity.LAT_LON_SEPARATOR
								+ startLatLon.getLongitude()).commit();
					
					String route_from = sharedPreference.getString(PREF_ROUTE_FROM, null);
					String route_to = sharedPreference.getString(PREF_ROUTE_TO, null);

					if (route_from != null && route_to != null){
						Location startPoint = new Location("map");
						startPoint.setLatitude(Double.parseDouble(route_from
								.split(LAT_LON_SEPARATOR)[0]));
						startPoint.setLongitude(Double.parseDouble(route_from
								.split(LAT_LON_SEPARATOR)[1]));
						
						LatLon endPoint = new LatLon(Double.parseDouble(route_to
								.split(LAT_LON_SEPARATOR)[0]), Double.parseDouble(route_to
								.split(LAT_LON_SEPARATOR)[1]));
						navigateToPoint(startPoint, endPoint);
					}
				}
			}
		});
		builder.show();
	}
	
	private void openRouteModeDialog(){
		Builder builder = new AlertDialog.Builder(this);
		Resources res = getResources();
		final String[] items = res.getStringArray(R.array.map_route_mode);
			
		builder.setTitle(R.string.transport_route_mode);
		builder.setItems(items, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int position) {
				if(position == 0){
					bySearchView.setText(items[0]);
					settings.APPLICATION_MODE.set(ApplicationMode.CAR);
					routingHelper.setAppMode(ApplicationMode.CAR);
				}else if(position == 1){
					bySearchView.setText(items[1]);
					settings.APPLICATION_MODE.set(ApplicationMode.PEDESTRIAN);
					routingHelper.setAppMode(ApplicationMode.PEDESTRIAN);
				}
				
				String route_from = sharedPreference.getString(PREF_ROUTE_FROM, null);
				String route_to = sharedPreference.getString(PREF_ROUTE_TO, null);

				if (route_from != null && route_to != null){
					Location startPoint = new Location("map");
					startPoint.setLatitude(Double.parseDouble(route_from
							.split(LAT_LON_SEPARATOR)[0]));
					startPoint.setLongitude(Double.parseDouble(route_from
							.split(LAT_LON_SEPARATOR)[1]));
					
					LatLon endPoint = new LatLon(Double.parseDouble(route_to
							.split(LAT_LON_SEPARATOR)[0]), Double.parseDouble(route_to
							.split(LAT_LON_SEPARATOR)[1]));
					navigateToPoint(startPoint, endPoint);
				}
			}
		});
		builder.show();
	}
	
	public void bySpinnerClicked(View v){
		openRouteModeDialog();
	}
	
	public void fromSpinnerClicked(View v){
		openFromDialog();
	}
	
	public void mapBackButtonClicked(View v){
		onBackPressed();
	}
}