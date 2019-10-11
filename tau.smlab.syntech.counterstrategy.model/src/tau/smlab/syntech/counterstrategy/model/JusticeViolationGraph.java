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

package tau.smlab.syntech.counterstrategy.model;


import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDD.BDDIterator;
import net.sf.javabdd.BDDDomain;
import net.sf.javabdd.BDDVarSet;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.PlayerModule;
import tau.smlab.syntech.games.rabin.RabinGame;
import tau.smlab.syntech.jtlv.CoreUtil;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.jtlv.lib.FixPoint;
import tau.smlab.syntech.spectragameinput.translator.Tracer;



/**
 * This class allows printing BDD using lazy evaluation. 
 * By providing this wrapper as parameter to the parameterized logger method, 
 * we can ensure the BDD will be translated to string only if this log is to be printed (depending on the log level).
 */
class BddStringWrapper {
	public BddStringWrapper(BDD b) {
		this.b = b;
	}
	
	public String toString() {
		return Env.toNiceSignleLineString(b);
	}
	
	BDD b;
}

/**
 * This class allows computing a JVG (aka JVTS) from a given Rabin game, accessing the result and concretizing.
 */
public class JusticeViolationGraph {
	private static final Logger log = Logger.getLogger(JusticeViolationGraph.class.getName());
	// The name of an variable added to the specification to differentiate same states when used for 
	// satisfying different environment assumptions. See "ASSUMP_RANK" in 
	//"A Symbolic Justice Violations Transition System For Unrealizable GR(1) Specifications" paper.
	public static final String ASSUMPTION_RANK = "ASSUMPTION_RANK";
	
	// members
	HashMap<Integer, JVGNode> nodes;
	HashMap<Integer, Vector<JVGEdge>> edges;
	Vector<BDD> memZ; // The Z intermediate results of the Rabin game solution
	Vector<Vector<Vector<BDD>>> memX; // The X intermediate results of the Rabin game solution
	BDD ini; // The input initial states
	Vector<BehaviorInfo> safeties; // safeties of the system
	BDD safetiesBDD; // states violating system safety
	BDD aux; // Auxiliary system safeties (i.e., safeties added during translation of specification)
	PlayerModule env;
	PlayerModule sys;
	List<BehaviorInfo> sysBehaviorInfo;
	List<BehaviorInfo> envBehaviorInfo;
	HashSet<String> auxVars; // Auxiliary variables, provided for the JVTS tool GUI
	boolean calcAssumpGraph; // Indicating if Assumption Satisfaction Sub-graph should be computed for each JVTS cycle node
	
	/**
	 * This class represents the Ranking Graph (aka Ranking Transition System).
	 *  The Ranking Graph contains sets of concrete states from memZ which are reachable from ini states
	 *  (via other memZ states).
	 */
	class RankingGraph {
		public RankingGraph() {
			this.nodes = new HashMap<Integer, RGNode>();
			this.edges = new HashMap<Integer, Vector<RGEdge>>();
		}
		
		HashMap<Integer, RGNode> nodes;
		// The edges are a mapping of source-node-id to edges from this source node.
		// For dead-end nodes, there will be an entry here with an empty Vector<RGEdge>
		HashMap<Integer, Vector<RGEdge>> edges;
	};

	/**
	 * A Ranking Graph edge
	 */
	class RGEdge {
		public String toString(String space) {
			return space + "destination node index = " + this.destInd;
		}

		// Index of the destination RG node
		int destInd;
	};

	/**
	 * A starting point in a memX cell representing all the states in the cell reachable from
	 * previously computed JVTS nodes
	 *
	 */
	class StartPointInfo {
		public StartPointInfo() {
		}
		
		public String toString() {
			String res = "prevNodeJVGInds = " + prevNodeJVGInds.toString() + "\n";
			res += "startPoint = " + Env.toNiceSignleLineString(startPoint) + "\n";
			return res;
		}
		
		Vector<Integer> prevNodeJVGInds;
		BDD startPoint;
	};
	
	/**
	 * A class containing information for computing a JVTS state from a Ranking Graph state
	 */
	class RGNodeProcessInfo implements Comparable<RGNodeProcessInfo> {
		public RGNodeProcessInfo() {
			this.zRank = -1;
			this.currNodeIndRG = -1;
			this.xCellToStartPointMap = new HashMap<Integer, HashMap<Integer, StartPointInfo>>();
			this.innerNodeTransitions = Env.FALSE();
		}
		
		@Override
		public int compareTo(RGNodeProcessInfo other) {
			if (this.zRank < other.zRank) {
				return -1;
			} else {
				return 1;
			}
		}
		
		public String toString() {
			String res = "currNodeIndRG = " + currNodeIndRG + ", zRank = " + zRank + "\n";
			Iterator<Map.Entry<Integer, HashMap<Integer, StartPointInfo>>> itr = xCellToStartPointMap.entrySet().iterator();
			while (itr.hasNext()) {
				Map.Entry<Integer, HashMap<Integer, StartPointInfo>> entry =
						(Map.Entry<Integer, HashMap<Integer, StartPointInfo>>) itr.next();
				Iterator<Map.Entry<Integer, StartPointInfo>> colItr = entry.getValue().entrySet().iterator();
				while (colItr.hasNext()) {
					Map.Entry<Integer, StartPointInfo> elem =
							(Map.Entry<Integer, StartPointInfo>) colItr.next();
					res += "xCell = [" + entry.getKey() + "," + elem.getKey() + "]:\n" + elem.getValue().toString() + "\n";
				}
			}

			res += "nodeXMatrix:\n";
			for (int i = 0; i < nodeXMatrix.size(); i++) {
				for (int j = 0; j < nodeXMatrix.get(i).size(); j++) {
					res += "nodeXMatrix[" + i + "][" + j + "] = " +
						Env.toNiceSignleLineString(nodeXMatrix.get(i).get(j)) + "\n";
				}
			}
			
			res += "innerNodeTransitions: " + Env.toNiceSignleLineString(innerNodeTransitions) + "\n";
			return res;
		}
		
		/**
		 * Filtering of duplicate states in same row in one of the matrices composing the memX.
		 * Only copy of a state in a row which is not removed is the one with the minimal cell index.
		 * @param xMatrix - a matrix which is in a cell in memX, representing steps towards satisfaction of 
		 * environment assumptions while violating a system justice guarantee. The filtered result of
		 * this xMatrix is saved in the member nodeXMatrix.
		 * @param safeties - Deprecated. A BDD representing all states which are deadend for the system 
		 */
		void setXMatrix(Vector<Vector<BDD>> xMatrix, BDD safeties) {
			log.finer("START");
			String beforeFilt = "matrix for z-rank " + zRank + " before filtering:\n";
			int k = 0;
			ArrayList<BddStringWrapper> beforeBdds = new ArrayList<BddStringWrapper>();
			for (int i = 0; i < xMatrix.size(); i++) {
				for (int j = 0; j < xMatrix.get(i).size(); j++) {
					beforeFilt += "xMatrix[" + i + "][" + j + "] = {" + k + "}\n";
					beforeBdds.add(new BddStringWrapper(xMatrix.get(i).get(j)));
					k++;
				}
			}
			log.log(Level.FINER, beforeFilt, beforeBdds.toArray());

			// removing duplicate states
			nodeXMatrix = new Vector<Vector<BDD>>();
			for (int i = 0; i < xMatrix.size(); i++) {
				Vector<BDD> xRow = new Vector<BDD>();
				BDD rowBDD = Env.FALSE();
				for (int j = 0; j < xMatrix.get(i).size(); j++) {
					// filtering
					BDD tmp = node.nodeBDD.id().andWith(rowBDD.not());
					BDD xCell = xMatrix.get(i).get(j).and(tmp);//.and(safeties.not());
					
					xRow.add(xCell);
					rowBDD.orWith(xCell.id());
					tmp.free();
				}
				nodeXMatrix.add(xRow);
			}
		
			String afterFilt = "matrix for z-rank " + zRank + " after filtering:\n";
			k = 0;
			ArrayList<BddStringWrapper> afterBdds = new ArrayList<BddStringWrapper>();
			for (int i = 0; i < nodeXMatrix.size(); i++) {
				for (int j = 0; j < nodeXMatrix.get(i).size(); j++) {
					afterFilt += "xMatrix[" + i + "][" + j + "] = {" + k + "}\n";
					afterBdds.add(new BddStringWrapper(nodeXMatrix.get(i).get(j)));
					k++;
				}
			}
			
			log.log(Level.FINER, afterFilt, afterBdds.toArray());
			log.finer("END");
		}
		
		/**
		 * @deprecated added instead to JusticeViolationGraph::removeAttrFromCands, 
		 * since we want to add the ASSUMPTION_RANK variable to the xMatrix cells only after removing 
		 * from them the attractorFromCands states
		 * (in attractorFromStates, we do not care about the ASSUMPTION_RANK since in any case these
		 * states will force the system either to safety violation or to another cycle).
		 */
		public void addAssumpRankToXMatrix() {
			log.finer("START");
			String beforeAdd = "matrix for z-rank " + zRank + " before adding ASSUMPTION_RANK:\n";
			int k = 0;
			ArrayList<BddStringWrapper> beforeBdds = new ArrayList<BddStringWrapper>();
			for (int i = 0; i < nodeXMatrix.size(); i++) {
				for (int j = 0; j < nodeXMatrix.get(i).size(); j++) {
					beforeAdd += "nodeXMatrix[" + i + "][" + j + "] = {" + k + "}\n";
					beforeBdds.add(new BddStringWrapper(nodeXMatrix.get(i).get(j)));
					k++;
				}
			}
			log.log(Level.FINER, beforeAdd, beforeBdds.toArray());

			// add current row marker variable ASSUMPTION_RANK
			for (int i = 0; i < nodeXMatrix.size(); i++) {
				for (int j = 0; j < nodeXMatrix.get(i).size(); j++) {
					// filtering
					BDD xCell = nodeXMatrix.get(i).get(j);
					// adding variable
					xCell.andWith(Env.getBDDValue(ASSUMPTION_RANK, "" + i).id());
					nodeXMatrix.get(i).set(j, xCell);
				}
			}
		
			String afterAdd = "matrix for z-rank " + zRank + " after adding ASSUMPTION_RANK:\n";
			k = 0;
			ArrayList<BddStringWrapper> afterBdds = new ArrayList<BddStringWrapper>();
			for (int i = 0; i < nodeXMatrix.size(); i++) {
				for (int j = 0; j < nodeXMatrix.get(i).size(); j++) {
					afterAdd += "xMatrix[" + i + "][" + j + "] = {" + k + "}\n";
					afterBdds.add(new BddStringWrapper(nodeXMatrix.get(i).get(j)));
					k++;
				}
			}
			
			log.log(Level.FINER, afterAdd, afterBdds.toArray());
			log.finer("END");
		}
		
		int zRank;
		int currNodeIndRG;
		RGNode node;
		// map of xMatrixRowInd -> xMatrixColInd -> StartPoint
		HashMap<Integer, HashMap<Integer, StartPointInfo>> xCellToStartPointMap;
		Vector<Vector<BDD>> nodeXMatrix;
		BDD innerNodeTransitions;
	}
	
	/**
	 * This class represents a sub-graph of a JVTS cycle node, displaying the satisfaction of different
	 * assumptions. It was not displayed to the user in the GUI (implementation for displaying it missing).
	 * Every node in this sub-graph is a set of concrete states used to satisfy a specific environment assumption
	 * while violating a system justice guarantee.
	 *
	 */
	class AssumptionSatisfactionGraph {
		public AssumptionSatisfactionGraph() {
			nodes = new HashMap<Integer, AssumptionSatNode>();
			edges = new HashMap<Integer, Vector<JVGEdge>>();
		}
		
		public String toString() {
			String res = "AssumptionSatisfactionGraph:\n";
			res += "nodes:\n";
			Iterator<Map.Entry<Integer, AssumptionSatNode>> nodeItr = nodes.entrySet().iterator();
			while (nodeItr.hasNext()) {
				Map.Entry<Integer, AssumptionSatNode> entry =
						(Map.Entry<Integer, AssumptionSatNode>) nodeItr.next();
				res += "node index " + entry.getKey() + ":\n";
				res += entry.getValue().toString() + "\n";
			}
			res += "edges:\n";
			Iterator<Map.Entry<Integer, Vector<JVGEdge>>> edgeItr = edges.entrySet().iterator();
			while (edgeItr.hasNext()) {
				Map.Entry<Integer, Vector<JVGEdge>> entry =
						(Map.Entry<Integer, Vector<JVGEdge>>) edgeItr.next();
				res += "edge incoming node index " + entry.getKey() + ":\n";
				res += entry.getValue().toString() + "\n";
			}
			
			return res;
		}
		
		HashMap<Integer, AssumptionSatNode> nodes;
		// each node should have only one outgoing edge, but we use Vector for a more general
		// case
		HashMap<Integer, Vector<JVGEdge>> edges;
	}
	
	/**
	 * A node in the AssumptionSatisfactionGraph
	 */
	class AssumptionSatNode extends JVGCommonNode {
		public AssumptionSatNode() {
			assumptionInd = -1;
			invariants = new Vector<JVGInvariant>();
			nodeBDD = Env.FALSE();
		}
		
		public String toString() {
			String str = "assumptionInd = " + assumptionInd + "\n";
			if (satAssump == null) {
				System.out.println("SAT ASSUMP IS NULL!");
			}
			if (Tracer.getNiceStringForId(satAssump.traceId) == null) {
				str += "satAssump = null\n";
			} else {
				str += "satAssump = " + Tracer.getNiceStringForId(satAssump.traceId) + "\n";
			}
			str += "assumption node BDD = " + Env.toNiceSignleLineString(nodeBDD) + "\n";
			for (int i = 0; i < invariants.size(); i++) {
				str += "\n" + invariants.get(i).toString();
			}
			return str;
		}
		
		// index of the satisfied assumption
		int assumptionInd;
		BehaviorInfo satAssump;
	}
	
	/**
	 * A point (i.e. xMatrix cell) in a path computed during traversal over the xMatrix, in the context
	 * of a specific system justice violation.
	 */
	class JVGXPoint {
		public JVGXPoint() {
			this.xRow = -1;
			this.xCol = -1;
			this.oldBDD = Env.FALSE();
			this.newBDD = Env.FALSE();
		}
		
		public String toString() {
			String res = "xRow = " + this.xRow + ", xCol = " + this.xCol + "\n"
					+ "oldBDD = " + Env.toNiceSignleLineString(this.oldBDD) + "\n"
					+ "newBDD = " + Env.toNiceSignleLineString(this.newBDD);
			return res;
		}
		
		int xRow;
		int xCol;
		// The BDD in this cell before computing successors in this cell from the previous 
		// JVGXPoint in the path
		BDD oldBDD;
		// The BDD in this cell after computing successors in this cell from the previous
		// JVGXPoint in the path
		BDD newBDD;
	}
	
	/**
	 * A path along an xMatrix, satisfying all assumptions while violating a system justice guarantee.
	 * Always ending in a cycle (i.e., a JVGXPoint which already appeared previously in the path). 
	 */
	class JVGXPath implements Cloneable{
		public JVGXPath() {
			this.path = new Vector<JVGXPoint>();
			this.higherZRankJVGInds = new Vector<Integer>();
			this.isCycle = false;
			this.cycleStartInd = -1;
		}
		
		public boolean ended() {
			if (path.isEmpty())
				return true;
			return path.get(path.size()-1).newBDD.isZero();
		}
		
		public boolean rowEnded() {
			if (path.isEmpty())
				return true;
			return (path.get(path.size()-1).xCol == 0);
		}
		
		public String toString() {
			String res = "isCycle = " + this.isCycle + ", cycleStartInd = " + this.cycleStartInd + "\n";
			for (int i = 0; i < this.path.size(); i++) {
				res += "path[" + i + "] = " + this.path.get(i).toString() + "\n";
			}

			return res;
		}
		
		@Override
		protected JVGXPath clone() throws CloneNotSupportedException {
			JVGXPath newPath = new JVGXPath();
			newPath.isCycle = this.isCycle;
			newPath.cycleStartInd = this.cycleStartInd;
			for (int i = 0 ; i < this.path.size(); i++) {
				newPath.path.add(this.path.get(i));
			}
			for (int i = 0 ; i < this.higherZRankJVGInds.size(); i++) {
				newPath.higherZRankJVGInds.add(this.higherZRankJVGInds.get(i));
			}
			
			return newPath;
		}
		
		Vector<JVGXPoint> path;
		/**
		 * @deprecated - previously held the indices of JVG nodes already computed which lead to this path
		 */
		Vector<Integer> higherZRankJVGInds;
		boolean isCycle;
		int cycleStartInd;
	}

	/**
	 * Constructor, calls the second constructor without logging and with computing Assumption Satisfaction Sub-graphs.
	 * @param rg The Rabin game solution
	 * @param model The game model, changed by the JVG computation
	 */
	public JusticeViolationGraph(RabinGame rg, GameModel model) {
		this(rg, model, Level.OFF, true /*calcAssumpGraph*/);
	}
	
	/**
	 * 
	 * @param rg The Rabin game solution
	 * @param model The game model, changed by the JVG computation
	 * @param logLevel level of traces to log
	 * @param calcAssumpGraph flag indicating if the Assumption Satisfaction Sub-graphs should be computed
	 */
	public JusticeViolationGraph(RabinGame rg, GameModel model, Level logLevel, boolean calcAssumpGraph) {
		// initializing the logger
		log.setUseParentHandlers(false);
		SimpleFormatter fmt = new SimpleFormatter();
		StreamHandler sh = new StreamHandler(System.out, fmt);
		sh.setLevel(logLevel);
		log.setLevel(logLevel);
		log.addHandler(sh);
		LogManager lm = LogManager.getLogManager();
		lm.addLogger(log);
		
		// initializing the members
		this.calcAssumpGraph = calcAssumpGraph;
		this.env = model.getEnv();
		this.sys = model.getSys();
		this.nodes = new HashMap<Integer, JVGNode>();
		this.edges = new HashMap<Integer, Vector<JVGEdge>>();
		this.ini = rg.getInitialStates().id();
		this.safeties = getSafeties(model.getSysBehaviorInfo(), false);
		this.aux = conjunct(getSafeties(model.getSysBehaviorInfo(), true)).and(sys.getDoms());
		this.sysBehaviorInfo =  model.getSysBehaviorInfo();
		this.envBehaviorInfo = model.getEnvBehaviorInfo();
		// Note - the actual safeties in the info.safeties can contain both unprimed and primed
		// variables, so they cannot be considered as states, but rather as transitions.
		// safetiesBDD should represent the unsafe states only, not the safe states which can lead to unsafe states
		this.safetiesBDD = sys.trans().exist(Env.globalPrimeVars()).not();
		log.log(Level.FINE, "safetiesBDD = {0}", new BddStringWrapper(this.safetiesBDD));
		
		// Copying the Rabin game ZMem. After Rabin Game, it contains:
		// in cell i - all states that either violate system justice i or from which the env can force
		// the system to a state in cell i-1.
		// (for i = 0 - states from which the env can force the system to violate a system safety)
		this.memZ = new Vector<BDD>();
		for (int i = 0; i < rg.getMem().getZMem().size(); i++) {
			this.memZ.add(rg.getMem().getZMem().get(i).id());
		}
		
		// Copying the Rabin game ZMem. After Rabin Game, XMem contains:
		// in cell i all the states as in memZ[i], divided to a matrix where each row represent an environment
		// justice being satisfied. Each cell in the row is a step towards satisfaction of relevant environment justice.
		this.memX = new Vector<Vector<Vector<BDD>>>();
		for (int i = 0; i < rg.getMem().getXMem().size(); i++) {
			Vector<Vector<BDD>> temp = new Vector<Vector<BDD>>();
			for (int j = 0; j < rg.getMem().getXMem().get(i).size(); j++) {
				Vector<BDD> temp2 = new Vector<BDD>();
				for (int k = 0; k < rg.getMem().getXMem().get(i).get(j).size(); k++) {
					temp2.add(rg.getMem().getXMem().get(i).get(j).get(k).id());
				}
				temp.add(temp2);
			}
			this.memX.add(temp);
		}
		
		// collecting the aux variables, to be used when displaying to the user in the GUI
		this.auxVars = new HashSet<String>();
		for (int i = 0; i < this.sys.getAuxFields().size(); i++) {
			auxVars.add(sys.getAuxFields().get(i).getName());
		}
		
	}
	
