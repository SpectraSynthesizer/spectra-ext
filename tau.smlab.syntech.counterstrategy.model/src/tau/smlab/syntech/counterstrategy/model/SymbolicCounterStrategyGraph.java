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


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import net.sf.javabdd.BDD;
import tau.smlab.syntech.counterstrategy.model.JusticeViolationGraph.AssumptionSatNode;
import tau.smlab.syntech.counterstrategy.model.JusticeViolationGraph.AssumptionSatisfactionGraph;
import tau.smlab.syntech.jtlv.Env;


public class SymbolicCounterStrategyGraph {
	private static final Logger log = Logger.getLogger(SymbolicCounterStrategyGraph.class.getName());

	JusticeViolationGraph jvg;
	boolean isRealizable;
	
	public SymbolicCounterStrategyGraph(JusticeViolationGraph jvGraph, Level logLevel) {
		LogManager lm = LogManager.getLogManager();
		
		log.setUseParentHandlers(false);
//		SimpleFormatter fmt = new SimpleFormatter();
//		StreamHandler sh = new StreamHandler(System.out, fmt);
//		sh.setLevel(Level.ALL);
		log.setLevel(logLevel);
	//	log.addHandler(sh);
		Handler[] handler = lm.getLogger(JusticeViolationGraph.class.getName()).getHandlers();
		log.addHandler(handler[0]);
		lm.addLogger(log);
		jvg = jvGraph;
	}
	
	public SymbolicCounterStrategyGraph(JusticeViolationGraph jvGraph) {
		this(jvGraph, Level.OFF);
	}
	
	/**
	 * <p>
	 * Will return the symbolic graph in a GraphStruct form, described below.
	 * Will be used by the View module to compute and get the symbolic graph in
	 * 2 cases:
	 * <ul>
	 * <li>When the user selects "Generate Symbolic Counter Strategy" in the
	 * actions screen. </li>
	 * <li>When the user selects "Generate Concrete Counter Strategy" in the
	 * actions screen. In this case, after calling this method, the View module
	 * will need to call {@link #getInitialStates} and {@link #performConcreteStep} to get the
	 * parts of the graph to display.</li>
	 * </ul></p>
	 * 
	 * @return symbolic graph.
	 */
	public CounterStrategyGraphStruct getSymbolicGraph() {
		log.fine("START");
		CounterStrategyGraphStruct graph = new CounterStrategyGraphStruct();
		
		graph.type = CounterStrategyGraphStruct.CSType.SYMBOLIC;
		Iterator<Map.Entry<Integer, JVGNode>> itr = jvg.nodes.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<Integer, JVGNode> entry = (Map.Entry<Integer, JVGNode>) itr.next();
			CounterStrategyGraphStruct.CSNode node = convertNodeToGraphStruct(entry.getKey(), 
					entry.getValue(), false /*isConcrete*/);
			graph.nodes.put(entry.getKey(), node);
			// add out going edges for this node
			if (!jvg.edges.containsKey(entry.getKey())) {
				log.fine("JVG does not contain out going edges for node: " + entry.getKey());
				continue;
			}
			Vector<JVGEdge> edges = jvg.edges.get(entry.getKey());
			HashMap<Integer, CounterStrategyGraphStruct.CSEdge> outEdges =
					new HashMap<Integer, CounterStrategyGraphStruct.CSEdge>();
			for (int i = 0; i < edges.size(); i++) {
				CounterStrategyGraphStruct.CSEdge edge = convertEdgeToGraphStruct(
						entry.getKey(), edges.get(i),
						CounterStrategyGraphStruct.CSEdgeType.BETWEEN_NODES, false /*isConcrete*/);
				outEdges.put(edges.get(i).destInd, edge);
			}
			graph.edges.put(entry.getKey(), outEdges);
		}
				
