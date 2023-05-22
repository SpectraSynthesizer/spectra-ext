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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.swing.tree.DefaultMutableTreeNode;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDVarSet;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.games.controller.Controller;
import tau.smlab.syntech.games.controller.symbolic.SymbolicController;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.jtlv.env.module.ModuleBDDField;

/**
 * This static class contains many different functions used for the different coverage algorithms.
 */
public class CoverageUtils {
	// The maximum number of iterations in each of the algorithms.
	public static int iterationsLimit = 20000;

	// When true, there will be more printing and calculations that are for debug purposes.
	public static boolean debugMode = true;
	
	/**
	 * Prints the string only if in debug mode
	 * @param str string to print.
	 */
	public static void debugPrint(String str) {
		if (debugMode) {
			System.out.println(str);
		}
	}

	/**
	 * Add all successors in the tree recursively to the successors BDD using "or".
	 * 
	 * @param node            Root of the tree
	 * @param successors      The BDD that will be added to.
	 * @param symblController The game.
	 */
	public static void addSuccessors(DefaultMutableTreeNode node, BDD successors, Controller controller) {
		if (node.getUserObject() != null) {
			successors.orWith(controller.kSucc((BDD) (node.getUserObject()), 1));
		} else {
			// Add all initial positions
			successors.orWith(controller.initial().id());
		}

		for (int i = 0; i < node.getChildCount(); i++) {
			addSuccessors((DefaultMutableTreeNode) (node.getChildAt(i)), successors, controller);
		}
	}

	/**
	 * @param node            The root of the tree to start the recursive search
	 *                        from.
	 * @param successor       Looking for the parent of this BDD.
	 * @param symblController The game.
	 * @return Returns the parent of the successor from the node. If none found,
	 *         returns null.
	 */
	public static DefaultMutableTreeNode findSuccessorParent(DefaultMutableTreeNode node, BDD successor,
			Controller controller) {

		if (node.getUserObject() != null) {
			// Check if the successor is in the nodes successors
			BDD parentBdd = (BDD) (node.getUserObject());

			BDD tmp = controller.kSucc(parentBdd, 1);
			tmp.andWith(successor.id());
			boolean isFound = !tmp.isZero();

			tmp.free();

			if (isFound) {
				return node;
			}
		} else {
			// Check if initial position - if so return our "null" node.
			BDD tmp = successor.and(controller.initial());
			boolean isFound = !tmp.isZero();
			tmp.free();

			if (isFound) {
				return node;
			}
		}

		for (int i = 0; i < node.getChildCount(); i++) {
			DefaultMutableTreeNode res = findSuccessorParent((DefaultMutableTreeNode) (node.getChildAt(i)), successor, controller);
			if (res != null) {
				return res;
			}
		}

		return null;
	}

	/**
	 * Plays a reachability game from "from" to "to". Goes form the end (to) and
	 * adds all the "new" predecessors to the list. Stops when there is an
	 * intersection between "from" and the last generated layer. The path is
	 * "cleaned" at the end in order to not contain non-useful states. Returns a
	 * list containing the path found.
	 * 
	 * @param from              Start of the path.
	 * @param to                Where we want to reach.
	 * @param symboliController The game.
	 * @param reachableStates   The reachable states in the game.
	 * @return The list of attractor layers. With the first being the one
	 *         intersecting with "from" and the last being "to".
	 */
	public static List<BDD> attractorLayers2(BDD from, BDD to, Controller controller,
			BDD reachableStates) {
		List<BDD> layers = new ArrayList<BDD>();
		BDD previousAttractor = Env.FALSE();

		layers.add(to.id());
		BDD attractor = to.id();

		BDD fromIntersection = layers.get(0).and(from);
		while (fromIntersection.isZero()) {
			CoverageUtils.debugPrint("Attractor2 reachability");
			previousAttractor.free();
			previousAttractor = attractor;

			// compute winning states
//			attractor = Env.pred(symboliController.trans(), attractor);
			attractor = controller.pred(attractor);

			BDD layer = attractor.id();
			// extract to each layer only the new states, that were not in previous layers
			layer.andWith(previousAttractor.not());
			// Only use reachable states
			layer.andWith(reachableStates.id());
			layers.add(0, layer);
			layer = null;

			fromIntersection.free();
			fromIntersection = layers.get(0).and(from);
		}

		fromIntersection.free();
		previousAttractor.free();
		attractor.free();

		// Clean up paths that did not help:
		layers.get(0).andWith(from.id());
		// MAYBE add -1 to layers.size()? didn't help
		for (int i = 1; i < layers.size(); i++) {
			layers.get(i).andWith(Env.succ(layers.get(i - 1), controller.transitions()));
		}

		return layers;
	}

