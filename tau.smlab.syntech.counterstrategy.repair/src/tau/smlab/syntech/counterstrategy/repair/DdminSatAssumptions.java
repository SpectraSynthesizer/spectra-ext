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

package tau.smlab.syntech.counterstrategy.repair;

import java.util.ArrayList;
import java.util.List;

import tau.smlab.syntech.checks.CouldAsmHelp;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.util.GameBuilderUtil;
import tau.smlab.syntech.games.util.AbstractDdmin;
import tau.smlab.syntech.repair.BasicAssumption;

/**
 * finds the smallest subset of assumptions that are irrelevant to satisfiability
 * actually this is ddmax for finding a locally maximal subset of assumptions that keeps satisfiability
 * 
 * @author shalom
 *
 */
public class DdminSatAssumptions extends AbstractDdmin<BasicAssumption> {
	private GameModel model;
	private List<BehaviorInfo> baseAsm;
	private List<BasicAssumption> allAsm;
	  
	public DdminSatAssumptions(GameModel model, List<BasicAssumption> total) {
		this.model = model;
		baseAsm = new ArrayList<BehaviorInfo>(model.getEnvBehaviorInfo());
		allAsm = new ArrayList<BasicAssumption>(total);
	}
	  
	/**
	 * check for satisfiability
	 * 
	 * @return true if spec is sat
	 */
	  @Override
	  protected boolean check(List<BasicAssumption> part) {

		List<BehaviorInfo> baseAndPart = new ArrayList<BehaviorInfo>(baseAsm);
		List<BasicAssumption> exceptPart = new ArrayList<BasicAssumption>(allAsm);
		exceptPart.removeAll(part);
		for (BasicAssumption a : exceptPart) {
			baseAndPart.add(a.translateToBehavior());
		}
		GameBuilderUtil.buildEnv(model, baseAndPart);

	    return CouldAsmHelp.couldAsmHelp(model);
	  }
}
