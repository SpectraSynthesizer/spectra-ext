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

package tau.smlab.syntech.games.gr1.wellseparation.ui;

import static tau.smlab.syntech.games.gr1.wellseparation.ui.Activator.PLUGIN_NAME;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.IOConsoleOutputStream;

import tau.smlab.syntech.bddgenerator.BDDGenerator;
import tau.smlab.syntech.bddgenerator.BDDGenerator.TraceInfo;
import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gameinputtrans.TranslationProvider;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.games.controller.enumerate.ConcreteControllerConstruction;
import tau.smlab.syntech.games.controller.enumerate.printers.MAAMinimizeAutomatonPrinter;
import tau.smlab.syntech.games.controller.enumerate.printers.SimpleTextPrinter;
import tau.smlab.syntech.games.gr1.GR1ConceteControllerConstructionSkip;
import tau.smlab.syntech.games.gr1.GR1Memory;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker.EnvSpecPart;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker.Positions;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker.Systems;
import tau.smlab.syntech.jtlv.BDDPackage;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.spectragameinput.ErrorsInSpectraException;
import tau.smlab.syntech.spectragameinput.SpectraTranslationException;
import tau.smlab.syntech.spectragameinput.SpectraInputProvider;
import tau.smlab.syntech.spectragameinput.translator.Tracer;
import tau.smlab.syntech.ui.extension.SyntechAction;
import tau.smlab.syntech.ui.jobs.MarkerKind;
import tau.smlab.syntech.ui.preferences.PreferencePage;

import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

public class WellSeparationMenu extends SyntechAction<WellSeparationActionID> {

	private static List<Integer> traceIDListInCore = new ArrayList<Integer>();
	protected static IFile previousFileWithMarkers = null;

	@Override
	public String getPluginName() {
		return PLUGIN_NAME;
	}

	@Override
	public WellSeparationActionID[] getActionItems() {
		return WellSeparationActionID.values();
	}

