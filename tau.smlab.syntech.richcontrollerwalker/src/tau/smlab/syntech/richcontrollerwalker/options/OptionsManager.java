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

package tau.smlab.syntech.richcontrollerwalker.options;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDDomain;
import net.sf.javabdd.BDDVarSet;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.richcontrollerwalker.bdds.BddUtil;
import tau.smlab.syntech.richcontrollerwalker.bdds.IValue;
import tau.smlab.syntech.richcontrollerwalker.bdds.IVar;
import tau.smlab.syntech.richcontrollerwalker.bdds.VarClassifier;
import tau.smlab.syntech.richcontrollerwalker.filters.Filter;
import tau.smlab.syntech.richcontrollerwalker.filters.FilterSummary;
import tau.smlab.syntech.richcontrollerwalker.filters.FilterType;
import tau.smlab.syntech.richcontrollerwalker.filters.IFilter;
import tau.smlab.syntech.richcontrollerwalker.util.Mod;
import tau.smlab.syntech.richcontrollerwalker.util.OptionsType;
import tau.smlab.syntech.richcontrollerwalker.util.SelectionMethod;

public class OptionsManager {
	private final IAllSteps allSteps;
	private final IInclusions inclusions = new Inclusions();
	private final IFilter filter = new Filter();
	private SelectionMethod selectionMethod = SelectionMethod.ONLY_STEPS;
	private BDD successors;
	private Mod currentTurn;
	private boolean hideFixed = false;
	private boolean hideDontCares = false;
	private final Map<IVar, IValue> fixedVars = new HashMap<>();
	private final Collection<IVar> dontCares = new ArrayList<>();
	private final Collection<IVar> hidden = new ArrayList<>();
	private boolean showSatCountInSteps = false;

	private BigInteger filteredCount = BigInteger.ZERO;
	private BigInteger totalCount = BigInteger.ZERO;

	public OptionsManager(int maxNumDisplayedOptions) {
		DisplayedOptions.maxNumDisplayedOptions = maxNumDisplayedOptions;
		allSteps = new AllSteps(500);
	}

	public SelectionMethod getSelectionMethod() {
		return selectionMethod;
	}

	public void setSelectionMethod(SelectionMethod newMethod, boolean recomputeInclusions) {
		selectionMethod = newMethod;
		boolean needsRecomputing = recomputeInclusions && selectionMethod.involvesInclusions() && inclusions.isEmpty();
		if (needsRecomputing) {
			computeInclusions();
		}
	}

	public void reset() {
		successors = null;
		allSteps.clear();
		clearComputations();
	}

	private void clearComputations() {
		freeInclusions();
		clearClassifications();
	}

	public FilterSummary getFilterSummary(FilterType type) {
		return filter.getFilterSummary(type);
	}

	private void freeInclusions() {
		inclusions.clear();
	}

	private void clearClassifications() {
		fixedVars.clear();
		dontCares.clear();
	}

	public void removeAllFilters(boolean recomputeOptions) {
		removeFilter(FilterType.DROPDOWN, false);
		removeFilter(FilterType.TEXT, recomputeOptions);
	}

	public void removeFilter(FilterType type, boolean recomputeOptions) {
		filter.remove(type);
		if (recomputeOptions) {
			filterAndClassify();
		}
	}

	public void setFilterActivity(FilterType type, boolean isActive, boolean recomputeOptions) {
		filter.setActivity(type, isActive);
		if (recomputeOptions) {
			filterAndClassify();
		}
	}

	public void addFilter(String expression, FilterType type, boolean recomputeOptions) {
		filter.add(expression, type);
		if (recomputeOptions) {
			filterAndClassify();
		}
	}

	public void updateHiddenVariables(Collection<String> hidden, boolean hideFixed, boolean hideDontCares,
			boolean recomputeOptions) {
		Map<String, IVar> allVars = BddUtil.getAllVarsMap();
		this.hidden.clear();
		this.hidden.addAll(hidden.stream().map(v -> allVars.get(v)).toList());
		this.hideFixed = hideFixed;
		this.hideDontCares = hideDontCares;
		if (recomputeOptions) {
			filterAndClassify();
		}
	}

	public void prepareNewOptions(BDD successors, Mod newTurn) {
		reset();
		this.successors = successors.id();
		this.currentTurn = newTurn;
		filterAndClassify();
	}

