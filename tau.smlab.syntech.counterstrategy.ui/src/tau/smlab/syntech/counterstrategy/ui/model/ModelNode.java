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

package tau.smlab.syntech.counterstrategy.ui.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.IPropertySource;
import org.eclipse.ui.views.properties.PropertyDescriptor;

import static tau.smlab.syntech.counterstrategy.ui.Activator.PLUGIN_ID;
import tau.smlab.syntech.counterstrategy.model.CounterStrategyGraphStruct.CSInvariant;
import tau.smlab.syntech.counterstrategy.model.CounterStrategyGraphStruct.CSNode;
import tau.smlab.syntech.counterstrategy.model.CounterStrategyGraphStruct.CSNodeType;
import tau.smlab.syntech.counterstrategy.ui.preferences.PreferencePage;

/**
 * The model of the nodes. These are the actual node objects which will be shown
 * in the view.
 */
public class ModelNode implements IPropertySource {
	private static final int NAME_LEN = 20;

	private int id;
	@SuppressWarnings("unused")
	private String name;
	private boolean isInitial; // to identify initial states in concrete graph

	/**
	 * the Model Node neighbors of this node (which there are edges between this
	 * node to them).
	 */
	private List<ModelNode> connections;
	/**
	 * the Model Nodes which this node is their neighbor (which there are edges
	 * between them to this node).
	 */
	private List<ModelNode> neighborOf;

	/** the related CSNode object of this ModelNode Object. */
	private CSNode selfCsNode;

	/**
	 * Maps {@link #selfCsNode}.invariants.var to
	 * {@link #selfCsNode}.invariants.val.
	 */
	private HashMap<String, String> cSinvariantsMap = null;
	// maybe: change it to map of CSInvariant.var to its CSInvariant.

	/** the related GraphNode object of this ModelNode Object. */
	// private GraphNode graphNode = null;

	public ModelNode(int id, String name) {
		this.id = id;
		this.name = name;
		this.connections = new ArrayList<ModelNode>();
		this.neighborOf = new ArrayList<ModelNode>();
		this.selfCsNode = null;
		this.isInitial = false;
	}

	public ModelNode(int id, CSNode csNode) {
		this(id, "id: " + id);
		this.selfCsNode = csNode;
		this.isInitial = false;
		setIdNum(id); // to ensure the node label is correct
	}

	public void setInitial() {
		isInitial = true;
	}

	public boolean isInitial() {
		return isInitial;
	}

	public String getId() {
		return "" + id;
	}

	public int getIdNum() {
		return id;
	}

	public void setIdNum(int id) {
		this.id = id;
		this.name = "" + id;
		if (!this.selfCsNode.isConcrete) {
			this.name += "\n" + this.selfCsNode.type;
		}
	}

	public String getName() {
		// This is not what is saved in the "name" field - that field can be used for
		// debugging.
		if (selfCsNode.isConcrete) {
			if (isInitial) {
				return "Initial: s" + id;
			} else {
				return "s" + id;
			}
		}
		String res = selfCsNode.violatedJustice;
		if (selfCsNode.type == CSNodeType.INITIAL) {
			res = "INI";
		} else if (selfCsNode.type == CSNodeType.ATTRACTOR) {
			return "attractor";
		} else {
			int startInd = 0;
			if (res.startsWith("gar")) {
				startInd = 4;
			} else if (res.startsWith("guarantee")) {
				startInd = 10;
			}
			if (res.length() > startInd + NAME_LEN) {
				res = "cycle !" + res.substring(startInd, startInd + NAME_LEN);
			} else {
				res = "cycle !" + res.substring(startInd);
			}
		}
		return res;
	}

	/**
	 * @return list of nodes that there are edges from this node to them.
	 */
	public List<ModelNode> getConnectedTo() {
		return connections;
	}

	/**
	 * @return list of nodes that there are edges from them to this node.
	 */
	public List<ModelNode> getPredecessors() {
		return neighborOf;
	}

