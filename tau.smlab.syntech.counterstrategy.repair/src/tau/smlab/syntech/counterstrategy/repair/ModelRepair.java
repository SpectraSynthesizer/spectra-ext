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
 * Repair based on JVTS
 * Assumes that we have variable reorder and that the Env is reset
 * 
 * @author Shalom
 */

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.counterstrategy.model.JusticeViolationGraph;
import tau.smlab.syntech.counterstrategy.model.JVGEdge;
import tau.smlab.syntech.counterstrategy.model.JVGNode;
import tau.smlab.syntech.counterstrategy.model.JVGNodeType;
import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker.EnvSpecPart;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker.Positions;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker.Systems;
import tau.smlab.syntech.games.rabin.RabinGame;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.repair.AbstractRepair;
import tau.smlab.syntech.repair.BasicAssumption;
import tau.smlab.syntech.repair.BasicAssumption.Kind;
import tau.smlab.syntech.counterstrategy.repair.SupportMethods;

public abstract class ModelRepair extends AbstractRepair {
	private boolean isRealizable;
	private long runtimeCount;
	

	//variants
	private boolean mergeNodes = true; // Merge JVTS attractor nodes? No merging gives less strict assumptions at each level
	protected boolean useEdgeData = false; // Take into account the environment choices when computing assumptions on edges (safety and trans ones)
	protected boolean useCores = false;
	protected boolean withRepairCore = false;
	
	protected long startTime;
	protected long timeToFirstRepair = -1;
	protected long timeToWellSep = -1;  // -1 means not applicable. 
	protected int wellSepCount = 0;
	protected int alpha = 2; // maximum BFS depth.
	protected List<Long> runtimes = new ArrayList<Long>();	
	
	public ModelRepair(GameInput gameInput, int alpha, boolean merge, boolean edgeData, boolean cores, boolean repairCore) {
		gi = gameInput;
		this.alpha = alpha;
		mergeNodes = merge;
		useEdgeData = edgeData;
		useCores = cores;
		withRepairCore = repairCore;
		
		results = new ArrayList<List<BasicAssumption>>();
	}
	
	/**
	 * do the computation
	 */
	public void execute() {
		
		computeWellSep();
		if (SupportMethods.isRealizable(gm)) {
			//The original specification is realizable, no assumptions added.
			startTime = System.currentTimeMillis();
			isRealizable = true;
		} else {
			isRealizable = false;
			startTime = System.currentTimeMillis(); // ignore runtime for initial realizability check
			applySearch();
		}
		runtimeCount = currTime();	
		computeWellSepStats();
	}
	
	public int numberOfWellSeparated()
	{
		return wellSepCount;
	}
	
	public long timeToWellSep() {
		return timeToWellSep;
	}
	
	/**
	 * Counts the number of well-separated repair options and gets time for first of them
	 * The check is done only for the less strict well-separation regardless of pref options 
	 * 
	 * if the spec is non-well-separated, we don't count the number of well-separated after the repair
	 * Note that it could happen that adding assumptions makes a non-well-separated specification, but we are not interested
	 * 
	 * @return number of well separated repair options found
	 */
	
	private void computeWellSepStats()
	{
		timeToWellSep = -1;
		wellSepCount = 0;
		for (int i=0; i<results.size(); i++) {
			List<BasicAssumption> repairSuggestion = results.get(i);
			createModelWithAdditions(repairSuggestion);

		    WellSeparationChecker c = new WellSeparationChecker();
		    try {
			    if (c.checkEnvWellSeparated(gm, Systems.SPEC, EnvSpecPart.JUSTICE,Positions.REACH, false)) {
			    	wellSepCount++;
			    	if (timeToWellSep == -1) {
			    		timeToWellSep = runtimes.get(i);
			    	}
			    }
		    } catch(Exception e) {
		    	e.printStackTrace();
		    }
		    gm.free();
		}
	}
	
	/**
	 * if the spec is realizable, no repairs exist
	 * @return realizability
	 */
	public boolean isRealizable() {
		return isRealizable;
	}


	public long getRuntime() {
		return runtimeCount;
	}
	
	public long getTimeToFirstRepair() {
		return timeToFirstRepair;
	}
	
	/**
	 * to be filled by inheriting classes - the search
	 */
	protected abstract void applySearch();

