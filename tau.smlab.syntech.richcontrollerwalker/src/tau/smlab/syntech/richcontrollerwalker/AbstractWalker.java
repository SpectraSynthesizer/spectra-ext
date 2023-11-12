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
import java.io.PrintStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.IFile;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDVarSet;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.richcontrollerwalker.Breakpoints.Breakpoint;
import tau.smlab.syntech.richcontrollerwalker.Engine.FullState;
import tau.smlab.syntech.richcontrollerwalker.History.HistoryItem;
import tau.smlab.syntech.richcontrollerwalker.filters.FilterSummary;
import tau.smlab.syntech.richcontrollerwalker.filters.FilterType;
import tau.smlab.syntech.richcontrollerwalker.options.DisplayedOptions;
import tau.smlab.syntech.richcontrollerwalker.options.IOptionsReply;
import tau.smlab.syntech.richcontrollerwalker.options.OptionsManager;
import tau.smlab.syntech.richcontrollerwalker.options.PartialAssignment;
import tau.smlab.syntech.richcontrollerwalker.util.Eval;
import tau.smlab.syntech.richcontrollerwalker.util.IBreakpoint;
import tau.smlab.syntech.richcontrollerwalker.util.Mode;
import tau.smlab.syntech.richcontrollerwalker.util.Mod;
import tau.smlab.syntech.richcontrollerwalker.util.Modules;
import tau.smlab.syntech.richcontrollerwalker.util.Preferences;
import tau.smlab.syntech.richcontrollerwalker.util.SelectionMethod;

abstract class AbstractWalker {
	private final String workingDir;
	private final Modules user;
	private final Logger logger;
	private final Route route;
	private final Breakpoints breakpoints;
	private final Printing printing;
	private final Engine engine;
	private final History history;
	private final OptionsManager options;
	private Mode mode;

	AbstractWalker(final PrintStream out, final IFile specFile, final Modules chosenUser, final Preferences preferences)
			throws IOException {
		Objects.requireNonNull(preferences).setCurrentBDDPackage();
		history = new History();
		route = new Route();
		user = Objects.requireNonNull(chosenUser);
		printing = new Printing(Objects.requireNonNull(out));
		Spec spec = new Spec(Objects.requireNonNull(specFile));
		ExpressionHelper.setSpec(spec);
		workingDir = spec.getWorkingDir();
		engine = new Engine(workingDir + File.separator + "out", spec.getModelName(), preferences.useJitController());
		logger = new Logger(preferences.isLogActive(), spec.getFilePath(), spec.getModelName());
		breakpoints = new Breakpoints();
		options = new OptionsManager(preferences.getMaxNumDisplayedSteps());

	}

	protected void resetFullState() {
		engine.getFullState().reset();
	}

	protected void restartFullState() {
		engine.getFullState().restart();
	}

	void applyEnvChoice(BDD step) {
		BDD succ = engine.getFullState().getSuccessors().and(step);
		engine.getFullState().getSuccessors().free();
		engine.getFullState().setSuccessors(succ);
	}

	void applySysChoice(BDD step) {
		BDD state = engine.getFullState().getSuccessors().and(step);
		engine.getFullState().freeAndSetNewStep(state);
	}

	void freeAndSetFullState(FullState newFullState) {
		engine.freeAndSetFullState(newFullState);
	}

	public Mode getMode() {
		return mode;
	}

	protected void setMode(Mode newMode) {
		mode = newMode;
	}

	protected void resetMode() {
		mode = Mode.FREE;
	}

	protected void requireFreeMode() {
		if (!mode.isFree()) {
			throw new IllegalStateException("must be in free mode to perform this action.");
		}
	}

	protected void requireRouteMode() {
		if (mode.isFree()) {
			throw new IllegalStateException("cannot perform this action in free mode.");
		}
	}

	public Mod getTurn() {
		return engine.getTurn();
	}

	protected void setTurn(Mod newTurnVal) {
		engine.setTurn(newTurnVal);
	}

	protected void resetTurn() {
		setTurn(Mod.ENV);
	}

	protected void toggleTurn() {
		setTurn(getTurn().equals(Mod.ENV) ? Mod.SYS : Mod.ENV);
	}

	protected boolean isUserTurn() {
		return mode.isFree() && (getTurn().isSys() && !user.isOnlyEnv()) || (getTurn().isEnv() && !isUserOnlySys());
	}

	protected BDD getCurrentState() {
		return engine.getFullState().getState();
	}

	protected void setCurrentState(BDD newCurrentState) {
		engine.getFullState().setState(newCurrentState);
	}

	protected void setStateAndUpdateSuccessors(BDD newState) {
		engine.getFullState().freeAndSetNewStep(newState);
	}

	public boolean isDeadlock() {
		return engine.getFullState().isDeadlock();
	}

