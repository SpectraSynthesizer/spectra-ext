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

import java.util.ArrayList;
import java.util.List;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.PlayerModule;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.jtlv.lib.FixPoint;

/**
 * A class for the Computation of a set of justice assumptions
 * Code matching lines 7-10 in the GLASS algorithm pseudocode (Algorithm 3 in the symbolic repair paper).
 * 
 * @author ringert
 *
 */

public class BottomJustices {

	/**
	 * compute justice assumptions from non-empty lowest cycles
	 * 
	 * @param gm
	 * @param quantifyAux 
	 * @return
	 */
	public static List<BDD> bottomJusticeAsm(GameModel gm, boolean quantifyAux) {

		List<BDD> justiceAsms = new ArrayList<>();

		PlayerModule env = gm.getEnv();
		PlayerModule sys = gm.getSys();

		BDD dead = Env.FALSE();
		for (FixPoint p = new FixPoint(true); p.advance(dead);) {
			dead = env.controlStates(sys, dead);
		}

		if (sys.justiceNum() == 0) {
			sys.addJustice(Env.TRUE());
		}
		if (env.justiceNum() == 0) {
			env.addJustice(Env.TRUE());
		}
		
		// this part is modified from the Rabin game where z is dead
		for (int j = 0; j < sys.justiceNum(); j++) {
			BDD notJj = sys.justiceAt(j).not();

			BDD y = Env.TRUE();
			for (FixPoint iterY = new FixPoint(true); iterY.advance(y);) {
				BDD nextYandNotJj = env.controlStates(sys, y).andWith(notJj.id());

				y = Env.TRUE();
				for (int i = 0; i < env.justiceNum(); i++) {
					BDD pre = dead.id().orWith(env.justiceAt(i).and(nextYandNotJj));

					BDD x = Env.FALSE();
					for (FixPoint iterX = new FixPoint(false); iterX.advance(x);) {
						x = pre.id().orWith(notJj.id().andWith(env.controlStates(sys, x)));
					}
					y = y.and(x);
					pre.free();
				}
				nextYandNotJj.free();
			}

			BDD cand = y.andWith(dead.not());
			
			if (quantifyAux) {
				BDD newCand = cand.exist(Env.union(sys.getAuxFields()));
				cand.free();
				cand = newCand;
			}
			
			if (!cand.isZero()) {
				justiceAsms.add(cand.notWithDoms());
			}

			cand.free();
			notJj.free();
		}
		dead.free();

		return justiceAsms;
	}

}
