/* ScummVM - Graphic Adventure Engine
 *
 * ScummVM is the legal property of its developers, whose names
 * are too numerous to list here. Please refer to the COPYRIGHT
 * file distributed with this source distribution.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

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

/** Optional alpha artwork above normal gameplay and clipped behind Split View content. */
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
		_destination.set(0, 0, getWidth(), getHeight());
		if (_splitViewActive) {
			if (_viewport.width() >= getWidth() && _viewport.height() >= getHeight()) return;
			int save = canvas.save();
			canvas.clipOutRect(_viewport);
			canvas.drawColor(Color.BLACK);
			canvas.restoreToCount(save);
		} else if (bitmap != null) {
			// SkinSurroundView is a transparent window layer above the default SurfaceView.
			// Drawing the whole PNG preserves its authored alpha: transparent pixels reveal
			// the game and non-transparent edge artwork may overlap it without changing geometry.
			canvas.drawBitmap(bitmap, null, _destination, null);
		}
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
