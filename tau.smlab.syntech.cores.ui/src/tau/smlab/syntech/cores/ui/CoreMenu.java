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

package tau.smlab.syntech.cores.ui;

import static tau.smlab.syntech.cores.ui.Activator.PLUGIN_NAME;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import tau.smlab.syntech.bddgenerator.BDDGenerator;
import tau.smlab.syntech.bddgenerator.BDDGenerator.TraceInfo;
import tau.smlab.syntech.checks.ddmin.AbstractDdmin;
import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gameinputtrans.TranslationProvider;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.util.EnvTraceInfoBuilder;
import tau.smlab.syntech.gamemodel.util.SysTraceInfoBuilder;
import tau.smlab.syntech.gamemodel.util.TraceIdentifier;
import tau.smlab.syntech.games.gr1.GR1GameExperiments;
import tau.smlab.syntech.jtlv.BDDPackage;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.jtlv.env.module.ModuleBDDField;
import tau.smlab.syntech.spectragameinput.ErrorsInSpectraException;
import tau.smlab.syntech.spectragameinput.SpectraTranslationException;
import tau.smlab.syntech.spectragameinput.SpectraInputProvider;
import tau.smlab.syntech.spectragameinput.translator.Tracer;
import tau.smlab.syntech.ui.extension.SyntechAction;
import tau.smlab.syntech.ui.jobs.MarkerKind;
import tau.smlab.syntech.ui.preferences.PreferencePage;
import tau.smlab.syntech.cores.AllCoresPunchAlgorithm;
import tau.smlab.syntech.cores.AllUnrealizebleCores;
import tau.smlab.syntech.cores.DdminUnrealizableVarsCore;
import tau.smlab.syntech.cores.QuickCore;
import tau.smlab.syntech.cores.util.Checker;
import tau.smlab.syntech.cores.util.CoreOutput;
import tau.smlab.syntech.cores.util.RealizabilityCheck;
import tau.smlab.syntech.cores.util.RealizabilityCheck.GameType;

import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

/**
 * This class handles all the core menu options
 * 
 * @author shalom
 *
 */

public class CoreMenu extends SyntechAction<CoresActionID> {

	private static List<Integer> traceIDListInCore = new ArrayList<Integer>();
	private static TraceIdentifier ti = null;

	protected static IFile previousFileWithMarkers = null;

	@Override
	public String getPluginName() {
		return PLUGIN_NAME;
	}

	@Override
	public CoresActionID[] getActionItems() {
		return CoresActionID.values();
	}

