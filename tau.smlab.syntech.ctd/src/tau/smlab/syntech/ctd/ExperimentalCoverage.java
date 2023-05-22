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

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDVarSet;
import tau.smlab.syntech.games.controller.Controller;
import tau.smlab.syntech.jtlv.Env;

public class ExperimentalCoverage {
	/**
	 * Search using a random approach. Supports both DFS and BFS. Will only add
	 * states that were not added already. This function was not optimised to not
	 * leak BDDs. It is provided only for testing purposes.
	 * 
	 * @param symblController The controller
	 * @param testTuples      The tuples to cover
	 * @param dfsMode         If true uses DFS, favouring less paths. Otherwise uses
	 *                        BFS, favouring less states.
	 * @param maxBddCount     The maximum number of BDDs that should be in the
	 *                        solution.
	 * @return The test suite
	 * @throws Exception
	 */
	public static DefaultTreeModel computeTestSuiteTreeRandom(Controller controller,
			boolean dfsMode, int maxBddCount, List<BDDVarSet> testTuples) throws Exception {
		// Assumes game is realizable.
		long startTime = System.nanoTime();

		BDD statesAddedBdd = Env.FALSE();

		int iterations = 0;

		// construct initialStates list
		DefaultMutableTreeNode root = new DefaultMutableTreeNode(null);
		DefaultTreeModel statesTree = new DefaultTreeModel(root);

		DefaultMutableTreeNode dfsNode = root;

		while (iterations <= Math.min(CoverageUtils.iterationsLimit, maxBddCount)) {

			iterations++;
			
			if (CoverageUtils.debugMode) {
				CoverageUtils.debugPrint("Iteration: " + iterations);
			}

			BDD successors = Env.FALSE();

			boolean bfsRound = false;

			if (dfsMode) {
				if (dfsNode.getUserObject() == null) {
					// If we are at the root, just perform a BFS round
					bfsRound = true;
				} else {
					controller.kSucc((BDD) (dfsNode.getUserObject()), 1);
					successors = successors.and(statesAddedBdd.not());
					if (successors.isZero()) {
						bfsRound = true;
					}
				}
			}
			if (!dfsMode || bfsRound) {
				CoverageUtils.addSuccessors((DefaultMutableTreeNode) (statesTree.getRoot()), successors, controller);
			}

			BDD collected = successors;

			BDD chosen = Env.randomSat(collected, Env.globalUnprimeVars());
			// TODO: Consider removing the copy.
			BDD chosenCopy = Env.FALSE().or(chosen);

			// Add BDD to the tree
			if (dfsMode && !bfsRound) {
				DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(chosenCopy);
				dfsNode.add(childNode);
				dfsNode = childNode;
			} else {
				DefaultMutableTreeNode parent = CoverageUtils.findSuccessorParent((DefaultMutableTreeNode) (statesTree.getRoot()),
						chosen, controller);
				assert (parent != null);
				DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(chosenCopy);
				parent.add(childNode);

				if (dfsMode && bfsRound) {
					dfsNode = childNode;
				}
			}
			statesAddedBdd = statesAddedBdd.or(chosen);
			
//			if (iterations%(maxBddCount/20) == 0) {
//				double coverageFraction = CoverageWrapper.getTreeFullControllersCoverage2(statesTree, new ArrayList<BDDVarSet>(testTuples),
//						symblController, reachableStates);
//				CoverageUtils.debugPrint("coverage: " + coverageFraction);		
//			}
		}

		CoverageUtils.debugPrint("Total duration: " + (System.nanoTime() - startTime));
		return statesTree;
	}
}
