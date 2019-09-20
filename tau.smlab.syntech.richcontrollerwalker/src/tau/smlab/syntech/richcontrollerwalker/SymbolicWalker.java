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

package tau.smlab.syntech.richcontrollerwalker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.QualifiedName;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDD.BDDIterator;
import net.sf.javabdd.BDDDomain;
import tau.smlab.syntech.games.controller.symbolic.SymbolicController;
import tau.smlab.syntech.games.controller.symbolic.SymbolicControllerReaderWriter;
import tau.smlab.syntech.jtlv.BDDPackage;
import tau.smlab.syntech.jtlv.BDDPackage.BBDPackageVersion;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.jtlv.env.module.ModuleBDDField;
import tau.smlab.syntech.spectragameinput.ErrorsInSpectraException;
import tau.smlab.syntech.spectragameinput.SpectraInputProvider;
import tau.smlab.syntech.spectragameinput.SpectraTranslationException;
import tau.smlab.syntech.logs.BDDLogReader;
import tau.smlab.syntech.logs.BDDLogWriter;

public class SymbolicWalker {

	private static final String ENERGY_VAL = "energyVal";
	// private static final String STEPS_SEPARATOR =
	// "========================================";
	private List<String> prints;
	private BDD currentState;
	private BDD envVars;
	private BDD sysVars;
	private List<BDD> curSysSteps;
	private List<BDD> curEnvSteps;
	private SymbolicController ctrl;
	private boolean initialState;
	private int walkingDepth;
	private int alternativeStepCount;
	private boolean envDeadLock;
	private IFile specFile;
	private PrintStream out;
	private UserModule userModule;
	private UserModule curPlayingModule;
	protected BDDLogWriter log;
	private boolean isLogActive;
	private List<BDD> logData;
	private List<BDD> reachData;
	private int logIndex;
	private int reachIndex;
	private Stack<Integer> consoleLines; // Stack of number of lines of each console message.
	public Stack<BDD> stepsStack;
	public Stack<BDD> compareStack;
	public Stack<Integer> stepIndexesStack;
	public int lastStepIndex;
	private BDDIterator steps;
	protected static HashMap<Integer, BreakPoint> breakPoints = new HashMap<>();
	protected static HashMap<Integer, BDD> watches = new HashMap<>();
	private boolean playEnv;
	private boolean playSys;
	private BDD curMove;
	private boolean followingLog;
	private boolean followingReachability;
	private String logFileName;
	private String logFilePath;
	private BDD currentStateCompare;
	private String moduleName;
	private long engUpperBound;

	public enum UserModule {
		ENV, SYS, BOTH;

		public boolean isEnv() {
			return this.equals(BOTH) || this.equals(ENV);
		}

		public boolean isSys() {
			return this.equals(BOTH) || this.equals(SYS);
		}

		protected boolean isBoth() {
			return this.equals(BOTH);
		}

		@Override
		public String toString() {
			if (isBoth()) {
				return "both SYS and ENV";
			}
			if (isSys()) {
				return "SYS";
			}

			return "ENV";
		}
	}

	/**
	 * @return true if writing to log file, false otherwise
	 */
	public boolean isGeneratingLog() {
		return isLogActive;
	}

	/**
	 * Constructor for SymbolicWalker
	 * 
	 * @param out
	 *            Output stream to write to
	 * @param walkingDepth
	 *            number of steps generated by the controller after the user makes
	 *            his choice
	 * @param alternativeStepCount
	 *            amount of max possible steps available for the user to choose from
	 * @param specFile
	 *            the spectra file
	 * @param bddPackage
	 *            BDD package
	 * @param bddVersion
	 *            BDD Version
	 * @param playEnv
	 *            true if the user is playing as ENV, false otherwise
	 * @param playSys
	 *            true if the user is playing as SYS, false otherwise
	 * @param isLogActive
	 *            on startup, enable log writing
	 * @throws Exception
	 */
	public SymbolicWalker(PrintStream out, int walkingDepth, int alternativeStepCount, IFile specFile,
			BDDPackage bddPackage, BBDPackageVersion bddVersion, boolean playEnv, boolean playSys, boolean isLogActive)
			throws Exception {
		this.prints = new ArrayList<>();
		this.out = out;
		this.specFile = specFile;
		this.walkingDepth = walkingDepth;
		this.alternativeStepCount = alternativeStepCount;
		this.playEnv = playEnv;
		this.playSys = playSys;
		setUserModule();
		envDeadLock = false;
		initialState = false;
		BDDPackage.setCurrPackage(bddPackage, bddVersion);

		this.moduleName = SpectraInputProvider.getSpectraModel(this.specFile.getFullPath().toString()).getName();

		init();
		this.isLogActive = isLogActive;

		if (isLogActive) {
			createNewLog();
		}
		this.followingLog = false;
		this.followingReachability = false;
	}

