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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import tau.smlab.syntech.gameinputtrans.TranslationProvider;
import tau.smlab.syntech.gameinputtrans.translator.DefaultTranslators;
import tau.smlab.syntech.gameinputtrans.translator.Translator;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.PlayerModule.TransFuncType;
import tau.smlab.syntech.games.controller.symbolic.SymbolicController;
import tau.smlab.syntech.jtlv.Env;

public abstract class WellSeparationTemplate {
	


	protected static List<Integer> allEnvVars;
	protected static List<Integer> allSysVars;
	protected static List<Integer> primeEnvVars;
	protected static List<Integer> unprimeEnvVars;
	protected static List<Integer> primeSysVars;
	protected static List<Integer> unprimeSysVars;
	protected static List<Integer> auxVars;
	protected static List<Integer> realSysVars;
	protected static BDD garConstraints;
	
	private static WellSeparationTemplate[] allTemplates = new WellSeparationTemplate[] {
			new AsmWithoutEnvVarsTemplate(),
			new SafetyToStateInvTemplate(),
			new PrimeEnvVarsTemplate(),
			new ContradictingAssumptionsTemplate(),
		};
	
	public abstract String getName();
	
	protected abstract boolean _detectSingleMatch(GameModel gm, boolean considerGuarantees);
	
	protected abstract SymbolicController _getController(GameModel gm, boolean considerGuarantees);
	
	protected abstract List<BehaviorInfo> _getCore(GameModel gm, boolean considerGuarantees);
	
	protected abstract String _getCauseDescription();
	
	private static void init(GameModel gm, boolean considerGars) {
		allEnvVars = new ArrayList<>();
		allSysVars = new ArrayList<>();
		primeEnvVars = Arrays.stream(gm.getEnv().modulePrimeVars().toArray()).boxed().collect(Collectors.toList());
		unprimeEnvVars = Arrays.stream(gm.getEnv().moduleUnprimeVars().toArray()).boxed().collect(Collectors.toList());
		primeSysVars = Arrays.stream(gm.getSys().modulePrimeVars().toArray()).boxed().collect(Collectors.toList());
		unprimeSysVars = Arrays.stream(gm.getSys().moduleUnprimeVars().toArray()).boxed().collect(Collectors.toList());
		allEnvVars.addAll(primeEnvVars);
		allEnvVars.addAll(unprimeEnvVars);
		allSysVars.addAll(primeSysVars);
		allSysVars.addAll(unprimeSysVars);
		auxVars = new ArrayList<>();
		gm.getSys().getAuxFields().stream().forEach(f ->{
			auxVars.addAll(Arrays.stream(f.support().toArray()).boxed().collect(Collectors.toList()));
			auxVars.addAll(Arrays.stream(f.other().support().toArray()).boxed().collect(Collectors.toList()));
		});
		//auxVars = gm.getSys().getAuxFields().stream().flatMap(f -> Arrays.stream(f.support().toArray()).boxed()).collect(Collectors.toList());
		realSysVars = new ArrayList<>(allSysVars);
		realSysVars.removeAll(auxVars);
		garConstraints = Env.TRUE();
		for (var asm : considerGars ? gm.getSysBehaviorInfo() : gm.getAuxBehaviorInfo())
			garConstraints.andWith(asm.getBdd().forAll(gm.getSys().moduleUnprimeVars()).forAll(gm.getEnv().moduleUnprimeVars()));
		
		
	}
	
	
	public static boolean detectSingleMatch(GameModel gm) {
		return detectSingleMatch(gm , false);
	}
	
	public static boolean detectSingleMatch(GameModel gm, boolean considerGuarantees) {
		return detectSingleMatch(gm, considerGuarantees, allTemplates);
	}
	
	public static boolean detectSingleMatch(GameModel gm, WellSeparationTemplate[] templates) {
		return detectSingleMatch(gm, false, templates);
	}
	
	public static boolean detectSingleMatch(GameModel gm, boolean considerGuarantees, WellSeparationTemplate[] templates) {
		init(gm, considerGuarantees);
		
		for (WellSeparationTemplate t : templates) {
			if (t._detectSingleMatch(gm, considerGuarantees))
				return true;
		}
		return false;
	}
	
