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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDD.BDDIterator;
import tau.smlab.syntech.games.controller.symbolic.SymbolicController;
import tau.smlab.syntech.games.util.SaveLoadWithDomains;
import tau.smlab.syntech.jtlv.CoreUtil;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.jtlv.lib.FixPoint;
import tau.smlab.syntech.richcontrollerwalker.bdds.BddUtil;
import tau.smlab.syntech.richcontrollerwalker.util.Mod;

final class Engine {
	private final SymbolicController ctrl = new SymbolicController();
	private final ReachabilityGame reachGame = new ReachabilityGame();
	private FullState fullState;
	private Mod turn;

	Engine(String path) throws IOException {
		Map<String, String[]> sysVarsMap = new HashMap<>();
		Map<String, String[]> envVarsMap = new HashMap<>();
		SaveLoadWithDomains.loadStructureAndDomains(path + "vars.doms", sysVarsMap, envVarsMap);
		ctrl.setTrans(Env.loadBDD(path + "controller.trans.bdd"));
		ctrl.setInit(Env.loadBDD(path + "controller.init.bdd").exist(Env.globalPrimeVars()));
		BddUtil.setNewVars(sysVarsMap, envVarsMap);
		fullState = new FullState();
		fullState.restart();
	}

	BDDIterator getStepsIterator() {
		return fullState.successors.iterator(BddUtil.getVarsByModule(turn).toVarSet());
	}
	
	boolean isReachable(BDD bpBdd) {
		return reachGame.isReachable(bpBdd);
	}

	List<BDD> computeReachRoute(BDD bpBdd) {
		return reachGame.computeReachRoute(bpBdd);
	}

	FullState getFullState() {
		return fullState;
	}


	Mod getTurn() {
		return turn;
	}

	void setTurn(Mod newTurn) {
		turn = newTurn;
	}

	

	BDD getInitialState() {
		return ctrl.initial().id();
	}

	BDD getEngineMove() {
		return Algs.randomSat(fullState.getSuccessors(), BddUtil.getVarsByModule(turn));
	}

	void FreeAndSetFullState(FullState newFullState) {
		fullState.reset();
		fullState = Objects.requireNonNull(newFullState);
	}

	FullState getCopyOfFullState() {
		FullState copy = new FullState();
		copy.setFullState(fullState.state.id(), fullState.successors.id());
		return copy;
	}



	class FullState {
		private BDD state;
		private BDD successors;

		BDD getState() {
			return state;
		}

		BDD getSuccessors() {
			return successors;
		}

		void setState(BDD newState) {
			state = newState;
		}

		void setSuccessors(BDD newSucc) {
			successors = newSucc;
		}

		void setFullState(BDD newState, BDD newSuccessors) {
			freeAndSetState(newState);
			freeAndSetSuccessors(newSuccessors);
		}

		void freeAndSetState(BDD newState) {
			if (Objects.nonNull(state) && !state.isFree()) {
				state.free();
			}
			state = newState;
		}

		void freeAndSetSuccessors(BDD newSuccessors) {
			if (Objects.nonNull(successors) && !successors.isFree()) {
				successors.free();
			}
			successors = newSuccessors;
		}

		void reset() {
			setFullState(null, null);
		}

		void restart() {
			setFullState(ctrl.initial().id(), ctrl.initial().id());
		}

		void applyEnvChoice(BDD step) {
			freeAndSetSuccessors(successors.and(step));
		}

		void applySysChoice(BDD step) {
			freeAndSetState(successors.and(step));
			updateSuccessors();
		}

		void setStateAndUpdateSuccessors(BDD newCurrentState) {
			setState(newCurrentState.id());
			// freeAndSetState(newCurrentState.id()); //this is the problematic "free"
			// command
			updateSuccessors();
		}

		private void updateSuccessors() {
			freeAndSetSuccessors(ctrl.succ(state));
		}

		boolean isDeadlock() {
			return Objects.nonNull(successors) && successors.isZero();
		}

	}

	

	class ReachabilityGame {
		boolean isReachable(final BDD bpBdd) {
			List<BDD> reachRoute = computeReachRoute(bpBdd);
			if (isReachRouteValid(reachRoute)) {
				Env.free(reachRoute);
				return true;
			}
			return false;
		}

