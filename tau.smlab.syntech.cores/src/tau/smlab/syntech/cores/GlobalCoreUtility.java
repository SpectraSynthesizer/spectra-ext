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

package tau.smlab.syntech.cores;

import java.util.List;
import java.util.HashMap;
import tau.smlab.syntech.gamemodel.BehaviorInfo;

/**
 * The class GlobalCoreUtility provides a computation of all cores and 
 * a global minimum core by using simple recursion
 * We employ a top down approach.
 * We use memoization in order to avoid duplicate realizability check.
 * 
 * @author shalom
 *
 */

import java.util.ArrayList;

import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.ModuleException;
import tau.smlab.syntech.gamemodel.PlayerModule;
import tau.smlab.syntech.games.gr1.GR1Game;
import tau.smlab.syntech.games.gr1.GR1GameMemoryless;

public class GlobalCoreUtility {
	private GameModel model = null;
	private HashMap<List<BehaviorInfo>, Boolean> results = null;
	private List<List<BehaviorInfo>> cores = new ArrayList<List<BehaviorInfo>>();
	List<BehaviorInfo> global = null;
	private List<BehaviorInfo> aux;
	private PlayerModule sys;	
	
	private int checkCounter = 0;
	
	/**
	 * This enum is for lookup results
	 * NONE means we know nothing.
	 * REL means we know realizability since we checked the set or some of its supersets with that result
	 * UNREL_FOUND means both that we know unrealizability and we checked the specific set so no need to work on subsets
	 * UNREL_UNFOUND means we deduce unrealizability from a subset, but we haven't encountered our set before so it may contain unknown cores
	 *
	 */
	private enum LookupResult { 
		NONE, REL, UNREL_FOUND, UNREL_UNFOUND;
		
		/**
		 * The result of the lookup was that we know unrealizability
		 * @return
		 */
		public boolean unrealizable() {
			return this==UNREL_FOUND || this==UNREL_UNFOUND;
		}
		
		/**
		 * The original lookup (for the unrealizable set) showed we haven't looked into it before
		 * @return
		 */
		public boolean unknown() {
			return this==NONE || this==UNREL_UNFOUND;
		}
	}
	
	public GlobalCoreUtility(GameModel m) {
		model = m;
	    sys = model.getSys();
	    aux = model.getAuxBehaviorInfo();
	    compute();
	}
	
	public List<List<BehaviorInfo>> getAllCores() {
		return cores;
	}	
	
	public List<BehaviorInfo> getGlobalCore() {
		return global;
	}

	/**
	 * returns the number of actual realizability checks for analysis purposes (may be redundant)
	 * @return
	 */
	public int getCheckCounter() {
		return checkCounter;
	}
	
	/** this computes all cores
	 * We assume that the input is non-realizable.
	 */
	private void compute() {
		results = new HashMap<List<BehaviorInfo>, Boolean>();
		List<BehaviorInfo> allGar = model.getSysBehaviorInfo();
		global = allGar;
		computeAllSubsets(allGar);
	}
	
	/**
	 * recursively compute for all subsets of the argument
	 * 
	 * @param superSet
	 */
	
	private void computeAllSubsets(List<BehaviorInfo> superSet) {
		boolean isCore = true;
		
		ArrayList<BehaviorInfo> subSet = null;
		for (BehaviorInfo b : superSet) {
			subSet = new ArrayList<BehaviorInfo>(superSet);
			subSet.remove(b);
			LookupResult r = lookup(subSet);
			if (r.unrealizable() || (r==LookupResult.NONE && !realizable(subSet))) {
				isCore = false; // we have an unrealizable subset so the superset cannot be a core
				if (r.unknown()) { // we haven't looked into this unrelizable set before
					computeAllSubsets(subSet);
				}
			}
		}
		if (isCore) {
			cores.add(superSet);
			if (superSet.size() < global.size()) {
				global = superSet;
			}
		}
	}
	
	/**
	 * Check for realizability for a subset of gars
	 * Remember the set and the result
	 * 
	 * @param set a set of gars to be checked
	 * @return
	 */
	private boolean realizable(List<BehaviorInfo> set) {
		if (set.equals(model.getSysBehaviorInfo())) { // we don't want to recheck the whole spec which we assume is unrealizable
			return false; // probably dead code but keep it safe
		}  else { // lets compute and store
			boolean result = garsCheck(set);
			checkCounter++;
			results.put(set, result);
			return result;
		}
	}
	
	/**
	 * Check realizability for part of the gars
	 * 
	 * @param part
	 * @return
	 */
	private boolean garsCheck(List<BehaviorInfo> part) {

	    try {
	      buildSys(part);
	    } catch (ModuleException e) {
	      throw new RuntimeException(e);
	    }

	    GR1Game gr1game = new GR1GameMemoryless(model);
	    boolean real = gr1game.checkRealizability();
	    gr1game.free();

	    return real;
	  }

	  /**
	   * resets and then adds guarantees to sys module
	   * 
	   * @param part set of gars to be checked
	   * @throws ModuleException
	   */
	  public void buildSys(List<BehaviorInfo> part) throws ModuleException {
	    sys.reset();

	    ArrayList<BehaviorInfo> gars = new ArrayList<BehaviorInfo>();
	    gars.addAll(aux);
	    gars.addAll(part);

	    for (BehaviorInfo gar : gars) {
	      if (gar.isInitial()) {
	        sys.conjunctInitial(gar.initial.id());
	      }
	      if (gar.isSafety()) {
	        sys.conjunctTrans(gar.safety.id());
	      }
	      if (gar.isJustice()) {
	        sys.addJustice(gar.justice.id(), gar.traceId);
	      }
	    }
	    sys.trans();
	  }
	  
	  /**
	   * 
	   * @param set set of gars to look up
	   * @return do we know this already from other checks? do we have this specific set checked?
	   */
	  private LookupResult lookup(List<BehaviorInfo> set) {
		  // first see if we have the set as is
		  if (results.keySet().contains(set)) {
			  return results.get(set) ? LookupResult.REL : LookupResult.UNREL_FOUND;
		  } else { // look for telling subsets or supersets
			  LookupResult r = LookupResult.NONE;
		   
			  for (List<BehaviorInfo> k : results.keySet()) {
				  if (results.get(k) && k.containsAll(set)) { // we already have a realizable superset 
					  r = LookupResult.REL;
					  break;
				  } else if (!results.get(k) && set.containsAll(k)) { // for unrealizable sets even if we have an unrel subset we may still have others
					  r = LookupResult.UNREL_UNFOUND;
					  break;
				  }  
			  }
			  return r;
		  }
	  }
}
