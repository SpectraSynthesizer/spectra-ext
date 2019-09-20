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

package tau.smlab.syntech.vacuity.ui;

import static tau.smlab.syntech.vacuity.ui.Activator.PLUGIN_NAME;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gameinputtrans.TranslationProvider;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.jtlv.BDDPackage;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.spectragameinput.ErrorsInSpectraException;
import tau.smlab.syntech.spectragameinput.SpectraInputProvider;
import tau.smlab.syntech.spectragameinput.SpectraTranslationException;
import tau.smlab.syntech.spectragameinput.translator.Tracer;
import tau.smlab.syntech.ui.extension.SyntechAction;
import tau.smlab.syntech.ui.jobs.MarkerKind;
import tau.smlab.syntech.ui.preferences.PreferencePage;
import tau.smlab.syntech.vacuity.ImplicationChecks;
import tau.smlab.syntech.vacuity.OutputFormatUtility;
import tau.smlab.syntech.vacuity.SatisfiabilityCheck;
import tau.smlab.syntech.vacuity.UnreachableValues;
import tau.smlab.syntech.vacuity.Vacuity;
import tau.smlab.syntech.vacuity.VacuityType;


/**
 * Menu class of vacuity options
 * 
 * @author shalom
 *
 */

public class VacuityMenu extends SyntechAction<VacuityActionID> {

	private static List<Integer> traceIDListInCore = new ArrayList<Integer>();
	protected static IFile previousFileWithMarkers = null;
	
	@Override
	public String getPluginName() {
		return PLUGIN_NAME;
	}

	@Override
	public VacuityActionID[] getActionItems() {
		return VacuityActionID.values();
	}

	@Override
	public void run(VacuityActionID actionID, IFile specFile) {
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

		ImplicationChecks imp = new ImplicationChecks();
		UnreachableValues unreach = new UnreachableValues();
		
		clearMarkers();
		switch (actionID) {
		case SYS_BEHAVIORS:
			consolePrinter.print("Vacuity check for system behaviors: ");
			consolePrinter.println("Check if system behaviors are implied by other behaviors.");
			consolePrinter.println("Note: There is no satisfiability check - irrelevant vacuities may be reported.");
			consolePrinter.println("");

			imp.init(gi, VacuityType.SMOD, true, false);
			Map<Integer, List<Integer>> map = imp.getSysMap();
			if (map.isEmpty()) {
				consolePrinter.println("None found.");
			} else {
				for (Integer toMark : map.keySet()) {
					String coreStr = "Core is in lines " + OutputFormatUtility.linesOfSet(map.get(toMark));
					createMarker(toMark, coreStr, MarkerKind.UNREAL_CORE);
					consolePrinter.println("Found vacuity in line " + OutputFormatUtility.getline(toMark) + ": " + coreStr);
				}
			}
			reportRunningTimes();
			imp.free();
			break;
			
		case ENV_BEHAVIORS:
			consolePrinter.print("Vacuity check for environment behaviors: ");
			consolePrinter.println("Check if environment behaviors are implied by other behaviors.");
			consolePrinter.println("Note: There is no satisfiability check - irrelevant vacuities may be reported.");
			consolePrinter.println("");
			
			imp.init(gi, VacuityType.EMOD, true, false);
			Map<Integer, List<Integer>> envMap = imp.getEnvMap();
			if (envMap.isEmpty()) {
				consolePrinter.println("None found.");
			} else {		
				for (Integer toMark : envMap.keySet()) {
					String coreStr = "Core is in lines " + OutputFormatUtility.linesOfSet(envMap.get(toMark));
					createMarker(toMark, "Core is in lines " + coreStr, MarkerKind.UNREAL_CORE);
					consolePrinter.println("Found vacuity in line " + OutputFormatUtility.getline(toMark) + ": " + coreStr);
				}
			}
			reportRunningTimes();
			imp.free();
			break;
			
		case SYS_REDUNDANT_VALS:
			consolePrinter.print("Vacuity check for system redundant variable values: ");
			consolePrinter.println("Note: There is no satisfiability check - irrelevant vacuities may be reported.");
			consolePrinter.println("");

			unreach.init(gi, VacuityType.SVAL, true, false);
			Map<BehaviorInfo, List<Integer>> sysUnreach = unreach.sysUnreachMap();
			if (sysUnreach.isEmpty()) {
				consolePrinter.println("None found.");
			} else {		
				for (BehaviorInfo toMark : sysUnreach.keySet()) {
					String coreStr = "Core is in lines " + OutputFormatUtility.linesOfSet(sysUnreach.get(toMark));
					consolePrinter.println("Found vacuity " + toMark.safety + ". " + coreStr);
				}		
			}
			reportRunningTimes();
			unreach.free();
			break;
			
		case ENV_REDUNDANT_VALS:
			consolePrinter.print("Vacuity check for environment redundant variable values: ");
			consolePrinter.println("Note: There is no satisfiability check - irrelevant vacuities may be reported.");
			consolePrinter.println("");
			
			unreach.init(gi, VacuityType.EVAL, true, false);
			Map<BehaviorInfo, List<Integer>> envUnreach = unreach.envUnreachMap();
			if (envUnreach.isEmpty()) {
				consolePrinter.println("None found.");
			} else {	
				for (BehaviorInfo toMark : envUnreach.keySet()) {
					String coreStr = "Core is in lines " + OutputFormatUtility.linesOfSet(envUnreach.get(toMark));
					consolePrinter.println("Found vacuity " + toMark.safety + ". " + coreStr);
				}
			}
			reportRunningTimes();
			unreach.free();
			break;
			
		case CHECK_SATISFIABILITY:
			SatisfiabilityCheck sat = new SatisfiabilityCheck();
			sat.init(gi, VacuityType.SAT, true, false);
			if (SatisfiabilityCheck.compute()) {
				consolePrinter.println("Yes. The specification is satisfiable. ");
				consolePrinter.println("If the specification is not realizable, you can try adding some assumptions to make the it realizable. ");
				consolePrinter.println("But it is not sure that such an assumption exists.");
			} else {
				consolePrinter.println("No. The specification is not satisfiable. ");
				String coreStr = "Core is in lines " + OutputFormatUtility.linesOfSet(SatisfiabilityCheck.getCore());
				consolePrinter.println(coreStr);
				consolePrinter.println("");				
				
				consolePrinter.println("It means that there are no possible assumptions that would allow the environment to satisfy all assumptions and allow the system to satisfy all guarantees at the same time. ");
				consolePrinter.println("The specification might be made realizable with assumptions that the environment cannot satisfy. Check also well-separation of the environment!");
			}
			sat.free();
			break;

		default:
			break;
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
	
	private void reportRunningTimes() {
		consolePrinter.println("Running time: " + Vacuity.getTimeToAll() + " milliseconds.");
		consolePrinter.println("Time to first vacuity: " + Vacuity.getTimeToFirst() + " milliseconds.");
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
