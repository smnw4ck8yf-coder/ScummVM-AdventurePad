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

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

/** Receives bounded relative pointer deltas from the separately installed AdventurePad app. */
public final class RelativeInputService extends Service {
	private static final String TAG = "AdventurePadBridge";
	private static final int MSG_RELATIVE_MOVE = 1;
	private static final int MSG_LEFT_BUTTON_DOWN = 2;
	private static final int MSG_LEFT_BUTTON_UP = 3;
	private static final int MSG_RIGHT_BUTTON_DOWN = 4;
	private static final int MSG_RIGHT_BUTTON_UP = 5;
	private static final int MSG_JOYSTICK_AXIS = 6;
	private static final int MSG_GAMEPAD_KEY = 7;
	private static final int MSG_VERTICAL_SCROLL = 8;
	private static final int MSG_QUERY_GAME_LIBRARY = 200;
	private static final int MSG_LAUNCH_GAME_TARGET = 201;
	private static final int MSG_OPEN_SCUMMVM_LIBRARY = 202;
	private static final int MSG_GAME_LIBRARY = 203;
	private static final int MSG_RESUME_GAME_TARGET = 204;
	private static final int MSG_LOAD_GAME_TARGET = 205;
	private static final int MSG_REFRESH_SAVE_CAPABILITIES = 206;
	private static final int MSG_ADD_GAME = 207;
	private static final int MSG_REMOVE_GAME_TARGET = 208;
	private static final int MSG_REMOVE_GAME_RESULT = 209;
	private static final String KEY_TARGETS = "targets";
	private static final String KEY_TARGET_ID = "targetId";
	private static final String KEY_TITLE = "title";
	private static final String KEY_ENGINE_ID = "engineId";
	private static final String KEY_GAME_ID = "gameId";
	private static final String KEY_ERROR = "error";
	private static final String KEY_RESUME_SAVE_SLOT = "resumeSaveSlot";
	private static final String KEY_RESUME_UNAVAILABLE_REASON = "resumeUnavailableReason";
	private static final String KEY_LOAD_GAME_AVAILABLE = "loadGameAvailable";
	private static final String KEY_SAVE_CAPABILITIES_READY = "saveCapabilitiesReady";
	private static final String KEY_REMOVED = "removed";
	private static final String EXTRA_ADVENTUREPAD_FACADE =
		"org.scummvm.scummvm.extra.ADVENTUREPAD_FACADE";
	private static final String EXTRA_ADVENTUREPAD_ADVANCED =
		"org.scummvm.scummvm.extra.ADVENTUREPAD_ADVANCED";
	private static final String EXTRA_ADVENTUREPAD_ADD_GAME =
		"org.scummvm.scummvm.extra.ADVENTUREPAD_ADD_GAME";
	private static final String EXTRA_ADVENTUREPAD_LOAD_TARGET =
		"org.scummvm.scummvm.extra.ADVENTUREPAD_LOAD_TARGET";
	private static final String EXTRA_ADVENTUREPAD_SAVE_SLOT =
		"org.scummvm.scummvm.extra.ADVENTUREPAD_SAVE_SLOT";
	private static final String EXTRA_ADVENTUREPAD_SAVE_CAPABILITY_REFRESH =
		"org.scummvm.scummvm.extra.ADVENTUREPAD_SAVE_CAPABILITY_REFRESH";
	private static final String EXTRA_ADVENTUREPAD_REMOVE_TARGET =
		"org.scummvm.scummvm.extra.ADVENTUREPAD_REMOVE_TARGET";
	private static final Map<String, SaveCapability> _saveCapabilities = new HashMap<>();
	private static boolean _saveCapabilitiesReady;
	private static boolean _saveCapabilityRefreshInFlight;
	private static boolean _nativeStartupInProgress;
	private static volatile RelativeInputService _activeService;
	private Messenger _libraryRecipient;

	private static final class SaveCapability {
		final int latestSlot;
		final boolean loadAvailable;
		final String unavailableReason;

		SaveCapability(int latestSlot, boolean loadAvailable, String unavailableReason) {
			this.latestSlot = latestSlot;
			this.loadAvailable = loadAvailable;
			this.unavailableReason = unavailableReason;
		}
	}

	static synchronized void beginSaveCapabilities() {
		_saveCapabilities.clear();
		_saveCapabilitiesReady = false;
		_saveCapabilityRefreshInFlight = true;
	}

	static synchronized void reportSaveCapability(String target, int latestSlot,
			boolean loadAvailable, String unavailableReason) {
		_saveCapabilities.put(target, new SaveCapability(latestSlot, loadAvailable, unavailableReason));
	}

	static synchronized void finishSaveCapabilities() {
		_saveCapabilitiesReady = true;
		_saveCapabilityRefreshInFlight = false;
		RelativeInputService service = _activeService;
		if (service != null)
			new Handler(Looper.getMainLooper()).post(() ->
				service._incomingHandler.replyWithGameLibrary(service._libraryRecipient));
	}

	static synchronized void setNativeStartupInProgress(boolean inProgress) {
		_nativeStartupInProgress = inProgress;
	}

