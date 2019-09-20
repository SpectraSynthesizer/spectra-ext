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

package tau.smlab.syntech.counterstrategy.repair.ui;


import static tau.smlab.syntech.counterstrategy.repair.ui.Activator.PLUGIN_NAME;

import org.eclipse.core.resources.IFile;

import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gameinputtrans.TranslationProvider;
import tau.smlab.syntech.jtlv.BDDPackage;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.spectragameinput.ErrorsInSpectraException;
import tau.smlab.syntech.spectragameinput.SpectraInputProvider;
import tau.smlab.syntech.spectragameinput.SpectraTranslationException;
import tau.smlab.syntech.ui.extension.SyntechAction;
import tau.smlab.syntech.ui.preferences.PreferencePage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import tau.smlab.syntech.counterstrategy.repair.BFSModelRepair;
import tau.smlab.syntech.repair.AbstractRepair;
import tau.smlab.syntech.repair.BasicAssumption;
import tau.smlab.syntech.repair.BasicAssumption.Kind;
import tau.smlab.syntech.repair.alur.SpecificationRefinement;
import tau.smlab.syntech.repair.chatterjee.TheUltimateFixer;
import tau.smlab.syntech.counterstrategy.repair.FullApplicationModelRepair;

/**
 *  
 * @author Shalom
 *
 */

public class JVTSBasedRepair extends SyntechAction<JVTSBasedRepairActionID> {

	@Override
	public String getPluginName() {
		return PLUGIN_NAME;
	}

	@Override
	public JVTSBasedRepairActionID[] getActionItems() {
		return JVTSBasedRepairActionID.values();
	}

	@Override
	public void run(JVTSBasedRepairActionID actionID, IFile specFile) {
		AbstractRepair repair = null;
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
		switch (actionID) {
		case BFS_REPAIR:
			repair = new BFSModelRepair(gi, 3, false, false, false, true);
			break;
		case FULL_APPLICATION:
			repair = new FullApplicationModelRepair(gi, 20, false, false, false, true);
			break;	
		case ALUR_REPAIR:
			HashMap<String, ArrayList<String>> P = new HashMap<String, ArrayList<String>>();
			P.put("P1", new ArrayList<String>());
			P.put("P2", new ArrayList<String>());
			P.put("P3", new ArrayList<String>());
			P.put("P4", new ArrayList<String>());
			repair = new SpecificationRefinement(gi, P, 2, 3, true);
			break;
		case ULTIMATE_FIXER:
			repair = new TheUltimateFixer(gi, true, true);
			break;
			
		default:
			break;
		}
		
		consolePrinter.println("Executing \"" + actionID.getMenuText() + "\".");

		repair.execute();
		
		List<List<BasicAssumption>> results = repair.getResultingAssumptions();
		if (repair.isRealizable()) {
			consolePrinter.println("The specification is realizable already. Does not need a fix");
		} else {
			consolePrinter.println("Found " + repair.numberOfRepairsFound() + " repair suggestions");
			consolePrinter.println("Number of well-separated suggestions is " + repair.numberOfWellSeparated());
			
			repair.computeBest();
			consolePrinter.println("Maximum variables in the best repair is " + repair.getMaxVars());
			consolePrinter.println("Maximum Node count: " + repair.getMaxNodes());

			consolePrinter.println("");
	
			int i=1;
			for (List<BasicAssumption> rep : results) {
				consolePrinter.println("Repair #" + i++);
				showResult(rep);
			}
		}
		
		consolePrinter.println("runtime of repair operation was "+ repair.getRuntime() + " in millisecs.");

	}
	
	/**
	 * print out assumptions
	 * 
	 * @param list the assumptions
	 */
	
	private void showResult(List<BasicAssumption> list) {

		consolePrinter.println("");
		
		consolePrinter.println("//Justices//");
		for (BasicAssumption ass : list) {
			if (ass.k == Kind.JUSTICE) {
				consolePrinter.println(ass.toString());
			}
		}
		consolePrinter.println("");
		consolePrinter.println("//Transitions//");
		for (BasicAssumption ass : list) {
			if (ass.k == Kind.SAFETY) {
				consolePrinter.println(ass.toString());
			}
		}
		consolePrinter.println("");
		consolePrinter.println("//Initial//");
		for (BasicAssumption ass : list) {
			if (ass.k == Kind.INIT) {
				consolePrinter.println(ass.toString());
			}
		}
		consolePrinter.println("");
	}

}
