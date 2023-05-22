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

package tau.smlab.syntech.ctd;

import java.util.List;
import java.util.TreeSet;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDD.BDDIterator;
import net.sf.javabdd.BDDVarSet;
import tau.smlab.syntech.games.controller.Controller;
import tau.smlab.syntech.jtlv.Env;

public class RecursiveCoverage {
	/**
	 * Recursively looks for the path that satisfies the most states in the
	 * uncovered BDDs.
	 * 
	 * @param maxDepth        The max depth to search in the "possibilities tree"
	 * @param pruneSize       The max width to search for in each step.
	 * @param ss              Object containing the state of the search. This object
	 *                        is not harmed.
	 * @param reachableStates The reachable states in the game.
	 * @param symblController The game.
	 * @return An object containing the state of the
	 */
	public static SearchState recursiveBestPath(int maxDepth, int pruneSize, SearchState ss, BDD reachableStates,
			Controller controller) {
		if (maxDepth <= 0 || ss.uncoveredSet.size() == 0) {
			return ss.getCopy();
		}

		BDD yetUncovered = CoverageUtils.getCollectedFromUncovered(ss.uncoveredSet);
		yetUncovered = yetUncovered.andWith(reachableStates.id());

		BDD successors = Env.FALSE();
		CoverageUtils.addSuccessors((DefaultMutableTreeNode) (ss.statesTree.getRoot()), successors, controller);

		boolean playedReachability = false;
		int addedDepth = 0;

		BDD tmp = successors.and(yetUncovered);

		BDD origSuccessors = successors.id();

		if (tmp.isZero()) {
			List<BDD> attractors = CoverageUtils.attractorLayers2(successors, yetUncovered, controller,
					reachableStates);
			successors = attractors.get(attractors.size() - 1).id();

			if (CoverageUtils.debugMode) {
				// Sanity check
				BDD tmp2 = successors.and(reachableStates);
				assert (tmp2.equals(successors));
				tmp2.free();
			}

			playedReachability = true;
			addedDepth = attractors.size() - 1;

			CoverageUtils.freeBddList(attractors);
		}

		tmp.free();

		// Use the successors of all branches
		BDD collected = successors.id();
		for (TupleBDD tupleBDD : ss.uncoveredSet) {
			BDD currIntersection = tupleBDD.getBdd().and(collected);
			if (!currIntersection.isZero()) {
				collected.free();
				collected = currIntersection;
			} else {
				currIntersection.free();
			}

			if (collected.satCount(Env.globalUnprimeVars()) <= pruneSize) {
				break;
			}
		}

		SearchState bestSS = null;
		double smallestSats = Double.MAX_VALUE;

		BDDIterator it = new BDDIterator(collected, Env.globalUnprimeVars());
		for (int i = 0; i < pruneSize && it.hasNext(); i++) {
			BDD one = it.nextBDD();
			SearchState newSS = ss.getCopy();

			if (playedReachability) {
				List<BDD> attractorsChosen = CoverageUtils.attractorLayers2(origSuccessors, one, controller,
						reachableStates);

				BDD chosenLayer0 = attractorsChosen.get(0).satOne(Env.globalUnprimeVars());
				DefaultMutableTreeNode parent = CoverageUtils.findSuccessorParent(
						(DefaultMutableTreeNode) (newSS.statesTree.getRoot()), chosenLayer0, controller);
				assert (parent != null);
				DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(chosenLayer0);
				parent.add(childNode);
				parent = childNode;

				BDD lastChosen = chosenLayer0;

				for (int j = 1; j < attractorsChosen.size(); j++) {
					BDD nextAttractors = attractorsChosen.get(j);
					nextAttractors.andWith(Env.succ(lastChosen, controller.transitions()));
					assert (!nextAttractors.isZero());

					lastChosen = nextAttractors.satOne(Env.globalUnprimeVars());

					DefaultMutableTreeNode newChild = new DefaultMutableTreeNode(lastChosen);
					parent.add(newChild);
					parent = newChild;
				}

				CoverageUtils.freeBddList(attractorsChosen);
			} else {
				DefaultMutableTreeNode parent = CoverageUtils.findSuccessorParent(
						(DefaultMutableTreeNode) (newSS.statesTree.getRoot()), one, controller);
				assert (parent != null);
				DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(one.id());
				parent.add(childNode);
			}

			// Updates newSS
			CoverageUtils.updateYetUncovered(one, newSS);
			one.free();

			// Known issue, if maxDepth-1-addedDepth is less then 0, everything will work
			// fine, but the results in some recursive cases might be suboptimal.
			SearchState newSSrec = recursiveBestPath(maxDepth - 1 - addedDepth, pruneSize, newSS, reachableStates,
					controller);

			if (smallestSats > newSSrec.statesLeft) {
				if (bestSS != null) {
					bestSS.free();
				}

				bestSS = newSS;
				// Just added to solve the bug.
				bestSS.statesLeft = newSSrec.statesLeft;
				smallestSats = newSSrec.statesLeft;
			} else {
				newSS.free();
			}
			newSSrec.free();
		}

		origSuccessors.free();
		it.free();
		return bestSS;
	}

	/**
	 * Computes a test suite using the recursive approach.
	 * 
	 * @param symblController The controller
	 * @param testTuples      The tuples to cover
	 * @param maxDepth        The max depth to search into at each iteration
	 * @param pruneSize       The max width to search in each iteration
	 * @return The test suite
	 * @throws Exception
	 */
	public static DefaultTreeModel computeTestSuiteTreeRecursive(Controller controller,
			List<BDDVarSet> testTuples, int maxDepth, int pruneSize) throws Exception {
		// Assumes game is realizable.

		long startTime = System.nanoTime();

		// Gets all reachable states from the environment BDD.
		BDD reachableStates = controller.kSucc(controller.initial().id(), 0);

		TreeSet<TupleBDD> uncoveredSet = new TreeSet<TupleBDD>(new TupleBDDCmp());
		for (int i = 0; i < testTuples.size(); i++) {
			uncoveredSet.add(new TupleBDD(testTuples.get(i), reachableStates));
		}

		// construct initialStates list
		DefaultMutableTreeNode root = new DefaultMutableTreeNode(null);
		DefaultTreeModel statesTree = new DefaultTreeModel(root);

		SearchState ss = new SearchState(uncoveredSet, statesTree);
		ss.updateStatesLeft();
		double initialStatesLeft = ss.statesLeft;

		int iterations = 0;

		while (ss.uncoveredSet.size() != 0) {
			iterations++;
			if (CoverageUtils.debugMode) {
				System.out.println("Iteration: " + iterations + " Coverage%: " + (1 - ss.statesLeft / initialStatesLeft)
						+ " Duration: " + (System.nanoTime() - startTime));
			}

			if (iterations > CoverageUtils.iterationsLimit) {
				CoverageUtils.debugPrint("Iterations count exceeded a threshold of " + CoverageUtils.iterationsLimit);
				return ss.statesTree;
			}

			SearchState tmp = recursiveBestPath(maxDepth, pruneSize, ss, reachableStates, controller);
			ss.free();
			ss = tmp;
		}

		reachableStates.free();
		// TODO: leaking here the uncovered set. No big deal because we are done.
		return ss.statesTree;
	}
}
