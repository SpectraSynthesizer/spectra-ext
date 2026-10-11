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

// TODO: merge identical code segments in ConcreteGraphView and SymbolicGraphView
package tau.smlab.syntech.counterstrategy.ui.views;

import static tau.smlab.syntech.counterstrategy.ui.Activator.CONCRETE_STEP_NUMBER_OF_SUCCESSORS_LIMIT;

import java.util.ArrayList;

import org.eclipse.gef.graph.Node;
import org.eclipse.gef.layout.algorithms.TreeLayoutAlgorithm;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import tau.smlab.syntech.counterstrategy.model.SymbolicCounterStrategyGraph;
import tau.smlab.syntech.counterstrategy.ui.PerformConcreteStepConsole;
import tau.smlab.syntech.counterstrategy.ui.model.ModelNode;
import tau.smlab.syntech.counterstrategy.ui.model.NodeModelContentProviderConcrete;

public class ConcreteGraphView extends CounterStrategyView {

	/** the identifier of the view. */
	public static final String ID = "tau.smlab.syntech.counterstrategy.ui.views.ConcreteGraph";

	/** actions for clicking options */
	private Action performConcreteStepAction;
	private Action getConcreteSubGraphAction;

	/** empty constructor. A viewer that shows a concrete graph. */
	public ConcreteGraphView() {
	}

	@Override
	protected void setContentProviderForNodes() {
		viewer.setContentProvider(new NodeModelContentProviderConcrete());
	}

	/**
	 * creating the graph in the view with *.model package.
	 * 
	 * @param symbolicGraph
	 */
	public void displayConcreteCounterStrategyModel(SymbolicCounterStrategyGraph symbolicGraph) {
		displayConcreteCounterStrategyModelWithSpecificNodeConcretization(symbolicGraph, null, false);
	}

	/**
	 * creating the graph in the view with "*.model" package.
	 * <dd>
	 * <li>If nodeToConcretized!=null && interactivePlay=false then it's generated
	 * from the concretization option of a specific node in the
	 * SymbolicGraphView.</li></dd>
	 * <dd>
	 * <li>If nodeToConcretized!=null && interactivePlay=true then it's generated
	 * from the "Play Interactively" option from the SymbolicGraphView and the
	 * nodeToConcretize is the only node need to be shown in the graph.</li></dd>
	 * 
	 * @param symbolicGraph
	 */
	public void displayConcreteCounterStrategyModelWithSpecificNodeConcretization(
			SymbolicCounterStrategyGraph symbolicGraph, ModelNode nodeToConcretized, boolean interactivePlay) {
		if (nodeToConcretized == null)
			viewer.setInput(symbolicGraph);
		else
			viewer.setInput(new Object[] { symbolicGraph, nodeToConcretized, new Boolean(interactivePlay) });
		viewer.setLayoutAlgorithm(new TreeLayoutAlgorithm(TreeLayoutAlgorithm.TOP_DOWN));
		// viewer.applyLayout();
	}

	/**
	 * updating the graph in the view with "*.model" package with an additional node
	 * *
	 */
	public void updateConcreteCounterStartegyModelWithNode(SymbolicCounterStrategyGraph symbolicGraph,
			ArrayList<ModelNode> nodesToAdd) {
		NodeModelContentProviderConcrete provider = (NodeModelContentProviderConcrete) viewer.getContentProvider();
		provider.addNodes(nodesToAdd, false /* addConcreteSubGraph */);
		viewer.refresh();

		/*
		 * This is a version not going through the content provider // TODO - right now,
		 * the nodeToConcretize is passed with ID = "1". // To change it to the correct
		 * ID, we get all of the node elements from the current viewer, and // find the
		 * maximal ID value - then change the nodeToConcretize with its ID. Object[]
		 * nodes = viewer.getNodeElements(); System.out.println("nodes size = " +
		 * nodes.length); int maxNodeId = 1; for (int i = 0; i < nodes.length; i++) {
		 * ModelNode node = (ModelNode) nodes[i]; if (node.getIdNum() > maxNodeId) {
		 * maxNodeId = node.getIdNum(); } } nodeToConcretize.setIdNum(maxNodeId + 1);
		 * 
		 * Object[] connections = viewer.getConnectionElements(); int maxEdgeId = 1; for
		 * (int i = 0; i < connections.length; i++) { ModelConnection edge =
		 * (ModelConnection) connections[i]; if (edge.getID() > maxEdgeId) { maxEdgeId =
		 * edge.getID(); } }
		 * 
		 * int edgeId = maxEdgeId + 1; // should only have 1 predecessor in a concrete
		 * step // TODO - if "addRelationship" is not an API according to this warning,
		 * what's the correct way to add a node and a connection?
		 * viewer.addRelationship(new ModelConnection(edgeId, "" + edgeId,
		 * nodeToConcretize, nodeToConcretize.getPredecessors().get(0)),
		 * nodeToConcretize.getPredecessors().get(0), nodeToConcretize);
		 */
	}