	protected void requireNoDeadlock() {
		if (isDeadlock()) {
			throw new IllegalStateException("cannot make any moves when in deadlock.");
		}
	}

	public boolean isReachable(final int bpId) {
		return engine.isReachable(getBreakpointBdd(bpId));
	}

	protected List<BDD> computeReachRoute(final int bpId) {
		return engine.computeReachRoute(getBreakpointBdd(bpId));
	}

	protected BDD getRandomMove() {
		return engine.getRandomMove();
	}

	protected BDD getInitialState() {
		return engine.getInitialState();
	}

	protected List<BDD> loadLogRoute(final String path) {
		return route.loadLogRoute(path);
	}

	protected void setRoute(final List<BDD> loadedRoute) {
		route.setRoute(loadedRoute);
	}

	protected List<BDD> getRoutePrefixCopy() {
		return route.getRoutePrefixCopy();
	}

	public boolean isRouteStart() {
		requireRouteMode();
		return route.isStart();
	}

	public boolean isRouteEnd() {
		requireRouteMode();
		return route.isEnd();
	}

	protected BDD getCurrentRouteState() {
		return route.getCurrentState();
	}

	protected BDD getNextRouteState() {
		requireRouteMode();
		return route.nextState();
	}

	protected BDD routeStartState() {
		requireRouteMode();
		return route.startState();
	}

	protected BDD routeLastState() {
		requireRouteMode();
		return route.lastState();
	}

	public int numRemainingRouteStates() {
		requireRouteMode();
		return route.numRemainingStates();
	}

	public int numRouteStates() {
		requireRouteMode();
		return route.getNumStates();
	}

	protected BDD advanceRoute() {
		requireRouteMode();
		return route.advance();
	}

	protected boolean canRouteStepBack() {
		requireRouteMode();
		return route.canStepBack();
	}

	protected BDD stepBackRoute() {
		requireRouteMode();
		return route.stepBack();
	}

	protected void resetRoute(boolean freeRouteStates) {
		route.reset(freeRouteStates);
	}

	protected int getStepIndexInRoute() {
		requireRouteMode();
		return route.getCurrentIndex();
	}

	protected void deleteLastLoggedState() {
		logger.removeLastState();
	}

	public boolean isGeneratingLog() {
		return logger.isGenerating();
	}

	public String getLogFileName() {
		return logger.getFileName();
	}

	public void toggleLog() {
		logger.toggle();
	}

	private void createNewLog() {
		logger.createNew();
	}

	protected void createNewLogIfGenerating() {
		if (isGeneratingLog()) {
			createNewLog();
		}
	}

	protected void writeToLogIfGenerating() {
		if (isGeneratingLog()) {
			writeToLog();
		}
	}

	private void writeToLog() {
		logger.writeState(getCurrentState());
	}

	protected void flushLogIfGenerating() {
		if (isGeneratingLog()) {
			logger.flush();
		}
	}

	protected void markReachability(int bpId) {
		breakpoints.markReachability(bpId);
	}

	protected void resetReachabilityMark() {
		breakpoints.resetReachabilityMark();
	}

	public void addNewBreakpoint() {
		breakpoints.addNew();
	}

	public void replaceBreakpoint(final int bpId, final String newExpression) {
		breakpoints.replace(bpId, newExpression);
	}

	public void removeBreakpoint(int bpId) {
		breakpoints.remove(bpId);
	}

	private BDD getBreakpointBdd(int bpId) {
		return breakpoints.getBreakpointBdd(bpId);
	}

	public List<IBreakpoint> getBreakpointsList() {
		return breakpoints.getDisplayList();
	}

	protected void freeBreakpoints() {
		breakpoints.freeAll();
	}

	public void updateBreakpoints() {
		boolean isInitial = !hasHistory() && (getMode().isFree() || !canRouteStepBack());
		boolean isAfterFreeEnvStep = getTurn().isSys() && getMode().isFree();
		for (Breakpoint bp : breakpoints.getBreakpoints()) {
			if (bp.eval().isValid()) {
				if (isInitial || (isAfterFreeEnvStep && !isBpOnlyVar(bp))) {
					bp.setEval(Eval.UNKNOWN);
				} else {
					BDD andResult = bp.getBdd().id().andWith(getCurrentState().id());
					boolean isBpTrue = !andResult.equals(Env.FALSE());
					andResult.free();
					bp.setEval(isBpTrue ? Eval.TRUE : Eval.FALSE);
				}
			}
		}
	}

	private boolean isBpOnlyVar(Breakpoint bp) {
		return false; // TODO: implement this
	}

	public void removeAllBreakpoints() {
		breakpoints.removeAll();
	}

	protected void printDeadlock() {
		printing.printDeadLock();
	}

	protected void printFreeStep(BDD step) {
		printing.printFreeStep(getTurn(), step);
	}