	/**
	 * @return list of CSNodes that there are edges from them to this node.
	 */
	public ArrayList<CSNode> getCSNodePredecessors() {
		ArrayList<CSNode> csNodeList = new ArrayList<CSNode>();
		for (ModelNode node : neighborOf) {
			csNodeList.add(node.getSelfCsNode());
		}
		return csNodeList;
	}

	public CSNode getSelfCsNode() {
		return selfCsNode;
	}

	// public GraphNode getGraphNode() {
	// return graphNode;
	// }
	//
	// public void setGraphNode(GraphNode graphNode) {
	// this.graphNode = graphNode;
	// }

	@Override
	public Object getEditableValue() {
		return this;
	}

	@Override
	public IPropertyDescriptor[] getPropertyDescriptors() {
		List<IPropertyDescriptor> propertyDiscriptors = new ArrayList<IPropertyDescriptor>();
		if (!selfCsNode.isConcrete) {
			// The properties appear in alphabetical order, so can't make this property
			// appear first
			PropertyDescriptor type = new PropertyDescriptor(PLUGIN_ID + ".properties.CSNodeType", "Node Type");
			propertyDiscriptors.add(type);
		}

		// don't show invariants on initial node in symbolic graph (it is only a marker
		// node which
		// doesn't contain states)
		if (selfCsNode.isConcrete || selfCsNode.type != CSNodeType.INITIAL) {
			for (CSInvariant inv : selfCsNode.invariants) {
				if (inv.isDisplayed || !PreferencePage.isHideAuxiliaryVariablesChecked()) {
					PropertyDescriptor invariant = new PropertyDescriptor(
							PLUGIN_ID + ".properties.invariants." + inv.var, inv.var);
					invariant.setCategory("Invariants");
					propertyDiscriptors.add(invariant);
				}
			}
			PropertyDescriptor invariant = new PropertyDescriptor(PLUGIN_ID + ".properties.justiceViolated",
					"Justice Violated");
			propertyDiscriptors.add(invariant);
		}

		// TODO - if selfCsNode.isConcrete, need to add a property of "symbolic node
		// index", but it needs to be
		// the index in the relevant symbolic graph view, not necessarily the index in
		// the selfCsNode.indexSymbolic.

		return propertyDiscriptors.toArray(new IPropertyDescriptor[propertyDiscriptors.size()]);
	}

	@Override
	public Object getPropertyValue(Object id) {
		if (id == null)
			return null;
		else if (id.toString().startsWith(PLUGIN_ID + ".properties.invariants.")) {
			if (cSinvariantsMap == null) // yet been initiated
				initiateCSInvariantMap();
			return cSinvariantsMap.get(id.toString().substring((PLUGIN_ID + ".properties.invariants.").length()));
		} else if ((PLUGIN_ID + ".properties.justiceViolated").equals(id)) {
			return selfCsNode.violatedJustice;
		} else if ((PLUGIN_ID + ".properties.CSNodeType").equals(id)) {
			return selfCsNode.type.toString();
		}
		return null;
	}

	private void initiateCSInvariantMap() {
		cSinvariantsMap = new HashMap<String, String>();
		for (CSInvariant invariant : selfCsNode.invariants)
			cSinvariantsMap.put(invariant.var, invariant.val);
	}

	@Override
	public boolean isPropertySet(Object id) {
		if (id != null && id.toString().startsWith(PLUGIN_ID + ".properties.invariants.")) {
			// Return true for indicating that this property does not have a meaningful
			// default value.
			return true;
		} else if ((PLUGIN_ID + ".properties.justiceViolated").equals(id)) {
			// Return true for indicating that this property does not have a meaningful
			// default value.
			return true;
		} else if ((PLUGIN_ID + ".properties.CSNodeType").equals(id)) {
			// Return true for indicating that this property does not have a meaningful
			// default value.
			return true;
		} else
			return false;
	}

	@Override
	public void resetPropertyValue(Object id) {
	}

	@Override
	public void setPropertyValue(Object id, Object value) {
	}

	/*
	 * (non-Javadoc)
	 * 
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
		if (!((ModelNode) obj).getSelfCsNode().equals(this.getSelfCsNode()))
			return false;
		// we only compare between concrete states
		if (!this.getSelfCsNode().isConcrete)
			return false;
		return true;
	}

}