	public static List<BDD> attractorLayers3(BDD from, BDD to, Controller controller, BDD reachableStates) {
		List<BDD> layers = new ArrayList<BDD>();

		layers.add(from.id());
		BDD lastLayer = layers.get(0);
		BDD intersect = lastLayer.and(to);
		
		while (intersect.isZero()) {
			CoverageUtils.debugPrint("Reachability cycle");
//			lastLayer = Env.succ(lastLayer, symboliController.trans());
			lastLayer = controller.succ(lastLayer);
			layers.add(lastLayer);
			
			intersect.free();
			intersect = lastLayer.and(to);
		}
		
		intersect.free();
		
		// No cleaning

		return layers;
	}

	
	/**
	 * Free all bdds in a list of bdds.
	 * 
	 * @param lst The list of bdds.
	 */
	public static void freeBddList(List<BDD> lst) {
		for (BDD bdd : lst) {
			bdd.free();
		}
	}
	
	/**
	 * Satisfy each attractor in the list to 1 solution.
	 * The created path will be a legal one in the game.
	 * @param attractors The list of attractor layers.
	 * @param symblController SC of the game.
	 */
	public static void satAttractors(List<BDD> attractors, Controller controller) {
		BDD first = Env.randomSat(attractors.get(0), Env.globalUnprimeVars());
		attractors.get(0).free();
		attractors.set(0, first);

		for (int i = 1; i < attractors.size(); i++) {
//			attractors.get(i).andWith(Env.succ(attractors.get(i - 1), symblController.trans()));
			attractors.get(i).andWith(controller.succ(attractors.get(i - 1)));
			BDD tmp = Env.randomSat(attractors.get(i), Env.globalUnprimeVars());
			//BDD tmp = attractors.get(i).satOne(Env.globalUnprimeVars());

			attractors.get(i).free();
			attractors.set(i, tmp);
		}
	}
	
	/**
	 * Updates the search state uncovered BDDs as if we also added chosen to the
	 * test suite.
	 * 
	 * @param chosen The BDD to add to the test suite
	 * @param ss     The search state to update
	 * @return The search state itself. For convince only, no copy occurs.
	 */
	public static SearchState updateYetUncovered(BDD chosen, SearchState ss) {
		ss.statesLeft = 0;
		LinkedList<TupleBDD> toAdd = new LinkedList<TupleBDD>();

		Iterator<TupleBDD> iter = ss.uncoveredSet.iterator();
		while (iter.hasNext()) {
			TupleBDD tupleBDD = iter.next();

			BDDVarSet nonTupleVars = tupleBDD.getNonTupleVarSet();
			BDD tmp = chosen.exist(nonTupleVars);
			BDD tmp2 = tmp.not();
			tmp.free();

			BDD tmp3 = tupleBDD.getBdd().and(tmp2);
			tmp2.free();

			BDD new_uncov = tmp3.exist(nonTupleVars);
			tmp3.free();

			if (new_uncov.isZero()) {
				tupleBDD.free();
				iter.remove();
			} else if (!tupleBDD.getBdd().equals(new_uncov)) {
				tupleBDD.setBdd(new_uncov.id());
				ss.statesLeft += tupleBDD.varSatCount();

				toAdd.add(tupleBDD);
				iter.remove();
			} else {
				ss.statesLeft += tupleBDD.varSatCount();
			}

			new_uncov.free();
		}

		for (TupleBDD tupleBDD : toAdd) {
			// here ordering happens
			ss.uncoveredSet.add(tupleBDD);
		}

		return ss;
	}

