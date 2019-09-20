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
import tau.smlab.syntech.checks.CouldAsmHelp;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.PlayerModule;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.jtlv.lib.FixPoint;


/**
 * A class for the Computation of a safety assumption
 * Code matching lines 1-3 in the GLASS algorithm pseudocode (Algorithm 3 in the symbolic repair paper).
 * 
 * @author ringert
 *
 */

public class NonRestrictiveSafety {

	/**
	 * Compute a weakest non-restrictive safety assumption following Chatterjee,
	 * Henzinger, Jobstmann 2008: Environment Assumptions for Synthesis, Sect. 4.2
	 * 
	 * Changed cooperative satisfiability to a stronger one that satisfies all asms
	 * and gars
	 * 
	 * Special case: if there is no collaborative satisfaction then we return FALSE as an assumption
	 * 
	 * @param gm
	 * @param quantifyAux whether auxiliary variables should be existentially quantified
	 * @return
	 */
	public static BDD weakestNonRestrictiveSafety(GameModel gm, boolean quantifyAux) {
		BDD c = collaborativeWin(gm);
		
		if (c.isZero()) {
			return c;
		}
		
		PlayerModule env = gm.getEnv();
		PlayerModule sys = gm.getSys();

		// compute environment choices that leave c
		BDD notNextC = Env.prime(c).not();
		BDD leaveC = sys.trans().imp(notNextC);
		notNextC.free();
		BDD outOfC = env.trans().id().andWith(leaveC.forAll(sys.modulePrimeVars()));
		leaveC.free();
		
		BDD fromCLeaveC = c.andWith(outOfC);
		BDD asm = fromCLeaveC.notWithDoms();
		fromCLeaveC.free();
		
		if (!quantifyAux) {
			return asm;
		}
		
		BDD res = asm.exist(Env.union(sys.getAuxFields()));
		asm.free();

		return res;
	}

	/**
	 * Computes the winning states of collaboratively playing a Buchi game of all
	 * assumptions and all guarantees
	 * 
	 * @param m
	 * @return
	 */
	public static BDD collaborativeWin(GameModel m) {
		BDD trans = m.getSys().trans().and(m.getEnv().trans());
		List<BDD> buchis = new ArrayList<BDD>();
		for (int i = 0; i < m.getSys().justiceNum(); i++) {
			buchis.add(m.getSys().justiceAt(i));
		}
		for (int i = 0; i < m.getEnv().justiceNum(); i++) {
			buchis.add(m.getEnv().justiceAt(i));
		}
		
		if (buchis.isEmpty()) {
			buchis.add(Env.TRUE());
		}
		
		BDD Z = Env.TRUE();

		FixPoint zFix = new FixPoint(true);
		while (zFix.advance(Z)) {
			Z = Z.id(); // copy to leave BDD in FixedPoint untouched
			for (BDD buchi : buchis) {
				BDD start = buchi.id().andWith(Env.pred(trans, Z));
				BDD nextZ = Z.id().andWith(CouldAsmHelp.reachBwd(trans, start));
				Z.free();
				Z = nextZ;
				start.free();
			}
		}
		trans.free();
		return Z;
	}

}
