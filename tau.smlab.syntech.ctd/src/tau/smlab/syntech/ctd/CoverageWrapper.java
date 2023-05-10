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

package tau.smlab.syntech.ctd;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDVarSet;
import tau.smlab.syntech.games.util.SaveLoadWithDomains;
import tau.smlab.syntech.jtlv.BDDPackage;
import tau.smlab.syntech.jtlv.BDDPackage.BBDPackageVersion;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.jtlv.env.module.ModuleBDDField;
import tau.smlab.syntech.games.controller.Controller;
import tau.smlab.syntech.games.controller.StaticController;
import tau.smlab.syntech.games.controller.jits.BasicJitController;

public class CoverageWrapper {

	/**
	 * Recursively dumps all BDDs to the results folder.
	 * 
	 * @param node      The root of a BDD tree to start from.
	 * @param prev_path path to save to. This is recursively altered.
	 * @return A tree used to locate the files when reloading them.
	 */
	public static TreeInfo countAndSaveBdds(String outputFolder, DefaultMutableTreeNode node, String prev_path) {
		TreeInfo res = new TreeInfo();
		if (null != node.getUserObject()) {
			try {
				Env.saveBDD(
						Paths.get(outputFolder, "BDD_" + prev_path.hashCode() + ".bin").toString(),
						(BDD) (node.getUserObject()), false);
			} catch (IOException e) {
				e.printStackTrace();
			}
			res.items++;
			if (node.isLeaf()) {
				res.leafs++;
			}
		}

		for (int i = 0; i < node.getChildCount(); i++) {
			TreeInfo ti = countAndSaveBdds(outputFolder, (DefaultMutableTreeNode) (node.getChildAt(i)),
					prev_path + "_" + i);
			res.items += ti.items;
			res.leafs += ti.leafs;
		}

		return res;
	}

	/**
	 * Loads BDDs recursively from the disk to a BDD tree.
	 * 
	 * @param prefixPath The prefix path of the BDDs to start loading from.
	 * @param node       Root of the tree to start loading/
	 * @param isInitial  True if root of the original tree. For recursive purposes.
	 * @throws IOException
	 */
	public static void LoadNodeFromFile(String bddFolder, String bddPrefix, DefaultMutableTreeNode node,
			boolean isInitial) throws IOException {
		if (!isInitial) {
			BDD bdd = Env.loadBDD(
					Paths.get(bddFolder, "BDD_" + bddPrefix.hashCode() + ".bin").toString());
			node.setUserObject(bdd);
		}

		for (int i = 0; i < node.getChildCount(); i++) {
			LoadNodeFromFile(bddFolder, bddPrefix + "_" + i, (DefaultMutableTreeNode) node.getChildAt(i), false);
		}
	}

