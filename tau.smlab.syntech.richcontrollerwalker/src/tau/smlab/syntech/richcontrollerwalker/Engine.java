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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.games.controller.Controller;
import tau.smlab.syntech.games.controller.StaticController;
import tau.smlab.syntech.games.controller.jits.BasicJitController;
import tau.smlab.syntech.games.util.SaveLoadWithDomains;
import tau.smlab.syntech.jtlv.CoreUtil;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.richcontrollerwalker.bdds.BddUtil;
import tau.smlab.syntech.richcontrollerwalker.util.Mod;

final class Engine {

	private final Controller ctrl;
	private final ReachabilityGame reachGame = new ReachabilityGame();
	private FullState fullState;
	private Mod turn;

	Engine(String path, String name, boolean useJitController) throws IOException {
		
		ctrl = useJitController ? new BasicJitController() : new StaticController();
		
		path += File.separator + (useJitController ? "jit" : "static");
		
		Map<String, String[]> sysVarsMap = new HashMap<>();
		Map<String, String[]> envVarsMap = new HashMap<>();
		
		SaveLoadWithDomains.loadStructureAndDomains(path + File.separator + name + "." + SaveLoadWithDomains.VARS_FILE, sysVarsMap, envVarsMap);
		
		sysVarsMap.entrySet().removeIf(var -> var.getKey().startsWith("util_"));
		envVarsMap.entrySet().removeIf(var -> var.getKey().startsWith("util_"));
	    
		ctrl.load(path, name, sysVarsMap, envVarsMap);
		
		BddUtil.setNewVars(sysVarsMap, envVarsMap);
		fullState = new FullState();
		fullState.restart();
	}
	
	boolean isReachable(BDD bp) {
		return reachGame.isReachable(bp);
	}

	List<BDD> computeReachRoute(BDD bp) {
		return reachGame.computeReachRoute(bp);
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
		return ctrl.initial();
	}
	
	BDD getRandomMove() {
		return Algs.randomSat(fullState.successors, BddUtil.getVarsByModule(getTurn()));
	}
	
	void freeAndSetFullState(FullState newFullState) {
		fullState.reset();
		fullState = Objects.requireNonNull(newFullState);
	}

	FullState getCopyOfFullState() {
		FullState copy = new FullState();
		copy.state = fullState.state.id();
		copy.successors = fullState.successors.id();
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

		void reset() {
			free();
			this.state = null;
			this.successors = null;
		}
		
		void free() {
			if (Objects.nonNull(state) && !state.isFree()) state.free();
			if (Objects.nonNull(successors) && !successors.isFree()) successors.free();
		}

		void restart() {
			free();
			this.state = ctrl.initial().id();
			this.successors = ctrl.initial().id();
		}

		void freeAndSetNewStep(BDD step) {
			free();
			this.state = step.id();
			this.successors = ctrl.succ(this.state);
		}
	

		boolean isDeadlock() {
			return Objects.nonNull(successors) && successors.isZero();
		}

	}

	

	class ReachabilityGame {
		boolean isReachable(BDD bp) {
			List<BDD> reachRoute = computeReachRoute(bp);
			if (isReachRouteValid(reachRoute)) {
				Env.free(reachRoute);
				return true;
			}
			return false;
		}

		private boolean isReachRouteValid(final List<BDD> route) {
			return Objects.nonNull(route) && route.size() > 1;
		}

		List<BDD> computeReachRoute(BDD bp) {
			return playReachabilityToBreakpoint(bp);
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
		List<BDD> playReachabilityToBreakpoint(BDD bp) {
			List<BDD> route = new ArrayList<BDD>();
			
			System.out.println("Reachability start");

			// iteration is in reverse because the last layer is the initial state layer
			final List<BDD> layers = attractorLayers(bp.id());

			boolean found = false;

			for (int i = layers.size() - 1; i >= 0; i--) {
				final BDD layer = layers.get(i);

				// choose a random initial state from the ones leading to the target states
				if (!found) { // if (i==layers.size()-1){
					BDD intersection = layer.and(fullState.state);
					if (!intersection.isZero()) {
						route.add(CoreUtil.satOne(intersection, Env.globalUnprimeVars()));
						found = true;
						System.out.println("Found intersection");
					}
					intersection.free();
					continue;
				}

				final BDD last = route.get(route.size() - 1);
				final BDD successors = ctrl.succ(last);
								
				BDD intersection = layer.and(successors);
				if (!intersection.isZero()) {
					route.add(CoreUtil.satOne(intersection, Env.globalUnprimeVars()));
					System.out.println("Adding new item to route");
				}
				intersection.free();
			}


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
		private List<BDD> attractorLayers(BDD to) {
			
			BDD attractor = to;
			BDD previousAttractor = attractor.id();

			List<BDD> layers = new ArrayList<BDD>();
			layers.add(attractor.id());

			do  {
				
				previousAttractor.free();
				previousAttractor = attractor;
				
				System.out.println("Computing pred");
	
				BDD curr = ctrl.pred(previousAttractor);
				attractor = attractor.or(curr);
				
				layers.add(curr.and(previousAttractor.not()));
				
				curr.free();
				
			} while (!attractor.equals(previousAttractor));

			// last layer is always FALSE
			layers.remove(layers.size()-1);

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
		static BDD randomSat(BDD bdd, BDD variables) {
			if (bdd.isZero()) {
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
						satCheck = bdd.and(satRes).andWith(bdd.getFactory().ithVar(i));
					} else {
						satCheck = bdd.and(satRes).andWith(bdd.getFactory().nithVar(i));
					}
					if (!satCheck.isZero()) {
						satRes.andWith(
								randChoice ? bdd.getFactory().ithVar(i) : bdd.getFactory().nithVar(i));
					} else {
						satRes.andWith(
								randChoice ? bdd.getFactory().nithVar(i) : bdd.getFactory().ithVar(i));
					}
					satCheck.free();
				}
			}
			return satRes;
		}
	}
}
