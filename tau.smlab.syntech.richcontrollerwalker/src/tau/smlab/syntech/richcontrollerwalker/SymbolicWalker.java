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
import java.io.PrintStream;
import java.util.List;

import org.eclipse.core.resources.IFile;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.richcontrollerwalker.History.RouteItem;
import tau.smlab.syntech.richcontrollerwalker.options.IOptionsReply;
import tau.smlab.syntech.richcontrollerwalker.util.Mode;
import tau.smlab.syntech.richcontrollerwalker.util.Modules;
import tau.smlab.syntech.richcontrollerwalker.util.Preferences;

public class SymbolicWalker extends AbstractWalker {

	public SymbolicWalker(final PrintStream out, final IFile specFile, final Modules userModule,
			final Preferences preferences) throws IOException {
		super(out, specFile, userModule, preferences);
		basicReset();
		restartFullState();
		createNewLogIfGenerating();
		prepareFirstFreeStep();
	}

	private void prepareFirstFreeStep() {
		requireFreeMode();
		if (!isDeadlock()) {
			if (isUserOnlySys()) {
				BDD step = getRandomMove();
				printFreeStep(step);
				applyEnvChoice(step);
				flushPrints();
				toggleTurn();
				step.free();
			}
			prepareNewOptions();
		}
	}

	public IOptionsReply reset() {
		resetRoute(true);
		basicReset();
		restartFullState();
		createNewLogIfGenerating();
		prepareFirstFreeStep();
		return getDisplayOptions();
	}

	private void basicReset() {
		flushLogIfGenerating();
		resetPrinting();
		resetHistory();
		resetOptions();
		resetTurn();
		resetMode();
		resetRoute(false);
	}

	public void close() {
		resetRoute(true);
		basicReset();
		resetFullState();
		freePersistent();
	}
	
	private void freePersistent() {
		freeBreakpoints();
		freeFilters();
	}

	public IOptionsReply startLogWalk(final String path) {
		requireFreeMode();
		final List<BDD> loadedRoute = loadLogRoute(path);
		return loadedRoute.isEmpty() ? IOptionsReply.emptyOptions() : loadLogWalk(loadedRoute);
	}

	private IOptionsReply loadLogWalk(final List<BDD> loadedRoute) {
		basicReset();
		resetFullState();
		createNewLogIfGenerating();
		setMode(Mode.LOG);
		loadedRoute.add(0, getInitialState());
		setRoute(loadedRoute);
		setStateAndUpdateSuccessors(routeStartState());
		createNewLogIfGenerating();
		printModeTransition(Mode.FREE, Mode.LOG);
		flushPrints();
		return IOptionsReply.RouteOption(getNextRouteState());
	}

	public IOptionsReply startReachability(final int bpId) {
		requireFreeMode();
		if (!isReachable(bpId)) {
			throw new IllegalArgumentException("cannot play reachability when destination is unreachable.");
		}
		markReachability(bpId);
		return loadReachWalk(computeReachRoute(bpId));
	}

	private IOptionsReply loadReachWalk(final List<BDD> loadedRoute) {
		resetRoute(false);
		resetOptions();
		setMode(Mode.REACH);
		setRoute(loadedRoute);
		saveFullState();
		resetFullState();
		setStateAndUpdateSuccessors(routeStartState());
		printModeTransition(Mode.FREE, Mode.REACH);
		flushPrints();
		return isRouteEnd() ? IOptionsReply.emptyOptions() : IOptionsReply.RouteOption(getNextRouteState());
	}

	public IOptionsReply exitRoute() {
		requireRouteMode();
		if (getMode().isReach()) {
			resetReachabilityMark();
		}
		if (isRouteStart()) {
			if (getMode().isLog() || !canStepBack()) {
				return reset();
			} else {
				removeLastPrints();
				resetOptions();
				return stepBackFromReachToFree();
			}
		}

		printModeTransition(getMode(), Mode.FREE);
		resetTurn();
		saveRoute(getRoutePrefixCopy(), getMode());
		resetMode();
		setStateAndUpdateSuccessors(getCurrentState().id());
		resetRoute(false);
		if (isDeadlock()) {
			printDeadlock();
			flushPrints();
			return IOptionsReply.emptyOptions();
		}
		if (isUserOnlySys()) {
			BDD step = getRandomMove();
			printFreeStep(step);
			applyEnvChoice(step);
			toggleTurn();
			step.free();
		}
		flushPrints();
		return prepareAndGetSteps();
	}

