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

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.richcontrollerwalker.bdds.AbstractPersistent;
import tau.smlab.syntech.richcontrollerwalker.util.Eval;
import tau.smlab.syntech.richcontrollerwalker.util.IBreakpoint;

class Breakpoints {
	private int markedForReachId = -1;
	private final Map<Integer, Breakpoint> breakpoints = new HashMap<>();
	private final List<IBreakpoint> displayList = new LinkedList<>();

	Breakpoints() {
		loadPersistentBreakpoints();
	}

	private int getUnusedId() {
		Set<Integer> currentIds = breakpoints.keySet();
		for (int i = 0; i <= currentIds.size(); i++) {
			if (!currentIds.contains(i)) {
				return i;
			}
		}
		throw new RuntimeException();
	}

	Collection<Breakpoint> getBreakpoints() {
		return breakpoints.values();
	}

	List<IBreakpoint> getDisplayList() {
		return displayList;
	}

	BDD getBreakpointBdd(final int bpId) {
		if (!containsAndValid(bpId)) {
			throw new IllegalArgumentException();
		}
		return breakpoints.get(bpId).getBdd();
	}

	void resetReachabilityMark() {
		markedForReachId = -1;
	}

	void markReachability(final int bpId) {
		if (!containsAndValid(bpId)) {
			throw new IllegalArgumentException();
		}
		markedForReachId = bpId;
	}

	private boolean containsAndValid(final int bpId) {
		if (!breakpoints.containsKey(bpId)) {
			return false;
		}
		Breakpoint bp = breakpoints.get(bpId);
		return Objects.nonNull(bp) && bp.eval().isValid() && Objects.nonNull(bp.getBdd());
	}

	void removeAll() {
		while (!displayList.isEmpty()) {
			int id = displayList.get(0).getId();
			remove(id);
		}
	}

	void remove(final int id) {
		Breakpoint bp = breakpoints.get(id);
		if (bp.isMarkedForReach()) {
			resetReachabilityMark();
		}
		bp.unsave();
		bp.clear();
		displayList.remove(bp);
		breakpoints.remove(id);
	}

	void replace(final int id, final String newExpression) {
		if (!breakpoints.get(id).getExpression().equals(newExpression)) {
			int indexInList = getIndexInList(id);
			remove(id);
			add(id, newExpression, indexInList);
		}
	}

	private int getIndexInList(final int id) {
		for (int i = 0; i < displayList.size(); i++) {
			if (displayList.get(i).getId() == id) {
				return i;
			}
		}
		throw new IllegalArgumentException("the list contains no breakpoint with id: " + id);
	}

	void addNew() {
		int id = getUnusedId();
		Breakpoint bp = new Breakpoint(id);
		breakpoints.put(id, bp);
		displayList.add(displayList.size(), bp);
	}

	void add(final int id, final String expression, final int indexInList) {
		BDD bpBdd = null;
		try {
			bpBdd = ExpressionHelper.getBddFromExpression(expression);
		} catch (Exception e) {
			e.printStackTrace();
		}
		Breakpoint bp;
		if (Objects.isNull(bpBdd)) {
			bp = new Breakpoint(id, expression);
		} else {
			bp = new Breakpoint(id, expression, bpBdd, Eval.UNKNOWN);
			bp.save();
		}
		breakpoints.put(id, bp);
		displayList.add(indexInList, bp);
	}
	
	private void loadPersistentBreakpoints() {
		Map<Integer, String> loadedMap = ExpressionHelper.getPersistentPropsByPrefix(IBreakpoint.PREFIX);
		for (Entry<Integer, String> entry : loadedMap.entrySet()) {
			int id = entry.getKey();
			String exp = entry.getValue();
			add(id, exp, displayList.size());
		}
	}


	void freeAll() {
		for (Breakpoint bp : breakpoints.values()) {
			bp.clear();
		}
	}


	class Breakpoint extends AbstractPersistent implements IBreakpoint {
		private Eval eval = Eval.INVALID;

		private Breakpoint(int id) {
			this(id, EXP_PLACEHOLDER);
		}

		private Breakpoint(int id, String expression) {
			super(expression, id);
			this.eval = Eval.INVALID;
		}

		private Breakpoint(int id, String expression, BDD newBdd, Eval newEval) {
			super(newBdd, expression, id);
			this.eval = Objects.requireNonNull(newEval);
		}

		@Override
		public Eval eval() {
			return eval;
		}

		void setEval(Eval newEval) {
			this.eval = newEval;
		}

		@Override
		public boolean isMarkedForReach() {
			return getId() == markedForReachId;
		}

		@Override
		public String toString() {
			String description = "Breakpoint [id=" + getId() + ", expression=" + getExpression();
			description += ", eval=" + eval;
			description += ", isMarkedForReach=" + isMarkedForReach();
			description += "]";
			return description;
		}
	}
}
