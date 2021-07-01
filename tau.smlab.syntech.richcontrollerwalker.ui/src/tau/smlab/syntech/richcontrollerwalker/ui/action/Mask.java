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

package tau.smlab.syntech.richcontrollerwalker.ui.action;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import tau.smlab.syntech.richcontrollerwalker.ExpressionHelper;
import tau.smlab.syntech.richcontrollerwalker.bdds.BddUtil;

public class Mask implements IMask {
	private final Set<String> hiddenVars = new HashSet<>();
	private static final String PERSISTENT_DELIMITER = ";";
private final OptionExpHelper helper = new OptionExpHelper();


	 public Mask() {
		loadPersistentMask();
	}

	private void loadPersistentMask() {
		Map<Integer, String> loadedMap = ExpressionHelper.getPersistentPropsByPrefix(IMask.PREFIX);
		if (loadedMap.size() > 1) {
			throw new IllegalStateException();
		}
		if (loadedMap.size() == 1) {
			String exp = loadedMap.values().iterator().next();
			setExpression(exp);
		}
	}

	@Override
	public Set<String> getHidden() {
		return new HashSet<>(hiddenVars);
	}

	@Override
	public void setHidden(Collection<String> hidden) {
		removeAll();
		for (String var : hidden) {
			if (BddUtil.isValidVar(var)) {
				hiddenVars.add(var);
			}
		}	
		save();
	}

	@Override
	public String getExpression() {
		return String.join(PERSISTENT_DELIMITER, hiddenVars);
	}

	@Override
	public void setExpression(String expression) {
		hiddenVars.clear();
		setHidden(Arrays.asList(expression.split(PERSISTENT_DELIMITER)));
	}
	


	@Override
	public String transform(String option) {
		String newOpt = option;
		for (String var : hiddenVars) {
			newOpt = helper.remove(newOpt, var);
		}
		return newOpt.isBlank() ? EMPTY_MESSAGE : newOpt;
	}


	@Override
	public void removeAll() {
		hiddenVars.clear();
		unsave();
	}

}