	static synchronized void finishGameRemoval(String target, boolean removed, String error) {
		if (removed)
			_saveCapabilities.remove(target);
		RelativeInputService service = _activeService;
		if (service != null)
			new Handler(Looper.getMainLooper()).post(() ->
				service._incomingHandler.finishGameRemoval(target, removed, error));
	}
	private static final float MAX_ABSOLUTE_DELTA = 512.0f;
	private static final double JE_BALL_UNITS_PER_PIXEL = 50.0;
	private static final int JOYSTICK_AXIS_MAX = 32767;
	private static final int JOYSTICK_TRIGGER_FLAGS = 0x40 | 0x80;
	private static final int JOYSTICK_VALID_FLAGS = 0xff;
	private static final int MAX_MIRROR_DIMENSION = 8192;
	private static final float MIN_NORMALIZED_CROP_DIMENSION = 0.05f;

	private static volatile ScummVM _nativeEventSink;
	private static volatile String _currentGameTarget = "";
	private static Messenger _geometryRecipient;
	private static int _mirrorSourceWidth;
	private static int _mirrorSourceHeight;
	private static int _mirrorSourceCapability;
	private static int _mirrorSourceOrientation;
	private static long _mirrorGeometryGeneration;

	static void setCurrentGameTarget(String target) {
		String newTarget = target == null ? "" : target;
		ScummVM sink;
		synchronized (RelativeInputService.class) {
			if (newTarget.equals(_currentGameTarget))
				return;
			_currentGameTarget = newTarget;
			sink = _nativeEventSink;
		}
		if (sink != null)
			sink.reportCurrentGameTargetChanged(newTarget);
	}

	static String getCurrentGameTarget() {
		return _currentGameTarget;
	}

	private double _fractionalResidualX;
	private double _fractionalResidualY;
	private double _fractionalScrollResidual;
	private final int[] _joystickAxisPositions = new int[8];
	private final HashSet<Integer> _gamepadKeysDown = new HashSet<>();
	private boolean _leftButtonDown;
	private boolean _rightButtonDown;
	private long _latestMirrorGeneration;
	private long _activeMirrorGeneration;
	private long _latestCropGeneration;
	private long _latestModeGeneration;
	private final IncomingHandler _incomingHandler = new IncomingHandler(Looper.getMainLooper());
	private final Messenger _messenger = new Messenger(_incomingHandler);

	static void attachNativeEventSink(ScummVM sink) {
		Messenger geometryRecipient;
		String currentGameTarget;
		int sourceWidth;
		int sourceHeight;
		int sourceCapability;
		int sourceOrientation;
		long geometryGeneration;
		synchronized (RelativeInputService.class) {
			if (_nativeEventSink == sink)
				return;
			_nativeEventSink = sink;
			geometryRecipient = _geometryRecipient;
			currentGameTarget = _currentGameTarget;
			sourceWidth = _mirrorSourceWidth;
			sourceHeight = _mirrorSourceHeight;
			sourceCapability = _mirrorSourceCapability;
			sourceOrientation = _mirrorSourceOrientation;
			geometryGeneration = _mirrorGeometryGeneration;
		}
		sink.restoreMirrorGeometryRegistration(geometryRecipient, currentGameTarget,
			sourceWidth, sourceHeight, sourceCapability, sourceOrientation, geometryGeneration);
		Log.i(TAG, "Native event sink attached");
	}

	static void detachNativeEventSink(ScummVM sink) {
		synchronized (RelativeInputService.class) {
			if (_nativeEventSink != sink)
				return;
			_nativeEventSink = null;
		}
		Log.i(TAG, "Native event sink detached");
	}

	static synchronized void rememberMirrorSourceGeometry(ScummVM sink, int width, int height,
			int capability, int orientation, long generation) {
		if (_nativeEventSink != sink)
			return;
		_mirrorSourceWidth = width;
		_mirrorSourceHeight = height;
		_mirrorSourceCapability = capability;
		_mirrorSourceOrientation = orientation;
		_mirrorGeometryGeneration = generation;
	}

	@Override
	public IBinder onBind(Intent intent) {
		_activeService = this;
		Log.i(TAG, "AdventurePad Messenger bound");
		return _messenger.getBinder();
	}

	@Override
	public boolean onUnbind(Intent intent) {
		detachMirrorForDisconnect();
		releaseForwardedInput();
		Log.i(TAG, "AdventurePad Messenger disconnected");
		return super.onUnbind(intent);
	}

	@Override
	public void onDestroy() {
		if (_activeService == this)
			_activeService = null;
		detachMirrorForDisconnect();
		releaseForwardedInput();
		_fractionalResidualX = 0.0;
		_fractionalResidualY = 0.0;
		_fractionalScrollResidual = 0.0;
		Log.i(TAG, "Relative input service destroyed");
		super.onDestroy();
	}