	/**
	 *
	 * Find all assumptions from the current JVTS graph
	 * 
	 * @return A list of assumptions
	 */
	protected List<BasicAssumption> assumptionFinderOneLevel(JusticeViolationGraph csg) {
		// only top level for now
		List<BasicAssumption> extraction = new ArrayList<BasicAssumption>();

		//each node 
		Iterator<Integer> iter = csg.getNodes().keySet().iterator();
		while (iter.hasNext()) {
			Integer nodeNum = iter.next();
			JVGNode node = csg.getNodes().get(nodeNum);
			// Just a check to see if the implementation has no unknown nodes
			JVGNodeType t = node.getType();
			try {
				assertTrue("An unknown node detected", t != JVGNodeType.UNKNOWN_NODE); // make sure there are no unknown nodes
			} catch (ModelRepairException e) {
				e.printStackTrace();
			}
			
			if (t != JVGNodeType.INIT_NODE) {// ignore the init node which is only a pointer to the init states
				//cycles create GF assumptions
				if (t == JVGNodeType.CYCLE_NODE) {
					extraction.add(new BasicAssumption(SupportMethods.quantifyOutAux(node.getNodeBDD(), csg).notWithDoms(), Kind.JUSTICE));
				//attractor nodes with no outgoing edges create safety assumptions (added to trans list because it begins with G)
				} else if ((t == JVGNodeType.ATTRACTOR_NODE_FROM_CYCLE || t == JVGNodeType.ATTRACTOR_NODE_NOT_FROM_CYCLE) && !csg.getEdges().containsKey(nodeNum)) {
					extraction.add(new BasicAssumption(SupportMethods.quantifyOutAux(node.getNodeBDD(), csg).notWithDoms(), Kind.SAFETY));
				}
				//each node that has outgoing edges create transition assumptions
				if (csg.getEdges().containsKey(nodeNum)) {
					Iterator<JVGEdge> edgeIter = csg.getEdges().get(nodeNum).iterator();
					while (edgeIter.hasNext()) {
						JVGEdge edge = edgeIter.next();
						JVGNode targetNode = csg.getNodes().get(edge.getDestInd());
						BDD origBDD = SupportMethods.quantifyOutAux(node.getNodeBDD(), csg);
						BDD targBDD = SupportMethods.quantifyOutAux(targetNode.getNodeBDD(), csg);
						BDD transBDD = origBDD.and(SupportMethods.removeSysAndPrimeEnvVars(targBDD, csg.getSys())); // node BDDs only
						if (useEdgeData) {
							transBDD = transBDD.and(SupportMethods.quantifyOutAux(node.getTransitions(), csg));
						}
						extraction.add(new BasicAssumption(transBDD.notWithDoms(), Kind.SAFETY));
					}
				}
			}/* else { // look at the init nodes of the JVTS pointed by the node for init assumptions
				if (csg.getEdges().containsKey(nodeNum)) {
					Iterator<JVGEdge> edgeIter = csg.getEdges().get(nodeNum).iterator();
					while (edgeIter.hasNext()) {
						JVGEdge edge = edgeIter.next();
						JVGNode targetNode = csg.getNodes().get(edge.getDestInd());
						BDD targBDD = SupportMethods.quantifyOutSysAndAux(targetNode.getNodeBDD(), csg);
						extraction.add(new BasicAssumption(targBDD.notWithDoms(), BasicAssumption.Kind.INIT));
					}
				}
			}*/
		}
		return BasicAssumption.removeStrong(extraction);
	}
	
	/**
	 * 
	 * @param m
	 * @param rg is consumed (freed)
	 * @return
	 */
	protected JusticeViolationGraph computeCS(GameModel m, RabinGame rg) {
		JusticeViolationGraph csg = new JusticeViolationGraph(rg, m);
		// disable reorder to compute JVTS faster
		Env.disableReorder();
		try {
			csg.computeGraph();
		} catch (Exception e) {
			e.printStackTrace();
		}
		rg.free();
		
		Env.enableReorder();
		if (mergeNodes) {
			csg.mergedAttractors();
		}

		return csg;
	}

	private long currTime() {
		return System.currentTimeMillis() - startTime;
	}
	
	protected static void assertTrue(String s, boolean cond) throws ModelRepairException {
	
		if (!cond) {
			throw new ModelRepairException(s);
		}
	}
}
