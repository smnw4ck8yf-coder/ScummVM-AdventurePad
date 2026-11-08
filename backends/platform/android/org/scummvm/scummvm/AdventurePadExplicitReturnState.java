/* ScummVM - Graphic Adventure Engine */
package org.scummvm.scummvm;

/** One-shot provenance for the in-game Return to AdventurePad command. */
final class AdventurePadExplicitReturnState {
	private boolean _requested;
	private boolean _pending;

	boolean request(boolean facadeLaunch) {
		if (!facadeLaunch)
			return false;
		_requested = true;
		return true;
	}

	void resolve(boolean accepted) {
		if (!_requested)
			return;
		_requested = false;
		_pending = accepted;
	}

	boolean consumeForTarget(String target) {
		if (!_pending || (target != null && !target.isEmpty()))
			return false;
		_pending = false;
		return true;
	}

	boolean isPending() {
		return _pending;
	}

	boolean isRequested() {
		return _requested;
	}

	void clear() {
		_requested = false;
		_pending = false;
	}
}
