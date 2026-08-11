/* ScummVM - Graphic Adventure Engine */
package org.scummvm.scummvm;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Optional artwork layer clipped so it can never draw over ScummVM's live viewport. */
public final class SkinSurroundView extends View {
	private static final Uri CHANGES_URI = Uri.parse("content://com.jamesmoran.adventurepad.skins/gameplay");
	private final Rect _viewport = new Rect();
	private final Rect _destination = new Rect();
	private final ExecutorService _loader = Executors.newSingleThreadExecutor();
	private String _target = "";
	private long _loadGeneration;
	private Bitmap _bitmap;
	private boolean _splitViewActive;
	private final ContentObserver _observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
		@Override
		public void onChange(boolean selfChange, Uri uri) { loadCurrentTarget(); }
	};

	public SkinSurroundView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setClickable(false);
		setFocusable(false);
		setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		try {
			getContext().getContentResolver().registerContentObserver(CHANGES_URI, true, _observer);
		} catch (RuntimeException ignored) {
			// AdventurePad is optional.
		}
	}

	@Override
	protected void onDetachedFromWindow() {
		try { getContext().getContentResolver().unregisterContentObserver(_observer); }
		catch (RuntimeException ignored) {}
		_loadGeneration++;
		_loader.shutdownNow();
		replaceBitmap(null);
		super.onDetachedFromWindow();
	}

	public void setGameTarget(String target, boolean advancedContext) {
		String resolved = advancedContext || target == null ? "" : target.trim();
		if (_target.equals(resolved)) return;
		_target = resolved;
		loadCurrentTarget();
	}

	public void setGameViewport(int left, int top, int right, int bottom, int sourceWidth, int sourceHeight) {
		if (sourceWidth <= 0 || sourceHeight <= 0 || getWidth() <= 0 || getHeight() <= 0) {
			_viewport.set(0, 0, getWidth(), getHeight());
		} else {
			float xScale = (float)getWidth() / sourceWidth;
			float yScale = (float)getHeight() / sourceHeight;
			_viewport.set(Math.max(0, Math.round(left * xScale)), Math.max(0, Math.round(top * yScale)),
				Math.min(getWidth(), Math.round(right * xScale)), Math.min(getHeight(), Math.round(bottom * yScale)));
		}
		invalidate();
	}

	public void setSplitViewActive(boolean active) {
		if (_splitViewActive == active) return;
		_splitViewActive = active;
		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		Bitmap bitmap = _bitmap;
		if ((!_splitViewActive && bitmap == null) ||
			(_viewport.width() >= getWidth() && _viewport.height() >= getHeight())) return;
		_destination.set(0, 0, getWidth(), getHeight());
		int save = canvas.save();
		canvas.clipOutRect(_viewport);
		if (_splitViewActive)
			canvas.drawColor(Color.BLACK);
		else
			canvas.drawBitmap(bitmap, null, _destination, null);
		canvas.restoreToCount(save);
	}

	private void loadCurrentTarget() {
		final long generation = ++_loadGeneration;
		final String target = _target;
		if (target.isEmpty()) { replaceBitmap(null); return; }
		_loader.execute(() -> {
			Bitmap loaded = null;
			try {
				Uri uri = new Uri.Builder().scheme("content").authority("com.jamesmoran.adventurepad.skins")
					.appendPath("gameplay").appendPath(target).appendPath("top-surround").build();
				try (InputStream input = getContext().getContentResolver().openInputStream(uri)) {
					if (input != null) loaded = BitmapFactory.decodeStream(input);
				}
			} catch (RuntimeException | java.io.IOException ignored) {
				// Missing providers/assets are the supported black-surround fallback.
			}
			final Bitmap result = loaded;
			post(() -> {
				if (generation == _loadGeneration) replaceBitmap(result);
				else if (result != null) result.recycle();
			});
		});
	}

	private void replaceBitmap(Bitmap bitmap) {
		Bitmap previous = _bitmap;
		_bitmap = bitmap;
		if (previous != null && previous != bitmap) previous.recycle();
		invalidate();
	}
}
