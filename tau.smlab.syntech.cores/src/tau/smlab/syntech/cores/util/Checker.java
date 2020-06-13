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

package tau.smlab.syntech.cores.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;


/**
 * This class performs checks, uses memoization, keeps statistics on actual and non-actual checks
 * Can be sent to various objects to the same time so memoization is mutual
 * The elements we remember are lists, and they are sorted for quick subset computation
 * We keep elements in sorted sets according to their size so we can check only relevant sizes for inclusion
 * 
 * Important! Order on T and List<T> is defined with toString representation and assumes the string is different for different values
 * 
 * @author shalom
 *
 */

public abstract class Checker<T> {
	Memoize mem = null;
	private int checks = 0;
	private int actualChecks = 0;
	
	/**
	 * Internal class for memoization
	 */
	private class Memoize {
		protected SortedSet<List<T>> pos = null;
		protected SortedSet<List<T>> neg = null; 
		
		protected Memoize() {
			//increasing in list size, break ties with comparison of toString
			pos = new TreeSet<List<T>>((a,b) -> (a.size()!=b.size()) ? a.size()-b.size() : cmp(a,b));
			//decreasing in list size, break ties with comparison of toString
			neg = new TreeSet<List<T>>((a,b) -> (a.size()!=b.size()) ? b.size()-a.size() : cmp(a,b)); 
		}
		
		protected void registerPos(List<T> toRegister) {
			pos.add(new ArrayList<T>(toRegister));
		}
		
		protected void registerNeg(List<T> toRegister) {
			neg.add(new ArrayList<T>(toRegister));
		}
		
		/**
		 * positives are ordered from smaller to bigger. We look for a positive which is a subset up to our size 
		 * 
		 * @param part
		 * @return
		 */
		protected boolean lookupPos(List<T> part) {
			List<T> curr = null;
			Iterator<List<T>> posIter = pos.iterator();
			while (posIter.hasNext() && part.size() >= (curr=posIter.next()).size()) {
				if (isSubset(curr, part)) {
					return true;
				}
			}
			return false;
		}
		
		/**
		 * negatives  are ordered from bigger to smaller. We look for a negative which is a superset down to our size 
		 * 
		 * @param part
		 * @return
		 */
		protected boolean lookupNeg(List<T> part) {
			List<T> curr = null;
			Iterator<List<T>> negIter = neg.iterator();
			while (negIter.hasNext() && part.size() <= (curr=negIter.next()).size()) {
				if (isSubset(part, curr)) {
					return true;
				}
			}
			return false;			
		}
		
		/**
		 * Sort to allow efficient subset computations 
		 * Important! Use the same comparison "cmp" that is used for the subset computation "isSubset"
		 * 
		 * @param elem
		 */
		protected void arrange(List<T> elem) {
			Collections.sort(elem, (a,b) -> cmp(a,b)); 

		}
		
		/**
		 * This is a linear time subset check implementation that assumes both sets are sorted lists.
		 * It replaces a simple b.containsAll(a) which is less efficient.
		 * In order to be able to compare type T which is general we use its toString.
		 * We usually use this on T=Integer for traces of behaviors, but this way we don't have to assume T is comparable.
		 * We must use the same compare method for sorting and for this method for correctness
		 * 
		 * @param a
		 * @param b
		 * @return a is a subset of b
		 */
		private boolean isSubset(List<T> a, List<T> b) {
			// we assume |a|<=|b| but don't check it because we wrote the code in a way that grants it.
			// The implementation is correct either way, only slower for |a|>|b|
			// assert(a.size()<=b.size());

			Iterator<T> iter = b.iterator();

			for (T ai : a)
				if (!seek(iter, ai))
					return false;

			return true;
		}

		/**
		 * Seek an element using the iterator. Move the iterator after all smaller than the sought item
		 * 
		 * @param iter
		 * @param sought
		 * @return true iff the element is found
		 */
		private boolean seek(Iterator<T> iter, T sought) {
			T curr;
			do {					// skip all items smaller than sought
				if (!iter.hasNext())
					return false;
			} while (cmp(curr = iter.next(), sought)<0);
			
			return cmp(curr, sought)==0; //if we got here we must have the one after all smaller than sought
		}

		/**
		 * Instead of assuming type T or List<T> are comparable, use their string representation
		 * We only assume that toString creates different strings for different values, but must be maintained.
		 */
		private int cmp(Object a, Object b) {
			return a.toString().compareTo(b.toString());
		}
	}

	protected Checker() {
		mem = new Memoize();
	}
	
	/**
	 * returns the numbers of checks for analysis purposes
	 * @return
	 */
	public int getChecks() {
		return checks;
	}

	/**
	 * returns the numbers of actual checks for analysis purposes
	 * @return
	 */
	public int getActualChecks() {
		return actualChecks;
	}

	/**
	 * find in lists and if not found apply check and count
	 * 
	 * @param part
	 * @return
	 */
	public boolean wrappedCheck(List<T> part) {
		
		checks++;

		// sort part before it is compared (to other sorted lists), or registered
		mem.arrange(part);
		
		if (mem.lookupPos(part)) {
			return true;
		}

		if (mem.lookupNeg(part)) {
			return false;
		}
		
		actualChecks++;
		boolean isPos = check(part);
		register(isPos, part); 
		return isPos;
	}
	
	public void register(boolean isPos, List<T> toRegister) {
		if (isPos) {
			mem.registerPos(toRegister);
		} else {
			mem.registerNeg(toRegister);
		}
	}

	  /**
	   * check if part satisfies criterion, e.g., unsatisfiabiliy when looking for unsat cores
	   * 
	   * @param part
	   * @return true if part satisfies criterion
	   */
	abstract protected boolean check(List<T> part);
}
