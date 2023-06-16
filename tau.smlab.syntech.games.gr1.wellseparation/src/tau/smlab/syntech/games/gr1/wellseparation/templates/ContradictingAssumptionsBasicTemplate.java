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

package tau.smlab.syntech.games.gr1.wellseparation.templates;

import java.util.ArrayList;
import java.util.List;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.checks.ddmin.AbstractDdmin;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.jtlv.Env;

public class ContradictingAssumptionsBasicTemplate extends ContradictingAssumptionsTemplate {
	
	
	@Override
	protected boolean _detectSingleMatch(GameModel gm, boolean considerGuarantees) { 
		return detectMatch(gm, considerGuarantees, gm.getEnvBehaviorInfo());
	}
	
	private boolean detectMatch(GameModel gm, boolean considerGuarantees, List<BehaviorInfo> asms) {
		BDD nextConstraints = Env.TRUE();
		BDD nextConstraintsSafe = Env.TRUE();
		BDD presentConstraints = Env.TRUE();
		for(var asm : asms) {
			if (!asm.isSafety() || !hasEnvVariables(asm.getBdd()) || !hasFutureVariables(asm.getBdd())) 
				continue;
			
			BDD unprime = asm.getBdd()
					.forAll(gm.getEnv().modulePrimeVars())
					.forAll(gm.getSys().modulePrimeVars())
					.not()
					.and(Env.unprime(nextConstraintsSafe));
			
			if (!isFalse(presentConstraints.and(unprime)) && garsAllow(considerGuarantees, gm, presentConstraints.and(unprime))) {
				presentConstraints = presentConstraints.and(unprime);
				BDD prime = asm.getBdd().and(unprime).exist(gm.getSys().moduleUnprimeVars()).exist(gm.getEnv().moduleUnprimeVars());
				nextConstraints = nextConstraints.and(prime);
				if (isFalse(nextConstraints)) {
					return true;
				}
				if (hasSysVariables(unprime) && !isFalse(Env.unprime(prime).and(unprime)) && garsAllow(considerGuarantees, gm, Env.unprime(prime).and(unprime)))
					nextConstraintsSafe = nextConstraintsSafe.and(prime);
			
				unprime.free();
				prime.free();
			}
		}
		for (var asm : asms) {
			if (!asm.isJustice() || hasAuxVariables(asm.getBdd()))
				continue;
			BDD toPrime = asm.getBdd().forAll(gm.getSys().moduleUnprimeVars());
			if (Env.prime(toPrime).and(nextConstraintsSafe).isZero() && garsAllow(considerGuarantees, gm, presentConstraints.and(toPrime))) {
				System.out.println(asm.getBdd() + "\ntoPrime: " + toPrime + "\nsafe: " + nextConstraintsSafe);
				return true;
			}
		}
		return false;
	}
	
	@Override
	public List<BehaviorInfo> _getCore(GameModel gm, boolean considerGuarantees) {
		class Minimizer extends AbstractDdmin<BehaviorInfo>{
			@Override
			protected boolean check(List<BehaviorInfo> part) {
				return detectMatch(gm, considerGuarantees, part);
			}
		}
		return new Minimizer().minimize(gm.getEnvBehaviorInfo());
	}
	
	private boolean garsAllow(boolean checkGuarantees, GameModel gm, BDD bdd) {
		//if (!checkGuarantees)
			//return true;
		bdd = Env.prime(bdd);
		return !garConstraints.and(gm.getSys().initial()).and(bdd).isZero();
	}
	
	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "T3 (basic)";
	}

}
