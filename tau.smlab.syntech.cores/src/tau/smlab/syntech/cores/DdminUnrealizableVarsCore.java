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

package tau.smlab.syntech.cores;

import java.util.ArrayList;
import java.util.List;

import tau.smlab.syntech.checks.ddmin.AbstractDdmin;
import tau.smlab.syntech.cores.util.RealizabilityCheck;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.util.GameBuilderUtil;
import tau.smlab.syntech.jtlv.env.module.ModuleBDDField;

/**
 * minimizes set of variables that keep system unrealizable given a minimized
 * set of guarantees.
 * 
 * @author shalom
 * 
 */

public class DdminUnrealizableVarsCore extends AbstractDdmin<ModuleBDDField> {

	private GameModel model;
	private List<ModuleBDDField> sysVars;

	/**
	 * This has to be filled by the caller in order to make realizability checks standard and menu opt. sensitive.
	 * @param gm
	 * @return
	 */
	public boolean realizable(GameModel gm) {
		return RealizabilityCheck.isRealizable(gm);
	}
	
	/**
	 * auxiliaries are always added to the system module
	 * 
	 * @param model
	 *            the model
	 * @param gc
	 *            the minimized guarantees core already found.
	 */

	public DdminUnrealizableVarsCore(GameModel model) {
		this.model = model;
		this.sysVars = model.getSys().getNonAuxFields();
	}

	/**
	 * check for unrealizability
	 * 
	 * @return true if sys is unrealizable
	 */
	@Override
	protected boolean check(List<ModuleBDDField> part) {
		// find the other variables
		List<ModuleBDDField> vars = new ArrayList<ModuleBDDField>(sysVars);
		vars.removeAll(part);

		GameBuilderUtil.buildQuantifiedSys(model, vars);

		return !realizable(model);
	}

}
