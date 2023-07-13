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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;

import tau.smlab.syntech.richcontrollerwalker.ExpressionHelper;
import tau.smlab.syntech.richcontrollerwalker.bdds.BddUtil;
import tau.smlab.syntech.richcontrollerwalker.util.Mod;

public class VarConfiguration implements IVarConfiguration {
	private final Set<String> hiddenVars = new HashSet<>();
	private final List<String> sysVars = new ArrayList<>();
	private final List<String> envVars = new ArrayList<>();
	private boolean hideDontCares = false;
	private boolean hideFixed = false;
	private static final String SEQUENCE_DELIMITER = ";";
	private static final String IN_SEQUENCE_DELIMITER = "@";

	public VarConfiguration() {
		loadPersistentConfig();
	}

	private void loadPersistentConfig() {
		Map<Integer, String> loadedMap = ExpressionHelper.getPersistentPropsByPrefix(IVarConfiguration.PREFIX);
		if (loadedMap.size() > 1) {
			throw new IllegalStateException();
		}
		if (loadedMap.isEmpty()) {
			initEmpty();
		} else if (loadedMap.size() == 1) {
			String exp = loadedMap.values().iterator().next();
			setExpression(exp);
		}
	}

	@Override
	public Set<String> getHidden() {
		return new HashSet<>(hiddenVars);
	}
	
	@Override
	public void resetVariableOrder() {
		List<String> envList = BddUtil.getVarNamesListByModule(Mod.ENV);
		envList.sort(null);
		List<String> sysList = BddUtil.getVarNamesListByModule(Mod.SYS);
		sysList.sort(null);
		setVariables(Mod.ENV, envList);
		setVariables(Mod.SYS, sysList);		
	}


	@Override
	public List<String> getVariablesByModule(Mod mod) {
		if (mod == Mod.SYS) {
			return new ArrayList<String>(sysVars);
		} else {
			return new ArrayList<String>(envVars);
		}
	}

	@Override
	public void setHidden(Collection<String> hidden) {
		hiddenVars.clear();
		for (String var : hidden) {
			if (BddUtil.isValidVar(var)) {
				hiddenVars.add(var);
			}
		}
		save();
	}

	private String serializeCollection(Collection<String> collection) {
		return String.join(IN_SEQUENCE_DELIMITER, collection);
	}

	@Override
	public String getExpression() {
		String res = String.join(SEQUENCE_DELIMITER, serializeCollection(hiddenVars), serializeCollection(envVars),
				serializeCollection(sysVars), String.valueOf(hideDontCares), String.valueOf(hideFixed));
		return res;
	}

	private void initEmpty() {
		setHidden(new ArrayList<>());
		resetVariableOrder();
		setHideDontCares(false);
		setHideFixed(false);
	}

	@Override
	public void setExpression(String expression) {
		String[] parts = expression.split(SEQUENCE_DELIMITER, -1);
		if (parts.length != 5) {
			throw new IllegalStateException();
		}

		removeAll();

		setHidden(Arrays.asList(parts[0].split(IN_SEQUENCE_DELIMITER)));
		setVariables(Mod.ENV, Arrays.asList(parts[1].split(IN_SEQUENCE_DELIMITER)));
		setVariables(Mod.SYS, Arrays.asList(parts[2].split(IN_SEQUENCE_DELIMITER)));
		setHideDontCares(Boolean.valueOf(parts[3]));
		setHideFixed(Boolean.valueOf(parts[4]));
	}

	private void setVariables(Collection<String> newVals, final List<String> toUpdate, Mod mod) {
		toUpdate.clear();
		// Handle the case where there is a mismatch in the variable count
		List<String> expectedVars = BddUtil.getVarNamesListByModule(mod);
		HashSet<String> expectedVarsSet = new HashSet<String>(expectedVars);
		if (!expectedVarsSet.equals(new HashSet<String>(newVals))) {
			expectedVars.sort(null);
			toUpdate.addAll(expectedVars);
			return;
		}

		toUpdate.addAll(newVals);
		save();
	}

	@Override
	public void setVariables(Mod mod, Collection<String> vars) {
		setVariables(vars, mod == Mod.ENV ? envVars : sysVars, mod);
	}

	@Override
	public void removeAll() {
		hiddenVars.clear();
		sysVars.clear();
		envVars.clear();
		hideDontCares = false;
		hideFixed = false;
		unsave();
	}

	@Override
	public boolean getHideFixed() {
		return hideFixed;
	}

	@Override
	public void setHideFixed(boolean value) {
		hideFixed = value;
		save();
	}

	@Override
	public boolean getHideDontCares() {
		return hideDontCares;
	}

	@Override
	public void setHideDontCares(boolean value) {
		hideDontCares = value;
		save();
	}
}
