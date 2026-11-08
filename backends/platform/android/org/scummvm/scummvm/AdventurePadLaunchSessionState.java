/* ScummVM - Graphic Adventure Engine */
package org.scummvm.scummvm;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;

/** Launch provenance and one-shot accepted-Quit state for an AdventurePad façade session. */
final class AdventurePadLaunchSessionState {
	static final String LOG_TAG = "AdventurePadSession";
	static final String FIRST_PARTY_ADVENTUREPAD = "FIRST_PARTY_ADVENTUREPAD";
	static final String EXTERNAL_LAUNCHER = "EXTERNAL_LAUNCHER";
	static final String DIRECT_SCUMMVM = "DIRECT_SCUMMVM";

	private static final String STATE_ORIGIN = "adventurePad.launchOrigin";
	private static final String STATE_SESSION_ID = "adventurePad.launchSessionId";
	private static final String STATE_GAME_OBSERVED = "adventurePad.gameObserved";
	private static final String STATE_REQUESTED_QUIT_SESSION_ID = "adventurePad.requestedQuitSessionId";
	private static final String STATE_ACCEPTED_QUIT_SESSION_ID = "adventurePad.acceptedQuitSessionId";
	private static final String STATE_HOME_CONSUMED = "adventurePad.homeConsumed";

	private String _origin = DIRECT_SCUMMVM;
	private long _sessionId;
	private boolean _gameObserved;
	private long _requestedQuitSessionId = -1;
	private long _acceptedQuitSessionId = -1;
	private boolean _homeConsumed;
	private static volatile boolean _debugLogging;

	static void configureLogging(Context context) {
		_debugLogging = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
	}

	static String normalizeOrigin(String origin) {
		if (FIRST_PARTY_ADVENTUREPAD.equals(origin) || EXTERNAL_LAUNCHER.equals(origin))
			return origin;
		return DIRECT_SCUMMVM;
	}

	synchronized boolean begin(String origin, long sessionId) {
		String normalizedOrigin = normalizeOrigin(origin);
		if (sessionId > 0 && sessionId == _sessionId && normalizedOrigin.equals(_origin)) {
			log("begin retained origin=" + _origin + " session=" + _sessionId);
			return false;
		}
		log("begin replace oldOrigin=" + _origin + " oldSession=" + _sessionId +
			" origin=" + normalizedOrigin + " session=" + sessionId);
		_origin = normalizedOrigin;
		_sessionId = sessionId;
		_gameObserved = false;
		_requestedQuitSessionId = -1;
		_acceptedQuitSessionId = -1;
		_homeConsumed = false;
		return true;
	}

	synchronized boolean isFacadeLaunch() {
		return !DIRECT_SCUMMVM.equals(_origin);
	}

	synchronized String origin() {
		return _origin;
	}

	synchronized long sessionId() {
		return _sessionId;
	}

	synchronized boolean hasObservedGame() {
		return _gameObserved;
	}

	synchronized void observeTarget(String target) {
		if (target != null && !target.isEmpty())
			_gameObserved = true;
		log("target=" + (target == null ? "<blank>" : target) +
			" origin=" + _origin + " session=" + _sessionId +
			" gameObserved=" + _gameObserved);
	}

	synchronized void requestQuit() {
		_requestedQuitSessionId = FIRST_PARTY_ADVENTUREPAD.equals(_origin) && _gameObserved ?
			_sessionId : -1;
		log("quit request origin=" + _origin + " session=" + _sessionId +
			" gameObserved=" + _gameObserved + " requestedSession=" +
			_requestedQuitSessionId);
	}

	synchronized void resolveQuit(boolean accepted) {
		if (_requestedQuitSessionId != _sessionId) {
			log("quit resolve ignored accepted=" + accepted + " session=" + _sessionId +
				" requestedSession=" + _requestedQuitSessionId + " acceptedSession=" +
				_acceptedQuitSessionId);
			return;
		}
		_acceptedQuitSessionId = accepted ? _sessionId : -1;
		log("quit resolve accepted=" + accepted + " session=" + _sessionId +
			" requestedSession=" + _requestedQuitSessionId + " acceptedSession=" +
			_acceptedQuitSessionId);
		_requestedQuitSessionId = -1;
	}

	synchronized void cancelPendingExit() {
		log("quit cancel session=" + _sessionId + " requestedSession=" +
			_requestedQuitSessionId + " acceptedSession=" + _acceptedQuitSessionId);
		_requestedQuitSessionId = -1;
		_acceptedQuitSessionId = -1;
	}

	synchronized boolean consumeFirstPartyHome(String target) {
		boolean targetPresent = target != null && !target.isEmpty();
		boolean blankTarget = target == null || target.isEmpty();
		boolean firstParty = FIRST_PARTY_ADVENTUREPAD.equals(_origin);
		boolean positiveSession = _sessionId > 0;
		boolean acceptedSessionMatches = _acceptedQuitSessionId == _sessionId;
		boolean eligible = !_homeConsumed && _gameObserved && blankTarget &&
			firstParty && positiveSession && acceptedSessionMatches;
		log("home evaluate session=" + _sessionId + " homeConsumed=" + _homeConsumed +
			" gameObserved=" + _gameObserved + " targetNull=" + (target == null) +
			" targetPresent=" + targetPresent + " blankTarget=" + blankTarget +
			" firstParty=" + firstParty +
			" positiveSession=" + positiveSession + " acceptedSession=" +
			_acceptedQuitSessionId + " acceptedSessionMatches=" + acceptedSessionMatches +
			" eligible=" + eligible);
		if (!eligible)
			return false;
		_homeConsumed = true;
		_acceptedQuitSessionId = -1;
		return true;
	}

	synchronized void save(Bundle state) {
		state.putString(STATE_ORIGIN, _origin);
		state.putLong(STATE_SESSION_ID, _sessionId);
		state.putBoolean(STATE_GAME_OBSERVED, _gameObserved);
		state.putLong(STATE_REQUESTED_QUIT_SESSION_ID, _requestedQuitSessionId);
		state.putLong(STATE_ACCEPTED_QUIT_SESSION_ID, _acceptedQuitSessionId);
		state.putBoolean(STATE_HOME_CONSUMED, _homeConsumed);
	}

	synchronized void restore(Bundle state) {
		_origin = normalizeOrigin(state.getString(STATE_ORIGIN));
		_sessionId = state.getLong(STATE_SESSION_ID, 0);
		_gameObserved = state.getBoolean(STATE_GAME_OBSERVED, false);
		_requestedQuitSessionId = state.getLong(STATE_REQUESTED_QUIT_SESSION_ID, -1);
		_acceptedQuitSessionId = state.getLong(STATE_ACCEPTED_QUIT_SESSION_ID, -1);
		_homeConsumed = state.getBoolean(STATE_HOME_CONSUMED, false);
		log("restore origin=" + _origin + " session=" + _sessionId +
			" gameObserved=" + _gameObserved + " requestedSession=" +
			_requestedQuitSessionId + " acceptedSession=" + _acceptedQuitSessionId +
			" homeConsumed=" + _homeConsumed);
	}

	static void log(String message) {
		if (_debugLogging)
			android.util.Log.d(LOG_TAG, message);
	}
}