	protected void printGuidedState(final BDD state) {
		printing.printGuidedState(state, getMode());
	}

	protected void printModeTransition(Mode from, Mode to) {
		printing.printModeTransition(from, to);
	}

	public Deque<String> getStepsSoFar() {
		return printing.getStepsSoFar();
	}

	protected void removeLastPrints() {
		printing.removeLastPrints();
	}

	protected void flushPrints() {
		printing.flushPrints();
	}

	/**
	 * Clear the prints array
	 */
	protected void resetPrinting() {
		printing.reset();
	}

	public Modules getUser() {
		return user;
	}

	public boolean isUserBoth() {
		return user.isBoth();
	}

	protected boolean isUserOnlySys() {
		return user.isOnlySys();
	}

	protected void saveFullState() {
		history.save(engine.getCopyOfFullState(), getTurn());
	}

	protected void saveRoute(List<BDD> route, Mode mode) {
		history.save(route, mode);
	}

	protected boolean hasHistory() {
		return history.canStepBack();
	}

	protected Mode modeOfPrevItem() {
		if (!getMode().isFree()) {
			throw new IllegalStateException("this method serves no purpose while in guided mode.");
		}
		return history.getModeOfLastItem();
	}

	protected HistoryItem popHistory() {
		return history.popPreviousItem();
	}

	protected void resetHistory() {
		history.reset();
	}

	public SelectionMethod getSelectionMethod() {
		return options.getSelectionMethod();
	}

	public IOptionsReply setSelectionMode(SelectionMethod newMethod) {
		options.setSelectionMethod(newMethod, getMode().isFree());
		return getMode().isFree() ? getDisplayOptions() : IOptionsReply.emptyOptions();
	}

	protected void prepareNewOptions() {
		requireFreeMode();
		options.prepareNewOptions(engine.getFullState().getSuccessors(), getTurn());
	}

	protected void resetOptions() {
		options.reset();
	}

	public BDD getChosenStep(int stepId) {
		requireFreeMode();
		return options.getStep(stepId);
	}

	int getRandomStepOfInclusion(int incId) {
		return options.getRandomStepOfInclusion(incId);
	}

	DisplayedOptions getAllStepsOfInclusion(int incId) {
		requireFreeMode();
		return options.getStepsOfInclusion(incId);
	}

	public DisplayedOptions getDisplayOptions() {
		requireFreeMode();
		return options.getDisplayedOptions();
	}

	public FilterSummary getFilterSummary(FilterType type) {
		return options.getFilterSummary(type);
	}

	public IOptionsReply updateHiddenVariables(Collection<String> hidden, boolean hideFixed, boolean hideDontCares) {
		options.updateHiddenVariables(hidden, hideFixed, hideDontCares, getMode().isFree());
		return getMode().isFree() ? getDisplayOptions() : IOptionsReply.emptyOptions();
	}

	public IOptionsReply setShowStepSatCount(boolean showSatCount) {
		options.setShowStepSatCount(showSatCount, getMode().isFree());
		return getMode().isFree() ? getDisplayOptions() : IOptionsReply.emptyOptions();
	}

	public IOptionsReply setFilterActivity(FilterType filterType, boolean isActive) {
		options.setFilterActivity(filterType, isActive, getMode().isFree());
		return getMode().isFree() ? getDisplayOptions() : IOptionsReply.emptyOptions();
	}

	public IOptionsReply addFilter(String expression, FilterType filterType) {
		options.addFilter(expression, filterType, getMode().isFree());
		return getMode().isFree() ? getDisplayOptions() : IOptionsReply.emptyOptions();
	}

	public IOptionsReply removeFilter(FilterType filterType) {
		options.removeFilter(filterType, getMode().isFree());
		return getMode().isFree() ? getDisplayOptions() : IOptionsReply.emptyOptions();
	}

	public IOptionsReply removeAllFilters() {
		options.removeAllFilters(getMode().isFree());
		return getMode().isFree() ? getDisplayOptions() : IOptionsReply.emptyOptions();
	}

	protected void freeFilters() {
		options.freeFilters();
	}

	public OptionsManager getOptionsManager() {
		return this.options;
	}

	public BigInteger getTotalOptionCount() {
		return options.getTotalCount();
	}

	public BigInteger getFilteredOptionCount() {
		return options.getFilteredCount();
	}

	/**
	 * Returns path to working directory
	 */
	public String getWorkingDir() {
		return workingDir;
	}
	
	public ArrayList<PartialAssignment> getPartialAssignments(List<String> varOrder, int depth) {
		BDD successors = options.getFilteredSuccessors();
		BDDVarSet usedVars = options.getUsedVarSet();
		return PartialAssignment.getSmallestPartialAssignments(usedVars, varOrder, successors, depth);
	}
}