	/**
	 * Loads a coverage from the disk.
	 * 
	 * @param treeFile  The file representing the tree.
	 * @param bddFolder The folder of the BDD files.
	 * @return The tree model as originally created.
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static DefaultTreeModel LoadFromFile(String treeFile, String bddFolder)
			throws IOException, ClassNotFoundException {
		// Read
		InputStream file2 = new FileInputStream(treeFile);
		InputStream buffer2 = new BufferedInputStream(file2);
		ObjectInput input2 = new ObjectInputStream(buffer2);

		DefaultTreeModel tree = (DefaultTreeModel) (input2.readObject());
		input2.close();

		File treeFileObj = new File(treeFile);
		String treeName = treeFileObj.getName();
		LoadNodeFromFile(bddFolder, treeName.substring(0, treeName.lastIndexOf('.')),
				(DefaultMutableTreeNode) tree.getRoot(), true);
		return tree;
	}

	/**
	 * Save all results of a coverage to the disk. Saves data to ctd-info.csv and also
	 * saves the BDDs to the output folder.
	 * 
	 * @param controllers        The BDD tree to save
	 * @param testTuples         The test tuples used for generating the coverage.
	 * @param symbolicController Controller of the game.
	 * @param coverageFraction   Coverage fraction achieved.
	 * @param outputFolder       The output folder for the BDDs.
	 * @param specName           The name of the spec.
	 * @param dfsMode            Was DFS mode used?
	 * @param createFolder		 Should put results in a new timestamped folder?
	 * @param origUnprimeVars	 The non-dummy unprime vars. Used for reachable calculation.
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static void saveResultsToFile(DefaultTreeModel controllers, List<BDDVarSet> testTuples,
			Controller controller, double coverageFraction, String outputFolder, String specName,
			boolean dfsMode, boolean createFolder, BDDVarSet origUnprimeVars) throws IOException, ClassNotFoundException {
		//long timeStamp = System.currentTimeMillis();
		String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss.SSS").format(new Date());
		
		File specFile = new File(specName);

		String innerOutputFolder = outputFolder;
		if (createFolder) {
			File innerFolder = new File(outputFolder, "" + timeStamp);
			if (!innerFolder.exists()){
				innerFolder.mkdirs();
			}
			
			innerOutputFolder = innerFolder.getAbsolutePath();
		}
		
		String outputTreePath = Paths.get(innerOutputFolder, specFile.getName() + "_tree" + ".bin").toString();

		OutputStream file = new FileOutputStream(outputTreePath);
		OutputStream buffer = new BufferedOutputStream(file);
		ObjectOutput output = new ObjectOutputStream(buffer);

		output.writeObject(controllers);
		output.close();

		// Write BDDs
		TreeInfo ti = countAndSaveBdds(innerOutputFolder, (DefaultMutableTreeNode) (controllers.getRoot()),
				specFile.getName() + "_tree");

		// Write info to csv
		String infoPath = Paths.get(outputFolder, "ctd-info.csv").toString();

		File tempFile = new File(infoPath);
		boolean isNewFile = !tempFile.exists();

		FileWriter fileWriter = new FileWriter(infoPath, true);

		if (isNewFile) {
			fileWriter.append(
					"spec_name, number_of_scenario_tuples, time_stamp, coverage_fraction, reachable_states_in_the_symbolic_controller, SS_variant?, num_scenarios, num_states\n");
		}

		fileWriter.append(specFile.getName() + ",");
		fileWriter.append(testTuples.size() + ",");
		fileWriter.append(timeStamp + ",");
		fileWriter.append(coverageFraction + ",");

		// Calculate reachable states
		BDD reachableStates = controller.kSucc(controller.initial().id(), 0);
		BDD reachExists = reachableStates.exist(Env.globalVarsMinus(Env.globalUnprimeVars()));
		double numReachable = reachExists.satCount(origUnprimeVars);

		fileWriter.append(new DecimalFormat("#").format(numReachable) + ",");
		fileWriter.append(dfsMode + ",");
		fileWriter.append(ti.leafs + "," + ti.items);

		fileWriter.append("\n");

		// finish off
		fileWriter.flush();
		fileWriter.close();
	}
	
	
	public static void loadVariables(String folder, String name, Map<String, String[]> sysVars,
			Map<String, String[]> envVars) throws IOException {
		
		
		BDDPackage.setCurrPackage(BDDPackage.CUDD, BBDPackageVersion.CUDD_3_0); 
		
		SaveLoadWithDomains.loadStructureAndDomains(folder + File.separator + name + "." + SaveLoadWithDomains.VARS_FILE, sysVars, envVars);
		
		sysVars.entrySet().removeIf(var -> var.getKey().startsWith("util_"));
		envVars.entrySet().removeIf(var -> var.getKey().startsWith("util_"));
	    
	}
	

	public static boolean computeCoverage(String gameModel, String outputFolder, String name, int tupleSize, boolean dfsMode, boolean useJitController)
			throws Exception {
		return computeCoverage(gameModel, outputFolder, name, tupleSize, dfsMode, 0, 0, 0, true, 1, useJitController);
	}

	public static boolean computeCoverage(String gameModel, String outputFolder, String name, int tupleSize, boolean dfsMode,
			int maxRandom, boolean useJitController) throws Exception {
		return computeCoverage(gameModel, outputFolder, name, tupleSize, dfsMode, 0, 0, maxRandom, true, 1, useJitController);
	}

	/**
	 * Wrapper for computeCoverageInternal used by the UI functions.
	 * 
	 * @param gameModel              The name of the game file to load.
	 * @param outputFolder           The output folder for the result.
	 * @param dfsMode                Is DFS mode?
	 * @param pruneSize              Max prune size for recursive lookup (width).
	 *                               Put 0 if not using recursive.
	 * @param maxDepth               Max depth for recursive lookup. Put 0 if not
	 *                               using recursive.
	 * @param maxRandom              Put 0 if not using random. Otherwise, the
	 *                               maximum number of states to be added to the
	 *                               coverage randomly.
	 * @param symbolicControllerPath Path to the symbolic controller to load.
	 * @param families               A list of lists. Each internal list is a list
	 *                               of vars in a family. Used for generating the
	 *                               coverage tuple.
	 * @param familySizes            The size of each respective family in
	 *                               "families".
	 * @param createFolder			 Should create a new timestamped folder for results?
	 * @param iniTreePath			 Path to initial spectra tree file. Can be null.
	 * @param iniBddsPath			 Path to initial bdds path. Can be null.
	 * @param requestedCoverage      Stop once reaching this coverage.
	 * @return true if 100% coverage was achieved.
	 * @throws Exception
	 */
	public static boolean computeCoverageUI(String gameModel, String outputFolder, boolean dfsMode, int pruneSize,
			int maxDepth, int maxRandom, String symbolicControllerPath, List<List<String>> families,
			List<Integer> familySizes, boolean createFolder, String iniTreePath, String iniBddsPath,
			double requestedCoverage, boolean useJitController) throws Exception {
		Env.resetEnv();
		Env.enableReorder();

		Map<String, String[]> sysVars = new HashMap<>();
		Map<String, String[]> envVars = new HashMap<>();
		
		String[] path = gameModel.split("/"); //The separator is for internal resources is the same for every os
		String name = path[path.length-1].substring(0, path[path.length-1].indexOf("."));
		
		loadVariables(symbolicControllerPath, name, sysVars, envVars);
		
		Controller controller = useJitController ? new BasicJitController() : new StaticController();
			
		controller.load(symbolicControllerPath, name, sysVars, envVars);

		ArrayList<BDDVarSet> testTuples = new ArrayList<>();

		for (int i = 0; i < families.size(); i++) {
			ArrayList<ModuleBDDField> allVars = new ArrayList<>();

			for (String var : families.get(i)) {
				ModuleBDDField tmp = Env.getVar(var);
				if (tmp == null) {
					throw new Exception("Did not find var: " + var);
				}

				allVars.add(tmp);
			}

			testTuples.addAll(CoverageUtils.generateTestTuples(allVars, familySizes.get(i)));
		}

		
		BDDVarSet origUnprimeVars = Env.globalUnprimeVars();
		
		// Load initial tree
		DefaultTreeModel statesTree = null;
		if (iniTreePath != null && iniBddsPath != null) {			
			statesTree = CoverageWrapper.LoadFromFile(iniTreePath, iniBddsPath);
		}
		
		return computeCoverageInternal(gameModel, controller, outputFolder, testTuples, dfsMode, pruneSize,
				maxDepth, maxRandom, true, createFolder, statesTree, origUnprimeVars, requestedCoverage);
	}