		log.fine("END");
		return graph;
	}
	
	private CounterStrategyGraphStruct.CSNode convertNodeToGraphStruct(int index, JVGNode jvgNode,
			boolean isConcrete) {
		log.log(Level.FINE, "START. index = " + index + ", jvgNode = {0}, isConcrete = " + isConcrete, jvgNode);
		CounterStrategyGraphStruct.CSNode resNode = new CounterStrategyGraphStruct.CSNode();
		resNode.indexSymbolic = index;
		resNode.isConcrete = isConcrete;
		resNode.type = convertNodeTypeToGraphStructType(jvgNode.type);
		resNode.violatedJustice = jvg.getViolatedJustice(jvgNode);
		if (!jvgNode.invariants.isEmpty()) {
			for (int i = 0; i < jvgNode.invariants.size(); i++) {
				CounterStrategyGraphStruct.CSInvariant invariant = 
						convertInvariantToGraphStructInvariant(jvgNode.invariants.get(i));
				resNode.invariants.add(invariant);
			}
		}
		
		if (jvgNode.type == JVGNodeType.CYCLE_NODE && !isConcrete) {
			resNode.assumptionsGraph = convertAssumptionGraphToGraphStruct(jvgNode.assumpGraph);
		}
		
		resNode.violatedSafeties = jvg.getViolatedSafeties(jvgNode);
		
		log.fine("End. resNode = " + resNode.toString());
		return resNode;
	}
	
	private CounterStrategyGraphStruct.CSAssumptionSatGraph convertAssumptionGraphToGraphStruct(
			AssumptionSatisfactionGraph jvgAssumpGraph) {
		log.log(Level.FINE, "START. jvgAssumpGraph = {0}", jvgAssumpGraph);
		
		CounterStrategyGraphStruct.CSAssumptionSatGraph graph = new
				CounterStrategyGraphStruct.CSAssumptionSatGraph();
		
		Iterator<Map.Entry<Integer, AssumptionSatNode>> itr = jvgAssumpGraph.nodes.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<Integer, AssumptionSatNode> entry =
					(Map.Entry<Integer, AssumptionSatNode>) itr.next();
			
			CounterStrategyGraphStruct.CSAssumptionNode assumpNode =
					convertAssumptionNodeToCSStruct(entry.getValue());
			
			graph.nodes.put(entry.getKey(), assumpNode);
		}
		
		Iterator<Map.Entry<Integer, Vector<JVGEdge>>> edgesIter =
				jvgAssumpGraph.edges.entrySet().iterator();
		while (edgesIter.hasNext()) {
			Map.Entry<Integer, Vector<JVGEdge>> entry =
					(Map.Entry<Integer, Vector<JVGEdge>>) edgesIter.next();
			// there should only be (at most) a single outgoing edge for each node in this
			// graph
			if (entry.getValue().size() == 0) {
				continue;
			}
			if (entry.getValue().size() > 1) {
				log.severe("More than 1 outgoing edge for node: " + entry.getKey());
			}
			CounterStrategyGraphStruct.CSEdge edge =
					convertEdgeToGraphStruct(entry.getKey(), entry.getValue().get(0),
							CounterStrategyGraphStruct.CSEdgeType.INSIDE_NODE, false /*isConcrete*/);
			HashMap<Integer, CounterStrategyGraphStruct.CSEdge> temp =
					new HashMap<Integer, CounterStrategyGraphStruct.CSEdge>();
			temp.put(entry.getValue().get(0).destInd, edge);
			graph.edges.put(entry.getKey(), temp);
		}
		
		log.fine("END - graph = " + graph);
		return graph;
	}
	
	private CounterStrategyGraphStruct.CSAssumptionNode convertAssumptionNodeToCSStruct(
			AssumptionSatNode assumpSatNode) {
		log.log(Level.FINE, "START. assumpSatNode = {0}", assumpSatNode);
		
		CounterStrategyGraphStruct.CSAssumptionNode res =
				new CounterStrategyGraphStruct.CSAssumptionNode();
		res.assumpInd = assumpSatNode.assumptionInd;
		res.satisfiedJustice = jvg.getSatisfiedAssumptionJustice(assumpSatNode);
		
		ArrayList<JVGInvariant> invs = jvg.getInvariantsFromBDD(assumpSatNode.nodeBDD);
		for (int i = 0; i < invs.size(); i++) {
			CounterStrategyGraphStruct.CSInvariant inv =
					convertInvariantToGraphStructInvariant(invs.get(i));
			res.invariants.add(inv);
		}
		
		log.fine("END. res = " + res);
		return res;
	}
	
	private CounterStrategyGraphStruct.CSNodeType convertNodeTypeToGraphStructType(
			JVGNodeType type) {
		log.finer("START. type = " + type);
		CounterStrategyGraphStruct.CSNodeType resType = CounterStrategyGraphStruct.CSNodeType.UNKNOWN;
		switch (type) {
		case UNKNOWN_NODE:
			resType = CounterStrategyGraphStruct.CSNodeType.UNKNOWN;
			break;
		case INIT_NODE:
			resType = CounterStrategyGraphStruct.CSNodeType.INITIAL;
			break;
		case CYCLE_NODE:
			resType = CounterStrategyGraphStruct.CSNodeType.CYCLE;
			break;
		case ATTRACTOR_NODE_FROM_CYCLE:
		case ATTRACTOR_NODE_NOT_FROM_CYCLE:
			resType = CounterStrategyGraphStruct.CSNodeType.ATTRACTOR;
			break;
		}
		
		log.finer("END. resType = " + resType);
		return resType;
	}
	
	private CounterStrategyGraphStruct.CSInvariant convertInvariantToGraphStructInvariant(
			JVGInvariant invariant) {		
		ArrayList<String> inv = jvg.convertInvariantToStrings(invariant);
		
		log.log(Level.FINER, "invariant = {0}", invariant);
		String var = inv.get(0);
		String val = inv.get(1);

		CounterStrategyGraphStruct.CSInvariant resInvariant = 
				new CounterStrategyGraphStruct.CSInvariant(var, val);
		
		if (inv.size() > 2 && inv.get(2).equals("aux")) {
			resInvariant.isDisplayed = false;
		}
		resInvariant.bdd = invariant.invariant;
		log.finer("resInvariant = " + resInvariant.toString());
		return resInvariant;
	}
	
	private CounterStrategyGraphStruct.CSEdge convertEdgeToGraphStruct(int fromInd, JVGEdge jvgEdge,
			CounterStrategyGraphStruct.CSEdgeType type, boolean isConcrete) {
		log.log(Level.FINE, "START. edge {0}", jvgEdge);
		CounterStrategyGraphStruct.CSEdge resEdge = new CounterStrategyGraphStruct.CSEdge();
		resEdge.fromNode = fromInd;
		resEdge.toNode = jvgEdge.destInd;
		resEdge.type = type;
		resEdge.isConcrete = isConcrete;
		if (!jvgEdge.invariants.isEmpty()) {
			for (int i = 0; i < jvgEdge.invariants.size(); i++) {
				CounterStrategyGraphStruct.CSInvariant invariant = 
						convertInvariantToGraphStructInvariant(jvgEdge.invariants.get(i));
				resEdge.invariants.add(invariant);
			}
		}
		
		log.fine("End. resEdge = " + resEdge.toString());
		return resEdge;		
	}
	
	/**
	 * Will return a CounterStartegyGraphStruct which is comprised of the
	 * concrete states in the <em>currNode</em> that are reachable in a single step from
	 * a node in the list of <em>leadingNodes</em>. If <em>leadingNodes</em> is empty, the
	 * states returned are those that are reachable in a single step from the
	 * initial node (i.e. the initial states) inside the current node.
	 * If the currNode is the initial symbolic node, this method returns all of the concrete
	 * initial states.
	 * <p>
	 * Will be used by the View module to get the starting concrete states on
	 * which to later perform the {@link #performConcreteStep} calls in the following
	 * scenarios:
	 * <ul>
	 * <li>When the user clicked on "Generate Concrete Counter Strategy".</li>
	 * <li>When the user chose to concretize a symbolic node.</li>
	 * </ul></p>
	 * Notice that the states in the returned graph may have connections between each other.
	 * 
	 * @param currNode
	 * @param leadingNodes
	 * @return
	 */
	public CounterStrategyGraphStruct getInitialStates(CounterStrategyGraphStruct.CSNode currNode,
			ArrayList<CounterStrategyGraphStruct.CSNode> leadingNodes) {
		// This method gets the current symbolic node and a list of symbolic nodes that have
		// edges leading to this node. It returns a graph of the concrete states in the currNode
		// for which there's an incoming edge from some node in the leading nodes
		log.info("START. currNode = " + currNode.toString());
		CounterStrategyGraphStruct graph = new CounterStrategyGraphStruct();
		graph.type = CounterStrategyGraphStruct.CSType.CONCRETE;

		int concreteNodeInd = 0;
		log.fine("leadningNodes.size = " + leadingNodes.size());
		if (currNode.type == CounterStrategyGraphStruct.CSNodeType.INITIAL) {
			log.fine("handling initial symbolic node - getting the initial concrete states");
			HashMap<Integer, ArrayList<BDD>> initialConcreteStates =
					jvg.getInitialConcreteStates();
			// the initial states should have symbolic index in them which is identical to
			// the symbolic node in which the state is actually located (and not the "0" which
			// represents the marker initial symbolic node)
			Iterator<Map.Entry<Integer, ArrayList<BDD>>> itr =
					initialConcreteStates.entrySet().iterator();
			while (itr.hasNext()) {
				Map.Entry<Integer, ArrayList<BDD>> entry =
						(Map.Entry<Integer, ArrayList<BDD>>) itr.next();
				
				log.finer("initialConcreteStates.size == " + entry.getValue().size());
				CounterStrategyGraphStruct.CSNode currSymNode =
						convertNodeToGraphStruct(entry.getKey(), jvg.nodes.get(entry.getKey()),
								false /*isConcrete*/);
				addNodesToConcreteGraph(graph, entry.getValue(), currSymNode, concreteNodeInd);
				concreteNodeInd += initialConcreteStates.size();
			}
		} else {
			for (int i = 0; i < leadingNodes.size(); i++) {
				ArrayList<BDD> concreteStates = jvg.getConcreteStates(leadingNodes.get(i).indexSymbolic,
						currNode.indexSymbolic);
				log.finer("concreteStates.size = " + concreteStates.size());
				addNodesToConcreteGraph(graph, concreteStates, currNode, concreteNodeInd);
				concreteNodeInd += concreteStates.size();
			}
		}
		
		log.info("END, graph = " + graph.toString());
		return graph;
	}

	private void addNodesToConcreteGraph(CounterStrategyGraphStruct graph, ArrayList<BDD> states,
			CounterStrategyGraphStruct.CSNode symbolicNode, int stateInd) {
		log.fine("START");

		for (int j = 0; j < states.size(); j++) {
			log.log(Level.FINER, "states[" + j + "] = {0}",
					new BddStringWrapper(states.get(j)));
			CounterStrategyGraphStruct.CSNode node = 
					constructCSNodeFromBDD(symbolicNode, states.get(j));
			graph.nodes.put(stateInd, node);
			stateInd++;
			// no edges incoming for the initial states
		}

		log.fine("END");
	}
	
	private CounterStrategyGraphStruct.CSNode constructCSNodeFromBDD(
			CounterStrategyGraphStruct.CSNode currNode,
			BDD bdd) {
		log.log(Level.FINE, "START - currNode = " + currNode.toString()
				+ ", bdd = {0}", new BddStringWrapper(bdd));
		CounterStrategyGraphStruct.CSNode resNode = new CounterStrategyGraphStruct.CSNode();
		resNode.isConcrete = true;
		resNode.type = currNode.type;
		resNode.violatedJustice = currNode.violatedJustice;
		resNode.indexSymbolic = currNode.indexSymbolic;
		
		ArrayList<JVGInvariant> invs = jvg.getInvariantsFromBDD(bdd);
		for (int i = 0; i < invs.size(); i++) {
			CounterStrategyGraphStruct.CSInvariant inv =
					convertInvariantToGraphStructInvariant(invs.get(i));
			resNode.invariants.add(inv);
		}
		
		log.fine("END. resNode = " + resNode.toString());
		return resNode;
	}
	
	/**
	 * Will return the sub-graph representing the neighbors and edges of the
	 * node given as input parameter.<br>
	 * Will be used by the View module to get the concrete neighbors of a given
	 * node (the node is in fact concrete, represented by having all variables
	 * assigned values in the "invariants" list).<br>
	 * Notice that the states in the returned graph may have connections between each other.
	 * 
	 * @param currState
	 * @return sub-graph representing the neighbors and edges of
	 *         <em>currState</em>.
	 */
	public CounterStrategyGraphStruct performConcreteStep(CounterStrategyGraphStruct.CSNode currState) {
		log.info("START - currState " + currState.toString());
		CounterStrategyGraphStruct graph = new CounterStrategyGraphStruct();
		graph.type = CounterStrategyGraphStruct.CSType.CONCRETE;
		int concreteInd = 0;
		graph.nodes.put(concreteInd, currState);
		
		BDD currBDD = getBDDFromCSNode(currState);
		HashMap<Integer, CounterStrategyGraphStruct.CSEdge> currNodeOutEdges =
				new HashMap<Integer, CounterStrategyGraphStruct.CSEdge>();
		
		log.log(Level.FINE, "currBDD = {0}", new BddStringWrapper(currBDD));
		ArrayList<Map.Entry<Integer, Map.Entry<JVGNode, JVGEdge>>> nodesAndEdges =
				new ArrayList<Map.Entry<Integer, Map.Entry<JVGNode, JVGEdge>>>();
		jvg.getNextStates(currState.indexSymbolic, currBDD, nodesAndEdges);
		concreteInd++;
		for (int i = 0; i < nodesAndEdges.size(); i++) {
			int symbolicIndex = nodesAndEdges.get(i).getKey();
			JVGNode jvgNode = nodesAndEdges.get(i).getValue().getKey();
			JVGEdge jvgEdge = nodesAndEdges.get(i).getValue().getValue();
			// adding the node
			CounterStrategyGraphStruct.CSNode node = convertNodeToGraphStruct(symbolicIndex, jvgNode,
					true /*isConcrete*/);
			graph.nodes.put(concreteInd, node);
			
			// adding the edge
			CounterStrategyGraphStruct.CSEdgeType edgeType =
					(currState.indexSymbolic == symbolicIndex ? 
							CounterStrategyGraphStruct.CSEdgeType.INSIDE_NODE :
							CounterStrategyGraphStruct.CSEdgeType.BETWEEN_NODES);
			CounterStrategyGraphStruct.CSEdge edge = convertEdgeToGraphStruct(
					0 /*the given state concreteIndex*/, jvgEdge, edgeType, true /*isConcrete*/);
			currNodeOutEdges.put(concreteInd, edge);
			concreteInd++;
		}
		
		graph.edges.put(0 /*the given state concreteIndex*/, currNodeOutEdges);
		
		log.info("END, graph = " + graph.toString());
		return graph;
	}
	
	/**
	 * Will return a list of variable names which are relevant to the current
	 * given node.<br>
	 * Will be used by the View module when the user chooses to perform
	 * interactive play to get the list of relevant variables for the current
	 * node. For each new assignment added by the user, the list will be
	 * internally updated by the View module (removing from it the already
	 * assigned variables), no need to re-call this API to get a new variable
	 * list.
	 * 
	 * @param node
	 * @return List of variables (not including node invariant variables), with each variable having
	 * a flag isAux - indicating if it is an aux variable
	 */
	public ArrayList<Map.Entry<String, Boolean>> getVarList(CounterStrategyGraphStruct.CSNode node) {
		log.info("START. node = " + node.toString());
		
		ArrayList<Map.Entry<String, Boolean>> vars = jvg.getAllVars();
		ArrayList<Map.Entry<String, Boolean>> res = new ArrayList<Map.Entry<String, Boolean>>();
		// only return variables which are not an invariant for this node
		for (int i = 0; i < vars.size(); i++) {
			boolean foundVar = false;
			for (int j = 0; j < node.invariants.size(); j++) {
				if (node.invariants.get(j).var.compareTo(vars.get(i).getKey()) == 0) {
					foundVar = true;
					break;
				}
			}
			if (!foundVar) {
				res.add(vars.get(i));
			}
		}
		
		System.out.println("res = " + res.toString());
		log.info("END. res = " + res.toString());
		return res;
	}
	
	/***
	 * Will return a list of variable names which are relevant successors of the given
	 * predecessor.
	 * Will be used by the View module when the user performs a concrete step in interactive
	 * play, and the number of neighbors is too large with regards to a values configured by
	 * the user. 
	 * For each new assignment added by the user, the list will be
	 * internally updated by the View module (removing from it the already
	 * assigned variables), no need to re-call this API to get a new variable
	 * list.
	 * @param predecessor - the concrete predecessor.
	 * @param succInvariants - list of the successors invariants (the initial invariants,
	 * before the user began to assign values to variables)  
	 * @return a list of variable names which are relevant to the successors of the given
	 * concrete predecessor, with each variable having a flag - isAux (is it auxiliary variable)
	 */
	public ArrayList<Map.Entry<String, Boolean>>  getSuccessorVarList(ArrayList<CounterStrategyGraphStruct.CSInvariant> succInvariants) {
		log.log(Level.INFO, "START - succInvariants = " + succInvariants);
		
		ArrayList<Map.Entry<String, Boolean>> vars = jvg.getAllVars();
		ArrayList<Map.Entry<String, Boolean>> res = new ArrayList<Map.Entry<String, Boolean>>();
		// only return variables which are not an invariant for the successors
		for (int i = 0; i < vars.size(); i++) {
			boolean foundVar = false;
			for (int j = 0; j < succInvariants.size(); j++) {
				if (succInvariants.get(j).var.compareTo(vars.get(i).getKey()) == 0) {
					foundVar = true;
					break;
				}
			}
			if (!foundVar) {
				res.add(vars.get(i));
			}
		}
		
		log.log(Level.INFO, "END - res = " + res);
		return res;
	}
	
	/**
	 * Will return a list of valid variable values for the given node and variable.<br>
	 * Will be called each time the user selected a variable to assign during
	 * the "interactive play" pop up window. The input node will contain all
	 * assignments which are the invariants of the node and the assignments
	 * already selected by the user.
	 * 
	 * @param node
	 * @param var
	 * @return list of valid variable values.
	 */
	public ArrayList<String> getValueList(CounterStrategyGraphStruct.CSNode node, String var) {
		log.info("START. node = " + node.toString() + ", var = " + var);

		BDD currBDD = getBDDFromCSNode(node);
		ArrayList<String> res = jvg.getNodeVarValues(node.indexSymbolic, currBDD, var);
		log.info("END. res = " + res.toString());
		return res;
	}	
	
	/**
	 * Will return a list of valid variable values for the successors of a given predecessor and variable.<br>
	 * Will be called each time the user selected a variable to assign during
	 * the concrete step pop up window. The input node will contain all
	 * assignments which are the invariants of the node and the assignments
	 * already selected by the user.
	 * 
	 * @param node - the node contains the assignments already selected by the user.
	 * Will not (!) contain the invariants of the current symbolic node, since
	 * the successor states might be in other symbolic nodes. 
	 * @param predecessor - a concrete state which is the predecessor of the states in which we
	 * check for the values of a variable
	 * @param var - the variable for which to get the values.
	 * @return list of valid variable values.
	 */
	public ArrayList<String> getSuccessorValueList(CounterStrategyGraphStruct.CSNode node,
			CounterStrategyGraphStruct.CSNode predecessor, String var) {
		log.info("START. node = " + node.toString() + ", predecessor = " +
			predecessor + ", var = " + var);

		BDD predBDD = getBDDFromCSNode(predecessor);
		BDD nodeBDD = getBDDFromCSNode(node);

		ArrayList<String> res = jvg.getSuccessorVarValues(predecessor.indexSymbolic, nodeBDD, predBDD, var);
		log.info("END. res = " + res.toString());
		return res;
	}	
	
	/**
	 * Will return a concrete state valid for the given node. It is possible
	 * that the result will be identical to the input node, if the input node
	 * already represents a concrete state.
	 * 
	 * @param node
	 * @return a concrete state valid for <em>node</em>.
	 */
	public CounterStrategyGraphStruct.CSNode getConcreteState(CounterStrategyGraphStruct.CSNode node) {
		log.info("START - node = " + node.toString());
		
		BDD currBDD = getBDDFromCSNode(node);
		Map.Entry<Integer, BDD> concreteState = jvg.getConcreteState(node.indexSymbolic, currBDD);
		CounterStrategyGraphStruct.CSNode symNode = node;
		if (concreteState.getKey() != node.indexSymbolic) {
			// This state is an initial state, so its symbolic index is different than "0",
			// which is the marker initial symbolic node
			symNode = convertNodeToGraphStruct(concreteState.getKey(),
					jvg.nodes.get(concreteState.getKey()), false /*isConcrete*/);
		}
		CounterStrategyGraphStruct.CSNode res = constructCSNodeFromBDD(symNode,
				concreteState.getValue());
		log.info("END - res = " + res.toString());
		return res;
	}

	/**
	 * Will return a concrete state valid for the given node (which contains the assignments
	 * chosen so far by the user) and which is a successor of the given predecessor.
	 * It is possible that the result will be identical to the input node value, if the input node
	 * already represents a concrete state.
	 * 
	 * @param node - the collection of assignments chosen so far by the user.
	 * @param predecessor - a concrete state.
	 * @return a concrete state valid for <em>node</em>, which is also a successor for predecessor.
	 */
	public CounterStrategyGraphStruct.CSNode getConcreteSuccessorState(
			CounterStrategyGraphStruct.CSNode node, CounterStrategyGraphStruct.CSNode predecessor) {
		log.info("START - node = " + node.toString() + ", predecessor = " + predecessor);
		
		BDD currBDD = getBDDFromCSNode(node);
		BDD predBDD = getBDDFromCSNode(predecessor);
		Map.Entry<Integer, BDD> concreteState = jvg.getSuccessorConcreteState(predecessor.indexSymbolic, currBDD, predBDD);
		CounterStrategyGraphStruct.CSNode successorNode = convertNodeToGraphStruct(concreteState.getKey(),
				jvg.nodes.get(concreteState.getKey()), true /*isConcrete*/);
		CounterStrategyGraphStruct.CSNode res = constructCSNodeFromBDD(successorNode, concreteState.getValue());
		log.info("END - res = " + res.toString());
		return res;
	}

	BDD getBDDFromCSNode(CounterStrategyGraphStruct.CSNode node) {
		BDD currBDD = Env.TRUE();
		for (int i = 0; i < node.invariants.size(); i++) {
			JVGInvariant inv = jvg.convertStringsToInvariant(node.invariants.get(i).var,
					node.invariants.get(i).val);
			currBDD = currBDD.and(inv.invariant);
		}
		
		return currBDD;
	}
	
	/***
	 * This method returns the number of neighbors of a given state.
	 * It should be used by the view when user requests to perform interactive play, to
	 * determine if need to get all the neighbors or to open a dialog window to determine
	 * specific neighbor to display.
	 * 
	 * @param node
	 * @return int - number of neighbors of given state.
	 */
	public int numStateNeighbors(CounterStrategyGraphStruct.CSNode node) {
		log.log(Level.INFO, "START - node = " + node);
		
		BDD currBDD = getBDDFromCSNode(node);
		int numNeighbors = jvg.countNumNeighbors(node.indexSymbolic, currBDD);
		
		log.info("END - numNeighbors = " + numNeighbors);
		return numNeighbors;
	}
	
	/***
	 * This method gets a node and returns the list of variables and values for variables
	 * for which there is only a single possible value
	 * @param node
	 * @return list of CSInvariants (variables and values) for variables that only have a
	 * single possible value, not including invariants the already exist in the current symbolic node.
	 */
	public ArrayList<CounterStrategyGraphStruct.CSInvariant> getVarsAndVals(
			CounterStrategyGraphStruct.CSNode node) {
		log.log(Level.INFO, "START - node = " + node);
		
		ArrayList<CounterStrategyGraphStruct.CSInvariant> res =
				new ArrayList<CounterStrategyGraphStruct.CSInvariant>();
		BDD currBDD = getBDDFromCSNode(node);
		ArrayList<JVGInvariant> jvgInvariants = jvg.getNodeVarsAndVals(node.indexSymbolic,
				currBDD);
		
		for (int i = 0; i < jvgInvariants.size(); i++) {
			CounterStrategyGraphStruct.CSInvariant inv = convertInvariantToGraphStructInvariant(jvgInvariants.get(i));
			if (!node.invariants.contains(inv)) {
				log.fine("node doesn't contain invariant: " + inv);
				res.add(inv);
			}
		}
		
		log.info("END - res = " + res);
		return res;
	}
	
	
	/***
	 * This method gets a node which contains the assignments chosen by the user so far, and
	 * a predecessor which is a concrete state in the current interactive play step,
	 * and returns the list of variables and values for variables for which there is only a 
	 * single possible value.
	 * Note: This method can be used both for getting the list of invariants when starting
	 * to perform a concrete step (i.e. invariants of all successors) by simply passing the
	 * "node" parameter with an empty list of invariants, and for getting the list of
	 * new "single-choice" variables during the process of selecting a successor (by
	 * having the "node" parameter contain all variables and values selected by the user so far).
	 * @param node - containing assignments chosen by user so far. Doesn't contain the symbolic
	 * node invariants.
	 * @param predecessor - a concrete state which is the predecessor in the current interactive
	 * play step user is performing.
	 * @return list of CSInvariants (variables and values) for variables that only have a
	 * single possible value, including (!) invariants that already exist in the predecessor symbolic node - since
	 * the successors might not be in the same symbolic node.
	 */
	public ArrayList<CounterStrategyGraphStruct.CSInvariant> getSuccessorVarsAndVals(
			CounterStrategyGraphStruct.CSNode node, CounterStrategyGraphStruct.CSNode predecessor) {
		log.log(Level.INFO, "START - node = " + node + ", predecessor = " + predecessor);
		
		ArrayList<CounterStrategyGraphStruct.CSInvariant> res =
				new ArrayList<CounterStrategyGraphStruct.CSInvariant>();
		BDD currBDD = getBDDFromCSNode(node);
		BDD predBDD = getBDDFromCSNode(predecessor);
		ArrayList<JVGInvariant> jvgInvariants = jvg.getSuccessorVarsAndVals(predecessor.indexSymbolic,
				currBDD, predBDD);
		
		for (int i = 0; i < jvgInvariants.size(); i++) {
			CounterStrategyGraphStruct.CSInvariant inv = convertInvariantToGraphStructInvariant(jvgInvariants.get(i));
			if (!node.invariants.contains(inv)) {
				res.add(inv);
			}
		}
		
		log.info("END - res = " + res);
		return res;
	}
	
	public void mergeAttractors() {
		log.info("START");
		jvg.mergedAttractors();
		log.info("END");
	}

	public JusticeViolationGraph getJvg() {
		return jvg;
	}
	
//	public boolean validate() {
//		log.info("START");
//		boolean res = jvg.validate();
//		log.info("END - res = " + res);
//		return res;
//	}
}
