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

package tau.smlab.syntech.repair.alur;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Vector;

import tau.smlab.syntech.checks.SatCheck;
import tau.smlab.syntech.repair.AbstractRepair;
import tau.smlab.syntech.repair.BasicAssumption;
import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.games.AbstractGamesException;
import tau.smlab.syntech.games.controller.enumerate.ConcreteControllerConstruction;
import tau.smlab.syntech.games.controller.enumerate.EnumStrategyI;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker.EnvSpecPart;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker.Positions;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker.Systems;
import tau.smlab.syntech.games.rabin.RabinConcreteControllerConstruction;
import tau.smlab.syntech.games.rabin.RabinGame;



/**
 * Based on the paper by Alur et al. "Counter-Strategy Guided Refinement of GR(1) Temporal Logic Specifications"
 * 
 * Assumes that we have variable reorder and that the Env is reset
 *
 * @author moskovitch
 * @author novgorodov
 * @author shalom
 */

public class SpecificationRefinement extends AbstractRepair {
	
	private HashMap<String, ArrayList<String>> P; // set of subsets of variables to be used in the patterns TODO: use other data structure
	private int alpha; //maximal depth of search
	private int beta; //maximal number of state in generated patterns
	
	private PrintStream out = System.out;
	
	AbstractFST fst;
	
	// interface implementation
	private long time = 0;
	private long timeToFirst = -1;
	private long timeToWellSep = -1;
	private long startTime;
	private List<Long> runtimes = new ArrayList<Long>();
	private boolean isRealizable = false;
	private int wellSepCount = 0;
	private boolean edgeHeuristic;
	
	
	public SpecificationRefinement(GameInput gameInput, HashMap<String, ArrayList<String>> P, int alpha, int beta, boolean useEdgeHeuristic){
		gi = gameInput;
		this.P = P;
		this.alpha = alpha;
		this.beta = beta;
		edgeHeuristic = useEdgeHeuristic;
	}
	
	public long getRuntime() { return time; }
	public long getTimeToFirstRepair() { return timeToFirst; }
	public int numberOfWellSeparated() { return wellSepCount; }
	public long timeToWellSep() { return timeToWellSep; }
	
	
	public void execute() {
		results = new ArrayList<List<BasicAssumption>>();
		computeWellSep();
		
    	startTime = System.currentTimeMillis();
    	findRefienment();
    	time = System.currentTimeMillis() - startTime;
    	computeWellSepStats();
	}
	
