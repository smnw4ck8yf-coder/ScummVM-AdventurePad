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

import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

/** Wire constants for the optional AdventurePad dual-surface rendering proof. */
final class MirrorSurfaceProtocol {
	private static final int MAX_DIAGNOSTIC_LENGTH = 160;
	static final boolean ENABLED = true;

	static final int MSG_ATTACH_SURFACE = 100;
	static final int MSG_DETACH_SURFACE = 101;
	static final int MSG_STATUS = 102;
	static final int MSG_QUERY_GEOMETRY = 103;
	static final int MSG_GEOMETRY = 104;
	static final int MSG_APPLY_CROP = 105;
	static final int MSG_CROP_ACK = 106;
	static final int MSG_APPLY_DISPLAY_MODE = 107;
	static final int MSG_DISPLAY_MODE_ACK = 108;
	static final int MSG_ABSOLUTE_SOURCE_POINTER = 109;
	static final int MSG_CURSOR_POSITION = 110;

	static final String KEY_SURFACE = "mirrorSurface";
	static final String KEY_GENERATION = "surfaceGeneration";
	static final String KEY_WIDTH = "width";
	static final String KEY_HEIGHT = "height";
	static final String KEY_DISPLAY_ID = "displayId";
	static final String KEY_STATUS = "status";
	static final String KEY_DIAGNOSTIC = "diagnostic";
	static final String KEY_SOURCE_WIDTH = "sourceWidth";
	static final String KEY_SOURCE_HEIGHT = "sourceHeight";
	static final String KEY_RENDERER_CAPABILITY = "rendererCapability";
	static final String KEY_GEOMETRY_GENERATION = "geometryGeneration";
	static final String KEY_GAME_ID = "gameId";
	static final String KEY_CROP_GENERATION = "cropGeneration";
	static final String KEY_EXPECTED_GEOMETRY_GENERATION = "expectedGeometryGeneration";
	static final String KEY_LEFT = "cropLeft";
	static final String KEY_TOP = "cropTop";
	static final String KEY_RIGHT = "cropRight";
	static final String KEY_BOTTOM = "cropBottom";
	static final String KEY_CROP_RESULT = "cropResult";
	static final String KEY_DISPLAY_MODE = "displayMode";
	static final String KEY_MODE_GENERATION = "modeGeneration";
	static final String KEY_MODE_RESULT = "modeResult";
	static final String KEY_ORIENTATION = "sourceOrientation";
	static final String KEY_SOURCE_X = "sourceX";
	static final String KEY_SOURCE_Y = "sourceY";
	static final String KEY_POINTER_ACTION = "pointerAction";
	static final String KEY_POINTER_ID = "pointerId";
	static final String KEY_POINTER_SEQUENCE_ID = "pointerSequenceId";
	static final String KEY_CURSOR_VISIBLE = "cursorVisible";

	static final int STATUS_SUPPORTED = 1;
	static final int STATUS_UNSUPPORTED_NO_TEXTURE = 2;
	static final int STATUS_ATTACHED = 3;
	static final int STATUS_DETACHED = 4;
	static final int STATUS_FAILED = 5;

	static final int CAPABILITY_CROP = 1;
	static final int CROP_APPLIED = 1;
	static final int CROP_REJECTED = 2;
	static final int CROP_INCOMPATIBLE_GEOMETRY = 3;
	static final int CROP_INVALID_RECTANGLE = 4;
	static final int CROP_UNSUPPORTED_SOURCE = 5;
	static final int CROP_STALE_GENERATION = 6;
	static final int MODE_FULL_FRAME_APPLIED = 1;
	static final int MODE_EXPANDED_APPLIED = 2;
	static final int MODE_EXPANDED_UNSUPPORTED_SHAPE = 3;
	static final int MODE_INVALID_CROP = 4;
	static final int MODE_STALE_GENERATION = 5;
	static final int MODE_UNSUPPORTED_RENDERER = 6;

	private MirrorSurfaceProtocol() {
	}

