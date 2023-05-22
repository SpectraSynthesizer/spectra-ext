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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import tau.smlab.syntech.richcontrollerwalker.bdds.IValue;
import tau.smlab.syntech.richcontrollerwalker.bdds.IVar;
import tau.smlab.syntech.richcontrollerwalker.filters.IFilter;
import tau.smlab.syntech.richcontrollerwalker.util.OptionsType;

public class DisplayedOptions implements IOptionsReply {
	static int maxNumDisplayedOptions;
	private final IAllSteps allSteps;
	private final IFilter filter;
	private final List<String> allOptions = new ArrayList<>();
	private final Map<Integer, Integer> idMap = new HashMap<>();
	private final OptionsType type;
	private final Map<String, String> fixed = new HashMap<>();
	private Collection<String> dontCares = new HashSet<>();
	private int batchIdx = 0;

	DisplayedOptions(Collection<? extends IOption> optList, IAllSteps allSteps, IFilter filter, OptionsType type, Map<IVar, IValue> fixedVars,
			Collection<IVar> dontCareVars) {
		this.type = type;
		this.allSteps = allSteps;
		this.filter = filter;
		prepareOptions(optList);
		computeSpecialVars(fixedVars, dontCareVars);
	}
	
	private void prepareOptions(Collection<? extends IOption> optList) {
		Iterator<? extends IOption> iter = optList.iterator();
		while (iter.hasNext()) {
			IOption opt = iter.next();
			allOptions.add(opt.getExpression());
			idMap.put(idMap.size(), opt.getId());
		}
	}
	
	public Map<String, String> getFixed() {
		return fixed;
	}
	
	public Collection<String> getDontCares() {
		return dontCares;
	}

	DisplayedOptions(IAllSteps allSteps, IFilter filter, Map<IVar, IValue> fixedVars,
			Collection<IVar> dontCares) {
		this(filter(allSteps.getCollection(), filter), allSteps, filter, OptionsType.STEPS, fixedVars, dontCares);
	}
	
	DisplayedOptions(IAllSteps allSteps, Collection<Integer> stepsIds, Map<IVar, IValue> fixedVars,
			Collection<IVar> dontCares) {
		this(filter(allSteps, stepsIds), allSteps, null, OptionsType.STEPS, fixedVars, dontCares);
	}

	private void computeSpecialVars(Map<IVar, IValue> fixedVars, Collection<IVar> dontCareVars) {
		for (IVar iVar : fixedVars.keySet()) {
			fixed.put(iVar.name(), fixedVars.get(iVar).name());
		}
		for (IVar iVar : dontCareVars) {
			dontCares.add(iVar.name());
		}
	}

	public int getOptId(int localIdx) {
		Integer index = batchIdx * maxNumDisplayedOptions + localIdx;
		return idMap.get(index);
	}

	private static Collection<? extends IOption> filter(Collection<IStep> allSteps, IFilter filter) {
		
		Collection<IOption> l = new ArrayList<>();
		for (IStep step : allSteps) {
			if (filter.isSatisfying(step)) {
				l.add(step);
			}
		}

		return l;
	}
	
	private static Collection<? extends IOption> filter(IAllSteps allSteps, Collection<Integer> stepsIds) {
		
		Collection<IOption> l = new ArrayList<>();
		for (IStep step : allSteps) {
			if (stepsIds.contains(step.getId())) {
				l.add(step);
			}
		}

		return l;
	}

	public OptionsType getType() {
		return type;
	}

	@Override
	public DisplayedOptions getDisplayedOptions() {
		return this;
	}

	public boolean hasMoreOptions() {
		return (allOptions.size() > maxNumDisplayedOptions * (batchIdx+1)) && 
				(allSteps == null || allSteps.iterator().hasNext());
	}

	@Override
	public List<String> strList() {
		int fromIndex = batchIdx * maxNumDisplayedOptions;
		
		if (fromIndex + maxNumDisplayedOptions > allOptions.size() && allSteps != null && filter != null) {
			System.out.println(String.format("Number of options is %d, loading until %d", allOptions.size(), fromIndex + maxNumDisplayedOptions));
			List<IStep> newSteps = allSteps.loadSteps();
			prepareOptions(filter(newSteps, filter));
		}
		
		int toIndex = Math.min(allOptions.size(), fromIndex + maxNumDisplayedOptions);

		return allOptions.subList(fromIndex, toIndex);
	}

	@Override
	public List<String> nextStrList() {
		if (!hasMoreOptions()) {
			throw new IllegalStateException();
		}
		
		batchIdx++;
		List<String> strList = strList();
		return strList;
	}

	@Override
	public boolean isEmpty() {
		return allOptions.isEmpty();
	}

}
