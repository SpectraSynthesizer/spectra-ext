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

// TODO: merge identical code segments in NodeModelContentProvider and NodeModelContentProviderConcrete
package tau.smlab.syntech.counterstrategy.ui.model;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.eclipse.gef.zest.fx.jface.IGraphContentProvider;
import org.eclipse.jface.viewers.Viewer;

import tau.smlab.syntech.counterstrategy.model.CounterStrategyGraphStruct;
import tau.smlab.syntech.counterstrategy.model.CounterStrategyGraphStruct.CSEdge;
import tau.smlab.syntech.counterstrategy.model.CounterStrategyGraphStruct.CSEdgeType;
import tau.smlab.syntech.counterstrategy.model.CounterStrategyGraphStruct.CSNode;
import tau.smlab.syntech.counterstrategy.model.SymbolicCounterStrategyGraph;
import tau.smlab.syntech.counterstrategy.ui.preferences.PreferencePage;

/**
 * This is the content provider for the (zest) GraphViewer. It creates the
 * actual graph which will be presented in the GraphViewr.
 */
public class NodeModelContentProviderConcrete implements IGraphContentProvider {
	/**
	 * The edges of the graph.
	 */
	private List<ModelConnection> connections = null;
	/**
	 * The vertices of the graph.
	 */
	private List<ModelNode> nodes = null;
	/**
	 * the vertices which will be the neighbors of the initial node.
	 */
	private List<ModelNode> initialNodes = null;

	/**
	 * The SymbolicCounterStrategyGraph to be shown.
	 */
	private SymbolicCounterStrategyGraph selfGraph = null;

	/**
	 * The node that should be concretized, or null if there is no such.
	 */
	private ModelNode nodeToConcretize = null;

	/**
	 * set of nodes that have already been added to the graph. the Integer value is
	 * the matching ModelNode's index at {@link #nodes}.
	 */
	HashMap<CSNode, Integer> cSNodeToModelNodeIndex;

	/** A flag that indicates if this graph is an interactive play. */
	@SuppressWarnings("unused")
	private boolean interactivePlay = false;

	/**
	 * initiating all the fields of this object:<br>
	 * {@link #nodes}, {@link #initialNodes}, {@link #cSNodeToModelNodeIndex},
	 * {@link #connections}.
	 */
	private void initiateDataBase() {
		nodes = new ArrayList<ModelNode>();
		initialNodes = new ArrayList<ModelNode>();
		cSNodeToModelNodeIndex = new HashMap<CSNode, Integer>();
		connections = new ArrayList<ModelConnection>();
	}

	/**
	 * fetching a graph according to symbolicGraph (set in selfGraph perviously).
	 * 
	 */
	private void initiate() {
		CounterStrategyGraphStruct graph = selfGraph.getSymbolicGraph();
		System.out.println("sym graph = " + graph.toString());/////////////////////////////////////////////////////////////////////////////////////////

		initiateDataBase();
		// getting the initial states:
		CounterStrategyGraphStruct concreteGraph;
		if (nodeToConcretize == null) // "Generate Concrete Graph" was clicked
			concreteGraph = selfGraph.getInitialStates(graph.nodes.get(new Integer(0)), // getting the (initial) root
																						// node from the symbolic graph
					new ArrayList<CSNode>(Arrays.asList(new CSNode[0])));
		else // "Concretize Node" was clicked
			concreteGraph = selfGraph.getInitialStates(nodeToConcretize.getSelfCsNode(),
					nodeToConcretize.getCSNodePredecessors());

		/**
		 * queue of nodes whose neighbors should be added to the graph. The Integer
		 * entry specify the depth of the tree search where this node's addition
		 * occurred
		 */
		Queue<SimpleEntry<CSNode, Integer>> queueOfNodes = new ArrayDeque<SimpleEntry<CSNode, Integer>>();
		addInitialNodes(concreteGraph, queueOfNodes);
		getConcreteSubGraph(queueOfNodes, false /* recheckExistingNodes */);
	}

