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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.richcontrollerwalker.bdds.BddUtil;
import tau.smlab.syntech.richcontrollerwalker.bdds.IValue;
import tau.smlab.syntech.richcontrollerwalker.bdds.IVar;
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
	private final Map<IVar, IValue> fixedVars = new HashMap<>();
	private final Collection<IVar> dontCares = new ArrayList<>();

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


	public void prepareNewOptions(BDD successors, Mod newTurn) {
		reset();
		allSteps.setNew(successors, BddUtil.getVarsByModule(newTurn));
		allSteps.loadSteps();
		filterAndClassify();
	}

	private void filterAndClassify() {
		clearComputations();
		classifyVars();
		if (selectionMethod.involvesInclusions()) {
			computeInclusions();
		}
	}

	private void classifyVars() {
		// TODO: implement this
	}
	
	private void computeInclusions() {
		// TODO implement this
	}
	
	public DisplayedOptions getDisplayedOptions() {
		switch (selectionMethod) {
		case ONLY_STEPS:
			return new DisplayedOptions(allSteps, filter, fixedVars, dontCares);
		case INCLUSIONS_THEN_STEPS:
		case ONLY_INCLUSIONS:
			return new DisplayedOptions(inclusions.getCollection(), null, null, OptionsType.INCLUSIONS, fixedVars, dontCares);
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

	public BDD getStep(int stepId) {
		return allSteps.getStep(stepId).getBdd();
	}

	public void freeFilters() {
		filter.freeAll();
	}
}
