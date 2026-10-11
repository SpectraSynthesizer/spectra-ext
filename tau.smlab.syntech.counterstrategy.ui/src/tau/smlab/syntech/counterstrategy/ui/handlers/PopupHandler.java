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

package tau.smlab.syntech.counterstrategy.ui.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

import static tau.smlab.syntech.counterstrategy.ui.Activator.PLUGIN_NAME;
import tau.smlab.syntech.bddgenerator.BDDGenerator;
import tau.smlab.syntech.bddgenerator.BDDGenerator.TraceInfo;
import tau.smlab.syntech.checks.Checker;
import tau.smlab.syntech.counterstrategy.model.JusticeViolationGraph;
import tau.smlab.syntech.counterstrategy.model.SymbolicCounterStrategyGraph;
import tau.smlab.syntech.counterstrategy.ui.preferences.PreferencePage;
import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gameinputtrans.TranslationProvider;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.games.gr1.GR1Game;
import tau.smlab.syntech.games.rabin.RabinGame;
import tau.smlab.syntech.jtlv.BDDPackage;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.spectragameinput.ErrorsInSpectraException;
import tau.smlab.syntech.spectragameinput.SpectraInputProvider;
import tau.smlab.syntech.spectragameinput.SpectraTranslationException;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;

/**
 * This handler will obtain the core information about files from a package explorer popup menu.
 */
public class PopupHandler extends AbstractHandler {
	protected IWorkbench workbench = null;
	public IWorkbenchWindow window;
	protected IWorkbenchPage activePage;
	
	/* the following fields are arrays of Objects representing
	 * each of the objects selected (in the navigator menu or
	 * package explorer) by the user. */
	private IProject[] theProject; // not necessary, just for future compatibility/use
	private IResource[] theResource;
	private IFile[] theFile;

	private String[] workspaceName = null; // not necessary, just for future compatibility/use
	private String[] projectName = null; // not necessary, just for future compatibility/use
	private String[] fileName = null; // not necessary, just for future compatibility/use
	
	/** array of strings represent the locations of the selected objects. */
	public String[] location = null; // not necessary, just for future compatibility/use
	/** array of strings represent the locations of the selected "*.spectra" objects. */
	public String[] spectraLocation = null; // not necessary, just for future compatibility/use
	/**
	 * array of strings represent the locations of the selected "*.spectra"
	 * objects. But the path is the local path (starts with the project name...)
	 * relative to the workspace
	 */
	public String[] spectraLocalLocationPath = null;
	
	/** the number of objects selected by the user */
	public int numSelected = 0;
	/** the number of "*.spectra" objects selected by the user */
	public int numSpectraSelected = 0;

	public PopupHandler() {
		// Empty constructor
	}

	public Object execute(ExecutionEvent event) throws ExecutionException {
		// Get the project and file name from the initiating event if at all possible
		if (!extractProjectAndFileFromInitiatingEvent(event)) {
			return null;
		}

		StringBuilder builder = new StringBuilder("");
		for (int i = 0; i < numSelected; i++) {
			builder.append("Workspace=" + workspaceName[i] + "\nProject=" + projectName[i] + "\nFile=" + fileName[i] + "\n\n");
		}
		MessageDialog.openInformation(this.window.getShell(), PLUGIN_NAME, "File Details.\n\n" + builder.toString());
		return null;
	}
	