	static void sendStatus(Messenger recipient, int status, long generation, String diagnostic) {
		if (recipient == null)
			return;
		Bundle data = new Bundle();
		data.putInt(KEY_STATUS, status);
		data.putLong(KEY_GENERATION, generation);
		String boundedDiagnostic = diagnostic == null ? "" : diagnostic;
		if (boundedDiagnostic.length() > MAX_DIAGNOSTIC_LENGTH)
			boundedDiagnostic = boundedDiagnostic.substring(0, MAX_DIAGNOSTIC_LENGTH);
		data.putString(KEY_DIAGNOSTIC, boundedDiagnostic);
		Message response = Message.obtain(null, MSG_STATUS);
		response.setData(data);
		try {
			recipient.send(response);
		} catch (RemoteException exception) {
			Log.w("AdventurePadMirror", "Could not deliver mirror status", exception);
		}
	}

	static void sendGeometry(Messenger recipient, int width, int height, int capability,
			long generation, String gameId, int orientation) {
		if (recipient == null)
			return;
		Bundle data = new Bundle();
		data.putInt(KEY_SOURCE_WIDTH, width);
		data.putInt(KEY_SOURCE_HEIGHT, height);
		data.putInt(KEY_RENDERER_CAPABILITY, capability);
		data.putLong(KEY_GEOMETRY_GENERATION, generation);
		data.putString(KEY_GAME_ID, gameId == null ? "" : gameId);
		data.putInt(KEY_ORIENTATION, orientation);
		send(recipient, MSG_GEOMETRY, data);
	}

	static void sendCropAck(Messenger recipient, int result, long cropGeneration,
			long geometryGeneration, String diagnostic) {
		if (recipient == null)
			return;
		Bundle data = new Bundle();
		data.putInt(KEY_CROP_RESULT, result);
		data.putLong(KEY_CROP_GENERATION, cropGeneration);
		data.putLong(KEY_GEOMETRY_GENERATION, geometryGeneration);
		String boundedDiagnostic = diagnostic == null ? "" : diagnostic;
		if (boundedDiagnostic.length() > MAX_DIAGNOSTIC_LENGTH)
			boundedDiagnostic = boundedDiagnostic.substring(0, MAX_DIAGNOSTIC_LENGTH);
		data.putString(KEY_DIAGNOSTIC, boundedDiagnostic);
		send(recipient, MSG_CROP_ACK, data);
	}

	static void sendDisplayModeAck(Messenger recipient, int result, long modeGeneration,
			long geometryGeneration, String diagnostic) {
		if (recipient == null)
			return;
		Bundle data = new Bundle();
		data.putInt(KEY_MODE_RESULT, result);
		data.putLong(KEY_MODE_GENERATION, modeGeneration);
		data.putLong(KEY_GEOMETRY_GENERATION, geometryGeneration);
		String boundedDiagnostic = diagnostic == null ? "" : diagnostic;
		if (boundedDiagnostic.length() > MAX_DIAGNOSTIC_LENGTH)
			boundedDiagnostic = boundedDiagnostic.substring(0, MAX_DIAGNOSTIC_LENGTH);
		data.putString(KEY_DIAGNOSTIC, boundedDiagnostic);
		send(recipient, MSG_DISPLAY_MODE_ACK, data);
	}

	static void sendCursorPosition(Messenger recipient, int x, int y, boolean visible,
			long geometryGeneration) {
		if (recipient == null)
			return;
		Bundle data = new Bundle();
		data.putInt(KEY_SOURCE_X, x);
		data.putInt(KEY_SOURCE_Y, y);
		data.putBoolean(KEY_CURSOR_VISIBLE, visible);
		data.putLong(KEY_GEOMETRY_GENERATION, geometryGeneration);
		send(recipient, MSG_CURSOR_POSITION, data);
	}

	private static void send(Messenger recipient, int what, Bundle data) {
		Message response = Message.obtain(null, what);
		response.setData(data);
		try {
			recipient.send(response);
		} catch (RemoteException exception) {
			Log.w("AdventurePadMirror", "Could not deliver mirror protocol reply", exception);
		}
	}
}