	/**
	 * Conjuncting all safeties given
	 * @param safeties A vector of BehaviorInfo containing the safeties
	 * @return The conjunction of all safeties
	 */
	private BDD conjunct(Vector<BehaviorInfo> safeties) {
		BDD conj = Env.TRUE();
		for (BehaviorInfo i : safeties) {
			conj.andWith(i.safety.id());
		}
		return conj;
	}

	/**
	 * Getting the safeties of the system. In the case of aux being True, getting the auxiliary safeties only.
	 * If aux is false, getting the negation of the non-auxiliary safeties only (since we will need for 
	 * non-auxiliary safeties the violation of the safeties).
	 * @param sysBehaviorInfo The system BehaviorInfo
	 * @param aux Flag indicating if auxiliary safeties should be collected or not
	 * @return A vector of BehaviorInfo of the system safeties, according to the passed aux flag.
	 */
	private Vector<BehaviorInfo> getSafeties(List<BehaviorInfo> sysBehaviorInfo, boolean aux) {
		Vector<BehaviorInfo> safeties = new Vector<>();
		for (BehaviorInfo bi : sysBehaviorInfo) {
			if (bi.isSafety() && bi.aux == aux) {
				BehaviorInfo c = new BehaviorInfo();
				c.traceId = bi.traceId;
				// BDD in BehaviorInfo for safety is the actual safety, not its violation. We want the violation of it
				// for non aux safeties in order to determine if a node can force to violate a safety.
				c.safety = aux ? bi.safety.id() : bi.safety.not();
				safeties.add(c);
			}
		}
		return safeties;
	}

	/**
	 * Calculating Ranking Graph (RG).
	 * The ini BDD in this implementation could be non deterministically chosen.
	 */
	public void calcRankingGraph() {
		RankingGraph graph = calcRankingGraphHelper(ini, safeties, aux);
		printRankingGraph(graph);
		freeRankingGraph(graph);
	}

	/**
	 * Helper function for computing the Ranking graph.
	 * The flow of the calucaltion is:
	 * 1. Remove duplicate states from the memZ vector, leaving them only in the cell with the lowest index
	 * 2. Compute the initial node of the Ranking graph from the input ini (a marker node which points to
	 * the nodes which actually contain the initial states)
	 * 3. Iterate over memZ from highest index to lowest, at each cell computing a node only from states 
	 * reachable from previously computed node.
	 * 
	 * @param ini set of initial concrete states
	 * @param safeties list of safeties
	 * @param aux auxiliary constraints
	 * @return
	 */
	private RankingGraph calcRankingGraphHelper(BDD ini,
			Vector<BehaviorInfo> safeties, BDD aux) {
		
		// Initialize Ranking Graph structs
		RankingGraph rankGraph = new RankingGraph();
		
		// Print the memZ vector (for debugging)
		log.finest("memZ:");
		for (int i = 0; i < memZ.size(); i++) {
			log.log(Level.FINEST, "memZ[" + i + "] = {0}", new BddStringWrapper(memZ.get(i)));
		}
		
		// Refine the memZ vector (remove duplicate states)
		Vector<BDD> refinedZ = new Vector<BDD>();
		refineZVector(refinedZ);
		
		// Print the refined memZ vector (for debugging)
		log.finest("refinedZ:");
		for (int i = 0; i < refinedZ.size(); i++) {
			log.log(Level.FINEST, "refinedZ[" + i + "] = {0}", new BddStringWrapper(refinedZ.get(i)));
		}
		
		// Initialize local temporary vector used for calculation
		Vector<BDD> tempZ = new Vector<BDD>();
		for (int i = 0; i < memZ.size(); i++) {
			tempZ.add(Env.FALSE());
		}
		
		int nodeKeyRG = 0;
		// Calculate initial node
		RGNode initNode = new RGNode();
		Vector<RGEdge> initEdges = new Vector<RGEdge>();
		BDD trans = env.trans().and(sys.trans());
		calcNodeAndEdgesRG(refinedZ, ini, refinedZ.size() /*z-rank limit*/, tempZ, 
				safeties, aux, initNode, initEdges, trans);
		rankGraph.nodes.put(nodeKeyRG, initNode);
		rankGraph.edges.put(nodeKeyRG, initEdges);
		
		// sanity test
		if (initEdges.isEmpty()) {
			log.severe("No edges from initial node to any z-rank node - i.e. winning states" +
					" for env are unreachable from initial state - should be realizable");
			return rankGraph;
		}
		nodeKeyRG++;
		
		// Map used for converting z-rank to Ranking Graph node indices
		HashMap<Integer, Integer> zRankToRGNodeInd = new HashMap<Integer, Integer>();
		
		// Go over temporary vector from highest z-rank to lowest
		for (int i = tempZ.size() - 1; i >= 0; i--) {
			if (!tempZ.get(i).isZero()) {
				RGNode node = new RGNode();
				Vector<RGEdge> edges = new Vector<RGEdge>(); 
				// Calculate the next node and its edges
				calcNodeAndEdgesRG(refinedZ, tempZ.get(i), i, tempZ, safeties, aux, node, edges, trans);
				rankGraph.nodes.put(nodeKeyRG, node);
				rankGraph.edges.put(nodeKeyRG, edges);
				zRankToRGNodeInd.put(i, nodeKeyRG);
				nodeKeyRG++;	
			}
		}
		trans.free();
				
		// The edges currently contain as destInds the z-rank of the nodes (since we didn't
		// have the node RG indices when we created the edges).
		// We convert them so that they will contain the actual node indices
		// (in Ranking Graph, there's a 1-to-1 mapping from z-rank to node index. However, we want
		// RGEdge to contain the node index and not the z-rank to make it more uniform with the
		// Justice Violation Graph, where several nodes can have the same z-rank)
		convertEdgesRG(rankGraph.edges, zRankToRGNodeInd);

		return rankGraph;
	}
	
	/**
	 * Removing duplicate states from the memZ, leaving them only in the cell with the lowest index
	 * @param refinedZ output, contains the memZ without the duplicate states
	 */
	private void refineZVector(Vector<BDD> refinedZ) {
		BDD lower = Env.FALSE();
		for (int i = 0; i <memZ.size(); i++) {			
			refinedZ.add(memZ.get(i).id().andWith(lower.not()));
			lower.orWith(memZ.get(i));
		}
		lower.free();
	}

	/**
	 * Compute a RG node and its successors. The flow is:
	 * Given a node represented by currBDD, constructed from memZ cell with index of zRankLimit, 
	 * we go over each memZ cell in increasing order (up to the index zRankLimit), and in each we search
	 * for successors of the states represented by currBDD. If successors are found, then an RG edge is 
	 * created. 
	 * @param refinedZ The memZ after duplicates were removed
	 * @param currBDD The BDD representing the set of states in the current node being calculated
	 * @param zRankLimit the index in the memZ of the cell from which the current node is calculated
	 * @param tempZ A temporary array indicating the successors in memZ located thus far for pervious nodes
	 * @param safeties A vector of the safeties
	 * @param aux The auxiliary constraints
	 * @param node The resulting calculated node
	 * @param edges The resulting calculated edges
	 * @param trans All valid transitions by the specification (passed as a performance optimization)
	 */
	private void calcNodeAndEdgesRG(Vector<BDD> refinedZ, BDD currBDD, int zRankLimit, Vector<BDD> tempZ,
			Vector<BehaviorInfo> safeties, BDD aux, RGNode node, Vector<RGEdge> edges, BDD trans) {
		// Print the relevant input - for debugging
		log.log(Level.FINER, "currBDD = {0}, zRankLimit = " + zRankLimit, new BddStringWrapper(currBDD));
		
		BDD toIntersect;
		// When handling the initial node, we want to locate the z-rank nodes where the actual
		// initial states are located. The initial node in RG will not contain any states, but
		// rather will be a marker pointing to the nodes containing the initial states.
		// If we're not handling the initial node, then we want to look at the successors of the
		// current node
		if (zRankLimit == refinedZ.size()) {
			log.finest("Handling the initial node");
			toIntersect = currBDD.id();
		} else {
			toIntersect = calcSuccNodeRG(currBDD, trans);
		}
		
		for (int i = 0; i < zRankLimit; i++) {
			if (toIntersect.isZero()) {
				log.finest("no more transitions from this node");
				break;
			}
			BDD temp = refinedZ.get(i).and(toIntersect);
			if (!temp.isZero()) {
				// Get all reachable states inside the current memZ cell
				FixPoint iter;
				for (iter = new FixPoint(true); iter.advance(temp.id());) {
					BDD nextSuccs = calcSuccNodeRG(temp, trans);
					temp.orWith(nextSuccs.and(refinedZ.get(i)));
					nextSuccs.free();
				}
				
				tempZ.get(i).orWith(temp.id());
				toIntersect.andWith(temp.id().not());
				RGEdge edge = new RGEdge();
				edge.destInd = i; // temporarily, we save here the z-rank, not the node index!
				edges.add(edge);
			}

			temp.free();
		}
		
		node.zRankNum = zRankLimit;
		node.isInitialNode = (node.zRankNum == refinedZ.size());
		if (node.isInitialNode) {
			// Even though the initial node does not represent any states, we still save the 
			// initial BDD in it to be used in the calculation of the Justice Violation Graph.
			node.nodeBDD = currBDD;
		} else {
			node.nodeBDD = currBDD;
			node.isSafeyViolated = isSafetyViolatedRG(node.nodeBDD);
			node.violatedSafeties = getViolatedSafetiesRG(node.nodeBDD, safeties, aux);
			// sanity
			assert (node.isSafeyViolated == !node.violatedSafeties.isEmpty());
			// this checks if all states can force to safety violation - but this
			// doesn't mean that a justice isn't violated as well along the way
			node.isOnlySafetyViolated = isOnlySafetyViolatedRG(node.nodeBDD);
			node.violatedJusticeNum = (node.zRankNum % sys.justiceNum()) + 1;
		}
	}
	
	/**
	 * Returning all valid successors of the given set of states (represented by BDD) and a give transition
	 * relation
	 * @param currNode BDD representing a set of states
	 * @param trans BDD representing a transition relation
	 * @return BDD representing the successors
	 */
	private BDD calcSuccNodeRG(BDD currNode, BDD trans) {
		return Env.succ(currNode, trans);
	}
	
	/**
	 * @deprecated Used to provide a list of safeties which are violated in a set of states. Was deprecated
	 * due to resulting in over-approximation of violated safeties.
	 * @param current The set of states to check for safety violation
	 * @param safeties Vector of safeties
	 * @param aux Auxiliary constraints
	 * @return Vector of safeties violated (empty vector after being deprecated)
	 */
	private Vector<BehaviorInfo> getViolatedSafetiesRG(BDD current,
			Vector<BehaviorInfo> safeties, BDD aux) {
		Vector<BehaviorInfo> violatedSafeties = new Vector<BehaviorInfo>();
/*
		for (BehaviorInfo safe : safeties) {
			if (safe != null) {
				// check if safety is violated
				// safeties are the actual unsafe states. We want to check if there are states leading to them.
				// The problem - sometimes safeties are states, sometimes they are transitions.
				// temp is the safety violation control states (states which can(!) lead to unsafe states)
				boolean hasPrimedVars = Env.containPrimeVars(safe.safety);
				BDD temp = Env.FALSE();
				if (hasPrimedVars) {
					temp = safe.safety.exist(Env.globalPrimeVars());
				} else {
					temp = env.trans().and(sys.trans()).and(Env.prime(safe.safety)).exist(Env.globalPrimeVars());
				}
				// these are the actual unsafe states
				BDD safeControlStates = getSafetyControlStatesRG(
						safe.safety, aux);
				if (!current.and(temp.or(safeControlStates)).isZero()) {
					violatedSafeties.add(safe);
				}
				safeControlStates.free();
			}
		}*/

		return violatedSafeties;
	}

	/**
	 * @deprecated No longer used
	 * @param safe 
	 * @param aux
	 * @return
	 */
	@SuppressWarnings("unused")
	private BDD getSafetyControlStatesRG(BDD safe, BDD aux) {
		return env.trans().and(safe.or(aux.not()).forAll(sys.modulePrimeVars()))
				.exist(env.modulePrimeVars());
	}

	/**
	 * @deprecated Used to indicate if a node violated a safety. deprecated due to over-approximation
	 * of safeties violated
	 * @param current BDD representing the current node
	 * @return True if the node violated a safety, False otherwise (after deprecation - always False)
	 */
	private boolean isSafetyViolatedRG(BDD current) {
		return false;
/*		BDD safetyViolateStates = env.controlStates(sys, Env.FALSE());
		FixPoint iter;
		for (iter = new FixPoint(false); iter.advance(safetyViolateStates);) {
			safetyViolateStates = safetyViolateStates.or(env.controlStates(sys, safetyViolateStates));
		}
		
		return !current.and(safetyViolateStates).isZero();*/
	}

	/**
	 * @deprecated Used to indicate if a node only violated a safety (i.e., did not violate a justice). 
	 * Deprecated due to over-approximation of safeties violated.
	 * @param current BDD representing the current node
	 * @return True if the node only violated safeties, False otherwise (always False after deprecation)
	 */
	private boolean isOnlySafetyViolatedRG(BDD current) {
		return false;
		/*
		BDD safetyViolateStates = env.controlStates(sys, Env.FALSE());
		FixPoint iter;
		for (iter = new FixPoint(false); iter.advance(safetyViolateStates);) {
			safetyViolateStates = safetyViolateStates.or(env.controlStates(sys, safetyViolateStates));
		}
		
		return current.and(safetyViolateStates.not()).isZero();*/
	}

