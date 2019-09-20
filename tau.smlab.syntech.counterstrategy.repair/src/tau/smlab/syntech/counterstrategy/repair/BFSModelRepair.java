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

/**
 * Use a queue to find repairs in BFS style (similar to Alur's algorithm)
 * 
 * @author Shalom
 *
 */

import java.util.ArrayList;
import java.util.List;

import tau.smlab.syntech.checks.CouldAsmHelp;
import tau.smlab.syntech.counterstrategy.model.JusticeViolationGraph;
import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.games.rabin.RabinGame;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.repair.BasicAssumption;
import tau.smlab.syntech.repair.RepairCore;

public class BFSModelRepair extends ModelRepair {

	public BFSModelRepair(GameInput gameInput, int alpha, boolean merge, boolean edgeData, boolean cores, boolean repairCore) {
		super(gameInput, alpha, merge, edgeData, cores, repairCore);
	}

	@Override
	protected void applySearch() {
		List<BasicAssumption> currAdditions = null;
		List<List<BasicAssumption>> queue = new ArrayList<List<BasicAssumption>>();
		queue.add(new ArrayList<BasicAssumption>());
		while (!queue.isEmpty()) {
			currAdditions = queue.remove(0);
			createModelWithAdditions(currAdditions);
			
			if (CouldAsmHelp.couldAsmHelp(gm)) { // abandon paths with inconsistent spec
				RabinGame rg = new RabinGame(gm);			
				if (!rg.checkRealizability()) {
					rg.free();
					int depth = currAdditions.size();
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
					if (results.size() == 1) {
						timeToFirstRepair = System.currentTimeMillis() - startTime;
						minDepth = depth;
					}
					
					maxDepth = java.lang.Math.max(depth, maxDepth);
					
	        		if (enough())
	        			return;
					
				} else if (alpha ==-1 || currAdditions.size() < alpha) { //go deeper only for smaller than max depth
					if (useCores) {
						rg.free();
						SupportMethods.computeAndSetGurCore(gm);
						// module of env might have changed and we recompute the Rabin memory
						rg = new RabinGame(gm);
						rg.checkRealizability();
					}
					JusticeViolationGraph csg = computeCS(gm, rg);
					List<BasicAssumption> extraction = assumptionFinderOneLevel(csg);
					csg.freeJVGraph(csg.getNodes(), csg.getEdges());
					// the variable has to be deleted from the Env without resetting the old Env or we would lose all bdds
					Env.deleteVar(JusticeViolationGraph.ASSUMPTION_RANK); 
					for (BasicAssumption ext : extraction) {
	//					BehaviorInfo nextAss = ext.translateToBehavior(); // first check if the assumption is helpful. The code is nasty, look for alternatives
	//					currModel.addEnvBehaviorInfo(nextAss);
	//					SupportMethods.buildEnv(currModel, currModel.getEnvBehaviorInfo());
	//					boolean res = false;
	//					if (SupportMethods.isRealizable(currModel)) {
	//						SupportMethods.computeAndSetGurCore(currModel); // we need a new core to check helpfulness
	//						res = true;
	//					}
	//					if (!res && SupportMethods.removeIfUnhelpful(currModel, nextAss)) { // we don't care about removing when it's helpful because we don't use the model anymore
	//						unhelpful++;
	//						if (resultingAssumptions.size() + unhelpful == enough) {
	//							foundEnough = true; 
	//						}
	//					} else {
							List<BasicAssumption> withExt = new ArrayList<BasicAssumption>(currAdditions);
							try {
								assertTrue("Could not add an assumption to the list", withExt.add(ext));
							} catch (Exception e) {
								e.printStackTrace();
							}
							queue.add(withExt);
					}
				}
			}
		gm.free();
		}
	}
	

}
