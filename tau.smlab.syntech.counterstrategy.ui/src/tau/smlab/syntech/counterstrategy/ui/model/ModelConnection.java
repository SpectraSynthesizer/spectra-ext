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

import static tau.smlab.syntech.counterstrategy.ui.Activator.PLUGIN_ID;

import tau.smlab.syntech.counterstrategy.model.CounterStrategyGraphStruct.CSEdge;
import tau.smlab.syntech.counterstrategy.model.CounterStrategyGraphStruct.CSInvariant;
import tau.smlab.syntech.counterstrategy.ui.preferences.PreferencePage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.IPropertySource;
import org.eclipse.ui.views.properties.PropertyDescriptor;

public class ModelConnection implements IPropertySource {
	int id;
	final String label;
	final ModelNode source;
	final ModelNode destination;
	CSEdge csEdge;

	/**
	 * Maps {@link #selfCsNode}.invariants.var to
	 * {@link #selfCsNode}.invariants.val.
	 */
	private HashMap<String, String> cSinvariantsMap = null;
	// maybe: change it to map of CSInvariant.var to its CSInvariant.

	public ModelConnection(int id, String label, ModelNode source, ModelNode destination) {
		this.id = id;
		this.label = label;
		this.source = source;
		this.destination = destination;
		this.csEdge = null;
	}

	public ModelConnection(int id, String label, ModelNode source, ModelNode destination, CSEdge csEdge) {
		this(id, label, source, destination);
		this.csEdge = csEdge;
	}

	public int getID() {
		return id;
	}

	public void setID(int id) {
		this.id = id;
	}

	public String getLabel() {
		return label;
	}

	public ModelNode getSource() {
		return source;
	}

	public ModelNode getDestination() {
		return destination;
	}

	public CSEdge getCsEdge() {
		return csEdge;
	}

	@Override
	public Object getEditableValue() {
		return this;
	}

	@Override
	public IPropertyDescriptor[] getPropertyDescriptors() {
		List<IPropertyDescriptor> propertyDescriptors = new ArrayList<IPropertyDescriptor>();
		for (CSInvariant inv : csEdge.invariants) {
			if (inv.isDisplayed || !PreferencePage.isHideAuxiliaryVariablesChecked()) {
				PropertyDescriptor invariant = new PropertyDescriptor(PLUGIN_ID + ".properties.invariants." + inv.var,
						inv.var);
				invariant.setCategory("Invariants");
				propertyDescriptors.add(invariant);
			}
		}
		if (csEdge.isConcrete) {
			PropertyDescriptor edgeType = new PropertyDescriptor(PLUGIN_ID + ".properties.CSEdgeType", "Edge Type");
			propertyDescriptors.add(edgeType);
		}
		return propertyDescriptors.toArray(new IPropertyDescriptor[propertyDescriptors.size()]);
	}

	@Override
	public Object getPropertyValue(Object id) {
		if (id == null)
			return null;
		else if (id.toString().startsWith(PLUGIN_ID + ".properties.invariants.")) {
			if (cSinvariantsMap == null) // yet been initiated
				initiateCSInvariantMap();
			return cSinvariantsMap.get(id.toString().substring((PLUGIN_ID + ".properties.invariants.").length()));
		} else if ((PLUGIN_ID + ".properties.CSEdgeType").equals(id)) {
			return csEdge.type;
		}
		return null;
	}

	private void initiateCSInvariantMap() {
		cSinvariantsMap = new HashMap<String, String>();
		for (CSInvariant invariant : csEdge.invariants)
			cSinvariantsMap.put(invariant.var, invariant.val);
	}

	@Override
	public boolean isPropertySet(Object id) {
		if (id != null && id.toString().startsWith(PLUGIN_ID + ".properties.invariants.")) {
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

}