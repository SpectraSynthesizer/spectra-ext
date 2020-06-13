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

import tau.smlab.syntech.checks.BDDBuilder;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.jtlv.env.module.ModuleBDDField;

/**
 * Look for domain values that can't be reached because they are inconsistent with behaviors
 * 
 * @author shalom
 *
 */

public class UnreachableValues extends Vacuity {
	
	public UnreachableValues() {
		super();
	}
	
	public Map<BehaviorInfo, List<Integer>> envUnreachMap() {
		return computeUnreach(gm.getEnv().getNonAuxFields(), false);
	}
	
	public Map<BehaviorInfo, List<Integer>> sysUnreachMap() {
		return computeUnreach(gm.getSys().getNonAuxFields(), true);
	}
	
	private Map<BehaviorInfo, List<Integer>> computeUnreach(List<ModuleBDDField> fields, boolean sys) {
		Map<BehaviorInfo, List<Integer>> result = new HashMap<BehaviorInfo, List<Integer>>();

	    long startTime = System.currentTimeMillis();
	    BehaviorInfo aSafety = new BehaviorInfo(null, Env.TRUE(), null, null, null, 0, false); // use  a general safety for behavior extraction
		List<BehaviorInfo> behaviors = sys ? getSysPremiseSet(aSafety) : getEnvPremiseSet(aSafety);
		BDDBuilder.setBehaviors(behaviors);
		outerloop:
		for (ModuleBDDField f : fields) {
			DomainBehaviorIterator iter = new DomainBehaviorIterator(f);
			while (iter.hasNext()){
				BehaviorInfo toCheck = iter.next();
				if (vacuityImplication(behaviors, toCheck)) {
					result.put(toCheck, coreOfBehavior(behaviors, toCheck));
					if (result.size()==1) {
						timeToFirst = System.currentTimeMillis() - startTime;
					}
					if (first) // this flag is only for stopping at first vacuity. Running times are computed either way.
						break outerloop;
				}
			}
		}
		

		BDDBuilder.release();
		timeToAll = System.currentTimeMillis() - startTime;
		timeToFirst = result.isEmpty() ? timeToAll : timeToFirst;
		return result;		
	}
}
