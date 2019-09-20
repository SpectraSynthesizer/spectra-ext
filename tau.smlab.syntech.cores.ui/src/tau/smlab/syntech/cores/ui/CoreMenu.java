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
import tau.smlab.syntech.checks.DdminRealizableCore;
import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gameinputtrans.TranslationProvider;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.games.gr1.GR1Game;
import tau.smlab.syntech.games.gr1.unreal.DdminUnrealizableCore;
import tau.smlab.syntech.games.gr1.unreal.DdminUnrealizableVarsCore;
import tau.smlab.syntech.jtlv.BDDPackage;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.jtlv.env.module.ModuleBDDField;
import tau.smlab.syntech.spectragameinput.ErrorsInSpectraException;
import tau.smlab.syntech.spectragameinput.SpectraTranslationException;
import tau.smlab.syntech.spectragameinput.SpectraInputProvider;
import tau.smlab.syntech.spectragameinput.translator.Tracer;
import tau.smlab.syntech.ui.extension.SyntechAction;
import tau.smlab.syntech.ui.jobs.MarkerKind;
//import tau.smlab.syntech.ui.jobs.MarkerKind;
import tau.smlab.syntech.ui.preferences.PreferencePage;
import tau.smlab.syntech.cores.GlobalCoreUtility;

import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;




/**
 *  
 * @author Shalom
 *
 */

public class CoreMenu extends SyntechAction<CoresActionID> {

	private static List<Integer> traceIDListInCore = new ArrayList<Integer>();
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
	public void run(CoresActionID actionID, IFile specFile)  {
		// TODO Auto-generated method stub
		GameInput gi = null;

		BDDPackage.setCurrPackage(PreferencePage.getBDDPackageSelection(), PreferencePage.getBDDPackageVersionSelection());
		if (PreferencePage.isReorderEnabled()) {
			Env.enableReorder();
		} else {
			Env.disableReorder();
		}
		try {
			gi = SpectraInputProvider.getGameInput(specFile.getFullPath().toString());
			TranslationProvider.translate(gi);
		} catch (ErrorsInSpectraException e1) {
			//Error in spectra
			e1.printStackTrace();
		} catch (SpectraTranslationException e2) {
			//Spectra translate exception
			e2.printStackTrace();
		}
		GameModel gm = BDDGenerator.generateGameModel(gi, TraceInfo.ALL);
		
		GR1Game game = new GR1Game(gm);
		boolean realizable = game.checkRealizability();
		
		GlobalCoreUtility glb = null;
		console.activate();
		clearMarkers();
		switch (actionID) {
			case SYS_VAR_CORE:
				if (realizable) {
					consolePrinter.println("Specification is realizable. Cannot compute system variables core.");
				} else {
					DdminUnrealizableCore coreMinimizer = new DdminUnrealizableCore(gm);
					List<BehaviorInfo> coreGars = new ArrayList<BehaviorInfo>(coreMinimizer.minimize(gm.getSysBehaviorInfo()));
					consolePrinter.println("Found guarantees unrealizable core with " + coreGars.size() + " guarantees.");
					writeSpecElements(coreGars, MarkerKind.UNREAL_CORE, true);
					consolePrinter.println("");
					
					DdminUnrealizableVarsCore varsMinimizer = new DdminUnrealizableVarsCore(gm, coreGars);
					List<ModuleBDDField> coreVars = varsMinimizer.minimize(gm.getSys().getNonAuxFields());
		  	  		if (!coreVars.isEmpty()) {
		  	  			consolePrinter.println("Found " + coreVars.size() + " unrealizable core system variables.");
		  	  			writeVars(coreVars);
		  	  		} else {
		  	  			consolePrinter.println("There are no core variables.");
		  	  		}
				}
		  	  	break;
		  	  	
			case ASM_CORE:
				if (!realizable) {
					consolePrinter.println("Specification is not realizable. Cannot compute assumptions core.");
				} else {
					DdminRealizableCore asmMinimizer = new DdminRealizableCore(gm);
					List<BehaviorInfo> asmCore = asmMinimizer.minimize(new ArrayList<BehaviorInfo>(gm.getEnvBehaviorInfo()));
					if (!asmCore.isEmpty()) {
		  	  			consolePrinter.println("Found " + asmCore.size() + " realizable core assumptions.");
						writeSpecElements(asmCore, MarkerKind.CUSTOM_TEXT_MARKER, true);
					} else {
		  	  			consolePrinter.println("There is no need for any assumptions to make the specification realizable.");
					}
				}
				break;
				
			case GLOBAL_CORE:
				if (realizable) {
					consolePrinter.println("Specification is realizable. Cannot compute guarantees core.");
				} else {
					glb = new GlobalCoreUtility(gm);
					List<BehaviorInfo> globalCore = glb.getGlobalCore();
					writeSpecElements(globalCore, MarkerKind.UNREAL_CORE, true);	
				}
				break;
				
			case UNREALIZABLE_CORE:
				clearMarkers();
	
				PreferencePage.setOptSelection();
				if (PreferencePage.getBDDPackageSelection().equals(BDDPackage.CUDD)) {
					DdminUnrealizableCore.cImpl = true;
				} else {
					DdminUnrealizableCore.cImpl = false;
				}
	
				DdminUnrealizableCore ucmin = new DdminUnrealizableCore(gm);
				List<BehaviorInfo> res = new ArrayList<BehaviorInfo>();
				res = ucmin.minimize(gm.getSysBehaviorInfo());
	
				if (!res.isEmpty()) {
					consolePrinter.println(
							"Found unrealizable core with " + res.size() + " elements. See markers in specification file.");
	
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
	
					createMarker(res, MarkerKind.UNREAL_CORE);
				} else {
					res = ucmin.getAuxCore();
					if (!res.isEmpty()) {
						consolePrinter.println("Found unrealizable core with " + res.size()
								+ " elements. See markers in specification file.");
						createMarker(res, MarkerKind.UNREAL_CORE);
					} else {
						consolePrinter.println("Unable to compute unrealizable core because the specification is realizable.");
					}
				}
				gm.free();
				break;
				
			case ALL_CORES:
				if (realizable) {
					consolePrinter.println("Specification is realizable. Cannot compute guarantees core.");
				} else {
					glb = new GlobalCoreUtility(gm);
					List<List<BehaviorInfo>> allCores = glb.getAllCores();
	
					for (List<BehaviorInfo> c : allCores) {
						consolePrinter.println("Core #" + (allCores.indexOf(c)+1) + " is");
						writeSpecElements(c, MarkerKind.UNREAL_CORE, false);
						consolePrinter.println("");
					}
				}
				break;
			
			default:
				break;
		}
	}
	
	private void writeSpecElements(List<BehaviorInfo> elems, MarkerKind k, boolean mark) {
		try {
			for (BehaviorInfo b : elems) {
				consolePrinter.println(b.toString() + " starts at line " + NodeModelUtils.getNode(Tracer.getTarget(b.traceId)).getStartLine());
				if (mark) {
					createMarker(b.traceId, k.getMessage(), k);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void writeVars(List<ModuleBDDField> elems) {
		try {
			for (ModuleBDDField b : elems) {
				consolePrinter.println(b.toString() + " starts at line " + NodeModelUtils.getNode(Tracer.getTarget(b.getTraceId())).getStartLine());
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

