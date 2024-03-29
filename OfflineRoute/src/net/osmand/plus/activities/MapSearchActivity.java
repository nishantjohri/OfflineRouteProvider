package net.osmand.plus.activities;

import java.sql.SQLException;

import net.osmand.plus.OsmandSettings;
import net.osmand.plus.views.MapPoiListCursorAdapter;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.EditText;
import android.widget.FilterQueryProvider;
import android.widget.ListView;

import com.j256.ormlite.android.AndroidCompiledStatement;
import com.j256.ormlite.android.apptools.OrmLiteBaseActivity;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.StatementBuilder.StatementType;
import com.osmand.android.R;
import com.osmand.model.DatabaseHelper;
import com.osmand.model.NoIdCursorWrapper;

public class MapSearchActivity extends OrmLiteBaseActivity<DatabaseHelper>
		implements OnClickListener {
	private ProgressDialog progessDialog;
	private Cursor poiListCursorWrapper;
	private ListView searchList;
	private MapPoiListCursorAdapter poiListCursorAdapter = null;
	private EditText searchView;
	private OsmandSettings settings;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.map_poi_search);
		settings = getMyApplication().getSettings();
		initViews();
		new LoadDataTask().execute();
	}

	OsmandApplication getMyApplication() {
		return ((OsmandApplication) getApplication());
	}

	private void initViews() {
		searchView = (EditText) findViewById(R.id.searchView);
		searchList = (ListView) findViewById(R.id.mapPoiSearchListView);
	}

	private TextWatcher filterTextWatcher = new TextWatcher() {

		public void afterTextChanged(Editable s) {
		}

		public void beforeTextChanged(CharSequence s, int start, int count,
				int after) {
		}

		public void onTextChanged(CharSequence s, int start, int before,
				int count) {
			if (s != null)
				poiListCursorAdapter.getFilter().filter(s);
		}

	};

	@Override
	protected void onResume() {
		super.onResume();
		searchView.addTextChangedListener(filterTextWatcher);
	}

	@Override
	protected void onPause() {
		super.onPause();
		searchView.removeTextChangedListener(filterTextWatcher);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (poiListCursorWrapper != null) {
			poiListCursorWrapper.close();
		}
		if (poiListCursorAdapter != null) {
			poiListCursorAdapter = null;
		}

	}

	private void loadUiData() {
		poiListCursorAdapter = new MapPoiListCursorAdapter(this,
				poiListCursorWrapper, this);
		searchList.setAdapter(poiListCursorAdapter);
		poiListCursorAdapter.notifyDataSetChanged();
		poiListCursorAdapter.setFilterQueryProvider(filterQueryProvider);

		searchView.setText("");
		searchList.setEmptyView(findViewById(R.id.noMapPoiSearchRes));
	}

	private FilterQueryProvider filterQueryProvider = new FilterQueryProvider() {
		public Cursor runQuery(CharSequence constraint) {
			constraint = constraint.toString().replace("'", "''");
			DatabaseHelper dbHelper = getHelper();
			String query = "Select * from Poi ";
			query += " where name like '%" + constraint
					+ "%' or description like '%" + constraint + "%' ";
			query += " order by name asc";
			SQLiteDatabase db = dbHelper.getReadableDatabase();
			Cursor cursor = db.rawQuery(query, null);
			poiListCursorWrapper = new NoIdCursorWrapper(cursor,
					MapPoiListCursorAdapter.POI_ID_COLUMN);
			return poiListCursorWrapper;
		}
	};

	private class LoadDataTask extends AsyncTask<Void, Void, Integer> {
		private final Context currentCtx = MapSearchActivity.this;

		private static final int UNEXPECTED_ERROR = 1;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progessDialog = new ProgressDialog(currentCtx);
			progessDialog.setTitle(getString(R.string.title_general_loading));
			progessDialog.setMessage(getString(R.string.msg_general_loading));
			progessDialog.setCancelable(false);
			progessDialog.setIndeterminate(true);
			progessDialog.show();
		}

		@Override
		protected Integer doInBackground(Void... params) {
			int result = 0;
			/*try {
				DatabaseHelper dbHelper = getHelper();
				Dao<Poi, Long> poiDao = dbHelper.getPoiDao();
				QueryBuilder<Poi, Long> qb = poiDao.queryBuilder();
				qb.orderBy("name", true);
				PreparedQuery<Poi> query = qb.prepare();
				AndroidCompiledStatement compiledStatement = (AndroidCompiledStatement) query
						.compile(dbHelper.getConnectionSource()
								.getReadOnlyConnection(), StatementType.SELECT);
				poiListCursorWrapper = new NoIdCursorWrapper(
						compiledStatement.getCursor(),
						PoiListCursorAdapter.POI_ID_COLUMN);
			} catch (SQLException e) {
				result = UNEXPECTED_ERROR;
				e.printStackTrace();
			}*/
			return result;
		}

		@Override
		protected void onPostExecute(Integer result) {
			if (progessDialog != null && progessDialog.isShowing()) {
				progessDialog.dismiss();
			}
			super.onPostExecute(result);
			loadUiData();
		}
	}

	@Override
	public void onClick(View v) {
		/*v.setClickable(false);
		final Long poiId = (Long) v.getTag();
		Poi poi = null;
		try {
			poi = getHelper().getPoiDao().queryForId(poiId);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		showNflickPoiOption(poi, v);*/
	}

	/*private void showNflickPoiOption(final Poi poi, final View view) {
		if (poi == null) {
			return;
		}
		Builder builder = new AlertDialog.Builder(this);
		Resources res = getResources();
		String[] items = res.getStringArray(R.array.map_poi_options);

		builder.setTitle(poi.getName());
		builder.setItems(items,
				new android.content.DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int position) {

						SharedPreferences sharedPreference = getApplicationContext()
								.getSharedPreferences(MapActivity.EGUIDE_PREFS,
										0);
						if (position == 0) {
							settings.setMapLocationToShow(poi.getLatitude(),
									poi.getLongitude(), 15, null,
									poi.getName(), null);
							finish();
							//mapLayers.getContextMenuLayer().setLocation(null,"");
						} else if (position == 1) {// setting start point
							sharedPreference
									.edit()
									.putString(
											MapActivity.PREF_ROUTE_FROM,
											poi.getLatitude()
													+ MapActivity.LAT_LON_SEPARATOR
													+ poi.getLongitude())
									.commit();

							// Clearing destination point if it is same as
							// source point
							String route_from = sharedPreference.getString(
									MapActivity.PREF_ROUTE_FROM, null);
							String route_to = sharedPreference.getString(
									MapActivity.PREF_ROUTE_TO, null);
							if (route_from != null && route_to != null
									&& route_from.equals(route_to)) {
								settings.clearPointToNavigate();
								sharedPreference
										.edit()
										.putString(MapActivity.PREF_ROUTE_TO,
												null).commit();
								;
							}
						} else if (position == 2) {// setting target point
							sharedPreference
									.edit()
									.putString(
											MapActivity.PREF_ROUTE_TO,
											poi.getLatitude()
													+ MapActivity.LAT_LON_SEPARATOR
													+ poi.getLongitude())
									.commit();

							// Clearing source point if it is same as target
							// point
							String route_from = sharedPreference.getString(
									MapActivity.PREF_ROUTE_FROM, null);
							String route_to = sharedPreference.getString(
									MapActivity.PREF_ROUTE_TO, null);
							if (route_from != null && route_to != null
									&& route_from.equals(route_to)) {
								sharedPreference
										.edit()
										.putString(MapActivity.PREF_ROUTE_FROM,
												null).commit();
								;
							}
							settings.setPointToNavigate(poi.getLatitude(),
									poi.getLongitude(), null);
						}

						if(view != null){
							view.setClickable(true);
						}	
					}
				});
		//builder.show();
		AlertDialog alert = builder.create();
		alert.show();
		alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialog) {
				if(view != null){
					view.setClickable(true);
				}				
			}
		});
		
		alert.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface arg0) {
				if(view != null){
					view.setClickable(true);
				}	
			}
		});
	}*/
}
