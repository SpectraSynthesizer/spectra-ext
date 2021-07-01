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

package tau.smlab.syntech.cores.domainagnostic;

/**
 * Abstract QuickXplain algorithm implemented by Roee Sinai for testing of unrealizable cores algorithms
 * This is a domain-agnostic local minimizer like ddmin and LinearMin
 * 
 * @author Roee Sinai
 */

import tau.smlab.syntech.cores.interfaces.MonotonicCheckMinimizer;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractQuickXplain<T> extends MonotonicCheckMinimizer<T> {
	public List<T> minimize(List<T> analyzed) {
		//first see if the property holds for the empty set because minimization assumes the core is not of size zero.
		if (check(new ArrayList<T>())) {
			return new ArrayList<T>();
		}
		return minimize(analyzed, new ArrayList<T>());
	}
	
	public List<T> minimize(List<T> analyzed, List<T> background) {
		if (analyzed.isEmpty()) {
			return new ArrayList<T>();
		}
		List<T> result = QuickXPlain(analyzed, background, background);
		// For the case the whole set satisfies the constrains 
		if (result.size() == analyzed.size() && !check(union(analyzed, background))) {
			return new ArrayList<T>();
		}
		return result;
	}
	
	private List<T> QuickXPlain(List<T> analyzed, List<T> background, List<T> checkSet) {
		if (!checkSet.isEmpty() && check(background)) {
			// Background is already enough
			dispose(analyzed);
			return new ArrayList<T>();
		}
		if (analyzed.size() == 1) {
			conserve(analyzed);
			return analyzed;
		}
		int k = analyzed.size() / 2;
		List<T> a1 = analyzed.subList(0, k), a2 = analyzed.subList(k, analyzed.size());
		List<T> res2 = QuickXPlain(a2, union(a1, background), a1);
		List<T> res1 = QuickXPlain(a1, union(res2, background), res2);
		return union(res1, res2);
	}
	
	private List<T> union(List<T> a, List<T> b) {
		List<T> res = new ArrayList<T>(a);
		res.addAll(b);
		return res;
	}
	
	abstract protected boolean check(List<T> part);
	
	/**
	 * elements that will be a part of the core.
	 * @param elements
	 */
	protected void conserve(List<T> elements) {
		
	}
	
	/**
	 * elements that won't be a part of the core.
	 * @param elements
	 */
	protected void dispose(List<T> elements) {
		
	}
}
