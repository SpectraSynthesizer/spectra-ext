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
import tau.smlab.syntech.bddgenerator.BDDGenerator;
import tau.smlab.syntech.bddgenerator.BDDGenerator.TraceInfo;
import tau.smlab.syntech.gameinput.model.Constraint;
import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gameinput.model.Constraint.Kind;
import tau.smlab.syntech.gameinput.spec.Operator;
import tau.smlab.syntech.gameinput.spec.Spec;
import tau.smlab.syntech.gameinput.spec.SpecExp;
import tau.smlab.syntech.gameinput.spec.VariableReference;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.PlayerModule.TransFuncType;
import tau.smlab.syntech.games.controller.symbolic.SymbolicController;
import tau.smlab.syntech.jtlv.Env;

public class PrimeEnvVarsTemplate extends WellSeparationTemplate{

	@Override
	protected boolean _detectSingleMatch(GameModel gm, boolean considerGuarantees) {
		return findBadAsm(gm, considerGuarantees, gm.getEnvBehaviorInfo()) != null;
	}
	
	private BehaviorInfo findBadAsm(GameModel gm, boolean considerGuarantees, List<BehaviorInfo> asms) {
		BDD all = asms.stream().filter(asm -> asm.isSafety() || asm.isInitial())
				.map(asm -> asm.getBdd())
				.reduce(Env.TRUE(), (total, current) -> total.and(
						hasPresentVariables(current) ? current : current.and(Env.unprime(current))
					));
		for (BehaviorInfo asm : asms) {
			if (asm.isSafety() && hasEnvVariables(asm.getBdd()) && 
					hasSysVariables(asm.getBdd()) && !hasFutureVariables(asm.getBdd())){
				BDD envPart = asm.getBdd().not().exist(gm.getSys().moduleUnprimeVars());
				if (!envPart.isZero() && !all.imp(envPart.not()).isOne() && garsAllow(considerGuarantees, gm, envPart.and(asm.getBdd().not())))
					return asm;
			}
		}
		return null;
	}
	
	private boolean garsAllow(boolean checkGuarantees, GameModel gm, BDD bdd) {
		if (!checkGuarantees)
			return true;
		bdd = Env.prime(bdd);
		return !checkGuarantees || !gm.getSys().trans().and(gm.getSys().initial()).and(bdd).isZero();
	}

	@Override
	protected SymbolicController _getController(GameModel gm, boolean considerGuarantees) {
		BDD all = gm.getEnvBehaviorInfo().stream().filter(asm -> asm.isSafety() || asm.isInitial())
				.map(asm -> asm.getBdd())
				.reduce(Env.TRUE(), (total, current) -> total.and(
						hasPresentVariables(current) ? current : current.and(Env.unprime(current))
					));
		for (BehaviorInfo asm : gm.getEnvBehaviorInfo()) {
			if (asm.isSafety() && hasEnvVariables(asm.getBdd()) && 
					hasSysVariables(asm.getBdd()) && !hasFutureVariables(asm.getBdd())){
				BDD envPart = asm.getBdd().not().exist(gm.getSys().moduleUnprimeVars());
				if (!envPart.isZero() && !all.imp(envPart.not()).isOne() && garsAllow(considerGuarantees, gm, envPart.and(asm.getBdd().not())))
					return new SymbolicController(envPart.imp(asm.getBdd().not()), envPart.imp(asm.getBdd().not()));
			}
		}
		return null;
	}
	
	@Override
	public List<BehaviorInfo> _getCore(GameModel gm, boolean considerGuarantees) {
		BehaviorInfo badAsm = findBadAsm(gm, considerGuarantees, gm.getEnvBehaviorInfo());
		if (badAsm != null)
			return new ArrayList<>() {{add(badAsm);}};
		return null;
	}
	
	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "T2";
	}

	@Override
	protected String _getCauseDescription() {
		// TODO Auto-generated method stub
		return "There exists an assumption that references system and environment variables in the current state, and no environment variables in the next state. This assumption causes non-well-separation, since the environment plays before the system. Consider using the 'next' operator on environment variables.";
	}
	
}
