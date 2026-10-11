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
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.games.gr1.GR1Game;
import tau.smlab.syntech.games.gr1.GR1GameExperiments;
import tau.smlab.syntech.games.gr1.GR1GameImplC;
import tau.smlab.syntech.games.gr1.GR1SeparatedGame;
import tau.smlab.syntech.games.gr1.GR1SeparatedGameImplC;
import tau.smlab.syntech.games.rabin.RabinGame;
import tau.smlab.syntech.games.rabin.RabinGameExperiments;
import tau.smlab.syntech.games.rabin.RabinGameImplC;
import tau.smlab.syntech.jtlv.Env;

/**
 * Realizability check wrappers that considers menu options for cores
 * 
 * @author shalom
 *
 */
public class RealizabilityCheck {
	public enum GameType {
		GR1_GAME, RABIN_GAME, KIND_GR1_GAME
	}

	public static GameType checkType = GameType.GR1_GAME;
	public static boolean useCUDD = true;

	/**
	 * Check realizability of model according to the specified game type
	 * 
	 * @param m
	 * @return
	 */
	public static boolean isRealizable(GameModel m) {
		return isRealizable(checkType, m);
	}

	public static boolean isRealizable(GameType currentCheckType, GameModel m) {
		switch (currentCheckType) {
			case GR1_GAME:
				return isGR1Realizable(m);
			case RABIN_GAME:
				return isRabinRealizable(m);
			default: // KIND_GR1_GAME
				return isKindRealizable(m);
		}
	}

	public static RealizabilityCheckResult checkRealizability(GameType currentCheckType, GameModel m, BDD initialY, boolean keepWinningStates) {
		switch (currentCheckType) {
			case GR1_GAME:
				return checkGR1Realizability(m, initialY, keepWinningStates);
			case RABIN_GAME:
				return checkRabinRealizability(m, initialY, keepWinningStates);
			default: // KIND_GR1_GAME
				return checkKindRealizability(m, initialY, keepWinningStates);
		}
	}

	public static RealizabilityCheckResult checkRealizability(GameType currentCheckType, GameModel m) {
		return checkRealizability(currentCheckType, m, null, false);
	}
	
	public static RealizabilityCheckResult checkRealizability(GameModel m, boolean keepWinningStates) {
		return checkRealizability(checkType, m, null, true);
	}

	public static RealizabilityCheckResult checkRealizability(GameModel m) {
		return checkRealizability(checkType, m);
	}

	/**
	 * This method checks realizability using a GR1 game
	 * It takes into account the BDD setting in order to decide if we use the C
	 * implementation or not
	 * 
	 * @param m the model
	 * @return
	 */

	public static boolean isGR1Realizable(GameModel m) {
		return checkGR1Realizability(m).sysWins;
	}

	/**
	 * This method checks realizability using a Rabin game
	 * It takes into account the BDD setting in order to decide if we use the C
	 * implementation or not
	 * 
	 * @param m the model
	 * @return
	 */
	public static boolean isRabinRealizable(GameModel m) {
		return checkRabinRealizability(m).sysWins;
	}

	/**
	 * This method checks realizability using a GR1 kind game (4fp)
	 * It takes into account the BDD setting in order to decide if we use the C
	 * implementation or not
	 * 
	 * @param m the model
	 * @return
	 */
	public static boolean isKindRealizable(GameModel m) {
		return checkKindRealizability(m).sysWins;
	}

	/**
	 * Check realizability using GR1.
	 * It takes into account the BDD setting in order to choose between the C and Java implementations.
	 * @param m The game
	 * @param initialY An initial value for the Y bdd, or null for the default value
	 * @param keepWinningStates Whether the check result should also contain the winning states BDD. 
	 */
	public static RealizabilityCheckResult checkGR1Realizability(GameModel m, BDD initialY, boolean keepWinningStates) {
		GR1Game gr1;
		if (useCUDD) {
			gr1 = new GR1GameImplC(m);
		} else {
			gr1 = new GR1GameExperiments(m);
		}
		boolean realizable = gr1.checkRealizability(initialY != null ? initialY : Env.FALSE());
		RealizabilityCheckResult result = new RealizabilityCheckResult(realizable, gr1.getMem(), keepWinningStates);
		gr1.free();
		return result;
	}

	public static RealizabilityCheckResult checkGR1Realizability(GameModel m) {
		return checkGR1Realizability(m, null, false);
	}

	/**
	 * Check realizability using Rabin.
	 * It takes into account the BDD setting in order to choose between the C and Java implementations.
	 * @param m The game
	 * @param initialY An initial value for the Y bdd, or null for the default value
	 * @param keepWinningStates Whether the check result should also contain the winning states BDD. 
	 */
	public static RealizabilityCheckResult checkRabinRealizability(GameModel m, BDD initialY, boolean keepWinningStates) {
		RabinGame rabin;
		if (useCUDD) {
			rabin = new RabinGameImplC(m);
		} else {
			rabin = new RabinGameExperiments(m);
		}
		boolean realizable = rabin.checkRealizability(initialY != null ? initialY : Env.TRUE());
		RealizabilityCheckResult result = new RealizabilityCheckResult(realizable, rabin.getMem(), keepWinningStates);
		rabin.free();
		return result;
	}

	public static RealizabilityCheckResult checkRabinRealizability(GameModel m) {
		return checkRabinRealizability(m, null, false);
	}

	/**
	 * Check realizability using kind GR1 (4FP).
	 * It takes into account the BDD setting in order to choose between the C and Java implementations.
	 * @param m The game
	 * @param initialY An initial value for the Y bdd, or null for the default value
	 * @param keepWinningStates Whether the check result should also contain the winning states BDD. 
	 */
	public static RealizabilityCheckResult checkKindRealizability(GameModel m, BDD initialY, boolean keepWinningStates) {
		GR1Game gr1;
		if (useCUDD) {
			gr1 = new GR1SeparatedGameImplC(m);
		} else {
			gr1 = new GR1SeparatedGame(m);
		}
		boolean realizable = gr1.checkRealizability(initialY != null ? initialY : Env.FALSE());
		RealizabilityCheckResult result = new RealizabilityCheckResult(realizable, gr1.getMem(), keepWinningStates);
		gr1.free();
		return result;
	}

	public static RealizabilityCheckResult checkKindRealizability(GameModel m) {
		return checkKindRealizability(m, null, false);
	}
}