	public boolean isRealizable() {return isRealizable;}

	
	//The main algorithm (Algorithm 1 from the paper) - finds the refinement for the specification
	public void findRefienment() {		

		out.println("Algorithm 1 started"); // DEBUG output, maybe omitted
	    out.println("Alpha is " + (alpha==-1 ? "infinite" : alpha)); // DEBUG output, maybe omitted
	    out.println(edgeHeuristic ? "Using max outgoing edge heuristic for beta" : ("Beta is " + beta));// DEBUG output, maybe omitted
	    out.println("Forbidden variables:"); // DEBUG output, maybe omitted
		out.println(P); // DEBUG output, maybe omitted
	    
	    // Compute a counter strategy - the main idea behind the algorithm is to
	    // use counter strategy for generating patterns for fixing the unrealizability
	    RabinGame rabin = new RabinGame(gm);
	    if (rabin.checkRealizability()) { // needed in order to create the counterstrategy
	    	out.println("Specification is unrealizable.");
	    	isRealizable = false;
	    	ConcreteControllerConstruction cc = new RabinConcreteControllerConstruction(rabin.getMem(), gm);
	    	try {
	    		EnumStrategyI counterStategy = cc.calculateConcreteController();
	    	    fst = new AbstractFST(counterStategy);
	    	    if (edgeHeuristic) {
	    	    	beta = fst.getMaxOutgoingEdge();
	    	    }	
	    	    
	    	    // Generating patterns, according to algorithms 2, 3, 4
	    	    List<Pattern> patterns = generatePatterns(counterStategy);
	    	    
	    	    out.println("Found patterns:"); // DEBUG output, maybe omitted
	    	    for (Pattern p : patterns){		// DEBUG output, maybe omitted
	    	    	out.println(p.toString());	// DEBUG output, maybe omitted
	    	    }
	    	    
	    	    // Generating candidates based on patterns computed above
	    	    Queue<List<BehaviorInfo>> candidates = generateCandidate(patterns);
    	    	/*out.println("Candidates are: "); // DEBUG output, maybe omitted
	    	    for(List<BehaviorInfo> candidate: candidates){
	    	    	for(BehaviorInfo c : candidate) {
	    	    		// printing the candidates
	    	    		if(c.isSafety()) {
	    	    			out.println("asm "); // DEBUG output, maybe omitted	    			
	    	    			out.println("G "+c.safety.toString()+";"); // DEBUG output, maybe omitted
	    	    		}
	    	    		if(c.isJustice()) {
	    	    			out.println("asm "); // DEBUG output, maybe omitted
	    	    			out.println("GF "+c.justice.toString()+";"); // DEBUG output, maybe omitted
	    	    		}
	    	    	}
	    	    	
	    	    }*/
	    	    
	    	    // Trying each of the candidates
	    	    while (!candidates.isEmpty()) { 
	    	    	List<BehaviorInfo> candidate = candidates.poll();
	    	    	
	    	    	createModelWithBehaviors(candidate);

/*	    	        for (BehaviorInfo b : gm.getEnvBehaviorInfo()) {
	    	        	if (b.isSafety())
	    	        		out.println(b.safety);
	    	        	if (b.isJustice())
	    	        		out.println(b.justice);	    	        	
	    	        }*/
    	        
	    	    	if(SatCheck.checkSat(gm.getEnv())) {        
	    	        	rabin = new RabinGame(gm);
	    	        	if (!rabin.checkRealizability()){
		    	        	out.println(""); //Actual output
		    	        	out.println(""); //Actual output
		    	        	out.println("Found refinement:"); //Actual output
		    	        	
		    	        	// printing the results
			    	    	for(BehaviorInfo c : candidate) {
			    	    		if(c.isSafety()) {
			    	    			out.println("asm "); //Actual output		
			    	    			out.println("G "+c.safety.toString()+";"); //Actual output
			    	    		}
			    	    		if(c.isJustice()) {
			    	    			out.println("asm "); //Actual output
			    	    			out.println("GF "+c.justice.toString()+";"); //Actual output
			    	    		}
			    	    	}

			    	    	List<BasicAssumption> asmResult = new ArrayList<BasicAssumption>();
			    	    	for (BehaviorInfo b : candidate) {
			    	    		asmResult.add(new BasicAssumption(b));
			    	    	}
							long time = System.currentTimeMillis() - startTime;
			    	    	recordRepair(asmResult, time, candidate.size()); //success
							runtimes.add(time);							
			    	    	if (results.size() == 1) {
			    	    		timeToFirst = System.currentTimeMillis() - startTime;
				        		minDepth = candidate.size();
			    	    	}
			        		maxDepth = java.lang.Math.max(candidate.size(), maxDepth);
			        		
			        		if (enough())
			        			return;
	    	        	}
	    	        	else{
		    	        	if (alpha==-1 || candidate.size() < alpha){
		    	        		// keep the successful candidate as part of all assumptions
		    	        		cc = new RabinConcreteControllerConstruction(rabin.getMem(), gm);
		    	        		counterStategy = cc.calculateConcreteController();
		    	        		fst = new AbstractFST(counterStategy);
		    		    	    if (fst.getInitialState() == null) {
		    		    	    	out.println("No initial state for the counterstrategy, check if the env is unsat.");
		    		    	    } else {
			    	        		List<Pattern> newPatterns = generatePatterns(counterStategy);
			    	        		Queue<List<BehaviorInfo>> newCandidates = generateCandidate(newPatterns);
			    	        		candidates.addAll(newCandidates);
			    	        		for(List<BehaviorInfo> cand: newCandidates){
			    	        			// adding original candidate to the new one
			    	        			cand.addAll(candidate);
			    		    	    }
		    		    	    }
		    	        	}
		    	        }
	    	        }
	    	    	gm.free();
	    	        
	    	    }
	    	   	if (results.isEmpty()) {
	    	   		out.println("No refinement found. Please try again with other parameters.");
	    	   	}
	    	} catch (AbstractGamesException e) {
	    		e.printStackTrace();
	    	} 
	    	
	    	rabin.free();
	    } else {
	    	out.println("Specification is realizable.");
	    	gm.free();
	    	isRealizable = true;
	    }
	}
	
