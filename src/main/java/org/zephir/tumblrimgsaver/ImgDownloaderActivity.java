package org.zephir.tumblrimgsaver;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class ImgDownloaderActivity extends Activity {
	// ===========================================================
	// Constants
	// ===========================================================
	public static final boolean LOG_DEBUG = true;
	private static final String LOG_TAG = ImgDownloaderActivity.class.getSimpleName();

	// ===========================================================
	// Fields
	// ===========================================================

	// ===========================================================
	// Constructors
	// ===========================================================

	// ===========================================================
	// Methods for/from SuperClass/Interfaces
	// ===========================================================
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
	
			// Get the intent that started this activity
			final Intent intent = getIntent();
			if (intent != null) {
			    final Uri data = intent.getData();
			    if (data != null) {
					final Intent ServiceIntent = new Intent(this, DownloadIntentService.class);
					ServiceIntent.putExtra(DownloadIntentService.URL, data.toString());
					startService(ServiceIntent);
					if (LOG_DEBUG) { Log.d(LOG_TAG, "ImgDownloaderActivity.onCreate called with URL: " + data.toString()); }

			    } else {
					Log.w(LOG_TAG, "No URL data in Intent");
			    }
			}

		} catch (Throwable e) {
			Log.e(LOG_TAG, "onCreate KO: " + e.toString(), e);
		} finally {
			finish();
		}
	}

	// ===========================================================
	// Methods
	// ===========================================================

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================
}
