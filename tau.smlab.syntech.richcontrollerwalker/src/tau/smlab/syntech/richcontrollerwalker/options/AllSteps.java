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

public class AllSteps implements IAllSteps {
	private BDD successors;
	private BDD varsByTurn;
	private final List<IStep> steps = new ArrayList<>();

	
	@Override
	public void setNew(BDD successors, BDD varsByTurn) {
		clear();
		this.successors = successors.id();
		this.varsByTurn = varsByTurn;
		steps.clear();
		loadSteps();
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
	
	private void loadSteps() {
		BDDIterator stepsBddIter = getStepsIterator();
		for (int i = 0; stepsBddIter.hasNext(); i++) {
			steps.add(new Step(i, stepsBddIter.next()));
		}
	}
	
	private BDDIterator getStepsIterator() {
		return successors.iterator(varsByTurn.toVarSet());
	}

	@Override
	public Iterator<IStep> iterator() {
		return steps.iterator();
	}
}
