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

package tau.smlab.syntech.games.gr1.wellseparation;

import java.util.ArrayList;
import java.util.List;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.bddgenerator.BDDGenerator;
import tau.smlab.syntech.bddgenerator.BDDGenerator.TraceInfo;
import tau.smlab.syntech.checks.CouldAsmHelp;
import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.ModuleException;
import tau.smlab.syntech.gamemodel.PlayerModule;
import tau.smlab.syntech.gamemodel.PlayerModule.TransFuncType;
import tau.smlab.syntech.games.AbstractGamesException;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker.EnvSpecPart;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker.Positions;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker.Systems;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.jtlv.lib.FixPoint;

public class WellSeparationRepair {

	private GameInput gi;
	private GameModel gm;
	
	public WellSeparationRepair(GameInput gameInput) {
	    gi = gameInput;
	    gm = BDDGenerator.generateGameModel(gi, TraceInfo.ALL,true,TransFuncType.DECOMPOSED_FUNC);
	  }
	
	public WellSeparationRepair(GameModel gm) {
		this.gm = gm;
	}
	
	public BDD getRepair() {
		PlayerModule env = gm.getEnv();
		PlayerModule sys = gm.getSys();
		
		BDD c = collaborativeWin(gm, true, true);
		BDD notNextC = Env.prime(c).not();
		BDD leaveC = sys.trans().and(notNextC);
		notNextC.free();
		BDD outOfC = leaveC.forAll(env.modulePrimeVars());
		leaveC.free();
		
		BDD fromCLeaveC = c.andWith(outOfC);
		BDD asm = fromCLeaveC.notWithDoms();
		fromCLeaveC.free();
		return asm;
	}
	
	public BehaviorInfo fixSafety() {
		BehaviorInfo ans = new BehaviorInfo();
		BDD colAll = collaborativeWin(gm, true, true);
		if (!colAll.isZero()) {
			System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ REPAIR ANALYSIS BEGIN ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
			System.out.println("found a safety assumption that ensures satisfaction of all assumptions and guarantees");
			//System.out.println("G[" + colAll + "]");
			System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ REPAIR ANALYSIS END ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
		    ans.safety = colAll;
		    return ans;
		}
		BDD colAlmostAll = collaborativeWin(gm, true, false);
		if (!colAlmostAll.isZero()) {
			System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ REPAIR ANALYSIS BEGIN ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
			System.out.println("found a safety assumption that ensures all assumptions and safety guarantees");
			//System.out.println("G[" + colAlmostAll + "]");
			System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ REPAIR ANALYSIS END ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
		    ans.safety = colAlmostAll;
		    return ans;
		}
	    BDD colSafe = collaborativeWin(gm, false, false);
	    if (!colSafe.isZero()) {
	    	System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ REPAIR ANALYSIS BEGIN ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
			System.out.println("found a safety assumption that ensures safety separation but not justice separation:");
			//System.out.println("G[" + colSafe + "]");
			System.out.println("fixing the justice separation requires removing an assumption from the assumption core");
			System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ REPAIR ANALYSIS END ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
		    ans.safety = colSafe;
		    return ans;
	    }
	    System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ REPAIR ANALYSIS BEGIN ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
		System.out.println("no addition of safety assumtion can ensure well separation. need to remove an assumption from the assumption core");
		System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ REPAIR ANALYSIS END ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
	    return ans;
	}
	
	private BDD collaborativeWin(GameModel m, boolean includeEnvJustice, boolean includeSysJustice) {
		BDD trans = m.getSys().trans().and(m.getEnv().trans());
		List<BDD> buchis = new ArrayList<BDD>();
		if (includeEnvJustice) {
			for (int i = 0; i < m.getEnv().justiceNum(); i++) {
				buchis.add(m.getEnv().justiceAt(i));
			}
		}
		if (includeSysJustice) {
			for (int i = 0; i < m.getSys().justiceNum(); i++) {
				buchis.add(m.getSys().justiceAt(i));
			}
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