	// adding all initial nodes to the graph
	private void addInitialNodes(CounterStrategyGraphStruct concreteGraph,
			Queue<SimpleEntry<CSNode, Integer>> queueOfNodes) {
		ModelNode node;
		int currentDepth = 0; // counter for the depth of the walking on the tree
		currentDepth++; // all the initial states are from depth 1

		// note: initialStatesGraph.nodes should contain <0, graph.nodes.get(new
		// Integer(0))> (or <0, nodeToConcretize.getSelfCsNode()>).
		for (Map.Entry<Integer, CSNode> entry : concreteGraph.nodes.entrySet()) {
			// if the node hasn't been already added to the graph - add it now
			if (!cSNodeToModelNodeIndex.containsKey(entry.getValue())) { // the node hasn't been added to the graph yet
				// node = new ModelNode(entry.getKey().toString(), entry.getValue());
				node = new ModelNode(new Integer(nodes.size() + 1), entry.getValue());
				node.setInitial();
				cSNodeToModelNodeIndex.put(entry.getValue(), nodes.size());
				nodes.add(node);
				// it's one of the initial states
				initialNodes.add(node);
				// add this node to the queue in order to go over its neighbors
				queueOfNodes.add(new SimpleEntry<CSNode, Integer>(entry.getValue(), currentDepth));
				System.out
						.println("initial node: id = " + node.getId() + ", node = " + node.getSelfCsNode().toString());
			} else { // if the node has been already added to the graph
				// it's one of the initial states
				initialNodes.add(nodes.get(getModelNodeIndexFromCSNode(entry.getValue(), cSNodeToModelNodeIndex)));
			}
		}
		// getInitialStates does not return any edges, even if there are edges between
		// the initial states. They will be returned
		// by performing "performConcreteStep" on each of the initial states and getting
		// its neighbors

		return;
	}

