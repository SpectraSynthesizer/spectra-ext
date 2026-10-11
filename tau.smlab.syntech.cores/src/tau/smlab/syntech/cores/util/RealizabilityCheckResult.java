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

import net.sf.javabdd.BDD;
import tau.smlab.syntech.games.gr1.GR1Memory;
import tau.smlab.syntech.games.rabin.RabinMemory;

/**
 * This class encapsultes useful information from realizability checks.
 * The class can be used for both Rabin and GR1 games.
 */
public class RealizabilityCheckResult {
	// Whether the system player wins the game
	public final boolean sysWins;
	// The index of the last justice used in the check.
	public final int lastUsedSysJusticeNum;
	// Is the winning states BDD "complete" - known to contain the correct winning states (and not an approximation, for example because of EUN).
	public final boolean isComplete;
	// The winning states BDD
	private BDD sysWinningStatesBdd;

	
	/**
	 * Initialize from a GR1 game result.
	 * @param isRealizable Was the game realizable (system wins)
	 * @param mem The game memory
	 * @param keepWinningStates Whether the winning states BDD should be kept.
	 */
	public RealizabilityCheckResult(boolean isRealizable, GR1Memory mem, boolean keepWinningStates) {
		this.sysWins = isRealizable;
		this.lastUsedSysJusticeNum = mem.getLastUsedSysJustice();
		this.isComplete = mem.isComplete();
		
		if (keepWinningStates) {			
			this.sysWinningStatesBdd = mem.getWin().id();
		} else {
			this.sysWinningStatesBdd = null;
		}
	}
	
	/**
	 * Initialize from a Rabin game result.
	 * @param isRabinRealizable Was the game realizable (environment wins)
	 * @param mem The game memory
	 * @param keepWinningStates Whether the winning states BDD should be kept.
	 */
	public RealizabilityCheckResult(boolean isRabinRealizable, RabinMemory mem, boolean keepWinningStates) {
		this.sysWins = !isRabinRealizable;
		this.lastUsedSysJusticeNum = mem.getLastUsedSysJustice();
		this.isComplete = mem.isComplete();
		if (keepWinningStates) {			
			this.sysWinningStatesBdd = mem.getWin().notWithDoms();
		} else {
			this.sysWinningStatesBdd = null;
		}
	}
	
	/**
	 * Get the winning states BDD.
	 * Note: may be null.
	 */
	public BDD getSysWinningStates() {
		return this.sysWinningStatesBdd;
	}
	
	/**
	 * Free the winning states bdd.
	 */
	public void free() {
		if (this.sysWinningStatesBdd != null) {
			this.sysWinningStatesBdd.free();
			this.sysWinningStatesBdd = null;
		}
	}
}