	/**
	 * Converting indices in edges of the Ranking Graph, needed as a result of the implementation of the
	 * Ranking Graph algorithm
	 * @param edges Mapping of edges ids to edges
	 * @param zRankToRGNodeInd mapping of indices in memZ to RG node ids
	 */
	private void convertEdgesRG(HashMap<Integer, Vector<RGEdge>> edges,
			HashMap<Integer, Integer> zRankToRGNodeInd) {
		Iterator<Map.Entry<Integer, Vector<RGEdge>>> itr = edges.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<Integer, Vector<RGEdge>> entry = (Map.Entry<Integer, Vector<RGEdge>>) itr.next();
			for (int i = 0; i < entry.getValue().size(); i++) {
				RGEdge convertedEdge = new RGEdge();				
				convertedEdge.destInd = zRankToRGNodeInd.get(entry.getValue().get(i).destInd);
				entry.getValue().set(i, convertedEdge);
			}
		}
	}

	/**
	 * Printing the nodes and edges in a Ranking graph
	 * @param graph The Ranking graph to print
	 */
	private void printRankingGraph(RankingGraph graph) {
		String str = "";
		Iterator<Map.Entry<Integer, RGNode>> itr = graph.nodes.entrySet().iterator();
		ArrayList<Object> params = new ArrayList<Object>();
		int k = 0;
		while (itr.hasNext()) {
			Map.Entry<Integer, RGNode> entry = (Map.Entry<Integer, RGNode>) itr.next();
			if (entry.getKey() == 0) {
				str += "Initial Node (key: 0):\n";
				str += "nodeBDD = {" + k + "}\n";
				params.add(new BddStringWrapper(entry.getValue().nodeBDD));
				k++;
			} else {
				str += "Node (key: " + entry.getKey() + "):\n {" + k + "}\n";
				params.add(entry.getValue());
				k++;
			}
			str += "\tTransitions to:\n";
			Vector<RGEdge> currEdges = graph.edges.get(entry.getKey());
			if (currEdges.size() == 0) {
				str += "\t\tno node\n";
			} else {
				for (int j = 0; j < currEdges.size(); j++) {
					str += currEdges.get(j).toString("\t\t") + "\n";
				}
			}
		}
		log.log(Level.INFO, str, params.toArray());
	}

	/**
	 * Freeing all BDDs composing the nodes in the Ranking graph
	 * @param graph The Ranking graph to free
	 */
	private void freeRankingGraph(RankingGraph graph) {
		// free nodes BDDs
		Iterator<Map.Entry<Integer, RGNode>> itr = graph.nodes.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<Integer, RGNode> entry = (Map.Entry<Integer, RGNode>) itr.next();
			if (!entry.getValue().nodeBDD.isFree()) {
				entry.getValue().nodeBDD.free();
			}
		}
	}

	/**
	 * Check if the environment can force the system to deadlock from an initial state.
	 * @return True if the environment can force the system to deadlock from initial state, False otherwise.
	 */
	private boolean handleIniSafetyViolation() {
		BDD sysDead = sys.initial().id().impWith(env.controlStates(sys, Env.FALSE()));
		BDD envKillIni = env.initial().id().andWith(sysDead.forAll(sys.moduleUnprimeVars()));
		sysDead.free();
		if (!envKillIni.isZero()) {
			BDD iniEnvChoice = CoreUtil.satOne(envKillIni, env.moduleUnprimeVars());
			envKillIni.free();
			JVGNode iniNode = new JVGNode();
			iniNode.type = JVGNodeType.INIT_NODE;
			iniNode.nodeBDD = iniEnvChoice.and(ini);
			iniNode.zRankNum = -1;
			iniNode.transitions = iniEnvChoice.id();
			iniNode.violatedSafeties = getViolatedSafetiesRG(iniNode.nodeBDD, safeties, aux);
			iniNode.isSafeyViolated = isSafetyViolatedRG(iniNode.nodeBDD);
			iniNode.isOnlySafetyViolated = isOnlySafetyViolatedRG(iniNode.nodeBDD);
			nodes.put(0, iniNode);
			
			JVGNode attrNode = new JVGNode();
			attrNode.type = JVGNodeType.ATTRACTOR_NODE_FROM_CYCLE;
			attrNode.nodeBDD = iniEnvChoice.and(ini);
			attrNode.zRankNum = 0;
			attrNode.transitions = Env.FALSE();
			attrNode.violatedJusticeNum = attrNode.zRankNum % sys.justiceNum();
			attrNode.violatedSafeties = getViolatedSafetiesRG(iniNode.nodeBDD, safeties, aux);
			attrNode.isSafeyViolated = isSafetyViolatedRG(attrNode.nodeBDD);
			attrNode.isOnlySafetyViolated = isOnlySafetyViolatedRG(attrNode.nodeBDD);
			nodes.put(1, attrNode);
			
			JVGEdge edge = new JVGEdge();
			edge.destInd = 1;
			Vector<JVGEdge> iniEdges = new Vector<JVGEdge>();
			iniEdges.add(edge);
			edges.put(0, iniEdges);
			
			return true;
		}
		
		return false;
	}
	
	
	/**
	 * Computing the JVG. The flow is described (with some minor adjustments) in "A Symbolic Justice Violations
	 * Transition System For Unrealizable GR(1) Specifications" paper.
	 * @throws Exception
	 */
	public void computeGraph() throws Exception {
		log.info("START");
		log.log(Level.INFO, "ini = {0}", new BddStringWrapper(ini));
		
		// handle special case where all initial states contain an env choice that can force sys choice 
		// to violate safeties. Not mentioned in the paper.
		if (handleIniSafetyViolation()) {
			addInvariants();
			log.log(Level.FINE, "initial state can force sys to violate safety");
			return;
		}
		
		// Compute the Ranking Graph
		RankingGraph rankGraph = calcRankingGraphHelper(ini, safeties, aux);
		log.info("ranking graph: ");
		printRankingGraph(rankGraph);
		log.info("ranking graph - end");
		
		// Add the "ASSUMPTION_RANK" variable to the specification variables.
		addEnvAssumpVars();

		LinkedList<RGNodeProcessInfo> workList = new LinkedList<RGNodeProcessInfo>();
		// Compute the initial JVG marker node (i.e., pointing to all locations in Ranking graph
		// where there are initial states). Add all Ranking graph nodes pointed to by the marker node
		// to the work list.
		Vector<RGNodeProcessInfo> destinations = handleInitialNode(rankGraph);
		String destsStr = "destinations:\n";
		for (int i = 0; i < destinations.size(); i++) {
			destsStr += "destination[" + i + "] = {" + i + "}\n";
		}
		log.log(Level.FINE, destsStr, destinations.toArray());
		addDestsToWorkList(workList, destinations);
		int jvgNodeInd = 1;
		// The main flow: while the work-list is not empty, get a Ranking graph node from it (in the form
		// of a RGNodeProcessInfo). Process it (compute JVG cycle, attractor-from-cycle and attractor-to-cycle
		// states from it) and compute relevant successors in other Ranking graph nodes, and add them to the work-list
		// (if not already in the work list). 
		// work-list is sorted by z-rank from highest to lowest.
		while (!workList.isEmpty()) {
			log.fine("workList before iteration:");
			String beforeWLStr = "";
			for (int i = 0; i < workList.size(); i++) {
				beforeWLStr += "worklist[" + i + "] = {" + i + "}\n";
			}
			log.log(Level.FINE, beforeWLStr, workList.toArray());

			RGNodeProcessInfo nodeToHandle = workList.pop();
			log.fine("handling node: " + nodeToHandle.toString());
			Vector<Integer> JVGNodesInds = handleNode(nodeToHandle, rankGraph, jvgNodeInd);
			destinations = handleDestinations(nodeToHandle, rankGraph, JVGNodesInds);
			addDestsToWorkList(workList, destinations);
			jvgNodeInd += JVGNodesInds.size();
			
			log.fine("workList after iteration:");
			String afterWLStr = "";
			for (int i = 0; i < workList.size(); i++) {
				afterWLStr += "worklist[" + i + "] = {" + i + "}\n";
			}
			log.log(Level.FINE, afterWLStr, workList.toArray());
		}
		
		// Add invariants to JVG nodes 
		addInvariants();
		log.fine("computed Justice Violation Graph:");
		printJVGraph();
		log.info("END");
	}
	
	/**
	 * Adds the ASSUMPTION_RANK variable to the specification
	 */
	private void addEnvAssumpVars() {
		log.fine("START");
		try {
			this.env.addVar(ASSUMPTION_RANK, 0,env.justiceNum()-1, true /*aux*/);
			auxVars.add(ASSUMPTION_RANK);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			log.severe("ERROR - can't add aux variables for tracking assumptions");
			e.printStackTrace();
		}
		log.fine("END");
	}

	/**
	 * Computes the initial marker JVG node and finds the destinations of the initial states
	 * (i.e., the Ranking graph nodes containing the initial states and the specific memX cells containing
	 * these state in the Ranking graph node)
	 * @param rankGraph The compute Ranking graph
	 * @return The Ranking graph nodes containing the initial states (successors of the marker initial node)
	 * Side-effect - updates the nodes member with the initial node.
	 */
	private Vector<RGNodeProcessInfo> handleInitialNode(RankingGraph rankGraph) {
		log.fine("START");
		
		Vector<RGNodeProcessInfo> destinations = new Vector<RGNodeProcessInfo>();
		RGNode initialNode = rankGraph.nodes.get(0);
		JVGNode initialJVGNode = new JVGNode();
		initialJVGNode.zRankNum = initialNode.zRankNum;
		Vector<RGEdge> edges = rankGraph.edges.get(0);
		Vector<RGNodeProcessInfo> destsTemp = new Vector<RGNodeProcessInfo>();
		// sorting destinations in increasing z-rank order
		for (int i = 0; i < edges.size(); i++) {
			RGNodeProcessInfo info = new RGNodeProcessInfo();
			RGNode destNode = rankGraph.nodes.get(edges.get(i).destInd);
			info.zRank = destNode.zRankNum;
			info.currNodeIndRG = edges.get(i).destInd;
			info.node = destNode;

			info.setXMatrix(memX.get(destNode.zRankNum), this.safetiesBDD);
			destsTemp.add(info);
		}

		Collections.sort(destsTemp);
		String destsTmpStr = "destsTemp sorted:\n";
		for (int i = 0; i < destsTemp.size(); i++) {
			destsTmpStr += "destsTemp[" + i + "] = {" + i + "}\n";
		}
		log.log(Level.FINER, destsTmpStr, destsTemp.toArray());
		
		BDD toCheck = initialNode.nodeBDD.id();
		for (int i = 0; i < destsTemp.size(); i++) {
			log.log(Level.FINER, "BDD to check for dest of z-rank " + destsTemp.get(i).zRank + ": {0}",
					new BddStringWrapper(toCheck));
			
			RGNodeProcessInfo currDest = destsTemp.get(i);
			// The below explanation is less relevant for the initial node (since it doesn't have the cycle states
			// or the ASSUMPTION_RANK in it), but in order for the destination check to have the same behavior in all
			// nodes, I implement it here as well:
			// we go over all of the xMatrix because of the ASSUMPTION_RANK env variable we added:
			// when we check for the start points here, the destination doesn't have this env variable yet, so
			// we would find start points in first row encountered. However, when starting to handle this destination,
			// we will add the ASSUMPTION_RANK variable to the different xCells, and a cell which was previously a 
			// start point might no longer be one (because the transition determined for its predecessor state in the 
			// leading node might require a different ASSUMPTION_RANK).
			// However, we can't just initially add the ASSUMPTION_RANK to all xCells in all nodes, since when computing the
			// attractor from states, we need to be able to ignore the ASSUMPTION_RANK in the transition.
			for (int j = 0; j < currDest.nodeXMatrix.size(); j++) {
				for (int k = 0; k < currDest.nodeXMatrix.get(j).size(); k++) {
					BDD xCell = currDest.nodeXMatrix.get(j).get(k).and(Env.getBDDValue(ASSUMPTION_RANK, "" + j).id());
					log.log(Level.FINEST, "xCell["+j+"]["+k+"] = {0}",
							new BddStringWrapper(xCell));
					log.log(Level.FINEST, "toCheck = {0}", new BddStringWrapper(toCheck));
					BDD succ = toCheck.and(xCell);
					if (!succ.isZero()) {
						// get a concrete environment choice
						// choose the first env choice given in the iterator - this is a random choice.
						BDD envChoice = CoreUtil.satOne(succ, env.moduleUnprimeVars());
						log.log(Level.FINER, "the env choice taken: {0}", new BddStringWrapper(envChoice));
						toCheck.andWith(envChoice.id());
						log.log(Level.FINEST, "toCheck after taking env choice: {0}", new BddStringWrapper(toCheck));
						StartPointInfo startPoint = new StartPointInfo();
						startPoint.prevNodeJVGInds = new Vector<Integer>();
						startPoint.prevNodeJVGInds.add(0); // the prev node is the initial node
						startPoint.startPoint = succ.and(envChoice);
						if (currDest.xCellToStartPointMap.containsKey(j)) {
							if (currDest.xCellToStartPointMap.get(j).containsKey(k)) {
								// if we already have a start point in this cell - add to it the new start point states
								currDest.xCellToStartPointMap.get(j).get(k).startPoint.orWith(startPoint.startPoint);
								currDest.xCellToStartPointMap.get(j).get(k).prevNodeJVGInds.addAll(startPoint.prevNodeJVGInds);
							} else {
								currDest.xCellToStartPointMap.get(j).put(k, startPoint);
							}
						} else {
							currDest.xCellToStartPointMap.put(j, new HashMap<Integer, StartPointInfo>());
							currDest.xCellToStartPointMap.get(j).put(k, startPoint);
						
						}
						
						envChoice.free();
						log.log(Level.FINEST, "currDest = {0}", currDest);
					}
					succ.free();
					xCell.free();
				}
			}
			
			if (!currDest.xCellToStartPointMap.isEmpty()) {
				destinations.add(currDest);
			}
		}
		
		// Creating the initial marker node and adding it to the nodes member of the class
		JVGNode initNode = new JVGNode();
		initNode.type = JVGNodeType.INIT_NODE;
		initNode.zRankNum = -1;
		initNode.nodeBDD = toCheck;
		initNode.transitions = toCheck.id();
		initNode.violatedSafeties = getViolatedSafetiesRG(initNode.nodeBDD, safeties, aux);
		initNode.isSafeyViolated = isSafetyViolatedRG(initNode.nodeBDD);
		initNode.isOnlySafetyViolated = isOnlySafetyViolatedRG(initNode.nodeBDD);		
		nodes.put(0, initNode);
		
		String destsStr = "returning destinations:\n";
		for (int i = 0; i < destinations.size(); i++) {
			destsStr += "destination[" + i + "] = {" + i + "}\n";
		}
		log.log(Level.FINE, destsStr, destinations.toArray());
		log.fine("END");
		return destinations;
	}
	
	/**
	 * Adding destinations (Ranking graph nodes and specific states in X-cells in Ranking graph nodes) to
	 * the work list
	 * @param workList The work list, containing the previously added destinations
	 * @param destinations The previously computed destinations to be added to the work list
	 */
	private void addDestsToWorkList(LinkedList<RGNodeProcessInfo> workList,
			Vector<RGNodeProcessInfo> destinations) {
		log.fine("START");
		
		for (int i = 0; i < destinations.size(); i++) {
			boolean foundNodeInWorkList = false;
			RGNodeProcessInfo currDest = destinations.get(i);
			Iterator<RGNodeProcessInfo> iter = workList.iterator();
			while (iter.hasNext()) {
				RGNodeProcessInfo elem = iter.next();
				if (elem.currNodeIndRG == currDest.currNodeIndRG) {
					log.finer("node already in work list - RG Node ind = " + currDest.currNodeIndRG);
					log.finer("update its start points");
					updateNodeStartPoints(elem, currDest);
					foundNodeInWorkList = true;
					break;
				}
			}
			
			if (!foundNodeInWorkList) {
				workList.add(currDest);
			}
		}
		
		Collections.sort(workList, Collections.reverseOrder());
		log.fine("END");
	}
	
	/**
	 * Updating the start-points (i.e., specific locations of reachable states in the memX of a Ranking graph
	 * node) of a process node 
	 * @param nodeToUpdate The process node (RGNodeProcessInfo) to update with the start points
	 * @param srcNode The node from which we can reach these start points via valid transitions
	 */
	private void updateNodeStartPoints(RGNodeProcessInfo nodeToUpdate, RGNodeProcessInfo srcNode) {
		log.finer("START");
		ArrayList<RGNodeProcessInfo> inputParams = new ArrayList<RGNodeProcessInfo>();
		inputParams.add(nodeToUpdate);
		inputParams.add(srcNode);
		log.log(Level.FINER, "nodeToUpdate - {0}, srcNode - {1}", inputParams);
		Iterator<Map.Entry<Integer, HashMap<Integer, StartPointInfo>>> iter =
				srcNode.xCellToStartPointMap.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<Integer, HashMap<Integer, StartPointInfo>> elem =
					(Map.Entry<Integer, HashMap<Integer, StartPointInfo>>) iter.next();
			Iterator<Map.Entry<Integer, StartPointInfo>> colItr = elem.getValue().entrySet().iterator();
			while (colItr.hasNext()) {
				Map.Entry<Integer, StartPointInfo> colElem =
						(Map.Entry<Integer, StartPointInfo>) colItr.next();
				if (nodeToUpdate.xCellToStartPointMap.containsKey(elem.getKey())) {
					if (nodeToUpdate.xCellToStartPointMap.get(elem.getKey()).containsKey(colElem.getKey())) {
						// nodeToUpdate already has a start point in this xCell, need to add to it the current start point
						nodeToUpdate.xCellToStartPointMap.get(elem.getKey()).get(colElem.getKey()).startPoint.orWith(
										colElem.getValue().startPoint.id());
						HashSet<Integer> prevNodes = new HashSet<Integer>();
						prevNodes.addAll(nodeToUpdate.xCellToStartPointMap.get(elem.getKey()).get(colElem.getKey()).prevNodeJVGInds);
						prevNodes.addAll(colElem.getValue().prevNodeJVGInds);
						nodeToUpdate.xCellToStartPointMap.get(elem.getKey()).get(colElem.getKey()).prevNodeJVGInds.clear();
						nodeToUpdate.xCellToStartPointMap.get(elem.getKey()).get(colElem.getKey()).prevNodeJVGInds.addAll(
								prevNodes);
					} else {
						// nodeToUpdate already has start point in this x row, but not in this x column - need to add
						// the start point for this x column
						nodeToUpdate.xCellToStartPointMap.get(elem.getKey()).put(
								colElem.getKey(), colElem.getValue());
					}
				} else {
					// nodeToUpdate doesn't have any start points in this x row - need to add a start point for both
					// x row and x column
					nodeToUpdate.xCellToStartPointMap.put(elem.getKey(), new HashMap<Integer, StartPointInfo>());
					nodeToUpdate.xCellToStartPointMap.get(elem.getKey()).put(
							colElem.getKey(), colElem.getValue());
				}
			}
		}
				
		log.finer("startPoints after update:\n");
		Iterator<Map.Entry<Integer, HashMap<Integer, StartPointInfo>>> printItr =
				nodeToUpdate.xCellToStartPointMap.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<Integer, HashMap<Integer, StartPointInfo>> elem =
					(Map.Entry<Integer, HashMap<Integer, StartPointInfo>>) printItr.next();
			Iterator<Map.Entry<Integer, StartPointInfo>> colIter = elem.getValue().entrySet().iterator();
			while (colIter.hasNext()) {
				Map.Entry<Integer, StartPointInfo> colElem = (Map.Entry<Integer, StartPointInfo>) colIter.next();
				log.log(Level.FINER, "startPoint: [" + elem.getKey() + "][" + colElem.getKey() + "] = {0}\n", elem.getValue());
			}
		}
		log.finer("END");
	}
	
	/**
	 * Handling a process node (splitting a Ranking graph node into JVTS nodes).
	 * The flow is:
	 * 1. Compute the states in the RG nodes of lower z-rank than the current RG node
	 * 2. Compute the attractor-from-candidates
	 * 3. Remove the attractor-from-candidates from the current node to handle (also add assignments to
	 * ASSUMPTION_RANK variable in remaining states)
	 * 4. Compute the paths along the memX states which remained after (3) in this node
	 * 5. Compute the JVG cycle-node
	 * 6. [Optional] Compute the JVG cycle-node sub-graph of Assumption Satisfaction
	 * 7. Compute the JVG attractor-to-cycle node
	 * 8. Compute the JVG attractor-to-cycle node
	 * 9. Add the new JVG nodes to the JVG
	 * @param nodeToHandle The process node to handle
	 * @param rankGraph The entire Ranking graph
	 * @param jvgNodeInd The index used to create the indices of the JVG nodes
	 * @return The indices of the JVG nodes created
	 * @throws Exception
	 */
	private Vector<Integer> handleNode(RGNodeProcessInfo nodeToHandle, RankingGraph rankGraph,
			int jvgNodeInd) throws Exception {
		log.log(Level.FINE, "START - nodeToHandle = {0}, jvgNodeInd = " + jvgNodeInd, nodeToHandle);
		
		log.finer("calculating lower z-rank states");
		// 1.
		BDD lowerZRank = calcLowerZRank(nodeToHandle, rankGraph);
		log.finer("calculating attractor-from candidates");
		// 2.
		BDD attrFromCands = calcAttrFromCands(nodeToHandle, lowerZRank);
		log.finer("removing attractor-from candidates from the node X matrix");
		// remove the attractor from cands states from the node x matrix and add ASSUMP RANK variable
		// 3.
		removeAttrFromCands(nodeToHandle, attrFromCands);
		log.finer("calculating X Paths");
		// calculate the XPaths
		// 4.
		Vector<JVGXPath> paths = calcXPaths(nodeToHandle, lowerZRank, attrFromCands);
		log.finer("calculating cycle states");
		// 5.
		BDD cycleStates = calcCycleStates(nodeToHandle, paths, lowerZRank, attrFromCands);
		log.finer("calculating assumption sat nodes");
		// 6.
		AssumptionSatisfactionGraph assumpGraph = new AssumptionSatisfactionGraph();
		if (this.calcAssumpGraph) {
			assumpGraph = calcAssumptionGraph(nodeToHandle, paths,
				cycleStates);
		}
		log.finer("calculating attr to states");
		// 7.
		BDD attractorToStates = calcAttractorToStates(paths, cycleStates);
		log.finer("calculating attr from states");
		// 8.
		BDD attractorFromStates = calcAttractorFromStates(attrFromCands, nodeToHandle,
				cycleStates, attractorToStates, lowerZRank);
		log.finer("updating nodes in JVG");
		// 9.
		Vector<Integer> jvgInds = updateNodesInGraph(nodeToHandle, cycleStates, attractorToStates,
				attractorFromStates, assumpGraph, jvgNodeInd);
		
		log.finer("freeing JVGXPaths");
		freeJVGXPaths(paths);
		
		log.fine("returning jvgInds = " + jvgInds.toString());
		log.fine("END");
		return jvgInds;
	}
	
	/**
	 * Calculating the set of states of all Ranking graph nodes with lower ZRank than the current node
	 * @param nodeToHandle The current node
	 * @param rankGraph The Ranking graph
	 * @return Set of states of all Ranking graph nodes with lower ZRank than current node
	 */
	private BDD calcLowerZRank(RGNodeProcessInfo nodeToHandle, RankingGraph rankGraph) {
		log.log(Level.FINE, "START - nodeToHandle = {0}", nodeToHandle);
		
		BDD lower = Env.FALSE();
		Vector<RGEdge> dests = rankGraph.edges.get(nodeToHandle.currNodeIndRG);
		for (int i = 0; i < dests.size(); i++) {
			// ensure the dest z-rank is lower than current node z-rank
			if (rankGraph.nodes.get(dests.get(i).destInd).zRankNum >= nodeToHandle.zRank) {
				log.severe("ERROR - destination z-rank is larger than source in ranking graph");
			}
			log.log(Level.FINEST, "adding lower z-rank states: {0}",
					new BddStringWrapper(rankGraph.nodes.get(dests.get(i).destInd).nodeBDD));
			lower.orWith(rankGraph.nodes.get(dests.get(i).destInd).nodeBDD.id());
			log.log(Level.FINEST, "lower after adding the states: {0}", new BddStringWrapper(lower));
		}
		
		log.log(Level.FINE, "lower = {0}", new BddStringWrapper(lower));
		log.fine("END");
		return lower;
	}
	
	/**
	 * Calculating the attractor-from-cycle candidate states for a specific processing node.
	 * This is an attractor computation of all states where environment can force to a lower Z-Rank state
	 * or to a dead-end for the system.
	 * @param nodeToHandle The current processing node in which to calculate the states
	 * @param lowerZRank All states in lower Z-Ranks (than the current node Z-Rank)
	 * @return The set of states which are attractor-from-cycle candidates
	 */
	private BDD calcAttrFromCands(RGNodeProcessInfo nodeToHandle, BDD lowerZRank) {
		ArrayList<Object> inputParams = new ArrayList<Object>();
		inputParams.add(nodeToHandle);
		inputParams.add(new BddStringWrapper(lowerZRank));
		log.log(Level.FINE, "START - nodeToHandle = {0}, lowerZRank = {1}", inputParams.toArray());
		
		BDD attrFromCands = Env.FALSE();		
		BDD srcStates = nodeToHandle.node.nodeBDD.id();//.and(this.safetiesBDD.not());
		
		// we go in reverse and collect all states from which env can force sys towards safety violation or
		// lower z-rank. For each such transition, we also save one of the env choices that forces sys. 
		// We don't simply do fixed point on control states of env because of the following scenario:
		// there might be 2 states, A and B, both the environment can force to a safety violation / lower Z rank. But,
		// also in both the environment can force (via a different choice) to the other state (i.e. A can force to B and vice versa).
		// Then when we get to the part of taking the reachable states and environment choices, we might take for A the env choice that
		// forces to B, and for B the env choice which forces to A. There can be several ways to prevent this, we chose to collect
		// the env choices when computing the candidates, and ensuring here that we will take "correct" env choices by
		// only moving backwards to states that were not already collected (the "srcStates.andWith(attrFromCands.not())" line).
		BDD predStates = performReverseEnvForceStep(nodeToHandle, srcStates, lowerZRank/*.or(this.safetiesBDD)*/);
		attrFromCands = predStates.id();
		
		// ensure the same state will not be repeated twice
		srcStates.andWith(attrFromCands.not());
		while (!srcStates.isZero()) {
			log.log(Level.FINE, "another iteration of reverse env force step");
			BDD tmpTarget = attrFromCands.or(lowerZRank);
			predStates = performReverseEnvForceStep(nodeToHandle, srcStates, tmpTarget/*.or(this.safetiesBDD)*/);
			if (predStates.isZero()) {
				log.log(Level.FINER, "no more predecessors - finished collecting attrFromCands");
				break;
			}
			
			attrFromCands.orWith(predStates.id());
			srcStates.andWith(attrFromCands.not());
			tmpTarget.free();
		}

		log.log(Level.FINE, "END - attrFromCands = {0}", new BddStringWrapper(attrFromCands));
		return attrFromCands;
	}
	
	/**
	 * Removing the attractor-from-cycle candidates from the RG node being processed (in order to be able
	 * to compute the paths on the remaining states)
	 * @param nodeToHandle The RG node being processed. This node is changed by this function
	 * @param attrFromCands The states to remove from the node being processed
	 */
	private void removeAttrFromCands(RGNodeProcessInfo nodeToHandle, BDD attrFromCands) {
		log.log(Level.FINE, "START - attrFromCands = {0}", new BddStringWrapper(attrFromCands));
		
		for (int i = 0; i < nodeToHandle.nodeXMatrix.size(); i++) {
			for (int j = 0; j < nodeToHandle.nodeXMatrix.get(i).size(); j++) {
				// add assumption rank variable
				nodeToHandle.nodeXMatrix.get(i).set(j,
						nodeToHandle.nodeXMatrix.get(i).get(j).andWith(attrFromCands.not())).andWith(
								Env.getBDDValue(ASSUMPTION_RANK, "" + i).id());
			}
		}
		
		log.fine("END");
	}
		
	/**
	 * Calculating the paths along the memX of the RG node being processed (after removing from it the 
	 * candidate attractor-from-cycle states).
	 * The flow is:
	 * 1. Calculate start paths - a path per start point in the nodeToHandle, containing only this start point.
	 * Add these paths to a work list
	 * 2. While the work list is not empty, remove a path from it
	 * 3. If the path reached a cycle (i.e., the last state added to it already existed in the path) - 
	 * we finished with this path, no need to continue traversing it - add it to the list of paths
	 * 4. Else - get the indices for the next memX cells to check for successors of the current path
	 * (if the current memX row ended - move to the next row)
	 * 5. Extend the current path (possibly creating multiple new paths from it) a single step
	 * 6. If the path could not be extended - we're in an invalid state, throw exception (all paths should
	 * end in a cycle, see the paper for explanation)
	 * 7. Add all the new paths to the work list, return to (2).
	 * @param nodeToHandle The RG node being processed
	 * @param lowerZRank The states in the RG nodes of lower Z-ranks
	 * @param attrFromCands The states which are candidates for the attractor-from-cycle state in the current
	 * RG node
	 * @return A vector of computed paths, each ending in a cycle
	 * @throws Exception
	 */
	private Vector<JVGXPath> calcXPaths(RGNodeProcessInfo nodeToHandle, BDD lowerZRank,
			BDD attrFromCands) throws Exception {
		log.fine("START");
		Vector<JVGXPath> paths = new Vector<JVGXPath>();
		// 1.
		LinkedList<JVGXPath> pathWorkList = calcStartPaths(nodeToHandle, attrFromCands);
		while (!pathWorkList.isEmpty()) {
			log.fine("workList size before iteration: " + pathWorkList.size());
			
			String wlBefore = "workList before iteration:\n";
			for (int i = 0; i < pathWorkList.size(); i++) {
				wlBefore += "workList[" + i + "] = {" + i + "}\n";
			}
			log.log(Level.FINER, wlBefore, pathWorkList.toArray());
			// 2.
			JVGXPath currPath = pathWorkList.pop();
			log.fine("current path size: " + currPath.path.size());
			log.log(Level.FINER, "current path: {0}", currPath);
			// 3.
			if (currPath.isCycle) {
				paths.add(currPath);
				log.fine("path is cycle - continuing to next path");
				continue;
			}
	
			// 4.
			int nextRowInd = currPath.path.get(currPath.path.size()-1).xRow;
			int nextColInd = currPath.path.get(currPath.path.size()-1).xCol;
			if (currPath.rowEnded()) {
				nextRowInd = (nextRowInd + 1) % env.justiceNum();
				nextColInd = nodeToHandle.nodeXMatrix.get(nextRowInd).size();
			}
			log.finer("nextRowInd = " + nextRowInd + ", nextColInd = " + nextColInd);
			// 5.
			Vector<JVGXPath> newPaths = calcNewXPathsJVG(currPath, nodeToHandle, attrFromCands, 
					lowerZRank, nextRowInd, nextColInd);
			// 6.
			if (newPaths.isEmpty()) {
				// This used to be possible - previously we allowed for start points to contain 0 states (because of
				// the  filtering of the X matrix, which - if the start point was entirely in attrFromCands - would result in 
				// a start point with an empty BDD). This is no longer allowed (we simply do not create such start points). 
				log.warning("path isn't a cycle and has no successors - it has ended");
				throw new RuntimeException("path isn't a cycle and has no successors - shouldn't happen");
			}
			
			String newPathsStr = "new Paths:\n";
			for (int k = 0; k < newPaths.size(); k++) {
				newPathsStr += "path[" + k + "] = {" + k + "}\n";
			}
			log.log(Level.FINER, newPathsStr, newPaths.toArray());
			log.fine("number of new paths = " + newPaths.size());
			// 7.
			pathWorkList.addAll(newPaths);
			
			String wlAfter = "workList after iteration:\n";
			for (int i = 0; i < pathWorkList.size(); i++) {
				wlAfter += "workList[" + i + "] = {" + i + "}\n";
			}
			log.log(Level.FINE, wlAfter, pathWorkList.toArray());
		}
		
		String pathStr = "END - paths:\n";
		for (int i = 0; i < paths.size(); i ++) {
			pathStr += "path[" + i + "] = {" + i + "}\n";
		}
		log.log(Level.FINE, pathStr, paths.toArray());
		return paths;
	}
	
	/**
	 * Calculating the Start Paths - a path per each Start Point of the current RG node being handled,
	 * containing only that Start Point
	 * @param nodeToHandle The current RG node being handled
	 * @param attrFromCands The candidate states for attractor-from-cycle
	 * @return A list of paths
	 */
	private LinkedList<JVGXPath> calcStartPaths(RGNodeProcessInfo nodeToHandle, BDD attrFromCands) {
		log.log(Level.FINE, "START - nodeToHandle = {0}", nodeToHandle);
		
		LinkedList<JVGXPath> paths = new LinkedList<JVGXPath>();
		for (Map.Entry<Integer, HashMap<Integer, StartPointInfo>> entry : nodeToHandle.xCellToStartPointMap.entrySet()) {
			for (Map.Entry<Integer,  StartPointInfo> colElem : entry.getValue().entrySet()) {
				BDD startPointInFilteredX = nodeToHandle.nodeXMatrix.get(entry.getKey()).get(colElem.getKey()).and(
						colElem.getValue().startPoint);
				if (startPointInFilteredX.isZero()) {
					// this means that all the states represented by this start point are in the attrFromCands.
					startPointInFilteredX.free();
					continue;
				}
				JVGXPath startPath = new JVGXPath();
				JVGXPoint startPoint = new JVGXPoint();
				startPoint.xRow = entry.getKey();
				startPoint.xCol = colElem.getKey();
				startPoint.oldBDD = nodeToHandle.nodeXMatrix.get(entry.getKey()).get(colElem.getKey()).id();
				startPoint.newBDD = startPointInFilteredX;
				if (startPoint.newBDD.isZero()) {
					continue;
				}
				startPath.path.add(startPoint);
				paths.add(startPath);
			}
		}
		
		String pathStr = "END - paths:\n";
		for (int i = 0; i < paths.size(); i ++) {
			pathStr += "path[" + i + "] = {" + i + "}\n";
		}
		log.log(Level.FINE, pathStr, paths);
		return paths;
	}

	/**
	 * Performing a single step from a given path (i.e., finding all valid successors of the last point
	 * in a given path). This can result in multiple new paths, each the result of successors in a differnet
	 * memX cell in the current RG node being processed.
	 * The flow:
	 * 1. Get the last point in the given path
	 * 2. For each relevant cell in the memX matrix of the current RG node, check if there are successors
	 * to the last point (using "performInnerNodeStep" method)
	 * 3. If there are successors, create a new path which is a result of the given path and a new point,
	 * the successors in the relevant memX cell
	 * 4. After going over all relevant memX cells, all states comprising the last point should have an
	 * "envChoice" selected for them (i.e., successors were located for them).
	 * @param path The path for which to search for successors
	 * @param nodeToHandle The current RG node being processed
	 * @param attrFromCands The states which are the attractor-from-cycle candidates
	 * @param lowerZRank The states which are in RG nodes with lower Z-Rank
	 * @param nextRowInd The memX in RG node row index to use when searching for successors to the path
	 * @param nextColInd The memX in RG node column index to use when search for successors to the path
	 * (we will search all columns up to nextcolInd) 
	 * @return A vector of new paths
	 * @throws Exception
	 */
	private Vector<JVGXPath> calcNewXPathsJVG(JVGXPath path, RGNodeProcessInfo nodeToHandle,
			BDD attrFromCands, BDD lowerZRank, int nextRowInd, int nextColInd) throws Exception {
		log.fine("START");
		Vector<JVGXPath> paths = new Vector<JVGXPath>();
		try {
			// 1.
			BDD lastPointBDD = path.path.get(path.path.size()-1).newBDD;
			log.log(Level.FINER, "lastPointBDD = {0}", new BddStringWrapper(lastPointBDD));
			BDD rowBDD = Env.FALSE();
			for (int i = 0; i < nextColInd; i++) {
				BDD currCellBDD = nodeToHandle.nodeXMatrix.get(nextRowInd).get(i);
				rowBDD.orWith(currCellBDD.id());
			}
			log.log(Level.FINER, "row BDD = {0}", new BddStringWrapper(rowBDD));
			log.finer("going over columns in row " + nextRowInd + " to get the new paths");
			rowBDD.orWith(attrFromCands.or(lowerZRank));
			for (int i = 0; i < nextColInd; i++) {
				log.finer("current col = " + i);
				BDD currCellBDD = nodeToHandle.nodeXMatrix.get(nextRowInd).get(i).id();
				log.log(Level.FINER, "currCellBDD = {0}", new BddStringWrapper(currCellBDD));
				// 2.
				BDD succ = performInnerNodeStep(nodeToHandle, lastPointBDD, currCellBDD,  rowBDD);			
				if (!succ.isZero()) {
					// 3.
					JVGXPoint point = new JVGXPoint();
					point.oldBDD = currCellBDD;
					point.newBDD = succ;
					point.xRow = nextRowInd;
					point.xCol = i;
					JVGXPath newPath = new JVGXPath();
					newPath = path.clone();
					newPath.path.add(point);
					// if cycle, updates newPath isCycle and cycleStartInd fields
					// ensure that:
					// 1. the last point BDD is identical to a BDD further down the path
					// 2. we visited all 0-column cells in all the xRows
					// (otherwise - it can't be a cycle, since we didn't satisfy some assumption)
					cyclePathCheck(newPath);
					log.log(Level.FINER, "found successor: {0}", new BddStringWrapper(succ));
					log.log(Level.FINER, "adding new path: {0}", newPath);
				
					paths.add(newPath);
				}
			}
			// SANITY: When we get here - all states contained in the "lastPointBDD" should already have an env choice for them
			// 4.
			if (!lastPointBDD.and(nodeToHandle.innerNodeTransitions.exist(env.modulePrimeVars())).equals(lastPointBDD)) {
				throw new RuntimeException("the current last point in a path doesn't have env choices for all of its states!");
			}
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}
		
		String pathStr = "END - returning new paths:\n";
		for (int i = 0; i < paths.size(); i ++) {
			pathStr += "path[" + i + "] = {" + i + "}\n";
		}
		log.log(Level.FINE, pathStr, paths.toArray());
		return paths;		
	}

	/**
	 * This function performs a reverse step where the environment can force the system to the given states.
	 * It is used during calculation of the attractor-from-cycle candidates, and it also stores the 
	 * environment choices taken in this step (stored in the processing node's innderNodeTrans field).
	 * The flow:
	 * 1. Compute the transition which the environment can force the system to some of the target states
	 * from the source states
	 * 2. Remove the source states for which environment choices were already chosen from the computation
	 * 3. While we still have source states for which environment choices were not chosen, choose environment 
	 * assignments for them
	 * 4. Add the new environment choices to the "innerNodeTransitions".
	 * 5. Repeat from (3) 
	 * @param nodeToHandle The processing RG node
	 * @param srcBDD The set of states which are the source of the step
	 * @param targetBDD The set of states which are the target of the step
	 * @return The successors, a subset of states in the targetBDD
	 */
	private BDD performReverseEnvForceStep(RGNodeProcessInfo nodeToHandle, BDD srcBDD, BDD targetBDD) {
		ArrayList<Object> inputParams = new ArrayList<Object>();
		inputParams.add(nodeToHandle);
		inputParams.add(new BddStringWrapper(srcBDD));
		inputParams.add(new BddStringWrapper(targetBDD));
		log.log(Level.FINE, "START - nodeToHandle = {0}, srcBDD = {1}, targetBDD = {2}", inputParams.toArray()); 

		// We know that the states in srcBDD will not already have an env choice for them, from the way we calculate
		// (this is attrFromCands calculation, so we don't have cycles)
	
		// need to get a specific env choice for each of the src states that have such a choice
		BDD tempTrans = env.trans().id().andWith(
				(sys.trans().not().orWith(sys.trans().id().andWith(Env.prime(targetBDD)))).forAll(sys.modulePrimeVars()));
		BDD predStates = Env.FALSE();
		// 1.
		BDD step = srcBDD.and(tempTrans);
		// 2.
		BDD remainingSrcStates = step.exist(env.modulePrimeVars());
		while (!remainingSrcStates.isZero()) {
			// 3.
			BDD envChoice = CoreUtil.satOne(step, env.modulePrimeVars());
			log.log(Level.FINER, "the env choice taken: {0}", new BddStringWrapper(envChoice));
			BDD tmp = step.and(envChoice);
			BDD srcStatesForTakenEnvChoice = (tmp.exist(
					env.modulePrimeVars().union(sys.modulePrimeVars()))).andWith(predStates.not());
			log.log(Level.FINEST, "srcStatesForTakenEnvChoice = {0}", new BddStringWrapper(srcStatesForTakenEnvChoice));
			// 4.
			nodeToHandle.innerNodeTransitions.orWith(srcStatesForTakenEnvChoice.and(envChoice));
			remainingSrcStates.andWith(srcStatesForTakenEnvChoice.not());
			predStates.orWith(srcStatesForTakenEnvChoice);
			envChoice.free();
			step.free();
			tmp.free();
			step = remainingSrcStates.and(tempTrans);
		}
		tempTrans.free();
		
		log.log(Level.FINE, "END - predStates = {0}", new BddStringWrapper(predStates));
		// returning the predecessors
		return predStates;		
	}

	/**
	 * This function performs a step in the context of the paths computation. It finds environment choices 
	 * for all states in the srcBDD, for which every system choice leads either to a state in the targetBDD
	 * or to a state in the otherSuccs, and at least one system choice leads to a state in the targetBDD.
	 * @param nodeToHandle The current RG node being processed
	 * @param srcBDD The source set of states
	 * @param targetBDD The target set of states
	 * @param otherSuccs Other possible successor states
	 * @return The successor states in the targetBDD
	 */
	private BDD performInnerNodeStep(RGNodeProcessInfo nodeToHandle, BDD srcBDD, BDD targetBDD,
			BDD otherSuccs) {
		ArrayList<Object> inputParams = new ArrayList<Object>();
		inputParams.add(nodeToHandle);
		inputParams.add(new BddStringWrapper(srcBDD));
		inputParams.add(new BddStringWrapper(targetBDD));
		inputParams.add(new BddStringWrapper(otherSuccs));
		log.log(Level.FINE, "START - nodeToHandle = {0}, srcBDD = {1}, targetBDD = {2}, otherSuccs = {3}",
				inputParams.toArray()); 

		// For a given set of src states:
		// 1. Check if env choice already selected for some of them - if so:
		//    1.1 calculate for the src states for which env choice was already selected the 
		//        relevant successors, per selected env choice, in target states.
		//    1.2 remove the selected src states from the given set of src states.
		// 2. On the remainder of src states - perform a regular step, until we either
		//    - run out of src states (i.e. there's an env choice for every src state leading to target state
		//    - the step returns 0 states - we found all src+target states for which there's an env choice.
		//    - each time we find new env choice, we add it and the relevant src states to the transitions bdd,
		//      and we accumulate the relevant successors
		// 3.The disjunction of the accumulated successors, along with the successors in 1.1, are the result of this
		//   step.
		BDD succsForWhichEnvAlreadyChose = Env.FALSE();
		BDD srcStatesForWhichEnvAlreadyChosen = srcBDD.id().andWith(nodeToHandle.innerNodeTransitions.exist(Env.globalPrimeVars()));
		if (!srcStatesForWhichEnvAlreadyChosen.isZero()) {
			BDD stepWithExistingEnvChoice = srcStatesForWhichEnvAlreadyChosen.id().andWith(nodeToHandle.innerNodeTransitions.id().andWith(
					env.trans().id().andWith(sys.trans().id()))).andWith(
					Env.prime(targetBDD.id()));
			log.log(Level.FINEST, "stepWithExistingEnvChoice = {0}", new BddStringWrapper(stepWithExistingEnvChoice));
			// find successor states in target for states in src for which we already have a specific env choice
			succsForWhichEnvAlreadyChose = Env.unprime(stepWithExistingEnvChoice.exist(env.moduleUnprimeVars().union(sys.moduleUnprimeVars())));
			log.log(Level.FINEST, "succsForWhichEnvAlreadyChose = {0}", new BddStringWrapper(succsForWhichEnvAlreadyChose));
			stepWithExistingEnvChoice.free();
		}
		
		// src states for which we didn't already have an env choice - will need to perform a step for them,
		// and get a specific env choice
		BDD remainingSrcStates = srcBDD.id().andWith(srcStatesForWhichEnvAlreadyChosen.not());
		while (!remainingSrcStates.isZero()) {
			log.log(Level.FINEST, "iteration start - remainingSrcStates = {0}", new BddStringWrapper(remainingSrcStates));

			BDD regularStep = innerNodeTransitionSysChoiceJVG(remainingSrcStates, targetBDD, otherSuccs);
			log.log(Level.FINEST, "regularStep = {0}", new BddStringWrapper(regularStep));
			if (regularStep.isZero()) {
				// This can happen if the current srcBDD contains states which do not have successors in the current target BDD.
				//e.g. If the current srcBDD is cell #4 in a row in the X-matrix, and target BDD is cell #0 - it might be that there
				// is a state in srcBDD that did not yet "choose" an env choice, and every possible valid env choice (valid as we
				// defined it - i.e. that every sys choice for it leads to "othersBDD" and there is a sys choice leading to "targetBDD")
				// does not exist for this "targetBDD" when starting from this source state.
				// Such a source state will have some environment choice that is valid for some other "targetBDD", and that is when such
				// a choice will be discovered.
				log.log(Level.FINER, "no more successors for the remaining states. remainingSrcStates = {0}",
						new BddStringWrapper(remainingSrcStates));
				regularStep.free();
				break;
			}
			
			BDD envChoice = CoreUtil.satOne(regularStep, env.modulePrimeVars());
			log.log(Level.FINER, "the env choice taken: {0}", new BddStringWrapper(envChoice));				
			BDD srcStatesForTakenEnvChoice = (regularStep.id().andWith(envChoice.id())).exist(
					env.modulePrimeVars().union(sys.modulePrimeVars()));
			log.log(Level.FINEST, "srcStatesForTakenEnvChoice = {0}", new BddStringWrapper(srcStatesForTakenEnvChoice));
			BDD succsForThisEnvChoice = (Env.unprime((regularStep.id().andWith(envChoice.id()).andWith(env.trans().id().andWith(
					sys.trans().id()))).exist(
							env.moduleUnprimeVars().union(sys.moduleUnprimeVars())))).andWith(targetBDD.id());
			log.log(Level.FINEST, "succsForThisEnvChoice = {0}", new BddStringWrapper(succsForThisEnvChoice));
			nodeToHandle.innerNodeTransitions.orWith(
					srcStatesForTakenEnvChoice.and(envChoice));
			log.log(Level.FINEST, "innerNodeTransitions = {0}", new BddStringWrapper(nodeToHandle.innerNodeTransitions));

			succsForWhichEnvAlreadyChose.orWith(succsForThisEnvChoice);
			log.log(Level.FINEST, "succsForWhichEnvAlreadyChose = {0}", new BddStringWrapper(succsForWhichEnvAlreadyChose));
			remainingSrcStates.andWith(srcStatesForTakenEnvChoice.not());
			log.log(Level.FINEST, "remainingSrcStates = {0}", new BddStringWrapper(remainingSrcStates));
			envChoice.free();
			srcStatesForTakenEnvChoice.free();
			regularStep.free();
		}
		remainingSrcStates.free();
		log.log(Level.FINE, "END - succsForWhichEnvAlreadyChose = {0}", new BddStringWrapper(succsForWhichEnvAlreadyChose));
		// returning the successors
		return succsForWhichEnvAlreadyChose;
	}

	/**
	 * Building the BDD representing the formula of having environment choices which can force to system
	 * from prevNode to otherSuccs or to currNode and for which at least one system choice leads to currNode.  
	 * @param prevNode The source states
	 * @param currNode The target states
	 * @param otherSuccs Other possible successor states
	 * @return A BDD
	 */
	private BDD innerNodeTransitionSysChoiceJVG(BDD prevNode, BDD currNode, BDD otherSuccs) {
		// 1. Exists env.primed s.t. prevNode AND env.trans():
		// 1.1 For every sys.primed, (sys.trans.not OR (sys.trans AND (currNode' OR prevNode' OR 
		//     otherSuccs')))
		// 1.2 Exists sys.primed s.t. sys.trans AND currNode'

		log.fine("Performing sys choice regular step");
		// 1.1
		// for all sys choices, have to at least advance towards violating a safety or satisfying an env assumption
		// while violating a sys guarantee. That is why we can't allow to stay in the prevNode
		BDD forAllSysChoices = (sys.trans().not().orWith(sys.trans().id().andWith(Env.prime(currNode.id()).orWith(
				Env.prime(otherSuccs.id()))))).forAll(sys.modulePrimeVars());
		
		log.log(Level.FINEST, "forAllSysChoices = {0}", new BddStringWrapper(forAllSysChoices));
		log.log(Level.FINEST, "forAllSysChoices with prevNode = {0}",
				new BddStringWrapper(prevNode.id().andWith(env.trans().id().andWith(forAllSysChoices.id()))));
		// 1.2
		BDD existsSysChoice = sys.trans().id().andWith(Env.prime(currNode.id()));
		log.log(Level.FINEST, "existsSysChoice = {0}", new BddStringWrapper(existsSysChoice));
		log.log(Level.FINEST, "existsSysChoice with prevNode = {0}", 
				new BddStringWrapper(prevNode.id().andWith(env.trans().id().andWith(existsSysChoice.id()))));
		// 1
		BDD sysChoicesCombined = prevNode.id().andWith(env.trans().id().andWith(
				forAllSysChoices.and(existsSysChoice)));
		forAllSysChoices.free();
		existsSysChoice.free();
		
		return sysChoicesCombined;
	}
	
	/**
	 * Checking if a given path is a cycle (i.e., if last point already appeared in the path)
	 * @param path The path to check
	 * @throws Exception
	 */
	private void cyclePathCheck(JVGXPath path) throws Exception {
		log.fine("START");
		if (path.path.size() < 2) {
			// can't be a cycle if path of size 1
			path.isCycle = false;
			log.fine("paths size < 2 - can't be a cycle");
			return;
		}
		log.log(Level.FINER, "path = {0}", path);
		
		JVGXPoint lastPoint = path.path.get(path.path.size()-1);
		int assumpInd = lastPoint.xRow;
		log.log(Level.FINER, "lastPoint in path = {0}", lastPoint);
		int numAssumptionsSatisfied = 0;
		for (int i = path.path.size()-2; i >= 0; i--) {
			JVGXPoint currPoint = path.path.get(i);
			log.log(Level.FINER, "currPoint = {0}", currPoint);
			if (currPoint.xRow != assumpInd) {
				// row changed - another assumption satisfied
				if (numAssumptionsSatisfied < env.justiceNum()) {
					numAssumptionsSatisfied++;
				}
				assumpInd = currPoint.xRow;
			}
			
			if (currPoint.xRow == lastPoint.xRow && currPoint.xCol == lastPoint.xCol &&
				currPoint.newBDD.equals(lastPoint.newBDD)) {
				// same point exists in the path, with same calculated BDD - reached a cycle
				if (numAssumptionsSatisfied != env.justiceNum() &&
					env.justiceNum() > 1) {
					// cycle did not visit all of the assumptions - this is an invalid cycle
					// (shouldn't happen because we only check for equality of BDDs between the same cells -
					// so if the last point BDD is equal to some BDD in the path but in a different cell - we
					// will not consider it a cycle and will continue running. And since we traverse the matrix
					// by going on all the rows in a loop, if we get to the same cell again, it means we already
					// went over all the assumptions).
					log.severe("Detected a cycle which did not satisfy all of the assumptions!");
					log.severe("path = " + path.toString());
					assert(numAssumptionsSatisfied == env.justiceNum()) :
							"Detected a cycle which did not satisfy all of the assumptions!";
				}
				log.fine("currPoint is the cycle start point");
				path.isCycle = true;
				path.cycleStartInd = i;
				return;
			}

		}
		path.isCycle = false;
		log.fine("END");
		return;
	}
	
	/**
	 * Calculating the JVG cycle node states. We go over all paths, and in each we collect all states
	 * which are a part of the cycle in the path.
	 * @param nodeToHandle The current RG node being processed
	 * @param paths The paths computed for this RG node
	 * @param lowerZRank States in RG nodes of lower Z-Rank
	 * @param attrFromCands States which are candidates to be in the attractor-from-cycle JVG node
	 * @return The set of states which are in the cycle node
	 */
	private BDD calcCycleStates(RGNodeProcessInfo nodeToHandle, Vector<JVGXPath> paths, BDD lowerZRank,
			BDD attrFromCands) {
		ArrayList<Object> inputParams = new ArrayList<Object>();
		inputParams.add(nodeToHandle);
		inputParams.add(new BddStringWrapper(lowerZRank));
		inputParams.add(new BddStringWrapper(attrFromCands));
		log.log(Level.INFO, "START - nodeToHandle = {0}, lowerZRank = {1}, attrFromCands = {2}",
				inputParams.toArray());
		log.log(Level.INFO, "paths = {0}", paths);
		
		BDD cycleStates = Env.FALSE();
		// states in all paths which are cycle paths, including the parts of the paths which are not part of the 
		// cycle (but since they're part of the path, they lead to cycle)
		BDD allPathStates = Env.FALSE();
		log.finer("going over all paths");
		for (int i = 0; i < paths.size(); i++) {
			JVGXPath currPath = paths.get(i);
			log.log(Level.FINER, "currPath = {0}", currPath);
			for (int j = 0; j < currPath.path.size(); j++) {
				if (currPath.isCycle & j >= currPath.cycleStartInd) {
					// this is a cycle set
					cycleStates.orWith(currPath.path.get(j).newBDD.id());
				}
				if (currPath.isCycle) {
					allPathStates.orWith(currPath.path.get(j).newBDD.id());
				}
			}
		}
		
		// find in-between cycle states:
		// using the inner-node-transition, which for every state tells us the relevant env choice for
		// it, we can perform fixed-point iteration on the cycle states, expanding it to allPathStates.
		// This way, we will find path states which are not a part of the cycle cell, but are leading
		// to cycle and can be reached from a cycle
		log.log(Level.FINER, "cycleStates before in-between calculation = {0}", new BddStringWrapper(cycleStates));
		log.log(Level.FINER, "allPathStates = {0}", new BddStringWrapper(allPathStates));
		
		FixPoint iter;
		for (iter = new FixPoint(true); iter.advance(cycleStates.id());) {
			log.log(Level.FINEST, "iter start - cycleStates = {0}", new BddStringWrapper(cycleStates));
			cycleStates.orWith(Env.unprime(
					(cycleStates.id().andWith(nodeToHandle.innerNodeTransitions.id().andWith(
							env.trans().id().andWith(sys.trans().id()))).andWith(Env.prime(allPathStates.id()))).
					exist(env.moduleUnprimeVars().union(sys.moduleUnprimeVars()))));
			log.log(Level.FINEST, "iter end - cycleStates = {0}", new BddStringWrapper(cycleStates));
		}
		
		log.log(Level.INFO, "END - cycleStates = {0}", new BddStringWrapper(cycleStates));
		return cycleStates;
	}
	
	/**
	 * Computing the Assumption Satisfaction subgraph in a cycle node
	 * @param nodeToHandle
	 * @param paths
	 * @param cycleStates
	 * @return
	 */
	private AssumptionSatisfactionGraph calcAssumptionGraph(RGNodeProcessInfo nodeToHandle,
			Vector<JVGXPath> paths, BDD cycleStates) {
		log.info("START");
		
		AssumptionSatisfactionGraph assumpGraph = new AssumptionSatisfactionGraph();
		// calculating the nodes
		BDD tempCycleStates = cycleStates.id();
		log.finer("going over all paths");
		boolean noMoreCycleStates = false;
		for (int i = 0; i < paths.size(); i++) {
			JVGXPath currPath = paths.get(i);
			log.log(Level.FINER, "currPath = {0}", currPath);
			// go over all points in the path (in order to get also the "in-between" cycle states)
			for (int j = 0; j < currPath.path.size(); j++) {
				log.finer("checking point " + j + " in path");
				JVGXPoint currPoint = currPath.path.get(j);
				log.log(Level.FINER, "currPoint = {0}", currPoint);
				BDD tmp = tempCycleStates.and(currPoint.newBDD);
				if (!tmp.isZero()) {
					int assumpInd = currPoint.xRow;
					if (!assumpGraph.nodes.containsKey(assumpInd)) {
						AssumptionSatNode assumpNode = new AssumptionSatNode();
						assumpNode.assumptionInd = assumpInd;
						assumpNode.nodeBDD = Env.FALSE();
						
				 		for (int k = 0; k < envBehaviorInfo.size(); k++) {
							if (envBehaviorInfo.get(k).isJustice()) {
								if (env.justiceAt(assumpInd).equals(
										envBehaviorInfo.get(k).justice)) {
									assumpNode.satAssump = envBehaviorInfo.get(k);
								}
							}
						}
				 		if (assumpNode.satAssump == null) {
				 			assumpNode.satAssump = new BehaviorInfo();
				 			assumpNode.satAssump.traceId = -1;
				 			assumpNode.satAssump.justice = Env.TRUE();
				 		}
						assumpGraph.nodes.put(assumpInd, assumpNode);
					}
					
					log.log(Level.FINEST, "tempCycleState = {0}",
							new BddStringWrapper(tempCycleStates));
					log.log(Level.FINEST, "currPoint.newBDD = {0}",
							new BddStringWrapper(currPoint.newBDD));
					log.log(Level.FINEST, "conjunct of above = {0}",
							new BddStringWrapper(tempCycleStates.and(currPoint.newBDD)));
					log.log(Level.FINEST, "assump nodeBDD = {0}",
							new BddStringWrapper(assumpGraph.nodes.get(assumpInd).nodeBDD));

					assumpGraph.nodes.get(assumpInd).nodeBDD.orWith(tempCycleStates.and(currPoint.newBDD));
					
					// removing the states from the tempCycleStates
					tempCycleStates.andWith(currPoint.newBDD.not());
					if (tempCycleStates.isZero()) {
						log.finer("no more cycle states to check");
						noMoreCycleStates = true;
						break;
					}
				}
				tmp.free();
				if (noMoreCycleStates) {
					break;
				}
			}
			
			if (noMoreCycleStates) {
				break;
			}	
		}
		
		if (!tempCycleStates.isZero()) {
			log.log(Level.SEVERE, "finished going over all path, but there are still cycle states"
					+ " remaining: {0}", new BddStringWrapper(tempCycleStates));
		}
		tempCycleStates.free();
		
		// calculating the edges
		int numAssumptions = nodeToHandle.nodeXMatrix.size();
		Iterator<Map.Entry<Integer, AssumptionSatNode>> nodeIter =
				assumpGraph.nodes.entrySet().iterator();
		while (nodeIter.hasNext()) {
			Map.Entry<Integer, AssumptionSatNode> elem =
					(Map.Entry<Integer, JusticeViolationGraph.AssumptionSatNode>) nodeIter.next();
		
			Iterator<Map.Entry<Integer, AssumptionSatNode>> innerIter =
					assumpGraph.nodes.entrySet().iterator();
			while (innerIter.hasNext()) {
				Map.Entry<Integer, AssumptionSatNode> innerElem =
						(Map.Entry<Integer, JusticeViolationGraph.AssumptionSatNode>) innerIter.next();
				
				if (elem.getKey() == innerElem.getKey()) {
					log.finer("no self edges");
					continue;
				}
				
				BDD succ = Env.unprime((elem.getValue().nodeBDD.id().andWith(
						nodeToHandle.innerNodeTransitions.id().andWith(
								Env.prime(innerElem.getValue().nodeBDD.id())))).exist(
										env.moduleUnprimeVars().union(
												sys.moduleUnprimeVars())));
				
				if (!succ.isZero()) {
					if (innerElem.getKey() != (elem.getKey() + 1) % numAssumptions) {
						log.severe("ERROR - edge exists between non-consecutive nodes"
								+ " in assumption sat graph - from " + elem.getKey() +
								" to " + innerElem.getKey());
					} else {
						// add edge
						log.finer("adding edge from " + elem.getKey() + " to " +
								innerElem.getKey());
						Vector<JVGEdge> temp = new Vector<JVGEdge>();
						JVGEdge edge = new JVGEdge();
						edge.destInd = innerElem.getKey();
						temp.add(edge);
						assumpGraph.edges.put(elem.getKey(), temp);
					}
				}
			}
		}
		
		calcNodeInvariants(assumpGraph.nodes);
		calcEdgeInvariants(assumpGraph.edges, assumpGraph.nodes);
		
		log.log(Level.FINE, "assumpGraph = {0}", assumpGraph);
		log.fine("END");
		return assumpGraph;
	}

	/**
	 * Calculating the attractor to cycle JVG node
	 * @param paths
	 * @param cycleStates
	 * @return
	 */
	private BDD calcAttractorToStates(Vector<JVGXPath> paths, BDD cycleStates) {
		log.info("START");
		BDD attractorToStates = Env.FALSE();
		// Go over all paths
		log.finer("going over all paths");
		for (int i = 0; i < paths.size(); i++) {
			JVGXPath currPath = paths.get(i);
			log.log(Level.FINER, "currPath = {0}", currPath);
			int cycleStartInd = currPath.cycleStartInd;
			if (!currPath.isCycle) {
				// UPDATE: we will never reach here - all paths are supposed to be cycles. If a path isn't a cycle, an exception is thrown
				// during the computation of the paths.
				// if current path is not a cycle, the cycleStartInd is the length of the path,
				// since all points in it are attractor-to points
				// NOTE: even though these points are not attracting to a cycle in this z-rank,
				// we still place them in the "attractor-to" node. The reason we do not make a separate
				// node for points which do not lead to or from a cycle in current z-rank is that
				// such nodes might create a cycle in the JVG, with nodes of attractor-to.
				// Attractor from nodes can't create a cycle of same z-rank, from the way they are
				// constructed (forcing to states in the attractor-from node or to lower z-rank nodes
				// only).
				log.warning("current path is not a cycle");
				cycleStartInd = currPath.path.size();
			}
			
			log.finer("cycleStartInd = " + cycleStartInd);
			log.finer("go over all non-cycle points in the path");
			// go over all non-cycle points in the path
			for (int j = 0; j < cycleStartInd; j++) {
				log.finer("checking point " + j + " in path");
				JVGXPoint currPoint = currPath.path.get(j);
				log.log(Level.FINER, "currPoint = {0}", currPoint);
				// remove from the current point states the "between cycle" states that we already
				// calculated and that are a part of the cycleStates
				// IMPORTANT: for this reason, calculating the "between cycle" states must (!!!) happen
				// before calculating the attractor to states
				BDD currAttrStates = currPoint.newBDD.id().andWith(cycleStates.not());
				log.log(Level.FINER, "currAttrState = {0}", new BddStringWrapper(currAttrStates));
				// add the current point attractor-to states to the general attractor-to states BDD
				attractorToStates.orWith(currAttrStates);
				log.log(Level.FINER, "attractorToStates = {0}", new BddStringWrapper(attractorToStates));
			}
		}
		
		log.log(Level.INFO, "attractorToStates = {0}", new BddStringWrapper(attractorToStates));
		log.info("END");
		return attractorToStates;
	}

	/**
	 * Calculating the attractor-from-cycle JVG node
	 * @param attrFromCands
	 * @param nodeToHandle
	 * @param cycleStates
	 * @param attractorToStates
	 * @param lowerZRank
	 * @return
	 */
	private BDD calcAttractorFromStates(BDD attrFromCands, RGNodeProcessInfo nodeToHandle,
			BDD cycleStates, BDD attractorToStates, BDD lowerZRank) {
		ArrayList<Object> inputParams = new ArrayList<Object>();
		inputParams.add(new BddStringWrapper(attrFromCands));
		inputParams.add(new BddStringWrapper(cycleStates));
		inputParams.add(new BddStringWrapper(attractorToStates));
		inputParams.add(nodeToHandle);
		log.log(Level.INFO, "START - attrFromCands = {0}, cycleStates = {1}, attractorToStates = {2}, nodeToHandle = {3}",
				inputParams.toArray());
		
		BDD attractorFromStates = Env.FALSE();
		// find the attractor-from states which are reachable from previous nodes
		for (Map.Entry<Integer, HashMap<Integer, StartPointInfo>> elem : nodeToHandle.xCellToStartPointMap.entrySet()) {
			for (Map.Entry<Integer, StartPointInfo> colElem : elem.getValue().entrySet()) {
				attractorFromStates.orWith(attrFromCands.and(colElem.getValue().startPoint));
			}
		}
		log.log(Level.FINER, "attractorFromStates after prev node start points = {0}",
				new BddStringWrapper(attractorFromStates));
		
		// find the attractor-from states which are reachable from cycleStates or attractorToStates		
		attractorFromStates.orWith(
				Env.unprime(((cycleStates.id().orWith(attractorToStates.id())).andWith(nodeToHandle.innerNodeTransitions.id().andWith(
						env.trans().id().andWith(sys.trans().id()))).andWith(
						Env.prime(attrFromCands.id()))).exist(
								env.moduleUnprimeVars().union(sys.moduleUnprimeVars()))));
		log.log(Level.FINER, "attractorFromStates after cycle/attractor-to states = {0}",
				new BddStringWrapper(attractorFromStates));
	
		// innerNodeTransitions already contains all of the transitions for the attractorFromStates, calculated
		// during attrFromCands calculation
		BDD succs = Env.unprime(attractorFromStates.id().andWith(nodeToHandle.innerNodeTransitions.id().andWith(sys.trans().id())).exist(
				env.moduleUnprimeVars().union(sys.moduleUnprimeVars()))).andWith(attrFromCands.id());
		while (!succs.isZero()) {
			attractorFromStates.orWith(succs.id());
			succs = Env.unprime(succs.andWith(nodeToHandle.innerNodeTransitions.id().andWith(sys.trans().id())).exist(
					env.moduleUnprimeVars().union(sys.moduleUnprimeVars()))).andWith(attrFromCands.id());
		}
		succs.free();
		log.log(Level.INFO, "END - attractorFromStates = {0}", new BddStringWrapper(attractorFromStates));
		return attractorFromStates;
	}
	
	/**
	 * Adding to the JVG the cycle and attractors JVG nodes computed for a specific RG node 
	 * @param nodeToHandle
	 * @param cycleStates
	 * @param attractorToStates
	 * @param attractorFromStates
	 * @param assumpGraph
	 * @param jvgNodeInd
	 * @return
	 */
	private Vector<Integer> updateNodesInGraph(RGNodeProcessInfo nodeToHandle, BDD cycleStates,
			BDD attractorToStates, BDD attractorFromStates, 
			AssumptionSatisfactionGraph assumpGraph, int jvgNodeInd) {
		ArrayList<Object> inputParams = new ArrayList<Object>();
		inputParams.add(nodeToHandle);
		inputParams.add(new BddStringWrapper(cycleStates));
		inputParams.add(new BddStringWrapper(attractorToStates));
		inputParams.add(new BddStringWrapper(attractorFromStates));
		log.log(Level.INFO, "START - jvgNodeInd = " + jvgNodeInd + ", nodeToHandle = {0}, cycleStates = {1},"
				+ " attractorToStates = {2}, attractorFromStates = {3}", inputParams.toArray());
		log.log(Level.FINER, "assumGraph = {0}", assumpGraph);
		
		// update the nodes
		Vector<Integer> jvgInds = new Vector<Integer>();
		if (!attractorToStates.isZero()) {
			JVGNode attractorToNode = new JVGNode();
			attractorToNode.type = JVGNodeType.ATTRACTOR_NODE_NOT_FROM_CYCLE;
			attractorToNode.nodeBDD = attractorToStates;
			attractorToNode.zRankNum = nodeToHandle.zRank;
			attractorToNode.violatedJusticeNum = attractorToNode.zRankNum % sys.justiceNum();
			attractorToNode.violatedSafeties = getViolatedSafetiesRG(
					attractorToNode.nodeBDD, safeties, aux);
			attractorToNode.isSafeyViolated = isSafetyViolatedRG(attractorToNode.nodeBDD);
			attractorToNode.isOnlySafetyViolated = isOnlySafetyViolatedRG(attractorToNode.nodeBDD);
			attractorToNode.transitions = 
					nodeToHandle.innerNodeTransitions.id().andWith(attractorToStates.id()).andWith(env.trans().id());//.and(sys.trans()))/*.and(
					//		Env.prime(safetiesBDD.not()))*/;
			nodes.put(jvgNodeInd, attractorToNode);
			jvgInds.add(jvgNodeInd);
			log.log(Level.FINE, "found attractor to node, ind = " + jvgNodeInd + ", node = {0}", attractorToNode);
			jvgNodeInd++;
		}
		if (!cycleStates.isZero()) {
			JVGNode cycleNode = new JVGNode();
			cycleNode.nodeBDD = cycleStates;
			cycleNode.type = JVGNodeType.CYCLE_NODE;
			cycleNode.zRankNum = nodeToHandle.zRank;
			cycleNode.violatedJusticeNum = cycleNode.zRankNum % sys.justiceNum();
			cycleNode.violatedSafeties = getViolatedSafetiesRG(
					cycleNode.nodeBDD, safeties, aux);
			cycleNode.isSafeyViolated = isSafetyViolatedRG(cycleNode.nodeBDD);
			cycleNode.isOnlySafetyViolated = isOnlySafetyViolatedRG(cycleNode.nodeBDD);
			cycleNode.transitions = 
					nodeToHandle.innerNodeTransitions.id().andWith(cycleStates.id()).andWith(env.trans().id());//.and(sys.trans()))/*.and(
						//	Env.prime(safetiesBDD.not()))*/;
			cycleNode.assumpGraph = assumpGraph;
			nodes.put(jvgNodeInd, cycleNode);
			jvgInds.add(jvgNodeInd);
			log.log(Level.FINE, "found cycle node, ind = " + jvgNodeInd + ", node = {0}", cycleNode);
			jvgNodeInd++;
		}

		if (!attractorFromStates.isZero()) {
			JVGNode attractorFromNode = new JVGNode();
			attractorFromNode.type = JVGNodeType.ATTRACTOR_NODE_FROM_CYCLE;
			attractorFromNode.nodeBDD = attractorFromStates;
			attractorFromNode.zRankNum = nodeToHandle.zRank;
			attractorFromNode.violatedJusticeNum = attractorFromNode.zRankNum % sys.justiceNum();
			attractorFromNode.violatedSafeties = getViolatedSafetiesRG(
					attractorFromNode.nodeBDD, safeties, aux);
			attractorFromNode.isSafeyViolated = isSafetyViolatedRG(attractorFromNode.nodeBDD);
			attractorFromNode.isOnlySafetyViolated = isOnlySafetyViolatedRG(attractorFromNode.nodeBDD);
			attractorFromNode.transitions = 
					nodeToHandle.innerNodeTransitions.id().andWith(attractorFromStates.id()).andWith(env.trans().id());//.and(sys.trans());/*.and(
					//		Env.prime(safetiesBDD.not()))*/;
			nodes.put(jvgNodeInd, attractorFromNode);
			jvgInds.add(jvgNodeInd);
			log.log(Level.FINE, "found attractor from node, ind = " + jvgNodeInd + ", node = {0}", attractorFromNode);
			jvgNodeInd++;
		}
		
		updateEdgesJVG(nodeToHandle, jvgInds);
		
		log.info("END - jvgInds = " + jvgInds.toString());
		return jvgInds;
	}
	
	/**
	 * Adding the edges to the JVG for the JVG nodes which were added in the context of a specific RG node
	 * @param nodeToHandle The specific RG node being processed
	 * @param jvgInds The indices of all the JVG nodes added
	 */
	private void updateEdgesJVG(RGNodeProcessInfo nodeToHandle, Vector<Integer> jvgInds) {
		log.log(Level.INFO, "START - jvgInds = " + jvgInds.toString() + ", nodeToHandle = {0}", nodeToHandle);
		
		Set<Integer> prevNodes = new HashSet<Integer>();
		// we place in the prevNodes all of the jvgInds. We know that there won't be an edge
		// from, for example, cycle node to attractor-to-cycle in same z-rank, from the way we 
		// made the env choices.
		prevNodes.addAll(jvgInds);
		
		for (Map.Entry<Integer, HashMap<Integer, StartPointInfo>> elem : nodeToHandle.xCellToStartPointMap.entrySet()) {
			for (Map.Entry<Integer, StartPointInfo> colElem : elem.getValue().entrySet()) {
				prevNodes.addAll(colElem.getValue().prevNodeJVGInds);				
			}
		}
		
		Iterator<Integer> setIter = prevNodes.iterator();
		while (setIter.hasNext()) {
			Integer prevNodeInd = setIter.next();
			for (int i = 0; i < jvgInds.size(); i++) {
				if (prevNodeInd == jvgInds.get(i)) {
					log.fine("ignore self-edges");
					continue;
				}
				
				BDD succ = Env.FALSE();
				if (prevNodeInd == 0) {
					log.fine("previous node is initial node");
					succ = nodes.get(jvgInds.get(i)).nodeBDD.and(nodes.get(prevNodeInd).nodeBDD);
					log.log(Level.FINEST, "succ = {0}", new BddStringWrapper(succ));
				} else {
					succ = Env.unprime((nodes.get(prevNodeInd).transitions.and(sys.trans()).and(
						Env.prime(nodes.get(jvgInds.get(i)).nodeBDD))).exist(
									env.moduleUnprimeVars().union(sys.moduleUnprimeVars())));
					log.log(Level.FINEST, "succ = {0}", new BddStringWrapper(succ));
				}
				
				if (!succ.isZero()) {
					log.fine("There's an edge from " + prevNodeInd + " to " + jvgInds.get(i));
					JVGEdge edge = new JVGEdge();
					edge.destInd = jvgInds.get(i);
					edge.type = JVGEdgeType.UNKNOWN_EDGE;
					// TODO - the edge.bdd can be updated here if needed.
					if (!edges.containsKey(prevNodeInd)) {
						Vector<JVGEdge> edgesVec = new Vector<JVGEdge>();
						edges.put(prevNodeInd, edgesVec);
					}
					edges.get(prevNodeInd).add(edge);
				}
			}
		}		
		
		log.info("END");
	}
	
	/**
	 * Handling the destinations - states in RG nodes computed as successors to the currently computed
	 * JVG nodes
	 * @param nodeToHandle
	 * @param rankGraph
	 * @param JVGNodesInds
	 * @return
	 */
	private Vector<RGNodeProcessInfo> handleDestinations(RGNodeProcessInfo nodeToHandle,
			RankingGraph rankGraph, Vector<Integer> JVGNodesInds) {
		log.log(Level.FINE, "START - nodeToHandle = {0}, JVGNodesInds = " + JVGNodesInds.toString(), nodeToHandle);
		
		Vector<RGNodeProcessInfo> destinations = new Vector<RGNodeProcessInfo>();
		Vector<RGEdge> edges = rankGraph.edges.get(nodeToHandle.currNodeIndRG);
		Vector<RGNodeProcessInfo> destsTemp = new Vector<RGNodeProcessInfo>();
		// sorting destinations in increasing z-rank order
		for (int i = 0; i < edges.size(); i++) {
			RGNodeProcessInfo info = new RGNodeProcessInfo();
			RGNode destNode = rankGraph.nodes.get(edges.get(i).destInd);
			info.zRank = destNode.zRankNum;
			info.currNodeIndRG = edges.get(i).destInd;
			info.node = destNode;
			info.setXMatrix(memX.get(destNode.zRankNum), this.safetiesBDD);
			destsTemp.add(info);
		}
		
		Collections.sort(destsTemp);
		if (destsTemp.isEmpty()) {
			log.finer("destsTemp is empty");
		} else {
			log.log(Level.FINER, "destsTemp sorted: {0}", destsTemp.toArray());
		}
		log.finer("iterating over current JVG nodes");
		for (int i = 0; i < JVGNodesInds.size(); i++) {
			BDD srcTransBDD = nodes.get(JVGNodesInds.get(i)).transitions.and(sys.trans());
			log.log(Level.FINER, "Checking JVG node " + JVGNodesInds.get(i) + ", transitionBDD = {0}",
					new BddStringWrapper(srcTransBDD));
		
			log.finer("iterating over destinations");
			for (int j = 0; j < destsTemp.size(); j++) {
				log.finer("Checking dest of z-rank " + destsTemp.get(j).zRank);
			
				RGNodeProcessInfo currDest = destsTemp.get(j);
				// we go over all of the xMatrix because of the ASSUMPTION_RANK env variable we added:
				// when we check for the start points here, the destination doesn't have this env variable yet, so
				// we would find start points in first row encountered. However, when starting to handle this destination,
				// we will add the ASSUMPTION_RANK variable to the different xCells, and a cell which was previously a 
				// start point might no longer be one (because the transition determined for its predecessor state in the 
				// leading node might require a different ASSUMPTION_RANK).
				// However, we can't just initially add the ASSUMPTION_RANK to all xCells in all nodes, since when computing the
				// attractor from states, we need to be able to ignore the ASSUMPTION_RANK in the transition.
				// NOTE: changing this from currDest...size() to "1" (i.e. looking only at first row)
				// fails the validation of completeness.
				for (int k = 0; k < currDest.nodeXMatrix.size(); k++) {
					for (int r = 0; r < currDest.nodeXMatrix.get(k).size(); r++) {
						BDD xCell = currDest.nodeXMatrix.get(k).get(r).and(Env.getBDDValue(ASSUMPTION_RANK, "" + k).id());
						log.log(Level.FINEST, "xCell["+k+"]["+r+"] = {0}",
								new BddStringWrapper(xCell));
						BDD succ = Env.unprime((srcTransBDD.id().andWith(Env.prime(xCell.id()))).exist(
								env.moduleUnprimeVars().union(sys.moduleUnprimeVars())));
						if (!succ.isZero()) {
							// we have a concrete env choice (from the way the transitions BDD is constructed)
							// only need to create a start point in the destination
							StartPointInfo startPoint = new StartPointInfo();
							startPoint.prevNodeJVGInds = new Vector<Integer>();
							startPoint.prevNodeJVGInds.add(JVGNodesInds.get(i));
							startPoint.startPoint = succ;
							log.log(Level.FINER, "found a start point: {0}", startPoint);
							if (currDest.xCellToStartPointMap.containsKey(k)) {
								if (currDest.xCellToStartPointMap.get(k).containsKey(r)) {
									// if we already have a start point in this cell - add to it the new start point states
									currDest.xCellToStartPointMap.get(k).get(r).startPoint.orWith(startPoint.startPoint);
									currDest.xCellToStartPointMap.get(k).get(r).prevNodeJVGInds.addAll(startPoint.prevNodeJVGInds);
								} else {
									currDest.xCellToStartPointMap.get(k).put(r, startPoint);
								}
							} else {
								currDest.xCellToStartPointMap.put(k, new HashMap<Integer, StartPointInfo>());
								currDest.xCellToStartPointMap.get(k).put(r, startPoint);						
							}
							log.log(Level.FINEST, "currDest = {0}", currDest);						
						} else {
							succ.free();
						}
						xCell.free();
					}
				}
				
				// currDest is of new destinations - the map will start as empty. It will be merged (if the destination
				// already exists) later, when updating the start points.
				if (!currDest.xCellToStartPointMap.isEmpty()) {
					destinations.add(currDest);
				}
			}
			srcTransBDD.free();
		}		
		
		// we might have the same destination appear multiple times in the vector. That is fine, since
		// when we add the destinations to the work list, we merge such destinations.
		log.log(Level.FINE, "returning destinations: {0}", destinations);
		log.fine("END");
		return destinations;
	}

	/**
	 * Adding invariants to the JVG nodes and edges
	 */
	private void addInvariants() {
		log.info("START");
		
		calcNodeInvariants(nodes);
		calcEdgeInvariants(edges, nodes);
		log.info("END");
	}

	/** 
	 * Freeing the paths computed
	 * @param paths
	 */
	private void freeJVGXPaths(Vector<JVGXPath> paths) {
		for (int i = 0; i < paths.size(); i++) {
			JVGXPath currPath = paths.get(i);
			// free the path points
			for (int j = 0; j < currPath.path.size(); j++) {
				JVGXPoint point = currPath.path.get(j);
				if (!point.oldBDD.isFree()) {
					point.oldBDD.free();
				}
				if (!point.newBDD.isFree()) {
					point.newBDD.free();
				}
			}
		}
	}
	
	/**
	 * Adding invariants to the JVG nodes
	 * @param nodes
	 */
	private void calcNodeInvariants(HashMap<Integer, ? extends JVGCommonNode> nodes) {
		log.fine("START");
		Iterator<? extends Map.Entry<Integer, ? extends JVGCommonNode>> itr =
				nodes.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<Integer, ? extends JVGCommonNode> entry =
					(Map.Entry<Integer, ? extends JVGCommonNode>) itr.next();

			BDD nodeBDD = entry.getValue().nodeBDD.id();
			log.log(Level.FINER, "nodeBDD: {0}", new BddStringWrapper(nodeBDD));
			
			ArrayList<JVGInvariant> invariants = calcInvariantsFromBDD(nodeBDD);
			entry.getValue().invariants.addAll(invariants);
		}
		
		log.fine("END");
	}	
	
	/**
	 * Extracting invariants of the form "var" = "val" from a BDD
	 * @param bdd
	 * @return
	 */
	private ArrayList<JVGInvariant> calcInvariantsFromBDD(BDD bdd) {
		log.log(Level.FINEST, "START - bdd = {0}", new BddStringWrapper(bdd));
		ArrayList<JVGInvariant> invariants = new ArrayList<JVGInvariant>();
		BDDDomain[] doms = bdd.support().getDomains();
		for (BDDDomain dom : doms) {		
			for (int i = 0; i < dom.size().intValue(); i++) {
				BDD invariantCand = dom.ithVar(i);
				log.log(Level.FINEST, "checking invariant: {0}", new BddStringWrapper(invariantCand));
				BDD tmp = bdd.and(invariantCand);
				if (tmp.equals(bdd)) {
					JVGInvariant invariant = new JVGInvariant();
					invariant.invariant = invariantCand;
					invariants.add(invariant);
					tmp.free();
					break;
				} else if (!tmp.isZero()) {
					tmp.free();
					break;
				}
				tmp.free();
			}
		}
		
		log.finest("END");
		return invariants;
	}
	
	/**
	 * Adding invariants to the JVG edges. The invariants are computed using the source and target nodes.
	 * @param edges
	 * @param nodes
	 */
	private void calcEdgeInvariants(HashMap<Integer, Vector<JVGEdge>> edges,
			HashMap<Integer, ? extends JVGCommonNode> nodes) {
		log.fine("START");
		// Go over all edges
		// For each edge, get the source and target nodes, calculate the transition between them
		// (an over-approximation, since we don't use the cross-node step but the general transition
		// so we might get edges that will not appear in an actual concrete winning strategy, and
		// therefore we might "lose" some invariants we would have had otherwise).
		// Finally, calculate invariants for this transition
		Iterator<Map.Entry<Integer, Vector<JVGEdge>>> itr = edges.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<Integer, Vector<JVGEdge>> entry = (Map.Entry<Integer, Vector<JVGEdge>>) itr.next();
			BDD srcNodeBDD = nodes.get(entry.getKey()).nodeBDD;
			log.finer("checking edges of node: " + entry.getKey());
			log.log(Level.FINER, "srcNodeBDD = {0}", new BddStringWrapper(srcNodeBDD));
			for (int i = 0; i < entry.getValue().size(); i++) {
				JVGEdge edge = entry.getValue().get(i);
				BDD destNodeBDD = nodes.get(edge.destInd).nodeBDD;
				log.finer("checking edge to: " + edge.destInd);
				log.log(Level.FINER, "destNodeBDD = {0}", new BddStringWrapper(destNodeBDD));
				edge.invariants.addAll(getEdgeInvariants(entry.getKey(), srcNodeBDD, destNodeBDD));
				entry.getValue().set(i, edge);
			}
		}

		log.fine("END");		
	}

	
	/**
	 * Helper function for getting the invariants of a JVG edge
	 * @param srcNodeInd
	 * @param srcBDD
	 * @param targetBDD
	 * @return
	 */
	private Vector<JVGInvariant> getEdgeInvariants(int srcNodeInd, BDD srcBDD, BDD targetBDD) {
		log.log(Level.FINER, "START. srcNodeInd = " + srcNodeInd);
		log.log(Level.FINER, "srcBDD = {0}", new BddStringWrapper(srcBDD));
		log.log(Level.FINER, "targetBDD = {0}", new BddStringWrapper(targetBDD));
		
		BDD trans = Env.FALSE();
		if (srcNodeInd == 0) {
			// The source node is the initial node
			trans = srcBDD.and(targetBDD);
			log.log(Level.FINER, "transition BDD: {0}", new BddStringWrapper(trans));
		} else {
			trans = Env.unprime(srcBDD.id().andWith(env.trans().id().andWith(sys.trans().id())).andWith(Env.prime(targetBDD.id())).
				exist(env.moduleUnprimeVars().union(sys.moduleUnprimeVars())));
			log.log(Level.FINER, "transition BDD (after exitential quantification): {0}",
					new BddStringWrapper(trans));
		}

		// This part is for the invariants calculation:
		// we go over all the unprimed system variables for this transition and check
		// if there is an invariant for any of them
		Vector<JVGInvariant> invs = new Vector<JVGInvariant>();
		BDDDomain[] doms = trans.support().getDomains();
		for (BDDDomain dom : doms) {
			for (int j = 0; j < dom.size().intValue(); j++) {
				BDD invariantCand = dom.ithVar(j);
				log.log(Level.FINER, "checking invariant: {0}", new BddStringWrapper(invariantCand));
				BDD tmp = trans.and(invariantCand);
				if (tmp.equals(trans)) {
					log.log(Level.FINE, "found invariant: {0}", new BddStringWrapper(invariantCand));
					JVGInvariant invariant = new JVGInvariant();
					invariant.invariant = invariantCand;
					invs.add(invariant);
					tmp.free();
					break;
				}
				tmp.free();
			}
		}
		trans.free();
		log.log(Level.FINER, "END - invs = {0}", invs);
		return invs;
	}
	
	private void printJVGraph() {
		String graphStr = "";
		Iterator<Map.Entry<Integer, JVGNode>> itr = nodes.entrySet().iterator();
		ArrayList<Object> bdds = new ArrayList<Object>();
		int k = 0;
		while (itr.hasNext()) {
			Map.Entry<Integer, JVGNode> entry = (Map.Entry<Integer, JVGNode>) itr.next();
			if (entry.getKey() == 0) {
				graphStr += "Initial Node (key: 0):\n";
				graphStr += "BDD: {" + k + "}\n";
				bdds.add(new BddStringWrapper(entry.getValue().nodeBDD));
				k++;
			} else {
				graphStr += "Node (key: " + entry.getKey() + "):\n";
				graphStr += "{" + k + "}\n";
				bdds.add(entry.getValue());
				k++;
			}
			graphStr += "\tTransitions to:\n";
			Vector<JVGEdge> currEdges = edges.get(entry.getKey());
			if (currEdges == null || currEdges.size() == 0) {
				graphStr += "\t\tno node\n";
			} else {
				for (int j = 0; j < currEdges.size(); j++) {
					graphStr += "{" + k + "}\n";
					bdds.add(currEdges.get(j));
					k++;
				}
			}
		}
		
		log.log(Level.INFO, graphStr, bdds.toArray());
	}

	public void freeJVGraph(HashMap<Integer, JVGNode> nodes,
			HashMap<Integer, Vector<JVGEdge>> edges) {
		// free nodes BDDs
		Iterator<Map.Entry<Integer, JVGNode>> itr = nodes.entrySet()
				.iterator();
		while (itr.hasNext()) {
			Map.Entry<Integer, JVGNode> entry = (Map.Entry<Integer, JVGNode>) itr.next();
			if (!entry.getValue().nodeBDD.isFree()) {
				entry.getValue().nodeBDD.free();
			}
			if (!entry.getValue().transitions.isFree()) {
				entry.getValue().transitions.free();
			}
			for (int i = 0; i < entry.getValue().invariants.size(); i++) {
				if (!entry.getValue().invariants.get(i).invariant.isFree()) {
					entry.getValue().invariants.get(i).invariant.free();
				}
			}
		}
		nodes.clear();
		
		// free edges BDDs
		Iterator<Map.Entry<Integer, Vector<JVGEdge>>> eItr = edges.entrySet().iterator();
		while (eItr.hasNext()) {
			Map.Entry<Integer, Vector<JVGEdge>> entry =
					(Map.Entry<Integer, Vector<JVGEdge>>) eItr.next();
			for (int i = 0; i < entry.getValue().size(); i++) {
				// edges transBDD is not initialized
				for (int j = 0; j < entry.getValue().get(i).invariants.size(); j++) {
					if (!entry.getValue().get(i).invariants.get(j).invariant.isFree()) {
						entry.getValue().get(i).invariants.get(j).invariant.free();
					}
				}
			}
		}
		edges.clear();
		
	}
	

	