	public IOptionsReply doNextStep(int stepId) {
		return getMode().isFree() ? doFreeStep(stepId) : doRouteStep();
	}

	public IOptionsReply doRouteStep() {
		requireRouteMode();
		if (isRouteEnd()) {
			throw new IllegalStateException("cannot do route step when at the end of the route");
		}
		setCurrentState(advanceRoute());
		printGuidedState(getCurrentState());
		writeToLogIfGenerating();
		flushPrints();
		if (isRouteEnd()) {
			return IOptionsReply.emptyOptions();
		}
		return IOptionsReply.RouteOption(getNextRouteState());
	}

	public IOptionsReply selectInclusion(int incId) {
		switch (getSelectionMethod()) {
		case INCLUSIONS_THEN_STEPS:
			return getAllStepsOfInclusion(incId);
		case ONLY_INCLUSIONS:
			return doFreeStep(getRandomStepOfInclusion(incId));
		default:
			throw new IllegalArgumentException("Unexpected value: " + getSelectionMethod());
		}
	}

	public IOptionsReply doFreeStep(final int stepId) {
		requireNoDeadlock();
		requireFreeMode();
		if (!isUserTurn()) {
			throw new IllegalStateException("user cannot move when it is the engine's turn.");
		}
		saveFullState();

		handleStep(getChosenStep(stepId));
		
		if (!isDeadlock() && !isUserBoth()) {
			handleStep(getRandomMove());
		}
		flushPrints();
		return isDeadlock() ? IOptionsReply.emptyOptions() : prepareAndGetSteps();
	}
	
	private void handleStep(BDD step) {
		printFreeStep(step);
		if (getTurn().isEnv()) {
			applyEnvChoice(step);
		} else {
			applySysChoice(step);
			writeToLogIfGenerating();
			if (isDeadlock()) {
				printDeadlock();
			}
		}
		toggleTurn();
		step.free();
	}

	private IOptionsReply prepareAndGetSteps() {
		requireFreeMode();
		prepareNewOptions();
		return getDisplayOptions();
	}

	public boolean canStepBack() {
		return hasHistory() || (!getMode().isFree() && canRouteStepBack());
	}

	public IOptionsReply stepBack() {
		if (!canStepBack()) {
			throw new IllegalStateException("Cannot step back. Need to check by first calling canStepBack().");
		}
		resetOptions();
		removeLastPrints();
		if (getMode().isFree()) {
			return modeOfPrevItem().isFree() ? stepBackFromFreeToFree() : stepBackFromFreeToRoute();
		}
		return isRouteStart() ? stepBackFromReachToFree() : stepBackFromRouteToRoute();
	}

	private IOptionsReply stepBackFromFreeToFree() {
		History.FreeItem freeItem = (History.FreeItem) popHistory();
		freeAndSetFullState(freeItem.getFullState());
		if (isGeneratingLog() && !(isUserBoth() && getTurn().isSys())) {
			deleteLastLoggedState();
		}
		setTurn(freeItem.getTurn());
		return prepareAndGetSteps();
	}

	private IOptionsReply stepBackFromFreeToRoute() {
		resetFullState();
		resetTurn();
		final History.RouteItem routeItem = (RouteItem) popHistory();
		setMode(routeItem.getMode());
		setRoute(routeItem.getRoute());
		setStateAndUpdateSuccessors(routeLastState());
		return IOptionsReply.emptyOptions();
	}

	private IOptionsReply stepBackFromRouteToRoute() {
		setCurrentState(stepBackRoute());
		if (isGeneratingLog()) {
			deleteLastLoggedState();
		}
		return IOptionsReply.RouteOption(getNextRouteState());
	}

	private IOptionsReply stepBackFromReachToFree() {
		if (!getMode().isReach()) {
			throw new IllegalStateException(
					"cannot step back from Reachability into free mode, when not in reachability mode.");
		}
		resetRoute(true);
		resetMode();
		History.FreeItem freeItem = (History.FreeItem) popHistory();
		freeAndSetFullState(freeItem.getFullState());
		setTurn(freeItem.getTurn());

		return prepareAndGetSteps();
	}

}