	//Generated patterns, based on Algorithms 2,3,4 from the paper
	private Queue<List<BehaviorInfo>> generateCandidate(List<Pattern> patterns) {
		
		Queue<List<BehaviorInfo>> candidates = new LinkedList<List<BehaviorInfo>>();
		
		for (Pattern p : patterns){
			List<BehaviorInfo> cand = new ArrayList<BehaviorInfo>();
			cand.add(p.replace(P, gm));
			candidates.add(cand);
		}
		
		return candidates;
		
	}
	
		
	//lets start by printing the patterns (and later change the return value for this function)
	private List<Pattern> generatePatterns(EnumStrategyI counterStategy) {
		out.println("Generating patterns...");
		List<Pattern> allPatterns= new ArrayList<Pattern>();
		
		List<Pattern> finallyGloballyPattern = finallyGloballyPatterns();
		allPatterns.addAll(finallyGloballyPattern);
		
		AbstractFSTState escape = fst.addEscapeState(counterStategy); // some patterns require that all states have an outgoing edge
		
		List<Pattern> finallyPatterns = finallyPatterns();
		for (Pattern p : new ArrayList<Pattern>(finallyPatterns)) { // remove patterns with escape state
			if (p.getStates().contains(escape)) {
				finallyPatterns.remove(p);
			}
		}
		allPatterns.addAll(finallyPatterns);

		List<Pattern> finallyNextPattern = finallyNextPatterns(finallyPatterns);
		for (Pattern p : new ArrayList<Pattern>(finallyNextPattern)) { // remove patterns with escape state
			if (p.getStates().contains(escape)) {
				finallyNextPattern.remove(p);
			}
		}
		allPatterns.addAll(finallyNextPattern);
		
		return allPatterns;
		
	}
	
	// Algorithm 2 from the paper
	private List<Pattern> finallyPatterns() {
		List<Pattern> patterns = new ArrayList<Pattern>();
		ArrayList<AbstractFSTState> initStateConfig = new ArrayList<AbstractFSTState>();
		initStateConfig.add(fst.getInitialState());
		patterns.add(new FinallyPattern(initStateConfig));
		for (int i = 1; i<= beta; i++){
			for(ArrayList<AbstractFSTState> set : getSubsets(i)){
				if (!contains(set, patterns)){
					if(!infiniteRun(set)){
						patterns.add(new FinallyPattern(set));	
					}
				}
			}
		}
		return patterns;
	}

	// Algorithm 3 from the paper
	private List<Pattern> finallyGloballyPatterns() {
		List<Pattern> patterns = new ArrayList<Pattern>();
		ArrayList<AbstractFSTState> Q_cycle = new ArrayList<AbstractFSTState>();
		Tarjan t = new Tarjan();
        List<List<Integer>> scComponents = t.getSCComponents(fst.generateGraph());
		
        for (int i = 0; i < scComponents.size(); i++) {
        	if(scComponents.get(i).size() > 1) {
        		for (int j = 0; j < scComponents.get(i).size(); j++)
        			Q_cycle.add(fst.getStateByID(scComponents.get(i).get(j)));
        	}
        	else {
        		if(fst.getStateByID(scComponents.get(i).get(0)).hasSelfLoop())
        			Q_cycle.add(fst.getStateByID(scComponents.get(i).get(0)));
        	}
        	        	
        }
        if (!Q_cycle.isEmpty()) {
        	patterns.add(new FinallyGloballyPattern(Q_cycle));
        }
        
		return patterns;
	}	
	