	/**
	 * Evaluates watch
	 * 
	 * @param watchIndex
	 *            index of watch to evaluate
	 * @return evaluation
	 */
	public boolean getWatchValue(int watchIndex) {
		if (!watches.containsKey(watchIndex))
			return false;
		return !watches.get(watchIndex).id().andWith(currentStateCompare.id()).equals(Env.FALSE());
	}

	/**
	 * Evaluates breakpoint
	 * 
	 * @param bpIndex
	 *            index of breakpoint to evaluate
	 * @return evaluation
	 */
	public boolean isBreakPointStop(int bpIndex) {
		if (!breakPoints.containsKey(bpIndex))
			return false;
		return !breakPoints.get(bpIndex).getBDD().id().andWith(currentStateCompare.id()).equals(Env.FALSE());
	}

	/**
	 * Reset the current player
	 */
	public void setUserModule() {
		if (playEnv && playSys) {
			this.userModule = UserModule.BOTH;
			this.curPlayingModule = UserModule.ENV;
		} else if (playSys) {
			this.userModule = UserModule.SYS;
			this.curPlayingModule = UserModule.SYS;
		} else {
			this.userModule = UserModule.ENV;
			this.curPlayingModule = UserModule.ENV;
		}
	}

	public boolean isEnvDeadLock() {
		return this.envDeadLock;
	}

	public List<String> getPrints() {
		return this.prints;
	}

	/**
	 * After the user chose a step to perform call this method to perform a depth
	 * walk, the method will generate random steps according to the number given in
	 * the constructor
	 * 
	 * @throws IOException
	 *             Failed writing to log if active
	 */
	public void doDepthSteps() throws IOException {
		if (envDeadLock) {
			return;
		}

		if (userModule.isBoth() && (curPlayingModule.isSys() || initialState)) {
			// if the user plays as both modules, perform depth steps
			// only when the current playing module is ENV
			return;
		}

		if (userModule.isEnv() && !userModule.isBoth()) {
			// the user plays only as ENV
			if (initialState)
				return; // do not perform any step if this is the initial state
			doSysStep();
		}

		boolean bp = false;
		for (int i = 0; i < walkingDepth && !bp; i++) {
			if (doEnvStep()) {
				doSysStep();

				// Stop on active breakpoints
				for (int index : breakPoints.keySet()) {
					if (breakPoints.get(index).isActive() && (bp = isBreakPointStop(index)) == true) {
						return;
					}
				}

			} else {
				envDeadLock = true;
				addToListAndPrintlnToConsole("ENV: Environment deadlock - system has won!");
				return;
			}
		}

		if ((!userModule.isEnv()) && !envDeadLock) {
			if (!doEnvStep()) {
				envDeadLock = true;
				addToListAndPrintlnToConsole("ENV: Environment deadlock - system has won!");
			}
		}
	}

	private void addToListAndPrintlnToConsole(String string) {
		prints.add(string);
		out.println(string);
	}

	public void donePrintingToConsole() {
		consoleLines.push(prints.size());
	}

	/**
	 * Init the variables according to spectra file
	 * 
	 * @throws SpectraTranslationException
	 * @throws CoreException
	 * @throws IOException
	 * @throws ErrorsInSpectraException
	 */
	public void init() throws ErrorsInSpectraException, IOException, CoreException, SpectraTranslationException {
		IPath filePath = specFile.getLocation();
		String path = filePath.uptoSegment(filePath.segmentCount() - 1).toString();
		path = path + "/out/";
		Set<String> sysVarsNames = null;
		Set<String> envVarsNames = null;
		try {
			sysVarsNames = SymbolicControllerReaderWriter.readSysVarNames(path);
			envVarsNames = SymbolicControllerReaderWriter.readEnvVarNames(path);
			ctrl = SymbolicControllerReaderWriter.readSymbolicController(path);
		} catch (IOException e) {
			// e.printStackTrace();
		}

		if (!isSpecFileMatchesController(path + "vars.doms")) {
			throw new FileNotFoundException(ControllerConstants.MISMATCHED_CONTROLLER);
		}

		if (!ExpressionHelper.specContainsEnergyValVar(this.specFile) && Env.getVar(ENERGY_VAL) != null) {
			this.engUpperBound = Env.getVar(ENERGY_VAL).getDomain().size().longValue() - 1;
		} else {
			this.engUpperBound = -1;
		}

		envVars = getModuleBDDVarSet(envVarsNames);
		sysVars = getModuleBDDVarSet(sysVarsNames);

		initSteps();
	}

	/**
	 * Reset the controller steps data
	 */
	private void initSteps() {
		if (userModule.isSys())
			curSysSteps = new ArrayList<>();
		if (userModule.isEnv())
			curEnvSteps = new ArrayList<>();

		currentState = ctrl.initial().id();
		initialState = true;
		currentStateCompare = currentState.id();

		this.stepsStack = new Stack<>();
		this.compareStack = new Stack<>();
		this.stepIndexesStack = new Stack<>();
		this.lastStepIndex = -1;
		this.consoleLines = new Stack<Integer>();
	}

