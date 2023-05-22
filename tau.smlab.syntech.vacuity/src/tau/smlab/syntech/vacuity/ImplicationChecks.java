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

package tau.smlab.syntech.vacuity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.jtlv.Env;


/**
 * 
 * @author shalom
 *
 */

public class ImplicationChecks extends Vacuity {

	public Map<Integer, List<Integer>> getEnvMap() {
		var bundle = bundle(gm.getEnvBehaviorInfo());
		return computeVacuous(bundle.keySet(), bundle, false);
	}
	
	public Map<Integer, List<Integer>> getSysMap() {
		var bundle = bundle(gm.getSysBehaviorInfo());
		return computeVacuous(bundle.keySet(), bundle, true);	
	}
	
	public static Map<Integer, List<Integer>> computeVacuous(Set<Integer> toCheck, Map<Integer, List<BehaviorInfo>> all, boolean sys) {
		Map<Integer, List<Integer>> result = new HashMap<Integer, List<Integer>>();

//		The following is a patch for safeties of "alw" to blend in with the usual "G" statements.
//		These are all unprimed so they are about the current state instead of the next state.
//		This allows them both to be implied by "G" and to imply "G".		
//		This is regardless of "alw" handled differently as both their elements in the module should be implied
//		They are boundled and detected as a vacuity only if both are implied by the combined premise sets.		
//		make all pairs (representing "alw") safeties unprime so they can imply and be implied by "G" elements
		all.values().stream().filter( item -> item.size()==2 ). // for all pairs
					forEach( item -> item.stream().filter( behave -> behave.isSafety()). // for safeties within pairs
							forEach(behave-> behave.safety = Env.unprime(behave.safety)) ); // unprime
	 	
	    long startTime = System.currentTimeMillis();
		outerloop:
		for (Integer b : toCheck) {
			List<BehaviorInfo> itemImplied = all.get(b);
			
			List<BehaviorInfo> withoutImplied = sys ? getSysPremiseSet(itemImplied) : getEnvPremiseSet(itemImplied);
			withoutImplied.removeAll(itemImplied);
			
			if (vacuityImplication(withoutImplied, itemImplied)) {
				result.put(b, coreOfBehavior(withoutImplied, all.get(b)));
				if (result.size()==1) {
					timeToFirst = System.currentTimeMillis() - startTime;
				}
				if (first) // this flag is only for stopping at first vacuity. Running times are computed either way.
					break outerloop;
			}
		}
		timeToAll = System.currentTimeMillis() - startTime;
		timeToFirst = result.isEmpty() ? timeToAll : timeToFirst;
		return result;
	}
}
