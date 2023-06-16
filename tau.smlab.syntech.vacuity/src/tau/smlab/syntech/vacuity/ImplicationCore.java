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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tau.smlab.syntech.checks.ddmin.AbstractDdmin;
import tau.smlab.syntech.gamemodel.BehaviorInfo;

/**
 * This class computes the local minimum of the behaviors needed to imply other behaviors, according to traceId.
 * In the implying behaviors we may have auxiliary behaviors that come from the implied. In this case we take them as a basis and compute the core on the others.
 *  
 * @author shalom
 *
 */


public class ImplicationCore extends AbstractDdmin<Integer> {
	
	private Map<Integer, List<BehaviorInfo>> implying;
	private List<BehaviorInfo> implied;
	private List<BehaviorInfo> base;

	
	public ImplicationCore(Map<Integer, List<BehaviorInfo>> implying, List<BehaviorInfo> imp) {
		this.implying = implying;
		this.implied = imp;
		base = new ArrayList<BehaviorInfo>();
		
		int traceOfImplied = implied.get(0).traceId;
		if (implying.containsKey(traceOfImplied)) {
			base.addAll(implying.get(traceOfImplied));
			implying.remove(traceOfImplied);
		}
	}
	
	protected boolean check(List<Integer> part) {
		List<BehaviorInfo> ofPart = new ArrayList<BehaviorInfo>(base);
		for (Integer i : part) {
			ofPart.addAll(implying.get(i));
		}
		return Vacuity.vacuityImplication(ofPart, implied);
	}

}