	/**
	 * Create a BDD from a group of string variables
	 * 
	 * @param moduleVarsNames
	 *            variables of the BDD
	 * @return BDD representation of the variables
	 */
	private BDD getModuleBDDVarSet(Set<String> moduleVarsNames) {
		ModuleBDDField field;
		BDDDomain fieldDom;
		BDD moduleVars = Env.TRUE();
		for (String moduleVarName : moduleVarsNames) {
			field = Env.getVar(moduleVarName);
			fieldDom = field.getDomain();
			moduleVars.andWith(fieldDom.set().toBDD());
		}
		return moduleVars;
	}

	/**
	 * Check that the spectra file math the controller
	 * 
	 * @param varsDomsPath
	 *            path to vars file
	 * @return trueif match, false otherwise
	 */
	private boolean isSpecFileMatchesController(String varsDomsPath) {
		// String moduleNameFromSpecFile =
		// SpectraInputProvider.getSpectraModel(specFile).getName();

		try {
			FileInputStream fis = new FileInputStream(varsDomsPath);
			ObjectInputStream ois = new ObjectInputStream(fis);
			@SuppressWarnings("unchecked")
			ArrayList<String[]> fieldValues = (ArrayList<String[]>) ois.readObject();
			if (fieldValues != null && fieldValues.size() > 0) {
				String[] firstVariable = fieldValues.get(0);
				String varKind = firstVariable[0];
				int separatorIndex = varKind.indexOf("_");
				String moduleNameFromVarsDomsFile = varKind.substring(0, separatorIndex);
				if (moduleNameFromVarsDomsFile.equals(this.moduleName)) {
					ois.close();
					return true;
				}
			}
			ois.close();
			return false;

		} catch (Exception e) {
			// e.printStackTrace();
			return false;
		}

	}

	/**
	 * Compute the next possible steps that the user can perform
	 * 
	 * @return a list of possible steps
	 */
	public List<String> computeCurrentPlayerSteps() {
		return computeCurrentPlayerSteps(true);
	}

	/**
	 * Compute the next possible steps that the user can perform
	 * 
	 * @param updateCurrentSteps
	 *            update the current state
	 * @return a list of possible steps
	 */
	public List<String> computeCurrentPlayerSteps(boolean updateCurrentSteps) {
		if (curPlayingModule.isSys())
			return computeSysSteps(updateCurrentSteps);
		if (curPlayingModule.isEnv())
			return computeEnvSteps(updateCurrentSteps);
		return null;
	}

	/**
	 * Compute the next possible steps that the user can perform as ENV
	 * 
	 * @param updateCurrentSteps
	 *            update the current state
	 * @return a list of possible steps
	 */
	private List<String> computeEnvSteps(boolean updateCurrentSteps) {
		if (updateCurrentSteps) {
			if (envDeadLock)
				return null;

			if (!initialState) {
				BDD successorStates = ctrl.succ(currentState);
				currentState.free();
				currentState = successorStates;
			}
			if (currentState.isZero()) {
				envDeadLock = true;
				addToListAndPrintlnToConsole("ENV: Environment deadlock - system has won!");
				return null;
			}
		}
		return computeModuleSteps(envVars, curEnvSteps);
	}

	/**
	 * Compute the next possible steps that the user can perform as SYS
	 * 
	 * @param updateCurrentSteps
	 *            update the current state
	 * @return a list of possible steps
	 */
	private List<String> computeSysSteps(boolean updateCurrentSteps) {
		// Computing SYS steps does not use updateCurrentSteps.
		return computeModuleSteps(sysVars, curSysSteps);
	}

	/**
	 * Compute the next possible steps that the user can perform
	 * 
	 * @param moduleVars
	 *            variables of the current module
	 * @param curModuleSteps
	 *            list of of steps to be filled by the function (BDD form)
	 * @return list of possible steps (String form)
	 */
	private List<String> computeModuleSteps(BDD moduleVars, List<BDD> curModuleSteps) {
		steps = currentState.iterator(moduleVars.toVarSet());

		Env.free(curModuleSteps);
		curModuleSteps.clear();
		List<String> stepsList = new ArrayList<>();

		while (steps.hasNext() && stepsList.size() < alternativeStepCount) {
			curMove = steps.nextBDD();
			curModuleSteps.add(curMove);
			stepsList.add(curMove.toStringWithDomains(Env.stringer));
		}

		return stepsList;
	}

	/**
	 * Check if there are more steps available
	 * 
	 * @return true if there are more steps available, false otherwise
	 */
	public boolean hasMoreSteps() {
		return steps.hasNext();
	}