	/**
	 * fetching the array properties of the selected objects by the user. <dt>
	 * <b>Further Instructions:</b></dt> <dd>this method should be called initially
	 * at {@link #execute(ExecutionEvent)}, because it obtains the right-now selection of the user.</dd>
	 * @param event
	 * @return true iff fetching the objects' properties successes completely.
	 */
	protected boolean extractProjectAndFileFromInitiatingEvent(ExecutionEvent event) {
		/*
		 * The execute method of the handler is invoked to handle the event.
		 * As we only contribute to Package Explorer views we expect to get a selection tree event.
		 */
		this.workbench = PlatformUI.getWorkbench();
		this.window = HandlerUtil.getActiveWorkbenchWindow(event);
		// Get the active WorkbenchPage
		this.activePage = this.window.getActivePage();

		// Get the Selection from the active WorkbenchPage page
		ISelection selection = this.activePage.getSelection();
		if (selection instanceof ITreeSelection) {
			TreeSelection treeSelection = (TreeSelection) selection;
			TreePath[] treePaths = treeSelection.getPaths();
			numSelected = treePaths.length;
			
			theProject = new IProject[numSelected];
			theResource = new IResource[numSelected];
			theFile = new IFile[numSelected];
			workspaceName = new String[numSelected];
			projectName = new String[numSelected];
			fileName = new String[numSelected];
			location = new String[numSelected];
			List<String> spectraLocationList = new ArrayList<String>();
			List<String> spectraLocalLocationPathList = new ArrayList<String>();

			for (int i = 0; i < numSelected; i++) {
				TreePath treePath = treePaths[i];
				// The TreePath contains a series of segments in our usage:
				// o The first segment is usually a project
				// o The last segment generally refers to the file

				// The first segment should be a IProject
				Object firstSegmentObj = treePath.getFirstSegment();
				this.theProject[i] = (IProject) ((IAdaptable) firstSegmentObj)
						.getAdapter(IProject.class);
				if (this.theProject[i] == null) {
					MessageDialog.openError(this.window.getShell(), PLUGIN_NAME, getClassHierarchyAsMsg(
						"Expected the first segment to be IAdapatable to an IProject.\nBut got the following class hierarchy instead.",
						"Make sure to directly select a file.", firstSegmentObj));
					return false;
				}

				// The last segment should be an IResource
				Object lastSegmentObj = treePath.getLastSegment();
				this.theResource[i] = (IResource) ((IAdaptable) lastSegmentObj).getAdapter(IResource.class);
				if (this.theResource[i] == null) {
					MessageDialog.openError(this.window.getShell(), PLUGIN_NAME, getClassHierarchyAsMsg(
						"Expected the last segment to be IAdapatable to an IResource.\nBut got the following class hierarchy instead.",
						"Make sure to directly select a file.", firstSegmentObj));
					return false;
				}

				// As the last segment is an IResource we should be able to get
				// an IFile reference from it
				this.theFile[i] = (IFile) ((IAdaptable) lastSegmentObj).getAdapter(IFile.class);

				// Extract additional information from the IResource and
				// IProject
				this.workspaceName[i] = this.theResource[i].getWorkspace().getRoot().getLocation().toOSString();
				this.projectName[i] = this.theProject[i].getName();
				this.fileName[i] = this.theResource[i].getName();
				location[i] = this.theResource[i].getLocation().toString();
				if (location[i].endsWith(".spectra")){
					spectraLocationList.add(location[i]);
					spectraLocalLocationPathList.add(this.theFile[i].getFullPath().toOSString());
				}
			}
			spectraLocation = spectraLocationList.toArray(new String[spectraLocationList.size()]);
			spectraLocalLocationPath = spectraLocalLocationPathList.toArray(new String[spectraLocalLocationPathList.size()]);
			numSpectraSelected = spectraLocation.length;
			return true;
		} else if (selection == null){
			MessageDialog.openError(this.window.getShell(), "Unexpected Selection",
					"Expected a TreeSelection but got null instead.\nProcessing Terminated.");
		} else {
			String selectionClass = selection.getClass().getSimpleName();
			MessageDialog.openError(this.window.getShell(), "Unexpected Selection Class",
					"Expected a TreeSelection but got a " + selectionClass + " instead.\nProcessing Terminated.");
		}

		return false;
	}

	@SuppressWarnings("rawtypes")
	private static String getClassHierarchyAsMsg(String msgHeader, String msgTrailer, Object theObj) {
		String msg = msgHeader + "\n\n";

		Class theClass = theObj.getClass();
		while (theClass != null) {
			msg = msg + "Class=" + theClass.getName() + "\n";
			Class[] interfaces = theClass.getInterfaces();
			for (Class theInterface : interfaces) {
				msg = msg + "    Interface=" + theInterface.getName() + "\n";
			}
			theClass = theClass.getSuperclass();
		}

		msg = msg + "\n" + msgTrailer;

		return msg;
	}

	/**
	 * executes the commands for computing the graph.
	 * 
	 * @return
	 */
	protected SymbolicCounterStrategyGraph initGraph(Level logLevel) throws Exception {
		BDDPackage.setCurrPackage(BDDPackage.CUDD);
		Env.resetEnv();
		GameModel model = getGameModel(spectraLocalLocationPath[0]);
		RabinGame rg;
		
		Checker checker = new Checker();
		List<BehaviorInfo> l = checker.computeEnvBadPrimesSpecs(model);
		// l should be empty - else open error message
		if (!(l.isEmpty())) {
			MessageDialog.openError(this.window.getShell(), PLUGIN_NAME, "Bad environment primed variables.");
			return null;
		}
		GR1Game g = new GR1Game(model);

		// if is realizable show message
		if (g.checkRealizability()) {
			MessageDialog.openInformation(this.window.getShell(), PLUGIN_NAME, "Game is realizable.");
			return null;
		}
		g.free();

		List<BehaviorInfo> l1 = checker.computeUnsatSafetyCore(model);
		if (!(l1.isEmpty())) {
			MessageDialog.openInformation(this.window.getShell(), PLUGIN_NAME,
					"Specification is unsatisfiable - safeties / initial conflicting.");
			return null;
		}

		rg = new RabinGame(model);
		if (!rg.checkRealizability()) { // this is (above) a bug - show a message
			MessageDialog.openError(this.window.getShell(), PLUGIN_NAME,
					"Environment does not win - specification is neither realizable or unrealizable "
							+ " - impossible state");
			return null;
		}

		JusticeViolationGraph jvg = new JusticeViolationGraph(rg, model, logLevel, true);
		jvg.computeGraph();
		
		if (PreferencePage.isMergeAttractorsChecked()) {
			jvg.mergedAttractors();
		}

		SymbolicCounterStrategyGraph csg = new SymbolicCounterStrategyGraph(jvg, logLevel);
		return csg;
	}

	private GameModel getGameModel(String fileName) throws ErrorsInSpectraException, SpectraTranslationException {
/*		SpectraInputProvider sip = new SpectraInputProvider();
		GameInput gi = sip.getGameInput(fileName); */
		GameInput gi = SpectraInputProvider.getGameInput(fileName);
/*		SpectraInputProviderNoIDE sip = new SpectraInputProviderNoIDE();
		GameInput gi = sip.getGameInput(fileName); */
		TranslationProvider.translate(gi);
		return BDDGenerator.generateGameModel(gi, TraceInfo.ALL);
	}
}