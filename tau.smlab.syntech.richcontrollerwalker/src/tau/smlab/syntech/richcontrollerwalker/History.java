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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.richcontrollerwalker.Engine.FullState;
import tau.smlab.syntech.richcontrollerwalker.util.Mode;
import tau.smlab.syntech.richcontrollerwalker.util.Mod;

public final class History {
	private final Deque<HistoryItem> stack = new ArrayDeque<>();

	boolean canStepBack() {
		return !stack.isEmpty();
	}

	Mode getModeOfLastItem() {
		return stack.getLast().getMode();
	}

	void save(final FullState fullState, Mod nextTurn) {
		stack.addLast(new FreeItem(fullState, nextTurn));
	}

	void save(final List<BDD> route, Mode mode) {
		stack.addLast(new RouteItem(route, mode));
	}

	void reset() {
		for (HistoryItem historyItem : stack) {
			historyItem.clear();
		}
		stack.clear();
	}

	HistoryItem popPreviousItem() {
		if (!canStepBack()) {
			throw new IllegalStateException("cannot get previous step when step history is empty.");
		}
		return stack.removeLast();
	}

	boolean isPrevFree() {
		return stack.getLast().getMode().equals(Mode.FREE);
	}

	abstract static class HistoryItem {
		private final Mode mode;

		public HistoryItem(Mode mode) {
			this.mode = mode;
		}

		public Mode getMode() {
			return mode;
		}

		abstract void clear();

	}

	static class FreeItem extends HistoryItem {
		private final FullState fullState;
		private final Mod nextTurn;

		public FreeItem(FullState fullState, Mod nextTurn) {
			super(Mode.FREE);
			Objects.requireNonNull(fullState);
			if (Objects.isNull(fullState.getState()) || Objects.isNull(fullState.getSuccessors())
					|| fullState.getState().isFree() || fullState.getSuccessors().isFree()) {
				throw new IllegalStateException("cannot save full state with null or free BDDs");
			}
			this.fullState = fullState;
			this.nextTurn = nextTurn;
		}

		FullState getFullState() {
			return fullState;
		}

		Mod getTurn() {
			return nextTurn;
		}

		@Override
		void clear() {
			fullState.reset();
		}
	}

	static class RouteItem extends HistoryItem {
		private List<BDD> route;

		public RouteItem(List<BDD> route, Mode mode) {
			super(ensureGuidedMode(mode));
			this.route = route;
		}

		private static Mode ensureGuidedMode(Mode mode) {
			if (mode.isFree()) {
				throw new IllegalArgumentException("route history item cannot be associated with FREE mode.");
			}
			return mode;
		}

		List<BDD> getRoute() {
			return route;
		}

		@Override
		void clear() {
			if (Objects.nonNull(route)) {
				Env.free(route);
				route = null;
			}
		}
	}

}
