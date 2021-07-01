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
import tau.smlab.syntech.counterstrategy.model.JusticeViolationGraph;
import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.games.rabin.RabinGame;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.repair.BasicAssumption;
import tau.smlab.syntech.repair.RepairCore;

/**
 * For every JVTS, take all assumptions created at every level
 * 
 * @author Shalom
 *
 */

public class FullApplicationModelRepair extends ModelRepair {
	
	public FullApplicationModelRepair(GameInput gameInput, int alpha, boolean merge, boolean edgeData, boolean repairCore) {
		super(gameInput, alpha, merge, edgeData, repairCore);
	}
	
	@Override
	protected void applySearch() {
		List<BasicAssumption> currAdditions = new ArrayList<BasicAssumption>();
		computeGameModel();
		RabinGame rg = new RabinGame(gm);	
		rg.checkRealizability(); // required for the JVTS computation

		int depth = 0;
		boolean halt = false;
		while ((alpha == -1 || depth<alpha) && !halt) {
			JusticeViolationGraph csg = computeCS(gm, rg); // note: deletes the rg
			List<BasicAssumption> extraction = assumptionFinderOneLevel(csg);
			csg.freeJVGraph(csg.getNodes(), csg.getEdges());
			// the variable has to be deleted from the Env without resetting the old Env or we would lose all bdds
			Env.deleteVar(JusticeViolationGraph.ASSUMPTION_RANK); 
			gm.free();
			currAdditions.addAll(extraction);
			createModelWithAdditions(currAdditions);
			
			depth++;
			minDepth = maxDepth = depth;

			if (CouldAsmHelp.couldAsmHelp(gm)) {
				rg = new RabinGame(gm);			
				if (!rg.checkRealizability()) {
					if (withRepairCore) {
						// it may be the case that not all the additions are needed.
						// compute a core of the additional assumptions
						computeGameModel();
						RepairCore coreFinder = new RepairCore(gm);
						currAdditions = coreFinder.minimize(currAdditions);
					}
					long time = System.currentTimeMillis() - startTime;
					recordRepair(currAdditions, time, depth); //success
					runtimes.add(time);
					halt = true;
					rg.free();
					gm.free();
				}
			} else {
				halt = true;
				rg.free();
				gm.free();
			}
		}
	}
}
