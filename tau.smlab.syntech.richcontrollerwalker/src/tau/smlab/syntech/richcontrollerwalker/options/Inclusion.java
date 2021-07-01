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
import java.util.List;
import java.util.RandomAccess;
import java.util.concurrent.ThreadLocalRandom;

import net.sf.javabdd.BDD;

class Inclusion extends AbstractOption implements IInclusion {
	private static final Collection<Integer> stepIds = new ArrayList<>();

	public Inclusion(int id) {
		super(id);
		// TODO: complete this
	}

	@Override
	public BDD getBdd() {
		// TODO: implement this
		return null;
	}

	@Override
	public String getExpression() {
		// TODO implement this
		return null;
	}

	@Override
	public Collection<Integer> getStepIds() {
		return new ArrayList<>(stepIds);
	}

	@Override
	public void clearStepIds() {
		stepIds.clear();

	}

	@Override
	public void addStepId(int stepId) {
		stepIds.add(stepId);
	}

	@Override
	public int getRandomStepId() {
		return getRandomElement(stepIds);
	}


	private static <E> E getRandomElement(Collection<E> collection) {
		if (collection.isEmpty()) {
			return null;
		}
		int randomIndex = ThreadLocalRandom.current().nextInt(collection.size());

		if (collection instanceof RandomAccess) {
			List<E> list = (List<E>) collection;

			return list.get(randomIndex);
		} else {
			for (E element : collection) {
				if (randomIndex == 0) {
					return element;
				}
				randomIndex--;
			}
			return null; // unreachable
		}
	}

}
