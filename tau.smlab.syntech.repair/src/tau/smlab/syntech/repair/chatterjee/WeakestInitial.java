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

package tau.smlab.syntech.repair.chatterjee;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.PlayerModule;
import tau.smlab.syntech.games.gr1.GR1Game;
import tau.smlab.syntech.jtlv.Env;


/**
 * A class for the Computation of an initial assumption
 * Code matching lines 14-15 in the GLASS algorithm pseudocode (Algorithm 3 in the symbolic repair paper).
 * 
 * @author ringert
 *
 */

public class WeakestInitial {

	/**
	 * Compute a weakest initial assumption
	 * 
	 * @param gm
	 * @param quantifyAux 
	 * @return
	 */
	public static BDD weakestIni(GameModel gm, boolean quantifyAux) {

		GR1Game gr1 = new GR1Game(gm);
		gr1.checkRealizability();
		BDD sysWin = gr1.sysWinningStates().id();
		gr1.free();

		if (sysWin.isZero()) {
			return Env.FALSE();
		}

		PlayerModule env = gm.getEnv();
		PlayerModule sys = gm.getSys();
		// compute environment choices that let system reach sysWin in current state
		BDD weakestIni = sys.initial().and(env.initial()).and(sysWin).exist(sys.moduleUnprimeVars());
		
		// alternative to returning weakestIni
		BDD cand = env.initial().notWithDoms().orWith(weakestIni);
		if (quantifyAux) {
			BDD newCand = cand.exist(Env.union(sys.getAuxFields()));
			cand.free();
			cand = newCand;
		}
		return cand;
	}

}
