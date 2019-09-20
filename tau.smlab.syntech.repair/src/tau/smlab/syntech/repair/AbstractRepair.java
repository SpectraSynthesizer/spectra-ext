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

package tau.smlab.syntech.repair;

import java.io.File;

/**
 * 
 * @author shalom
 * 
 * Abstract class for repair classes. Mostly handles the repairs.
 */

import java.util.ArrayList;
import java.util.List;
import net.sf.javabdd.BDD;
import tau.smlab.syntech.bddgenerator.BDDGenerator;
import tau.smlab.syntech.bddgenerator.BDDGenerator.TraceInfo;
import tau.smlab.syntech.checks.CouldAsmHelp;
import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.PlayerModule;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker.EnvSpecPart;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker.Positions;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker.Systems;
import tau.smlab.syntech.jtlv.env.module.ModuleBDDField;

/**
 * Abstract repair class
 * runs repair with method execute
 * 
 * @author shalom
 *
 */

public abstract class AbstractRepair implements StatInterface {

	protected static GameInput gi;
	protected static GameModel gm = null;
	protected boolean isWellSep;
	protected int minDepth = -1; // -1 means a non applicable depth
	protected int maxDepth = -1; 
	protected static List<List<BasicAssumption>> results = null;
	private long maxVars;
	private long maxNodes;
	private long initials;
	private long safeties;
	private long justices;

	// with this option the repairs are wriiten to files
	private boolean writeRepairs = false;
	private RepairOutput repairIO = null;
	
	// with this option the spec has preset subset of the assumptions. Useful for running on unrealizable sub-specs of realizable ones
	private static boolean useAlternativeAssumptions = false;
	private static List<BehaviorInfo> alternativeAssumptions = null;
	
	private long repairLimit = -1; // -1 means there is no limit

	abstract public void execute();	

	public void setOutput(File folder) {
		writeRepairs = true;
				
		if (!folder.exists()) {
			folder.mkdir();
		}
		repairIO = new RepairOutput(folder);
	}
	


	/**
	 * All obtained repairs should be stored using this method which also allows writing repairs to files. 
	 * @param repair
	 * @param when the repair was obtained
	 * @param the depth of the repair
	 * @throws Exception
	 */
	protected void recordRepair(List<BasicAssumption> repair, long time, long depth) {
		// cleanup the repair before writing it
		for (BasicAssumption ba : repair) {
			ba.bdd = removeDontCareVars(ba.bdd);
		}
		results.add(repair);

		if (writeRepairs) {
			repairIO.writeRepairToFiles(gm, repair, results.size(), time, depth);
		}
	}
	
	public void setAlternativeAssumptions(List<BehaviorInfo> alternative) {
		useAlternativeAssumptions = true;
		alternativeAssumptions = new ArrayList<BehaviorInfo>(alternative);
	}
	
	public boolean isFixable() {
		computeGameModel();
		return CouldAsmHelp.couldAsmHelp(gm);
	}
	
	/**
	 * Write a repair to the output folder according to the index. Can be used from outside with the index of the repair
	 * No time or depth recorded.
	 * @param repairIndex the index number
	 */
	public void setRepairLimit(long limit) {
		repairLimit = limit;
	}
	
	protected boolean enough() {
		return (repairLimit>0 && results.size() == repairLimit);
	}
	
	public long getMinDepth() {
		return minDepth;
	}
	
	public long getMaxDepth() {
		return maxDepth;
	}

	/**
	 * Retrieve results
	 * @return resulting assumptions
	 */
	public List<List<BasicAssumption>> getResultingAssumptions() {
		return results;
	}
	
	/**
	 * return the maximum number of variables in the best repair
	 */
	public long getMaxVars() {
		return maxVars;
	}
	
	/**
	 * return the maximum number of nodes in the BDDs of the best repair
	 */
	public long getMaxNodes() {
		return maxNodes;
	}
	
	public long getIni() {
		return initials;
	}
	
	public long getSafe() {
		return safeties;
	}
	
	public long getJust() {
		return justices;
	}
	
	/**
	 * 
	 * @return number of repair options found
	 */
	
	public int numberOfRepairsFound() {
		return results.size();
	}
	
	/**
	 * 
	 * @return is well-separated
	 */
	public boolean isWellSeparated() {
	    return isWellSep;
	}
	
