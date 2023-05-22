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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;

import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.PlayerModule;
import tau.smlab.syntech.games.controller.Controller;
import tau.smlab.syntech.games.controller.StaticController;
import tau.smlab.syntech.games.controller.jits.BasicJitController;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.jtlv.env.module.ModuleBDDField;
import tau.smlab.syntech.ui.extension.console.ConsolePrinter;

public class CTDUILogic {

	private static String getStackTrace(Exception e) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		return sw.toString();
	}

	/**
	 * Computes a coverage based on the .ctd.properties file of the spec
	 * 
	 * @param specFile       The path to the spectra file
	 * @param configFile     The path to the .ctd.properties file
	 * @param controllerPath The "out" path containing the symbolic controller.
	 * @param outPath        The suite will be written here.
	 * @param createFolder   Should create a new timestamped folder for output?
	 * @param useJitController Use a JIT controller or a static controller
	 * @throws Exception
	 */
	public static void compute(String specFile, String configFile, String controllerPath, String outPath,
			boolean createFolder, boolean useJitController) throws Exception {
		compute(specFile, configFile, controllerPath, outPath, createFolder, null, useJitController);
	}

	/**
	 * Computes a coverage based on the .ctd.properties file of the spec
	 * 
	 * @param specFile     The path to the spectra file
	 * @param configFile   The path to the .ctd.properties file
	 * @param outPath      The "out" path containing the symbolic controller. The
	 *                     output will be written there.
	 * @param createFolder Should create a new timestamped folder for output?
	 * @param useJitController Use a JIT controller or a static controller
	 * @throws Exception
	 */
	public static void compute(String specFile, String configFile, String outPath, boolean createFolder, boolean useJitController)
			throws Exception {
		File ctDir = new File(outPath, "ctd.out");
		if (!ctDir.exists()) {
			ctDir.mkdirs();
		}

		compute(specFile, configFile, outPath, ctDir.getAbsolutePath(), createFolder, null, useJitController);
	}

	/**
	 * Get the results of the last scenario suite from the CSV
	 * 
	 * @param csvPath Path to csv
	 * @return The results
	 */
	private static String getCSVResults(String csvPath) {
		try {
			String res = "";
			String strLine = null, tmp;
			String firstLine = "";

			FileInputStream in = null;
			BufferedReader br = null;
			try {
				in = new FileInputStream(csvPath);
				br = new BufferedReader(new InputStreamReader(in));

				firstLine = br.readLine() + "\n";

				while ((tmp = br.readLine()) != null) {
					strLine = tmp;
				}

			} finally {
				if (br != null) {
					br.close();
				}
				if (in != null) {
					in.close();
				}
			}

			String lastLine = strLine;
			in.close();

			String[] info = firstLine.replaceAll("\\s+", "").split(",");
			String[] values = lastLine.replaceAll("\\s+", "").split(",");

			for (int i = 0; i < info.length; i++) {
				res += info[i] + ": " + values[i] + "\n";
			}

			return res;
		} catch (Exception e) {
		}

		return "";
	}

	/**
	 * Computes a coverage based on the .ctd.properties file of the spec
	 * 
	 * @param specFile               The path to the spectra file
	 * @param configFile             The path to the .ctd.properties file
	 * @param symbolicControllerPath The path to load the symbolic controller from.
	 * @param outPath                The "out" path containing the symbolic
	 *                               controller. The output will be written there.
	 * @param createFolder           Should create a new timestamped folder for the
	 *                               output?
	 * @param consolePrinter         Prints to console if supplied. Can be null.
	 * @param useJitController Use a JIT controller or a static controller
	 * @throws Exception
	 */
	public static void compute(String specFile, String configFile, String symbolicControllerPath, String outPath,
			boolean createFolder, ConsolePrinter consolePrinter, boolean useJitController) throws Exception {
		try {
			cPrint(consolePrinter, "Preparing to compute scenario suite");
		
			Env.resetEnv();

			Properties prop = new Properties();
			InputStream is = null;
			try {
				is = new FileInputStream(configFile);
				prop.load(is);

			} catch (IOException e) {
				cPrint(consolePrinter, "Failed to read properties file: " + configFile);
				System.out.println(getStackTrace(e));

				return;
			} finally {
				if (is != null) {
					is.close();
				}
			}

			boolean isDfs = false;
			int depth = 0;
			int width = 0;
			double requestedCoverage = 1;

			try {
				isDfs = prop.getProperty("isSS").equals("true");
			} catch (Exception e) {
				throw new Exception("Missing property in .properties file.");
			}

			try {
				requestedCoverage = Double.parseDouble(prop.getProperty("coverageGoal"));
			} catch (Exception e) {
				depth = 1;
			}

			try {
				depth = Integer.parseInt(prop.getProperty("recursiveDepth"));
				width = Integer.parseInt(prop.getProperty("recursiveWidth"));
			} catch (Exception e) {
				// If error in parsing these just use 0 as a default.
				depth = 0;
				width = 0;
			}

			String iniTreePath;
			String iniBddsPath;
			try {
				iniTreePath = prop.getProperty("iniTreePath");
				iniBddsPath = prop.getProperty("iniBddsPath");
			} catch (Exception e) {
				// If error in parsing these just use null as a default.
				iniTreePath = null;
				iniBddsPath = null;
			}

			// Used to load Env
			cPrint(consolePrinter, "Loading Game");

			List<List<String>> families = new ArrayList<>();
			ArrayList<Integer> familySizes = new ArrayList<>();

			String[] varSets;
			try {
				varSets = prop.getProperty("varSets").split(",");
			} catch (Exception e) {
				throw new Exception("Missing varSets property in .properties file.");
			}

			for (String varSet : varSets) {
				varSet = varSet.replaceAll("\\s+", "");
				if (varSet.equals("")) {
					continue;
				}

				cPrint(consolePrinter, varSet + ":");

				int k;
				ArrayList<String> vars;
				try {
					k = Integer.parseInt(prop.getProperty(varSet + ".k"));
					vars = new ArrayList<String>(Arrays.asList(prop.getProperty(varSet + ".vars").split(",")));
				} catch (Exception e) {
					throw new Exception("Exception parsing a varSet.");
				}

				for (int i = 0; i < vars.size(); i++) {
					vars.set(i, vars.get(i).replaceAll("\\s+", ""));
				}
				vars.remove("");

				cPrint(consolePrinter, vars.toString());

				families.add(vars);
				familySizes.add(k);

			}

			long startTime = System.currentTimeMillis();

			CoverageWrapper.computeCoverageUI(specFile, outPath, isDfs, width, depth, 0, symbolicControllerPath,
					families, familySizes, createFolder, iniTreePath, iniBddsPath, requestedCoverage, useJitController);
			long endTime = System.currentTimeMillis();

			cPrint(consolePrinter, "Computation time: " + (endTime - startTime) + "ms");
			cPrint(consolePrinter, getCSVResults(Paths.get(outPath, "ctd-info.csv").toString()));
		} finally {
			Env.resetEnv();
		}
	}

	/**
	 * Computes a coverage based on the .ctd.properties file of the spec
	 * 
	 * @param specFile       The spec file
	 * @param consolePrinter Prints to console if supplied. Can be null.
	 * @param useJitController Use a JIT controller or a static controller
	 * @throws Exception
	 */
	public static void compute(IFile specFile, ConsolePrinter consolePrinter, boolean useJitController) throws Exception {
		IProject proj = specFile.getProject();
		IFile configFile = proj.getFile(specFile.getProjectRelativePath().toString() + ".ctd.properties");

		String outPath = specFile.getLocation().removeLastSegments(1).addTrailingSeparator().toString() + "out"
				+ File.separator + (useJitController? "jit" : "static") + File.separator;

		File ctDir = new File(outPath, "ctd.out");
		if (!ctDir.exists()) {
			ctDir.mkdirs();
		}

		compute(specFile.getLocation().toString(), configFile.getLocation().toString(), outPath,
				ctDir.getAbsolutePath(), true, consolePrinter, useJitController);
	}

	public static class VariableComparator implements Comparator<String> {
		@Override
		public int compare(String arg0, String arg1) {
			if (arg0.equals("Zn")) {
				return 1;
			}

			if (arg1.equals("Zn")) {
				return -1;
			}

			if (arg0.contains(".") && arg1.contains(".")) {
				return arg0.compareTo(arg1);
			}

			if (arg0.contains(".")) {
				return 1;
			}

			if (arg1.contains(".")) {
				return -1;
			}

			return arg0.compareTo(arg1);
		}
	}

	/**
	 * Generates a sample .ctd.properties config file using the symbolic controller.
	 * 
	 * @param specFile       The spec to generate the config for
	 * @param consolePrinter Printer object for info
	 * @param useJitController Use a JIT controller or a static controller
	 * @throws CoreException
	 * @throws IOException
	 */
	public static void generateConfigFromSC(IFile specFile, ConsolePrinter consolePrinter, boolean useJitController)
			throws CoreException, IOException {
		try {
			Env.resetEnv();
			IProject proj = specFile.getProject();
			String configFilePath = specFile.getProjectRelativePath().toString() + ".ctd.properties";
			IFile configFile = proj.getFile(configFilePath);

			Map<String, String[]> sysVars = new HashMap<>();
			Map<String, String[]> envVars = new HashMap<>();
			
			String controllerPath = specFile.getLocation().removeLastSegments(1).append("/out").toString() +
					File.separator + (useJitController? "jit" : "static");

			cPrint(consolePrinter, "Loading symbolic controller.");
			
			String name = specFile.getName().substring(0, specFile.getName().indexOf("."));

			CoverageWrapper.loadVariables(controllerPath, name, sysVars, envVars);
			Controller controller = useJitController ? new BasicJitController() : new StaticController();
				
			controller.load(controllerPath, name, sysVars, envVars);
			
			cPrint(consolePrinter, "Loaded symbolic controller.");

			String contents = "isSS=true\n" + "coverageGoal=1.0\n" + "\n#iniTreePath=\n#iniBddsPath=\n"
					+ "\nvarSets=allVars\n\n";
			contents += "allVars.k = 2\nallVars.vars = ";

			List<String> sorted_vars = new ArrayList<>(sysVars.keySet());
			sorted_vars.addAll(envVars.keySet());
			Collections.sort(sorted_vars, new VariableComparator());

			for (String key : sorted_vars) {
				contents += key + ", ";
			}

			// Remove last ,
			contents = contents.substring(0, contents.length() - 2);

			InputStream empty = new ByteArrayInputStream(contents.getBytes());
			cPrint(consolePrinter, "Creating config file.");
			try {
				configFile.create(empty, false, null);
			} catch (Exception e) {
				cPrint(consolePrinter,
						"Failed creating .properties file - make sure " + configFilePath + " does not exist.");
				System.out.println(getStackTrace(e));
				return;
			}
			cPrint(consolePrinter, "Completed generating config file.");
		} finally {
			Env.resetEnv();
		}
	}

	/**
	 * Generates a sample .ctd.properties config file using the specification file.
	 * 
	 * @param specFile       The spec to generate the config for
	 * @param consolePrinter Prints to console if supplied. Can be null.
	 * @throws CoreException
	 */
	public static void generateConfigFromSpec(IFile specFile, ConsolePrinter consolePrinter) throws CoreException {
		try {
			IProject proj = specFile.getProject();
			String configFilePath = specFile.getProjectRelativePath().toString() + ".ctd.properties";
			IFile configFile = proj.getFile(configFilePath);

			String contents = "isSS=true\n" + "coverageGoal=1.0\n" + "\n#iniTreePath=\n#iniBddsPath=\n"
					+ "\nvarSets=allVars\n\n";
			contents += "allVars.k = 2\nallVars.vars = ";

			cPrint(consolePrinter, "Loading game.");
			GameModel m = Helper.getGameModel(specFile.getFullPath().toOSString());
			cPrint(consolePrinter, "Loaded spec.");

			PlayerModule sys = m.getSys();
			PlayerModule env = m.getEnv();

			List<ModuleBDDField> varsSys = sys.getAllFields();
			List<ModuleBDDField> varsEnv = env.getAllFields();

			List<String> sorted_vars = new ArrayList<>();

			for (ModuleBDDField moduleBDDField : varsEnv) {
				sorted_vars.add(moduleBDDField.getName());
			}

			for (ModuleBDDField moduleBDDField : varsSys) {
				sorted_vars.add(moduleBDDField.getName());
			}

			Collections.sort(sorted_vars, new VariableComparator());
			for (String key : sorted_vars) {
				contents += key + ", ";
			}

			// Remove last ,
			contents = contents.substring(0, contents.length() - 2);

			InputStream empty = new ByteArrayInputStream(contents.getBytes());
			cPrint(consolePrinter, "Creating config file.");
			try {
				configFile.create(empty, false, null);
			} catch (Exception e) {
				cPrint(consolePrinter,
						"Failed createing .properties file - make sure " + configFilePath + " does not exist.");
				return;
			}
			cPrint(consolePrinter, "Completed generating config file.");
		} finally {
			Env.resetEnv();
		}
	}

	private static void cPrint(ConsolePrinter consolePrinter, String str) {
		if (consolePrinter != null) {
			consolePrinter.println(str);
		} else {
			System.out.println(str);
		}
	}
}
