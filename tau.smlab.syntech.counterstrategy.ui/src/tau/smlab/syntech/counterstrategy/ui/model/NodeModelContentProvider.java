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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.gef.zest.fx.jface.IGraphContentProvider;
import org.eclipse.jface.viewers.Viewer;

import tau.smlab.syntech.counterstrategy.model.CounterStrategyGraphStruct;
import tau.smlab.syntech.counterstrategy.model.CounterStrategyGraphStruct.CSEdge;
import tau.smlab.syntech.counterstrategy.model.CounterStrategyGraphStruct.CSNode;
import tau.smlab.syntech.counterstrategy.model.CounterStrategyGraphStruct.CSNodeType;
import tau.smlab.syntech.counterstrategy.model.SymbolicCounterStrategyGraph;

/**
 * This is the content provider for the (zest) GraphViewer. It creates the
 * actual graph which will be presented in the GraphViewr.
 */
public class NodeModelContentProvider implements IGraphContentProvider {
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
	 * Mapping the indexes of symbolicGraph.getSymbolicGraph().nodes to their
	 * positions in {@link #nodes} list.
	 */
	private HashMap<Integer, Integer> nodesIndexes = null;

	/**
	 * The SymbolicCounterStrategyGraph to be shown.
	 */
	private SymbolicCounterStrategyGraph selfGraph = null;

	/**
	 * fetching a graph according to symbolicGraph.
	 * 
	 * @param symbolicGraph
	 *            - the symbolic graph to be fetched.
	 */
	private void initiate(SymbolicCounterStrategyGraph symbolicGraph) {
		CounterStrategyGraphStruct graph = symbolicGraph.getSymbolicGraph();
		System.out.println("sym graph = " + graph.toString());/////////////////////////////////////////////////////////////////////////////////////////
		nodes = new ArrayList<ModelNode>();
		initialNodes = new ArrayList<ModelNode>();
		nodesIndexes = new HashMap<Integer, Integer>();

		// going over all the graph's nodes and put them in the nodes List.
		ModelNode node;
		// node = new ModelNode("initial", "noName");
		// nodes.add(node);

		// iterating over all the entries instead of numeric-order
		for (Map.Entry<Integer, CSNode> entry : graph.nodes.entrySet()) {
			node = new ModelNode(new Integer(nodes.size()), entry.getValue());
			nodes.add(node);
			nodesIndexes.put(entry.getKey(), nodes.size() - 1);
			if (node.getSelfCsNode() != null && node.getSelfCsNode().type == CSNodeType.INITIAL) {
				// it's one of the initial states
				initialNodes.add(node);
			}
		}

		connections = new ArrayList<ModelConnection>();
		ModelConnection connect;
		int edgesCounter = 0; // a counter for numbering the edges
		/*
		 * going over all the initial nodes and creating edges between THE initial node
		 * to them for (edgesCounter = 0; edgesCounter < initialNodes.size();
		 * edgesCounter++) { connect = new ModelConnection("" + edgesCounter, "" +
		 * edgesCounter, nodes.get(0), initialNodes.get(edgesCounter));
		 * connections.add(connect); }
		 */

		// going over all the CSEdge's and creating edges accordingly
		HashMap<Integer, CSEdge> nodeEdges;

		// iterating over all the entries instead of numeric-order
		for (Map.Entry<Integer, HashMap<Integer, CSEdge>> entry : graph.edges.entrySet()) {
			nodeEdges = entry.getValue();
			if (nodeEdges != null)
				for (Map.Entry<Integer, CSEdge> entry2 : nodeEdges.entrySet()) {
					connect = new ModelConnection(edgesCounter, "", // We do not label edges
							nodes.get(nodesIndexes.get(entry.getKey())), nodes.get(nodesIndexes.get(entry2.getKey())),
							entry2.getValue());
					connections.add(connect);
					edgesCounter++;
				}
		}

		// Because we are lazy we save the info about the connections in the nodes
		for (ModelConnection connection : connections) {
			connection.getSource().getConnectedTo().add(connection.getDestination());
			connection.getDestination().getPredecessors().add(connection.getSource());
		}

		// TODO - because we implement the graph content provider, which requires edges,
		// if we have
		// no edges (that means we have a group of nodes not connected), we add a
		// "dummy" edge for
		// each with it as the source, and with null as the target.
		if (connections.isEmpty()) {
			for (ModelNode currNode : nodes) {
				connect = new ModelConnection(edgesCounter, "", currNode, null);
				connections.add(connect);
				edgesCounter++;
			}
		}
	}

	public List<ModelConnection> getConnections() {
		return connections;
	}

	public List<ModelNode> getInitialNodes() {
		return initialNodes;
	}

	public HashMap<Integer, Integer> getNodesIndexes() {
		return nodesIndexes;
	}

	public SymbolicCounterStrategyGraph getSelfGraph() {
		return selfGraph;
	}

	// @Override
	// public Object[] getElements(Object inputElement) {
	// if (selfGraph == null)
	// return null;
	// if (nodes == null) {
	// return null;
	// } else {
	// ArrayList<Object> elems = new ArrayList<Object>();
	// elems.addAll(nodes);
	// elems.addAll(connections);
	// return elems.toArray();
	// }
	// }

	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		if (newInput == null)
			return;
		else if (newInput instanceof SymbolicCounterStrategyGraph) {
			selfGraph = (SymbolicCounterStrategyGraph) newInput;
			initiate((SymbolicCounterStrategyGraph) selfGraph);
		}
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