	@Override
	public void run(CoresActionID actionID, IFile specFile) {
		// TODO Auto-generated method stub
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

		GameModel gm = BDDGenerator.generateGameModel(gi, TraceInfo.ALL, false,
				PreferencePage.getTransFuncSelection(true));
		ti = new TraceIdentifier(gm);

		boolean prevUseMemory = GR1GameExperiments.WITH_MEMORY;
		GR1GameExperiments.WITH_MEMORY = false; // no need for memory in these menu options because we only check for
												// realizability

		console.activate();
		clearMarkers();

		PreferencePage.setOptSelection();

		boolean quickCore = tau.smlab.syntech.cores.ui.preferences.PreferencePage.getUseQuickCore();
		RealizabilityCheck.checkType = (tau.smlab.syntech.cores.ui.preferences.PreferencePage.useGR1Realizability())
				? GameType.GR1_GAME
				: GameType.RABIN_GAME;
		RealizabilityCheck.useCUDD = PreferencePage.getBDDPackageSelection().equals(BDDPackage.CUDD);

		boolean realizable = RealizabilityCheck.isRealizable(gm);

		final SysTraceInfoBuilder builder = new SysTraceInfoBuilder(gm);

		CoreOutput<Integer> allCoresOutputLog = new CoreOutput<Integer>("", consolePrinter.getPrintStream()) {
			@Override
			public void writeBegin() {
				output.println("Intermediate results of cores and all cores intersection:");
			}

			@Override
			public void writeEnd() {
			}

			@Override
			public void writeCoreIntersection(List<Integer> inter, int checks, int actual) {
				if (inter.size() == 0) {
					output.println("The intersection of all the cores is empty.");
				} else {
					output.println("Intersection: " + TraceIdentifier.formatLines(inter)
							+ ". See markers in specification file.");
					for (Integer b : inter) {
						createMarker(b, MarkerKind.UNREAL_CORE.getMessage(), MarkerKind.UNREAL_CORE);
					}
				}
			}

			@Override
			public void writeCore(List<Integer> c, int checks, int actual) {
				Collections.sort(c);
				output.println("Core: " + TraceIdentifier.formatLines(c));
			}
		};

		switch (actionID) {
		case SYS_VAR_CORE:
			if (realizable) {
				consolePrinter.printlnAndLog(specFile, actionID.toString(),
						"Specification is realizable. Cannot compute system variables core.");
			} else {
				List<Integer> coreGars = computeGarCore(gm, builder, quickCore);
				consolePrinter.printlnAndLog(specFile, actionID.toString(),
						"Found guarantees unrealizable core with " + coreGars.size() + " guarantees, at lines "
								+ TraceIdentifier.formatLines(coreGars) + ". See markers in specification file.");

				writeSpecElements(coreGars, MarkerKind.UNREAL_CORE, true);
				consolePrinter.println("");

				DdminUnrealizableVarsCore varsMinimizer = new DdminUnrealizableVarsCore(builder.build(coreGars));
				List<ModuleBDDField> coreVars = varsMinimizer.minimize(gm.getSys().getNonAuxFields());
				if (!coreVars.isEmpty()) {
					consolePrinter.printlnAndLog(specFile, actionID.toString(),
							"Found " + coreVars.size() + " unrealizable core system variables.");
					writeVars(coreVars);
				} else {
					consolePrinter.printlnAndLog(specFile, actionID.toString(), "There are no core variables.");
				}
			}
			break;

		case ASM_CORE:
			if (!realizable) {
				consolePrinter.println("Specification is not realizable. Cannot compute assumptions core.");
			} else {
				final EnvTraceInfoBuilder ebuilder = new EnvTraceInfoBuilder(gm);
				AbstractDdmin<Integer> asmMinimizer = new AbstractDdmin<Integer>() {
					@Override
					public boolean check(List<Integer> part) {
						return RealizabilityCheck.isRealizable(ebuilder.build(part));
					}
				};
				List<Integer> asmCore = asmMinimizer.minimize(ebuilder.getTraceList());
				if (!asmCore.isEmpty()) {
					consolePrinter.printlnAndLog(specFile, actionID.toString(),
							"Found " + asmCore.size() + " realizable core assumptions, at lines "
									+ TraceIdentifier.formatLines(asmCore) + ". See markers in specification file.");
					writeSpecElements(asmCore, MarkerKind.CUSTOM_TEXT_MARKER, true);
				} else {
					consolePrinter.printlnAndLog(specFile, actionID.toString(),
							"There is no need for any assumptions to make the specification realizable.");
				}
			}
			break;

		case ALL_ASM_CORE:
			if (!realizable) {
				consolePrinter.printlnAndLog(specFile, actionID.toString(),
						"Specification is not realizable. Cannot compute assumptions cores.");
			} else {
				final EnvTraceInfoBuilder ebuilder = new EnvTraceInfoBuilder(gm);
				AllCoresPunchAlgorithm<Integer> all = new AllCoresPunchAlgorithm<Integer>(new Checker<Integer>() {
					@Override
					public boolean check(List<Integer> part) {
						return RealizabilityCheck.isRealizable(ebuilder.build(part));
					}
				});
				all.setCoreOutput(allCoresOutputLog);
				all.computeAllCores(ebuilder.getTraceList());
				List<List<Integer>> allCores = all.getCoreData().getAllCores();

				consolePrinter.println("\nFinal results:\n");
				for (List<Integer> c : allCores) {
					Collections.sort(c);
					consolePrinter.printlnAndLog(specFile, actionID.toString(), "Core #" + (allCores.indexOf(c) + 1)
							+ " at lines " + TraceIdentifier.formatLines(c) + " is");
					writeSpecElements(c, MarkerKind.UNREAL_CORE, false);
					consolePrinter.println("");
				}

				List<Integer> intersection = all.getCoreData().getCoresIntersection();
				if (intersection.size() == 0) {
					consolePrinter.printlnAndLog(specFile, actionID.toString(),
							"The intersection of all the cores is empty.");
				} else {
					consolePrinter.printlnAndLog(specFile, actionID.toString(),
							"Found " + intersection.size() + " elements that belong to all cores, at lines "
									+ TraceIdentifier.formatLines(intersection)
									+ ". See markers in specification file.");
					writeSpecElements(intersection, MarkerKind.UNREAL_CORE, false);
				}
			}
			break;

		case GLOBAL_CORE:
			if (realizable) {
				consolePrinter.printlnAndLog(specFile, actionID.toString(),
						"Specification is realizable. Cannot compute guarantees core.");
			} else {
				AllUnrealizebleCores glb = new AllUnrealizebleCores(gm, quickCore);

				glb.computeAllCores(builder.getTraceList());
				List<Integer> globalCore = glb.getCoreData().getGlobalCore();
				consolePrinter.printlnAndLog(specFile, actionID.toString(),
						"Found a global unrealizable core with " + globalCore.size() + " elements, at lines "
								+ TraceIdentifier.formatLines(globalCore) + ". See markers in specification file.");

				writeSpecElements(globalCore, MarkerKind.UNREAL_CORE, true);
			}
			break;

		case UNREALIZABLE_CORE:
			if (realizable) {
				consolePrinter.printlnAndLog(specFile, actionID.toString(),
						"Specification is realizable. Cannot compute guarantees core.");
			} else {
				List<Integer> result = computeGarCore(gm, builder, quickCore);
				consolePrinter.printlnAndLog(specFile, actionID.toString(),
						"Found unrealizable core with " + result.size() + " elements, at lines "
								+ TraceIdentifier.formatLines(result) + ". See markers in specification file.");

				PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
					public void run() {
						try {
							PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
									.showView("unrealizableCoreMarker");
						} catch (PartInitException e) {
							System.err.println("View id not found");
						}
					}
				});

				writeSpecElements(result, MarkerKind.UNREAL_CORE, true);
			}
			break;