	// concreteGraph should contain the initial states
	private void getConcreteSubGraph(Queue<SimpleEntry<CSNode, Integer>> queueOfNodes, boolean recheckExistingNodes) {
		// going over all the graph's nodes and put them in the nodes List until
		// CONCRETIZATION_DEPTH depth, using BFS-style method
		ModelNode node;
		// all the initial states are depth 1, which is why we start here with depth 2
		int currentDepth = 2;
		int edgesCounter = connections.size();

		while (!queueOfNodes.isEmpty() && currentDepth <= PreferencePage.getConcretizationDepth() + 1) {
			SimpleEntry<CSNode, Integer> entry = queueOfNodes.poll(); // the queue isn't empty so pull() shouldn't
																		// return null, but:
			if (entry == null) // maybe the object in the queue (itself) is null
				continue;
			if (entry.getValue() == currentDepth) { // because now that we've reached currentDepth, this node's
													// neighbors will be put in the queue as the next layer (which is
													// currDepth+1)
				currentDepth++; // the current depth of the neighbors we put in the queue
				if (currentDepth > PreferencePage.getConcretizationDepth() + 1)
					break;
				/*
				 * if it was (CONCRETIZATION_DEPTH + 1) instead of CONCRETIZATION_DEPTH than: if
				 * the last layer (CONCRETIZATION_DEPTH) is reached, then all the nodes in this
				 * layer are added to the graph, but not their neighbors, which from first sight
				 * makes sense, B - U - T: if the neighbors are already in the graph, we should
				 * add appropriate edges between them!!!!! so in the last layer we should go
				 * over all the neighbors of the nodes, check which ones are already in the
				 * graph, and add appropriate edges. notice that this option of edges from lower
				 * layer to higher layer (and vice versa) is possible because that the edges are
				 * directed.
				 */
			}
			if (nodeToConcretize != null
					&& entry.getKey().indexSymbolic != nodeToConcretize.getSelfCsNode().indexSymbolic) {
				// don't go over nodes that are outside the node to concretize (when
				// concretizing a specific symbolic node)
				continue;
			}
			// going over all neighbors of this node
			CounterStrategyGraphStruct concreteGraph = this.selfGraph.performConcreteStep(entry.getKey());
			for (Map.Entry<Integer, CSNode> subGraphEntry : concreteGraph.nodes.entrySet()) {
				// TODO: fix the documentation of performConcreteStep - the root of the subGraph
				// is the parameter node itself.

				// if the node hasn't already been added to the graph - add it now
				if (!cSNodeToModelNodeIndex.containsKey(subGraphEntry.getValue())) {
					if (currentDepth <= PreferencePage.getConcretizationDepth()) {
						// node = new ModelNode(subGraphEntry.getKey().toString(),
						// subGraphEntry.getValue());
						node = new ModelNode(new Integer(nodes.size() + 1), subGraphEntry.getValue());
						cSNodeToModelNodeIndex.put(subGraphEntry.getValue(), nodes.size());
						nodes.add(node);
						// add this node to the queue in order to go over its neighbors
						queueOfNodes.add(new SimpleEntry<CSNode, Integer>(subGraphEntry.getValue(), currentDepth));
					}
				} else if (recheckExistingNodes) {
					// add this node to the queue in order to go over its neighbors
					queueOfNodes.add(new SimpleEntry<CSNode, Integer>(subGraphEntry.getValue(), currentDepth));
				}
			}

			// going over all the CSEdge's and creating edges accordingly
			// iterating over all the entries instead of numeric-order
			for (Map.Entry<Integer, HashMap<Integer, CSEdge>> edgeEntry : concreteGraph.edges.entrySet()) {
				if (edgeEntry == null || edgeEntry.getKey() == null || edgeEntry.getValue() == null)
					continue;
				// notice that entry.getValue() is HashMap<Integer, CSEdge> = a list of
				// neighbors (as indexes) and their edges' object
				for (Map.Entry<Integer, CSEdge> entry2 : edgeEntry.getValue().entrySet()) {
					if (entry2 == null || entry2.getKey() == null || entry2.getValue() == null)
						continue;

					// NOTE - the CSEdge fromNode/toNode here is the symbolic indices
					CounterStrategyGraphStruct.CSEdge edge = entry2.getValue();

					int concreteGraphFromInd = edgeEntry.getKey();
					int concreteGraphToInd = entry2.getKey();
					System.out.println(
							"concreteGraphFromInd = " + concreteGraphFromInd + ", ToInd = " + concreteGraphToInd);
					CounterStrategyGraphStruct.CSNode concreteSrcNode = concreteGraph.nodes.get(concreteGraphFromInd);
					CounterStrategyGraphStruct.CSNode concreteDstNode = concreteGraph.nodes.get(concreteGraphToInd);
					if (!cSNodeToModelNodeIndex.containsKey(concreteSrcNode)
							|| !cSNodeToModelNodeIndex.containsKey(concreteDstNode)) {
						// either the source node or the target node of the edge are not in the graph -
						// do not add the edge
						continue;
					}

					ModelNode srcNode = nodes.get(cSNodeToModelNodeIndex.get(concreteSrcNode));
					ModelNode tgtNode = nodes.get(cSNodeToModelNodeIndex.get(concreteDstNode));

					// if we already have this edge in the view - do not add
					boolean foundEdge = false;
					for (int j = 0; j < connections.size(); j++) {
						if (connections.get(j).getCsEdge() == null) {
							// this is the dummy edge used in order to display an unconnected node, no need
							// to check it
							continue;
						}

						if (connections.get(j).source.getIdNum() == srcNode.getIdNum()
								&& connections.get(j).destination.getIdNum() == tgtNode.getIdNum()) {
							foundEdge = true;
							break;
						}
					}
					if (!foundEdge) {
						edge.fromNode = srcNode.getIdNum();
						edge.toNode = tgtNode.getIdNum();
						System.out.println("Adding edge");
						ModelConnection connect = new ModelConnection(edgesCounter, "" /* no labels for edges */,
								srcNode, tgtNode, edge);

						connections.add(connect);
						edgesCounter++;
					}
				}
			}
		}

		/*
		 * // TODO - I removed this because we use the connections to get the nodes, not
		 * the other way around. Make sure this doesn't ruin the behavior Because we are
		 * lazy we save the info about the connections in the nodes for (ModelConnection
		 * connection : connections) {
		 * connection.getSource().getConnectedTo().add(connection.getDestination());
		 * connection.getDestination().getPredecessors().add(connection.getSource()); }
		 */
		// Because we implement the graph content provider, which requires edges, if we
		// have
		// no edges (that means we have a group of nodes not connected), we add a
		// "dummy" edge for
		// each with it as the source, and with null as the target.
		if (connections.isEmpty()) {
			for (ModelNode currNode : nodes) {
				ModelConnection connect = new ModelConnection(edgesCounter, "" /* no labels for edges */, currNode,
						null);
				connections.add(connect);
				edgesCounter++;
			}
		}
	}