//#############################Functions from this point are for the JVTS GUI usage only or common helper functions, not a part of core algorithm#################
	

	// This method gets the prevNode and currNode index, and returns a list of BDDs
	// of states in the current node to which there's a transition leading from the previous node
	public ArrayList<BDD> getConcreteStates(int prevNodeInd, int currNodeInd) {
		log.fine("START. prevNodeInd = " + prevNodeInd + ", currNodeInd = " + currNodeInd);
		ArrayList<BDD> concreteStates = new ArrayList<BDD>();
		
		BDD step = Env.FALSE();
		if (prevNodeInd == 0) {
			// previous node is the initial node, so we do not look for its successor states,
			// since the states it represents are in the current node
			step = Env.prime(nodes.get(prevNodeInd).nodeBDD.and(nodes.get(currNodeInd).nodeBDD));
		} else {
			BDD prevNodeTransitionsBDD = nodes.get(prevNodeInd).transitions.and(sys.trans());
			BDD currNodeBDD = nodes.get(currNodeInd).nodeBDD;
			
			log.log(Level.FINER, "prevNodeTransitionsBDD = {0}", new BddStringWrapper(prevNodeTransitionsBDD));
			log.log(Level.FINER, "currNodeBDD = {0}", new BddStringWrapper(currNodeBDD));
			step = (prevNodeTransitionsBDD.and(Env.prime(currNodeBDD)).exist(
					env.moduleUnprimeVars().union(sys.moduleUnprimeVars())));			
		}
		
		log.log(Level.FINEST, "step = {0}", new BddStringWrapper(step));
		BDDIterator envIter = step.iterator(env.modulePrimeVars());
		while (envIter.hasNext()) {
			BDD envChoice = (BDD) envIter.next();
			log.log(Level.FINER, "envChoice = {0}", new BddStringWrapper(envChoice));
			BDD stepWithEnvChoice = step.and(envChoice);
			log.log(Level.FINEST, "stepWithEnvChoice = {0}", new BddStringWrapper(stepWithEnvChoice));
			BDDIterator sysIter = stepWithEnvChoice.iterator(sys.modulePrimeVars());
			while (sysIter.hasNext()) {
				BDD sysChoice = (BDD) sysIter.next();
				log.log(Level.FINER, "sysChoice = {0}", new BddStringWrapper(sysChoice));
				BDD concreteState = Env.unprime(envChoice.and(sysChoice));
				log.log(Level.FINER, "concreteStates = {0}", new BddStringWrapper(concreteState));
				
				concreteStates.add(concreteState);
			}
		}
		
		log.fine("concreteStates:");
		for (int i = 0; i < concreteStates.size(); i++) {
			log.log(Level.FINE, "concreteStates["+i+"] = {0}", new BddStringWrapper(concreteStates.get(i)));
		}
		log.fine("END");
		return concreteStates;
	}
	
	// This method returns all of the initial concrete states
	public HashMap<Integer, ArrayList<BDD>> getInitialConcreteStates() {
		log.fine("START");
		
		HashMap<Integer, ArrayList<BDD>> res = new HashMap<Integer, ArrayList<BDD>>();
		Vector<JVGEdge> initialNodeSuccs = edges.get(0);
		for (int i = 0; i < initialNodeSuccs.size(); i++) {
			int destInd = initialNodeSuccs.get(i).destInd;
			ArrayList<BDD> concreteStates = getConcreteStates(0, destInd);
			res.put(destInd, concreteStates);
		}
		
		log.fine("END");
		return res;
	}

	
	// This method gets a BDD and extracts all of the invariants from it (of the form "var = value")
	public ArrayList<JVGInvariant> getInvariantsFromBDD(BDD bdd) {
		log.log(Level.FINE, "START - bdd = {0}", new BddStringWrapper(bdd));
		ArrayList<JVGInvariant> invariants = calcInvariantsFromBDD(bdd);
		log.fine("END");
		return invariants;
	}

	// This method gets a current node index and a BDD representing a state in this node,
	// and returns all of the states which are its neighbors in the node and in other nodes,
	// for which there is an edge leading from the given state to them
	public void getNextStates(int currNodeInd, BDD currBDD,
			ArrayList<Map.Entry<Integer, Map.Entry<JVGNode, JVGEdge>>> nodesAndEdges) {
		log.log(Level.FINE, "START. currNodeInd = " + currNodeInd + ", currBDD = {0}", 
			new BddStringWrapper(currBDD));
		BDD currNodeTransitions = nodes.get(currNodeInd).transitions.and(sys.trans());
		log.log(Level.FINER, "currNodeTransitions = {0}", new BddStringWrapper(currNodeTransitions));
		
		ArrayList<Integer> destNodesInds = new ArrayList<Integer>();
		if (currNodeInd != 0) {
			// if not the initial node, we want to look for neighbor states in same node
			destNodesInds.add(currNodeInd);
		}
		Vector<JVGEdge> nodeEdges = edges.get(currNodeInd);
		if (nodeEdges != null) {
			// there are edges for this node in the symbolic graph
			for (int i = 0; i < nodeEdges.size(); i++) {
				destNodesInds.add(nodeEdges.get(i).destInd);
			}
		}
		log.finer("destNodesInds = " + destNodesInds.toString());
		
		for (int i = 0; i < destNodesInds.size(); i++) {
			getNextStatesInNode(currNodeTransitions, currBDD, destNodesInds.get(i), nodesAndEdges);
		}
		
		log.fine("nodesAndEdge:");
		for (int i = 0; i < nodesAndEdges.size(); i++) {
			int ind = nodesAndEdges.get(i).getKey();
			JVGNode jvgNode = nodesAndEdges.get(i).getValue().getKey();
			JVGEdge jvgEdge = nodesAndEdges.get(i).getValue().getValue();
			ArrayList<Object> params = new ArrayList<Object>();
			params.add(jvgNode);
			params.add(jvgEdge);
			log.log(Level.FINE, "nodesAndEdges[" + i + "]: ind = " + ind + ", jvgNode = {0}, jvgEdge = {1}",
					params.toArray());
		}
		
		log.fine("END");
	}
		
	private void getNextStatesInNode(BDD currNodeTransitions, BDD currBDD, int destNodeInd,
			ArrayList<Map.Entry<Integer, Map.Entry<JVGNode, JVGEdge>>> nodesAndEdges) {
		log.finer("START - destNodeInd = " + destNodeInd);
		// get all neighbor states in current node
		BDD currNodeBDD = nodes.get(destNodeInd).nodeBDD;
		
		// Note - we already removed from the symbolic nodes the states that are safety violations
		
		BDD currNodeInnerStep = currNodeTransitions.and(currBDD).and(Env.prime(currNodeBDD));
		log.log(Level.FINEST, "currNodeBDD = {0}", new BddStringWrapper(currNodeBDD));
		log.log(Level.FINEST, "currNodeInnerStep = {0}", new BddStringWrapper(currNodeInnerStep));
		BDDIterator iter = currNodeInnerStep.iterator(env.modulePrimeVars().union(sys.modulePrimeVars()));
		while (iter.hasNext()) {
			// note: there should only be one envChoice since env is deterministic and we
			// started from a concrete state
			// But there can be multiple sys choices, which is why this is a "while" loop
			BDD choice = (BDD) iter.next();
			log.log(Level.FINER, "choice = {0}", new BddStringWrapper(choice));
			BDD concreteState = Env.unprime(choice);
			log.log(Level.FINER, "concreteState = {0}", new BddStringWrapper(concreteState));
			JVGNode jvgNode = new JVGNode();
			jvgNode.isOnlySafetyViolated = nodes.get(destNodeInd).isOnlySafetyViolated;
			jvgNode.isSafeyViolated = nodes.get(destNodeInd).isSafeyViolated;
			jvgNode.nodeBDD = nodes.get(destNodeInd).nodeBDD.id();
			jvgNode.transitions = nodes.get(destNodeInd).transitions.id();
			jvgNode.type = nodes.get(destNodeInd).type;
			jvgNode.violatedJusticeNum = nodes.get(destNodeInd).violatedJusticeNum;
			jvgNode.violatedSafeties = nodes.get(destNodeInd).violatedSafeties;
			jvgNode.zRankNum = nodes.get(destNodeInd).zRankNum;
			jvgNode.invariants.addAll(getInvariantsFromBDD(concreteState));
			JVGEdge jvgEdge = new JVGEdge();
			jvgEdge.destInd = destNodeInd;
			jvgEdge.invariants.addAll(getInvariantsFromBDD(concreteState));
			// no invariants on edge are needed, since there are concrete states
			// if we would want invariants, they will be identical to the invariants of the 
			// destination state
			Map.Entry<JVGNode, JVGEdge> nodeAndEdge =
					new AbstractMap.SimpleEntry<JVGNode, JVGEdge>(jvgNode, jvgEdge);
			Map.Entry<Integer, Map.Entry<JVGNode, JVGEdge>> entry =
					new AbstractMap.SimpleEntry<Integer, Map.Entry<JVGNode, JVGEdge>>(
							destNodeInd, nodeAndEdge);
			nodesAndEdges.add(entry);
		}
		
		log.finer("END");
	}

	// This method gets a var and a val strings and returns a JVGInvariant of the form
	// "var = val"
	public JVGInvariant convertStringsToInvariant(String var, String val) {
		log.fine("START - var = " + var + ", val = " + val);

		JVGInvariant resInv = new JVGInvariant();	
		// Using the name, we get the domain
		BDDDomain domain =  Env.getVar(var).getDomain();
		// Enumerate over the possible domain values, until finding the one equal to the trimmed formula
		for (long i = 0; i < domain.size().longValue(); i++) {
			if (Env.stringer.elementName(domain, new BigInteger(""+i)).equals(val)) {
				resInv.invariant = domain.ithVar(i).id();
				break;
			}
		}
		
		log.log(Level.FINE, "END - resInv = {0}", resInv);
		return resInv;
	}
	
	public String getViolatedJustice(JVGNode node) {
		log.log(Level.FINE, "START - node = {0}", node);
		
		if (node.violatedJusticeNum == -1) {
			log.fine("END - node doesn't violate a justice");
			return "";
		}
		
		String justiceStr = "";
  		for (int i = 0; i < sysBehaviorInfo.size(); i++) {
			if (sysBehaviorInfo.get(i).isJustice()) {
				if (sys.justiceAt(node.violatedJusticeNum).equals(
						sysBehaviorInfo.get(i).justice)) {
					justiceStr = Tracer.getNiceStringForId(sysBehaviorInfo.get(i).traceId);
				}
			}
		}
		
		log.fine("END - justice = " + justiceStr);
		return justiceStr;
	}
	
	public String getSatisfiedAssumptionJustice(AssumptionSatNode assumpSatNode)
	{
		log.log(Level.FINE, "START - node = {0}", assumpSatNode);
		
		String justiceStr = "";
  		for (int i = 0; i < envBehaviorInfo.size(); i++) {
			if (envBehaviorInfo.get(i).isJustice()) {
				if (env.justiceAt(assumpSatNode.assumptionInd).equals(
						envBehaviorInfo.get(i).justice)) {
					justiceStr = Tracer.getNiceStringForId(envBehaviorInfo.get(i).traceId);
				}
			}
		}
		
		log.fine("END - justice = " + justiceStr);
		return justiceStr;
	}		
	
	// This method gets a list of all of the unprimed variables, and for which one a flag - 
	// true = this variable is auxiliary, false = this variable is not auxiliary.
	public ArrayList<Map.Entry<String, Boolean>> getAllVars() {
		log.fine("START.");
		
		// We take all variables - otherwise we might miss variables which do not appear in the nodeBDD.
		// It is true these variables are "don't care" as far as the nodeBDD, but they do result in different
		// concrete states, and therefore the user should be allowed to choose values for them.
		ArrayList<String> varList = new ArrayList<String>();
		BDDDomain[] doms = Env.globalUnprimeVars().getDomains();

		for (BDDDomain dom : doms) {
			varList.add(dom.getName());
		}
		
		ArrayList<Map.Entry<String, Boolean>> res = new ArrayList<Map.Entry<String, Boolean>>();
		for (String var: varList) {
			boolean isAux;
			if (this.auxVars.contains(var)) {
				isAux = true;
			} else {
				isAux = false;
			}
			Map.Entry<String, Boolean> val = new AbstractMap.SimpleEntry<String, Boolean>(var, isAux);
			res.add(val);
		}
		
		log.fine("END. res = " + res.toString());
		return res;
	}

	private ArrayList<String> getValNames(BDDDomain domain, BDD bdd, boolean primeDomain) {
		log.log(Level.FINE, "START. domain = " + domain.getName() + ", bdd = {0}", new BddStringWrapper(bdd));
		ArrayList<String> res = new ArrayList<String>();
		for (long i = 0; i < domain.size().longValue(); i++) {
			String valName = Env.stringer.elementName(domain, new BigInteger("" + i));
			log.finest("domain value = " + valName);
			BDD domBDD = domain.ithVar(i);
			if (primeDomain) {
				domBDD = Env.prime(domBDD);
			}
			if (!bdd.and(domBDD).isZero()) {
				res.add(valName);
			}	
		}
		
		log.fine("END. res = " + res);
		return res;
	}
	
	// This method gets a node, a BDD and a variable and returns all possible values which
	// can be assigned to this variable and still satisfy the given BDD and the node constraints.
	public ArrayList<String> getNodeVarValues(int nodeInd, BDD currBDD, String var) {
		log.log(Level.FINE, "START. nodeInd = " + nodeInd + ", var = " + var + ", curBDD = {0}",
				new BddStringWrapper(currBDD));
		
		BDD currNodeBDD = nodes.get(nodeInd).nodeBDD.and(currBDD);
		
		// Using the name, we get the domain
		BDDDomain domain =  Env.getVar(var).getDomain();
		ArrayList<String> res = getValNames(domain, currNodeBDD, false /*primeDomain*/);
		log.fine("END. res = " + res.toString());
		return res;
	}
	
	// This method gets a node and a BDD and returns a pair of (index, BDD) which represents a concrete state in the
	// relevant symbolic node of "index", that also satisfies the given BDD.
	public Map.Entry<Integer, BDD> getConcreteState(int nodeInd, BDD currBDD) {
		log.log(Level.FINE, "START. nodeInd = " + nodeInd + ", currBDD = {0}", new BddStringWrapper(currBDD));

		BDD tmp = nodes.get(nodeInd).nodeBDD.and(currBDD);
		// if we use the "support" method of the BDD to get the vars, in the case of variables whose domain
		// is represented by multiple boolean vars (i.e. the domain contains > 2 values), the support might
		// not include all of these boolean vars. Therefore, using satOne with them will end up providing
		// a BDD with concrete assignments ("var = val") to some of the variables.
		BDD assign = CoreUtil.satOne(tmp, Env.globalUnprimeVars());
		log.log(Level.FINE, "assign = {0}", new BddStringWrapper(assign));
		
		int resInd = nodeInd;
		if (nodeInd == 0) {
			// this is the initial marker node, and we want the index of the actual node where
			// this initial state is contained
			for (int i = 0; i < edges.get(nodeInd).size(); i++) {
				if (!assign.and(nodes.get(edges.get(nodeInd).get(i).destInd).nodeBDD).isZero()) {
					resInd = edges.get(nodeInd).get(i).destInd;
					break;
				}
			}
		}
		
		Map.Entry<Integer, BDD> res = new AbstractMap.SimpleEntry<Integer, BDD>(resInd, assign);
		log.log(Level.FINE, "END. res = {0}", res);
		return res;
	}

	public ArrayList<String> convertInvariantToStrings(JVGInvariant invariant) {
		log.log(Level.FINE, "START. invariant = {0}", invariant);
		
		ArrayList<String> res = new ArrayList<String>();
		
		BDD invBDD = invariant.invariant;
		BDDDomain[] doms = invBDD.support().getDomains();
		if (doms.length > 1) {
			log.severe("ERROR - invariant contains multiple variables - unsupported invariants");
		}
		res.add(doms[0].getName());
		
		// these are all possible values according to the invariant. Should be only one value,
		// since the invariant is of the form "var =  val"
		ArrayList<String> valNames = getValNames(doms[0], invariant.invariant, false /*primeDomain*/);
		if (valNames.size() > 1) {
			log.severe("ERROR - multiple possible values for invariant, invalid scenraio. Taking the first value");
		}
		res.add(valNames.get(0));
		
		// check if this is aux var
		if (this.auxVars.contains(doms[0].getName())) {
			res.add("aux");
		}
		
		log.fine("END. res = " + res);
		return res;
	}

	// This method returns the number of neighbors for a given concrete state
	public int countNumNeighbors(int nodeInd, BDD currState) {
		log.log(Level.INFO, "START - nodeInd = " + nodeInd + ", currState = {0}",
				new BddStringWrapper(currState));
		
		BDD nodeTransitions = nodes.get(nodeInd).transitions.and(sys.trans());
		BDD succ = nodeTransitions.and(currState);
		double satCount = succ.satCount();

		System.out.println("satCount = " + satCount);
		log.info("END - satCount = " + satCount);
		return (int) satCount;
	}
	
	public ArrayList<JVGInvariant> getNodeVarsAndVals(int nodeInd, BDD currBDD) {
		log.log(Level.INFO, "START - nodeInd = " + nodeInd + ", currBDD = {0}",
				new BddStringWrapper(currBDD));
		ArrayList<JVGInvariant> res = new ArrayList<JVGInvariant>();
		
		BDD nodeBDD = nodes.get(nodeInd).nodeBDD;
		BDD toCheck = nodeBDD.and(currBDD);
		// we can only look at the support here because "don't care" variables, which do not appear in this
		// node BDD combined with the currBDD, will not be constrained to a single value by this BDD anyway. 
		BDDVarSet vars = toCheck.support();
		BDDDomain[] doms = vars.getDomains();
		for (BDDDomain dom : doms) {
			log.finer("Checking variable = " + dom.getName());
			BDD temp = toCheck.id().exist(vars.minus(dom.set()));
			double numAssign = temp.satCount(dom.set());
			if (numAssign == 1) {
				log.finer("Found only one assignment for this variable - returning it");
				JVGInvariant inv = new JVGInvariant();
				BDD invariant = CoreUtil.satOne(toCheck, dom.set());
				inv.invariant = invariant;
				res.add(inv);
			}
			temp.free();
		}
		
		log.log(Level.INFO, "END - res = {0}", res);
		return res;	
	}

	// Returns a list of successor variable values, for all successors of the given currBDD
	// (predBDD should be a concrete state, i.e. only unprimed variables, and they
	// should all already be assigned. nodeBDD should be contain the user choices of
	// variable assignments so far)
	public ArrayList<String> getSuccessorVarValues(int nodeInd, BDD nodeBDD, BDD predBDD,
			String var) {
		log.log(Level.INFO, "START - nodeInd = " + nodeInd + ", nodeBDD = {0}, predBDD = {1}",
				new Object[] { new BddStringWrapper(nodeBDD), new BddStringWrapper(predBDD)});
		
		BDD succs = nodes.get(nodeInd).transitions.and(sys.trans()).and(predBDD).and(
				Env.prime(nodeBDD));
		// Using the name, we get the domain
		BDDDomain domain =  Env.getVar(var).getDomain();
		ArrayList<String> res = getValNames(domain, succs, true /*primeDomain*/);
		log.fine("END. res = " + res.toString());
		return res;
	}
	
	public Map.Entry<Integer, BDD> getSuccessorConcreteState(int nodeInd, BDD currBDD, BDD predBDD) {
		log.log(Level.INFO, "START - nodeInd = " + nodeInd + ", currBDD = {0}, predBDD = {1}",
				new Object[] { new BddStringWrapper(currBDD), new BddStringWrapper(predBDD)});
				
		BDD tmp = nodes.get(nodeInd).transitions.and(sys.trans()).and(predBDD).and(Env.prime(currBDD));
		// if we use the "support" method of the BDD to get the vars, in the case of variables whose domain
		// is represented by multiple boolean vars (i.e. the domain contains > 2 values), the support might
		// not include all of these boolean vars. Therefore, using satOne with them will end up providing
		// a BDD with concrete assignments ("var = val") to some of the variables.
		BDD res = Env.unprime(CoreUtil.satOne(tmp, Env.globalPrimeVars()));
		
		// find in which symbolic node this successor state is located
		BDD nodeBDD = nodes.get(nodeInd).nodeBDD;
		int succInd = -1;
		if (!nodeBDD.and(res).isZero()) {
			// found the symbolic node containing this state - it is the same node as predecessor
			succInd = nodeInd;
		} else {
			Vector<JVGEdge> edgeList = edges.get(nodeInd);
			for (int i = 0; i < edgeList.size(); i++) {
				nodeBDD = nodes.get(edgeList.get(i).destInd).nodeBDD;
				if (!nodeBDD.and(res).isZero()) {
					// found the symbolic node containing this state
					succInd = edgeList.get(i).destInd;
					break;
				}
			}
		}
		
		Map.Entry<Integer, BDD> ret = new AbstractMap.SimpleEntry<Integer, BDD>(succInd, res);
		log.log(Level.FINE, "END. succInd = " + succInd + ", res = {0}", new BddStringWrapper(res));
		return ret;
	}

	public ArrayList<JVGInvariant> getSuccessorVarsAndVals(int nodeInd, BDD currBDD, BDD predBDD) {
		log.log(Level.INFO, "START - nodeInd = " + nodeInd + ", currBDD = {0}, predBDD = {1}",
				new Object[] { new BddStringWrapper(currBDD), new BddStringWrapper(predBDD) });
		ArrayList<JVGInvariant> res = new ArrayList<JVGInvariant>();

		BDD toCheck = nodes.get(nodeInd).transitions.and(sys.trans()).and(predBDD).and(Env.prime(currBDD));
		BDDVarSet vars = toCheck.support().intersect(Env.globalPrimeVars());
		BDDDomain[] doms = vars.getDomains();
		for (BDDDomain dom : doms) {
			log.finer("Checking variable = " + dom.getName());
			BDD temp = toCheck.id().exist(vars.minus(dom.set()).union(Env.globalUnprimeVars()));
			double numAssign = temp.satCount(dom.set());
			if (numAssign == 1) {
				log.info("Found only one assignment for this variable - returning it");
				JVGInvariant inv = new JVGInvariant();
				BDD invariant = Env.unprime(CoreUtil.satOne(toCheck, dom.set()));
				inv.invariant = invariant;
				res.add(inv);
			}
			temp.free();
		}
		
		log.log(Level.INFO, "END - res = {0}", res);
		return res;	
	}
	
	/**
	 * Merging of attractors in JVG
	 */
	public void mergedAttractors() {
		log.info("START");

		if (nodes.isEmpty()) {
			log.fine("symbolic graph is empty - nothing to merge");
			return;
		}
		
		// merge nodes
		TreeSet<Integer> workList = new TreeSet<Integer>();
		workList.add(0); // adding the initial node
		while (!workList.isEmpty()) {
			log.finest("workList = " + workList);
			int currNodeInd = workList.pollFirst();
			log.finest("currNodeInd = " + currNodeInd);
			JVGNode currNode = nodes.get(currNodeInd);
			if (currNode.type != JVGNodeType.ATTRACTOR_NODE_FROM_CYCLE &&
				currNode.type != JVGNodeType.ATTRACTOR_NODE_NOT_FROM_CYCLE) {
				log.fine("node with ind " + currNodeInd + " is not an attractor");
				if (edges.containsKey(currNodeInd)) {
					log.fine("node " + currNodeInd + " has children - adding them to work list");
					Vector<JVGEdge> edgesVec = edges.get(currNodeInd);
					for (int i = 0; i < edgesVec.size(); i++) {
						int destInd = edgesVec.get(i).destInd;
						workList.add(destInd);
					}
				}
			} else {
				log.fine("node with ind " + currNodeInd + " is an attractor");
				if (edges.containsKey(currNodeInd)) {
					log.fine("going over node " + currNodeInd + " children");
					Vector<JVGEdge> edgesVec = edges.get(currNodeInd);
					TreeSet<Integer> sortedChildren = new TreeSet<Integer>();
					for (int i = 0; i < edgesVec.size(); i++) {
						sortedChildren.add(edgesVec.get(i).destInd);
					}
					
					while (!sortedChildren.isEmpty()) {
						int destInd = sortedChildren.pollFirst();						
						if (workList.contains(destInd)) {
							workList.remove(destInd);
						}

						if (nodes.get(destInd).type != JVGNodeType.ATTRACTOR_NODE_FROM_CYCLE &&
							nodes.get(destInd).type != JVGNodeType.ATTRACTOR_NODE_NOT_FROM_CYCLE) {
							log.finer("child not an attractor - adding to worklist: " + destInd);
							workList.add(destInd);
						} else if (!shouldMerge(currNodeInd, destInd)) {
							log.finer("child is an attractor, but merging will create a cycle -"
									+ " not merging and adding to worklist: " + destInd);
							workList.add(destInd);
						} else {
							log.fine("merging node " + destInd + " into node " + currNodeInd);
							// NOTE: this changes the sortedChildren, updating it with the merged child node's (destInd) children
							mergeNodes(currNodeInd, destInd, sortedChildren);
						}
						log.fine("edgesVec = " + edgesVec);
					}
				}							
			}
		}

		// after merging the attractors, we might have "holes" in the sequence of node keys. This will change them to
		// be a single sequence starting at 0.
		changeNodeKeysToSequentialOrder();
		
		printJVGraph();
		
		log.info("END");
	}
	
	private boolean shouldMerge(int sourceInd, int targetInd) {
		log.finer("START - sourceInd = " + sourceInd + ", targetInd = " + targetInd);
		
		int numPaths = 0;
		LinkedList<Integer> workList = new LinkedList<Integer>();
		workList.add(sourceInd);
		while (!workList.isEmpty()) {
			int currInd = workList.pop();
			if (currInd == targetInd) {
				numPaths++;
				if (numPaths > 1) {
					log.finer("more than 1 path from " + sourceInd + " to " + targetInd);
					return false;
				}
			} else {
				if (edges.containsKey(currInd)) {
					log.finest("add node children to worklist: " + currInd);
					for (int i = 0; i < edges.get(currInd).size(); i++) {
						workList.add(edges.get(currInd).get(i).destInd);
					}
				}
			}
		}
		
		log.finer("END - node " + sourceInd + " has at most 1 path to target " + targetInd);
		return true;
	}
	
	private void mergeNodes(int mergeInto, int mergeFrom, TreeSet<Integer> sortedChildren) {
		log.fine("START. mergeInto = " + mergeInto + ", mergeFrom = " + mergeFrom);
		JVGNode mergeIntoNode = nodes.get(mergeInto);
		JVGNode mergeFromNode = nodes.get(mergeFrom);
		
		mergeIntoNode.nodeBDD = mergeIntoNode.nodeBDD.or(mergeFromNode.nodeBDD);
		mergeIntoNode.transitions = mergeIntoNode.transitions.or(mergeFromNode.transitions);
		mergeIntoNode.isOnlySafetyViolated = (mergeIntoNode.isOnlySafetyViolated && mergeFromNode.isOnlySafetyViolated);
		mergeIntoNode.isSafeyViolated = (mergeIntoNode.isOnlySafetyViolated || mergeFromNode.isSafeyViolated);		
		HashSet<BehaviorInfo> safeSet = new HashSet<BehaviorInfo>();
		safeSet.addAll(mergeIntoNode.violatedSafeties);
		safeSet.addAll(mergeFromNode.violatedSafeties);
		mergeIntoNode.violatedSafeties.clear();
		mergeIntoNode.violatedSafeties.addAll(safeSet);

		Vector<JVGInvariant> mergedInvs = mergeInvariants(mergeIntoNode.invariants, mergeFromNode.invariants);
		mergeIntoNode.invariants.clear();
		mergeIntoNode.invariants.addAll(mergedInvs);
		
		// There might be multiple justices being violated now in the merged node - 
		// but we only write on it the first justice, since this is an attractor node in any
		// case, so which justice is violated is less important. Same for the z-rank.
		// These are attractor nodes - no assumption graph in either of them
		
		// update the graph
		nodes.remove(mergeFrom);
		
		Vector<JVGEdge> intoEdges = edges.get(mergeInto);
		for (int i = 0; i < intoEdges.size(); i++) {
			if (intoEdges.get(i).destInd == mergeFrom) {
				intoEdges.remove(i);
				break;
			}
		}

		if (edges.containsKey(mergeFrom)) {
			if (intoEdges.isEmpty()) {
				log.fine("intoEdges is empty");
				intoEdges.addAll(edges.get(mergeFrom));
				for (int i = 0; i < edges.get(mergeFrom).size(); i++) {
					sortedChildren.add(edges.get(mergeFrom).get(i).destInd);
				}
				log.fine("intoEdges = " + intoEdges);
			} else {
				log.fine("intoEdges is not empty");
				Vector<JVGEdge> fromEdges = edges.get(mergeFrom);
				Vector<JVGEdge> edgesToAdd = new Vector<JVGEdge>();
				for (int j = 0; j < fromEdges.size(); j++) {
					boolean edgeFound = false;
					for (int i = 0; i < intoEdges.size(); i++) {
						if (intoEdges.get(i).destInd == fromEdges.get(j).destInd) {
							Vector<JVGInvariant> mergedInvars = mergeInvariants(intoEdges.get(i).invariants,
									fromEdges.get(j).invariants);
							intoEdges.get(i).invariants.clear();
							intoEdges.get(i).invariants.addAll(mergedInvars);
							edgeFound = true;
							break;
						}
					}
					if (!edgeFound) {
						edgesToAdd.add(fromEdges.get(j));
						sortedChildren.add(fromEdges.get(j).destInd);
					}
				}
				intoEdges.addAll(edgesToAdd);
				log.fine("intoEdges = " + intoEdges);
			}
			edges.remove(mergeFrom);
		}	
		
		// go over all edges and change destInd to new ind, and update invariants
		BDD destNodeBDD = nodes.get(mergeInto).nodeBDD;
		Iterator<Map.Entry<Integer, Vector<JVGEdge>>> itr = edges.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<Integer, Vector<JVGEdge>> elem =
					(Map.Entry<Integer, Vector<JVGEdge>>) itr.next();
			for (int i = 0; i < elem.getValue().size(); i++) {
				if (elem.getValue().get(i).destInd == mergeFrom) {
					elem.getValue().get(i).destInd = mergeInto;
					elem.getValue().get(i).invariants.clear();
					BDD srcNodeBDD = nodes.get(elem.getKey()).nodeBDD;
					elem.getValue().get(i).invariants.addAll(getEdgeInvariants(elem.getKey(), srcNodeBDD, destNodeBDD));
				}
			}
		}
		
		log.fine("END");
	}
	
	private Vector<JVGInvariant> mergeInvariants(Vector<JVGInvariant> into, Vector<JVGInvariant> from) {
		log.fine("START");
		
		Vector<JVGInvariant> mergedInvariants = new Vector<JVGInvariant>();
		for (int i = 0; i < into.size(); i++) {
			BDD intoInv = into.get(i).invariant;
			for (int j = 0; j < from.size(); j++) {
				BDD fromInv = from.get(j).invariant;
				if (intoInv.equals(fromInv)) {
					mergedInvariants.add(into.get(i));
					break;
				}
			}
		}
		
		log.fine("END");
		return mergedInvariants;
	}
	
	private void changeNodeKeysToSequentialOrder() {
		log.fine("START");
		
		int newNodeInd = 0;
		Iterator<Map.Entry<Integer, JVGNode>> nodesItr = nodes.entrySet().iterator();
		HashMap<Integer, Integer> changes = new HashMap<Integer, Integer>();
		while (nodesItr.hasNext()) {
			Map.Entry<Integer, JVGNode> entry = (Map.Entry<Integer, JVGNode>) nodesItr.next();
			if (entry.getKey() == newNodeInd) {
				newNodeInd++;
				continue;
			} else {
				if (nodes.containsKey(newNodeInd)) {
					switchNodesIndices(entry.getKey(), newNodeInd);
				} else {
					changes.put(entry.getKey(), newNodeInd);
				}
				newNodeInd++;
			}
		}
		
		Iterator<Map.Entry<Integer, Integer>> changesItr = changes.entrySet().iterator();
		while (changesItr.hasNext()) {
			Map.Entry<Integer, Integer> entry = (Map.Entry<Integer, Integer>) changesItr.next();
			int oldKey = entry.getKey();
			int newKey = entry.getValue();
			nodes.put(newKey, nodes.get(oldKey));
			nodes.remove(oldKey);
			if (edges.get(oldKey) != null) {
				edges.put(newKey, edges.get(oldKey));
				edges.remove(oldKey);
			}
			Iterator<Map.Entry<Integer, Vector<JVGEdge>>> edgesItr = edges.entrySet().iterator();
			while (edgesItr.hasNext()) {
				Map.Entry<Integer, Vector<JVGEdge>> elem = (Map.Entry<Integer, Vector<JVGEdge>>) edgesItr.next();
				for (int i = 0; i < elem.getValue().size(); i++) {
					JVGEdge edge = elem.getValue().get(i);
					if (oldKey == edge.destInd) {
						edge.destInd = newKey;
					}
				}
			}			
		}
		
		log.fine("END");
	}
	
	// switching the indices of two nodes in the graph
	private void switchNodesIndices(int firstInd, int secondInd) {
		log.fine("START");
		
		// changing the nodes map
		JVGNode firstNode = nodes.get(firstInd);
		nodes.put(firstInd, nodes.get(secondInd));
		nodes.put(secondInd, firstNode);
		
		// changing the edges map
		// changing out going edges
		Vector<JVGEdge> firstEdges = edges.get(firstInd);
		Vector<JVGEdge> secondEdges = edges.get(secondInd);
		if (firstEdges != null || secondEdges != null) {
			if (firstEdges != null) {
				edges.put(secondInd, firstEdges);
			} else {
				// if the switch node has no out-going edges, remove it from from edges map
				edges.remove(secondInd);
			}
			if (secondEdges != null) {
				edges.put(firstInd, secondEdges);
			} else {
				// if the switch node has no out-going edges, remove it from from edges map
				edges.remove(firstInd);
			}
		}
		
		// changing incoming edges
		Iterator<Map.Entry<Integer, Vector<JVGEdge>>> edgesItr = edges.entrySet().iterator();
		while (edgesItr.hasNext()) {
			Map.Entry<Integer, Vector<JVGEdge>> elem = (Map.Entry<Integer, Vector<JVGEdge>>) edgesItr.next();
			for (int i = 0; i < elem.getValue().size(); i++) {
				// we must change both in a single iteration, to avoid a case of double change of the indices
				JVGEdge edge = elem.getValue().get(i);
				if (edge.destInd == firstInd) {
					edge.destInd = secondInd;
				} else if (edge.destInd == secondInd) {
					edge.destInd = firstInd;
				}
			}
		}
		
		log.fine("END");
	}
	
	public ArrayList<String> getViolatedSafeties(JVGNode node) {
		log.log(Level.FINER, "START. node = {0}", node);

		ArrayList<String> res = new ArrayList<String>();
		Vector<BehaviorInfo> safetiesViolated = node.violatedSafeties;
		for (int i = 0; i < safetiesViolated.size(); i++) {
			res.add(Tracer.getNiceStringForId(safetiesViolated.get(i).traceId));
		}
		
		log.log(Level.FINER, "END. res = {0}", res);
		return res;
	}
	