	//Algorithm 4 from the paper
	private List<Pattern> finallyNextPatterns(List<Pattern> finallyPatterns) {
		List<Pattern> patterns = new ArrayList<Pattern>();
		for(Pattern p : finallyPatterns){
			List<AbstractFSTState> nextStates = new ArrayList<AbstractFSTState>();
			for(AbstractFSTState s : fst.getNextStates(p.getStates())){
				if (!nextStates.contains(s))
					nextStates.add(s);
			}
			if (!nextStates.isEmpty()) { // now just for safety, we always have a next state
				patterns.add(new FinallyNextPattern(p.getStates(), nextStates)); 	
			}
		}
		return patterns;
	}
	

	private boolean infiniteRun(List<AbstractFSTState> set) {
		AbstractFST partialFST = fst.genPartialFST(set);
		return partialFST.hasCycle();
	}
	
	private boolean contains(List<AbstractFSTState> set, List<Pattern> configurations) {
		for(Pattern p : configurations){
			if (set.containsAll(p.getStates()))
				return true;
		}
		return false;
	}
	
	
	
	private List<ArrayList<AbstractFSTState>> getSubsets(int size){
		Vector<AbstractFSTState> superset = fst.getStates();
		//System.out.println(superset.remove(fst.getInitialState()));
		List<ArrayList<AbstractFSTState>> subsets = getSubsets(size, superset);	
		return subsets;
		
	}
	
	
	private List<ArrayList<AbstractFSTState>> getSubsets(int size, Vector<AbstractFSTState> superset)  {
		List<ArrayList<AbstractFSTState>> subsets = new ArrayList<ArrayList<AbstractFSTState>>();
		if (superset.size() < size){ // no subsets of size exactly bigger than the total number of states
			return subsets;
		}
		if (size == 1){
			for(AbstractFSTState state : superset){
				ArrayList<AbstractFSTState> l = new ArrayList<AbstractFSTState>(Arrays.asList(state));
				subsets.add(l);
			}
			return subsets;
		}
		else{
			AbstractFSTState state = superset.elementAt(0);
			Vector<AbstractFSTState> newSuperset =new Vector<AbstractFSTState>(superset);
			newSuperset.removeElementAt(0);
			List<ArrayList<AbstractFSTState>> rec_subsets = getSubsets(size-1, newSuperset);
			if (rec_subsets != null){
				for(ArrayList<AbstractFSTState> l: rec_subsets){
					l.add(state);
					subsets.add(l);
				}
			}
			rec_subsets = getSubsets(size, newSuperset);
			if (rec_subsets != null){
				subsets.addAll(rec_subsets);
			}
			
		}
		return subsets;	
	}
	
	private void computeWellSepStats() {
		wellSepCount = 0;
		timeToWellSep = -1;
		
		for (int i=0; i<results.size(); i++) {
			List<BasicAssumption> result = results.get(i);
	    	try {
	    		createModelWithAdditions(result);
	        	WellSeparationChecker ch = new WellSeparationChecker();
		    	if (ch.checkEnvWellSeparated(gm, Systems.SPEC, EnvSpecPart.JUSTICE,Positions.REACH, false)) {
		    		wellSepCount++;
		    		if (timeToWellSep == -1) {
		    			timeToWellSep = runtimes.get(i);
		    		}
		    	}
	    	} catch(Exception e) {
		    	e.printStackTrace();
		    }
		}
    }
	

}