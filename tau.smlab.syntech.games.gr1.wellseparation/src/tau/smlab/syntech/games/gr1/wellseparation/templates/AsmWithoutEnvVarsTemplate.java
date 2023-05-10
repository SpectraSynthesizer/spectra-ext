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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDVarSet;
import tau.smlab.syntech.bddgenerator.BDDGenerator;
import tau.smlab.syntech.bddgenerator.BDDGenerator.TraceInfo;
import tau.smlab.syntech.gameinput.model.Constraint;
import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.PlayerModule.TransFuncType;
import tau.smlab.syntech.games.controller.symbolic.SymbolicController;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.jtlv.env.module.ModuleBDDField;

public class AsmWithoutEnvVarsTemplate extends WellSeparationTemplate{

	@Override
	protected boolean _detectSingleMatch(GameModel gm, boolean considerGuarantees) {
		return findBadAsm(gm, considerGuarantees) != null;
	}

	@Override
	protected SymbolicController _getController(GameModel gm, boolean considerGuarantees) {
		BehaviorInfo badAsm = findBadAsm(gm, considerGuarantees);
		if (badAsm != null)
			return new SymbolicController(badAsm.getBdd().not(), badAsm.getBdd().not());
		return null;
	}
	
	@Override
	public List<BehaviorInfo> _getCore(GameModel gm, boolean considerGuarantees) {
		BehaviorInfo badAsm = findBadAsm(gm, considerGuarantees);
		if (badAsm != null)
			return new ArrayList<>() {{add(badAsm);}};
		return null;
	}
	
	private BehaviorInfo findBadAsm(GameModel gm, boolean considerGuarantees) {
		outer_loop:
		for (var asm : gm.getEnvBehaviorInfo()) {
			BDD bdd = asm.getBdd();
			if (hasSysVariables(bdd) && !hasEnvVariables(bdd) && !hasAuxVariables(bdd) && garsAllow(considerGuarantees, gm, bdd.not()))
				return asm;
			
			if (!hasEnvVariables(bdd) && hasAuxVariables(bdd)) {
				BDDVarSet lookingForVars = Env.TRUE().getFactory().emptySet();
				for (ModuleBDDField f : gm.getSys().getAuxFields()) {
					if (!f.support().intersect(bdd.support()).isEmpty() || !f.other().support().intersect(bdd.support()).isEmpty()) {
						lookingForVars.unionWith(f.support().id());
						lookingForVars.unionWith(f.other().support().id());
					}
				}
				for (var auxAsm : gm.getAuxBehaviorInfo()) {
					if (!auxAsm.getBdd().support().intersect(lookingForVars).isEmpty() && hasEnvVariables(auxAsm.getBdd()))
						continue outer_loop;
				}
				return asm;
			}
			
		}
		return null;
	}
	
	private boolean garsAllow(boolean checkGuarantees, GameModel gm, BDD bdd) {
		if (!checkGuarantees)
			return true;
		bdd = hasFutureVariables(bdd) ? bdd : Env.prime(bdd);
		return !checkGuarantees || !gm.getSys().trans().and(gm.getSys().initial()).and(bdd).isZero();
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "T1";
	}

	@Override
	protected String _getCauseDescription() {
		// TODO Auto-generated method stub
		return "There exists an assumptions that references no environment variables. The environment has no control over the satisfaction of the assumption, which causes non-well-separation";
	}

}
