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

import java.util.logging.Level;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.IHandler;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

import static tau.smlab.syntech.counterstrategy.ui.Activator.PLUGIN_NAME;

import tau.smlab.syntech.counterstrategy.model.SymbolicCounterStrategyGraph;
import tau.smlab.syntech.counterstrategy.ui.views.SymbolicGraphView;

public class PopupSymbolicHandler extends PopupHandler implements IHandler {
	
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
		}
		if (graph == null)
			return null;


		SymbolicGraphView view = null;
		// setting focus on the SymbolicGraph view
		try {
			view = (SymbolicGraphView) this.activePage.showView(SymbolicGraphView.ID);
		} catch (PartInitException e) {
			e.printStackTrace();
			System.out.println(e.getMessage());
			return null;
		}

		/* Instruct the View module to display the symbolic counter strategy. */
		view.displaySymbolicCounterStrategyModel(graph);
		return null;
	}

}