	// public List<ModelNode> getNodes() {
	// return nodes;
	// }

	public List<ModelConnection> getConnections() {
		return connections;
	}

	public List<ModelNode> getInitialNodes() {
		return initialNodes;
	}

	public SymbolicCounterStrategyGraph getSelfGraph() {
		return selfGraph;
	}

	// @Override
	// public Object[] getElements(Object inputElement) {
	// if (selfGraph == null)
	// return null;
	// if (nodes == null)
	// return null;
	// // return new Object[0];
	// else {
	// ArrayList<Object> elems = new ArrayList<Object>();
	// // TODO - perhaps nodes not needed, since we implement the GraphViewer
	// interface,
	// // which goes over the edges as elements.
	// elems.addAll(nodes);
	// elems.addAll(connections);
	// return elems.toArray();
	// }
	// }

	/*
	 * @Override public Object[] getConnectedTo(Object entity) { if (entity
	 * instanceof ModelNode) { ModelNode node = (ModelNode) entity; return
	 * node.getConnectedTo().toArray(); } throw new
	 * RuntimeException("Type not supported"); }
	 */
	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		if (newInput == null)
			return;
		else if ((newInput instanceof Object[]) && (((Object[]) newInput).length == 3)
				&& (((Object[]) newInput)[0] instanceof SymbolicCounterStrategyGraph)
				&& (((Object[]) newInput)[1] instanceof ModelNode) && (((Object[]) newInput)[2] instanceof Boolean)) {
			selfGraph = (SymbolicCounterStrategyGraph) ((Object[]) newInput)[0];
			nodeToConcretize = (ModelNode) ((Object[]) newInput)[1];
			if (((Boolean) (((Object[]) newInput)[2]).equals(new Boolean(false)))) {
				// the input is a graph and a node that should be concretized
			} else {
				initiateDataBase();
				this.interactivePlay = true;
				// the input is a graph after the user chose "Play Interactively" from
				// the SymbolicGraphView and the node is the product of the Interactive
				// Play dialog (the new concrete graph initially contains only this node)
				nodes.clear();
				initialNodes.clear();
				connections.clear();
				cSNodeToModelNodeIndex.clear();
				nodes.add(nodeToConcretize);
				cSNodeToModelNodeIndex.put(nodeToConcretize.getSelfCsNode(), nodes.size() - 1);

				/*
				 * // TODO - this was a code for self loop in interactive play - but there
				 * shouldn't be a self loop in that context. So commenting it out for now. //
				 * add self loop edge for this node if exists CounterStrategyGraphStruct
				 * concreteGraph =
				 * selfGraph.performConcreteStep(nodeToConcretize.getSelfCsNode()); Integer
				 * indexOfCSNodeinGraph = null; // The index of nodeToConcretize.getSelfCsNode()
				 * in concreteGraph.nodes for (Map.Entry<Integer, CSNode> subGraphEntry :
				 * concreteGraph.nodes.entrySet()) {
				 * if(nodeToConcretize.getSelfCsNode().equals(subGraphEntry.getValue())){
				 * indexOfCSNodeinGraph = subGraphEntry.getKey(); break; } } if
				 * (indexOfCSNodeinGraph != null) { CSEdge loop =
				 * concreteGraph.edges.get(indexOfCSNodeinGraph).get(indexOfCSNodeinGraph);
				 * ModelConnection modelLoop = new ModelConnection(0, "0", nodeToConcretize,
				 * nodeToConcretize, loop); if (loop != null) { connections.add(modelLoop); //
				 * modelLoop.getSource().getConnectedTo().add(modelLoop.getDestination()); //
				 * modelLoop.getDestination().getPredecessors().add(modelLoop.getSource());
				 * nodeToConcretize.getConnectedTo().add(nodeToConcretize);
				 * nodeToConcretize.getPredecessors().add(nodeToConcretize); } }
				 */
				// TODO - because we implement the graph content provider, which requires edges,
				// if we have
				// no edges (that means we have a group of nodes not connected), we add a
				// "dummy" edge for
				// each with it as the source, and with null as the target.
				if (connections.isEmpty()) {
					ModelConnection dummyEdge = new ModelConnection(0, "" /* no labels for edges */, nodeToConcretize,
							null);
					connections.add(dummyEdge);
				}
				return;
			}
		}