	@Override
	public void run(WellSeparationActionID actionID, IFile specFile) {
		GameInput gi = null;

		BDDPackage.setCurrPackage(PreferencePage.getBDDPackageSelection(),
				PreferencePage.getBDDPackageVersionSelection());
		if (PreferencePage.isReorderEnabled()) {
			Env.enableReorder();
		} else {
			Env.disableReorder();
		}
		try {
			gi = SpectraInputProvider.getGameInput(specFile.getFullPath().toString());
			TranslationProvider.translate(gi);
		} catch (ErrorsInSpectraException e1) {
			// Error in spectra
			e1.printStackTrace();
		} catch (SpectraTranslationException e2) {
			// Spectra translate exception
			e2.printStackTrace();
		}
		GameModel model = BDDGenerator.generateGameModel(gi, TraceInfo.ALL);

		console.activate();
		clearMarkers(specFile);

		WellSeparationChecker c = new WellSeparationChecker();

		switch (actionID) {

		case DIAGNOSE:

			List<String> res = new ArrayList<String>();
			boolean hasGF = (model.getEnv().justiceNum() != 0);

			try {
				if (tau.smlab.syntech.games.gr1.wellseparation.ui.preferences.PreferencePage.getWellSepIncludeSys()) {
					consolePrinter
							.println("Checking well-separation when considering system guarantees (see Preferences).");
					res = c.diagnose(model, Systems.SPEC);
				} else {
					consolePrinter
							.println("Checking well-separation ignoring all system guarantees (see Preferences).");
					res = c.diagnose(model, Systems.NONE);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			if (res.isEmpty()) {
				// this.isWellSeparated = true;
				consolePrinter.println("Environment specification is well-separated.");
			} else {
				// this.isWellSeparated = false;
				consolePrinter.println("Result: The environment specification is non-well-separated.");
				for (String info : res) {
					// filter out justice reasons when the only justice is GF TRUE added by
					// algorithm
					if (!info.contains(EnvSpecPart.JUSTICE.toString()) || hasGF) {
						String message = "  The system can force the environment to violate ";
						if (info.contains(EnvSpecPart.INI.toString())) {
							message += "some initial assumption ";
						} else if (info.contains(EnvSpecPart.SAFETY.toString())) {
							message += "some safety assumption (G ...) ";
						} else {
							message += "some justice assumption (GF ...) ";
						}
						if (info.contains(Positions.ALL.toString())) {
							message += " from all initial states.";
						} else {
							message += " from some reachable states.";
						}
						consolePrinter.println(message);
					}
				}

				if (c.hasWitnessInfo()) {
					consolePrinter.println("The analysis computed a witness:");
					for (String witness : c.getWitnessInfo().keySet()) {
						String message = "  The reachable state " + witness
								+ " (partial assignment) forces the environment to violate the highlighted assumptions.";
						for (int traceId : c.getWitnessInfo().get(witness)) {
							this.createMarker(traceId, message, MarkerKind.CUSTOM_TEXT_MARKER);
						}
						consolePrinter.println(message);
					}
				}
			}
			break;

		case COMPUTE_CORE:
			res = new ArrayList<String>();
			Systems systems;
			try {
				if (tau.smlab.syntech.games.gr1.wellseparation.ui.preferences.PreferencePage.getWellSepIncludeSys()) {
					consolePrinter.println(
							"Computing non-well-separated core when considering system guarantees (see Preferences).");
					systems = Systems.SPEC;
				} else {
					consolePrinter.println(
							"Computing non-well-separated core when ignoring all system guarantees (see Preferences).");
					systems = Systems.NONE;
				}
				res = c.diagnose(model, systems);
			} catch (Exception e) {
				e.printStackTrace();
				break;
			}

			if (res.isEmpty()) {
				consolePrinter.println("No core computed because the environment specification is well-separated.");
				break;
			}

			Positions positions;
			if (res.get(0).contains(Positions.ALL.toString())) {
				positions = Positions.ALL;
			} else {
				positions = Positions.REACH;
			}

			List<BehaviorInfo> behaviorInfo;

			try {
				behaviorInfo = c.computeCore(model, systems, positions);
			} catch (Exception e) {
				e.printStackTrace();
				break;
			}

			if (!behaviorInfo.isEmpty()) {
				consolePrinter.println("Computed non-well-separated core with " + behaviorInfo.size()
						+ " elements. See markers in specification file.");

				PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
					public void run() {
						try {
							PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
									.showView("wellSeparatedCoreMarker");
						} catch (PartInitException e) {
							System.err.println("View id not found");
						}
					}
				});
			} else {
				consolePrinter.println("There seems to be a bug in the core computation. Please contact us at ....");
			}

			clearMarkers(specFile);
			createMarker(behaviorInfo, MarkerKind.WELL_SEP_CORE);

			break;

		case COMPUTE_COUNTER_STRATEGY:

			GR1Memory memory = null;
			try {
				if (tau.smlab.syntech.games.gr1.wellseparation.ui.preferences.PreferencePage.getWellSepIncludeSys()) {
					consolePrinter.println(
							"Computing non-well-separated strategy when considering system guarantees (see Preferences).");
					memory = c.computeNonWellSepGame(model, Systems.SPEC);
				} else {
					consolePrinter.println(
							"Computing non-well-separated strategy when ignoring all system guarantees (see Preferences).");
					memory = c.computeNonWellSepGame(model, Systems.NONE);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			if (memory != null) {
				// this.isWellSeparated = false;
				consolePrinter.println(
						"Initial states below are all reachable states from where the system can force the environment to violate at least one assumption.");
				ConcreteControllerConstruction cc = new GR1ConceteControllerConstructionSkip(memory, model);
				IOConsoleOutputStream cout = console.newOutputStream();
				PrintStream out = new PrintStream(cout);
				try {
					if ("CMP".equals(PreferencePage.getConcreteControllerFormat())) {
						MAAMinimizeAutomatonPrinter.REMOVE_DEAD_STATES = false;
						new MAAMinimizeAutomatonPrinter(model).printController(out, cc.calculateConcreteController());
					} else if ("JTLV".equals(PreferencePage.getConcreteControllerFormat())) {
						new SimpleTextPrinter().printController(out, cc.calculateConcreteController());
					}
					out.close();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				// this.isWellSeparated = true;
				// this.coreSize = 0;
				consolePrinter.println("No strategy computed because the environment specification is well-separated.");
			}

			clearMarkers(specFile);

			break;

		default:
			break;
		}

		model.free();
	}

	/**
	 * deletes all markers of all SYNTECH MarkerKind(s)
	 */
	public void clearMarkers(IFile specIfile) {
		traceIDListInCore.clear();

		for (MarkerKind k : MarkerKind.values()) {
			try {
				specIfile.deleteMarkers(k.getMarkerID(), true, IResource.DEPTH_ZERO);
			} catch (CoreException e) {
			}
		}
	}

	/**
	 * create a marker for an element with the given traceId
	 * 
	 * @param traceId
	 * @param message
	 * @param kind
	 */
	public void createMarker(List<BehaviorInfo> infos, MarkerKind kind) {
		if (infos != null && infos.size() > 0) {
			if (previousFileWithMarkers != null) {
				clearMarkers(previousFileWithMarkers);

			}
			previousFileWithMarkers = specFile;
		}
		for (BehaviorInfo info : infos) {
			createMarker(info.traceId, kind.getMessage(), kind);
		}
	}

	public void createMarker(int traceId, String message, MarkerKind kind) {
		EObject o = Tracer.getTarget(traceId);
		traceIDListInCore.add(traceId);
		if (o != null) {
			INode node = NodeModelUtils.getNode(o);
			try {
				IMarker marker = specFile.createMarker(kind.getMarkerID());
				marker.setAttribute(IMarker.MESSAGE, message);
				marker.setAttribute(IMarker.PRIORITY, IMarker.PRIORITY_NORMAL);
				marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO);
				marker.setAttribute(IMarker.LOCATION, node.getStartLine());
				marker.setAttribute(IMarker.CHAR_START, node.getOffset());
				marker.setAttribute(IMarker.CHAR_END, node.getEndOffset());
			} catch (CoreException e) {
			}
		}
	}
}
