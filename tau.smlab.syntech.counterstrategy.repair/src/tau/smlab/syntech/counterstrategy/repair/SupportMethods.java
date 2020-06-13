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

package tau.smlab.syntech.counterstrategy.repair;

import java.util.ArrayList;
import java.util.List;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.bddgenerator.BDDGenerator;
import tau.smlab.syntech.bddgenerator.BDDGenerator.TraceInfo;
import tau.smlab.syntech.counterstrategy.model.JusticeViolationGraph;
import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.PlayerModule;
import tau.smlab.syntech.gamemodel.util.GameBuilderUtil;
import tau.smlab.syntech.games.gr1.GR1Game;
import tau.smlab.syntech.games.gr1.GR1GameExperiments;
import tau.smlab.syntech.games.gr1.GR1GameImplC;
import tau.smlab.syntech.games.gr1.unreal.DdminUnrealizableCore;
import tau.smlab.syntech.jtlv.BDDPackage;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.jtlv.env.module.ModuleBDDField;
import tau.smlab.syntech.jtlv.lib.FixPoint;
import tau.smlab.syntech.repair.BasicAssumption;
import tau.smlab.syntech.ui.preferences.PreferencePage;

/**
 * Support methods for repair classed
 * 
 * @author Shalom
 */

public class SupportMethods  {

/**
 * This is an implementation of a generalized Buchi model checking. <br>
 * 
 * <li>implements a check whether we reached a fixed point early</li>
 * <li>computes all winning states</li>
 * 
 * Checks if a Player module is consistent (has any valid traces)
 */
  public static boolean checkSat(PlayerModule m) {
	  
	if (m.trans().isZero() || m.initial().isZero()) {
		return false;
	}
	  
    BDD Z = Env.TRUE();
    // don't free on advance because we update for every justice
    int iterationsWithoutLoss = 0;
    while (iterationsWithoutLoss < m.justiceNum()) {
      for (int i = 0; i < m.justiceNum(); i++) {
        BDD justAndToWin = m.justiceAt(i).id().andWith(m.pred(Z));
        BDD X = justAndToWin.id(); // instead of starting from FALSE we start somewhere in the middle
        FixPoint fX = new FixPoint(true);
        while (fX.advance(X)) {
          X = justAndToWin.id().orWith(m.pred(X));
        }
        if (X.equals(Z)) {
          iterationsWithoutLoss++;
        } else {
          iterationsWithoutLoss = 0;
        }
        Z.free();
        justAndToWin.free();
        Z = X;
      }
    }
    BDD winIni = Z.and(m.initial());
    Z.free();
    boolean sat = !winIni.isZero();
    winIni.free();
    return sat;
  }
  
  /**
   * check realizability of a game model
   * 
   * @param m
   * @return
   */
  public static boolean isRealizable(GameModel m) {
	  boolean useMemory = GR1GameExperiments.WITH_MEMORY;
	  GR1GameExperiments.WITH_MEMORY = false; // no need for memory in these menu options because we only check for realizability
	  GR1Game gr1;		  
	  if (PreferencePage.getBDDPackageSelection().equals(BDDPackage.CUDD)) {
		  gr1 = new GR1GameImplC(m);
	  } else {
		  gr1 = new GR1GameExperiments(m);
	  }
	  boolean realizable = gr1.checkRealizability();
	  gr1.free();
	  GR1GameExperiments.WITH_MEMORY = useMemory;
	  return realizable;
  }

  public static void computeAndSetGurCore(GameModel model) {
	  DdminUnrealizableCore ucmin = new DdminUnrealizableCore(model);
	  List<BehaviorInfo> res = new ArrayList<BehaviorInfo>();
	  try {
		  res = ucmin.minimize(model.getSysBehaviorInfo());
		  GameBuilderUtil.buildSys(model, new ArrayList<BehaviorInfo>(res));
	  } catch (Exception e) {
		  e.printStackTrace();
	  }
  }
  
  /**
   * checks for, and removes unhelpful assumptions
   * assumes that the model already has a locally minimal set of gars
   * the algorithm is from cimatti et al. "Diagnostic information for realizability"
   * @param model
   */
  public static void removeUnhelpfulAssumptions(GameModel model) {
	  List<BehaviorInfo> assumList = new ArrayList<BehaviorInfo>(model.getEnvBehaviorInfo());
	  for (BehaviorInfo assum : assumList) {
		  removeIfUnhelpful(model, assum);
	  }
  }
  
  /**
   * assumes that the gars are minimal, and that model is unrealizable
   * checks if the gars are still minimal if we are without the env behavior.
   * @param model the model
   * @param b the env behavior to be checked
   * @return b is unhelpful
   */
   public static boolean removeIfUnhelpful(GameModel model, BehaviorInfo b) {
	  List<BehaviorInfo> garList = new ArrayList<BehaviorInfo>(model.getSysBehaviorInfo());
	  List<BehaviorInfo> asmList = new ArrayList<BehaviorInfo>(model.getEnvBehaviorInfo());

	  boolean answer = unhelpful(model, b);
	  if (!answer) {
		  GameBuilderUtil.buildEnv(model, asmList);			   // the assumption is helpful. keep it.
	  }
	  //restore the guars
	  GameBuilderUtil.buildSys(model, garList);
	  
	  return answer;

  }
  