		// the input is a graph that should be concretized
		else if (newInput instanceof SymbolicCounterStrategyGraph) {
			selfGraph = (SymbolicCounterStrategyGraph) newInput;
			nodeToConcretize = null; // a whole graph should be concretized, not just one symbolic node
		} else
			return;
		initiate();
	}

	/**
	 * return the appropriate ModelNode index in {@link #nodes} according to the the
	 * matching CSNode.
	 * 
	 * @param node
	 *            - the wanted CSNode.
	 * @param cSNodeIndexToModelNodeIndex
	 *            - Mapping the CSNode Objects to the matching ModelNode's index at
	 *            {@link #nodes}.
	 * @return the wanted ModelNode's index in {@link #nodes} list.
	 */
	private Integer getModelNodeIndexFromCSNode(CSNode node, HashMap<CSNode, Integer> cSNodeToModelNodeIndex) {
		return cSNodeToModelNodeIndex.get(node);
	}

	/**
	 * return the appropriate ModelNode according to the matching CSNode.
	 * 
	 * @param node
	 *            - the wanted CSNode.
	 * @param cSNodeIndexToModelNodeIndex
	 *            - Mapping the CSNode Objects to the matching ModelNode's index at
	 *            {@link #nodes}.
	 * @return the wanted ModelNode.
	 */
	@SuppressWarnings("unused")
	private ModelNode getModelNodeFromCSNode(CSNode node, HashMap<CSNode, Integer> cSNodeToModelNodeIndex) {
		return nodes.get(getModelNodeIndexFromCSNode(node, cSNodeToModelNodeIndex));
	}

	// @Override
	// public Object getSource(Object rel) {
	// if (rel instanceof ModelConnection) {
	// ModelConnection connection = (ModelConnection) rel;
	// return connection.getSource();
	// } else {
	// return null;
	// }
	// }
	//
	// @Override
	// public Object getDestination(Object rel) {
	// if (rel instanceof ModelConnection) {
	// ModelConnection connection = (ModelConnection) rel;
	// return connection.getDestination();
	// } else {
	// return null;
	// }
	// }

	public void addNodes(ArrayList<ModelNode> nodesToAdd, boolean addConcreteSubGraph) {
		ArrayList<ModelNode> nodesAlreadyInView = new ArrayList<ModelNode>();
		ArrayList<ModelNode> newNodes = new ArrayList<ModelNode>();

		if (addConcreteSubGraph) {
			// adding a concrete sub-graph as part of "get concrete sub graph"

			/**
			 * queue of nodes whose neighbors should be added to the graph. The Integer
			 * entry specify the depth of the tree search where this node's addition
			 * occurred
			 */
			Queue<SimpleEntry<CSNode, Integer>> queueOfNodes = new ArrayDeque<SimpleEntry<CSNode, Integer>>();
			// depth of the root node is considered "1"
			int depth = 1;
			// there's only one node selected in this case, since we're generating a
			// sub-graph from
			// a single root node
			CounterStrategyGraphStruct.CSNode node = nodesToAdd.get(0).getSelfCsNode();
			queueOfNodes.add(new SimpleEntry<CSNode, Integer>(node, depth));
			getConcreteSubGraph(queueOfNodes, true /* recheckExistingNodes */);

		} else {
			// adding a node as part of "perform concrete step"
			addNewNodes(nodesToAdd, newNodes, nodesAlreadyInView);
			addConnections(newNodes, nodesAlreadyInView);
		}
	}

	private void addNewNodes(ArrayList<ModelNode> nodesToAdd, ArrayList<ModelNode> newNodes,
			ArrayList<ModelNode> nodesAlreadyInView) {
		// The nodesToAdd are passed with IDs starting from "1" which are not relevant
		// to the IDs actually currently
		// in the content provider.
		// To change it to the correct ID, we get all of the node elements, find the
		// maximal ID value -
		// then start counting the new node values from that ID.
		int maxNodeId = 1;
		for (int i = 0; i < nodes.size(); i++) {
			if (nodes.get(i).getIdNum() > maxNodeId) {
				maxNodeId = nodes.get(i).getIdNum();
			}
		}

		int newNodeId = maxNodeId + 1;
		for (int i = 0; i < nodesToAdd.size(); i++) {
			// if the node hasn't already been added to the graph - add it now
			if (!cSNodeToModelNodeIndex.containsKey(nodesToAdd.get(i).getSelfCsNode())) {
				nodesToAdd.get(i).setIdNum(newNodeId++);
				newNodes.add(nodesToAdd.get(i));
				cSNodeToModelNodeIndex.put(nodesToAdd.get(i).getSelfCsNode(), nodes.size() - 1);
			} else { // if the node has been already added to the graph
				nodesAlreadyInView.add(nodesToAdd.get(i));
			}
		}

		/*
		 * TODO previous version - remove if working int newNodeId = maxNodeId + 1; for
		 * (int i = 0; i < nodesToAdd.size(); i++) { if
		 * (!nodes.contains(nodesToAdd.get(i))) {
		 * nodesToAdd.get(i).setIdNum(newNodeId++); newNodes.add(nodesToAdd.get(i)); }
		 * else { // node already in the viewer
		 * nodesAlreadyInView.add(nodesToAdd.get(i)); } }
		 */

		nodes.addAll(newNodes);
		return;
	}

	private void addConnections(ArrayList<ModelNode> newNodes, ArrayList<ModelNode> nodesAlreadyInView) {
		int maxEdgeId = 1;
		for (int i = 0; i < connections.size(); i++) {
			ModelConnection connect = connections.get(i);
			if (connect.getID() > maxEdgeId) {
				maxEdgeId = connect.getID();
			}
		}

		int newEdgeId = maxEdgeId + 1;
		for (int i = 0; i < newNodes.size(); i++) {
			CounterStrategyGraphStruct.CSEdge newEdge = new CounterStrategyGraphStruct.CSEdge();
			newEdge.isConcrete = true;
			newEdge.fromNode = newNodes.get(i).getPredecessors().get(0).getIdNum();
			newEdge.toNode = newNodes.get(i).getIdNum();

			// if we already have this edge in the view - do not add
			boolean foundEdge = false;
			for (int j = 0; j < connections.size(); j++) {
				if (connections.get(j).getCsEdge() == null) {
					// this is the dummy edge used in order to display an unconnected node, no need
					// to check it
					continue;
				}
				if (connections.get(j).getCsEdge().fromNode == newEdge.fromNode
						&& connections.get(j).getCsEdge().toNode == newEdge.toNode) {
					foundEdge = true;
					break;
				}
			}
			if (foundEdge) {
				continue;
			}

			// assuming a single predecessor for this step
			int predSymIndex = newNodes.get(i).getPredecessors().get(0).getSelfCsNode().indexSymbolic;
			int currSymIndex = newNodes.get(i).getSelfCsNode().indexSymbolic;
			newEdge.type = (predSymIndex == currSymIndex ? CSEdgeType.INSIDE_NODE : CSEdgeType.BETWEEN_NODES);
			ModelConnection newConnect = new ModelConnection(newEdgeId, "" /* no labels for edges */,
					newNodes.get(i).getPredecessors().get(0), newNodes.get(i), newEdge);
			newEdgeId++;
			connections.add(newConnect);
		}

		// adding edges for nodes already in view
		for (int i = 0; i < nodesAlreadyInView.size(); i++) {
			ModelNode targetNode = nodesAlreadyInView.get(i);
			// since the viewer gets the nodes via the connectors, in order to create an
			// edge to an already
			// existing node element, we need to get the relevant element from the
			// connectors.
			// we know it will be in one of the connectors (perhaps in a dummy connector if
			// this node is not actually connected
			// in the view), since this node was discovered to be already in the nodes list,
			// which is synced with the
			// connectors list
			for (int j = 0; j < connections.size(); j++) {
				if (connections.get(j).getSource().equals(nodesAlreadyInView.get(i))) {
					targetNode = connections.get(j).getSource();
					break;
				} else if (connections.get(j).getDestination() != null && // might be null if this is a dummy edge
						connections.get(j).getDestination().equals(nodesAlreadyInView.get(i))) {
					targetNode = connections.get(j).getDestination();
					break;
				}
			}

			targetNode.getPredecessors().addAll(nodesAlreadyInView.get(i).getPredecessors());
			CounterStrategyGraphStruct.CSEdge newEdge = new CounterStrategyGraphStruct.CSEdge();
			newEdge.isConcrete = true;
			newEdge.fromNode = nodesAlreadyInView.get(i).getPredecessors().get(0).getIdNum();
			newEdge.toNode = targetNode.getIdNum();

			// if we already have this edge in the view - do not add
			boolean foundEdge = false;
			for (int j = 0; j < connections.size(); j++) {
				if (connections.get(j).getCsEdge() == null) {
					// this is the dummy edge used in order to display an unconnected node, no need
					// to check it
					continue;
				}
				if (connections.get(j).getCsEdge().fromNode == newEdge.fromNode
						&& connections.get(j).getCsEdge().toNode == newEdge.toNode) {
					foundEdge = true;
					break;
				}
			}
			if (foundEdge) {
				continue;
			}

			// assuming a single predecessor for this step
			int predSymIndex = nodesAlreadyInView.get(i).getPredecessors().get(0).getSelfCsNode().indexSymbolic;
			int currSymIndex = targetNode.getSelfCsNode().indexSymbolic;
			newEdge.type = (predSymIndex == currSymIndex ? CSEdgeType.INSIDE_NODE : CSEdgeType.BETWEEN_NODES);
			ModelConnection newConnect = new ModelConnection(newEdgeId, "" /* no labels for edges */,
					nodes.get(nodes.indexOf(nodesAlreadyInView.get(i).getPredecessors().get(0))), targetNode, newEdge);
			newEdgeId++;
			connections.add(newConnect);
		}
	}

	@Override
	public Object[] getAdjacentNodes(Object node) {
		if (node instanceof ModelNode) {
			ModelNode mnode = (ModelNode) node;
			if (mnode.getConnectedTo() != null) {
				return mnode.getConnectedTo().toArray();
			}
		}
		return null;
	}

	@Override
	public Object[] getNestedGraphNodes(Object node) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object[] getNodes() {
		if (nodes != null) {
			return nodes.toArray();
		}
		return null;
	}

	@Override
	public boolean hasNestedGraph(Object node) {
		// TODO Auto-generated method stub
		return false;
	}
}