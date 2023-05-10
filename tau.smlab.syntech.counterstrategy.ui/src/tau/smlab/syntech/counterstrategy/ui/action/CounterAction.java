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

package tau.smlab.syntech.counterstrategy.ui.action;

import static tau.smlab.syntech.counterstrategy.ui.Activator.PLUGIN_NAME;


import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.FileEditorInput;
import tau.smlab.syntech.bddgenerator.BDDGenerator;
import tau.smlab.syntech.bddgenerator.BDDGenerator.TraceInfo;
import tau.smlab.syntech.checks.Checker;
import tau.smlab.syntech.counterstrategy.model.JusticeViolationGraph;
import tau.smlab.syntech.counterstrategy.model.SymbolicCounterStrategyGraph;
import tau.smlab.syntech.counterstrategy.ui.handlers.PopupConcreteHandler;
import tau.smlab.syntech.counterstrategy.ui.preferences.PreferencePage;
import tau.smlab.syntech.counterstrategy.ui.views.SymbolicGraphView;
import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gameinputtrans.TranslationProvider;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.games.rabin.RabinGame;
import tau.smlab.syntech.jtlv.BDDPackage;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.spectragameinput.ErrorsInSpectraException;
import tau.smlab.syntech.spectragameinput.SpectraInputProvider;
import tau.smlab.syntech.spectragameinput.SpectraTranslationException;


public class CounterAction {
	private IFile specFile;
	private IWorkbench workbench;
	private IWorkbenchWindow window;
	private IWorkbenchPage activePage;
	private Shell shell;	
	private SymbolicCounterStrategyGraph graph; 
	GameModel model;
	GameInput gi;
	
	public void run(String action) {
	  
	  if (!setSpecFileAndEnvironmentFields())
	  {
	    return;
	  }
	  
	  new Job("Compute Justice Violation Graph") {

      @Override
      public IStatus run(IProgressMonitor monitor) { 	
    	// computing the graph
        try {
          initGraph(Level.SEVERE);
        } catch (Exception e) {
          e.printStackTrace();
          System.out.println(e.getMessage());
          return Status.CANCEL_STATUS;
        }
        if (graph == null)
          return Status.CANCEL_STATUS;
        
        Display.getDefault().syncExec(new Runnable() {
          
          @Override
          public void run() {
            if (action.equals(ActionsID.SYMBOLIC)) {

              SymbolicGraphView view = null;
              // setting focus on the SymbolicGraph view
              try {
                view = (SymbolicGraphView) activePage.showView(SymbolicGraphView.ID);
              } catch (PartInitException e) {
                e.printStackTrace();
                System.out.println(e.getMessage());
              }
              view.displaySymbolicCounterStrategyModel(graph);
            } else if (action.equals(ActionsID.CONCRETE)) {
              PopupConcreteHandler.activateConcreteGraphView(graph, workbench, window, activePage, null,
                  false);
            } else {
              System.err.println("Unhandled action id: " + action);
            }    
          }
        });     
        
        return Status.OK_STATUS;
      }

    }.schedule();
    
	}
	
	/**
	 * @return true if setting succeeded, false otherwise.
	 */
	private boolean setSpecFileAndEnvironmentFields() {
		workbench = PlatformUI.getWorkbench();
		window = workbench.getActiveWorkbenchWindow();
		shell = window.getShell();
		activePage = window.getActivePage();
		List<IFile> selectedFiles = new ArrayList<>();
		
		if (window == null) {
			return false;
		}

		ISelectionService selectionService = window.getSelectionService();
		if (selectionService == null) {
			return false;
		}

		ISelection selection = selectionService.getSelection();
		if (selection == null) {
			return false;
		}

		// selected on text view (the code window)
		if (selection instanceof TextSelection)
		{
			 IEditorPart editor = activePage.getActiveEditor();
			 IFile original = ((FileEditorInput)editor.getEditorInput()).getFile();
			 specFile = original;
		}
		
		// selected on explorer view
		else if (selection instanceof IStructuredSelection) {
			IStructuredSelection structSelection = (IStructuredSelection) selection;
			List<?> selected = structSelection.toList();
			for (Object object : selected) {
				if (object instanceof IFile) {
					IFile file = (IFile) object;
					selectedFiles.add(file);
				}
			}
			if (selectedFiles == null || selectedFiles.size() == 0 || selectedFiles.size() >= 2)
			{
				MessageDialog.openInformation(shell, PLUGIN_NAME, "Please select only one .spectra file.");
				return false;
			}
			else
			{
				specFile = selectedFiles.get(0);			
			}
		}
		
		
		return true;
	}

