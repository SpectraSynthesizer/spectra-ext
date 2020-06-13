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

package tau.smlab.syntech.richcontrollerwalker;

import java.util.ArrayList;
import java.util.List;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDD.BDDIterator;
import tau.smlab.syntech.games.controller.symbolic.SymbolicController;
import tau.smlab.syntech.jtlv.CoreUtil;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.jtlv.lib.FixPoint;

public class ReachabilityGame {

	/**
	 * This component handles the reachability game. It computes the layers of
	 * states participating in the reachability attractor computation and extracts a
	 * route that goes from an initial state to one of the states in "targetStates".
	 * 
	 * @param breakpointState
	 *            breakpoint state to reach
	 * @param symblController
	 * @param initialState
	 *            initial state (current state)
	 * @return a route of states leading to the selected breakpoint from an initial
	 *         state
	 */
	public static List<BDD> playReachabilityToBreakPoint(BDD breakpointState, SymbolicController symblController,
			BDD initialState) {
		List<BDD> route = new ArrayList<BDD>();

		// iteration is in reverse because the last layer is the initial state layer
		List<BDD> layers = attractorLayers(breakpointState, symblController);

		boolean foundInitial = false;

		for (int i = layers.size() - 1; i >= 0; i--) {
			BDD layer = layers.get(i);

			// choose a random initial state from the ones leading to the target states
			if (!foundInitial) { // if (i==layers.size()-1){
				if (!layer.and(initialState).equals(Env.FALSE())) {
					route.add(CoreUtil.satOne(layer.and(initialState), Env.globalUnprimeVars()));
					foundInitial = true;
				}
				continue;
			}

			// if (!foundInitial){
			// System.out.println("no initial in any of the layers");
			// }

			BDD prev = route.get(route.size() - 1);
			BDD successors = Env.kSucc(prev, symblController.trans(), 2);
			BDDIterator initialIterator = new BDDIterator(layer, Env.globalUnprimeVars());
			while (initialIterator.hasNext()) {
				BDD currentState = initialIterator.next();
				if (!successors.and(currentState).equals(Env.FALSE())) {
					// prev = currentState;
					route.add(currentState);
					break;
				}
			}
		}

		int subIndex = 0;
		for (int i = route.size() - 1; i >= 0; i--) {
			BDD state = route.get(i);

			// if (subIndex > 0) {
			// break;
			// }

			if (initialState.equals(state)) {
				subIndex = i;
				break;
			}
		}

		// if (subIndex > 0) {
		// System.out.println("extracting from state " + subIndex);
		// }

		route = route.subList(subIndex, route.size());

		return route;
	}

	/**
	 * This component handles the reachability game in log walk.
	 * 
	 * @param breakpointState
	 *            breakpoint state to reach
	 * @param symblController
	 * @param initialState
	 *            initial state (current state)
	 * @return a route of states leading to the selected breakpoint from an initial
	 *         state, or NULL on failure
	 */
	public static List<BDD> playLogReachabilityToBreakPoint(BDD breakpointState, List<BDD> logStates) {
		List<BDD> route = new ArrayList<BDD>();

		for (BDD state : logStates) {
			route.add(state);
			if (!state.id().andWith(breakpointState.id()).equals(Env.FALSE()))
				return route;
		}

		return null;
	}

	/**
	 * Returns a list of BDDs. Each BDD symbolizes a layer of states in the
	 * reachability game, and the layers are disjoint. There is a route to the "to"
	 * BDD from the initial states that every step of it goes through a single state
	 * in each layer.
	 * 
	 * @param to
	 * @param symboliController
	 * @return
	 */
	private static List<BDD> attractorLayers(BDD to, SymbolicController symbolicController) {
		BDD attractor = Env.FALSE();
		FixPoint f = new FixPoint(true);

		List<BDD> layers = new ArrayList<BDD>();
		BDD previousAttractor = Env.FALSE();

		while (f.advance(attractor)) {
			previousAttractor = attractor;

			// compute winning states
			attractor = to.or(Env.pred(symbolicController.trans(), attractor));

			BDD layer = Env.FALSE().or(attractor);
			// extract to each layer only the new states, that were not in previous layers
			layer = layer.and(previousAttractor.not());
			layers.add(layer);
		}

		// last layer is always FALSE
		if (layers.get(layers.size() - 1).equals(Env.FALSE())) {
			layers.remove(layers.size() - 1);
		}

		return layers;
	}

}