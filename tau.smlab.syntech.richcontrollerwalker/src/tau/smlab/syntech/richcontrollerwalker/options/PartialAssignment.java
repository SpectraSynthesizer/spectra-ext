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

package tau.smlab.syntech.richcontrollerwalker.options;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDDomain;
import net.sf.javabdd.BDDVarSet;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.richcontrollerwalker.bdds.BddUtil;
import tau.smlab.syntech.richcontrollerwalker.bdds.VarClassifier;

public class PartialAssignment {
	private BDD assignment;
	private ArrayList<PartialAssignment> children = new ArrayList<PartialAssignment>();
	private PartialAssignment parent = null;
	private boolean isDirectlyComplete = false;

	private PartialAssignment(BDD assignment, boolean isComplete) {
		this.assignment = assignment;
		this.isDirectlyComplete = isComplete;
	}

	public BDD getBDD() {
		BDD result = Env.TRUE();
		PartialAssignment cur = this;
		while (cur.parent != null) {
			result = result.andWith(cur.assignment.id());
			cur = cur.parent;
		}
		return result;
	}

	public void insert(PartialAssignment assignment) {
		this.children.add(assignment);
		if (assignment != null) {
			assignment.parent = this;
		}
	}

	public ArrayList<PartialAssignment> getAssignments() {
		return children;
	}

	public boolean isValid() {
		return assignment != null;
	}

	public boolean isComplete() {
		if (!isValid()) {
			return false;
		}

		if (isDirectlyComplete) {
			return true;
		}

		if (this.children.isEmpty()) {
			return false;
		}

		boolean result = true;
		for (PartialAssignment e : this.children) {
			// There is a sub-branch
			if (e != null) {
				result &= e.isComplete();
			}

			if (!result) {
				break;
			}
		}

		return result;
	}

	@Override
	public String toString() {
		String result = BddUtil.bddToStr(this.getBDD());
		if (!isComplete()) {
			// Partial end of the tree
			result += " [...]";
		}
		return result;
	}

	private static void findLeaves(ArrayList<PartialAssignment> result, PartialAssignment root) {
		if (root.getAssignments().isEmpty()) {
			result.add(root);
			return;
		}

		for (var r : root.getAssignments()) {
			findLeaves(result, r);
		}
	}

	private static BDDDomain[] getDomainsByOrder(List<String> varOrder, BDDVarSet varSet) {
		BDDDomain[] result = varSet.getDomains();
		Arrays.sort(result, new Comparator<BDDDomain>() {
			@Override
			public int compare(BDDDomain d1, BDDDomain d2) {
				return varOrder.indexOf(d1.getName()) - varOrder.indexOf(d2.getName());
			}
		});
		return result;
	}

	/**
	 * This function returns a list of partial assignments that, together, represent all the possible assignments.
	 * The algorithm works by finding variable assignments that "determine" (make fixed/dont care) all other variables.
	 * 	- If no such assignment is found, the algorithm fails
	 * 	- Otherwise, the variable is chosen, and the algorithm searches for more variables recursively for all its non-determining assignments.
	 * 
	 * Note that the algorithm depends on the order, as the variable search order decides which variables will be chosen when there are multiple possible candidates.
	 * 
	 * @param usedVars The set of variables the algorithm should care about
	 * @param varOrder The variable search order
	 * @param successors The next possible successors BDD
	 * @param maxDepth The maximum search depth
	 * @return A list of partial assignments. If no partial assignments exist, an empty list is returned.
	 */
	public static ArrayList<PartialAssignment> getSmallestPartialAssignments(BDDVarSet usedVars, List<String> varOrder,
			BDD successors, int maxDepth) {
		BDDDomain[] orderedDomains = getDomainsByOrder(varOrder, usedVars);
		PartialAssignment result = PartialAssignment.getSmallestPartialAssignmentsInner(usedVars, orderedDomains,
				successors, maxDepth);

		if (!result.isValid()) {
			return new ArrayList<>();
		}

		ArrayList<PartialAssignment> leaves = new ArrayList<>();
		findLeaves(leaves, result);
		return leaves;
	}

	private static PartialAssignment getSmallestPartialAssignmentsInner(BDDVarSet usedVars, BDDDomain[] orderedDomains,
			BDD successors, int depth) {
		if (isFullAssignment(usedVars, successors)) {
			// Full assignment, nothing more to do
			return new PartialAssignment(Env.TRUE(), true);
		}

		if (depth == 0) {
			// Reached max depth, failed
			return new PartialAssignment(null, false);
		}

		for (BDDDomain variable : orderedDomains) {
			// Indices for assignments that determine all the variables
			HashSet<BigInteger> determiningIndices = new HashSet<>();
			// Indices for all other (non determining but also non-FALSE) assignments
			HashSet<BigInteger> nonDeterminingIndices = new HashSet<>();
			for (BigInteger i = BigInteger.ZERO; i.compareTo(variable.size()) < 0; i = i.add(BigInteger.ONE)) {
				BDD test = successors.and(variable.ithVar(i));
				if (test.isZero()) {
					// This assignment is invalid
					continue;
				} else if (isFullAssignment(usedVars, test)) {
					// This option is the end of the current branch - found a determining variable!
					determiningIndices.add(i);
				} else {
					// This is a non-determining assignment
					nonDeterminingIndices.add(i);
				}
			}

			if (determiningIndices.isEmpty()) {
				// There's no determining assignment for this variable, continue to the next variable
				continue;
			}

			// The new assignment is complete if there are no non-determining assignments
			PartialAssignment res = new PartialAssignment(Env.TRUE(), nonDeterminingIndices.isEmpty());
			for (BigInteger i : determiningIndices) {
				// The determining indices are directly-complete partial assignments (by definition).
				res.insert(new PartialAssignment(variable.ithVar(i), true));
			}

			// Calculate the partial assignments for the other non-determining values of the current
			// variable
			for (BigInteger i : nonDeterminingIndices) {
				BDD value = variable.ithVar(i);
				BDD nextSuccessors = successors.and(value);
				PartialAssignment child = getSmallestPartialAssignmentsInner(usedVars, orderedDomains, nextSuccessors,
						depth - 1);
				child.assignment = value;
				res.insert(child);
			}

			return res;
		}

		// Went over everything and found no determining assignment
		return new PartialAssignment(null, false);
	}

	/**
	 * Checks whether all the required variables are either fixed or dont-cares in
	 * the given BDD.
	 */
	private static boolean isFullAssignment(BDDVarSet usedVars, BDD opt) {
		BDDVarSet dontCares = BddUtil.getVarSet(VarClassifier.findDontCareVars(opt));
		BDDVarSet fixed = BddUtil.getVarSet(VarClassifier.findFixedVars(opt).keySet());
		BDDVarSet both = dontCares.unionWith(fixed);

		return usedVars.intersect(both).equals(usedVars);
	}
}