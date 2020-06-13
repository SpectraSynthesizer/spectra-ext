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

import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.games.gr1.GR1Game;
import tau.smlab.syntech.games.gr1.GR1GameExperiments;
import tau.smlab.syntech.games.gr1.GR1GameImplC;
import tau.smlab.syntech.games.rabin.RabinGame;
import tau.smlab.syntech.games.rabin.RabinGameExperiments;
import tau.smlab.syntech.games.rabin.RabinGameImplC;

/**
 * Realizability checks that considers menu options for cores
 * 
 * @author shalom
 *
 */

public class RealizabilityCheck {
	public enum GameType {
		GR1_GAME, RABIN_GAME
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
		return (checkType == GameType.GR1_GAME) ?
				isGR1Realizable(m) : isRabinRealizable(m);
	}
	
	  /**
	   * This method checks realizability using a GR1 game
	   * It takes into account the BDD setting in order to decide if we use the C implementation or not
	   * @param m the model
	   * @return
	   */
	  
	public static boolean isGR1Realizable(GameModel m) {
		GR1Game gr1;		  
		if (useCUDD) {
			gr1 = new GR1GameImplC(m);
		} else {
			gr1 = new GR1GameExperiments(m);
		}
		boolean realizable = gr1.checkRealizability();
		gr1.free();
		return realizable;
	}
	  
	  /**
	   * This method checks realizability using a Rabin game
	   * It takes into account the BDD setting in order to decide if we use the C implementation or not   
	   * @param m the model
	   * @return
	   */
	public static boolean isRabinRealizable(GameModel m) {
		RabinGame rg;
		if (useCUDD) {
			rg = new RabinGameImplC(m);
		} else {
			rg = new RabinGameExperiments(m);
		}
		boolean realizable = !rg.checkRealizability();
		rg.free();
		return realizable;
	}
}