	/**
	 * Load more steps (use after computeCurrentPlayerSteps)
	 * 
	 * @return list of posssible steps
	 */
	public List<String> getMoreSteps() {
		List<BDD> curModuleSteps = null;
		if (curPlayingModule.isSys()) {
			curModuleSteps = curSysSteps;
		} else if (curPlayingModule.isEnv()) {
			curModuleSteps = curEnvSteps;
		} else {
			return null;
		}

		List<String> stepsList = new ArrayList<>();
		BDD curMove;

		// add steps to the list until max reached or no more steps
		while (steps.hasNext() && stepsList.size() < alternativeStepCount) {
			curMove = steps.nextBDD();
			curModuleSteps.add(curMove);
			stepsList.add(curMove.toStringWithDomains(Env.stringer));
		}

		return stepsList;
	}

	/**
	 * Perform a step chosen by the user
	 * 
	 * @param stepId
	 *            the index of the step in the steps list
	 * @param curModuleSteps
	 *            The list of BDD steps
	 * @param playingModule
	 *            the playing module
	 */
	private void doChosenModuleStep(int stepId, List<BDD> curModuleSteps, UserModule playingModule) {
		if (followingLog || followingReachability) {
			// if following log or reachability load the next step from the log or route
			// array
			BDD next = null;
			if (followingReachability) {
				reachIndex++;
				next = getCurrentReachabilityItem();
				if (followingLog) {
					logIndex++;
				}
			} else {
				logIndex++;
				next = getCurrentLogItem();
			}

			if (next != null) {
				currentState.free();
				currentState = next;
				currentStateCompare.free();
				currentStateCompare = currentState.id();
			}
		} else if (stepId >= 0 && stepId < curModuleSteps.size()) {
			// Save step history
			stepsStack.push(currentState.id());
			compareStack.push(currentStateCompare.id());
			stepIndexesStack.push(Integer.valueOf(stepId));

			BDD updatedState = currentState.and(curModuleSteps.get(stepId));
			currentState.free();
			currentState = updatedState;
			currentStateCompare.free();
			currentStateCompare = currentState.id();

			// Log state
			if (curPlayingModule.isSys() && isGeneratingLog() && !followingLog) {
				try {
					log.write(currentStateCompare);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			// addToListAndPrintlnToConsole(STEPS_SEPARATOR);
			addToListAndPrintlnToConsole(
					playingModule.toString() + ": " + curModuleSteps.get(stepId).toStringWithDomains(Env.stringer));
			// addToListAndPrintlnToConsole(STEPS_SEPARATOR);
		}
	}

	/**
	 * Perform a step chosen by the user
	 * 
	 * @param stepId
	 *            the index of the step in the steps list
	 * @return true if it is SYS player turn, false otherwise
	 */
	public boolean doChosenSysStep(int stepId) {
		if (UserModule.SYS.equals(curPlayingModule)) {
			doChosenModuleStep(stepId, curSysSteps, UserModule.SYS);
			if (userModule.isBoth()) {
				curPlayingModule = UserModule.ENV;
			}
			return true;
		}
		return false;
	}

	/**
	 * Perform a step chosen by the user
	 * 
	 * @param stepId
	 *            the index of the step in the steps list
	 * @return true if it is ENV player turn, false otherwise
	 */
	public boolean doChosenEnvStep(int stepId) {
		if (UserModule.ENV.equals(curPlayingModule)) {
			doChosenModuleStep(stepId, curEnvSteps, UserModule.ENV);
			if (userModule.isBoth()) {
				curPlayingModule = UserModule.SYS;
			}
			if (initialState) {
				initialState = false;
			}
			return true;
		}
		return false;
	}

	/**
	 * Perform a SYS step generated by the controller
	 */
	private void doSysStep() throws IOException {
		if (followingLog) {
			BDD next = getCurrentLogItem();
			if (next != null) {
				currentState.free();
				currentState = next;
				logIndex++;
			} else {
				followingLog = false;
			}
		} else if (!envDeadLock) {
			BDD sysChoice = randomSat(currentState, sysVars);
			addToListAndPrintlnToConsole("SYS: " + sysChoice.toStringWithDomains(Env.stringer));
			currentState.andWith(sysChoice);

			// Log state
			if (isGeneratingLog())
				log.write(currentState);
		}

		currentStateCompare = currentState.id();
	}

	/**
	 * Perform an ENV step generated by the controller
	 */
	private boolean doEnvStep() throws IOException {
		BDD input;
		boolean stepPerformed = false;
		if (initialState) {
			input = randomSat(currentState, envVars);
			if (!input.isZero()) {
				addToListAndPrintlnToConsole("ENV: " + input.toStringWithDomains(Env.stringer));
				currentState.andWith(input);
				initialState = false;
				stepPerformed = true;
			} /*
				 * else { }
				 */
			currentStateCompare = currentState.id();
		} else {
			BDD successorStates = ctrl.succ(currentState);
			if (!successorStates.isZero()) {
				currentState.free();
				currentState = successorStates;
				input = randomSat(currentState, envVars);
				addToListAndPrintlnToConsole("ENV: " + input.toStringWithDomains(Env.stringer));
				currentState.andWith(input);
				stepPerformed = true;
			}
			currentStateCompare = currentState.id();
		}
		return stepPerformed;
	}

	/**
	 * Loads a log for log walk
	 * 
	 * @param path
	 *            to log
	 * @return true on successful load, false otherwise
	 */
	public boolean loadLog(String path) {
		boolean isValid = false;
		try {
			BDDLogReader reader = new BDDLogReader(path);
			logData = new ArrayList<>();

			// Load states
			for (BDD b : reader) {
				logData.add(b);
				// Assuming a log is only valid if at least 1 state isn't pure true/false
				// (reader returns true on invalid lines)
				if (!(b.equals(Env.TRUE()) || b.equals(Env.FALSE()))) {
					isValid = true;
				}
			}

			// Assert valid states loaded correctly
			if (logData.size() == 0 || !isValid) {
				return false;
			}

			this.logIndex = 0;
			this.followingLog = true;
			if (!(this.currentState != null || currentState.isFree()))
				this.currentState.free();
			this.initialState = true;
			this.currentState = logData.get(0).id();
			this.currentStateCompare = logData.get(0).id();
			setUserModule();

			this.compareStack.clear();
			this.stepsStack.clear();
			this.stepIndexesStack.clear();

			// Create new log if log generation is active
			if (isLogActive) {
				createNewLog();
			}

		} catch (Exception e) {
			isValid = false;
			this.isLogActive = false;
			e.printStackTrace();
			return isValid;
		}

		return isValid;
	}

	/**
	 * Returns a BDD which represents the current state in a log walk
	 * 
	 * @return BDD of current log state
	 */
	private BDD getCurrentLogItem() {
		if (followingLog && logIndex >= 0 && logIndex <= logData.size() - 1) {
			BDD ret = logData.get(logIndex).id();
			return ret;
		}
		return null;
	}

	/**
	 * Returns a BDD which represents the current state in a reachability walk
	 * 
	 * @return BDD of current reachability state
	 */
	private BDD getCurrentReachabilityItem() {
		if (followingReachability && reachIndex >= 0 && reachIndex <= reachData.size() - 1) {
			BDD ret = reachData.get(reachIndex).id();
			return ret;
		}

		return null;
	}

	/**
	 * Returns the string that represents the BDD of the current state in a log or
	 * reachability walk
	 * 
	 * @return String that represents BDD or null if out of states
	 */
	public String getCurrentState() {
		BDD next = null;
		boolean endWalk = false;
		// Load reachability state
		if (isFollowingReachability()) {
			next = getCurrentReachabilityItem();
			if (next == null) {
				this.exitReachabilityMode();
				endWalk = true;
			}
		}

		// Load loaded log state
		if (isFollowingLog()) {
			next = getCurrentLogItem();
			if (next == null) {
				this.exitLogMode();
				return null;
			}
		}

		// Mark reachability ended
		if (endWalk)
			return null;

		if (next != null) {
			this.prints.clear(); // prevents writing same state twice when entering reachability
			// addToListAndPrintlnToConsole(STEPS_SEPARATOR);
			// addToListAndPrintlnToConsole("State" + ": " +
			// next.toStringWithDomains(Env.stringer));
			// addToListAndPrintlnToConsole(STEPS_SEPARATOR);

			return next.toStringWithDomains(Env.stringer);
		}

		return null;
	}

	/**
	 * Returns a BDD which represents a satisfying assignment of the specified bdd.
	 * The assignment is random and refers to the specified variables.
	 * 
	 * @param bdd
	 * @param variables
	 *            The variables the assignment should refer to
	 * @return A random assignment of the specified variables which satisfies the
	 *         specified bdd.
	 */
	private BDD randomSat(BDD bdd, BDD variables) {

		if (bdd.isZero()) {
			return Env.FALSE();
		}

		// now we are sure we have a non trivially FALSE BDD
		BDD satRes = Env.TRUE(), satCheck;
		Random random = new Random();
		boolean randChoice;
		int[] varProfile = variables.varProfile();
		for (int i = 0; i < varProfile.length; i++) {
			if (varProfile[i] > 0) {
				randChoice = random.nextBoolean();
				if (randChoice) {
					satCheck = bdd.and(satRes).andWith(bdd.getFactory().ithVar(i));
				} else {
					satCheck = bdd.and(satRes).andWith(bdd.getFactory().nithVar(i));
				}
				if (!satCheck.isZero()) {
					satRes.andWith(randChoice ? bdd.getFactory().ithVar(i) : bdd.getFactory().nithVar(i));
				} else {
					satRes.andWith(randChoice ? bdd.getFactory().nithVar(i) : bdd.getFactory().ithVar(i));
				}
				satCheck.free();
			}
		}
		return satRes;
	}

	/**
	 * Plays reachability game for breakpoint in given index
	 * 
	 * @param bPIndex
	 *            index of breakpoint
	 */
	public void playReachability() {
		if (this.reachData != null) {
			compareStack.clear();
			stepsStack.clear();
			stepIndexesStack.clear();
			this.followingReachability = true;
			this.reachIndex = 0;
		}
	}

	/**
	 * Checks reachability for breakpoint in given index
	 * 
	 * @param bPIndex
	 *            index of breakpoint
	 * @return true on success, false otherwise
	 */
	public boolean checkReachability(int bpIndex) {

		if (!breakPoints.containsKey(bpIndex) || breakPoints.get(bpIndex) == null)
			return false;
		List<BDD> reachRoute = null;
		BDD bpBDD = breakPoints.get(bpIndex).getBDD();

		// Calculate route to breakpoint
		if (!followingLog) {
			// Calculate route
			reachRoute = ReachabilityGame.playReachabilityToBreakPoint(bpBDD, ctrl, this.currentState);
		} else {
			// Calculate route in log
			reachRoute = ReachabilityGame.playLogReachabilityToBreakPoint(bpBDD,
					this.logData.subList(logIndex, logData.size()));
		}

		if (reachRoute == null || reachRoute.isEmpty()) {
			return false;
		}

		this.reachData = reachRoute;

		return true;
	}

	/**
	 * Adds breakpoint in index from given expression
	 * 
	 * @param bPIndex
	 *            index of breakpoint
	 * @param bPExpression
	 *            expression to use as breakpoint
	 * @return true on successful addition, false otherwise
	 */
	public boolean addBreakpoint(int bPIndex, String bPExpression) {
		boolean res = false;
		BDD bPStates;
		try {
			bPStates = ExpressionHelper.addExpression(bPExpression, specFile, this.engUpperBound);
		} catch (Exception e) {
			e.printStackTrace();
			return res;
		}

		if (bPStates != null) {
			// Add to breakpoint collection
			breakPoints.put(bPIndex, new BreakPoint(bPStates));

			// Persist
			saveBreakpoint(bPIndex, bPStates, bPExpression);

			res = true;
		}

		return res;
	}

	/**
	 * Adds watch in index from given expression
	 * 
	 * @param watchIndex
	 *            index of watch
	 * @param watchExpression
	 *            expression to use as watch
	 * @return true on successful addition, false otherwise
	 */
	public boolean addWatch(int watchIndex, String watchExpression) {
		boolean res = false;
		BDD watchStates;
		try {
			watchStates = ExpressionHelper.addExpression(watchExpression, specFile, this.engUpperBound);
		} catch (Exception e) {
			e.printStackTrace();
			return res;
		}

		if (watchStates != null) {
			// Add to watch collection
			watches.put(watchIndex, watchStates);

			// Persist
			saveWatch(watchIndex, watchStates, watchExpression);

			res = true;
		}

		return res;
	}

	/**
	 * Removes breakpoint with given index
	 * 
	 * @param bPIndex
	 *            index of breakpoint to remove
	 * @return true on successful removal, false otherwise
	 */
	public boolean removeBreakpoint(int bPIndex) {
		// Remove persistence
		saveBreakpoint(bPIndex, null, null);

		// Remove from breakpoints collection
		breakPoints.remove(bPIndex);

		return true;
	}

	/**
	 * Removes watch with given index
	 * 
	 * @param watchIndex
	 *            index of watch to remove
	 * @return true on successful removal, false otherwise
	 */
	public boolean removeWatch(int watchIndex) {
		// Remove persistence
		saveWatch(watchIndex, null, null);

		// Remove from watches collection
		watches.remove(watchIndex);
		return true;
	}

	/**
	 * Saves watch for future walks
	 * 
	 * @param watchIndex
	 *            index of watch to save
	 * @param watch
	 *            watch to save
	 * @param watchExpression
	 *            watch expression to save
	 */
	private void saveWatch(int watchIndex, BDD watch, String watchExpression) {
		// Save watch expression
		try {
			savePersistentProperty(getWatchExpressionQualifier(watchIndex), watchExpression);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Get a watch qualified name for persistence
	 * 
	 * @param watchIndex
	 *            watch id
	 * @return The watch qualified name
	 */
	private QualifiedName getWatchExpressionQualifier(int watchIndex) {
		return new QualifiedName(ControllerConstants.WATCH_NAME_PREFIX + watchIndex,
				ControllerConstants.WATCH_NAME_PREFIX + watchIndex);
	}

	/**
	 * Get a breakpoint qualified name for persistence
	 * 
	 * @param bpIndex
	 *            breakpoint id
	 * @return The breakpoint qualified name
	 */
	private QualifiedName getBreakpointExpressionQualifier(int bpIndex) {
		return new QualifiedName(ControllerConstants.BREAKPOINT_NAME_PREFIX + bpIndex,
				ControllerConstants.BREAKPOINT_NAME_PREFIX + bpIndex);
	}

	/**
	 * Saves breakpoint for future walks
	 * 
	 * @param bpIndex
	 *            index of breakpoint to save
	 * @param breakPoint
	 *            breakpoint to save
	 * @param bPExpression
	 *            breakpoint expression to save
	 */
	private void saveBreakpoint(int bpIndex, BDD breakPoint, String bPExpression) {
		// Save breakpoint expression
		try {
			savePersistentProperty(getBreakpointExpressionQualifier(bpIndex), bPExpression);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Loads watch expressions
	 * 
	 * @return set of expressions by index
	 */
	public HashMap<Integer, String> loadWatchExpressionsProperties() {
		Map<QualifiedName, String> persistencyMap;
		try {
			persistencyMap = this.specFile.getPersistentProperties();
		} catch (CoreException e) {
			e.printStackTrace();
			persistencyMap = new HashMap<>();
		}

		HashMap<Integer, String> watchExpressions = new HashMap<>();

		for (QualifiedName qualName : persistencyMap.keySet()) {
			String qualifier = qualName.getQualifier();
			try {
				if (qualifier.startsWith(ControllerConstants.WATCH_NAME_PREFIX)) {
					watchExpressions.put(Integer.parseInt(qualifier.split(ControllerConstants.WATCH_NAME_PREFIX)[1]),
							persistencyMap.get(qualName));
					// addWatch(Integer.parseInt(qualifier.split(ControllerConstants.WATCH_NAME_PREFIX)[1]),
					// persistencyMap.get(qualName));
				}
			} catch (Exception e) {
				// Remove problematic property
				try {
					savePersistentProperty(qualName, null);
				} catch (CoreException e1) {
					e1.printStackTrace();
				}
			}
		}

		return watchExpressions;
	}

	/**
	 * Loads breakpoint expressions
	 * 
	 * @return set of expressions by index
	 */
	public HashMap<Integer, String> loadBreakpointExpressionsProperties() {
		Map<QualifiedName, String> persistencyMap;
		try {
			persistencyMap = this.specFile.getPersistentProperties();
		} catch (CoreException e) {
			e.printStackTrace();
			persistencyMap = new HashMap<>();
		}

		HashMap<Integer, String> bPExpressions = new HashMap<>();

		for (QualifiedName qualName : persistencyMap.keySet()) {
			String qualifier = qualName.getQualifier();

			try {
				if (qualifier.startsWith(ControllerConstants.BREAKPOINT_NAME_PREFIX)) {
					int iD = Integer.parseInt(qualifier.split(ControllerConstants.BREAKPOINT_NAME_PREFIX)[1]);
					bPExpressions.put(iD, persistencyMap.get(qualName));
					// addBreakpoint(iD, persistencyMap.get(qualName));
					// breakPoints.get(iD).setActive(false);
				}
			} catch (Exception e) {
				// Remove problematic property
				try {
					savePersistentProperty(qualName, null);
				} catch (CoreException e1) {
					e1.printStackTrace();
				}
			}
		}

		return bPExpressions;
	}

	/**
	 * Saves property for future walks
	 * 
	 * @param propertyQualifier
	 *            property qualifier
	 * @param value
	 *            value to save
	 * @throws CoreException
	 */
	private void savePersistentProperty(QualifiedName propertyQualifier, String value) throws CoreException {
		this.specFile.setPersistentProperty(propertyQualifier, value);
	}

	/**
	 * Disable breakpoint
	 * 
	 * @param bpIndex
	 *            the breakpoint id
	 */
	public void disableBreakPoint(int bpIndex) {
		if (breakPoints.containsKey(bpIndex))
			breakPoints.get(bpIndex).setActive(false);
	}

	/**
	 * Enable breakpoint
	 * 
	 * @param bpIndex
	 *            the breakpoint id
	 */
	public void enableBreakPoint(int bpIndex) {
		if (breakPoints.containsKey(bpIndex))
			breakPoints.get(bpIndex).setActive(true);
	}

	/**
	 * Clear the prints array
	 */
	public void clearPrints() {
		this.prints.clear();
	}

	/**
	 * Toggles logging state
	 */
	public void changeLogState() throws IOException {
		// Toggle state
		isLogActive = !isLogActive;

		// Close current log
		if (this.log != null) {
			this.log.close();
		}

		// Turned off log generation
		if (!isLogActive) {
			// Remove previous log if no steps were made
			removeEmptyLogFile(this.logFilePath + this.logFileName);
		}
		// Turned on log generation
		else {
			// Generate new log for walk
			createNewLog();
		}
	}

	/**
	 * Creates new log file
	 */
	public void createNewLog() throws IOException {
		// Close current log
		if (this.log != null) {
			this.log.close();
		}

		this.logFilePath = specFile.getLocation().uptoSegment(specFile.getLocation().segmentCount() - 1)
				.addTrailingSeparator().toString();
		this.logFileName = moduleName + "_log_" + new SimpleDateFormat("dd.MM.yyyy_HH_mm_ss").format(new Date())
				+ ".txt";
		this.log = new BDDLogWriter(logFilePath + logFileName, Env.globalUnprimeVars());
		log.write(currentStateCompare);
	}

	/**
	 * Gets name of current log file
	 */
	public String getLogFileName() {
		if (logFileName != null)
			return logFileName;
		return "";
	}

	/**
	 * Resets log file
	 */
	private void resetLog() {
		try {
			this.log.close();
			this.log = new BDDLogWriter(logFilePath + logFileName, Env.globalUnprimeVars());
			this.log.write(currentStateCompare);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Resets walk
	 */
	public void reset() {
		this.setUserModule();
		prints.clear();

		// Reinitialize
		this.initSteps();

		// Reset log
		if (isLogActive) {
			this.resetLog();
		}

		// Exit reachability
		this.exitReachabilityMode();

		// Exit log mode
		this.exitLogMode();
	}

	/**
	 * Handles step back logic
	 * 
	 * @return number of lines to remove from console.
	 */
	public int stepBack() {
		// Return if trying to go back from the first step.
		if (!this.canStepBack())
			return -1;
		// Change turns between ENV and SYS.
		if (userModule.isBoth()) {
			if (curPlayingModule.isSys())
				curPlayingModule = UserModule.ENV;
			else
				curPlayingModule = UserModule.SYS;
		}

		// Go back to previous state
		if (this.isFollowingLog()) {
			this.logIndex--;
		}

		if (this.isFollowingReachability()) {
		}

		// Load previous step.
		if (!(isFollowingLog() || isFollowingReachability())) {
			currentState = stepsStack.pop();
			currentStateCompare = compareStack.pop();
			lastStepIndex = stepIndexesStack.pop();
		} else if (isFollowingLog()) {
			currentState = this.logData.get(this.logIndex).id();
			currentStateCompare = currentState.id();
		} else if (isFollowingReachability()) {
			currentState = this.reachData.get(this.reachIndex).id();
			currentStateCompare = currentState.id();
		}

		// Create new log
		if (isLogActive) {
			try {
				createNewLog();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return consoleLines.pop().intValue();
	}

	public UserModule getPlayingModule() {
		return curPlayingModule;
	}

	public void jumpToStart() {
		if (followingLog) {
			this.logIndex = 0;
			this.reachIndex = 0; // allows skip to end in reachability

			currentState = this.logData.get(this.logIndex).id();
			currentStateCompare = currentState.id();

			// Create new log
			if (isLogActive) {
				try {
					createNewLog();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		this.prints.clear();
	}

	/**
	 * Checks if current state is the last state in log
	 * 
	 * @return true if last state, false otherwise
	 */
	public boolean isEndOfLog() {
		return this.isFollowingLog() && this.logIndex == this.logData.size() - 1;

	}

	/**
	 * Checks if current state is the last state in reachability route
	 * 
	 * @return true if last state, false otherwise
	 */
	public boolean isEndOfReachability() {
		return this.isFollowingReachability() && this.reachIndex == this.reachData.size() - 1;
	}

	public boolean isFollowingLog() {
		return followingLog;
	}

	public boolean isFollowingReachability() {
		return followingReachability;
	}

	/**
	 * Clears log file
	 */
	private void clearLog() {
		// Clear log
		try {
			if (this.log != null) {
				this.log.close();
			}

			// Remove empty logs
			if (isLogActive)
				removeEmptyLogFile(this.logFilePath + this.logFileName);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void removeEmptyLogFile(String path) {
		BufferedReader br = null;

		try {
			File logFile = new File(path);

			br = new BufferedReader(new FileReader(logFile));
			br.readLine();
			if (br.readLine() == null) {
				br.close();
				if (!logFile.delete()) {
					// Failed to delete empty log.
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (br != null)
					br.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Free resources. Called when user closes the window or presses cancel.
	 */
	public void clear() {
		clearLog();
	}

	/**
	 * Checks if step back is feasible
	 */
	public boolean canStepBack() {
		// Log and reachability logs don't use step stacks
		if (isFollowingLog())
			return (logIndex > 0) ? true : false;
		if (isFollowingReachability())
			return (reachIndex > 0) ? true : false;

		return !stepsStack.isEmpty();
	}

	/**
	 * Stop log mode
	 */
	public void exitLogMode() {
		followingLog = false;
		this.logData = null;
		stepsStack.clear();
		compareStack.clear();
		stepIndexesStack.clear();
		setUserModule();
	}

	/**
	 * Stop reachability mode
	 */
	public void exitReachabilityMode() {
		this.followingReachability = false;
		this.reachData = null;
		stepsStack.clear();
		compareStack.clear();
		stepIndexesStack.clear();
		setUserModule();
	}

	/**
	 * Returns path to working directory
	 */
	public String getWorkingDir() {
		return specFile.getLocation().uptoSegment(specFile.getLocation().segmentCount() - 1).toString();
	}
}