	/**
	 * Wrapper for computeCoverageInternal. Useful for most uses.
	 * 
	 * @param gameModel    The name of the game file to load.
	 * @param outputFolder The output folder for the result.
	 * @param tupleSize    The size of each tuple for the coverage (k).
	 * @param dfsMode      Should use DFS mode? Not supported for recursive
	 *                     variation.
	 * @param pruneSize    Max prune size for recursive lookup (width). Put 0 if not
	 *                     using recursive.
	 * @param maxDepth     Max depth for recursive lookup. Put 0 if not using
	 *                     recursive.
	 * @param maxRandom    Put 0 if not using random. Otherwise, the maximum number
	 *                     of states to be added to the coverage randomly.
	 * @param ordered      If false, doesn't sort the BDDs based on satCount before
	 *                     intersecting. Should usually be true.
	 * @param requestedCoverage Stop once reaching this coverage.
	 * @return
	 * @throws Exception
	 */
	public static boolean computeCoverage(String gameModel, String outputFolder, String name, int tupleSize, boolean dfsMode,
			int pruneSize, int maxDepth, int maxRandom, boolean ordered, double requestedCoverage, boolean useJitController) throws Exception {
		Env.resetEnv();
		Env.enableReorder();

		Map<String, String[]> sysVars = new HashMap<>();
		Map<String, String[]> envVars = new HashMap<>();
		File file = new File(gameModel);
		String parentPath = file.getParent();
		String outPath = Paths.get(parentPath, "out").toString() + File.separator + (useJitController? "jit" : "static") + File.separator;
		loadVariables(outPath, name, sysVars, envVars);
		Controller controller = useJitController ? new BasicJitController() : new StaticController();
		controller.load(outPath, name, sysVars, envVars);

		List<ModuleBDDField> varsSys = new ArrayList<ModuleBDDField>();
		List<ModuleBDDField> varsEnv = new ArrayList<ModuleBDDField>();

		for (String sysVar : sysVars.keySet()) {
			// Add all vars except "Zn".
			if (!sysVar.equals("Zn")) {
				varsSys.add(Env.getVar(sysVar));
			}
		}
		for (String envVar : envVars.keySet()) {
			varsEnv.add(Env.getVar(envVar));
		}

		// Creates a list of all of the pairs of vars, including pairs from the same
		// list.

		System.out.println("Generating scenario tuples");
		List<BDDVarSet> testTuples = CoverageUtils.generateTestTuples(varsSys, varsEnv, tupleSize);

		System.out.println("Calling internal function");
		return computeCoverageInternal(gameModel, controller, outputFolder, testTuples, dfsMode, pruneSize,
				maxDepth, maxRandom, ordered, false, null, Env.globalUnprimeVars(), requestedCoverage);

	}

