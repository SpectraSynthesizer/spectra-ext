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

package tau.smlab.syntech.repair.alur;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDVarSet;
import tau.smlab.syntech.bddgenerator.BDDGenerator;
import tau.smlab.syntech.gameinput.model.TypeDef;
import tau.smlab.syntech.gameinput.model.Variable;
import tau.smlab.syntech.gameinput.spec.VariableReference;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.jtlv.Env;

/**
 * @author moskovitch
 * @author novgorodov
 */

public class FinallyPattern extends Pattern {
	
	protected List<AbstractFSTState> states;
	
	public FinallyPattern(List<AbstractFSTState> states){
		this.states = states;
	}
	
	@Override
	public String toString(){
		String pattern="";
		for(AbstractFSTState s : states){
			pattern += "|"+s.getID();
		}
		pattern = pattern.substring(1) +")";
		return "F("+pattern;
	}

	@Override
	public BehaviorInfo replace(HashMap<String, ArrayList<String>> vars, GameModel model) {
		
		BDDVarSet varset = Env.getEmptySet();
		varset.unionWith(Env.union(model.getSys().getAuxFields()));
		varset.unionWith(Env.union(model.getSys().getNonAuxFields()));
		// filtering out variable forbidden by the user
		for(String v : vars.get("P2"))
			varset.unionWith(BDDGenerator.createBdd(new VariableReference(new Variable(v, new TypeDef())), -1).toVarSet());
		BDD bdd = Env.FALSE();
		
		for(AbstractFSTState s : states){
			bdd.orWith(s.getStateI().getData().id());
		}
		bdd = bdd.exist(varset);
		
		BehaviorInfo bi = new BehaviorInfo();
		// when negating a BDD the domain has to be considered if it doesn't include all values. Make the negation consider the doms
		// since safety is always used in a way that excludes values outside domains it won't cause noticable bugs, but the BDD in the fix may hold unrelated values
		bi.safety = bdd.notWithDoms();
		return bi;
	}
	
	@Override
	public List<AbstractFSTState> getStates() {
		return states;
	}

}
