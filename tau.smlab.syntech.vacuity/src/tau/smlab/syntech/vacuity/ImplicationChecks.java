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

import tau.smlab.syntech.gamemodel.BehaviorInfo;


/**
 * 
 * @author shalom
 *
 */

public class ImplicationChecks extends Vacuity {

	public Map<Integer, List<Integer>> getEnvMap() {			
		return computeVacuous(bundle(gm.getEnvBehaviorInfo()), false);
	}
	
	public Map<Integer, List<Integer>> getSysMap() {
		return computeVacuous(bundle(gm.getSysBehaviorInfo()), true);	
	}
	
	protected static Map<Integer, List<Integer>> computeVacuous(Map<Integer, List<BehaviorInfo>> implied, boolean sys) {
		Map<Integer, List<Integer>> result = new HashMap<Integer, List<Integer>>();

	    long startTime = System.currentTimeMillis();
		outerloop:
		for (Integer b : implied.keySet()) {
			List<BehaviorInfo> itemImplied = implied.get(b);
			assert(itemImplied.size()==1); // I assume there is only one behavior info with the trace in the implied side
			
			List<BehaviorInfo> withoutImplied = sys ? getSysPremiseSet(itemImplied.get(0)) : getEnvPremiseSet(itemImplied.get(0));
			withoutImplied.removeAll(itemImplied);
			
			if (vacuityImplication(withoutImplied, itemImplied.get(0))) {
				result.put(b, coreOfBehavior(withoutImplied, implied.get(b)));
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
