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

import java.util.ArrayList;
import java.util.List;

import tau.smlab.syntech.cores.util.Checker;
import tau.smlab.syntech.games.util.AbstractDdmin;

/**
 * This is a general algorithm for all cores and global cores computation (I named it "PUNCH algorithm")
 * We use ddmin as a default to find a core according to an abstract check method given by a checker, and remove elements one by one to get to the next core
 * The check must be monotone.
 * The core can be found in other ways than ddmin if one overrides "minimize" - must be a local minimum
 * Memoization using lists of known results is used in order to avoid unnecessary checks.
 * 
 * Important! Order on T and List<T> for Memoization (implemented in Checker) is defined with toString representation and assumes the string is different for different values
 * 
 * 
 * @author shalom
 *
 * @param <T>
 */
public abstract class AllCoresPunchAlgorithm<T> extends AllCoresBase<T> {

	protected Checker<T> checker = null;
	
	public AllCoresPunchAlgorithm(Checker<T> c) {
		checker = c;
	}
	
	public int checks() {
		return checker.getChecks();
	}
	
	public int actualChecks() {
		return checker.getActualChecks();
	}
	
	public void computeAllCores(List<T> superSet) {
		start();
		computeCoresWithBase(superSet, new ArrayList<T>());
		end();
	}

	protected void computeCoresWithBase(List<T> superSet, List<T> base) {
		List<T> aCore = findCore(new ArrayList<T>(superSet), base);
		List<T> continuing = new ArrayList<T>();
		List<T> newBase = new ArrayList<T>(base);
		
		// compute aCore\base
		List<T> coreWithoutBase = new ArrayList<T>(aCore);
		coreWithoutBase.removeAll(base);

		for (T elem : coreWithoutBase) {
			(acCheck(punch(superSet, elem)) ? continuing : newBase).add(elem);
		}
		
		setIntersection(newBase);

		for (T contElem : continuing) {
			computeCoresWithBase(punch(superSet, contElem), newBase);
		}
	}

	/**
	 * lookup a core, and run minimize and save it if not found
	 * @param set	Superset
	 * @param base  These elements are given as part of the future core
	 * @return
	 */
	protected List<T> findCore(List<T> set, List<T> base) {
		List<T> found = null;
		for (List<T> core : data.getAllCores()) {
			if (set.containsAll(core)) {
				found = core;
			}
		}
		
		if (found==null) {
			found = new ArrayList<T>(minimize(set, base));
			found.addAll(base);

			registerCore(found);
			checker.register(true, found); // cores are added to the set of positives for memoization
		}
		
		return found;
	}
	
	
	/**
	 * Returns a new list without one element
	 * 
	 * @param set
	 * @param without
	 * @return
	 */
	private List<T> punch(List<T> set, T without) {
		List<T> ret = new ArrayList<T>(set);
		ret.remove(without);
		return ret;
	}
	
	/**
	 * Given a base set which must be included in the core, this method provides a minimization of the elements in set\base
	 * This should indicate a local minimum - a core, which is the minimized set with the base
	 * assumes that check is monotonic.
	 * The default is ddmin
	 * 
	 * @param set 	A set of elements for which we want a core
	 * @param base	A subset of set which is also a subset of some core
	 * @return		A minimization of set\base with base elements granted. by adding base this is a core
	 */
	protected List<T> minimize(List<T> set, List<T> base) {
		return ddmin(set, base);
	}
	
	/**
	 * Use standard ddmin for finding a core when a subset is given in base
	 * 
	 * @param set
	 * @param base
	 * @return
	 */
	protected List<T> ddmin(List<T> set, List<T> base) {

		AbstractDdmin<T> minimizer = new AbstractDdmin<T>() {
			@Override
			protected boolean check(List<T> part) {
				List<T> baseAndPart = new ArrayList<T>(base);
				baseAndPart.addAll(part);					
				return acCheck(baseAndPart);
			}				
		};
		set.removeAll(base);
		return minimizer.minimize(set);
	}
	
	  /**
	   * Check method for detecting a subset that fulfills the criterion.
	   * Assumed to be monotonic.
	   * 
	   * @param part
	   * @return
	   */
	protected boolean acCheck(List<T> part) {
		return checker.wrappedCheck(part);
	}

}
