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

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.Deque;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.richcontrollerwalker.bdds.BddUtil;
import tau.smlab.syntech.richcontrollerwalker.util.Mode;
import tau.smlab.syntech.richcontrollerwalker.util.Mod;

public final class Printing {
	private static final String MODE_TRANSITION_TITLE = "Mode Transition";
	private static final String REACH_STATE_TITLE = "Reachability State";
	private static final String LOG_STATE_TITLE = "Log State";
	private static final String ENV_DEADLOCK_MESSAGE = "ENV: Environment deadlock - system has won!";
	private final Deque<String> lastPrints = new ArrayDeque<>();
	private final Deque<Integer> numPrintsHistory = new ArrayDeque<>();
	private final Deque<String> stepsSoFar = new ArrayDeque<>();
	private final PrintStream console;


	public Printing(final PrintStream out) {
		this.console = out;
	}
	
	Deque<String> getStepsSoFar() {
		return stepsSoFar;
	}

	void reset() {
		numPrintsHistory.clear();
		lastPrints.clear();
		stepsSoFar.clear();
	}
	
	void removeLastPrints() {
		if (!lastPrints.isEmpty()) {
			throw new IllegalStateException("There should be no unflushed prints when deleting the previous prints.");
		}
		Integer numPrints = numPrintsHistory.removeLast();
		for (int i = 0; i < numPrints; i++) {
			stepsSoFar.removeLast();
		}
	}
	
	void flushPrints() {
		numPrintsHistory.addLast(lastPrints.size());
		for (String message : lastPrints) {
			stepsSoFar.addLast(message);
		}
		lastPrints.clear();
	}
	
	void printDeadLock() {
		print(ENV_DEADLOCK_MESSAGE);
	}

	void printFreeStep(final Mod turn, final BDD step) {
		print(combineIntoConsoleMessage(turn.toString(), BddUtil.bddToStr(step)));
	}
	
	void printGuidedState(final BDD state, Mode guidedMode) {
		if (guidedMode.isFree()) {
			throw new IllegalArgumentException("cannot print guided state of free mode");
		}
		String state_title = guidedMode.isReach() ? REACH_STATE_TITLE: LOG_STATE_TITLE;
		print(combineIntoConsoleMessage(state_title, state.toString()));
	}

	void printModeTransition(Mode from, Mode to) {
		print(combineIntoConsoleMessage(MODE_TRANSITION_TITLE, "from " + from.toString() + " to " + to.toString()));
	}
	
	private String combineIntoConsoleMessage(String title, String content) {
		return title + ":" + content;
	}

	private void print(final String string) {
		lastPrints.addLast(string);
		console.println(string);
	}
}
