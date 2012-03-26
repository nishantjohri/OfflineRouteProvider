package net.osmand.plus.views;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

import com.osmand.android.R;

public class MapPoiListCursorAdapter extends CursorAdapter {
	private LayoutInflater inflater;
	private OnClickListener onClickListener;
	public static final String POI_ID_COLUMN = "id";
	public static final String POI_NAME_COLUMN = "name";

	public MapPoiListCursorAdapter(Context context,
			Cursor poiListCursorWrapper,
			OnClickListener onClickListener) {
		super(context, poiListCursorWrapper, true);
		inflater = LayoutInflater.from(context);
		this.onClickListener = onClickListener;
	}

	@Override
	public void bindView(View view, final Context context, Cursor cursor) {
		final long poiId = cursor.getLong(cursor.getColumnIndex(POI_ID_COLUMN));
		view.setTag(poiId);
		view.setOnClickListener(onClickListener);

		TextView poiNameView = (TextView) view.findViewById(R.id.poiName);
		String name = cursor.getString(cursor
				.getColumnIndex(POI_NAME_COLUMN));
		poiNameView.setText(name);
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		return inflater.inflate(R.layout.map_poi_search_list_item, parent,
				false);
	}
}