	/**
	 * A function containing all the internal variations of the coverage algorithm.
	 * Saves the results to the disk. Validates the results are valid.
	 * 
	 * @param gameModel          The name of the game file to load.
	 * @param symbolicController The controller.
	 * @param outputFolder       The output folder for the result.
	 * @param testTuples         The tuples to create a coverage for.
	 * @param dfsMode            Should use DFS mode? Not supported for recursive
	 *                           variation.
	 * @param pruneSize          Max prune size for recursive lookup (width). Put 0
	 *                           if not using recursive.
	 * @param maxDepth           Max depth for recursive lookup. Put 0 if not using
	 *                           recursive.
	 * @param maxRandom          Put 0 if not using random. Otherwise, the maximum
	 *                           number of states to be added to the coverage
	 *                           randomly.
	 * @param ordered            If false, doesn't sort the BDDs based on satCount
	 *                           before intersecting. Should usually be true.
	 * @param createFolder		 Should create a new timestamped folder for the results?
	 * @param statesTree		 The initial states tree for tree coverage. Otherwise unused.
	 * 							 Can be null.
	 * @param origUnprimeVars	 The non-dummy unprime vars. Used for reachable calculation.
	 * @param requestedCoverage  Stop once reaching this coverage. Only supported for regular variation. 
	 * @return
	 * @throws Exception
	 */
	public static boolean computeCoverageInternal(String gameModel, Controller controller,
			String outputFolder, List<BDDVarSet> testTuples, boolean dfsMode, int pruneSize, int maxDepth,
			int maxRandom, boolean ordered, boolean createFolder, DefaultTreeModel statesTree, BDDVarSet origUnprimeVars,
			double requestedCoverage) throws Exception {

		DefaultTreeModel controllersTree = null;

		if (maxRandom != 0) {
			controllersTree = ExperimentalCoverage.computeTestSuiteTreeRandom(controller, dfsMode, maxRandom,
					testTuples);
		} else if (pruneSize == 0 && maxDepth == 0) {
			controllersTree = TreeCoverage.computeTestSuiteTree(controller,
					new ArrayList<BDDVarSet>(testTuples), dfsMode, ordered, statesTree, requestedCoverage);
		} else {
			controllersTree = RecursiveCoverage.computeTestSuiteTreeRecursive(controller, testTuples, maxDepth,
					pruneSize);
		}
		assert (controllersTree != null);
		assert (isValidTransitionsTree(controllersTree, controller));

		double coverageFraction = getTreeFullControllersCoverage(controllersTree, new ArrayList<BDDVarSet>(testTuples),
				controller);
		System.out.println("Is full coverage: " + (coverageFraction == 1));

		saveResultsToFile(controllersTree, testTuples, controller, coverageFraction, outputFolder, gameModel,
				dfsMode, createFolder, origUnprimeVars);

		return true;
	}

