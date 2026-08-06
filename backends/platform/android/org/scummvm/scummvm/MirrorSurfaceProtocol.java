/* ScummVM - Graphic Adventure Engine
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
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

	static final String KEY_SURFACE = "mirrorSurface";
	static final String KEY_GENERATION = "surfaceGeneration";
	static final String KEY_WIDTH = "width";
	static final String KEY_HEIGHT = "height";
	static final String KEY_DISPLAY_ID = "displayId";
	static final String KEY_STATUS = "status";
	static final String KEY_DIAGNOSTIC = "diagnostic";

	static final int STATUS_SUPPORTED = 1;
	static final int STATUS_UNSUPPORTED_NO_TEXTURE = 2;
	static final int STATUS_ATTACHED = 3;
	static final int STATUS_DETACHED = 4;
	static final int STATUS_FAILED = 5;

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
}