//	public boolean validate() {
//		log.fine("START");
//		BDD graphInit = nodes.get(0).nodeBDD.id();
//		BDD graphTrans = Env.FALSE();
//		Iterator<Map.Entry<Integer, JVGNode>> nodesItr = nodes.entrySet().iterator();
//		while (nodesItr.hasNext()) {
//			Map.Entry<Integer, JVGNode> elem = (Map.Entry<Integer, JVGNode>) nodesItr.next();
//			if (elem.getKey() == 0) {
//				continue;
//			}
//			graphTrans = graphTrans.or(elem.getValue().transitions);
//		}
//		
//		SymbolicController sym = new SymbolicController();
//		sym.setInit(graphInit.id());
//		sym.setTrans(graphTrans.id());
//		GameModel m = new GameModel();
//		m.setEnv(this.env);
//		m.setSys(this.sys);
//		if (!SymbolicControllerChecker.checkCompletenessForSys(sym, m)) {
//			throw new RuntimeException();
//		}
//
//		boolean res = SymbolicControllerChecker.checkRabinSpec(sym, m);
//		sym.free();
//
//		graphInit.free();
//		graphTrans.free();
//
//		log.fine("END");
//		return res;
//	}

	// helper method - for debugging
	@SuppressWarnings("unused")
	private BDD invariantsToBDD(ArrayList<Map.Entry<String, String>> invariants) {
		BDD res = Env.TRUE();
		for (int i = 0; i  < invariants.size(); i++) {
			String var = invariants.get(i).getKey();
			String val = invariants.get(i).getValue();
			
			JVGInvariant inv = convertStringsToInvariant(var, val);
			res = res.and(inv.invariant);
		}
		
		return res;
	}
	
	public PlayerModule getEnv() {
		return env;
	}
	
	public PlayerModule getSys() {
		return sys;
	}	
	
	public HashMap<Integer, JVGNode> getNodes() {
		return nodes;
	}
	
	public HashMap<Integer, Vector<JVGEdge>> getEdges() {
		return edges;
	}
	
}
 