	public BDDVarSet getHiddenVarSet() {
		BDDVarSet set = BddUtil.getVarSet(this.hidden);

		if (this.hideFixed) {
			set.unionWith(BddUtil.getVarSet(this.fixedVars.keySet()));
		}

		if (this.hideDontCares) {
			set.unionWith(BddUtil.getVarSet(this.dontCares));
		}

		return set;
	}

	public BDDVarSet getUsedVarSet() {
		BDDVarSet hiddenVars = getHiddenVarSet();
		BDDVarSet turnVars = getCurrentTurnVars();
		return turnVars.minus(hiddenVars);
	}

	public BDD getFilteredSuccessors() {
		return allSteps.getBdd();
	}

	private void applyFilters() {
		BDD filteredSuccessors = successors.and(filter.getBdd());

		classifyVars(filteredSuccessors);

		allSteps.setNew(filteredSuccessors, getUsedVarSet(), getCurrentTurnVars(), showSatCountInSteps);
		allSteps.loadSteps();
	}

	private void computeSatCounts() {
		double totalDouble = Env.getSatCount(successors, getCurrentTurnVars());
		double filteredDouble = Env.getSatCount(getFilteredSuccessors(), getUsedVarSet());

		// Note: there may be some loss of precision
		this.totalCount = BigDecimal.valueOf(totalDouble).toBigInteger();
		this.filteredCount = BigDecimal.valueOf(filteredDouble).toBigInteger();
	}

	private void filterAndClassify() {
		clearComputations();
		applyFilters();
		computeSatCounts();
		if (selectionMethod.involvesInclusions()) {
			computeInclusions();
		}
	}

	private void classifyDontCares(BDD successors) {
		dontCares.addAll(VarClassifier.findDontCareVars(successors));
	}

	private void classifyFixedVars(BDD successors) {
		fixedVars.putAll(VarClassifier.findFixedVars(successors));
	}

	private void classifyVars(BDD successors) {
		classifyDontCares(successors);
		classifyFixedVars(successors);
	}

	private void computeInclusions() {
		// TODO implement this
	}

	public DisplayedOptions getDisplayedOptions() {
		switch (selectionMethod) {
		case ONLY_STEPS:
			return new DisplayedOptions(allSteps, fixedVars, dontCares);
		case INCLUSIONS_THEN_STEPS:
		case ONLY_INCLUSIONS:
			return new DisplayedOptions(inclusions.getCollection(), null, OptionsType.INCLUSIONS, fixedVars, dontCares);
		default:
			throw new IllegalArgumentException("Unexpected value: " + selectionMethod);
		}
	}

	public DisplayedOptions getStepsOfInclusion(int incId) {
		if (selectionMethod != SelectionMethod.INCLUSIONS_THEN_STEPS) {
			throw new IllegalStateException();
		}
		return new DisplayedOptions(allSteps, inclusions.getInclusion(incId).getStepIds(), fixedVars, dontCares);
	}

	public int getRandomStepOfInclusion(int incId) {
		if (selectionMethod != SelectionMethod.ONLY_INCLUSIONS) {
			throw new IllegalStateException();
		}
		return inclusions.getInclusion(incId).getRandomStepId();
	}

	private boolean hasHiddenVariables() {
		if (!hidden.isEmpty()) {
			return true;
		}

		if (hideFixed && !fixedVars.isEmpty()) {
			return true;
		}

		if (hideDontCares && !dontCares.isEmpty()) {
			return true;
		}

		return false;
	}

	private BDDVarSet getCurrentTurnVars() {
		return BddUtil.getVarsByModule(currentTurn).toVarSet();
	}

	public BDD getStep(int stepId) {
		BDD step = allSteps.getStep(stepId).getBdd();
		if (!hasHiddenVariables()) {
			return step;
		}

		// Get a possible assignment to "fill in" the hidden variables
		BDD nextState = step.and(successors);
		BigInteger[] possibleAssignments = nextState.scanAllVar();
		nextState.free();

		BDDVarSet missingVars = getHiddenVarSet().intersectWith(getCurrentTurnVars());
		for (BDDDomain dom : missingVars.getDomains()) {
			step.andWith(dom.ithVar(possibleAssignments[dom.getIndex()]));
		}

		return step;
	}

	public void freeFilters() {
		filter.freeAll();
	}

	public BigInteger getTotalCount() {
		return this.totalCount;
	}

	public BigInteger getFilteredCount() {
		return this.filteredCount;
	}
	
	public void setShowStepSatCount(boolean showSatCount, boolean recomputeOptions) {
		showSatCountInSteps = showSatCount;
		if (recomputeOptions) {
			filterAndClassify();
		}
	}
}