	private final class IncomingHandler extends Handler {
		IncomingHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message message) {
			switch (message.what) {
			case MSG_RELATIVE_MOVE:
				forwardRelativeMove(message);
				return;
			case MSG_LEFT_BUTTON_DOWN:
			case MSG_LEFT_BUTTON_UP:
			case MSG_RIGHT_BUTTON_DOWN:
			case MSG_RIGHT_BUTTON_UP:
				forwardButtonEvent(message.what);
				return;
			case MSG_JOYSTICK_AXIS:
				forwardJoystickAxis(message.arg1, message.arg2);
				return;
			case MSG_GAMEPAD_KEY:
				forwardGamepadKey(message.arg1, message.arg2);
				return;
			case MSG_VERTICAL_SCROLL:
				forwardVerticalScroll(message);
				return;
			case MSG_QUERY_GAME_LIBRARY:
				replyWithGameLibrary(message.replyTo);
				return;
			case MSG_LAUNCH_GAME_TARGET:
				launchGameTarget(message.getData().getString(KEY_TARGET_ID));
				return;
			case MSG_OPEN_SCUMMVM_LIBRARY:
				openScummVMLibrary();
				return;
			case MSG_RESUME_GAME_TARGET:
				resumeGameTarget(message.getData().getString(KEY_TARGET_ID),
					message.getData().getInt(KEY_RESUME_SAVE_SLOT, -1));
				return;
			case MSG_LOAD_GAME_TARGET:
				openLoadGame(message.getData().getString(KEY_TARGET_ID));
				return;
			case MSG_REFRESH_SAVE_CAPABILITIES:
				requestSaveCapabilityRefresh(message.replyTo);
				return;
			case MSG_ADD_GAME:
				openAddGame();
				return;
			case MSG_REMOVE_GAME_TARGET:
				removeGameTarget(message);
				return;
			case MirrorSurfaceProtocol.MSG_ATTACH_SURFACE:
				attachMirrorSurface(message);
				return;
			case MirrorSurfaceProtocol.MSG_DETACH_SURFACE:
				detachMirrorSurface(message);
				return;
			case MirrorSurfaceProtocol.MSG_QUERY_GEOMETRY:
				queryMirrorGeometry(message);
				return;
			case MirrorSurfaceProtocol.MSG_APPLY_CROP:
				applyMirrorCrop(message);
				return;
			case MirrorSurfaceProtocol.MSG_APPLY_DISPLAY_MODE:
				applyDisplayMode(message);
				return;
			case MirrorSurfaceProtocol.MSG_ABSOLUTE_SOURCE_POINTER:
				forwardAbsoluteSourcePointer(message);
				return;
			default:
				Log.w(TAG, "Rejected unknown Messenger message type " + message.what);
			}
		}

		private void replyWithGameLibrary(Messenger recipient) {
			if (recipient == null)
				return;
			_libraryRecipient = recipient;
			Bundle reply = new Bundle();
			synchronized (RelativeInputService.class) {
				reply.putBoolean(KEY_SAVE_CAPABILITIES_READY, _saveCapabilitiesReady);
			}
			ArrayList<Bundle> targets = new ArrayList<>();
			try (FileReader reader = new FileReader(new File(getFilesDir(), "scummvm.ini"))) {
				Map<String, Map<String, String>> config = INIParser.parse(reader);
				if (config == null)
					throw new IOException("ScummVM configuration could not be parsed");
				for (Map.Entry<String, Map<String, String>> section : config.entrySet()) {
					Map<String, String> values = section.getValue();
					String gameId = values.get("gameid");
					if (gameId == null || gameId.isEmpty() || values.containsKey("id_came_from_command_line"))
						continue;
					Bundle target = new Bundle();
					target.putString(KEY_TARGET_ID, section.getKey());
					target.putString(KEY_TITLE, valueOrDefault(values, "description", section.getKey()));
					target.putString(KEY_ENGINE_ID, valueOrDefault(values, "engineid", ""));
					target.putString(KEY_GAME_ID, gameId);
					SaveCapability capability;
					synchronized (RelativeInputService.class) {
						capability = _saveCapabilities.get(section.getKey());
					}
					if (capability != null) {
						target.putInt(KEY_RESUME_SAVE_SLOT, capability.latestSlot);
						target.putBoolean(KEY_LOAD_GAME_AVAILABLE, capability.loadAvailable);
						target.putString(KEY_RESUME_UNAVAILABLE_REASON, capability.unavailableReason);
					}
					targets.add(target);
				}
				Collections.sort(targets, new Comparator<Bundle>() {
					@Override
					public int compare(Bundle left, Bundle right) {
						String leftTitle = left.getString(KEY_TITLE);
						String rightTitle = right.getString(KEY_TITLE);
						return (leftTitle == null ? "" : leftTitle).compareToIgnoreCase(
							rightTitle == null ? "" : rightTitle);
					}
				});
			} catch (IOException | RuntimeException exception) {
				Log.e(TAG, "Unable to read configured ScummVM targets", exception);
				reply.putString(KEY_ERROR, exception.getMessage() == null ?
					exception.getClass().getSimpleName() : exception.getMessage());
			}
			reply.putParcelableArrayList(KEY_TARGETS, targets);
			Message response = Message.obtain(null, MSG_GAME_LIBRARY);
			response.setData(reply);
			try {
				recipient.send(response);
			} catch (android.os.RemoteException exception) {
				Log.w(TAG, "AdventurePad library reply failed", exception);
			}
		}

