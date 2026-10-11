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

// TODO: merge identical code segments in PopupConcreteHandler and PopupSymbolicHandler
package tau.smlab.syntech.counterstrategy.ui.handlers;

import static tau.smlab.syntech.counterstrategy.ui.Activator.PLUGIN_NAME;

import java.util.ArrayList;
import java.util.logging.Level;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.IHandler;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

import tau.smlab.syntech.counterstrategy.model.SymbolicCounterStrategyGraph;
import tau.smlab.syntech.counterstrategy.ui.model.ModelNode;
import tau.smlab.syntech.counterstrategy.ui.views.ConcreteGraphView;

public class PopupConcreteHandler extends PopupHandler implements IHandler {

	@Override
	public Object execute(ExecutionEvent event) {
		this.workbench = PlatformUI.getWorkbench();
		this.window = HandlerUtil.getActiveWorkbenchWindow(event);
		// Get the active WorkbenchPage
		this.activePage = this.window.getActivePage();
		// Get the project and file name from the initiating event if at all possible
		if (!extractProjectAndFileFromInitiatingEvent(event)) {
			return null;
		}

		// checking if there is exactly 1 "*.spectra" file.
		if (numSpectraSelected != 1) {
			// maybe should be: window = HandlerUtil.getActiveWorkbenchWindow(event);
			MessageDialog.openError(this.window.getShell(), PLUGIN_NAME,
					"Expected to be exactly 1 \"*.spectra\" file. please choose again.\n");
			return null;
		}

		// computing the graph
		SymbolicCounterStrategyGraph graph = null;
		try {
			graph = initGraph(Level.SEVERE);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(e.getMessage());
			return null;
		}
		if (graph == null)
			return null;

		return activateConcreteGraphView(graph, this.workbench, this.window, this.activePage, null, false);
	}

	/**
	 * switching to CounterStrategy perspective and showing the appropriate
	 * concrete graph in ConcreteGraph view or an accordingly error message.
	 * 
	 * @param graph
	 * @param workbench
	 *            - the active workbench, can simply be returned by
	 *            PlatformUI.getWorkbench().
	 * @param window
	 *            - the active window of eclipse, can simply be returned by
	 *            <code>workbench</code>.getActiveWorkbenchWindow(). It's also
	 *            possible to pass null instead once <code>workbench</code> is
	 *            passed appropriately.
	 * @param activePage
	 *            - the active WorkbenchPage, can simply be returned by
	 *            <code>window</code>.getActivePage(). It's also possible to
	 *            pass null instead once <code>workbench</code> is passed
	 *            appropriately.
	 * @param nodeToConcretize
	 *            - an optional parameter which indicates that this is a
	 *            symbolic node in the <code>graph</code> which has to be
	 *            concretized. If null passed instead, it refers as "Generate
	 *            Concrete Graph" has been chosen (no specific node has been
	 *            chosen to be concretized). If interactivePlay==true, this is
	 *            the node that need to be shown in the graph.
	 * @param interactivePlay
	 *            - if this is true, it indicates that the user chose "Play
	 *            Interactively" from the SymbolicGraphView and nodeToConcretize
	 *            is the single node need to be shown in the new concrete graph.
	 * @return null
	 */
	public static Object activateConcreteGraphView(SymbolicCounterStrategyGraph graph, IWorkbench workbench,
			IWorkbenchWindow window, IWorkbenchPage activePage, ModelNode nodeToConcretize, boolean interactivePlay) {
		if (window == null)
			window = workbench.getActiveWorkbenchWindow();
		if (activePage == null)
			activePage = window.getActivePage();
		// TODO - use the symbolicNodeIndex passed here to mark relevant node in the symbolic view
		
		ConcreteGraphView view = null;
		// setting focus on the ConcreteGraph view
		try {
			activePage = window.getActivePage();
			view = (ConcreteGraphView) activePage.showView(ConcreteGraphView.ID);
		} catch (PartInitException e) {
			e.printStackTrace();
			System.out.println(e.getMessage());
			return null;
		}

		try {
		/* Instruct the View module to display the concrete counter strategy. */
			if (nodeToConcretize == null)
				view.displayConcreteCounterStrategyModel(graph);
			else
				view.displayConcreteCounterStrategyModelWithSpecificNodeConcretization(graph, nodeToConcretize,
						interactivePlay);
		} catch (ArrayIndexOutOfBoundsException e) {
			MessageDialog.openError(window.getShell(), PLUGIN_NAME,
					"Error while computing concrete counter strategy graph.\nProbably, graph is too big.");
			e.printStackTrace();
			System.out.println(e.getMessage());
			return null;
		}
		return null;
	}

	/**
	 * updating the concrete graph in the relevant view with new node when performing a concrete step
	 * 
	 * @param graph
	 * @param workbench
	 *            - the active workbench, can simply be returned by
	 *            PlatformUI.getWorkbench().
	 * @param window
	 *            - the active window of eclipse, can simply be returned by
	 *            <code>workbench</code>.getActiveWorkbenchWindow(). It's also
	 *            possible to pass null instead once <code>workbench</code> is
	 *            passed appropriately.
	 * @param activePage
	 *            - the active WorkbenchPage, can simply be returned by
	 *            <code>window</code>.getActivePage(). It's also possible to
	 *            pass null instead once <code>workbench</code> is passed
	 *            appropriately.
	 * @param nodesToAdd
	 *            - The nodes to add to the graph
	 * @return null
	 */
	public static Object updateConcreteGraphView(SymbolicCounterStrategyGraph graph, IWorkbench workbench,
			IWorkbenchWindow window, IWorkbenchPage activePage, ArrayList<ModelNode> nodesToAdd) {
		if (window == null)
			window = workbench.getActiveWorkbenchWindow();

		ConcreteGraphView view = null;
		// setting focus on the ConcreteGraph view
		try {
			activePage = window.getActivePage();
			view = (ConcreteGraphView) activePage.showView(ConcreteGraphView.ID);
		} catch (PartInitException e) {
			e.printStackTrace();
			System.out.println(e.getMessage());
			return null;
		}

		try {
			// instruct the view to add the new node to the graph it displays
			view.updateConcreteCounterStartegyModelWithNode(graph, nodesToAdd);
		} catch (ArrayIndexOutOfBoundsException e) {
			MessageDialog.openError(window.getShell(), PLUGIN_NAME,
					"Error while computing concrete counter strategy graph.\nProbably, graph is too big.");
			e.printStackTrace();
			System.out.println(e.getMessage());
			return null;
		}
		return null;
	}

}