		private boolean isReachRouteValid(final List<BDD> route) {
			return Objects.nonNull(route) && route.size() > 1;
		}

		List<BDD> computeReachRoute(final BDD bpBDD) {
			return playReachabilityToBreakpoint(bpBDD);
		}

		/**
		 * This component handles the reachability game. It computes the layers of
		 * states participating in the reachability attractor computation and extracts a
		 * route that goes from an initial state to one of the states in "targetStates".
		 * 
		 * @param bpBDD        breakpoint state to reach
		 * @param ctrl
		 * @param initialState initial state (current state)
		 * @return a route of states leading to the selected breakpoint from an initial
		 *         state
		 */
		List<BDD> playReachabilityToBreakpoint(final BDD bpBDD) {
			List<BDD> route = new ArrayList<BDD>();

			// iteration is in reverse because the last layer is the initial state layer
			final List<BDD> layers = attractorLayers(bpBDD);

			boolean foundInitial = false;

			for (int i = layers.size() - 1; i >= 0; i--) {
				final BDD layer = layers.get(i);

				// choose a random initial state from the ones leading to the target states
				if (!foundInitial) { // if (i==layers.size()-1){
					if (!layer.and(fullState.state.id()).equals(Env.FALSE())) {
						route.add(CoreUtil.satOne(layer.and(fullState.state.id()), Env.globalUnprimeVars()));
						foundInitial = true;
					}
					continue;
				}

				final BDD prev = route.get(route.size() - 1);
				final BDD successors = Env.kSucc(prev, ctrl.trans(), 2);
				final BDDIterator initialIterator = new BDDIterator(layer, Env.globalUnprimeVars());
				while (initialIterator.hasNext()) {
					final BDD currentState = initialIterator.next();
					if (!successors.and(currentState).equals(Env.FALSE())) {
						route.add(currentState);
						break;
					}
				}
			}

			int subIndex = 0;
			for (int i = route.size() - 1; i >= 0; i--) {
				BDD state = route.get(i);

				if (fullState.state.equals(state)) {
					subIndex = i;
					break;
				}
			}

			route = route.subList(subIndex, route.size());
			return route;
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
		private List<BDD> attractorLayers(final BDD to) {
			BDD attractor = Env.FALSE();
			FixPoint f = new FixPoint(true);

			List<BDD> layers = new ArrayList<BDD>();
			BDD previousAttractor = Env.FALSE();

			while (f.advance(attractor)) {
				previousAttractor = attractor;

				// compute winning states
				attractor = to.or(Env.pred(ctrl.trans(), attractor));

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

	private static final class Algs {

		// Suppress default constructor for noninstantiability
		private Algs() {
			throw new AssertionError();
		}

		/**
		 * Returns a BDD which represents a satisfying assignment of the specified bdd.
		 * The assignment is random and refers to the specified variables.
		 * 
		 * @param currentState
		 * @param variables    The variables the assignment should refer to
		 * @return A random assignment of the specified variables which satisfies the
		 *         specified bdd.
		 */
		static BDD randomSat(BDD successors, BDD variables) {
			if (successors.isZero()) {
				return Env.FALSE();
			}
			// now we are sure we have a non trivially FALSE BDD
			BDD satRes = Env.TRUE();
			int[] varProfile = variables.varProfile();
			for (int i = 0; i < varProfile.length; i++) {
				if (varProfile[i] > 0) {
					boolean randChoice = ThreadLocalRandom.current().nextBoolean();
					BDD satCheck;
					if (randChoice) {
						satCheck = successors.and(satRes).andWith(successors.getFactory().ithVar(i));
					} else {
						satCheck = successors.and(satRes).andWith(successors.getFactory().nithVar(i));
					}
					if (!satCheck.isZero()) {
						satRes.andWith(
								randChoice ? successors.getFactory().ithVar(i) : successors.getFactory().nithVar(i));
					} else {
						satRes.andWith(
								randChoice ? successors.getFactory().nithVar(i) : successors.getFactory().ithVar(i));
					}
					satCheck.free();
				}
			}
			return satRes;
		}
	}
}
