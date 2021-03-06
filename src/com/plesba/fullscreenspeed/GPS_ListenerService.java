package com.plesba.fullscreenspeed;

import java.text.SimpleDateFormat;
import java.util.Date;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;

public class GPS_ListenerService extends Service {
	private static final String TAG = "GPS_ListenerService";
	private FileWriter write;
	private LocationManager locationManager;
	private LocationListener gpsLocationListener;
	private long lastGPStime;
	private double lastLatitude;
	private double lastLongitude;
	private float lastSpeed;
	private float maxSpeed;
	private final float metersSec_in_MPH = 2.23694f;
	@SuppressLint("SimpleDateFormat")
	private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS");

	// other modules will call these public methonds
	public String getTime() {
		return timeFormat.format(new Date(lastGPStime));
	}

	// latitude ranges from 0.0 to 90.0
	// In the US, latitude is always double-digits: 44.xxyyzz
	// We'll keep six digits after the decimal point
	public String getLat() {
		String lValue = Double.toString(lastLatitude);
		if (lValue.length() < 9)
			return lValue;
		return lValue.substring(0, 9);
	} // latitude has max 2 digits before

	// in the US, Longitude is always three digits: 123.xxyyzz
	// We'll keep six digits after the decimal point (ITIS)
	public String getLong() {
		String lValue = Double.toString(lastLongitude);
		if (lValue.length() < 10)
			return lValue;
		return lValue.substring(0, 10);
	} // longitude has up to 3 digits

	// speed is reported in meters/second
	// speed needs three digits
	// 145
	public String getSpeed() {
		if (lastSpeed < 1.0f) { return "000"; }
		float mph = lastSpeed * metersSec_in_MPH;
		String lValue = Integer.toString((int) mph);
		if (lValue.length() == 1) { return "  " + lValue; }
		if (lValue.length() == 2) { return " " + lValue; }
		return lValue;
	}

	public String getMaxSpeed() {
		if (maxSpeed < 1.0f) { return "0.0"; }
		String lValue = Float.toString(maxSpeed * metersSec_in_MPH);
		if (lValue.length() < 7) {
			return lValue;
		} else
			return lValue.substring(0, 7);
	}
	
	// setup this service to allow binding for access to public methods above.
	// http://developer.android.com/guide/components/bound-services.html
	private final IBinder mBinder = new GPSBinder();

	public class GPSBinder extends Binder {
		GPS_ListenerService getService() {
			return GPS_ListenerService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	// the usual 'Service' methods below
	@Override
	public void onCreate() {
		super.onCreate();
		write = FileWriter.getInstance();
		// instantiate the inner class
		gpsLocationListener = new GPSLocationListener();
		// get the system manager
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		// and demand Speed values
		Criteria criteria = new Criteria();
		criteria.setSpeedRequired(true);
		//criteria.setSpeedAccuracy(Criteria.ACCURACY_FINE); // Not supported in Android 2.1 !!
        int minMeters = 1;
        int minMillis = 200;
		// register the listener
		locationManager.requestLocationUpdates(
				locationManager.getBestProvider(criteria, false),
                minMillis, minMeters, gpsLocationListener);
		write.syslog(TAG + " GPS updates requested.");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		locationManager.removeUpdates(gpsLocationListener);
	}

	private class GPSLocationListener implements LocationListener,
			GpsStatus.Listener {
		Location mLastLocation = null;
		boolean isGPSFix;

		public void onGpsStatusChanged(int event) {
			switch (event) {
			case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
				if (mLastLocation != null)
					isGPSFix = (SystemClock.elapsedRealtime() - lastGPStime) < 3000;
				if (isGPSFix) { // A fix has been acquired.
					write.syslog(TAG + " GPS has a fix.");
				} else { // The fix has been lost.
					write.syslog(TAG + " GPS DOES NOT have a fix.");
				}
				break;
			case GpsStatus.GPS_EVENT_FIRST_FIX:
				write.syslog(TAG + " GPS got first fix.");
				isGPSFix = true;
				break;
			}
		}

		@Override
		public void onLocationChanged(Location location) {
			mLastLocation = location;
			lastGPStime = location.getTime();
			lastLatitude = location.getLatitude();
			lastLongitude = location.getLongitude();
			lastSpeed = location.getSpeed();
			if (lastSpeed > maxSpeed) {
				maxSpeed = lastSpeed;
			}
			// Log.i(TAG, "GPS update received.");
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
			String statusDescription = "unknown";
			switch (status) {
			case LocationProvider.OUT_OF_SERVICE:
				statusDescription = "OUT_OF_SERVICE";
				break;
			case LocationProvider.AVAILABLE:
				statusDescription = "AVAILABLE";
				break;
			case LocationProvider.TEMPORARILY_UNAVAILABLE:
				statusDescription = "TEMPORARILY_UNAVAILABLE";
				break;
			}
			write.syslog(TAG + " GPS provider status changed to "
					+ statusDescription);
			write.syslog(TAG + " Last speed was: " + getSpeed());
		}

		@Override
		public void onProviderEnabled(String provider) {
			write.syslog(TAG + " GPS provider enabled.");
		}

		@Override
		public void onProviderDisabled(String provider) {
			write.syslog(TAG + " GPS provider disabled?");
		}

	}

	public void zeroMaxSpeed() {
		maxSpeed = 0.0f;
	}

}
