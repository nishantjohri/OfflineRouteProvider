package com.osmand.model;

import android.os.Parcel;
import android.os.Parcelable;

public class MapPoi implements Parcelable {
	private long poiId;
	private String name;
	private double latitude;
	private double longitude;
	private int seq;
	private String displaySequenceNumber = "";
	private boolean duplicate;

	public MapPoi() {

	}

	public MapPoi(Parcel parcel) {
		this.poiId = parcel.readLong();
		this.name = parcel.readString();
		this.latitude = parcel.readDouble();
		this.longitude = parcel.readDouble();
		this.seq = parcel.readInt();
		this.displaySequenceNumber = parcel.readString();
	}

	public MapPoi(long poiId, String name, double latitude, double longitude,
			int seq) {
		super();
		this.poiId = poiId;
		this.name = name;
		this.latitude = latitude;
		this.longitude = longitude;
		this.seq = seq;
		this.displaySequenceNumber = ""+seq;
	}

	public long getPoiId() {
		return poiId;
	}

	public void setPoiId(long poiId) {
		this.poiId = poiId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public double getLatitude() {
		return latitude;
	}

	public void setLatitude(double latitude) {
		this.latitude = latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	public void setLongitude(double longitude) {
		this.longitude = longitude;
	}

	public int getSeq() {
		return seq;
	}

	public void setSeq(int seq) {
		this.seq = seq;
		this.displaySequenceNumber = ""+seq;
	}

	/**
	 * @return the displaySequenceNumber
	 */
	public String getDisplaySequenceNumber() {
		return displaySequenceNumber;
	}

	/**
	 * @param displaySequenceNumber the displaySequenceNumber to set
	 */
	public void setDisplaySequenceNumber(String displaySequenceNumber) {
		this.displaySequenceNumber = displaySequenceNumber;
	}

	
	
	/**
	 * @return the duplicate
	 */
	public boolean isDuplicate() {
		return duplicate;
	}

	/**
	 * @param duplicate the duplicate to set
	 */
	public void setDuplicate(boolean duplicate) {
		this.duplicate = duplicate;
	}

	@Override
	public int describeContents() {
		return 0;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		long temp;
		temp = Double.doubleToLongBits(latitude);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(longitude);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MapPoi other = (MapPoi) obj;
		if (Double.doubleToLongBits(latitude) != Double
				.doubleToLongBits(other.latitude))
			return false;
		if (Double.doubleToLongBits(longitude) != Double
				.doubleToLongBits(other.longitude))
			return false;
		return true;
	}
	@Override
	public String toString() {
		return "Name :"+name +"Lat " + latitude +" Lon "+ longitude; //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	public void writeToParcel(Parcel dest, int arg1) {
		dest.writeLong(poiId);
		dest.writeString(name);
		dest.writeDouble(latitude);
		dest.writeDouble(longitude);
		dest.writeInt(seq);
		dest.writeString(displaySequenceNumber);
	}

	public static final Parcelable.Creator CREATOR = new Parcelable.Creator() {
		public MapPoi createFromParcel(Parcel in) {
			return new MapPoi(in);
		}

		public MapPoi[] newArray(int size) {
			return new MapPoi[size];
		}
	};
}