	/**
	 * This function initiates the SymbolicCounterStrategyGraph "from scratch" and places it in graph
	 * @param logLevel - the log level to use in the initiation process
	 * @throws Exception
	 */
	private void initGraph(Level logLevel) throws Exception {
		initEnv();
		
		try {
			initGameModel();
		} catch (ErrorsInSpectraException | SpectraTranslationException e) {
			e.printStackTrace();
		}
		
		graph = initGraphFromModel(logLevel);
	}
	
	/**
	 * This function sets the BDDPackage and puts the Env into a ready state for computing the graph 
	 */
	private void initEnv() {
		BDDPackage.setCurrPackage(tau.smlab.syntech.ui.preferences.PreferencePage.getBDDPackageSelection(), tau.smlab.syntech.ui.preferences.PreferencePage.getBDDPackageVersionSelection());
		Env.resetEnv();
		Env.enableReorder();
	}
	
	/**
	 * This function computes the SymbolicCounterStrategyGraph based on the GameModel currently in the model parameter and returns it
	 * @param logLevel - the log level to use in the initiation process
	 * @return - the computed graph
	 * @throws Exception
	 */
	private SymbolicCounterStrategyGraph initGraphFromModel(Level logLevel) throws Exception {

		RabinGame rg;

		Checker checker = new Checker();
		List<BehaviorInfo> l = checker.computeEnvBadPrimesSpecs(model);
		// l should be empty - else open error message
		if (!(l.isEmpty())) {
			MessageDialog.openError(shell, PLUGIN_NAME, "Bad environment primed variables.");
			return null;
		}

		List<BehaviorInfo> l1 = checker.computeUnsatSafetyCore(model);
		if (!(l1.isEmpty())) {
			MessageDialog.openInformation(shell, PLUGIN_NAME,
					"Specification is unsatisfiable - safeties / initial conflicting.");
			return null;
		}

		rg = new RabinGame(model);
		if (!rg.checkRealizability()) {
			MessageDialog.openInformation(shell, PLUGIN_NAME, "Game is realizable.");
			return null;
		}
		Env.disableReorder();
		
		JusticeViolationGraph jvg = new JusticeViolationGraph(rg, model, logLevel, false /*calcAssumpGraph*/);
		jvg.computeGraph();

		if (PreferencePage.isMergeAttractorsChecked()) {
			jvg.mergedAttractors();
		}

		SymbolicCounterStrategyGraph csg = new SymbolicCounterStrategyGraph(jvg, logLevel);
		return csg;
	}

	/**
	 * This function sets the gameInput gi based on the specification path in specFile and generates the gameModel model based on this GameInput.
	 * @throws ErrorsInSpectraException
	 * @throws SpectraTranslationException
	 */
	private void initGameModel() throws ErrorsInSpectraException, SpectraTranslationException {
		if (gi == null) {
			gi = getGameInput(specFile.getFullPath().toOSString());
		}
		
		model = BDDGenerator.generateGameModel(gi, TraceInfo.ALL);
	}
	
	/**
	 * This function receives a relativePath of a specification, computes a GameInput based on that specification and returns it.
	 * @param relativePath - the relativePath of the specification
	 * @return - the computed GameInput 
	 * @throws ErrorsInSpectraException
	 * @throws SpectraTranslationException
	 */
	private GameInput getGameInput(String relativePath) throws ErrorsInSpectraException, SpectraTranslationException {
		/*
		 * SpectraInputProvider sip = new SpectraInputProvider(); GameInput gi =
		 * sip.getGameInput(fileName);
		 */
		GameInput gi = SpectraInputProvider.getGameInput(relativePath);
		/*
		 * SpectraInputProviderNoIDE sip = new SpectraInputProviderNoIDE();
		 * GameInput gi = sip.getGameInput(fileName);
		 */
		TranslationProvider.translate(gi);
		return gi;
	}

}
