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
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;

/**
 * The class GlobalCoreUtility provides a computation of all cores and 
 * a global minimum core by using simple recursion
 * We employ a top down approach.
 * We use memoization in order to avoid unnecessary realizability check.
 * 
 * @author shalom
 *
 */


public abstract class GlobalCoreUtility<T> extends AllCoresBase<T> {
	private HashMap<List<T>, Boolean> results = null;
	private int checks = 0;
	private int actualChecks = 0;
	
	/**
	 * This enum is for lookup results
	 * NONE means we know nothing.
	 * NEG means we know it is negative since we checked the set or some of its supersets with that result
	 * POS_FOUND means both that we know positivity and we checked the specific set so no need to work on subsets
	 * UNREL_UNFOUND means we deduce positivity from a subset, but we haven't encountered our set before so it may contain unknown cores
	 *
	 */
	private enum LookupResult { 
		NONE, NEG, POS_FOUND, POS_UNFOUND;
		
		/**
		 * The result of the lookup was positive
		 * @return
		 */
		public boolean pos() {
			return this==POS_FOUND || this==POS_UNFOUND;
		}
		
		/**
		 * No data or the original lookup for the positive set showed we haven't looked into it before
		 * @return
		 */
		public boolean unknown() {
			return this==NONE || this==POS_UNFOUND;
		}
	}

	public GlobalCoreUtility() {
		results = new HashMap<List<T>, Boolean>();
		checks = 0;
		actualChecks = 0;
	}
	
	/**
	 * report number of checks - filling an abstract method
	 */
	public int checks() {
		return checks;
	}
	
	/**
	 * report number of actual checks - filling an abstract method
	 */
	public int actualChecks() {
		return actualChecks;
	}
	
	public void computeAllCores(List<T> superSet) {
		start();
		topDown(superSet);
		end();
	}

	/**
	 * recursively compute for all subsets of the argument
	 * 
	 * @param superSet
	 */
	
	private void topDown(List<T> superSet) {
		boolean isCore = true;
		
		ArrayList<T> subSet = null;
		for (T b : new ArrayList<T>(superSet)) {
			subSet = new ArrayList<T>(superSet);
			subSet.remove(b);
			LookupResult r = lookup(subSet);
			if (r.pos() || (r==LookupResult.NONE && actualCheck(subSet))) {
				isCore = false; // we have a pos subset so the superset cannot be a core
				if (r.unknown()) { // we haven't looked into this pos set before
					topDown(subSet);
				}
			}
		}
		if (isCore) {
			registerCore(superSet);
		}
	}
	/** 
	 * 
	 * @param look up using old results
	 * @return do we know this already from other checks? do we have this specific set checked?
	 */
	private LookupResult lookup(List<T> set) {
		checks++;

		LookupResult r = LookupResult.NONE;

		Iterator<Map.Entry<List<T>, Boolean>> iter = results.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<List<T>, Boolean> entry = iter.next();
			if (!entry.getValue() && entry.getKey().containsAll(set)) { // if set is contained in a negative set - negatuve
				r = LookupResult.NEG;
				break;
			} else if (entry.getValue() && set.containsAll(entry.getKey())) { // if set contains a positive set - positive. if also vice versa, it is the exact one
				r = entry.getKey().containsAll(set) ? LookupResult.POS_FOUND : LookupResult.POS_UNFOUND;
				break;
			}  
		}
		return r;
	}
	
	private boolean actualCheck(List<T> part) {
		actualChecks++;
		boolean result = acCheck(part);
		results.put(part, result);
		return result;
	}
	
	/**
	 * Abstract check method for detecting a subset that fulfills the criterion.
	 * Assumed to be monotonic.
	 * 
	 * @param part
	 * @return
	 */
	protected abstract boolean acCheck(List<T> part);
}
