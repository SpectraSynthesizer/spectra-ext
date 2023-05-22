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

import java.util.AbstractSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDVarSet;
import tau.smlab.syntech.games.controller.Controller;
import tau.smlab.syntech.jtlv.Env;

public class TreeCoverage {
	public static DefaultTreeModel computeTestSuiteTree(Controller controller,
			List<BDDVarSet> testTuples, boolean dfsMode, boolean ordered) throws Exception {
		return computeTestSuiteTree(controller, testTuples, dfsMode, ordered, null, 1);
	}
	
	/**
	 * Search using a greedy approach. Supports both DFS and BFS. It is provided
	 * only for testing purposes.
	 * 
	 * @param symblController The controller
	 * @param testTuples      The tuples to cover
	 * @param dfsMode         If true uses DFS, favouring less paths. Otherwise uses
	 *                        BFS, favouring less states.
	 * @param ordered		  If true uses an ordered set for the testing tuples.
	 * 						  False should be used mainly for experimentation.
	 * @param statesTree	  An initial tree containing an existing test suite.
	 * 						  This suite will be expanded upon.
	 * @param requestedCoverage Stop once reaching this coverage.
	 * @return The test suite
	 * @throws Exception
	 */
	public static DefaultTreeModel computeTestSuiteTree(Controller controller,
			List<BDDVarSet> testTuples, boolean dfsMode, boolean ordered, DefaultTreeModel statesTree, double requestedCoverage) throws Exception {
		// Assumes game is realizable.

		CoverageUtils.debugPrint("Starting time");
		long startTime = System.nanoTime();

		// Gets all reachable states from the environment BDD.
		BDD reachableStates = controller.kSucc(controller.initial().id(), 0);

		CoverageUtils.debugPrint("Got reachable - took: " + (System.nanoTime()-startTime));
		

		AbstractSet<TupleBDD> uncoveredSet;
		if (ordered) {
			uncoveredSet = new TreeSet<TupleBDD>(new TupleBDDCmp());
		} else {
			uncoveredSet = new LinkedHashSet<TupleBDD>();
		}

		for (int i = 0; i < testTuples.size(); i++) {
			uncoveredSet.add(new TupleBDD(testTuples.get(i), reachableStates));
		}

		CoverageUtils.debugPrint("Created uncovered set");

		int iterations = 0;

		SearchState ss = new SearchState(uncoveredSet, statesTree);
		ss.updateStatesLeft();
		
		double initialStatesLeft = ss.statesLeft;
		CoverageUtils.debugPrint("Initial States: " + initialStatesLeft);
		
		DefaultMutableTreeNode root;
		if (statesTree == null) {
			root = new DefaultMutableTreeNode(null);
			statesTree = new DefaultTreeModel(root);			
		} else {			
			CoverageUtils.debugPrint("Used ini tree");
			root = (DefaultMutableTreeNode)statesTree.getRoot();
		}

		ss.statesTree = statesTree;
		CoverageUtils.updateStatesFromTree(ss, root);
		

		DefaultMutableTreeNode dfsNode = root;

		while (ss.uncoveredSet.size() > 0 && (1-ss.statesLeft/initialStatesLeft) <= requestedCoverage) {

			iterations++;
			if (CoverageUtils.debugMode) {
				System.out.println("Iteration: " + iterations + " Coverage%: " + (1-ss.statesLeft/initialStatesLeft) + " Duration: " + (System.nanoTime() - startTime));
			}

			if (iterations > CoverageUtils.iterationsLimit) {
				CoverageUtils.debugPrint("Iterations count exceeded a threshold of " + CoverageUtils.iterationsLimit);
				return ss.statesTree;
			}

			
			// yetUncovered is used to know if we need a reachability game.
			// Consider optimizing here as well
			BDD yetUncovered = CoverageUtils.getCollectedFromUncovered(ss.uncoveredSet);
			yetUncovered = yetUncovered.andWith(reachableStates.id());

			BDD successors = Env.FALSE();

			boolean bfsRound = false;

			if (dfsMode) {
				if (dfsNode.getUserObject() == null) {
					// If we are at the root, just perform a BFS round
					bfsRound = true;
				} else {
					successors.free();
					successors = controller.kSucc(((BDD) (dfsNode.getUserObject())).id(), 1);
					BDD tmp = successors.and(yetUncovered);
					if (tmp.isZero()) {
						bfsRound = true;
					}

					tmp.free();
				}
			}
			if (!dfsMode || bfsRound) {
				// Consdier optimizing here and not calculating this each time
				CoverageUtils.addSuccessors((DefaultMutableTreeNode) (ss.statesTree.getRoot()), successors, controller);
			}

			boolean playedReachability = false;
			BDD origSuccessors = successors.id();

			BDD tmp = successors.and(yetUncovered);
			if (tmp.isZero()) {
				CoverageUtils.debugPrint("Playing reachability");
				// Changed from 2 to 3
				List<BDD> attractors = CoverageUtils.attractorLayers3(successors, yetUncovered, controller, reachableStates);
				successors = attractors.get(attractors.size() - 1).id();
				if (CoverageUtils.debugMode) {
					// Sanity check
					BDD tmp2 = successors.and(reachableStates);
					assert (tmp2.equals(successors));
					tmp2.free();
				}

				playedReachability = true;
				CoverageUtils.freeBddList(attractors);
			}

			tmp.free();

			// Use the successors of all branches
			BDD collected = successors.id();
			for (TupleBDD tupleBDD : ss.uncoveredSet) {
				BDD currIntersection = (tupleBDD.getBdd().and(collected));
				if (!currIntersection.isZero()) {
					
					collected.free();
					collected = currIntersection;
					
					if (collected.satCount(Env.globalUnprimeVars())==1.0) {
						// break if sat count is 1.
						break;
					}
					
				} else {
					currIntersection.free();
				}
			}

			BDD chosen = Env.randomSat(collected, Env.globalUnprimeVars());
			assert (!chosen.isZero());

			// Add BDD to the tree
			if (dfsMode && !bfsRound) {
				// Reachability is never played in this flow
				assert (!playedReachability);

				DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(chosen);
				dfsNode.add(childNode);
				dfsNode = childNode;
			} else {
				if (playedReachability) {
					List<BDD> attractorsChosen = CoverageUtils.attractorLayers2(origSuccessors, chosen, controller,
							reachableStates);
					CoverageUtils.satAttractors(attractorsChosen, controller);
					
					BDD chosenLayer0 = attractorsChosen.get(0);
					DefaultMutableTreeNode parent = CoverageUtils.findSuccessorParent(
							(DefaultMutableTreeNode) (ss.statesTree.getRoot()), chosenLayer0, controller);
					assert (parent != null);
					DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(chosenLayer0);
					parent.add(childNode);
					parent = childNode;

					for (int i = 1; i < attractorsChosen.size(); i++) {
						DefaultMutableTreeNode newChild = new DefaultMutableTreeNode(attractorsChosen.get(i));
						parent.add(newChild);
						parent = newChild;
					}

					if (dfsMode && bfsRound) {
						dfsNode = parent;
					}

				} else {
					DefaultMutableTreeNode parent = CoverageUtils.findSuccessorParent(
							(DefaultMutableTreeNode) (ss.statesTree.getRoot()), chosen, controller);
					assert (parent != null);
					DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(chosen);
					parent.add(childNode);

					if (dfsMode && bfsRound) {
						dfsNode = childNode;
					}
				}
			}

			//System.out.println("Chosen: " + chosen);
			
			// update tuples in T
			CoverageUtils.updateYetUncovered(chosen, ss);

			successors.free();
			origSuccessors.free();
			collected.free();
		}

		CoverageUtils.debugPrint("Iteration: " + iterations + " Coverage%: " + (1-ss.statesLeft/initialStatesLeft) + " Duration: " + (System.nanoTime() - startTime));
		
		reachableStates.free();
		
		CoverageUtils.debugPrint("Total duration: " + (System.nanoTime() - startTime));
		return ss.statesTree;
	}
}
