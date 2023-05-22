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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDVarSet;
import tau.smlab.syntech.bddgenerator.sfa.SFAGeneratorFactory;
import tau.smlab.syntech.bddgenerator.sfa.SFAGeneratorFactory.RegExpSFAGeneratorType;
import tau.smlab.syntech.ctd.CoverageWrapper;
import tau.smlab.syntech.ctd.TreeInfo;
import tau.smlab.syntech.gameinput.model.RegexpTestModel;
import tau.smlab.syntech.gamemodel.PlayerModule;
import tau.smlab.syntech.gamemodel.SFAModuleConstraint;
import tau.smlab.syntech.games.controller.Controller;
import tau.smlab.syntech.games.controller.symbolic.SymbolicController;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.sfa.SFA;

public class MtdUtils {
	/**
	 * Generates a list of non-deterministic SFA without eps transitions
	 * 
	 * @param exps   List of regexps
	 * @param sysMod The PlayerModule of the constraints.
	 * @return A list of SFAModuleConstraint of the constraints.
	 */
	public static List<SFAModuleConstraint> generateRegexpTestConstraints(List<RegexpTestModel> exps,
			PlayerModule sysMod) {
		List<SFAModuleConstraint> sfaModuleConstraints = new ArrayList<>();
		SFAModuleConstraint regExpSfaConstraint;
		SFA regExpSfa;
		for (RegexpTestModel exp : exps) {
			// Generates a non-deterministic SFA and removes it's eps transitions, yet it is
			// still non-deterministic.
			regExpSfa = SFAGeneratorFactory.getGenerator(RegExpSFAGeneratorType.SYMBOLIC, exp.getRegExp(), 0)
					.generateRegExpSfa();
			SFAModuleConstraint.Builder builder = new SFAModuleConstraint.Builder();
			builder.setVarNamePrefix("regexptest_sfa_states_");
			regExpSfaConstraint = builder.playerModule(sysMod).groupVars(false).sfa(regExpSfa).restrictIniTrans(false)
					.traceId(exp.getTraceId()).build();
			sfaModuleConstraints.add(regExpSfaConstraint);

			regExpSfa.free();
		}

		return sfaModuleConstraints;
	}

	/**
	 * Plays a reachability game on the controller. Goes backwards and then forwards
	 * for cleanup. Useful when one does not want to compute a BDD containing all
	 * reachable states.
	 * 
	 * @param from              From BDD.
	 * @param to                To BDD.
	 * @param symboliController The symbolic controller.
	 * @return A list of BDDs. These form reachability paths from From to To.
	 */
	public static List<BDD> attractorLayers4(BDD from, BDD to, SymbolicController controller) {
		List<BDD> layers = new ArrayList<BDD>();

		layers.add(from.id());
		BDD lastLayer = layers.get(0);
		BDD intersect = lastLayer.and(to);

		BDD visited = from.id();

		while (intersect.isZero()) {
			BDD newLayer = controller.succ(lastLayer);
			layers.add(newLayer);
			
			intersect.free();
			intersect = newLayer.and(to);
			if (intersect.isZero() && visited.and(newLayer).equals(newLayer)) {
				return null;
			}
			
			visited = visited.orWith(newLayer.id());			
			lastLayer = newLayer;
		}

		intersect.free();

		// Clean up paths that did not help:
		layers.get(layers.size() - 1).andWith(to.id());
		for (int i = layers.size() - 2; i >= 0; i--) {
			layers.get(i).andWith(controller.pred(layers.get(i + 1)));
		}

		return layers;
	}

	/**
	 * Remove all vars that are not varsToKeep from the BDDs using exist
	 * 
	 * @param bdds       List of BDDs
	 * @param varsToKeep The vars to keep.
	 */
	public static void removeVarsFromBdds(List<BDD> bdds, BDDVarSet varsToKeep) {
		BDDVarSet toRemove = Env.globalVarsMinus(varsToKeep);
		for (int i = 0; i < bdds.size(); i++) {
			BDD tempBdd = bdds.get(i).exist(toRemove);
			bdds.get(i).free();
			bdds.set(i, tempBdd);
		}
	}