		case MAX_REAL:
			if (realizable) {
				consolePrinter.printlnAndLog(specFile, actionID.toString(),
						"Specification is realizable. The maximal set of guarantees that keeps realizability is all guarantees.");
			} else {
				AbstractDdmin<Integer> rmax = new AbstractDdmin<Integer>() {
					@Override
					public boolean check(List<Integer> part) {
						List<Integer> negPart = new ArrayList<Integer>(builder.getTraceList());
						assert (negPart.containsAll(part));
						negPart.removeAll(part);
						return RealizabilityCheck.isRealizable(builder.build(negPart));
					}
				};
				List<Integer> res = new ArrayList<Integer>(builder.getTraceList());
				List<Integer> neg = new ArrayList<Integer>(rmax.minimize(builder.getTraceList()));
				res.removeAll(neg);
				consolePrinter.printlnAndLog(specFile, actionID.toString(),
						"Found a maximal realizable set of guarantees with " + res.size() + " elements, at lines "
								+ TraceIdentifier.formatLines(res) + ". See markers in specification file.");

				writeSpecElements(res, MarkerKind.UNREAL_CORE, true);
			}
			break;

		case ALL_CORES:
			if (realizable) {
				consolePrinter.printlnAndLog(specFile, actionID.toString(),
						"Specification is realizable. Cannot compute guarantees core.");
			} else {
				AllUnrealizebleCores all = new AllUnrealizebleCores(gm, quickCore);
				all.setCoreOutput(allCoresOutputLog);

				all.computeAllCores(builder.getTraceList());
				List<List<Integer>> allCores = all.getCoreData().getAllCores();

				consolePrinter.println("\nFinal results:\n");
				for (List<Integer> c : allCores) {
					Collections.sort(c);
					consolePrinter.printlnAndLog(specFile, actionID.toString(), "Core #" + (allCores.indexOf(c) + 1)
							+ " at lines " + TraceIdentifier.formatLines(c) + " is");
					writeSpecElements(c, MarkerKind.UNREAL_CORE, false);
					consolePrinter.println("");
				}

				List<Integer> intersection = all.getCoreData().getCoresIntersection();
				if (intersection.size() == 0) {
					consolePrinter.printlnAndLog(specFile, actionID.toString(),
							"The intersection of all the cores is empty.");
				} else {
					consolePrinter.printlnAndLog(specFile, actionID.toString(),
							"Found " + intersection.size() + " elements that belong to all cores, at lines "
									+ TraceIdentifier.formatLines(intersection)
									+ ". See markers in specification file.");
					writeSpecElements(intersection, MarkerKind.UNREAL_CORE, false);
				}
			}
			break;