	public static String diagnose(GameModel gm, boolean considerGuarantees) {
		init(gm, considerGuarantees);
		
		for (WellSeparationTemplate t : allTemplates) {
			if (t._detectSingleMatch(gm, considerGuarantees)) {
				return String.format("The specification is not-well-separated.\n%s", t._getCauseDescription());
			}
		}
		return "The specification appears to be well-separated. However, the analysis is incomplete; consider using the complete diagnosis.";
	}
	
	public static SymbolicController getController(GameModel gm, boolean considerGuarantess, WellSeparationTemplate[] templates) {
		for (WellSeparationTemplate t : templates) {
			SymbolicController current = t._getController(gm, considerGuarantess);
			if (current != null)
				return current;
		}
		return null;
	}
	
	public static List<BehaviorInfo> getCore(GameModel gm){
		return getCore(gm, false);
	}
	
	public static List<BehaviorInfo> getCore(GameModel gm, boolean considerGuarantees){
		return getCore(gm, considerGuarantees, allTemplates);
	}
	
	public static List<BehaviorInfo> getCore(GameModel gm, WellSeparationTemplate[] templates){
		return getCore(gm, false, templates);
	}
	
	public static List<BehaviorInfo> getCore(GameModel gm, boolean considerGuarantees, WellSeparationTemplate[] templates) {
		init(gm, considerGuarantees);
		
		for (WellSeparationTemplate t : templates) {
			List<BehaviorInfo> current = t._getCore(gm, considerGuarantees);
			if (current != null)
				return current;
		}
		return null;
	}
	
	protected boolean varProfileIncludesVars(int[] profile, List<Integer> vars) {
		for (int i = 0; i < profile.length; i++) {
			if (vars.contains(i) && profile[i] > 0)
				return true;
		}
		return false;
	}
	
	protected List<Integer> getVars(int[] profile){
		List<Integer> ans = new ArrayList<>();
		for (int i = 0; i < profile.length; i++) {
			if (profile[i] > 0)
				ans.add(i);
		}
		return ans;
	}
	
	protected List<Integer> getAuxVars(int[] profile){
		List<Integer> ans = new ArrayList<>();
		for (int i = 0; i < profile.length; i++) {
			if (profile[i] > 0 && auxVars.contains(i))
				ans.add(i);
		}
		return ans;
	}
	
	protected boolean hasPresentVariables(BDD bdd) {
		return varProfileIncludesVars(bdd.varProfile(), unprimeEnvVars) ||
				varProfileIncludesVars(bdd.varProfile(), unprimeSysVars);
	}
	
	protected boolean hasFutureVariables(BDD bdd) {
		return varProfileIncludesVars(bdd.varProfile(), primeEnvVars) ||
				varProfileIncludesVars(bdd.varProfile(), primeSysVars);
	}
	
	protected boolean hasEnvVariables(BDD bdd) {
		return varProfileIncludesVars(bdd.varProfile(), primeEnvVars) ||
				varProfileIncludesVars(bdd.varProfile(), unprimeEnvVars);
	}
	
	protected boolean hasSysVariables(BDD bdd) {
		return varProfileIncludesVars(bdd.varProfile(), primeSysVars) ||
				varProfileIncludesVars(bdd.varProfile(), unprimeSysVars);
	}
	
	protected boolean hasAuxVariables(BDD bdd) {
		return varProfileIncludesVars(bdd.varProfile(), auxVars);
	}
	
	protected boolean hasEnvFutureVariables(BDD bdd) {
		return varProfileIncludesVars(bdd.varProfile(), primeEnvVars);
	}
	
	protected boolean hasSysFutureVariables(BDD bdd) {
		return varProfileIncludesVars(bdd.varProfile(), primeSysVars);
	}
	
	protected boolean hasRealSysVariables(BDD bdd) {
		return varProfileIncludesVars(bdd.varProfile(), realSysVars);
	}
	
	
	
}
