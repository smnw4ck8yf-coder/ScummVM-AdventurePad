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

import android.content.res.AssetManager;
import android.graphics.PixelFormat;
import android.util.Log;
import android.os.Messenger;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Scanner;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

public abstract class ScummVM implements SurfaceHolder.Callback,
	   CompatHelpers.SystemInsets.SystemInsetsListener, Runnable {
	public static final int SHOW_ON_SCREEN_MENU = 1;
	public static final int SHOW_ON_SCREEN_INPUT_MODE = 2;

	final protected static String LOG_TAG = "ScummVM";
	final private AssetManager _asset_manager;
	final private Object _sem_surface;
	final private MyScummVMDestroyedCallback _svm_destroyed_callback;

	private EGL10 _egl;
	private EGLDisplay _egl_display = EGL10.EGL_NO_DISPLAY;
	private EGLConfig _egl_config;
	private EGLContext _egl_context = EGL10.EGL_NO_CONTEXT;
	private EGLSurface _egl_surface = EGL10.EGL_NO_SURFACE;
	private EGLSurface _egl_mirror_surface = EGL10.EGL_NO_SURFACE;
	private final Object _mirror_surface_lock = new Object();
	private MirrorSurfaceRequest _pending_mirror_request;
	private long _mirror_surface_generation;
	private int _mirror_surface_width;
	private int _mirror_surface_height;
	private Messenger _mirror_status_recipient;
	private int _mirror_diagnostic_frames_remaining;
	private int _mirror_slow_swap_logs_remaining;
	private int _mirror_render_log_count;
	private volatile boolean _mirror_disabled_for_session;
	private final Object _mirror_crop_lock = new Object();
	private MirrorCropRequest _pending_mirror_crop;
	private Messenger _geometry_recipient;
	private Messenger _crop_ack_recipient;
	private long _crop_ack_generation;
	private int _mirror_source_width;
	private int _mirror_source_height;
	private int _mirror_source_capability;
	private long _mirror_geometry_generation = 1L;
	private UpperPresentationRequest _pending_upper_presentation;
	private Messenger _upper_presentation_ack_recipient;
	private long _upper_presentation_ack_generation;
	private MirrorCropRequest _crop_ack_request;
	private long _active_crop_generation;
	private long _active_crop_geometry_generation;
	private float _active_crop_left;
	private float _active_crop_top;
	private float _active_crop_right = 1.0f;
	private float _active_crop_bottom = 1.0f;
	private boolean _split_view_active;
	private long _active_pointer_sequence;
	private int _active_pointer_id = -1;
	private long _last_pointer_sequence;
	private int _mirror_source_orientation;

	private SurfaceHolder _surface_holder;
	private int bitsPerPixel;

	private boolean _assetsUpdated;
	private String[] _args;

	private native void create(AssetManager asset_manager,
	                           EGL10 egl,
	                           EGLDisplay egl_display,
	                           boolean assetsUpdated);
	private native void destroy();
	private native void setSurface(int width, int height, int bpp);
	@SuppressWarnings("ConfusingMainMethod")
	private native int main(String[] args);

	// pause the engine and all native threads
	final public native void setPause(boolean pause);
	// Feed an event to ScummVM.  Safe to call from other threads.
	final public native void pushEvent(int type, int arg1, int arg2, int arg3,
										int arg4, int arg5, int arg6);
	// Update the 3D touch controls
	final public native void setupTouchMode(int oldValue, int newValue);
	final public native void updateTouch(int action, int ptr, int x, int y);

	final public native void syncVirtkeyboardState(boolean newState);

	public static native void setDefaultAudioValues(int sampleRate, int framesPerBurst);
	public static native void notifyAudioDisconnect();

	final public native String getNativeVersionInfo();

	// CompatHelpers.WindowInsets.SystemInsetsListener interface
	@Override
	final public native void systemInsetsUpdated(int[] gestureInsets, int[] systemInsets, int[] cutoutInsets);

	// Callbacks from C++ peer instance
	/** @noinspection unused */ @Keep
	abstract protected void getDPI(float[] values);
	/** @noinspection unused */ @Keep
	abstract protected void displayMessageOnOSD(String msg);
	/** @noinspection unused */ @Keep
	abstract protected void openUrl(String url);
	/** @noinspection unused */ @Keep
	abstract protected boolean hasTextInClipboard();
	/** @noinspection unused */ @Keep
	abstract protected String getTextFromClipboard();
	/** @noinspection unused */ @Keep
	abstract protected boolean setTextInClipboard(String text);
	/** @noinspection unused */ @Keep
	abstract protected boolean isConnectionLimited();
	/** @noinspection unused */ @Keep
	abstract protected void setWindowCaption(String caption);
	/** @noinspection unused */ @Keep
	abstract protected void showVirtualKeyboard(boolean enable);
	/** @noinspection unused */ @Keep
	abstract protected void showOnScreenControls(int enableMask);
	/** @noinspection unused */ @Keep
	abstract protected void setTouchMode(int touchMode);
	/** @noinspection unused */ @Keep
	abstract protected int getTouchMode();
	/** @noinspection unused */ @Keep
	abstract protected void setOrientation(int orientation);
	/** @noinspection unused */ @Keep
	abstract protected String getScummVMBasePath();
	/** @noinspection unused */ @Keep
	abstract protected String getScummVMConfigPath();
	/** @noinspection unused */ @Keep
	abstract protected String getScummVMLogPath();
	/** @noinspection unused */ @Keep
	abstract protected void setCurrentGame(String target);
	/** @noinspection unused */ @Keep
	abstract protected void notifyHTTPService(int localPort, boolean minimal);
	/** @noinspection unused */ @Keep
	abstract protected String[] getSysArchives();
	/** @noinspection unused */ @Keep
	abstract protected String[] getAllStorageLocations();
	/** @noinspection unused */ @Keep
	abstract protected SAFFSTree getNewSAFTree(boolean write, String initialURI, String prompt);
	/** @noinspection unused */ @Keep
	abstract protected SAFFSTree[] getSAFTrees();
	/** @noinspection unused */ @Keep
	abstract protected SAFFSTree findSAFTree(String name);
	/** @noinspection unused */ @Keep
	abstract protected int exportBackup(String prompt);
	/** @noinspection unused */ @Keep
	abstract protected int importBackup(String prompt, String path);

	@SuppressWarnings("ClassEscapesDefinedScope")
	public ScummVM(AssetManager asset_manager, SurfaceHolder holder, final MyScummVMDestroyedCallback scummVMDestroyedCallback) {
		_asset_manager = asset_manager;
		_sem_surface = new Object();
		_svm_destroyed_callback = scummVMDestroyedCallback;
		holder.addCallback(this);
	}

	final public String getInstallingScummVMVersionInfo() {
		return getNativeVersionInfo();
	}

	final void queueMirrorSurfaceAttach(Surface surface, long generation, int width, int height,
			Messenger statusRecipient) {
		synchronized (_mirror_surface_lock) {
			releasePendingMirrorSurface();
			_pending_mirror_request = MirrorSurfaceRequest.attach(
				surface, generation, width, height, statusRecipient);
		}
		requestMirrorRefresh("ATTACH generation=" + generation);
	}

	final void queueMirrorSurfaceDetach(long generation, Messenger statusRecipient) {
		synchronized (_mirror_surface_lock) {
			releasePendingMirrorSurface();
			_pending_mirror_request = MirrorSurfaceRequest.detach(generation, statusRecipient);
		}
		queueUpperPresentationFallback();
	}

	final void queueMirrorGeometryQuery(Messenger recipient) {
		synchronized (_mirror_crop_lock) {
			_geometry_recipient = recipient;
			if (_mirror_source_width > 0 && _mirror_source_height > 0) {
				MirrorSurfaceProtocol.sendGeometry(recipient, _mirror_source_width,
					_mirror_source_height, _mirror_source_capability, _mirror_geometry_generation,
					RelativeInputService.getCurrentGameTarget(), _mirror_source_orientation);
			}
		}
	}

	final void queueMirrorCrop(float left, float top, float right, float bottom,
			long cropGeneration, long expectedGeometryGeneration, Messenger recipient) {
		synchronized (_mirror_crop_lock) {
			_active_crop_generation = 0;
			resetAbsoluteSourcePointerLocked();
			_pending_mirror_crop = new MirrorCropRequest(left, top, right, bottom,
				cropGeneration, expectedGeometryGeneration, recipient);
		}
		requestMirrorRefresh("CROP generation=" + cropGeneration);
	}

	final void queueMirrorCropFallback() {
		synchronized (_mirror_crop_lock) {
			_active_crop_generation = 0;
			resetAbsoluteSourcePointerLocked();
			_pending_mirror_crop = MirrorCropRequest.fallback();
		}
		requestMirrorRefresh("CROP fallback");
	}

	final void queueUpperPresentation(int mode, float left, float top, float right, float bottom,
			long modeGeneration, long expectedGeometryGeneration, Messenger recipient) {
		synchronized (_mirror_crop_lock) {
			_split_view_active = false;
			resetAbsoluteSourcePointerLocked();
			_pending_upper_presentation = new UpperPresentationRequest(mode, left, top, right,
				bottom, modeGeneration, expectedGeometryGeneration, recipient);
		}
		requestMirrorRefresh("MODE generation=" + modeGeneration + " mode=" + mode);
	}

	final void queueUpperPresentationFallback() {
		synchronized (_mirror_crop_lock) {
			_pending_upper_presentation = UpperPresentationRequest.fullFrame();
			_split_view_active = false;
			resetAbsoluteSourcePointerLocked();
		}
		requestMirrorRefresh("MODE fallback");
	}

	private void requestMirrorRefresh(String reason) {
		if (_mirror_render_log_count < 64) {
			++_mirror_render_log_count;
			Log.i("AdventurePadRender", "event=" + _mirror_render_log_count +
				"/64 protocol queued " + reason + "; pushing JE_MIRROR_REFRESH");
		}
		pushEvent(ScummVMEvents.JE_MIRROR_REFRESH, 0, 0, 0, 0, 0, 0);
	}

	final boolean isMirrorOutputEnabled() {
		return MirrorSurfaceProtocol.ENABLED && !_mirror_disabled_for_session;
	}

	// SurfaceHolder callback
	final public void surfaceCreated(@NonNull SurfaceHolder holder) {
		Log.d(LOG_TAG, "surfaceCreated");

		// no need to do anything, surfaceChanged() will be called in any case
	}

	// SurfaceHolder callback
	final public void surfaceChanged(@NonNull SurfaceHolder holder, int format,
										int width, int height) {

		PixelFormat pixelFormat = new PixelFormat();
		PixelFormat.getPixelFormatInfo(format, pixelFormat);
		bitsPerPixel = pixelFormat.bitsPerPixel;

		Log.d(LOG_TAG, String.format(Locale.ROOT, "surfaceChanged: %dx%d (%d: %dbpp)",
										width, height, format, bitsPerPixel));

		// store values for the native code
		// make sure to do it before notifying the lock
		// as it leads to a race condition otherwise
		setSurface(width, height, bitsPerPixel);

		synchronized(_sem_surface) {
			_surface_holder = holder;
			_sem_surface.notifyAll();
		}
	}

	// SurfaceHolder callback
	final public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
		Log.d(LOG_TAG, "surfaceDestroyed");

		synchronized(_sem_surface) {
			_surface_holder = null;
			_sem_surface.notifyAll();
		}

		// Don't call when EGL is not init:
		// this avoids polluting the static variables with obsolete values
		if (_egl != null) {
			// clear values for the native code
			setSurface(0, 0, 0);
		}
	}

	final public void setAssetsUpdated(boolean assetsUpdated) {
		_assetsUpdated = assetsUpdated;
	}

	final public void setArgs(String[] args) {
		_args = args;
	}

	final public void run() {
		try {
			// wait for the surfaceChanged callback
			synchronized(_sem_surface) {
				while (_surface_holder == null)
					_sem_surface.wait();
			}

			initEGL();
		} catch (Exception e) {
			deinitEGL();

			throw new RuntimeException("Error preparing the ScummVM thread", e);
		}

		create(_asset_manager, _egl, _egl_display,
				_assetsUpdated);

		int res = main(_args);

		destroy();

		deinitEGL();

		// Don't exit force-ably here!
		if (_svm_destroyed_callback != null) {
			_svm_destroyed_callback.handle(res);
		}
	}

	private void initEGL() throws Exception {
		_egl = (EGL10)EGLContext.getEGL();
		_egl_display = _egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

		int[] version = new int[2];
		_egl.eglInitialize(_egl_display, version);
		Log.d(LOG_TAG, String.format(Locale.ROOT, "EGL version %d.%d initialized", version[0], version[1]));

		int[] num_config = new int[1];
		_egl.eglGetConfigs(_egl_display, null, 0, num_config);

		final int numConfigs = num_config[0];

		if (numConfigs <= 0)
			throw new IllegalArgumentException("No EGL configs");

		EGLConfig[] configs = new EGLConfig[numConfigs];
		_egl.eglGetConfigs(_egl_display, configs, numConfigs, num_config);

		// Android's eglChooseConfig is busted in several versions and
		// devices so we have to filter/rank the configs ourselves.
		_egl_config = chooseEglConfig(configs, version);

		int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
		int[] attrib_list = { EGL_CONTEXT_CLIENT_VERSION, 2,
		                      EGL10.EGL_NONE };
		_egl_context = _egl.eglCreateContext(_egl_display, _egl_config,
		                                     EGL10.EGL_NO_CONTEXT, attrib_list);

		if (_egl_context == EGL10.EGL_NO_CONTEXT)
			throw new Exception(String.format(Locale.ROOT, "Failed to create context: 0x%x",
												_egl.eglGetError()));
	}

	/** @noinspection unused
	 * Callback from C++ peer instance
	 */
	@Keep
	final protected EGLSurface initSurface() throws Exception {
		_egl_surface = _egl.eglCreateWindowSurface(_egl_display, _egl_config,
													_surface_holder, null);

		if (_egl_surface == EGL10.EGL_NO_SURFACE)
			throw new Exception(String.format(Locale.ROOT,
				"eglCreateWindowSurface failed: 0x%x", _egl.eglGetError()));

		_egl.eglMakeCurrent(_egl_display, _egl_surface, _egl_surface,
							_egl_context);

		GL10 gl = (GL10)_egl_context.getGL();

		Log.i(LOG_TAG, String.format(Locale.ROOT, "Using EGL %s (%s); GL %s/%s (%s)",
						_egl.eglQueryString(_egl_display, EGL10.EGL_VERSION),
						_egl.eglQueryString(_egl_display, EGL10.EGL_VENDOR),
						gl.glGetString(GL10.GL_VERSION),
						gl.glGetString(GL10.GL_RENDERER),
						gl.glGetString(GL10.GL_VENDOR)));

		return _egl_surface;
	}

	/** Consume pending mirror lifecycle work on ScummVM's GL thread. */
	@SuppressWarnings("unused") @Keep
	final protected long[] updateMirrorSurface() {
		MirrorSurfaceRequest request;
		synchronized (_mirror_surface_lock) {
			request = _pending_mirror_request;
			_pending_mirror_request = null;
		}
		if (request == null)
			return null;

		if (!request.attach) {
			// A detach invalidates the entire mirror channel. This also handles an
			// attach/detach pair that was queued before the render thread observed it.
			destroyMirrorSurface();
			MirrorSurfaceProtocol.sendStatus(request.statusRecipient,
				MirrorSurfaceProtocol.STATUS_DETACHED, request.generation, "Mirror surface detached");
			return new long[] { 0, request.generation, 0, 0 };
		}
		if (!isMirrorOutputEnabled()) {
			MirrorSurfaceProtocol.sendStatus(request.statusRecipient,
				MirrorSurfaceProtocol.STATUS_FAILED, request.generation,
				"Mirror rendering is disabled for this session");
			if (request.surface != null)
				request.surface.release();
			return new long[] { -1, request.generation, 0, 0 };
		}

		destroyMirrorSurface();
		try {
			if (request.surface == null || !request.surface.isValid())
				throw new IllegalArgumentException("Mirror surface became invalid before EGL creation");
			_egl_mirror_surface = _egl.eglCreateWindowSurface(
				_egl_display, _egl_config, request.surface, null);
			int createError = _egl.eglGetError();
			if (_egl_mirror_surface == EGL10.EGL_NO_SURFACE) {
				throw new IllegalStateException(String.format(Locale.ROOT,
					"eglCreateWindowSurface failed: 0x%x", createError));
			}
			_mirror_surface_generation = request.generation;
			_mirror_surface_width = request.width;
			_mirror_surface_height = request.height;
			_mirror_status_recipient = request.statusRecipient;
			_mirror_diagnostic_frames_remaining = 8;
			_mirror_slow_swap_logs_remaining = 4;
			Log.i("AdventurePadMirror", "Created mirror EGLSurface generation=" +
				request.generation + " primary=" + _egl_surface + " mirror=" +
				_egl_mirror_surface + " eglError=0x" + Integer.toHexString(createError) +
				" current=" + currentEglSurfaces());
			MirrorSurfaceProtocol.sendStatus(_mirror_status_recipient,
				MirrorSurfaceProtocol.STATUS_ATTACHED, _mirror_surface_generation,
				"Secondary EGLSurface attached");
			return new long[] { 1, request.generation, request.width, request.height };
		} catch (RuntimeException exception) {
			_egl_mirror_surface = EGL10.EGL_NO_SURFACE;
			MirrorSurfaceProtocol.sendStatus(request.statusRecipient,
				MirrorSurfaceProtocol.STATUS_FAILED, request.generation, exception.getMessage());
			Log.e(LOG_TAG, "Mirror EGLSurface creation failed", exception);
			return new long[] { -1, request.generation, 0, 0 };
		} finally {
			if (request.surface != null)
				request.surface.release();
		}
	}

	@SuppressWarnings("unused") @Keep
	final protected long reportMirrorSourceGeometry(int width, int height, int capability, int orientation) {
		synchronized (_mirror_crop_lock) {
			if (width <= 0 || height <= 0 || capability <= 0) {
				width = 0;
				height = 0;
				capability = 0;
			}
			if (_mirror_source_width != width || _mirror_source_height != height ||
				_mirror_source_capability != capability || _mirror_source_orientation != orientation) {
				_mirror_source_width = width;
				_mirror_source_height = height;
				_mirror_source_capability = capability;
				_mirror_source_orientation = orientation;
				++_mirror_geometry_generation;
				_pending_mirror_crop = MirrorCropRequest.fallback();
				_pending_upper_presentation = UpperPresentationRequest.fullFrame();
				resetAbsoluteSourcePointerLocked();
				_split_view_active = false;
			}
			MirrorSurfaceProtocol.sendGeometry(_geometry_recipient, width, height, capability,
					_mirror_geometry_generation, RelativeInputService.getCurrentGameTarget(), orientation);
			return _mirror_geometry_generation;
		}
	}

	@SuppressWarnings("unused") @Keep
	final protected double[] updateMirrorCrop() {
		synchronized (_mirror_crop_lock) {
			MirrorCropRequest request = _pending_mirror_crop;
			_pending_mirror_crop = null;
			if (request == null)
				return null;
			if (request.cropGeneration > 0 &&
				request.expectedGeometryGeneration != _mirror_geometry_generation) {
				MirrorSurfaceProtocol.sendCropAck(request.recipient,
					MirrorSurfaceProtocol.CROP_INCOMPATIBLE_GEOMETRY, request.cropGeneration,
					_mirror_geometry_generation, "Source geometry generation changed");
				return MirrorCropRequest.fallback().toArray(_mirror_geometry_generation);
			}
			if (request.cropGeneration > 0 && _mirror_source_capability == 0) {
				MirrorSurfaceProtocol.sendCropAck(request.recipient,
					MirrorSurfaceProtocol.CROP_UNSUPPORTED_SOURCE, request.cropGeneration,
					_mirror_geometry_generation, "Current renderer has no crop-capable source");
				return MirrorCropRequest.fallback().toArray(_mirror_geometry_generation);
			}
			_crop_ack_recipient = request.recipient;
			_crop_ack_generation = request.cropGeneration;
			_crop_ack_request = request;
			return request.toArray(_mirror_geometry_generation);
		}
	}

	@SuppressWarnings("unused") @Keep
	final protected void reportMirrorCropAck(int result, long cropGeneration,
			long geometryGeneration, String diagnostic) {
		synchronized (_mirror_crop_lock) {
			if (cropGeneration <= 0 || cropGeneration != _crop_ack_generation)
				return;
			MirrorSurfaceProtocol.sendCropAck(_crop_ack_recipient, result, cropGeneration,
				geometryGeneration, diagnostic);
			if (result == MirrorSurfaceProtocol.CROP_APPLIED && _crop_ack_request != null) {
				_active_crop_generation = cropGeneration;
				_active_crop_geometry_generation = geometryGeneration;
				_active_crop_left = _crop_ack_request.left;
				_active_crop_top = _crop_ack_request.top;
				_active_crop_right = _crop_ack_request.right;
				_active_crop_bottom = _crop_ack_request.bottom;
			} else {
				_active_crop_generation = 0;
				resetAbsoluteSourcePointerLocked();
			}
			_crop_ack_recipient = null;
			_crop_ack_generation = 0;
			_crop_ack_request = null;
		}
	}

	@SuppressWarnings("unused") @Keep
	final protected double[] updateUpperPresentation() {
		synchronized (_mirror_crop_lock) {
			UpperPresentationRequest request = _pending_upper_presentation;
			_pending_upper_presentation = null;
			if (request == null)
				return null;
			if (request.modeGeneration > 0 &&
				request.expectedGeometryGeneration != _mirror_geometry_generation) {
				MirrorSurfaceProtocol.sendDisplayModeAck(request.recipient,
					MirrorSurfaceProtocol.MODE_STALE_GENERATION, request.modeGeneration,
					_mirror_geometry_generation, "Source geometry generation changed");
				return UpperPresentationRequest.fullFrame().toArray(_mirror_geometry_generation);
			}
			if (request.modeGeneration > 0 && _mirror_source_capability == 0) {
				MirrorSurfaceProtocol.sendDisplayModeAck(request.recipient,
					MirrorSurfaceProtocol.MODE_UNSUPPORTED_RENDERER, request.modeGeneration,
					_mirror_geometry_generation, "Current renderer cannot expand presentation");
				return UpperPresentationRequest.fullFrame().toArray(_mirror_geometry_generation);
			}
			_upper_presentation_ack_recipient = request.recipient;
			_upper_presentation_ack_generation = request.modeGeneration;
			return request.toArray(_mirror_geometry_generation);
		}
	}

	@SuppressWarnings("unused") @Keep
	final protected void reportUpperPresentationAck(int result, long modeGeneration,
			long geometryGeneration, String diagnostic) {
		synchronized (_mirror_crop_lock) {
			if (modeGeneration <= 0 || modeGeneration != _upper_presentation_ack_generation)
				return;
			MirrorSurfaceProtocol.sendDisplayModeAck(_upper_presentation_ack_recipient, result,
				modeGeneration, geometryGeneration, diagnostic);
			_split_view_active = result == MirrorSurfaceProtocol.MODE_EXPANDED_APPLIED;
			if (!_split_view_active)
				resetAbsoluteSourcePointerLocked();
			_upper_presentation_ack_recipient = null;
			_upper_presentation_ack_generation = 0;
		}
	}

	final boolean pushAbsoluteSourcePointer(int x, int y, int action, int pointerId,
			long sequenceId, long cropGeneration, long geometryGeneration) {
		synchronized (_mirror_crop_lock) {
			if (!_split_view_active || !isMirrorOutputEnabled() || sequenceId <= 0 || pointerId < 0 ||
				cropGeneration != _active_crop_generation ||
				geometryGeneration != _active_crop_geometry_generation ||
				geometryGeneration != _mirror_geometry_generation || x < 0 || y < 0 ||
				x >= _mirror_source_width || y >= _mirror_source_height ||
				!isInsideActiveCrop(x, y))
				return false;
			switch (action) {
			case 1: // DOWN moves the canonical cursor but does not press a mouse button.
				if (_active_pointer_sequence != 0 || sequenceId <= _last_pointer_sequence)
					return false;
				_active_pointer_sequence = sequenceId;
				_active_pointer_id = pointerId;
				break;
			case 0: // MOVE
			case 2: // UP performs the click in native virtual coordinates.
				if (sequenceId != _active_pointer_sequence || pointerId != _active_pointer_id)
					return false;
				break;
			case 3: // CANCEL
				if (sequenceId != _active_pointer_sequence || pointerId != _active_pointer_id)
					return false;
				break;
			default:
				return false;
			}
			pushEvent(ScummVMEvents.JE_ABSOLUTE_SOURCE_POINTER, action, x, y, 0, 0, 0);
			if (action == 2 || action == 3) {
				_last_pointer_sequence = sequenceId;
				_active_pointer_sequence = 0;
				_active_pointer_id = -1;
			}
			return true;
		}
	}

	private boolean isInsideActiveCrop(int x, int y) {
		int left = Math.max(0, Math.min(_mirror_source_width - 1,
			(int)Math.floor(_active_crop_left * _mirror_source_width)));
		int top = Math.max(0, Math.min(_mirror_source_height - 1,
			Math.round(_active_crop_top * _mirror_source_height)));
		int right = Math.max(left + 1, Math.min(_mirror_source_width,
			(int)Math.ceil(_active_crop_right * _mirror_source_width)));
		int bottom = Math.max(top + 1, Math.min(_mirror_source_height,
			(int)Math.ceil(_active_crop_bottom * _mirror_source_height)));
		return x >= left && x < right && y >= top && y < bottom;
	}

	private void resetAbsoluteSourcePointerLocked() {
		_active_pointer_sequence = 0;
		_active_pointer_id = -1;
	}

	@SuppressWarnings("unused") @Keep
	final protected boolean makeMirrorSurfaceCurrent() {
		String currentBefore = _mirror_diagnostic_frames_remaining > 0 ? currentEglSurfaces() : "not-sampled";
		boolean result = _egl_mirror_surface != EGL10.EGL_NO_SURFACE &&
			_egl.eglMakeCurrent(_egl_display, _egl_mirror_surface, _egl_mirror_surface, _egl_context);
		int error = _egl.eglGetError();
		if (_mirror_diagnostic_frames_remaining > 0 || !result) {
			Log.i("AdventurePadMirror", "eglMakeCurrent(mirror) result=" + result +
				" eglError=0x" + Integer.toHexString(error) + " primary=" + _egl_surface +
				" mirror=" + _egl_mirror_surface + " before=" + currentBefore +
				" after=" + currentEglSurfaces());
		}
		return result && error == EGL10.EGL_SUCCESS;
	}

	@SuppressWarnings("unused") @Keep
	final protected boolean makePrimarySurfaceCurrent() {
		String currentBefore = _mirror_diagnostic_frames_remaining > 0 ? currentEglSurfaces() : "not-sampled";
		boolean result = _egl_surface != EGL10.EGL_NO_SURFACE &&
			_egl.eglMakeCurrent(_egl_display, _egl_surface, _egl_surface, _egl_context);
		int error = _egl.eglGetError();
		if (_mirror_diagnostic_frames_remaining > 0 || !result) {
			Log.i("AdventurePadMirror", "eglMakeCurrent(primary) result=" + result +
				" eglError=0x" + Integer.toHexString(error) + " primary=" + _egl_surface +
				" mirror=" + _egl_mirror_surface + " before=" + currentBefore +
				" after=" + currentEglSurfaces());
		}
		if (_mirror_diagnostic_frames_remaining > 0)
			--_mirror_diagnostic_frames_remaining;
		return result && error == EGL10.EGL_SUCCESS;
	}

	@SuppressWarnings("unused") @Keep
	final protected boolean swapMirrorSurface() {
		if (_egl_mirror_surface == EGL10.EGL_NO_SURFACE)
			return false;
		long startedNanos = System.nanoTime();
		boolean swapped = _egl.eglSwapBuffers(_egl_display, _egl_mirror_surface);
		int error = _egl.eglGetError();
		long durationMicros = (System.nanoTime() - startedNanos) / 1000L;
		boolean logSlowSwap = durationMicros > 20000L && _mirror_slow_swap_logs_remaining > 0;
		if (_mirror_diagnostic_frames_remaining > 0 || logSlowSwap || !swapped) {
			Log.i("AdventurePadMirror", "Mirror swap generation=" + _mirror_surface_generation +
				" durationUs=" + durationMicros + " success=" + swapped + " eglError=0x" +
				Integer.toHexString(error) + " current=" + currentEglSurfaces());
			if (logSlowSwap)
				--_mirror_slow_swap_logs_remaining;
		}
		return swapped && error == EGL10.EGL_SUCCESS;
	}

	@SuppressWarnings("unused") @Keep
	final protected void reportMirrorStatus(int status, long generation, String diagnostic) {
		if (generation == _mirror_surface_generation)
			MirrorSurfaceProtocol.sendStatus(_mirror_status_recipient, status, generation, diagnostic);
	}

	@SuppressWarnings("unused") @Keep
	final protected void reportMirrorCursor(int x, int y, boolean visible, long geometryGeneration) {
		MirrorSurfaceProtocol.sendCursorPosition(_mirror_status_recipient, x, y, visible,
			geometryGeneration);
	}

	@SuppressWarnings("unused") @Keep
	final protected boolean failMirrorSurface(long generation, String diagnostic) {
		if (generation != _mirror_surface_generation)
			return false;
		boolean primaryRestored = makePrimarySurfaceCurrent();
		if (!primaryRestored) {
			_mirror_disabled_for_session = true;
			Log.e("AdventurePadMirror", "Primary EGLSurface restoration failed; mirror disabled for session");
		}
		Messenger recipient = _mirror_status_recipient;
		destroyMirrorSurface();
		MirrorSurfaceProtocol.sendStatus(recipient, MirrorSurfaceProtocol.STATUS_FAILED,
			generation, diagnostic);
		return primaryRestored;
	}

	private String currentEglSurfaces() {
		if (_egl == null)
			return "egl=null";
		return "draw=" + _egl.eglGetCurrentSurface(EGL10.EGL_DRAW) +
			" read=" + _egl.eglGetCurrentSurface(EGL10.EGL_READ) +
			" context=" + _egl.eglGetCurrentContext();
	}

	/** @noinspection unused
	 * Callback from C++ peer instance
	 */
	@Keep
	final protected void deinitSurface() {
		if (_egl_display != EGL10.EGL_NO_DISPLAY) {
			_egl.eglMakeCurrent(_egl_display, EGL10.EGL_NO_SURFACE,
								EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);

			if (_egl_surface != EGL10.EGL_NO_SURFACE)
				_egl.eglDestroySurface(_egl_display, _egl_surface);
		}

		_egl_surface = EGL10.EGL_NO_SURFACE;
	}

	/** @noinspection unused
	 * Callback from C++ peer instance
	 */
	@Keep
	final protected int eglVersion() {
		String version = _egl.eglQueryString(_egl_display, EGL10.EGL_VERSION);
		if (version == null) {
			// 1.0
			return 0x00010000;
		}

		Scanner versionScan = new Scanner(version).useLocale(Locale.ROOT).useDelimiter("[ .]");
		int versionInt = versionScan.nextInt() << 16;
		versionInt |= versionScan.nextInt() & 0xffff;
		return versionInt;
	}

	private void deinitEGL() {
		destroyMirrorSurface();
		synchronized (_mirror_surface_lock) {
			releasePendingMirrorSurface();
			_pending_mirror_request = null;
		}
		if (_egl_display != EGL10.EGL_NO_DISPLAY) {
			_egl.eglMakeCurrent(_egl_display, EGL10.EGL_NO_SURFACE,
								EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);

			if (_egl_surface != EGL10.EGL_NO_SURFACE)
				_egl.eglDestroySurface(_egl_display, _egl_surface);

			if (_egl_context != EGL10.EGL_NO_CONTEXT)
				_egl.eglDestroyContext(_egl_display, _egl_context);

			_egl.eglTerminate(_egl_display);
		}

		_egl_surface = EGL10.EGL_NO_SURFACE;
		_egl_context = EGL10.EGL_NO_CONTEXT;
		_egl_config = null;
		_egl_display = EGL10.EGL_NO_DISPLAY;
		_egl = null;
	}

	private void destroyMirrorSurface() {
		if (_egl != null && _egl_display != EGL10.EGL_NO_DISPLAY &&
			_egl_mirror_surface != EGL10.EGL_NO_SURFACE) {
			EGLSurface destroyedSurface = _egl_mirror_surface;
			String currentBeforeDestroy = currentEglSurfaces();
			boolean destroyed = _egl.eglDestroySurface(_egl_display, destroyedSurface);
			int error = _egl.eglGetError();
			Log.i("AdventurePadMirror", "eglDestroySurface mirror=" + destroyedSurface +
				" result=" + destroyed + " eglError=0x" + Integer.toHexString(error) +
				" currentBefore=" + currentBeforeDestroy + " currentAfter=" + currentEglSurfaces());
		}
		_egl_mirror_surface = EGL10.EGL_NO_SURFACE;
		_mirror_surface_generation = 0;
		_mirror_surface_width = 0;
		_mirror_surface_height = 0;
		_mirror_status_recipient = null;
	}

	private void releasePendingMirrorSurface() {
		if (_pending_mirror_request != null && _pending_mirror_request.surface != null)
			_pending_mirror_request.surface.release();
	}

	private static final class MirrorSurfaceRequest {
		final boolean attach;
		final Surface surface;
		final long generation;
		final int width;
		final int height;
		final Messenger statusRecipient;

		private MirrorSurfaceRequest(boolean attach, Surface surface, long generation,
				int width, int height, Messenger statusRecipient) {
			this.attach = attach;
			this.surface = surface;
			this.generation = generation;
			this.width = width;
			this.height = height;
			this.statusRecipient = statusRecipient;
		}

		static MirrorSurfaceRequest attach(Surface surface, long generation, int width, int height,
				Messenger statusRecipient) {
			return new MirrorSurfaceRequest(true, surface, generation, width, height, statusRecipient);
		}

		static MirrorSurfaceRequest detach(long generation, Messenger statusRecipient) {
			return new MirrorSurfaceRequest(false, null, generation, 0, 0, statusRecipient);
		}
	}

	private static final class MirrorCropRequest {
		final float left;
		final float top;
		final float right;
		final float bottom;
		final long cropGeneration;
		final long expectedGeometryGeneration;
		final Messenger recipient;

		MirrorCropRequest(float left, float top, float right, float bottom,
				long cropGeneration, long expectedGeometryGeneration, Messenger recipient) {
			this.left = left;
			this.top = top;
			this.right = right;
			this.bottom = bottom;
			this.cropGeneration = cropGeneration;
			this.expectedGeometryGeneration = expectedGeometryGeneration;
			this.recipient = recipient;
		}

		static MirrorCropRequest fallback() {
			return new MirrorCropRequest(0.0f, 0.0f, 1.0f, 1.0f, 0, 0, null);
		}

		double[] toArray(long geometryGeneration) {
			return new double[] { cropGeneration, geometryGeneration, left, top, right, bottom };
		}
	}

	private static final class UpperPresentationRequest {
		final int mode;
		final float left;
		final float top;
		final float right;
		final float bottom;
		final long modeGeneration;
		final long expectedGeometryGeneration;
		final Messenger recipient;

		UpperPresentationRequest(int mode, float left, float top, float right, float bottom,
				long modeGeneration, long expectedGeometryGeneration, Messenger recipient) {
			this.mode = mode;
			this.left = left;
			this.top = top;
			this.right = right;
			this.bottom = bottom;
			this.modeGeneration = modeGeneration;
			this.expectedGeometryGeneration = expectedGeometryGeneration;
			this.recipient = recipient;
		}

		static UpperPresentationRequest fullFrame() {
			return new UpperPresentationRequest(0, 0.0f, 0.0f, 1.0f, 1.0f, 0, 0, null);
		}

		double[] toArray(long geometryGeneration) {
			return new double[] { mode, modeGeneration, geometryGeneration, left, top, right, bottom };
		}
	}

	private static final int[] s_eglAttribs = {
		EGL10.EGL_CONFIG_ID,
		EGL10.EGL_BUFFER_SIZE,
		EGL10.EGL_RED_SIZE,
		EGL10.EGL_GREEN_SIZE,
		EGL10.EGL_BLUE_SIZE,
		EGL10.EGL_ALPHA_SIZE,
		EGL10.EGL_CONFIG_CAVEAT,
		EGL10.EGL_DEPTH_SIZE,
		EGL10.EGL_LEVEL,
		EGL10.EGL_MAX_PBUFFER_WIDTH,
		EGL10.EGL_MAX_PBUFFER_HEIGHT,
		EGL10.EGL_MAX_PBUFFER_PIXELS,
		EGL10.EGL_NATIVE_RENDERABLE,
		EGL10.EGL_NATIVE_VISUAL_ID,
		EGL10.EGL_NATIVE_VISUAL_TYPE,
		EGL10.EGL_SAMPLE_BUFFERS,
		EGL10.EGL_SAMPLES,
		EGL10.EGL_STENCIL_SIZE,
		EGL10.EGL_SURFACE_TYPE,
		EGL10.EGL_TRANSPARENT_TYPE,
		EGL10.EGL_TRANSPARENT_RED_VALUE,
		EGL10.EGL_TRANSPARENT_GREEN_VALUE,
		EGL10.EGL_TRANSPARENT_BLUE_VALUE,
		EGL10.EGL_RENDERABLE_TYPE
	};
	final private static int EGL_OPENGL_ES_BIT = 1;
	final private static int EGL_OPENGL_ES2_BIT = 4;

	final private class EglAttribs  {

		LinkedHashMap<Integer, Integer> _lhm;

		public EglAttribs(EGLConfig config) {
			_lhm = new LinkedHashMap<>(s_eglAttribs.length);

			int[] value = new int[1];

			// prevent throwing IllegalArgumentException
			if (_egl_display == null || config == null) {
				return;
			}

			for (int i : s_eglAttribs) {
				_egl.eglGetConfigAttrib(_egl_display, config, i, value);

				_lhm.put(i, value[0]);
			}
		}

		private int weightBits(int attr, int size) {
			final int value = get(attr);

			int score = 0;

			if (value == size || (size > 0 && value > size))
				score += 10;

			// penalize for wasted bits
			if (value > size)
				score -= value - size;

			return score;
		}

		public int weight() {
			int score = 10000;

			if (get(EGL10.EGL_CONFIG_CAVEAT) != EGL10.EGL_NONE)
				score -= 1000;

			// If there is a config with EGL_OPENGL_ES2_BIT it must be favored
			// This attribute can only be checked with EGL 1.3 but it may be present on older versions
			if ((get(EGL10.EGL_RENDERABLE_TYPE) & EGL_OPENGL_ES2_BIT) > 0)
				score += 5000;

			// less MSAA is better
			score -= get(EGL10.EGL_SAMPLES) * 100;

			// Must be at least 565, but then smaller is better
			score += weightBits(EGL10.EGL_RED_SIZE, 5);
			score += weightBits(EGL10.EGL_GREEN_SIZE, 6);
			score += weightBits(EGL10.EGL_BLUE_SIZE, 5);
			score += weightBits(EGL10.EGL_ALPHA_SIZE, 0);
			// Prefer 24 bits depth
			score += weightBits(EGL10.EGL_DEPTH_SIZE, 24);
			score += weightBits(EGL10.EGL_STENCIL_SIZE, 8);

			return score;
		}

		@NonNull
		public String toString() {
			String s;

			if (get(EGL10.EGL_ALPHA_SIZE) > 0)
				s = String.format(Locale.ROOT, "[%d] RGBA%d%d%d%d",
									get(EGL10.EGL_CONFIG_ID),
									get(EGL10.EGL_RED_SIZE),
									get(EGL10.EGL_GREEN_SIZE),
									get(EGL10.EGL_BLUE_SIZE),
									get(EGL10.EGL_ALPHA_SIZE));
			else
				s = String.format(Locale.ROOT, "[%d] RGB%d%d%d",
									get(EGL10.EGL_CONFIG_ID),
									get(EGL10.EGL_RED_SIZE),
									get(EGL10.EGL_GREEN_SIZE),
									get(EGL10.EGL_BLUE_SIZE));

			if (get(EGL10.EGL_DEPTH_SIZE) > 0)
				s += String.format(Locale.ROOT, " D%d", get(EGL10.EGL_DEPTH_SIZE));

			if (get(EGL10.EGL_STENCIL_SIZE) > 0)
				s += String.format(Locale.ROOT, " S%d", get(EGL10.EGL_STENCIL_SIZE));

			if (get(EGL10.EGL_SAMPLES) > 0)
				s += String.format(Locale.ROOT, " MSAAx%d", get(EGL10.EGL_SAMPLES));

			if ((get(EGL10.EGL_SURFACE_TYPE) & EGL10.EGL_WINDOW_BIT) > 0)
				s += " W";
			if ((get(EGL10.EGL_SURFACE_TYPE) & EGL10.EGL_PBUFFER_BIT) > 0)
				s += " P";
			if ((get(EGL10.EGL_SURFACE_TYPE) & EGL10.EGL_PIXMAP_BIT) > 0)
				s += " X";

			if ((get(EGL10.EGL_RENDERABLE_TYPE) & EGL_OPENGL_ES_BIT) > 0)
				s += " ES";
			if ((get(EGL10.EGL_RENDERABLE_TYPE) & EGL_OPENGL_ES2_BIT) > 0)
				s += " ES2";


			switch (get(EGL10.EGL_CONFIG_CAVEAT)) {
			case EGL10.EGL_NONE:
				break;

			case EGL10.EGL_SLOW_CONFIG:
				s += " SLOW";
				break;

			case EGL10.EGL_NON_CONFORMANT_CONFIG:
				s += " NON_CONFORMANT";

			default:
				s += String.format(Locale.ROOT, " unknown CAVEAT 0x%x",
									get(EGL10.EGL_CONFIG_CAVEAT));
			}

			return s;
		}

		public Integer get(Integer key) {
			if (_lhm.containsKey(key) && _lhm.get(key) != null) {
				return _lhm.get(key);
			} else {
				return 0;
			}
		}
	}

	private EGLConfig chooseEglConfig(EGLConfig[] configs, int[] version) {
		EGLConfig res = configs[0];
		int bestScore = -1;

		Log.d(LOG_TAG, "EGL configs:");

		for (EGLConfig config : configs) {
			if (config != null) {
				boolean good = true;

				EglAttribs attr = new EglAttribs(config);

				// must have
				if ((attr.get(EGL10.EGL_SURFACE_TYPE) & EGL10.EGL_WINDOW_BIT) == 0)
					good = false;

				if (version[0] >= 2 ||
					(version[0] == 1 && version[1] >= 3)) {
					// EGL_OPENGL_ES2_BIT is only supported since EGL 1.3
					if ((attr.get(EGL10.EGL_RENDERABLE_TYPE) & EGL_OPENGL_ES2_BIT) == 0)
						good = false;
				}
				if (attr.get(EGL10.EGL_BUFFER_SIZE) < bitsPerPixel)
					good = false;

				// Force a config with a depth buffer and a stencil buffer when rendering directly on backbuffer
				if ((attr.get(EGL10.EGL_DEPTH_SIZE) == 0) || (attr.get(EGL10.EGL_STENCIL_SIZE) == 0))
					good = false;

				int score = attr.weight();

				Log.d(LOG_TAG, String.format(Locale.ROOT, "%s (%d, %s)", attr, score, good ? "OK" : "NOK"));

				if (!good) {
					continue;
				}

				if (score > bestScore) {
					res = config;
					bestScore = score;
				}
			}
		}

		if (bestScore < 0)
			Log.e(LOG_TAG,
					"Unable to find an acceptable EGL config, expect badness.");

		Log.d(LOG_TAG, String.format(Locale.ROOT, "Chosen EGL config: %s",
										new EglAttribs(res)));

		return res;
	}

	static {
//		// For grabbing with gdb...
//		final boolean sleep_for_debugger = false;
//		if (sleep_for_debugger) {
//			try {
//				Thread.sleep(20 * 1000);
//			} catch (InterruptedException ignored) {
//			}
//		}

		System.loadLibrary("scummvm");
	}
}
