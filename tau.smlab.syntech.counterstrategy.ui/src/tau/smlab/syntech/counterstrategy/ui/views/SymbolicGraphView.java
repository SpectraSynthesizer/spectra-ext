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

package tau.smlab.syntech.counterstrategy.ui.views;

import org.eclipse.gef.graph.Node;
import org.eclipse.gef.layout.algorithms.TreeLayoutAlgorithm;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import tau.smlab.syntech.counterstrategy.model.SymbolicCounterStrategyGraph;
import tau.smlab.syntech.counterstrategy.ui.InteractivePlayConsole;
import tau.smlab.syntech.counterstrategy.ui.handlers.PopupConcreteHandler;
import tau.smlab.syntech.counterstrategy.ui.model.ModelNode;
import tau.smlab.syntech.counterstrategy.ui.model.NodeModelContentProvider;

public class SymbolicGraphView extends CounterStrategyView {

	/** the identifier of the view. */
	public static final String ID = "tau.smlab.syntech.counterstrategy.ui.views.SymbolicGraph";

	/** actions for clicking options */
	private Action concretizeNodeAction;
	private Action playInteractivelyAction;

	/** empty constructor. A viewer that shows a symbolic graph. */
	public SymbolicGraphView() {
	}

	@Override
	protected void setContentProviderForNodes() {
		viewer.setContentProvider(new NodeModelContentProvider());
	}

	/**
	 * creating the graph in the view with "*.model" package.
	 * 
	 * @param symbolicGraph
	 */
	public void displaySymbolicCounterStrategyModel(SymbolicCounterStrategyGraph symbolicGraph) {
		viewer.setInput(symbolicGraph);
		viewer.setLayoutAlgorithm(new TreeLayoutAlgorithm(TreeLayoutAlgorithm.TOP_DOWN));
		// viewer.applyLayout();
	}

	@Override
	public void dispose() {
		super.dispose();
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
					concretizeNodeAction.setEnabled(false);
					playInteractivelyAction.setEnabled(false);
				} else if (((IStructuredSelection) selection).getFirstElement() instanceof Node) {
					concretizeNodeAction.setEnabled(true);
					playInteractivelyAction.setEnabled(true);
				} else {
					concretizeNodeAction.setEnabled(false);
					playInteractivelyAction.setEnabled(false);
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
		manager.add(concretizeNodeAction);
		manager.add(playInteractivelyAction);
	}

	protected void addActionsToContextMenu(IMenuManager manager) {
		manager.add(concretizeNodeAction);
		manager.add(playInteractivelyAction);
	}

	@Override
	protected void makeActions() {
		super.makeActions();

		concretizeNodeAction = new Action() {
			public void run() {
				ISelection selection = viewer.getSelection();
				Object obj = ((IStructuredSelection) selection).getFirstElement();
				if (obj instanceof Node) {
					Node node = (Node) obj;
					ModelNode modelNode = (ModelNode) node.getAttributes().get("element-model");
					PopupConcreteHandler.activateConcreteGraphView(
							((NodeModelContentProvider) (viewer.getContentProvider())).getSelfGraph(),
							PlatformUI.getWorkbench(), null, null, modelNode, false);
				}
			}
		};
		concretizeNodeAction.setText("Concretize Node");
		/*
		 * The concrete graph which will be displayed will be of the depth the user
		 * configured in Eclipse preferences, or less if the graph is smaller than the
		 * configured depth.
		 */
		concretizeNodeAction.setToolTipText("concretize the current node");
		concretizeNodeAction.setImageDescriptor(
				PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK));

		doubleClickAction = new Action() {
			public void run() {
				playInteractivelyAction.run();
			}
		};

		playInteractivelyAction = new Action() {
			public void run() {
				ISelection selection = viewer.getSelection();
				Object obj = ((IStructuredSelection) selection).getFirstElement();
				if (obj instanceof Node) {
					Node node = (Node) obj;
					ModelNode modelNode = (ModelNode) node.getAttributes().get("element-model");
					InteractivePlayConsole dialog = new InteractivePlayConsole(
							((NodeModelContentProvider) viewer.getContentProvider()).getSelfGraph(), modelNode);
					// if (dialog.isVariablesListEmpty()) {
					// there is no choice, probably this is the initial node.
					// TODO
					// } else {
					Shell shell = dialog.createShell(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell());
					shell.open();
					// while (!shell.isDisposed())
					// if (!display.readAndDispatch())
					// display.sleep();
					// }
				}
			}
		};
		playInteractivelyAction.setText("Play Interactively");
		playInteractivelyAction.setToolTipText("Choose assignments to invariants and show the selected state");
		playInteractivelyAction.setImageDescriptor(
				PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_OBJ_ADD));
	}
}