	/**
	 * Create a list of tuples from all possible pairs of variables (system and
	 * environment)
	 * 
	 * @param varsSys
	 * @param varsEnv
	 * @param numVars
	 * @return
	 */
	public static List<BDDVarSet> generateTestTuples(List<ModuleBDDField> varsSys, List<ModuleBDDField> varsEnv,
			int numVars) {
		List<ModuleBDDField> allVars = new ArrayList<ModuleBDDField>();
		allVars.addAll(varsSys);
		allVars.addAll(varsEnv);

		return generateTestTuples(allVars, numVars);
	}

	public static List<BDDVarSet> generateTestTuples(List<ModuleBDDField> allVars, int numVars) {
		numVars = Math.min(numVars, allVars.size());

		return innerGenerateTestTuples(allVars, 0, numVars, Env.getEmptySet());
	}

	static List<BDDVarSet> innerGenerateTestTuples(List<ModuleBDDField> allVars, int startIndex, int varsLeft,
			BDDVarSet current) {
		ArrayList<BDDVarSet> res = new ArrayList<BDDVarSet>();

		if (varsLeft == 0) {
			res.add(current.id());
			return res;
		}

		for (int i = startIndex; i < allVars.size(); i++) {
			if (allVars.get(i) == null) {
				System.out.println("i:" + i);
			}
			BDDVarSet toAdd = allVars.get(i).support();
			toAdd.unionWith(current.id());
			res.addAll(innerGenerateTestTuples(allVars, i + 1, varsLeft - 1, toAdd));
			// toAdd.free();
		}

		return res;
	}

	/**
	 * Returns a BDD that is the disjunction of all the uncovered_t BDDs
	 * 
	 * @param uncoveredMap
	 * @param tuples
	 * @return
	 */
	static BDD getCollectedFromUncovered(AbstractSet<TupleBDD> uncoveredSet) {
		BDD collected = Env.FALSE();
		for (TupleBDD tupleBDD : uncoveredSet) {
			collected.orWith(tupleBDD.getBdd().id());
		}

		return collected;
	}

	/**
	 * Creates a symbolic controller from a sequence of states. The symbolic
	 * controller will only fix the environment variables and not the system
	 * variables.
	 * 
	 * @param sequence
	 * @param m        GameModel of the specification
	 * @return
	 */
	@SuppressWarnings("unused")
	private static SymbolicController getEnvControllerForSequence(List<BDD> sequence, GameModel m) {
		SymbolicController ctrl = new SymbolicController();
		BDDVarSet sysVars = Env.unionUnprime(m.getSys().getNonAuxFields());
		BDD current = sequence.get(0).exist(sysVars);

		// set initial state
		ctrl.setInit(current.id());

		// compute transitions for states i to i+1
		for (int i = 0; i < sequence.size() - 1; i++) {
			BDD next = sequence.get(i + 1).exist(sysVars);
			// add transition to controller
			ctrl.disjunctTrans(current.and(Env.prime(next)));
			current.free();
			current = next;
		}
		return ctrl;
	}
	
	/**
	 * Update the search state with all the BDDs in the tree starting from the given node.
	 * @param ss The search state object
	 * @param root The root of the tree
	 */
	public static void updateStatesFromTree(SearchState ss, DefaultMutableTreeNode root) {
		if (root.getUserObject() != null) {
			updateYetUncovered((BDD)root.getUserObject(), ss);			
		}
		
		for (int i=0; i<root.getChildCount(); i++) {
			updateStatesFromTree(ss, (DefaultMutableTreeNode)root.getChildAt(i));
		}
	}
}
