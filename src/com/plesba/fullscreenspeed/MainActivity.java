package com.plesba.fullscreenspeed;

import java.text.SimpleDateFormat;
import java.util.Date;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.plesba.fullscreenspeed.GPS_ListenerService.GPSBinder;

public class MainActivity extends Activity {
	private static final String TAG = "FullScreenSpeed";
	private SharedPreferences settings;
	private SharedPreferences.Editor editor;
	private String fontSizeStr = "248";
	private View rootView;
	private TextView displayView;
	private TextView logoView;
	private FileWriter write;
	private PowerManager.WakeLock wakeLock;
	private ServiceConnection gpsSvcConn;
	private GPS_ListenerService gpsService;
	@SuppressLint("SimpleDateFormat")
	private SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm:ss");
	private Thread updateThread;
	private Handler handler = new Handler();
	private boolean isGPSserviceBound;
	private String gpsTime = "";
	private String lastGPStime = "";
	private String latitude = "";
	private String lastLat = "";
	private String longitude = "";
	private String lastLong = "";
	private String speed = "";
	private String lastSpeed = "";
	private boolean colorToggle = true;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		write = FileWriter.getInstance();
		startGPSService();
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, TAG);
		initializeSettings();
		initializeGui(); // this method actually acquires the wakelock
		// start out the Data file
		write.data("SYSTIME,GPSTIME,LAT,LONG,SPEED");
		updateThread = new Thread() {
			public void run() {
				if (isGPSserviceBound) {
					gpsTime = gpsService.getTime();
					if (!gpsTime.equals(lastGPStime)) {
						speed = gpsService.getSpeed();
						latitude = gpsService.getLat();
						longitude = gpsService.getLong();
						// update the speed display, at least
						setDisplayText(speed);
						// decide whether to log it
						if (!lastSpeed.equals(speed)  ||
							!lastLat.equals(latitude) || 
							!lastLong.equals(longitude)) {
							write.data(clockFormat.format(new Date()) + "," + 
									gpsTime + "," + latitude + "," +
									longitude + "," + speed);									
						}
						lastGPStime = gpsTime;
						lastSpeed = speed;
						lastLat = latitude;
						lastLong = longitude;
					}	// gpsTIme hasn't changed, no need to update display nor write						
				}  // GPS service isn't bound, can't do anything
				handler.postDelayed(this, 300); // wait a while
			}
		};
	}

	// start of stuff to bind to GPS service so we can get values
	private void startGPSService() {
		startService(new Intent(this, GPS_ListenerService.class));
		gpsSvcConn = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName name, IBinder binder) {
				GPSBinder gpsBinder = (GPSBinder) binder;
				gpsService = gpsBinder.getService();
				isGPSserviceBound = true;
				write.syslog("GPS service bound");
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {
				isGPSserviceBound = false; 
				write.syslog("GPS service came unbound?");
			}
		};
		Intent intent = new Intent(this, GPS_ListenerService.class);
		bindService(intent, gpsSvcConn, Context.BIND_AUTO_CREATE);
		Log.i(TAG, "started gps service");
		write.syslog("Started to bind to GPS service");
	}
	
	private void initializeSettings() {
		settings = PreferenceManager.getDefaultSharedPreferences(this);;
		fontSizeStr = settings.getString("FONT_SIZE", fontSizeStr);
		write.syslog("read settings from preferences");
		write.syslog("FONT_SIZE: " + fontSizeStr);
	}

	private void initializeGui() {
	    requestWindowFeature(Window.FEATURE_NO_TITLE); 
	    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		//getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.activity_main);
		displayView = (TextView) findViewById(R.id.textView2);
		rootView = displayView.getRootView();
		logoView = (TextView) findViewById(R.id.textView1);
		logoView.setTextColor(Color.GRAY);
		displayView.setTextSize(TypedValue.COMPLEX_UNIT_PX, Float.valueOf(fontSizeStr)); // 1=DeviceIndependentPixels
		wakeLock.acquire();
		write.syslog("gui initialized");
	}

	private void toggleColor() {
		if (colorToggle) {
			rootView.setBackgroundColor(Color.BLACK);
			displayView.setBackgroundColor(Color.BLACK);
			displayView.setTextColor(Color.WHITE);
			colorToggle = false;
		} else {
			rootView.setBackgroundColor(Color.WHITE);
			displayView.setBackgroundColor(Color.WHITE);
			displayView.setTextColor(Color.BLACK);
			colorToggle = true;
		}
	}
	
	private void setDisplayText(final String str) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				displayView.setText(str);
			}
		});
	}

	@Override
	protected void onPause() {
		super.onPause();
		handler.removeCallbacks(updateThread);
		handler.postDelayed(updateThread, 0);
		write.syslog("MainActivity paused");
	}

	@Override
	protected void onResume() {
		super.onResume();
		handler.postDelayed(updateThread, 0);
		write.syslog("MainActivity resumed");
	}

	@Override
	protected void onStop() {
		// The activity is no longer visible (it is now "stopped")
		super.onStop();
		// stop GPS service
		unbindService(gpsSvcConn);
		stopService(new Intent(this, GPS_ListenerService.class));
		// release wake lock
		wakeLock.release();
		// close log files... but write this first.
		write.syslog("MainActivity stopped");
		write.finalize();

	}

	public void changeFontSize(int direction) {
		float increment = 10.0f;
		float currentSize = displayView.getTextSize();
		Log.i(TAG, "currentFontSize: " + currentSize);
		float newSize;
		switch (direction) {
		case -1:
			newSize = (currentSize - increment);
			break;
		case 0:
			newSize = Float.valueOf(fontSizeStr);
			break;
		case 1:
			newSize = (currentSize + increment);
			break;
		default:
			newSize = currentSize;
			break;
		}
		displayView.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSize); // 1=DeviceIndependentPixels
		editor = settings.edit();
		editor.putString("FONT_SIZE", Float.toString(newSize));
		editor.commit();
		write.syslog("changed font size to :" + newSize);
	}
	
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.smallerFontItem:
			changeFontSize(-1);
			return true;
		case R.id.biggerFontItem:
			changeFontSize(1);
			return true;
		case R.id.resetFontItem:
			changeFontSize(0);
			return true;
		case R.id.toggleColorItem:
			toggleColor();
			return true;
		case R.id.rollFilesItem:
			// start new files somehow
			write.rollFiles();
			gpsService.zeroMaxSpeed();
			write.syslog("files rolled");
			write.data("SYSTIME,GPSTIME,LAT,LONG,SPEED");
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}