	/**
	 * Checks if a coverage tree is valid. Makes sure initial positions are valid
	 * and that each transition is.
	 * 
	 * @param controllers     The coverage tree.
	 * @param symblController The controller.
	 * @return True if valid. Otherwise False.
	 */
	public static boolean isValidTransitionsTree(DefaultTreeModel controllers, Controller controller) {
		DefaultMutableTreeNode root = (DefaultMutableTreeNode) controllers.getRoot();
		assert (null == root.getUserObject());

		for (int i = 0; i < root.getChildCount(); i++) {
			DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
			BDD bdd = (BDD) child.getUserObject();
			if (controller.initial().and(bdd).isZero()) {
				System.out.println("Invalid initial pos - " + i);
				return false;
			}
			if (!isValidTransitionsNode(child, controller.transitions())) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Recursively checks all transitions in a coverage tree for validity.
	 * 
	 * @param node  The node to start from.
	 * @param trans Transition BDD of the game.
	 * @return True if valid. Otherwise False.
	 */
	public static boolean isValidTransitionsNode(DefaultMutableTreeNode node, BDD trans) {
		BDD bdd = (BDD) node.getUserObject();

		for (int i = 0; i < node.getChildCount(); i++) {
			DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
			BDD childBdd = (BDD) child.getUserObject();

			BDD succBdd = Env.succ(bdd, trans);
			if (succBdd.and(childBdd).isZero()) {
				System.out
						.println("Invalid transition - bdd: " + bdd + "\nsuccBDD: " + succBdd + "\nChild: " + childBdd);
				System.out.println("childHash: " + childBdd.hashCode());
				return false;
			}

			if (!isValidTransitionsNode(child, trans)) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Calculates the coverage percent of a coverage tree.
	 * 
	 * @param controllers     The tree to check.
	 * @param testTuples      The tuples to cover.
	 * @param symblController The controller of the game.
	 * @return The fraction covered.
	 */
	public static double getTreeFullControllersCoverage(TreeModel controllers, List<BDDVarSet> testTuples,
			Controller controller) {
		BDD reachableStates = controller.kSucc(controller.initial().id(), 0);
		return getTreeFullControllersCoverage2(controllers, testTuples, controller, reachableStates);
	}

	/**
	 * Calculates the coverage percent of a coverage tree.
	 * 
	 * @param controllers     The tree to check.
	 * @param testTuples      The tuples to cover.
	 * @param symblController The controller of the game.
	 * @param reachableStates reachable states BDD
	 * @return The fraction covered.
	 */
	public static double getTreeFullControllersCoverage2(TreeModel controllers, List<BDDVarSet> testTuples,
			Controller controller, BDD reachableStates) {

		double statesToCover = 0;

		TreeSet<TupleBDD> uncoveredSet = new TreeSet<TupleBDD>(new TupleBDDCmp());
		for (BDDVarSet tuple : testTuples) {
			TupleBDD currentTupleBDD = new TupleBDD(tuple, reachableStates);
			uncoveredSet.add(currentTupleBDD);

			statesToCover += currentTupleBDD.varSatCount();
			// System.out.print(statesToCover + " ");
		}

		double statesToCover2 = 0;
		// System.out.println("");
		for (TupleBDD tupleBDD : uncoveredSet) {
			statesToCover2 += tupleBDD.varSatCount();
			// System.out.print(statesToCover2 + " ");
		}

		assert (statesToCover == statesToCover2);

		assert (statesToCover != 0);

		SearchState ss = new SearchState(uncoveredSet, new DefaultTreeModel(null));

		ss = updateChildrenControllerCoverage((DefaultMutableTreeNode) (controllers.getRoot()), ss);

		// System.out.println("");

		double statesMissed = 0;

		for (TupleBDD tupleBDD : ss.uncoveredSet) {
			statesMissed += tupleBDD.varSatCount();
			// System.out.print(statesMissed + " ");
		}

		return 1 - (statesMissed / statesToCover);
	}

	/**
	 * Recursively updates the coverage of fraction for the entire tree. Used mainly
	 * by getTreeFullControllersCoverage().
	 * 
	 * @param node            Node to start from.
	 * @param ss              The SearchState object. This object is used for the
	 *                        algorithm mainly and is here for not rewriting code.
	 * @param symblController The symbolic controller of the game.
	 * @return The SearchState object after it was updated.
	 */
	public static SearchState updateChildrenControllerCoverage(DefaultMutableTreeNode node, SearchState ss) {
		if (node.getUserObject() != null) {
			ss = CoverageUtils.updateYetUncovered((BDD) (node.getUserObject()), ss);
		}

		for (int i = 0; i < node.getChildCount(); i++) {
			ss = updateChildrenControllerCoverage((DefaultMutableTreeNode) (node.getChildAt(i)), ss);
		}

		return ss;
	}

}
