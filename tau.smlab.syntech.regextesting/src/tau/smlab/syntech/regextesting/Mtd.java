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

package tau.smlab.syntech.regextesting;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDVarSet;
import tau.smlab.syntech.bddgenerator.BDDGenerator;
import tau.smlab.syntech.bddgenerator.BDDGenerator.TraceInfo;
import tau.smlab.syntech.ctd.CoverageWrapper;
import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gameinput.model.RegexpTestModel;
import tau.smlab.syntech.gameinputtrans.TranslationProvider;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.SFAModuleConstraint;
import tau.smlab.syntech.games.controller.Controller;
import tau.smlab.syntech.games.controller.StaticController;
import tau.smlab.syntech.games.controller.jits.BasicJitController;
import tau.smlab.syntech.jtlv.BDDPackage;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.jtlv.BDDPackage.BBDPackageVersion;
import tau.smlab.syntech.ui.extension.console.ConsolePrinter;
import tau.smlab.syntech.spectragameinput.SpectraInputProvider;

public class Mtd {
	private static void cPrint(ConsolePrinter consolePrinter, String str) {
		if (consolePrinter != null) {
			consolePrinter.println(str);
			System.out.println(str);
		} else {
			System.out.println(str);
		}
	}

	private static void printTest(ConsolePrinter consolePrinter, List<BDD> test) {
		Integer index = 1;
		for (BDD bdd : test) {
			cPrint(consolePrinter, index.toString() + ": " + bdd.toString());
			index++;
		}
	}

	/**
	 * Computes a manual test suite based on the tests defined in the spectra file
	 * 
	 * @param specFile               The path to the spectra file
	 * @param symbolicControllerPath The path to load the symbolic controller from.
	 * @param outPath                The path the output will be written to.
	 * @param createFolder           Should create a new timestamped folder for the
	 *                               output?
	 * @throws Exception
	 */
	public static void compute(String specFile, String symbolicControllerPath, String outPath, boolean createFolder, boolean useJitController)
			throws Exception {
		compute(specFile, symbolicControllerPath, outPath, createFolder, null, useJitController);
	}

	/**
	 * Computes a manual test suite based on the tests defined in the spectra file
	 * 
	 * @param specFile               The path to the spectra file
	 * @param symbolicControllerPath The path to load the symbolic controller from.
	 * @param outPath                The path the output will be written to.
	 * @param createFolder           Should create a new timestamped folder for the
	 *                               output?
	 * @param consolePrinter         Prints to console if supplied. Can be null.
	 * @throws Exception
	 */
	public static void compute(String specFile, String symbolicControllerPath, String outPath, boolean createFolder,
			ConsolePrinter consolePrinter, boolean useJitController) throws Exception {
		try {
			BDDPackage.setCurrPackage(BDDPackage.CUDD, BBDPackageVersion.CUDD_3_0);
			Env.resetEnv();
			
			Map<String, String[]> sysVars = new HashMap<>();
			Map<String, String[]> envVars = new HashMap<>();
			
			File file = new File(specFile);
			String name = file.getName();
			name = name.substring(0, name.indexOf("."));

			CoverageWrapper.loadVariables(symbolicControllerPath, name, sysVars, envVars);
				
			Controller controller = useJitController ? new BasicJitController() : new StaticController();
			controller.load(symbolicControllerPath, name, sysVars, envVars);
			

			BDDVarSet originalUnprimeVars = Env.globalUnprimeVars();

			cPrint(consolePrinter, "Loading Spectra file.");
			GameInput gi = null;

			gi = SpectraInputProvider.getGameInput(specFile);

			cPrint(consolePrinter, "Translating Spectra file.");
			TranslationProvider.translate(gi);

			cPrint(consolePrinter, "Generating game model.");
			GameModel model = BDDGenerator.generateGameModel(gi, TraceInfo.ALL);

			cPrint(consolePrinter, "Generating constraints from regexp.");
			List<RegexpTestModel> regexps = gi.getRegtestExpressions();
			if (regexps.size() == 0) {
				cPrint(consolePrinter, "No regexp scenarios were defined.");
				return;
			}

			List<SFAModuleConstraint> constraints = MtdUtils.generateRegexpTestConstraints(regexps, model.getSys());

			DefaultMutableTreeNode root = new DefaultMutableTreeNode(null);
			DefaultTreeModel testSuite = new DefaultTreeModel(root);

			cPrint(consolePrinter, "Computing scenarios.");

			assert (regexps.size() == constraints.size());
			for (Integer i = 0; i < constraints.size(); i++) {
				String testName = regexps.get(i).getName();
				if (testName != null) {
					testName = ": " + testName;
				} else {
					testName = "";
				}

				cPrint(consolePrinter, "\nscenario #" + i.toString() + testName);

				List<BDD> test = MtdUtils.computeTest(constraints.get(i), controller, originalUnprimeVars);
				if (test == null) {
					cPrint(consolePrinter, "No valid scenario.");
					continue;
				}

				if (test.size() == 0) {
					cPrint(consolePrinter, "Found satisfying scenario of length 0.");
					continue;
				}
				
				MtdUtils.addTestToSuite(test, root);
				printTest(consolePrinter, test);
			}

			MtdUtils.saveResultsToFile(testSuite, outPath, specFile, createFolder);
		} finally {
			Env.resetEnv();
		}
	}
}