		default:
			break;
		}
		gm.free();
		GR1GameExperiments.WITH_MEMORY = prevUseMemory; // restore previous choice
	}

	/**
	 * computes a guarantees core while considering QuickCore menu option
	 * 
	 * @param gm
	 * @param builder
	 * @param check
	 * @param quickCore
	 * @return
	 */
	private List<Integer> computeGarCore(GameModel gm, SysTraceInfoBuilder builder, boolean quickCore) {
		Checker<Integer> check = new Checker<Integer>() {
			protected boolean check(List<Integer> sys) {
				return !RealizabilityCheck.isRealizable(builder.build(sys));
			}
		};
		List<Integer> coreGars = new ArrayList<Integer>();
		if (quickCore) {
			QuickCore qc = new QuickCore(gm, check);
			coreGars.addAll(qc.minimize(ti.getSysTraces()));
		} else {
			AbstractDdmin<Integer> ucmin = new AbstractDdmin<Integer>() {
				@Override
				public boolean check(List<Integer> part) {
					return !RealizabilityCheck.isRealizable(builder.build(part));
				}
			};
			coreGars.addAll(ucmin.minimize(builder.getTraceList()));
		}
		return coreGars;
	}

	private void writeSpecElements(List<Integer> elems, MarkerKind k, boolean mark) {
		try {
			for (Integer b : elems) {
				consolePrinter.println(ti.formatMarked(b));

				if (mark) {
					createMarker(b, k.getMessage(), k);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void writeVars(List<ModuleBDDField> elems) {
		try {
			for (ModuleBDDField b : elems) {
				consolePrinter.println(TraceIdentifier.getLine(b.getTraceId()) + " variable " + b.toString() + ".");
				createMarker(b.getTraceId(), MarkerKind.CUSTOM_TEXT_MARKER.getMessage(), MarkerKind.CUSTOM_TEXT_MARKER);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * deletes all markers of all SYNTECH MarkerKind(s)
	 */
	private void clearMarkers() {
		traceIDListInCore.clear();

		if (previousFileWithMarkers == null)
			return;
		for (MarkerKind k : MarkerKind.values()) {
			try {
				previousFileWithMarkers.deleteMarkers(k.getMarkerID(), true, IResource.DEPTH_ZERO);
			} catch (CoreException e) {
			}
		}
	}

	public void createMarker(List<BehaviorInfo> infos, MarkerKind kind) {
		if (infos != null && infos.size() > 0) {
			if (previousFileWithMarkers != null) {
				clearMarkers();

			}
			previousFileWithMarkers = specFile;
		}
		for (BehaviorInfo info : infos) {
			createMarker(info.traceId, kind.getMessage(), kind);
		}
	}

	/**
	 * create a marker for an element with the given traceId
	 * 
	 * @param traceId
	 * @param message
	 * @param kind
	 */
	public void createMarker(int traceId, String message, MarkerKind kind) {
		previousFileWithMarkers = specFile;
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
