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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.logs.BDDLogReader;

public class Route {
	private int idx;
	private List<BDD> route;

	List<BDD> getRoutePrefixCopy() {
		List<BDD> routePrefix = new ArrayList<>();
		for (int i = 0; i <= idx; i++) {
			if (Objects.isNull(route.get(i)) || route.get(i).isFree()) {
				throw new IllegalStateException();
			}
			routePrefix.add(route.get(i).id());
		}
		return route;
	}

	BDD advance() {
		idx++;
		return Objects.requireNonNull(getCurrentState());
	}

	BDD stepBack() {
		idx--;
		return Objects.requireNonNull(getCurrentState());
	}

	boolean canStepBack() {
		return idx > 0;
	}

	int numRemainingStates() {
		return lastIndex() - idx;
	}
	
	int getNumStates() {
		return lastIndex();
	}

	void reset(boolean freeRouteStates) {
		if (freeRouteStates && Objects.nonNull(route)) {
			Env.free(route); // This is problematic in some cases, so this must be enabled explicitly via the
							// method argument
		}
		route = null;
		idx = 0;
	}

	BDD nextState() {
		return getStateByIndex(idx + 1);
	}

	BDD startState() {
		idx = 0;
		return getCurrentState();
	}

	BDD lastState() {
		idx = lastIndex();
		return getCurrentState();
	}

	boolean isEnd() {
		return idx == lastIndex();
	}

	boolean isStart() {
		return idx == 0;
	}

	BDD getCurrentState() {
		if (!isIndexLegal()) {
			throw new IllegalStateException("route index has illegal value");
		}
		return getStateByIndex(idx);
	}

	private BDD getStateByIndex(final int idx) {
		return route.get(idx);
	}

	private int lastIndex() {
		return route.size() - 1;
	}

	private boolean isIndexLegal() {
		Objects.requireNonNull(route, "cannot check index validity when route is null");
		return idx >= 0 && idx <= lastIndex();
	}

	List<BDD> loadLogRoute(final String path) {
		final List<BDD> loadedRoute = LogLoader.getLogRoute(path);
		return LogLoader.isLoadedRouteValid(loadedRoute) ? loadedRoute : Collections.emptyList();
	}

	void setRoute(final List<BDD> loadedRoute) {
		reset(false);
		route = loadedRoute;
	}

	static class LogLoader {
		private static final int MIN_LOG_SIZE = 1;

		static List<BDD> getLogRoute(final String path) {
			final List<BDD> loadedRoute = new ArrayList<>();
			// Load states
			for (BDD b : new BDDLogReader(path)) {
				loadedRoute.add(b);
			}
			return loadedRoute;
		}

		private static boolean isLoadedRouteValid(final List<BDD> loadedRoute) {
			if (loadedRoute.size() < MIN_LOG_SIZE) {
				return false;
			}
			for (BDD state : loadedRoute) {
				if (!(state.equals(Env.TRUE()) || state.equals(Env.FALSE()))) {
					return true;
				}
			}
			return false;
		}
	}

	int getCurrentIndex() {
		return idx;
	}



}
