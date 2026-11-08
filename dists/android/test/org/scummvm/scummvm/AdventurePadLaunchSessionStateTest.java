package org.scummvm.scummvm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AdventurePadLaunchSessionStateTest {
	@Test
	public void acceptedFirstPartyQuitRequiresObservedGameThenBlankAndConsumesOnce() {
		AdventurePadLaunchSessionState state = new AdventurePadLaunchSessionState();
		state.begin(AdventurePadLaunchSessionState.FIRST_PARTY_ADVENTUREPAD, 41);
		state.requestQuit();
		state.resolveQuit(true);
		assertFalse(state.consumeFirstPartyHome(""));
		state.observeTarget("atlantis");
		state.requestQuit();
		state.resolveQuit(true);
		assertFalse(state.consumeFirstPartyHome("atlantis"));
		assertTrue(state.consumeFirstPartyHome(""));
		assertFalse(state.consumeFirstPartyHome(""));
	}

	@Test
	public void acceptedFirstPartyQuitTreatsNullAsAuthoritativeBlank() {
		AdventurePadLaunchSessionState state = new AdventurePadLaunchSessionState();
		state.begin(AdventurePadLaunchSessionState.FIRST_PARTY_ADVENTUREPAD, 49);
		state.observeTarget("atlantis");
		state.requestQuit();
		state.resolveQuit(true);

		assertTrue(state.consumeFirstPartyHome(null));
		assertFalse(state.consumeFirstPartyHome(null));
	}

	@Test
	public void acceptedFirstPartyQuitTreatsEmptyStringAsAuthoritativeBlank() {
		AdventurePadLaunchSessionState state = new AdventurePadLaunchSessionState();
		state.begin(AdventurePadLaunchSessionState.FIRST_PARTY_ADVENTUREPAD, 50);
		state.observeTarget("atlantis");
		state.requestQuit();
		state.resolveQuit(true);

		assertTrue(state.consumeFirstPartyHome(""));
		assertFalse(state.consumeFirstPartyHome(""));
	}

	@Test
	public void duplicateAcceptedResolutionPreservesAcceptedSession() {
		AdventurePadLaunchSessionState state = new AdventurePadLaunchSessionState();
		state.begin(AdventurePadLaunchSessionState.FIRST_PARTY_ADVENTUREPAD, 51);
		state.observeTarget("atlantis");
		state.requestQuit();
		state.resolveQuit(true);
		state.resolveQuit(true);

		assertTrue(state.consumeFirstPartyHome(null));
	}

	@Test
	public void duplicateCancellationAfterAcceptancePreservesAcceptedSession() {
		AdventurePadLaunchSessionState state = new AdventurePadLaunchSessionState();
		state.begin(AdventurePadLaunchSessionState.FIRST_PARTY_ADVENTUREPAD, 52);
		state.observeTarget("atlantis");
		state.requestQuit();
		state.resolveQuit(true);
		state.resolveQuit(false);

		assertTrue(state.consumeFirstPartyHome(null));
	}

	@Test
	public void cancelledQuitNeverNavigatesHome() {
		AdventurePadLaunchSessionState state = new AdventurePadLaunchSessionState();
		state.begin(AdventurePadLaunchSessionState.FIRST_PARTY_ADVENTUREPAD, 42);
		state.observeTarget("atlantis");
		state.requestQuit();
		state.resolveQuit(false);
		assertFalse(state.consumeFirstPartyHome(null));
		assertFalse(state.consumeFirstPartyHome(""));
	}

	@Test
	public void externalAndDirectSessionsRetainOrdinaryExitBehaviour() {
		AdventurePadLaunchSessionState state = new AdventurePadLaunchSessionState();
		state.begin(AdventurePadLaunchSessionState.EXTERNAL_LAUNCHER, 43);
		state.observeTarget("atlantis");
		state.requestQuit();
		state.resolveQuit(true);
		assertFalse(state.consumeFirstPartyHome(null));

		state.begin(null, 0);
		state.observeTarget("atlantis");
		state.requestQuit();
		state.resolveQuit(true);
		assertFalse(state.consumeFirstPartyHome(""));
	}

	@Test
	public void staleResolutionAfterNewSessionCannotAffectNewSession() {
		AdventurePadLaunchSessionState state = new AdventurePadLaunchSessionState();
		state.begin(AdventurePadLaunchSessionState.FIRST_PARTY_ADVENTUREPAD, 53);
		state.observeTarget("atlantis");
		state.requestQuit();

		state.begin(AdventurePadLaunchSessionState.FIRST_PARTY_ADVENTUREPAD, 54);
		state.observeTarget("atlantis");
		state.resolveQuit(true);
		assertFalse(state.consumeFirstPartyHome(null));

		state.requestQuit();
		state.resolveQuit(true);
		assertTrue(state.consumeFirstPartyHome(null));
	}

	@Test
	public void repeatedFirstPartySessionsHaveIndependentOneShotHomeConsumption() {
		AdventurePadLaunchSessionState state = new AdventurePadLaunchSessionState();
		state.begin(AdventurePadLaunchSessionState.FIRST_PARTY_ADVENTUREPAD, 55);
		state.observeTarget("atlantis");
		state.requestQuit();
		state.resolveQuit(true);
		assertTrue(state.consumeFirstPartyHome(null));
		assertFalse(state.consumeFirstPartyHome(""));

		state.begin(AdventurePadLaunchSessionState.FIRST_PARTY_ADVENTUREPAD, 56);
		state.observeTarget("atlantis");
		state.requestQuit();
		state.resolveQuit(true);
		assertTrue(state.consumeFirstPartyHome(""));
		assertFalse(state.consumeFirstPartyHome(null));
	}

	@Test
	public void newerSessionInvalidatesAcceptedQuitButSameSessionReuseDoesNot() {
		AdventurePadLaunchSessionState state = new AdventurePadLaunchSessionState();
		state.begin(AdventurePadLaunchSessionState.FIRST_PARTY_ADVENTUREPAD, 44);
		state.observeTarget("atlantis");
		state.requestQuit();
		state.resolveQuit(true);
		assertFalse(state.begin(AdventurePadLaunchSessionState.FIRST_PARTY_ADVENTUREPAD, 44));
		assertTrue(state.consumeFirstPartyHome(""));

		state.begin(AdventurePadLaunchSessionState.FIRST_PARTY_ADVENTUREPAD, 45);
		state.observeTarget("atlantis");
		state.requestQuit();
		state.resolveQuit(true);
		assertTrue(state.begin(AdventurePadLaunchSessionState.FIRST_PARTY_ADVENTUREPAD, 46));
		assertFalse(state.consumeFirstPartyHome(""));
	}

	@Test
	public void explicitReturnCancellationInvalidatesPendingQuit() {
		AdventurePadLaunchSessionState state = new AdventurePadLaunchSessionState();
		state.begin(AdventurePadLaunchSessionState.FIRST_PARTY_ADVENTUREPAD, 47);
		state.observeTarget("atlantis");
		state.requestQuit();
		state.resolveQuit(true);
		state.cancelPendingExit();
		assertFalse(state.consumeFirstPartyHome(""));
	}

	@Test
	public void internalQuitWithoutGlobalMenuRequestCannotNavigateHome() {
		AdventurePadLaunchSessionState state = new AdventurePadLaunchSessionState();
		state.begin(AdventurePadLaunchSessionState.FIRST_PARTY_ADVENTUREPAD, 48);
		state.observeTarget("atlantis");
		state.resolveQuit(true);
		assertFalse(state.consumeFirstPartyHome(null));
		assertFalse(state.consumeFirstPartyHome(""));
	}
}
