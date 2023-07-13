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

package tau.smlab.syntech.richcontrollerwalker.filters;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.richcontrollerwalker.ExpressionHelper;
import tau.smlab.syntech.richcontrollerwalker.bdds.AbstractPersistent;

public class Filter implements IFilter {
	private IFilterExp dropdownFilter;
	private IFilterExp textFilter;
	private IFilterExp smartFilter;

	public Filter() {
		loadPersistentFilters();
	}

	public IFilterExp getFilterExp(FilterType type) {
		switch (type) {
		case DROPDOWN:
			return dropdownFilter;
		case TEXT:
			return textFilter;
		case SMART:
			return smartFilter;
		}
		
		throw new IllegalArgumentException();
	}

	private void setExp(IFilterExp fe, FilterType type) {
		switch(type) {
		case DROPDOWN:
			dropdownFilter = fe;
			break;
		case TEXT:
			textFilter = fe;
			break;
		case SMART:
			smartFilter = fe;
		}
	}

	private void loadPersistentFilters() {
		Map<Integer, String> loadedMap = ExpressionHelper.getPersistentPropsByPrefix(IFilterExp.PREFIX);
		for (Entry<Integer, String> entry : loadedMap.entrySet()) {
			int id = entry.getKey();
			if (id == IFilterExp.SMART_ID) {
				// Never restore the smart filter
				continue;
			}
			if (id != IFilterExp.DROPDOWN_ID && id != IFilterExp.TEXT_ID) {
				throw new IllegalArgumentException();
			}
			FilterType type = id == IFilterExp.DROPDOWN_ID ? FilterType.DROPDOWN : FilterType.TEXT;
			String exp = entry.getValue();
			add(exp, type);
		}
	}

	private BDD combinedBdd() {
		BDD combined = Env.TRUE();
		if (dropdownFilter != null && dropdownFilter.isValid()) {
			combined = dropdownFilter.getBdd().id();
		}
		if (textFilter != null && textFilter.isValid()) {
			combined = combined.andWith(textFilter.getBdd().id());
		}
		if (smartFilter != null && smartFilter.isValid()) {
			combined = combined.andWith(smartFilter.getBdd().id());
		}
		return combined;
	}

	@Override
	public void freeAll() {
		if (dropdownFilter != null && dropdownFilter.isValid()) {
			dropdownFilter.clear();
		}
		if (textFilter != null && textFilter.isValid()) {
			textFilter.clear();
		}
		if (smartFilter != null && smartFilter.isValid()) {
			smartFilter.clear();
		}
	}

	@Override
	public void add(String expression, FilterType type) {
		remove(type);
		BDD feBdd = null;
		try {
			feBdd = ExpressionHelper.getBddFromExpression(expression);
		} catch (Exception e) {
			e.printStackTrace();
		}
		IFilterExp fe;
		if (Objects.isNull(feBdd)) {
			fe = new FilterExp(expression, type);
		} else {
			fe = new FilterExp(feBdd, expression, type);
			fe.save();
		}
		fe.setActivity(true);
		setExp(fe, type);
	}

	@Override
	public void remove(FilterType type) {
		IFilterExp fe = getFilterExp(type);
		delFilter(fe);
		nullifyField(type);
	}

	private void delFilter(IFilterExp fe) {
		if (Objects.nonNull(fe)) {
			fe.clear();
			fe.unsave();
		}
	}

	private void nullifyField(FilterType type) {
		switch(type) {
		case DROPDOWN:
			dropdownFilter = null;
			break;
		case TEXT:
			textFilter = null;
			break;
		case SMART:
			smartFilter = null;
		}
	}

	@Override
	public void setActivity(FilterType type, boolean isActive) {
		IFilterExp fe = getFilterExp(type);
		if (fe != null) {
			fe.setActivity(isActive);
		}
	}

	@Override
	public boolean isActive(FilterType type) {
		IFilterExp fe = getFilterExp(type);
		return fe != null && fe.isActive();
	}

	public static class FilterExp extends AbstractPersistent implements IFilterExp {
		private boolean active;
		private final FilterType type;

		public FilterExp(BDD bdd, String exp, FilterType type) {
			super(bdd, exp, IFilterExp.getTypeId(type));
			this.type = type;
		}

		public FilterExp(String exp, FilterType type) {
			super(exp, IFilterExp.getTypeId(type));
			this.type = type;
		}

		@Override
		public void setActivity(boolean isActive) {
			active = isActive;
		}

		@Override
		public boolean isActive() {
			return active;
		}

		@Override
		public FilterType getType() {
			return type;
		}
	}

	@Override
	public FilterSummary getFilterSummary(FilterType type) {
		return new FilterSummary(getFilterExp(type), type);
	}

	@Override
	public BDD getBdd() {
		return combinedBdd();
	}
}