		private void requestSaveCapabilityRefresh(Messenger recipient) {
			if (recipient != null)
				_libraryRecipient = recipient;
			boolean replyFromCurrentMap = false;
			boolean launchRefresh = false;
			synchronized (RelativeInputService.class) {
				if (_saveCapabilitiesReady) {
					replyFromCurrentMap = true;
				} else if (_saveCapabilityRefreshInFlight) {
					Log.i(TAG, "Coalesced duplicate save-capability refresh request");
					return;
				} else {
					_saveCapabilityRefreshInFlight = true;
					if (_nativeEventSink == null && !_nativeStartupInProgress)
						launchRefresh = true;
					else
						Log.i(TAG, "Deferred save-capability refresh until native initialization is safe");
				}
			}
			if (replyFromCurrentMap) {
				replyWithGameLibrary(recipient);
				return;
			}
			if (!launchRefresh)
				return;

			Intent intent = new Intent(Intent.ACTION_MAIN);
			intent.setComponent(new ComponentName(RelativeInputService.this, ScummVMActivity.class));
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY |
				Intent.FLAG_ACTIVITY_NO_ANIMATION);
			intent.putExtra(EXTRA_ADVENTUREPAD_SAVE_CAPABILITY_REFRESH, true);
			try {
				Log.i(TAG, "Starting hidden native save-capability refresh");
				startActivity(intent);
			} catch (RuntimeException exception) {
				synchronized (RelativeInputService.class) {
					_saveCapabilityRefreshInFlight = false;
				}
				Log.e(TAG, "Could not start native save-capability refresh", exception);
			}
		}

		private String valueOrDefault(Map<String, String> values, String key, String fallback) {
			String value = values.get(key);
			return value == null || value.trim().isEmpty() ? fallback : value;
		}

		private void launchGameTarget(String targetId) {
			if (targetId == null || targetId.isEmpty()) {
				Log.w(TAG, "Rejected blank AdventurePad target launch");
				return;
			}
			Intent intent = new Intent(Intent.ACTION_MAIN,
				Uri.fromParts("scummvm", targetId, null));
			intent.setComponent(new ComponentName(RelativeInputService.this, ScummVMActivity.class));
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.putExtra(EXTRA_ADVENTUREPAD_FACADE, true);
			startActivity(intent);
		}

		private void openScummVMLibrary() {
			Intent intent = new Intent(Intent.ACTION_MAIN);
			intent.setComponent(new ComponentName(RelativeInputService.this, ScummVMActivity.class));
			// ScummVMActivity is singleInstance. Reusing it would deliver this flag only
			// after the native launcher had already decided which widgets to create.
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
			intent.putExtra(EXTRA_ADVENTUREPAD_ADVANCED, true);
			startActivity(intent);
		}

		private void openAddGame() {
			Intent intent = new Intent(Intent.ACTION_MAIN);
			intent.setComponent(new ComponentName(RelativeInputService.this, ScummVMActivity.class));
			// Start a fresh native instance so the one-shot launch flag is available
			// before the ScummVM launcher is constructed.
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
			intent.putExtra(EXTRA_ADVENTUREPAD_ADVANCED, true);
			intent.putExtra(EXTRA_ADVENTUREPAD_ADD_GAME, true);
			startActivity(intent);
		}

		private void removeGameTarget(Message message) {
			String targetId = message.getData().getString(KEY_TARGET_ID);
			if (targetId == null || targetId.isEmpty()) {
				replyWithGameRemoval(message.replyTo, targetId, false, "The selected target is invalid.");
				return;
			}
			String activeTarget = getCurrentGameTarget();
			if (activeTarget != null && !activeTarget.isEmpty()) {
				replyWithGameRemoval(message.replyTo, targetId, false,
					"Remove Game is unavailable while a game is active.");
				return;
			}
			_libraryRecipient = message.replyTo;
			Intent intent = new Intent(Intent.ACTION_MAIN);
			intent.setComponent(new ComponentName(RelativeInputService.this, ScummVMActivity.class));
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK |
				Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NO_ANIMATION);
			intent.putExtra(EXTRA_ADVENTUREPAD_REMOVE_TARGET, targetId);
			try {
				startActivity(intent);
			} catch (RuntimeException exception) {
				Log.e(TAG, "Could not start native game removal", exception);
				replyWithGameRemoval(message.replyTo, targetId, false,
					"ScummVM could not remove the configured target.");
			}
		}

		private void finishGameRemoval(String target, boolean removed, String error) {
			replyWithGameRemoval(_libraryRecipient, target, removed, error);
			if (removed)
				replyWithGameLibrary(_libraryRecipient);
		}

		private void replyWithGameRemoval(Messenger recipient, String target, boolean removed,
				String error) {
			if (recipient == null)
				return;
			Bundle result = new Bundle();
			result.putString(KEY_TARGET_ID, target == null ? "" : target);
			result.putBoolean(KEY_REMOVED, removed);
			if (error != null && !error.isEmpty())
				result.putString(KEY_ERROR, error);
			Message response = Message.obtain(null, MSG_REMOVE_GAME_RESULT);
			response.setData(result);
			try {
				recipient.send(response);
			} catch (android.os.RemoteException exception) {
				Log.w(TAG, "AdventurePad removal reply failed", exception);
			}
		}

		private void resumeGameTarget(String targetId, int saveSlot) {
			if (targetId == null || targetId.isEmpty() || saveSlot < 0) {
				Log.w(TAG, "Rejected invalid AdventurePad resume request");
				return;
			}
			Intent intent = new Intent(Intent.ACTION_MAIN, Uri.fromParts("scummvm", targetId, null));
			intent.setComponent(new ComponentName(RelativeInputService.this, ScummVMActivity.class));
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.putExtra(EXTRA_ADVENTUREPAD_FACADE, true);
			intent.putExtra(EXTRA_ADVENTUREPAD_SAVE_SLOT, saveSlot);
			startActivity(intent);
		}

		private void openLoadGame(String targetId) {
			if (targetId == null || targetId.isEmpty()) {
				Log.w(TAG, "Rejected blank AdventurePad load request");
				return;
			}
			Intent intent = new Intent(Intent.ACTION_MAIN);
			intent.setComponent(new ComponentName(RelativeInputService.this, ScummVMActivity.class));
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
			intent.putExtra(EXTRA_ADVENTUREPAD_ADVANCED, true);
			intent.putExtra(EXTRA_ADVENTUREPAD_LOAD_TARGET, targetId);
			startActivity(intent);
		}

		private void attachMirrorSurface(Message message) {
			Bundle data = message.getData();
			data.setClassLoader(Surface.class.getClassLoader());
			Surface surface = getMirrorSurface(data);
			long generation = data.getLong(MirrorSurfaceProtocol.KEY_GENERATION, 0);
			int width = data.getInt(MirrorSurfaceProtocol.KEY_WIDTH, 0);
			int height = data.getInt(MirrorSurfaceProtocol.KEY_HEIGHT, 0);
			int displayId = data.getInt(MirrorSurfaceProtocol.KEY_DISPLAY_ID, -1);

			String rejection = validateMirrorAttachment(surface, generation, width, height);
			ScummVM sink = _nativeEventSink;
			if (!MirrorSurfaceProtocol.ENABLED)
				rejection = "Mirror prototype is disabled";
			else if (sink == null)
				rejection = "Native renderer is unavailable";
			else if (!sink.isMirrorOutputEnabled())
				rejection = "Mirror rendering is disabled for this session";

			if (rejection != null) {
				if (surface != null)
					surface.release();
				MirrorSurfaceProtocol.sendStatus(message.replyTo, MirrorSurfaceProtocol.STATUS_FAILED,
					generation, rejection);
				Log.w(TAG, "Rejected mirror surface generation " + generation + ": " + rejection);
				return;
			}

			_latestMirrorGeneration = generation;
			_activeMirrorGeneration = generation;
			Log.i(TAG, "Accepted mirror surface generation " + generation + " size=" +
				width + "x" + height + " reportedDisplayId=" + displayId);
			sink.queueMirrorSurfaceAttach(surface, generation, width, height, message.replyTo);
		}

		private void detachMirrorSurface(Message message) {
			long generation = message.getData().getLong(MirrorSurfaceProtocol.KEY_GENERATION, 0);
			if (generation <= 0 || generation != _activeMirrorGeneration) {
				MirrorSurfaceProtocol.sendStatus(message.replyTo, MirrorSurfaceProtocol.STATUS_FAILED,
					generation, "Detach does not match the active surface generation");
				return;
			}

			_activeMirrorGeneration = 0;
			ScummVM sink = _nativeEventSink;
			if (sink != null)
				sink.queueMirrorSurfaceDetach(generation, message.replyTo);
			else
				MirrorSurfaceProtocol.sendStatus(message.replyTo, MirrorSurfaceProtocol.STATUS_DETACHED,
					generation, "Renderer already unavailable");
		}

		private String validateMirrorAttachment(Surface surface, long generation, int width, int height) {
			if (surface == null || !surface.isValid())
				return "Surface is null or invalid";
			if (generation <= _latestMirrorGeneration)
				return "Surface generation is stale";
			if (width <= 0 || height <= 0 || width > MAX_MIRROR_DIMENSION || height > MAX_MIRROR_DIMENSION)
				return "Surface dimensions are outside the accepted bounds";
			return null;
		}

		private void queryMirrorGeometry(Message message) {
			synchronized (RelativeInputService.class) {
				_geometryRecipient = message.replyTo;
			}
			ScummVM sink = _nativeEventSink;
			if (sink == null || !sink.isMirrorOutputEnabled()) {
				MirrorSurfaceProtocol.sendGeometry(message.replyTo, 0, 0, 0, 0, getCurrentGameTarget(), 0);
				return;
			}
			sink.queueMirrorGeometryQuery(message.replyTo);
		}

		private void applyMirrorCrop(Message message) {
			Bundle data = message.getData();
			float left = data.getFloat(MirrorSurfaceProtocol.KEY_LEFT, Float.NaN);
			float top = data.getFloat(MirrorSurfaceProtocol.KEY_TOP, Float.NaN);
			float right = data.getFloat(MirrorSurfaceProtocol.KEY_RIGHT, Float.NaN);
			float bottom = data.getFloat(MirrorSurfaceProtocol.KEY_BOTTOM, Float.NaN);
			long cropGeneration = data.getLong(MirrorSurfaceProtocol.KEY_CROP_GENERATION, 0);
			long expectedGeometryGeneration = data.getLong(
				MirrorSurfaceProtocol.KEY_EXPECTED_GEOMETRY_GENERATION, 0);
			ScummVM sink = _nativeEventSink;
			int rejection = 0;
			String diagnostic = null;
			if (cropGeneration <= _latestCropGeneration) {
				rejection = MirrorSurfaceProtocol.CROP_STALE_GENERATION;
				diagnostic = "Crop generation is stale";
			} else if (!isValidCrop(left, top, right, bottom)) {
				rejection = MirrorSurfaceProtocol.CROP_INVALID_RECTANGLE;
				diagnostic = "Crop rectangle is invalid or below the safe minimum";
			} else if (sink == null || !sink.isMirrorOutputEnabled()) {
				rejection = MirrorSurfaceProtocol.CROP_UNSUPPORTED_SOURCE;
				diagnostic = "Mirror source is unavailable";
			}
			if (rejection != 0) {
				if (sink != null)
					sink.queueMirrorCropFallback();
				MirrorSurfaceProtocol.sendCropAck(message.replyTo, rejection, cropGeneration,
					expectedGeometryGeneration, diagnostic);
				return;
			}
			_latestCropGeneration = cropGeneration;
			sink.queueMirrorCrop(left, top, right, bottom, cropGeneration,
				expectedGeometryGeneration, message.replyTo);
		}

		private boolean isValidCrop(float left, float top, float right, float bottom) {
			final float edgeTolerance = 0.0001f;
			final boolean validRectangle = Float.isFinite(left) && Float.isFinite(top) && Float.isFinite(right) &&
				Float.isFinite(bottom) && left >= 0.0f && top >= 0.0f && right <= 1.0f &&
				bottom <= 1.0f && left < right && top < bottom &&
				right - left >= MIN_NORMALIZED_CROP_DIMENSION &&
				bottom - top >= MIN_NORMALIZED_CROP_DIMENSION;
			if (!validRectangle)
				return false;
			final boolean fullFrame = left <= edgeTolerance && top <= edgeTolerance &&
				right >= 1.0f - edgeTolerance && bottom >= 1.0f - edgeTolerance;
			final boolean lowerSplitRegion = left <= edgeTolerance && right >= 1.0f - edgeTolerance &&
				bottom >= 1.0f - edgeTolerance;
			return fullFrame || lowerSplitRegion;
		}

		private void applyDisplayMode(Message message) {
			Bundle data = message.getData();
			int mode = data.getInt(MirrorSurfaceProtocol.KEY_DISPLAY_MODE, -1);
			long modeGeneration = data.getLong(MirrorSurfaceProtocol.KEY_MODE_GENERATION, 0);
			long expectedGeometryGeneration = data.getLong(
				MirrorSurfaceProtocol.KEY_EXPECTED_GEOMETRY_GENERATION, 0);
			float left = data.getFloat(MirrorSurfaceProtocol.KEY_LEFT, Float.NaN);
			float top = data.getFloat(MirrorSurfaceProtocol.KEY_TOP, Float.NaN);
			float right = data.getFloat(MirrorSurfaceProtocol.KEY_RIGHT, Float.NaN);
			float bottom = data.getFloat(MirrorSurfaceProtocol.KEY_BOTTOM, Float.NaN);
			ScummVM sink = _nativeEventSink;
			int rejection = 0;
			String diagnostic = null;
			if (modeGeneration <= _latestModeGeneration) {
				rejection = MirrorSurfaceProtocol.MODE_STALE_GENERATION;
				diagnostic = "Display mode generation is stale";
			} else if ((mode != 0 && mode != 1) || !isValidCrop(left, top, right, bottom)) {
				rejection = MirrorSurfaceProtocol.MODE_INVALID_CROP;
				diagnostic = "Display mode or crop rectangle is invalid";
			} else if (sink == null || !sink.isMirrorOutputEnabled()) {
				rejection = MirrorSurfaceProtocol.MODE_UNSUPPORTED_RENDERER;
				diagnostic = "Crop-capable renderer is unavailable";
			}
			if (rejection != 0) {
				if (sink != null)
					sink.queueUpperPresentationFallback();
				MirrorSurfaceProtocol.sendDisplayModeAck(message.replyTo, rejection,
					modeGeneration, expectedGeometryGeneration, diagnostic);
				return;
			}
			_latestModeGeneration = modeGeneration;
			sink.queueUpperPresentation(mode, left, top, right, bottom, modeGeneration,
				expectedGeometryGeneration, message.replyTo);
		}

		private void forwardAbsoluteSourcePointer(Message message) {
			Bundle data = message.getData();
			int x = data.getInt(MirrorSurfaceProtocol.KEY_SOURCE_X, -1);
			int y = data.getInt(MirrorSurfaceProtocol.KEY_SOURCE_Y, -1);
			int action = data.getInt(MirrorSurfaceProtocol.KEY_POINTER_ACTION, -1);
			int pointerId = data.getInt(MirrorSurfaceProtocol.KEY_POINTER_ID, -1);
			long sequenceId = data.getLong(MirrorSurfaceProtocol.KEY_POINTER_SEQUENCE_ID, 0);
			long cropGeneration = data.getLong(MirrorSurfaceProtocol.KEY_CROP_GENERATION, 0);
			long geometryGeneration = data.getLong(
				MirrorSurfaceProtocol.KEY_EXPECTED_GEOMETRY_GENERATION, 0);
			ScummVM sink = _nativeEventSink;
			if (sink == null || !sink.pushAbsoluteSourcePointer(x, y, action, pointerId,
				sequenceId, cropGeneration, geometryGeneration)) {
				Log.w(TAG, "Rejected absolute-source pointer action=" + action +
					" sequence=" + sequenceId + " cropGeneration=" + cropGeneration +
					" geometryGeneration=" + geometryGeneration);
			}
		}

		@SuppressWarnings("deprecation")
		private Surface getMirrorSurface(Bundle data) {
			return data.getParcelable(MirrorSurfaceProtocol.KEY_SURFACE);
		}

		private void forwardRelativeMove(Message message) {
			float dx = Float.intBitsToFloat(message.arg1);
			float dy = Float.intBitsToFloat(message.arg2);
			if (Float.isNaN(dx) || Float.isInfinite(dx) ||
				Float.isNaN(dy) || Float.isInfinite(dy)) {
				Log.w(TAG, "Rejected non-finite relative delta dx=" + dx + " dy=" + dy);
				return;
			}

			float boundedDx = Math.max(-MAX_ABSOLUTE_DELTA, Math.min(MAX_ABSOLUTE_DELTA, dx));
			float boundedDy = Math.max(-MAX_ABSOLUTE_DELTA, Math.min(MAX_ABSOLUTE_DELTA, dy));
			if (boundedDx != dx || boundedDy != dy)
				Log.w(TAG, "Bounded relative delta from dx=" + dx + " dy=" + dy +
					" to dx=" + boundedDx + " dy=" + boundedDy);
			ScummVM sink = _nativeEventSink;
			if (sink == null) {
				Log.w(TAG, "Rejected relative delta because the native event sink is unavailable");
				return;
			}

			double scaledX = boundedDx * JE_BALL_UNITS_PER_PIXEL + _fractionalResidualX;
			double scaledY = boundedDy * JE_BALL_UNITS_PER_PIXEL + _fractionalResidualY;
			int argX = (int)Math.round(scaledX);
			int argY = (int)Math.round(scaledY);
			_fractionalResidualX = scaledX - argX;
			_fractionalResidualY = scaledY - argY;

			sink.pushEvent(ScummVMEvents.JE_BALL, MotionEvent.ACTION_MOVE,
				argX, argY, 0, 0, 0);
		}

		private void forwardButtonEvent(int messageType) {
			final int action;
			final int button;
			final String diagnostic;
			switch (messageType) {
			case MSG_LEFT_BUTTON_DOWN:
				action = MotionEvent.ACTION_DOWN;
				button = MotionEvent.BUTTON_PRIMARY;
				diagnostic = "LEFT_BUTTON_DOWN";
				break;
			case MSG_LEFT_BUTTON_UP:
				action = MotionEvent.ACTION_UP;
				button = MotionEvent.BUTTON_PRIMARY;
				diagnostic = "LEFT_BUTTON_UP";
				break;
			case MSG_RIGHT_BUTTON_DOWN:
				action = MotionEvent.ACTION_DOWN;
				button = MotionEvent.BUTTON_SECONDARY;
				diagnostic = "RIGHT_BUTTON_DOWN";
				break;
			case MSG_RIGHT_BUTTON_UP:
				action = MotionEvent.ACTION_UP;
				button = MotionEvent.BUTTON_SECONDARY;
				diagnostic = "RIGHT_BUTTON_UP";
				break;
			default:
				return;
			}

			boolean isDown = action == MotionEvent.ACTION_DOWN;
			if (button == MotionEvent.BUTTON_PRIMARY) {
				if (_leftButtonDown == isDown)
					return;
				_leftButtonDown = isDown;
			} else {
				if (_rightButtonDown == isDown)
					return;
				_rightButtonDown = isDown;
			}

			ScummVM sink = _nativeEventSink;
			if (sink == null) {
				Log.w(TAG, "Rejected " + diagnostic + " because the native event sink is unavailable");
				return;
			}

			Log.i(TAG, diagnostic);
			sink.pushEvent(ScummVMEvents.JE_MOUSE_BUTTON, action, button, 0, 0, 0, 0);
		}

		private void forwardVerticalScroll(Message message) {
			float distance = Float.intBitsToFloat(message.arg1);
			if (Float.isNaN(distance) || Float.isInfinite(distance) || distance == 0.0f)
				return;

			ScummVM sink = _nativeEventSink;
			if (sink == null) {
				Log.w(TAG, "Rejected vertical scroll because the native event sink is unavailable");
				return;
			}

			// Preserve sub-step trackpad motion between Messenger messages. The existing
			// wheel event path remains the sole owner of ScummVM launcher/game scrolling.
			double accumulated = _fractionalScrollResidual + distance / 48.0;
			int steps = accumulated > 0.0 ? (int)Math.floor(accumulated) : (int)Math.ceil(accumulated);
			_fractionalScrollResidual = accumulated - steps;
			int boundedSteps = Math.max(-16, Math.min(16, steps));
			for (int i = 0; i < Math.abs(boundedSteps); ++i) {
				int eventType = boundedSteps > 0
					? ScummVMEvents.JE_MOUSE_WHEEL_UP
					: ScummVMEvents.JE_MOUSE_WHEEL_DOWN;
				sink.pushEvent(eventType, 0, 0, 0, 0, 0, 0);
			}
		}

		private void forwardJoystickAxis(int axisFlag, int position) {
			if (!isSingleSupportedAxisFlag(axisFlag)) {
				Log.w(TAG, "Rejected joystick axis flag 0x" + Integer.toHexString(axisFlag));
				return;
			}

			int boundedPosition;
			if ((axisFlag & JOYSTICK_TRIGGER_FLAGS) != 0)
				boundedPosition = Math.max(0, Math.min(JOYSTICK_AXIS_MAX, position));
			else
				boundedPosition = Math.max(-JOYSTICK_AXIS_MAX, Math.min(JOYSTICK_AXIS_MAX, position));

			int axisIndex = Integer.numberOfTrailingZeros(axisFlag);
			if (_joystickAxisPositions[axisIndex] == boundedPosition)
				return;
			_joystickAxisPositions[axisIndex] = boundedPosition;

			ScummVM sink = _nativeEventSink;
			if (sink != null) {
				sink.pushEvent(ScummVMEvents.JE_JOYSTICK, MotionEvent.ACTION_MOVE,
					boundedPosition, 0, axisFlag, 0, 0);
			}
		}

		private void forwardGamepadKey(int action, int keyCode) {
			if ((action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) ||
				!isSupportedGamepadKey(keyCode)) {
				Log.w(TAG, "Rejected gamepad key action=" + action + " keyCode=" + keyCode);
				return;
			}

			if (action == KeyEvent.ACTION_DOWN) {
				if (!_gamepadKeysDown.add(keyCode))
					return;
			} else if (!_gamepadKeysDown.remove(keyCode)) {
				return;
			}

			ScummVM sink = _nativeEventSink;
			if (sink != null)
				sink.pushEvent(ScummVMEvents.JE_GAMEPAD, action, keyCode, 0, 0, 0, 0);
		}
	}

	private void detachMirrorForDisconnect() {
		long generation = _activeMirrorGeneration;
		_activeMirrorGeneration = 0;
		ScummVM sink = _nativeEventSink;
		if (generation > 0 && sink != null)
			sink.queueMirrorSurfaceDetach(generation, null);
	}

	private void releaseForwardedInput() {
		ScummVM sink = _nativeEventSink;
		for (int i = 0; i < _joystickAxisPositions.length; ++i) {
			if (_joystickAxisPositions[i] != 0 && sink != null) {
				sink.pushEvent(ScummVMEvents.JE_JOYSTICK, MotionEvent.ACTION_MOVE,
					0, 0, 1 << i, 0, 0);
			}
			_joystickAxisPositions[i] = 0;
		}
		if (sink != null) {
			for (int keyCode : _gamepadKeysDown)
				sink.pushEvent(ScummVMEvents.JE_GAMEPAD, KeyEvent.ACTION_UP, keyCode, 0, 0, 0, 0);
			if (_leftButtonDown)
				sink.pushEvent(ScummVMEvents.JE_MOUSE_BUTTON, MotionEvent.ACTION_UP,
					MotionEvent.BUTTON_PRIMARY, 0, 0, 0, 0);
			if (_rightButtonDown)
				sink.pushEvent(ScummVMEvents.JE_MOUSE_BUTTON, MotionEvent.ACTION_UP,
					MotionEvent.BUTTON_SECONDARY, 0, 0, 0, 0);
		}
		_gamepadKeysDown.clear();
		_leftButtonDown = false;
		_rightButtonDown = false;
		_fractionalScrollResidual = 0.0;
	}

	private static boolean isSingleSupportedAxisFlag(int axisFlag) {
		return axisFlag != 0 && (axisFlag & ~JOYSTICK_VALID_FLAGS) == 0 &&
			(axisFlag & (axisFlag - 1)) == 0;
	}

	private static boolean isSupportedGamepadKey(int keyCode) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_UP:
		case KeyEvent.KEYCODE_DPAD_DOWN:
		case KeyEvent.KEYCODE_DPAD_LEFT:
		case KeyEvent.KEYCODE_DPAD_RIGHT:
		case KeyEvent.KEYCODE_DPAD_CENTER:
		case KeyEvent.KEYCODE_BUTTON_X:
		case KeyEvent.KEYCODE_BUTTON_Y:
		case KeyEvent.KEYCODE_BUTTON_L1:
		case KeyEvent.KEYCODE_BUTTON_R1:
		case KeyEvent.KEYCODE_BUTTON_THUMBL:
		case KeyEvent.KEYCODE_BUTTON_THUMBR:
		case KeyEvent.KEYCODE_BUTTON_START:
		case KeyEvent.KEYCODE_BUTTON_SELECT:
		case KeyEvent.KEYCODE_BUTTON_MODE:
			return true;
		default:
			return false;
		}
	}
}