	@Override
	protected void hookContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				fillContextMenu(manager);

				// set Enabled of actions
				ISelection selection = viewer.getSelection();
				if (((IStructuredSelection) selection).size() != 1) {
					performConcreteStepAction.setEnabled(false);
					getConcreteSubGraphAction.setEnabled(false);
				} else if (((IStructuredSelection) selection).getFirstElement() instanceof Node) {
					performConcreteStepAction.setEnabled(true);
					getConcreteSubGraphAction.setEnabled(true);
				} else {
					performConcreteStepAction.setEnabled(false);
					getConcreteSubGraphAction.setEnabled(false);
				}
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}

	@Override
	protected void fillLocalPullDown(IMenuManager manager) {
		super.fillLocalPullDown(manager);
		manager.add(new Separator());
		manager.add(performConcreteStepAction);
		manager.add(getConcreteSubGraphAction);
	}

	protected void addActionsToContextMenu(IMenuManager manager) {
		manager.add(performConcreteStepAction);
		manager.add(getConcreteSubGraphAction);
	}

	@Override
	protected void makeActions() {
		super.makeActions();

		doubleClickAction = new Action() {
			public void run() {
				performConcreteStepAction.run();
			}
		};

		performConcreteStepAction = new Action() {
			public void run() {
				ISelection selection = viewer.getSelection();
				Object obj = ((IStructuredSelection) selection).getFirstElement();
				if (obj instanceof Node) {
					Node node = (Node) obj;
					ModelNode modelNode = (ModelNode) node.getAttributes().get("element-model");
					PerformConcreteStepConsole dialog = new PerformConcreteStepConsole(
							((NodeModelContentProviderConcrete) viewer.getContentProvider()).getSelfGraph(), modelNode);
					if (dialog.numSuccessors() <= CONCRETE_STEP_NUMBER_OF_SUCCESSORS_LIMIT) {
						dialog.updateGraphWithSuccessors();
					} else {
						Shell shell = dialog
								.createShell(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell());
						shell.open();
						// while (!shell.isDisposed())
						// if (!display.readAndDispatch())
						// display.sleep();
						// }
					}
				}
			}
		};
		performConcreteStepAction.setText("Perform Concrete Step");
		performConcreteStepAction.setToolTipText("Choose the next concrete state");
		performConcreteStepAction.setImageDescriptor(
				PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_OBJ_ADD));

		getConcreteSubGraphAction = new Action() {
			public void run() {
				ISelection selection = viewer.getSelection();
				Object obj = ((IStructuredSelection) selection).getFirstElement();
				if (obj instanceof Node) {
					Node node = (Node) obj;
					ModelNode modelNode = (ModelNode) node.getAttributes().get("element-model");
					NodeModelContentProviderConcrete contentProvider = (NodeModelContentProviderConcrete) viewer
							.getContentProvider();
					ArrayList<ModelNode> nodesToAdd = new ArrayList<ModelNode>();
					nodesToAdd.add(modelNode);
					contentProvider.addNodes(nodesToAdd, true /* add concrete subgraph */);
					viewer.refresh();
				}
			}
		};

		getConcreteSubGraphAction.setText("Get Concrete Sub Graph");
		getConcreteSubGraphAction
				.setToolTipText("Get concrete sub-graph starting from selected node, to" + " the configued depth");
		getConcreteSubGraphAction.setImageDescriptor(
				PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_OBJ_ADD));
	}

}
