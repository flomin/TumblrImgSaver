package org.zephir.tumblrimgsaver;

import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

public class DownloadIntentService extends IntentService {

	// ===========================================================
	// Constants
	// ===========================================================
	public static final String LOG_TAG = DownloadIntentService.class.getSimpleName();
	public static final String URL = "URL";
	public static final String TUMBLR_URL_PATTERN = "^(.*\\/)post\\/(\\d++).*$";
	public static final String TUMBLR_READ_API_PATTERN = "$1api/read?id=$2";

	// ===========================================================
	// Fields
	// ===========================================================
	private Handler mainThreadHandler;

	// ===========================================================
	// Constructors
	// ===========================================================
	/**
	 * A constructor is required, and must call the super IntentService(String) constructor with a name for the worker thread.
	 */
	public DownloadIntentService() {
		super(DownloadIntentService.class.getSimpleName());
		mainThreadHandler = new Handler();
	}

	// ===========================================================
	// Methods for/from SuperClass/Interfaces
	// ===========================================================
	/**
	 * The IntentService calls this method from the default worker thread with the intent that started the service. When this method returns,
	 * IntentService stops the service, as appropriate.
	 */
	@Override
	protected void onHandleIntent(final Intent intent) {
		try {
			// Get the parameters
			final String url = intent.getStringExtra("URL");

			if (!TextUtils.isEmpty(url)) {
				if (ImgDownloaderActivity.LOG_DEBUG) {
					Log.d(LOG_TAG, "onHandleIntent called with url=" + url);
				}

				// Get redirected URL
				if (ImgDownloaderActivity.LOG_DEBUG) {
					Log.d(LOG_TAG, "Querying for final redirected url");
				}
				final String finalUrl = getFinalRedirectedUrl(url);
				if (ImgDownloaderActivity.LOG_DEBUG) {
					Log.d(LOG_TAG, "getFinalRedirectedUrl(" + url + ") -> " + finalUrl);
				}

				// Query tumblr read api for the images list of the post
				final List<String> imgList = getTumblrPostImages(finalUrl);

				if (CollectionUtils.isNotEmpty(imgList)) {
					final DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
					for (final String imgSrc : imgList) {
						if (ImgDownloaderActivity.LOG_DEBUG) {
							Log.d(LOG_TAG, "-> DownloadManager enqueued with: " + imgSrc);
						}
						// download the images
						final Uri imgUri = Uri.parse(imgSrc);
						final String imgName = imgUri.getLastPathSegment();
						final Request request = new Request(imgUri).setNotificationVisibility(Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setTitle("Downloading: " + imgSrc).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, imgName);
						downloadManager.enqueue(request);
						
						if (ImgDownloaderActivity.LOG_DEBUG) {
							Log.d(LOG_TAG, "<- DownloadManager enqueued with: " + imgSrc);
						}

						final String imgSrc_f = imgSrc;
						mainThreadHandler.post(new Runnable() {
							@Override
							public void run() {
								Toast.makeText(getApplicationContext(), "Downloading: " + imgSrc_f, Toast.LENGTH_SHORT).show();
							}
						});
					}
				} else {
					Log.e(LOG_TAG, "No image found on URL: " + url);
					mainThreadHandler.post(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(getApplicationContext(), "No image found on URL: " + url, Toast.LENGTH_SHORT).show();
						}
					});
				}
			}
		} catch (final IllegalStateException e) {
			mainThreadHandler.post(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(getApplicationContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
				}
			});
		} catch (final Throwable e) {
			Log.e(LOG_TAG, "onHandleIntent KO: " + e.toString(), e);
			mainThreadHandler.post(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(getApplicationContext(), "Error ! (" + e.getClass().getSimpleName() + ")", Toast.LENGTH_SHORT).show();
				}
			});
		}
	}

	// ===========================================================
	// Methods
	// ===========================================================
	// private boolean resourceExists(final URL url) throws IOException {
	// final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	// connection.setRequestMethod("HEAD");
	// connection.connect();
	// return connection.getResponseCode() == 200;
	// }

	private String getFinalRedirectedUrl(final String url) throws Exception {
		HttpURLConnection connection = null;
		try {
			String finalUrl = url;
			do {
				connection = (HttpURLConnection) new URL(finalUrl).openConnection();
				connection.setInstanceFollowRedirects(false);
				connection.setUseCaches(false);
				connection.setRequestMethod("GET");
				connection.connect();
				int responseCode = connection.getResponseCode();
				if (responseCode >= 300 && responseCode < 400) {
					String redirectedUrl = connection.getHeaderField("Location");
					if (null == redirectedUrl) {
						break;
					}
					finalUrl = redirectedUrl;
				} else {
					break;
				}
			} while (connection.getResponseCode() != HttpURLConnection.HTTP_OK);
			connection.disconnect();
			return finalUrl;
		} catch (Exception e) {
			throw e;
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	private List<String> getTumblrPostImages(final String url) throws IllegalStateException, Exception {
		try {
			// create tumblr api query based on final post URL
			if (!url.matches(TUMBLR_URL_PATTERN)) {
//				throw new IllegalStateException("getTumblrPostImages KO: URL doesn't match tumblr pattern: " + url);
				Log.w(LOG_TAG, "getTumblrPostImages KO: URL doesn't match tumblr pattern: " + url);
			}
			final String tumblrReadQuery = url.replaceAll(TUMBLR_URL_PATTERN, TUMBLR_READ_API_PATTERN);

			// query tumblr api
			if (ImgDownloaderActivity.LOG_DEBUG) {
				Log.d(LOG_TAG, "getTumblrPostImages(" + url + ") calling tumblr read api with query: " + tumblrReadQuery);
			}
			final DefaultHttpClient httpClient = new DefaultHttpClient();
			final HttpGet request = new HttpGet(tumblrReadQuery);
			final HttpResponse response = httpClient.execute(request);
			// check response code
			if (response.getStatusLine().getStatusCode() != 200) {
				throw new IllegalStateException("getTumblrPostImages KO: Response error code " + response.getStatusLine().getStatusCode() + " returned for url=" + tumblrReadQuery);
			}

			// parse XML response
			final String responseText = EntityUtils.toString(response.getEntity());
			final DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			final DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			final Document doc = dBuilder.parse(new InputSource(new StringReader(responseText)));
			doc.getDocumentElement().normalize();

			final List<String> imgList = new ArrayList<String>();

			final NodeList photoset = doc.getElementsByTagName("photoset");
			if (photoset.getLength() > 0) {
				if (ImgDownloaderActivity.LOG_DEBUG) {
					Log.d(LOG_TAG, "Found photoset");
				}
				final NodeList photos = photoset.item(0).getChildNodes();
				// Go through the photoset
				for (int i = 0; i < photos.getLength(); i++) {
					final NodeList photoUrls = photos.item(i).getChildNodes();
					final String photoUrl = getHighestResImageUrl(photoUrls);
					if (TextUtils.isEmpty(photoUrl)) {
						throw new IllegalStateException("getTumblrPostImages KO: no 'posts/post/photoset/photo/photo-url' value ! xml='" + responseText + "'");
					}
					imgList.add(photoUrl);
				}
			} else {
				final NodeList photoUrls = doc.getElementsByTagName("photo-url");
				if (photoUrls.getLength() > 0) {
					if (ImgDownloaderActivity.LOG_DEBUG) {
						Log.d(LOG_TAG, "Found single photo post");
					}
					final String photoUrl = getHighestResImageUrl(photoUrls);
					if (TextUtils.isEmpty(photoUrl)) {
						throw new IllegalStateException("getTumblrPostImages KO: no 'posts/post/photo-url' value ! xml='" + responseText + "'");
					}
					imgList.add(photoUrl);
				} else {
					if (ImgDownloaderActivity.LOG_DEBUG) {
						Log.w(LOG_TAG, "No photo found in post");
					}
				}
			}

			if (ImgDownloaderActivity.LOG_DEBUG) {
				Log.d(LOG_TAG, "getTumblrPostImages returns " + imgList.size() + " images urls");
			}
			return imgList;
		} catch (Exception e) {
			Log.e(LOG_TAG, "getTumblrPostImages KO: " + e.toString(), e);
			throw e;
		}
	}

	private String getHighestResImageUrl(final NodeList photoUrls) {
		String maxWidthUrl = "";
		int maxWidth = 0;
		for (int i = 0; i < photoUrls.getLength(); i++) {
			final Node photoUrl = photoUrls.item(i);
			final String maxWidthStr = ((Element) photoUrl).getAttribute("max-width");
			final int maxWidthValue = Integer.parseInt(maxWidthStr);
			if (maxWidthValue > maxWidth) {
				maxWidthUrl = photoUrl.getTextContent();
				maxWidth = maxWidthValue;
			}
		}
		if (ImgDownloaderActivity.LOG_DEBUG && !TextUtils.isEmpty(maxWidthUrl)) {
			Log.d(LOG_TAG, "Image found with max-width=" + maxWidth + ": " + maxWidthUrl);
		}
		return maxWidthUrl;
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================
}