	/**
	 * computes well separation of the current spec.
	 * computes gm as the game model of the spec, and restores it
	 */
	protected void computeWellSep() {
		computeGameModel();
	    WellSeparationChecker c = new WellSeparationChecker();
	    isWellSep = false;
	    try {
	    	isWellSep = c.checkEnvWellSeparated(gm, Systems.SPEC, EnvSpecPart.JUSTICE,Positions.REACH, false);
	    } catch(Exception e) {
	    	e.printStackTrace();
	    }
	    gm.free();
		computeGameModel();
	}
	/**
	 * 
	 * @return the number of maximum support variables in the best repair
	 */
	public void computeBest() {
		List<ModuleBDDField> fields = new ArrayList<ModuleBDDField>();
		fields.addAll(gm.getSys().getAllFields());
		fields.addAll(gm.getEnv().getAllFields());
		maxVars = 1000000; // assume the result should be less than that
		
		if (numberOfRepairsFound() == 0) {
			maxVars = -1; // not applicable
			maxNodes = -1;
			initials = -1;
			safeties = -1;
			justices = -1;
		} else {
			maxNodes = 1000000;
			for (List<BasicAssumption> repair : results) {
				long asmVars;
				long currMaxVars;
				if (repair.isEmpty()) {
					currMaxVars = 1000000;
				} else {
					currMaxVars = 0;
					for (BasicAssumption b : repair) {
						asmVars= 0;
						for (ModuleBDDField f : fields) {
							if (!b.bdd.support().intersect(f.support()).isEmpty() || !b.bdd.support().intersect(f.other().support()).isEmpty()) {
								asmVars++;
							}
						}
						currMaxVars = java.lang.Math.max(currMaxVars, asmVars);
					}	
				}
				if (currMaxVars < maxVars) { 
					maxVars = currMaxVars;
					maxNodes = computeMaxNodes(repair); // always prefer the repair with lowest max number of vars 
					computeComponentNumbers(repair);
				} else if (currMaxVars == maxVars) { // compare node sizes
					long currMaxNodes = computeMaxNodes(repair);
					if (currMaxNodes < maxNodes) {
						maxNodes = currMaxNodes;
						computeComponentNumbers(repair);
					}
				}
			}	
		}
	}
	
	/**
	 * Clear a BDD form don't care variables. Could stay in the BDD because the domain of the field is not a power of 2 
	 * @param toClear
	 * @return
	 */
	
	static private BDD removeDontCareVars(BDD toClear) {
		BDD withoutDontcare;

		List<ModuleBDDField> fields = new ArrayList<ModuleBDDField>();
		fields.addAll(gm.getSys().getAllFields());
		fields.addAll(gm.getEnv().getAllFields());
   
	   for (ModuleBDDField f : fields) {
			withoutDontcare = toClear;
			withoutDontcare = withoutDontcare.exist(f.support());
			BDD restore = withoutDontcare.and(f.getDomain().domain().id()); 
			if(restore.biimp(toClear).isOne()) {
				toClear = withoutDontcare;
			}
		}
		for (ModuleBDDField f : fields) {
			withoutDontcare = toClear;
			withoutDontcare = withoutDontcare.exist(f.other().support());
			BDD restore = withoutDontcare.and(f.getDomain().domain().id()); 
			if(restore.biimp(toClear).isOne()) {
				toClear = withoutDontcare;
			}
		}
		return toClear;
	}
	
	private long computeMaxNodes(List<BasicAssumption> repair) {
		int maxNodeCount = -1;
		for (BasicAssumption asm : repair) {
			maxNodeCount = java.lang.Math.max(maxNodeCount, asm.bdd.nodeCount());
		}
		return maxNodeCount;
	}
	
	private void computeComponentNumbers(List<BasicAssumption> repair) {
		initials = safeties = justices = 0;
		for (BasicAssumption ba : repair) {
			switch (ba.kind()) {
			case INIT:
				initials++;
				break;
			case SAFETY:
				safeties++;
				break;
			case JUSTICE:
				justices++;
				break;
			default:
				break;
			}
		}
	}

	
	/**
	 * create the model from the game input
	 * @return the model
	 */
	protected static void computeGameModel() {
		gm = BDDGenerator.generateGameModel(gi, TraceInfo.ALL);
		
		if (useAlternativeAssumptions) {
			gm.getEnv().reset();
			gm.getEnvBehaviorInfo().clear();
			addBehaviors(alternativeAssumptions);
		}
	}
	
	
	/**
	 * create a model of the original spec and add assumptions from a given list
	 * @param additions the additional assumptions
	 * @return the model
	 */
	
	protected static void createModelWithAdditions(List<BasicAssumption> additions) {
		computeGameModel();
		
		for (BasicAssumption ba : additions) {
			BehaviorInfo bh = ba.translateToBehavior();
			ba.addToModel(gm);
			gm.addEnvBehaviorInfo(bh);
		}
		
	}
	
	
	/**
	 * create a model of the original spec and add assumptions from a given list
	 * @param additions the additional assumptions
	 * @return the model
	 */
	
	protected static void createModelWithBehaviors(List<BehaviorInfo> behaviors) {
		computeGameModel();
		addBehaviors(behaviors);
	}
	
	private static void addBehaviors(List<BehaviorInfo> behaviors) {
	
		PlayerModule env = gm.getEnv();
		
		for (BehaviorInfo bh : behaviors) {
			gm.addEnvBehaviorInfo(bh);
			if (bh.isInitial()) {
				env.conjunctInitial(bh.initial.id());
			}
			if (bh.isSafety()) {
				env.conjunctTrans(bh.safety.id());
			}
			if (bh.isJustice()) {
				env.addJustice(bh.justice.id(), bh.traceId);
			}
		}
	}

}
