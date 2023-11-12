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
import java.util.Iterator;
import java.util.List;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDD.BDDIterator;
import net.sf.javabdd.BDDVarSet;
import tau.smlab.syntech.jtlv.Env;

public class AllSteps implements IAllSteps {
	private BDD successors;
	private BDDVarSet turnVars;
	private final List<IStep> steps = new ArrayList<>();
	private BDDIterator iterator;
	private int maxOptionsToLoad;
	private boolean includeSatCount = false;

	public AllSteps(int maxOptionsToLoad) {
		this.maxOptionsToLoad = maxOptionsToLoad;
	}

	@Override
	public void setNew(BDD successors, BDDVarSet varsToShow, BDDVarSet turnVars, boolean includeSatCount) {
		clear();
		this.successors = successors.id();
		this.iterator = successors.iterator(varsToShow);
		this.turnVars = turnVars;
		this.includeSatCount = includeSatCount;
		steps.clear();
	}

	@Override
	public BDD getBdd() {
		return successors;
	}

	@Override
	public IStep getStep(int stepId) {
		return steps.get(stepId);
	}

	@Override
	public Collection<IStep> getCollection() {
		return steps;
	}

	public List<IStep> loadSteps() {

		int i = 0;
		List<IStep> newSteps = new ArrayList<>();
		while (iterator.hasNext() && i < maxOptionsToLoad) {
			BDD step = iterator.next();
			int satCount = includeSatCount ? getSatCount(step) : -1;
			newSteps.add(new Step(steps.size() + newSteps.size(), step, satCount));
			i++;
		}
		steps.addAll(newSteps);

		return newSteps;
	}

	private int getSatCount(BDD step) {
		BDD next = successors.and(step);
		int count = (int) Env.getSatCount(next, turnVars);
		next.free();
		return count;
	}

	@Override
	public Iterator<IStep> iterator() {
		return steps.iterator();
	}
}