	/**
	 * Satisfy each attractor in the list to 1 solution. The created path will be a
	 * legal one in the game. Starts from the end and goes to the beginning
	 * 
	 * @param attractors      The list of attractor layers.
	 * @param symblController SC of the game.
	 */
	public static void satAttractorsB(List<BDD> attractors, SymbolicController controller) {
		BDD first = Env.randomSat(attractors.get(attractors.size() - 1), Env.globalUnprimeVars());
		attractors.get(attractors.size() - 1).free();
		attractors.set(attractors.size() - 1, first);

		for (int i = attractors.size() - 2; i >= 0; i--) {
			attractors.get(i).andWith(controller.pred(attractors.get(i + 1)));
			BDD tmp = Env.randomSat(attractors.get(i), Env.globalUnprimeVars());

			assert (!tmp.isZero());

			attractors.get(i).free();
			attractors.set(i, tmp);
		}
	}

	/**
	 * Compute a single test
	 * 
	 * @param sfa                The constraints of the test
	 * @param symbolicController The SC
	 * @param varsToKeep         The vars to keep in the final result.
	 * @return List of BDDs that are the test. Null if none found.
	 */
	public static List<BDD> computeTest(SFAModuleConstraint sfa, Controller controller,
			BDDVarSet varsToKeep) {
		BDD newIni = sfa.getIni().and(controller.initial());
		BDD newTrans = sfa.getTrans().and(controller.transitions());
		SymbolicController newSC = new SymbolicController(newIni, newTrans);

		List<BDD> res = attractorLayers4(newIni, sfa.getAcceptance(), newSC);
		if (res == null) {
			return null;
		}

		// We remove the last BDD. This is done because of an implementation detail
		// in the SFA module, which only accepts a state after it has reached the
		// desired state.
		res.remove(res.size() - 1);
		// In case of empty test no need to choose one path.
		if (res.size() != 0) {
			satAttractorsB(res, newSC);
		}

		removeVarsFromBdds(res, varsToKeep);

		return res;
	}

	/**
	 * Add a test to the test suite tree
	 * 
	 * @param test The test
	 * @param root The tree
	 */
	public static void addTestToSuite(List<BDD> test, DefaultMutableTreeNode root) {
		DefaultMutableTreeNode curr = root;
		for (BDD bdd : test) {
			DefaultMutableTreeNode node = new DefaultMutableTreeNode(bdd);
			curr.add(node);
			curr = node;
		}
	}

	/**
	 * Write the test suite to the disk.
	 * 
	 * @param testSuite          The test suite.
	 * @param symbolicController The SC.
	 * @param outputFolder       The folder to write the test suite to.
	 * @param specName           The specification name.
	 * @param createFolder       Should create new folder with timestamp?
	 * @throws IOException
	 */
	public static void saveResultsToFile(DefaultTreeModel testSuite,
			String outputFolder, String specName, boolean createFolder) throws IOException {
		String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss.SSS").format(new Date());

		File specFile = new File(specName);

		String innerOutputFolder = outputFolder;
		if (createFolder) {
			File innerFolder = new File(outputFolder, "" + timeStamp);
			if (!innerFolder.exists()) {
				innerFolder.mkdirs();
			}

			innerOutputFolder = innerFolder.getAbsolutePath();
		}

		String outputTreePath = Paths.get(innerOutputFolder, specFile.getName() + "_tree" + ".bin").toString();

		OutputStream file = null;
		OutputStream buffer = null;
		ObjectOutput output = null;

		try {
			file = new FileOutputStream(outputTreePath);
			buffer = new BufferedOutputStream(file);
			output = new ObjectOutputStream(buffer);
			output.writeObject(testSuite);
		} finally {
			if (output != null) {
				output.close();
			}

			if (buffer != null) {
				buffer.close();
			}

			if (file != null) {
				file.close();
			}
		}

		TreeInfo ti = CoverageWrapper.countAndSaveBdds(innerOutputFolder,
				(DefaultMutableTreeNode) (testSuite.getRoot()), specFile.getName() + "_tree");

		// Write info to csv
		String infoPath = Paths.get(outputFolder, "rtd-info.csv").toString();

		File tempFile = new File(infoPath);
		boolean isNewFile = !tempFile.exists();

		FileWriter fileWriter = null;
		try {
			fileWriter = new FileWriter(infoPath, true);

			if (isNewFile) {
				fileWriter.append("spec_name, time_stamp, num_scenarios, num_states\n");
			}

			fileWriter.append(specFile.getName() + ",");
			fileWriter.append(timeStamp + ",");
			fileWriter.append(ti.leafs + "," + ti.items);
			fileWriter.append("\n");

		} finally {
			if (fileWriter != null) {
				fileWriter.flush();
				fileWriter.close();
			}
		}
	}
}
