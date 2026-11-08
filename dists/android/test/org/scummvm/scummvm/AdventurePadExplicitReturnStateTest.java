package org.scummvm.scummvm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AdventurePadExplicitReturnStateTest {
	@Test
	public void facadeReturnWaitsForBlankTargetAndIsConsumedOnce() {
		AdventurePadExplicitReturnState state = new AdventurePadExplicitReturnState();
		assertTrue(state.request(true));
		assertTrue(state.isRequested());
		assertFalse(state.isPending());
		state.resolve(true);
		assertFalse(state.isRequested());
		assertTrue(state.isPending());
		assertFalse(state.consumeForTarget("monkey"));
		assertTrue(state.consumeForTarget(""));
		assertFalse(state.consumeForTarget(""));
	}

	@Test
	public void cancelledConfirmationCannotAffectLaterQuit() {
		AdventurePadExplicitReturnState state = new AdventurePadExplicitReturnState();
		assertTrue(state.request(true));
		state.resolve(false);
		assertFalse(state.isRequested());
		assertFalse(state.isPending());
		assertFalse(state.consumeForTarget(""));
	}

	@Test
	public void directLaunchCannotArmExplicitAdventurePadReturn() {
		AdventurePadExplicitReturnState state = new AdventurePadExplicitReturnState();
		assertFalse(state.request(false));
		assertFalse(state.isPending());
		assertFalse(state.consumeForTarget(""));
	}

	@Test
	public void ordinaryExitWithoutExplicitRequestDoesNotReturnToAdventurePad() {
		AdventurePadExplicitReturnState state = new AdventurePadExplicitReturnState();
		assertFalse(state.consumeForTarget(""));
	}
}
