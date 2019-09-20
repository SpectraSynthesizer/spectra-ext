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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class CounterStrategyGraphStruct {
	enum CSType {
		NOT_INIT, SYMBOLIC, CONCRETE
	}

	public enum CSNodeType {
		UNKNOWN, INITIAL, CYCLE, ATTRACTOR
	}
	
	static public class CSAssumptionSatGraph {
		/**
		 * HashMap of Integer to CSAssumptionNode, representing the different nodes.
		 * <br>The Integer is the index of the node. There is no initial node (this graph
		 * is a cycle).
		 */
		public HashMap<Integer, CSAssumptionNode> nodes;
		/**
		 * HashMap of Integer to a HashMap of Integer to CSEdge. <br>The first Integer
		 * is the index of the "from" node and the second Integer is the index of the
		 * "to" node. The CSEdge is the edge object between the two nodes.
		 */
		public HashMap<Integer, HashMap<Integer, CSEdge>> edges;
		
		public CSAssumptionSatGraph() {
			nodes = new HashMap<Integer, CSAssumptionNode>();
			edges = new HashMap<Integer, HashMap<Integer, CSEdge>>();
		}
		
		public String toString() {
			String str = "\nAssumptionSatisfcationGraph:\n";
			str +=       "---------------------------\n";
			str += "ASSUMP NODES: " + nodes.size() + " nodes";
			Iterator<Map.Entry<Integer, CSAssumptionNode>> iter = nodes.entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry<Integer, CSAssumptionNode> elem = (Map.Entry<Integer, CSAssumptionNode>) iter.next();
				str += "\nnode of index " + elem.getKey() + ":\n" + elem.getValue().toString();
			}
			
			str += "\nASSUMP EDGES:";
			if (edges.size() == 0) {
				str += "no edges";
				return str;
			}
			
			Iterator<Map.Entry<Integer, HashMap<Integer, CSEdge>>> edgesIter = edges.entrySet().iterator();
			while (edgesIter.hasNext()) {
				Map.Entry<Integer, HashMap<Integer, CSEdge>> entry =
						(Map.Entry<Integer, HashMap<Integer, CSEdge>>) edgesIter.next();
				Iterator<Map.Entry<Integer, CSEdge>> singleNodeEdges = entry.getValue().entrySet().iterator();
				while (singleNodeEdges.hasNext()) {
					Map.Entry<Integer, CSEdge> edgeEntry = (Map.Entry<Integer, CSEdge>) singleNodeEdges.next();
					str += "\nedge from " + entry.getKey() + " to " + edgeEntry.getKey() + ":\n" +
							edgeEntry.getValue();
				}
			}
			
			return str;			
		}
		
		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#clone()
		 */
		@Override
		public CSAssumptionSatGraph clone() {
			CSAssumptionSatGraph newGraph = new CSAssumptionSatGraph();
			Iterator<Map.Entry<Integer, CSAssumptionNode>> nodesItr = this.nodes.entrySet().iterator();
			while (nodesItr.hasNext()) {
				Map.Entry<Integer, CSAssumptionNode> elem = (Map.Entry<Integer, CSAssumptionNode>) nodesItr.next();
				newGraph.nodes.put(elem.getKey(), elem.getValue().clone());
			}
			
			Iterator<Map.Entry<Integer, HashMap<Integer, CSEdge>>> edgesItr = this.edges.entrySet().iterator();
			while (edgesItr.hasNext()) {
				Map.Entry<Integer, HashMap<Integer, CSEdge>> temp = (Map.Entry<Integer, HashMap<Integer,CSEdge>>) edgesItr.next();
				Iterator<Map.Entry<Integer, CSEdge>> itr = temp.getValue().entrySet().iterator();
				while (itr.hasNext()) {
					Map.Entry<Integer, CSEdge> elem = (Map.Entry<Integer, CSEdge>) itr.next();
					if (!newGraph.edges.containsKey(temp.getKey())) {
						newGraph.edges.put(temp.getKey(), new HashMap<Integer, CSEdge>());
					}
					newGraph.edges.get(temp.getKey()).put(elem.getKey(), elem.getValue().clone());
				}
			}

			return newGraph;
		}

	}

	static public class CSAssumptionNode {
		/**
		 * An integer representing the assumption being satisfied
		 */
		public int assumpInd;
		/**
		 * A string representing the assumption justice satisfied in this node.
		 */
		public String satisfiedJustice;

		
		/**
		 * The invariants of this node
		 */
		public ArrayList<CSInvariant> invariants;
		
		public CSAssumptionNode() {
			this.assumpInd = -1;
			this.invariants = new ArrayList<CSInvariant>();
		}
		
		public String toString() {
			String str = "assumpInd = " + assumpInd;
			str += "\nsatisfiedJustice = " + satisfiedJustice;
			for (int i = 0; i < invariants.size(); i++) {
				str += "\ninvariant[" + i + "] = " + invariants.get(i).toString();
			}
			
			str += "\n";
			return str;
		}
		
		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#clone()
		 */
		@Override
		public CSAssumptionNode clone() {
			CSAssumptionNode newNode = new CSAssumptionNode();
			newNode.invariants = new ArrayList<CSInvariant>(this.invariants.size());
			for (CSInvariant invariant : this.invariants) {
				newNode.invariants.add(invariant.clone());
			}
			newNode.assumpInd = this.assumpInd;
		
			return newNode;
		}
	}
	
	/**
	 * The structure representing a single node in the graph.
	 */
	static public class CSNode {
		/**
		 * A string representing the justice violated in this node.
		 */
		public String violatedJustice;
		/**
		 * A list of CSInvariant structs. In the case of a concrete state, this
		 * list will include values for all variables.
		 */
		public ArrayList<CSInvariant> invariants;
		/**
		 * Can be INITIAL, CYCLE, ATTRACTOR. <br>In the case of a concrete state,
		 * this will be the type of the node containing the state.
		 */
		public CSNodeType type;
		/**
		 * the index of this node in the graph.
		 * <br>In case of symbolic node, this is the current node's entry key in
		 * CounterStrategyGraphStruct.nodes HashMap.
		 * <br>In case of concrete state, this is (the entry key in
		 * CounterStrategyGraphStruct.nodes HashMap of) the symbolic node whom
		 * it belongs to.
		 */
		public int indexSymbolic;
		/**
		 * Flag indicating if this is a concrete state or a symbolic node.
		 */
		public boolean isConcrete;
		/**
		 * ArrayList of strings representing violated safeties
		 * Only relevant for isConcrete == false.
		 * No need to use it for comparison between nodes.
		 */
		public ArrayList<String> violatedSafeties;
		/**
		 * The assumption satisfaction graph.
		 * Only relevant if this node type is CYCLE and isConcrete == false.
		 * No need to use it for comparison between nodes.
		 */
		public CSAssumptionSatGraph assumptionsGraph;
		
		public CSNode() {
			invariants = new ArrayList<CSInvariant>();
			violatedSafeties = new ArrayList<String>();
			indexSymbolic = -1;
		}
		
		public String toString() {
			String str = "\nCSNode:\n-------\nindexSymbolic = " + indexSymbolic + "\nisConcrete = "
					+ isConcrete + "\ntype = " + type + "\nviolatedJustice = "
					+ violatedJustice + "\n" + invariants.size()
					+ " invariants";
			for (int i = 0; i < invariants.size(); i++) {
				str += "\ninvariant[" + i + "] = " + invariants.get(i).toString();
			}
			/* TODO - the violated safeties should be one of the following:
			 * 1. Only safeties that the env can force the sys to violate
			 * 2. Core of safeties which can be violated
			 * Currently, they're all safeties that the sys can choose to violate - which is too much.
			 *
			for (int i = 0; i < violatedSafeties.size(); i++) {
				str+= "\nviolated safety[" + i + "] = " + violatedSafeties.get(i);
			}
			if (violatedSafeties.isEmpty()) {
				str+= "\nno safeties are violated";
			}*/
			if (type == CSNodeType.CYCLE && !isConcrete) {
				str += "\nassumtpionsGraph = " + assumptionsGraph.toString();
			}
			
			str += "\n";
			return str;
		}
		
		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + indexSymbolic;
			result = prime * result + ((invariants == null) ? 0 : invariants.size());
			result = prime * result;
			for (CSInvariant element : invariants)
				result += element.hashCode();
			result = prime * result + (isConcrete ? 1231 : 1237);
			result = prime * result + ((type == null) ? 0 : type.hashCode());
			result = prime * result + ((violatedJustice == null) ? 0 : violatedJustice.hashCode());
			return result;
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			CSNode other = (CSNode) obj;
			if (indexSymbolic != other.indexSymbolic)
				return false;
			if (!compareArrayLists(invariants, other.invariants))
				return false;
			if (isConcrete != other.isConcrete)
				return false;
			if (type != other.type)
				return false;
			if (violatedJustice == null) {
				if (other.violatedJustice != null)
					return false;
			} else if (!violatedJustice.equals(other.violatedJustice))
				return false;
			// No need to compare the violated safeties or the assumption graph
			return true;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#clone()
		 */
		@Override
		public CSNode clone() {
			CSNode newNode = new CSNode();
			newNode.violatedJustice = new String(this.violatedJustice);
			newNode.invariants = new ArrayList<CSInvariant>(this.invariants.size());
			for (CSInvariant invariant : this.invariants) {
				newNode.invariants.add(invariant.clone());
			}
			newNode.type = this.type;
			newNode.indexSymbolic = this.indexSymbolic;
			newNode.isConcrete = this.isConcrete;
			if (this.type == CSNodeType.CYCLE) {
				newNode.assumptionsGraph = this.assumptionsGraph.clone();
			}
			return newNode;
		}
		
		
	}
		
	public enum CSEdgeType {
		INSIDE_NODE, BETWEEN_NODES
	}

	/**
	 * The structure representing an edge in the graph.
	 */
	static public class CSEdge {
		/**
		 * index of the source node of this edge (according to the mapping of
		 * CounterStrategyGraphStruct.edges).
		 */
		public int fromNode;
		/**
		 * index of the target node of this edge (according to the mapping of
		 * CounterStrategyGraphStruct.edges).
		 */
		public int toNode;
		/**
		 * a list of CSInvariant structs for this edge.
		 */
		public ArrayList<CSInvariant> invariants;
		/**
		 * Can be either INSIDE_NODE or BETWEEN_NODES. <br>Relevant only to symbolic
		 * graphs and interactive play on symbolic graphs.
		 */
		public CSEdgeType type;
		
		/**
		 * True if this is an edge in a concrete graph
		 */
		public boolean isConcrete;
		
		public CSEdge() {
			invariants = new ArrayList<CSInvariant>();
		}
		
		public String toString() {
			String str = "\nCSEdge:\n------\nfromNode = " + fromNode + "\ntoNode = " + toNode
					+ "\ntype = " + type + "\nisConcrete = " + isConcrete + "\n" + invariants.size()
					+ " invariants";
			
			for (int i = 0; i < invariants.size(); i++) {
				str += "\ninvariant[" + i + "] = " + invariants.get(i).toString();
			}
			
			str += "\n";
			return str;
		}
		
		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#clone()
		 */
		@Override
		public CSEdge clone() {
			CSEdge newEdge = new CSEdge();
			newEdge.invariants = new ArrayList<CSInvariant>(this.invariants.size());
			for (CSInvariant invariant : this.invariants) {
				newEdge.invariants.add(invariant.clone());
			}
			newEdge.fromNode = this.fromNode;
			newEdge.toNode = this.toNode;
			newEdge.type = this.type;
			newEdge.isConcrete = this.isConcrete;
		
			return newEdge;
		}

	}
	
	/**
	 * The structure representing the invariant.
	 * <br>Currently we only support simple invariants of the form "variable = value".
	 */
	static public class CSInvariant {
		/**
		 * the variable to which this invariant applies.
		 */
		public String var;
		/**
		 * the value of the variable according to this invariant.
		 */
		public String val;
		/**
		 * should this variable be shown to the user.
		 */
		public boolean isDisplayed;
		
		public CSInvariant(String newVar, String newVal, boolean newIsDisplayed) {
			var = newVar;
			val = newVal;
			isDisplayed = newIsDisplayed;
		}
		
		public CSInvariant(String newVar, String newVal) {
			this(newVar, newVal, true);
		}
		
		public String toString() {
			String str = "var = " + var + ", val = " + val + ", isDisplayed = " + isDisplayed;
			return str;
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((val == null) ? 0 : val.hashCode());
			result = prime * result + ((var == null) ? 0 : var.hashCode());
			return result;
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass()) // if (!(obj instanceof CSInvariant))
				return false;
			CSInvariant other = (CSInvariant) obj;
			if (val == null) {
				if (other.val != null)
					return false;
			} else if (!val.equals(other.val))
				return false;
			if (var == null) {
				if (other.var != null)
					return false;
			} else if (!var.equals(other.var))
				return false;
			if (isDisplayed != other.isDisplayed)
				return false;
			return true;
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Object#clone()
		 */
		@Override
		public CSInvariant clone() {
			return new CSInvariant(this.var, this.val, this.isDisplayed);
		}
	}
	
	/**
	 * the type of the graph - can be SYMBOLIC or CONCRETE.
	 */
	public CSType type;
	/**
	 * HashMap of Integer to CSNode, representing the different nodes.
	 * <br>The Integer is the index of the node, 0 being the initial node.
	 */
	public HashMap<Integer, CSNode> nodes;
	/**
	 * HashMap of Integer to a HashMap of Integer to CSEdge. <br>The first Integer
	 * is the index of the "from" node and the second Integer is the index of the
	 * "to" node. The CSEdge is the edge object between the two nodes.
	 */
	public HashMap<Integer, HashMap<Integer, CSEdge>> edges;
	
	CounterStrategyGraphStruct() {
		nodes = new HashMap<Integer, CSNode>();
		edges = new HashMap<Integer, HashMap<Integer, CSEdge>>();
	}
	
	public String toString() {
		String str = "\nCounterStrategyGraphStruct:\n";
		str +=       "---------------------------\n";
		str += "NODES: " + nodes.size() + " nodes";
		Iterator<Map.Entry<Integer, CSNode>> iter = nodes.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<Integer, CSNode> elem = (Map.Entry<Integer, CSNode>) iter.next();
			str += "\nnode of index " + elem.getKey() + ":\n" + elem.getValue().toString();
		}
		
		str += "\nEDGES:";
		if (edges.size() == 0) {
			str += "no edges";
			return str;
		}
		
		Iterator<Map.Entry<Integer, HashMap<Integer, CSEdge>>> edgesIter = edges.entrySet().iterator();
		while (edgesIter.hasNext()) {
			Map.Entry<Integer, HashMap<Integer, CSEdge>> entry =
					(Map.Entry<Integer, HashMap<Integer, CSEdge>>) edgesIter.next();
			Iterator<Map.Entry<Integer, CSEdge>> singleNodeEdges = entry.getValue().entrySet().iterator();
			while (singleNodeEdges.hasNext()) {
				Map.Entry<Integer, CSEdge> edgeEntry = (Map.Entry<Integer, CSEdge>) singleNodeEdges.next();
				str += "\nedge from " + entry.getKey() + " to " + edgeEntry.getKey() + ":\n" +
						edgeEntry.getValue();
			}
		}
		
		return str;			
	}

	/**
	 * Comparing (without order) two objects using T.equals().
	 * <br>If one is null and the other isn't - then returns false.
	 * 
	 * @param <T>
	 *            the type of the list's objects
	 * @param first ArrayList to be compared.
	 * @param second ArrayList to be compared.
	 * @return true iff the two arrays contains the same objects.
	 */
	public static <T> boolean compareArrayLists(ArrayList<T> first, ArrayList<T> second) {
		if (first == second)
			return true;
		else if (first == null || second == null)
			return false;
		List<T> sourceList = new ArrayList<T>(first);
		List<T> destinationList = new ArrayList<T>(second);
		sourceList.removeAll(second);
		destinationList.removeAll(first);
		return sourceList.isEmpty() && destinationList.isEmpty();
	}
	
	public int numTransitions() {
		int numTransitions = 0;
		Iterator<Map.Entry<Integer, HashMap<Integer, CSEdge>>> itr =
				this.edges.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<Integer, HashMap<Integer, CSEdge>> entry = 
					(Map.Entry<Integer, HashMap<Integer, CSEdge>>) itr.next();
			if (entry.getKey() == 0) {
				// don't count initial marker state edges.
				continue;
			}
			int nodeNumEdges = entry.getValue().size();
			numTransitions += nodeNumEdges;
		}

		return numTransitions;
	}
	
    public int lenLongestShortestPath() {
    	int lenLongestPath = 0;
    	int initNodeId = 0;
    	HashMap<Integer, Integer> nodeToLevel = new HashMap<Integer, Integer>();
    	LinkedList<Integer> workList = new LinkedList<Integer>();
    	// by initializing to -1, we make sure not to count the initial marker node edges
    	nodeToLevel.put(initNodeId, -1);
    	workList.add(initNodeId);
    	while (!workList.isEmpty()) {
    		int currNodeId = workList.pop();
    		if (!this.edges.containsKey(currNodeId)) {
    			continue;
    		}
    		Iterator<Map.Entry<Integer, CSEdge>> itr = this.edges.get(currNodeId).entrySet().iterator();
    		while (itr.hasNext()) {
    			Map.Entry<Integer, CSEdge> entry = (Map.Entry<Integer, CSEdge>) itr.next();
    			int succNodeId = entry.getKey();
    			if (nodeToLevel.containsKey(succNodeId)) {
    				if (nodeToLevel.get(succNodeId) > (nodeToLevel.get(currNodeId) + 1)) {
    					nodeToLevel.put(succNodeId, nodeToLevel.get(currNodeId) + 1);
    					// need to go over all of succNodeId's successors again
    					if (!workList.contains(succNodeId)) {
    						workList.add(succNodeId);
    					}
    				}
    			} else {
					nodeToLevel.put(succNodeId, nodeToLevel.get(currNodeId) + 1);
					if (!workList.contains(succNodeId)) {
						workList.add(succNodeId);
					}    				
    			}
    			
    		}
			lenLongestPath = Collections.max(nodeToLevel.values());
    	}
    	
    	if (lenLongestPath == -1) {
    		lenLongestPath = 0;
    	}
    	return lenLongestPath;
    }
    
	public int maxNumOutEdgesPerNode() {
		int maxOutEdges = 0;
		Iterator<Map.Entry<Integer, HashMap<Integer, CSEdge>>> itr =
				this.edges.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<Integer, HashMap<Integer, CSEdge>> entry = 
					(Map.Entry<Integer, HashMap<Integer, CSEdge>>) itr.next();
			if (entry.getKey() == 0) {
				// ignore max out edges of marker node
				continue;
			}
			maxOutEdges = Math.max(maxOutEdges, entry.getValue().size());
		}
		return maxOutEdges;
	}
	
	public int maxNumInEdgesPerNode() {
		HashMap<Integer, Integer> numInEdges = new HashMap<Integer, Integer>();
		Iterator<Map.Entry<Integer, CSNode>> itr = this.nodes.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<Integer, CSNode> entry = 
					(Map.Entry<Integer, CSNode>) itr.next();
			if (entry.getKey() == 0) {
				// ignore marker initial node
				continue;
			}
			int currNodeId = entry.getKey();
			numInEdges.put(currNodeId, 0);
			Iterator<Map.Entry<Integer, HashMap<Integer, CSEdge>>> innerItr =
					this.edges.entrySet().iterator();
			while (innerItr.hasNext()) {
				Map.Entry<Integer, HashMap<Integer, CSEdge>> innerEntry = 
						(Map.Entry<Integer, HashMap<Integer, CSEdge>>) innerItr.next();
				if (innerEntry.getKey() == currNodeId || innerEntry.getKey() == 0) {
					// ignore initial marker node and current node
					continue;
				}
				
				if (innerEntry.getValue().containsKey(currNodeId)) {
					numInEdges.put(currNodeId, numInEdges.get(currNodeId) + 1);
				}
			}
		}
		
		return Collections.max(numInEdges.values());
	}
	
	public int numAttractors() {
		int count = 0;
		Iterator<Map.Entry<Integer, CSNode>> itr =
				this.nodes.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<Integer, CSNode> entry = 
					(Map.Entry<Integer, CSNode>) itr.next();
			if (entry.getValue().type == CSNodeType.ATTRACTOR) {
				count++;
			}
		}
		return count;
	}

	public int numCycles() {
		int count = 0;
		Iterator<Map.Entry<Integer, CSNode>> itr =
				this.nodes.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<Integer, CSNode> entry = 
					(Map.Entry<Integer, CSNode>) itr.next();
			if (entry.getValue().type == CSNodeType.CYCLE) {
				count++;
			}
		}
		return count;
	}

}
