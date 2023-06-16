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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDDomain;
import tau.smlab.syntech.bddgenerator.BDDGenerator;
import tau.smlab.syntech.bddgenerator.BDDGenerator.TraceInfo;
import tau.smlab.syntech.checks.ddmin.AbstractDdmin;
import tau.smlab.syntech.gameinput.model.Constraint;
import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gameinput.model.Variable;
import tau.smlab.syntech.gameinput.spec.PrimitiveValue;
import tau.smlab.syntech.gameinput.spec.Spec;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.PlayerModule.TransFuncType;
import tau.smlab.syntech.games.controller.symbolic.SymbolicController;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.jtlv.env.module.ModuleBDDField;

public class ContradictingAssumptionsTemplate extends WellSeparationTemplate {
	
	
	private boolean run(GameModel gm, boolean considerGuarantees, List<BehaviorInfo> usedAssumptions) {
		BDD nextConstraints = Env.TRUE();
		BDD nextConstraintsSafe = Env.TRUE();
		BDD presentConstraints = Env.TRUE();
		List<Set<BDD>> nextMultiValuedConstraints = new ArrayList<>() {{add(new HashSet<>() {{add(Env.TRUE());}});}};
		List<BDD> presentMultiValuedConstraints = new ArrayList<>() {{add(Env.TRUE());}};
		List<BehaviorInfo> toIterate = new ArrayList<>(usedAssumptions);
		toIterate.addAll(gm.getAuxBehaviorInfo());
		for(var asm : toIterate) {
			if (!asm.isSafety() || !hasEnvVariables(asm.getBdd()) || !hasFutureVariables(asm.getBdd())) 
				continue;
			
			BDD unprime = asm.getBdd()
					.forAll(gm.getEnv().modulePrimeVars())
					.forAll(gm.getSys().modulePrimeVars())
					.not()
					.and(Env.unprime(nextConstraintsSafe));
			if (!isFalse(presentConstraints.and(unprime)) && garsAllow(considerGuarantees, gm, presentConstraints.and(unprime), false)) {
				if (hasSysFutureVariables(asm.getBdd())) {
					Set<BDD> additionalMulti = new HashSet<>();
					for (int i = 0; i < presentMultiValuedConstraints.size(); i++) {
						if (!isFalse(presentConstraints.and(presentMultiValuedConstraints.get(i)))) {
							for (BDD nextMulti : nextMultiValuedConstraints.get(i)) {
								BDD conjunct = asm.getBdd().and(unprime).and(Env.unprime(nextMulti)).simplify(gm.getSys().moduleUnprimeVars()).exist(gm.getEnv().moduleUnprimeVars());
								//System.out.println("conj = " + asm.getBdd().simplify(gm.getSys().moduleUnprimeVars()));
								if (!conjunct.isZero()) {
									additionalMulti.add(conjunct);
								}
							}
						}
					}
					nextMultiValuedConstraints.add(additionalMulti);
					presentMultiValuedConstraints.add(unprime.id());
					continue;
				}
				BDD prime = asm.getBdd().and(unprime).exist(gm.getSys().moduleUnprimeVars());
				if (prime.support().getDomains().length == 2 &&
					(prime.support().getDomains()[0].getName()+"'").equals(prime.support().getDomains()[1].getName()) &&
					isTrue(prime.exist(gm.getEnv().moduleUnprimeVars())) && 
					isFalse(prime.forAll(gm.getEnv().moduleUnprimeVars())) &&
					isTrue(prime.and(prime.support().getDomains()[0].ithVar(0)).exist(gm.getEnv().moduleUnprimeVars()).biimp(prime.support().getDomains()[1].ithVar(0)))) {

					Set<BDD> additionalMulti = new HashSet<>();
					for (int i = 0; i < prime.support().getDomains()[1].size().intValue(); i++) {
						BDD current = prime.support().getDomains()[1].ithVar(i);
						additionalMulti.add(current);
					}
					nextMultiValuedConstraints.add(additionalMulti);
					presentMultiValuedConstraints.add(unprime.id());
				}
				else {
					presentConstraints = presentConstraints.and(unprime);
					prime = prime.exist(gm.getEnv().moduleUnprimeVars());
					nextConstraints = nextConstraints.and(prime);
					if (isFalse(nextConstraints)) {
						return true;
					}
					if (hasSysVariables(unprime) && !isFalse(Env.unprime(prime).and(unprime)) && garsAllow(considerGuarantees, gm, Env.unprime(prime).and(unprime), false))
						nextConstraintsSafe = nextConstraintsSafe.and(prime);
				}
				unprime.free();
				prime.free();
			}
		}
		for (int i = 0; i < presentMultiValuedConstraints.size(); i++) {
			if (!isFalse(presentConstraints.and(presentMultiValuedConstraints.get(i))) &&
					garsAllow(considerGuarantees, gm, presentConstraints.and(presentMultiValuedConstraints.get(i)), false)){
				for (BDD bdd : nextMultiValuedConstraints.get(i)) {
					if (isFalse(nextConstraints.and(bdd))) {
						return true;
					}
				}
			}
		}
		for (var asm : usedAssumptions) {
			if (!asm.isJustice())
				continue;
			
			BDD toPrime = hasAuxVariables(asm.getBdd()) ? asm.getBdd() : asm.getBdd().forAll(gm.getSys().moduleUnprimeVars());
			if (Env.prime(toPrime).and(nextConstraintsSafe).isZero() && garsAllow(considerGuarantees, gm, presentConstraints.and(toPrime), true)) {
				return true;
			}
			for (int i = 0; i < nextMultiValuedConstraints.size(); i++) {
				if (garsAllow(considerGuarantees, gm, presentMultiValuedConstraints.get(i).and(toPrime).and(presentConstraints), true)) {
					for (BDD bdd : nextMultiValuedConstraints.get(i)) {
						if (bdd.and(Env.prime(toPrime)).isZero()) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}
	
	@Override
	protected boolean _detectSingleMatch(GameModel gm, boolean considerGuarantees) { 
		return run(gm, considerGuarantees, gm.getEnvBehaviorInfo());
	}
	
	private boolean garsAllow(boolean checkGuarantees, GameModel gm, BDD bdd, boolean safe) {
		if (!checkGuarantees)
			return true;
		bdd = Env.prime(bdd);
		if (safe)
			return !garConstraints.and(gm.getSys().initial()).and(bdd).isZero();
		else
			return !gm.getSys().trans().and(gm.getSys().initial()).and(bdd).isZero();
	}
	

	protected static boolean isFalse(BDD b) {
		BDD domains = Env.TRUE();
		for (BDDDomain d : b.support().getDomains()) {
			domains.andWith(d.domain());
		}
		boolean r = domains.imp(b.not()).isOne();
		domains.free();
		return r;
	}
	
	protected static boolean isTrue(BDD b) {
		BDD domains = Env.TRUE();
		for (BDDDomain d : b.support().getDomains()) {
			domains.andWith(d.domain());
		}
		boolean r = domains.imp(b).isOne();
		domains.free();
		return r;
	}

	@Override
	protected SymbolicController _getController(GameModel gm, boolean considerGuarantees) {
		BDD unconditionedPrime = Env.TRUE();
		BDD unconditionedUnprime = Env.TRUE();
		for(var asm : gm.getEnvBehaviorInfo()) {
			if (asm.isSafety()) {
				unconditionedPrime = unconditionedPrime.and(asm.safety.forAll(gm.getEnv().moduleUnprimeVars()));
				unconditionedUnprime = unconditionedUnprime.and(asm.safety.forAll(gm.getEnv().modulePrimeVars()));
			}
		}
		if (unconditionedPrime.equals(Env.FALSE()) && !unconditionedUnprime.equals(Env.FALSE())){
			return new SymbolicController(gm.getSys().initial(), unconditionedUnprime);
		}
		if (unconditionedUnprime.equals(Env.FALSE()))
			return null;
		for (var asm : gm.getEnv().getJustices()) {
			if (Env.prime(asm.forAll(gm.getSys().moduleUnprimeVars())).and(unconditionedPrime).equals(Env.FALSE()))
				return new SymbolicController(gm.getSys().initial(), 
						unconditionedUnprime.and(asm.forAll(gm.getEnv().moduleUnprimeVars())));
		}
		return null;
	}
	
	@Override
	public List<BehaviorInfo> _getCore(GameModel gm, boolean considerGuarantees) {
		class Minimizer extends AbstractDdmin<BehaviorInfo>{

			@Override
			protected boolean check(List<BehaviorInfo> part) {
				return run(gm, considerGuarantees, part);
			}
		}
		return new Minimizer().minimize(gm.getEnvBehaviorInfo());
	}
	
	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "T3 (full)";
	}

	@Override
	protected String _getCauseDescription() {
		// TODO Auto-generated method stub
		return "There exist some assumptions that cannot be mutually satisfied by the environment";
	}
	

}
