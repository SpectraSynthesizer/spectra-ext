/*
Copyright (c) since 2015, Tel Aviv University and Software Modeling Lab

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of Tel Aviv University and Software Modeling Lab nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL Tel Aviv University and Software Modeling Lab 
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE 
GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT 
LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT 
OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
*/

package tau.smlab.syntech.cores.util;

import java.util.List;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.cores.util.RealizabilityCheck.GameType;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.util.TraceInfoBuilder;

/**
 * This class encapsulates the information required to perform repeated realizability checks of subspecifications
 * with shared configuration - check type (regular / rabin / kind), initial Y and keeping of winning states BDDs.
 */
public class RealizabilityChecker extends Checker<Integer> {
	protected TraceInfoBuilder builder;
	protected RealizabilityCheckResult lastCheckResult;
	protected boolean checksKeepWinningStates = false;
	protected BDD nextInitialY = null;
	protected GameType gameType = GameType.GR1_GAME;
	
	/**
	 * Initialize a new checker of the type stated at RealizabilityCheck.checkType.
	 */
	public RealizabilityChecker(TraceInfoBuilder builder) {
		this(builder, RealizabilityCheck.checkType);
	}
	
	public RealizabilityChecker(TraceInfoBuilder builder, GameType gameType) {
		this.builder = builder;
		this.lastCheckResult = null;
		this.checksKeepWinningStates = false;
		this.nextInitialY = null;
		this.gameType = gameType;
	}
	
	/**
	 * Get the result of the last realizability check performed by the checker.
	 */
	public RealizabilityCheckResult getLastCheckResult() {
		return lastCheckResult;
	}
	
	/**
	 * Clear the result of the last check performed by the checker.
	 */
	public void clearLastCheckResult() {
		if (lastCheckResult != null) {
			lastCheckResult.free();
		}
		lastCheckResult = null;
	}

	/**
	 * Change the checker's game type
	 * @param gameType
	 */
	public void setGameType(GameType gameType) {
		this.gameType = gameType;
	}
	
	public GameType getGameType() {
		return gameType;
	}
		
	/**
	 * Select whether the RealizabilityCheckResults should also contain the winning states BDD.
	 */
	public void setChecksKeepWinningStates(boolean b) {
		checksKeepWinningStates = b;
	}
	
	/**
	 * Select the initial Y BDD passed as an argument to the realizability check.
	 */
	public void setInitialY(BDD win) {
		if (nextInitialY != null) {
			nextInitialY.free();
		}
		nextInitialY = win == null ? null : win.id();
	}
	
	/**
	 * Check the realizability of the game subset passed.
	 */
	@Override
	protected boolean check(List<Integer> part) {
		GameModel gm = builder.build(part);
		lastCheckResult = RealizabilityCheck.checkRealizability(gameType, gm, nextInitialY, checksKeepWinningStates);
		return !lastCheckResult.sysWins;
	}
	
	@Override
	public boolean wrappedCheck(List<Integer> part, boolean checkCache, boolean storeInCache) {
		// Clear the previous result before realizability checking - so we get null instead of
		// the previous check result when the new game is already cached.
		clearLastCheckResult();
		return super.wrappedCheck(part, checkCache, storeInCache);
	}

	public TraceInfoBuilder getBuilder() {
		return builder;
	}
}
