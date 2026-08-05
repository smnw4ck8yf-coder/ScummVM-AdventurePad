/* ScummVM - Graphic Adventure Engine
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.scummvm.scummvm;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.HashSet;

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
	private static final float MAX_ABSOLUTE_DELTA = 512.0f;
	private static final double JE_BALL_UNITS_PER_PIXEL = 50.0;
	private static final int JOYSTICK_AXIS_MAX = 32767;
	private static final int JOYSTICK_TRIGGER_FLAGS = 0x40 | 0x80;
	private static final int JOYSTICK_VALID_FLAGS = 0xff;

	private static volatile ScummVM _nativeEventSink;

	private double _fractionalResidualX;
	private double _fractionalResidualY;
	private final int[] _joystickAxisPositions = new int[8];
	private final HashSet<Integer> _gamepadKeysDown = new HashSet<>();
	private boolean _leftButtonDown;
	private boolean _rightButtonDown;
	private final Messenger _messenger = new Messenger(new IncomingHandler(Looper.getMainLooper()));

	static void attachNativeEventSink(ScummVM sink) {
		_nativeEventSink = sink;
		Log.i(TAG, "Native event sink attached");
	}

	static void detachNativeEventSink(ScummVM sink) {
		if (_nativeEventSink == sink) {
			_nativeEventSink = null;
			Log.i(TAG, "Native event sink detached");
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.i(TAG, "AdventurePad Messenger bound");
		return _messenger.getBinder();
	}

	@Override
	public boolean onUnbind(Intent intent) {
		releaseForwardedInput();
		Log.i(TAG, "AdventurePad Messenger disconnected");
		return super.onUnbind(intent);
	}

	@Override
	public void onDestroy() {
		releaseForwardedInput();
		_fractionalResidualX = 0.0;
		_fractionalResidualY = 0.0;
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
			default:
				Log.w(TAG, "Rejected unknown Messenger message type " + message.what);
			}
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