  /**
   *  check if the core is unchanged. If so, the assumption is unhelpful
   *  the model is changed! the assumption is removed and gars too
   * @param model the model without the assumption, and a minimal set of gars of the model with the checked assumption
   * @param b the assumption to be checked
   * @return the assumption is unhelpful
   */
  private static boolean unhelpful(GameModel model, BehaviorInfo b) {
	  boolean answer = true;
	  List<BehaviorInfo> newGars = new ArrayList<BehaviorInfo>();
	  List<BehaviorInfo> garList = new ArrayList<BehaviorInfo>(model.getSysBehaviorInfo());
	  List<BehaviorInfo> asmList = new ArrayList<BehaviorInfo>(model.getEnvBehaviorInfo());

	  List<BehaviorInfo> newAsms = new ArrayList<BehaviorInfo>();
	  newAsms.addAll(asmList);
	  newAsms.remove(b);
	  GameBuilderUtil.buildEnv(model, newAsms);

	  
	  for (BehaviorInfo g : garList) {
		  newGars.clear();
		  newGars.addAll(garList);
		  newGars.remove(g);
		  
		  GameBuilderUtil.buildSys(model, newGars);

		  if (!isRealizable(model)) {
			  answer = false;
			  break;
		  }
	  }
	  
	  return answer;
  }
  
	/**
	 * Takes a BDD of a node, quantifies out system variables and primes the environment variables
	 * Useful for extraction of data from target nodes for transition repair assumptions
	 * Aux fields are assumed to have been removed
	 * 
	 * 
	 *  @param nBDD the BDD
	 *  @param sys the system module of the JVTS
	 *  @return the resulting BDD
	 */
	protected static BDD removeSysAndPrimeEnvVars(BDD nBDD, PlayerModule sys) {
		BDD result = nBDD.id();

		List<ModuleBDDField> sysVars = new ArrayList<ModuleBDDField>(sys.getAllFields());
// following line exist only because we assume the aux vars are already quantified out by quantifyOutAux
		sysVars.removeAll(sys.getAuxFields());	
		
		//quantify out all sys vars
		for (ModuleBDDField v : sysVars) {
			result = result.exist(v.support());
		}

		//prime env vars
		result = result.replace(Env.allCouplesPairing());

		return result;
	}
	
	/**
	 * 
	 * Given a bdd, we want to keep only the system and environment variables.
	 * Removes ASSUMPTION_RANK variable which is a result of the JVTS computation for cycle nodes
	 * Duplicates the BDD to avoid changing the JVTS.
	 * 
	 * @param b the bdd to quanify 
	 *  @param sys the system module of the JVTS
	 * @return BDD
	 */
	
	protected static BDD quantifyOutAux(BDD b, JusticeViolationGraph csg) {
		BDD result = b.id();
		
		PlayerModule env = csg.getEnv();
		List<ModuleBDDField> auxVars = new ArrayList<ModuleBDDField>(env.getAuxFields());

		PlayerModule sys = csg.getSys();
		auxVars.addAll(sys.getAuxFields());
		
		// quantify out all aux vars from the BDD.
		// This should also remove the vars from the BDD var set which is important for negations
		for (ModuleBDDField v : auxVars) {
			result = result.exist(v.support());
		}
		
		// quantify out only ASSUMPTION_RANK
/*		ModuleBDDField asmrnk = Env.getVar(JusticeViolationGraph.ASSUMPTION_RANK);
		if (asmrnk!=null) {
			result.exist(Env.getVar(JusticeViolationGraph.ASSUMPTION_RANK).support());
		}*/
		return result;
	}

	/**
	 * 
	 * 	 **** Needed only for initial assumptions which is not currently used, thus commented out. ***
	 * 
	 * Given a bdd, we want to keep only the env variables (remove sys including aux vars).
	 * Useful for extraction of data from init nodes for init repair assumptions
	 * Duplicates the BDD to avoid changing the JVTS.
	 * 
	 * 
	 * @param nBDD the bdd to quanify 
	 * @param csg JVTS to take vars from
	 * @return BDD
	 
	protected static BDD quantifyOutSysAndAux(BDD nBDD, JusticeViolationGraph csg) {
		BDD result = quantifyOutAux(nBDD, csg);

		List<ModuleBDDField> vars = new ArrayList<ModuleBDDField>(csg.getSys().getAllFields());
		
		//quantify out all sys vars
		for (ModuleBDDField v : vars) {
			result = result.exist(v.support());
		}
		return result;		
	}*/
	
	public static boolean validate(GameInput gi, List<BasicAssumption> additions) {
		GameModel model  = BDDGenerator.generateGameModel(gi, TraceInfo.ALL);
		
		for (BasicAssumption ba : additions) {
			BehaviorInfo bh = ba.translateToBehavior();
			ba.addToModel(model);
			model.addEnvBehaviorInfo(bh);
		}
		boolean valid = isRealizable(model);
		model.free();
		return valid;
	}

}
