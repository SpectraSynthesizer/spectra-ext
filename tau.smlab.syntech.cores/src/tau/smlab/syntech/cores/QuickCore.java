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

package tau.smlab.syntech.cores;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.checks.ddmin.AbstractCDD;
import tau.smlab.syntech.checks.ddmin.Minimizer;
import tau.smlab.syntech.cores.StatsChecker.UnrealCheckInfo;
import tau.smlab.syntech.cores.domainagnostic.MinimizerFactory;
import tau.smlab.syntech.cores.domainagnostic.MinimizerType;
import tau.smlab.syntech.cores.util.Checker;
import tau.smlab.syntech.cores.util.RealizabilityCheck.GameType;
import tau.smlab.syntech.cores.util.RealizabilityCheckResult;
import tau.smlab.syntech.cores.util.RealizabilityChecker;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.util.EnvTraceInfoBuilder;
import tau.smlab.syntech.gamemodel.util.SysTraceInfoBuilder;
import tau.smlab.syntech.gamemodel.util.TraceIdentifier;
import tau.smlab.syntech.games.gr1.GR1Game;
import tau.smlab.syntech.games.gr1.GR1GameExperiments;
import tau.smlab.syntech.games.gr1.GR1GameImplC;
import tau.smlab.syntech.games.gr1.GR1SeparatedGameImplC;

/**
 * Computes an unrealizable core of specified sys behaviors
 * Allows a base which is an assured part of a core while others are minimized
 * Changes the game model
 * 
 * Minimizes the justices first using a domain-agnostic minimizer (ddmin as default), and if none needed removes justice assumptions
 * Minimizes safeties using a minimizer of the same type
 * Inis are minimized using the win region
 * Optional base set of guarantees to build on. Note: Must be a subset of _all_ cores
 * Allows statistics about running times and number of realizability checks.
 * 
 * 
 * @author shalom
 *
 */

public class QuickCore implements Minimizer<Integer> {
	
	// Heuristics supposed to make the core computation faster.
	
	/**
	 * Use the previous realizability check result to remove all justice guarantees
	 * unused by the GR(1) algorithm.
	 */
	public static boolean EARLY_JUTSICE_DETECTION = true;
	
	/**
	 * Reuse winning states as initial Y BDD for later computations.
	 */
	public static boolean INCREMENTAL_UNREAL_RESULTS = true;
	
	public static MinimizerType DEFAULT_MINIMIZER_TYPE = MinimizerType.CDD;
	
	private GameModel gm = null;
	private RealizabilityChecker checker = null;
	// When statistics are used, this checker is used to get check info for different parts of the algorithm
	private Checker<Integer> curChecker = null;
	private TraceIdentifier ti = null;
	private SysTraceInfoBuilder builder = null;
	private List<Integer> nonJust = null;
	private List<Integer> justs = null;
	private List<Integer> ini = null;
	private List<Integer> newEnv = null;
	private List<Integer> base = null; // those must be in the core we find, and not in the part we ask to minimize, nor in the result
	private Stats stats = null;
	private MinimizerType mtype = null;
	private MinimizerFactory<Integer> factory = null;
	private RealizabilityCheckResult initialCheckResult = null;
	
	// Used for INCREMENTAL_UNREAL_RESULTS
	// The winning states for the minimum unrealizable spec from justice minimization
	private BDD minUnrealBdd = null;
	// The number of guarantees in the spec
	private int minUnrealPartsSize = Integer.MAX_VALUE;
	// Whether the result is "complete" - reached a fixed-point
	private boolean minUnrealComplete = true;
	// Whether all the unreal specs so far were complete
	private boolean allUnrealComplete = true;
	// The winning states for the minimum unrealizable spec that was complete so far
	private BDD minCompleteUnrealBdd = null;
	
	public static class Stats {
		public long iniTime;
		public long safeTime;
		public long justTime;
		public int justChecks;	
		public int justActualChecks;
		public List<UnrealCheckInfo> justCheckInfo = new ArrayList<>();
		public int safeChecks;
		public int safeActualChecks;
		public List<UnrealCheckInfo> safeCheckInfo = new ArrayList<>();
	}
	
	public QuickCore(GameModel gm, RealizabilityChecker c) {
		this(gm, null, c, DEFAULT_MINIMIZER_TYPE);
	}
	
	public QuickCore(GameModel gm, RealizabilityCheckResult initialCheckResult, RealizabilityChecker c) {
		this(gm, initialCheckResult, c, DEFAULT_MINIMIZER_TYPE);
	}
	
	public QuickCore(GameModel gm, RealizabilityCheckResult initalCheckResult, RealizabilityChecker c, MinimizerType t) {
		this.gm = gm;
		checker = c;
		curChecker = checker;
		ti = new TraceIdentifier(gm);
		builder = new SysTraceInfoBuilder(gm);
		nonJust = new ArrayList<Integer>();
		justs = new ArrayList<Integer>();
		ini = new ArrayList<Integer>();
		newEnv = new ArrayList<Integer>();
		base = new ArrayList<Integer>();
		mtype = t;
		factory = new MinimizerFactory<Integer>();
		this.initialCheckResult = initalCheckResult;
	}

	/**
	 * Set given elements of the core
	 * 
	 * @param base
	 */
	public void setBase(List<Integer> base) {
		this.base = new ArrayList<Integer>(base);
	}

	/**
	 * Create a Stats object in order to collect statistics about the run
	 */
	public void computeStats() {
		stats = new Stats();		
	}
	
	public Stats getStats() {
		return stats;
	}

	/**
	 * Minimizes part while (disjoint) base is considered a part of the core
	 * If Stats object is set, computes statistics.
	 * 
	 * @param part
	 * @return
	 */
	public List<Integer> minimize(List<Integer> part) {
		if (stats!=null) {
			curChecker = new StatsChecker(checker);
			long startTime = System.currentTimeMillis();
			minimizeJustices(part);
			stats.justTime = System.currentTimeMillis() - startTime;
			stats.justChecks = checker.getChecks();
			stats.justActualChecks = checker.getActualChecks();
			stats.justCheckInfo = ((StatsChecker)curChecker).getCheckInformation();
			
			curChecker = new StatsChecker(checker);
			startTime = System.currentTimeMillis();
			minimizeSafe();
			stats.safeTime = System.currentTimeMillis() - startTime;
			stats.safeChecks += checker.getChecks() - stats.justChecks;
			stats.safeActualChecks += checker.getActualChecks() - stats.justActualChecks;
			stats.safeCheckInfo = ((StatsChecker)curChecker).getCheckInformation();
			
			startTime = System.currentTimeMillis();
			minimizeIni();
			stats.iniTime = System.currentTimeMillis() - startTime;
			curChecker = checker;
		} else {
			minimizeJustices(part);
			minimizeSafe();
			minimizeIni();
		}
		
		List<Integer> min = new ArrayList<Integer>(nonJust);
		min.addAll(justs);
		
		freeIncrementalInfo();
		
		builder.restore();
		
 		return min;
	}

	private void minimizeJustices(List<Integer> part) {
		checkCondition(!part.removeAll(base), "Base and part must be disjoint"); //verify that base and part are not intersected
		checkCondition(ti.getSysTraces().containsAll(part), "Part must be included in system"); // the part must be all sys traces
		List<Integer> allJusts = new ArrayList<Integer>();
		Integer guessedCoreJustice = -1;
		this.justs = new ArrayList<Integer>();

		for (Integer t : part) {
			(ti.getType(t)==TraceIdentifier.Type.JUST ? allJusts : nonJust).add(t);
		}
		
		// Initialize the incremental information
		if (INCREMENTAL_UNREAL_RESULTS) {
			updateIncrementalInfo(initialCheckResult, Integer.MAX_VALUE, true);
		}
		
		List<Integer> newSys = mergeLists(nonJust, base);
		
		// Early justice detection: 
		int lastUsedJusticeNum = (EARLY_JUTSICE_DETECTION && initialCheckResult != null && !allJusts.isEmpty()) ? initialCheckResult.lastUsedSysJusticeNum : -allJusts.size()-1;
		if (lastUsedJusticeNum >= 0) {
			// Optimization (1) - remove justices not used by the initial realizability check
			List<Integer> justBehaviors = new ArrayList<>(ti.getSysBehaviorInfo().stream().filter(b -> b.isJustice()).map(BehaviorInfo::getTraceId).toList());
			justBehaviors.subList(lastUsedJusticeNum + 1, justBehaviors.size()).clear();
			Integer lastUsedValue = justBehaviors.get(lastUsedJusticeNum);
			
			allJusts.clear();
			for (var i : justBehaviors) {
				if (!allJusts.contains(i) && !base.contains(i)) {
					allJusts.add(i);
				}
			}
			
			checker.register(true, mergeLists(newSys, allJusts));
			
			// Optimization (2) - the last used justice is guessed to be part of the core
			// The behavior is slightly different when there is only one justice - in which case it is more beneficial to do a realizability check without any justice, first. 
			if (allJusts.size() > 1) {
				guessedCoreJustice = lastUsedValue;
			}
		}
		
		boolean baseHasJusts = ti.countType(base, TraceIdentifier.Type.JUST) > 0;
		boolean specHasJusts = !allJusts.isEmpty();

		if (baseHasJusts || (specHasJusts && (guessedCoreJustice != -1 || !unrealizableEx(newSys, false)))) { // we need some of the justices for unrealizability, compute core of them alone
			newEnv = ti.getEnvTraces(); // env remains the same
			
			if (!baseHasJusts && specHasJusts && allJusts.size() == 1) {
				// Realizable without the justs - because there's a single justice, it is required!
				justs.addAll(allJusts);
			} else if (guessedCoreJustice != -1 && unrealizableEx(mergeLists(newSys, List.of(guessedCoreJustice)), false)) {
				// Heuristic - in many cases, the single guessed justice is enough for unrealizability and we can skip the ddmin
				// Nothing extra to do in this case
				if (!unrealizableEx(newSys, false)) {
					justs.add(guessedCoreJustice);
				}
			}
			else if (specHasJusts) {
				// The heuristics were not enough to find the correct justice,
				// so minimize the rest of the justice guarantees.
				List<Integer> baseForChecks = newSys;
				double initialCddProbability = AbstractCDD.INITIAL_PROBABILITY;
				if (mtype == MinimizerType.CDD) {		
					// Empirically selected to yield the best results
					AbstractCDD.INITIAL_PROBABILITY = 0.15;
				}
				if (INCREMENTAL_UNREAL_RESULTS) {					
					justs.addAll(minimizeIncrementalUnrealResultsInternal(allJusts, baseForChecks));
				} else {					
					Checker<Integer> justChecker = new Checker<Integer>() {
						@Override 
						public boolean check(List<Integer> part) {
							return unrealizableEx(mergeLists(baseForChecks, part), false);
						}
					};
					Minimizer<Integer> justFinder = factory.makeMinimizer(mtype, justChecker);
					justs.addAll(justFinder.minimize(allJusts));
				}
				
				AbstractCDD.INITIAL_PROBABILITY = initialCddProbability;
			}
		}
		
		newSys.addAll(justs);
		
		if (justs.isEmpty() && !baseHasJusts){
			// no need for any justices including env ones. Build a no just env
			EnvTraceInfoBuilder ebuilder = new EnvTraceInfoBuilder(gm);
			newEnv = new ArrayList<Integer>();
			
			for (Integer t : ebuilder.getTraceList()) {
				if (ti.getType(t)!=TraceIdentifier.Type.JUST) {
					newEnv.add(t);
				}
			}
			if (ebuilder.getTraceList().size() != newEnv.size()) { // if env is changed
				ebuilder.build(newEnv);
			}
		}
		builder.build(newSys);
	}

	/**
	 * Assert that the supplied {@code predicate} is {@code true}.
	 *
	 * @param predicate the predicate to check
	 * @param message precondition violation message
	 * @throws RuntimeException if the predicate is {@code false}
	 * @see org.junit.platform.commons.util.Preconditions.condition(predicate, message)
	 */
	private void checkCondition(boolean predicate, String message) {
		if (!predicate) {
			throw new RuntimeException(message);
		}
	}

	private void minimizeSafe() {
		List<Integer> nonIni = new ArrayList<Integer>();
		
		// take only "clean" ini (not complex)
		for (Integer t : nonJust) {
			(ti.getType(t)==TraceIdentifier.Type.SAFE || ti.getKind(t)==TraceIdentifier.Kind.COMPLEX ? nonIni : ini).add(t);
		}
		
		List<Integer> knownCoreGars = mergeLists(justs, base);
		
		List<Integer> nonCoreBase = new ArrayList<Integer>(ini);
		
		double initialCddProbability = AbstractCDD.INITIAL_PROBABILITY;
		if (mtype == MinimizerType.CDD && !nonIni.isEmpty()) {
			// Empirically selected
			AbstractCDD.INITIAL_PROBABILITY = (3.0 / nonIni.size());
		}
		
		if (INCREMENTAL_UNREAL_RESULTS){
			nonJust = new ArrayList<Integer>(minimizeSafesIncrementalUnrealResults(nonIni, knownCoreGars, nonCoreBase));
		} else {
			nonJust = new ArrayList<Integer>(minimizeSafesSimple(nonIni, knownCoreGars, nonCoreBase));
		}
		nonJust.addAll(ini);
		buildCurrSys();
		
		AbstractCDD.INITIAL_PROBABILITY = initialCddProbability;
	}
	
	private ArrayList<Integer> minimizeSafesSimple(List<Integer> nonIni, List<Integer> currentCore, List<Integer> nonCoreBase) {
		List<Integer> baseForChecks = new ArrayList<Integer>(currentCore);
		baseForChecks.addAll(nonCoreBase);
		
		Minimizer<Integer> finder = factory.makeMinimizer(mtype, new Checker<Integer>() {			
			@Override 
			public boolean check(List<Integer> part) {
				return unrealizable(mergeLists(baseForChecks, part));
			}
		});
		
		return new ArrayList<Integer>(finder.minimize(nonIni));
	}
	
	/**
	 * Minimize using incremental unreal results.
	 * This is done by a double-minimization process; First, minimize using
	 * EUN while also reusing partial winning states as the initial Y value;
	 * Second, LinearMin the result using a valid initial Y.
	 */
	private List<Integer> minimizeIncrementalUnrealResultsInternal(List<Integer> toMinimize, List<Integer> baseForChecks) {
		Checker<Integer> unsoundChecker = new Checker<Integer>() {
			@Override
			protected boolean check(List<Integer> part) {
				return unrealizableEx(mergeLists(baseForChecks, part), true);
			}
		};
		
		Minimizer<Integer> unsoundMinimizer = factory.makeMinimizer(mtype, unsoundChecker);
		
		List<Integer> unsoundResult = unsoundMinimizer.minimize(toMinimize);
		if (allUnrealComplete) {
			return unsoundResult;
		}
		
		List<Integer> unsoundCore = unsoundChecker.getSmallestPos();
		if (unsoundCore == null) {
			unsoundCore = toMinimize;
		}
		
		Checker<Integer> soundChecker = new Checker<Integer>() {
			@Override
			protected boolean check(List<Integer> part) {
				return unrealizableEx(mergeLists(baseForChecks, part), false);
			}
		};
		
		if (unsoundCore.size() >= 5) {
			// Another small heuristic - when the new "core" is large enough,
			// it becomes beneficial to compute the complete winning states and
			// use them for the LinearMin.
			GR1GameExperiments.STOP_WHEN_INITIALS_LOST = false;
			List<Integer> currentCore = mergeLists(baseForChecks, unsoundCore);
			
			checker.setChecksKeepWinningStates(true);
			checker.setInitialY(minCompleteUnrealBdd);
			
			boolean isUnreal = curChecker.wrappedCheck(currentCore, false, true);
			assert(isUnreal);
			
			checker.setInitialY(null);
			checker.setChecksKeepWinningStates(false);
			
			RealizabilityCheckResult unrealCheckResult = checker.getLastCheckResult();
			
			updateIncrementalInfo(unrealCheckResult, currentCore.size(), true);
			
			unrealCheckResult.free();
			
			GR1GameExperiments.STOP_WHEN_INITIALS_LOST = true;

		}
		
		// LinearMin the core over-approximation and find the actual core.
		Minimizer<Integer> soundMinimizer = factory.makeMinimizer(MinimizerType.LINEARMIN, soundChecker);
		return new ArrayList<>(soundMinimizer.minimize(unsoundCore));
	}
	
	/**
	 * Minimize the safeties using the incremental unrealizable winning states optimizations.
	 * The optimization first minimizes the the safeties by passing on the winning states of unrealizable sub-specifications
	 * as the Y BDD, and then minimizes again the result using linear min.
	 * The first minimization gives the correct result in most of the cases, but is unsound because of EUN.
	 * Since we assume the result is mostly-correct, we use linearmin instead of ddmin for the next minimization.
	 * 
	 * @param nonIni - The safeties to minimize
	 * @param currentCore - The currently known guarantees of the core - justices and base.
	 * @param nonCoreBase - "Constant" guarantees throughout the computation, that are not necessarily part of the core (i.e, inis).
	 */
	private List<Integer> minimizeSafesIncrementalUnrealResults(List<Integer> nonIni, List<Integer> currentCore, List<Integer> nonCoreBase) {
		// Special cases in which the heuristic was found to be ineffective
		if (ti.countType(currentCore, TraceIdentifier.Type.JUST) == 0 || ti.countType(newEnv, TraceIdentifier.Type.JUST) == 0) {
			return minimizeSafesSimple(nonIni, currentCore, nonCoreBase);
		}
				
		return minimizeIncrementalUnrealResultsInternal(nonIni, mergeLists(currentCore, nonCoreBase));
	}

	private void minimizeIni() {
		if (ini.isEmpty()) {
			return;
		}
		
		// If the specification without inis is unrealizable for the over-approximation,
		// we can skip the expensive, unoptimized, realizability check.
		if (INCREMENTAL_UNREAL_RESULTS && minUnrealBdd != null) {
			GR1Game game = new GR1Game(gm);
			nonJust.removeAll(ini);
			
			buildCurrSys();
			if (!game.sysWinAllInitial(minUnrealBdd)) {
				game.free();
				return;
			}
			
			nonJust.addAll(ini);
			buildCurrSys();
		}
		
		GR1GameExperiments game;
		if (checker.getGameType() == GameType.KIND_GR1_GAME) {
			game = new GR1SeparatedGameImplC(gm);
		} else {
			game = new GR1GameImplC(gm);
		}
		
		boolean recycle = GR1GameExperiments.USE_FIXPOINT_RECYCLE;
		boolean stop = GR1GameExperiments.STOP_WHEN_INITIALS_LOST;
		
		GR1GameExperiments.USE_FIXPOINT_RECYCLE = GR1GameExperiments.STOP_WHEN_INITIALS_LOST = false;
		
		boolean unreal = !game.checkRealizability();
		assert(unreal);
		for (Integer curr : new ArrayList<Integer>(ini)) {
			nonJust.remove(curr);
			buildCurrSys();
			if (game.sysWinAllInitial()) {
				nonJust.add(curr);
			}
		}
		// build again according to found
		buildCurrSys();
		GR1GameExperiments.USE_FIXPOINT_RECYCLE = recycle;
		GR1GameExperiments.STOP_WHEN_INITIALS_LOST = stop;
		game.free();
	}
	
	private void buildCurrSys() {
		List<Integer> newSys = mergeLists(nonJust, justs, base);
		builder.build(newSys);			
	}
	
	private boolean unrealizable(List<Integer> part) {
		return curChecker.wrappedCheck(part);
	}
	
	/**
	 * Check whether the specification that contains the given guarantees is unrealizable.
	 * If INCREMENTAL_UNREAL_RESULTS is enabled, the function also keeps track of the winning states of the smallest
	 * unrealizable specification encountered so far.
	 * 
	 * @param allowIncompleteY - Allow using previous incomplete winning states as the initial Y.
	 * 							 When true, the check results may be unsound - and return "true" for an
	 * 							 unrealizable specification (but "false" for a realizable one).
	 */
	private boolean unrealizableEx(List<Integer> toMinimize, boolean allowIncompleteY) {
		if (!INCREMENTAL_UNREAL_RESULTS) {
			return unrealizable(toMinimize);
		}
		
		// Choose the correct initial Y value - either null, an incomplete value if allowed or a complete value
		BDD initialY = null;
		if (toMinimize.size() >= minUnrealPartsSize) {
			initialY = null;
		}
		else if (allUnrealComplete || allowIncompleteY) {
			initialY = minUnrealBdd;
		} else if (!allowIncompleteY) {
			initialY = minCompleteUnrealBdd;
		}
		
		checker.setInitialY(initialY);
		checker.setChecksKeepWinningStates(true);
			
		boolean result;
		
		if (allowIncompleteY && !allUnrealComplete) {
			// Incomplete flow - don't store realizable results in the cache
			// When used inside DDMIN, DDMIN's internal cache will "catch" these cases
			// And prevent unnecessary checks.
			result = curChecker.wrappedCheck(toMinimize, true, false);
			if (result && checker.getLastCheckResult() != null) {
				checker.register(true, toMinimize);
			}

		} else {
			result = curChecker.wrappedCheck(toMinimize);
		}
		
		RealizabilityCheckResult unrealResult = checker.getLastCheckResult();
		updateIncrementalInfo(unrealResult, toMinimize.size(), !allowIncompleteY);
				
		checker.setChecksKeepWinningStates(false);
		checker.setInitialY(null);
		
		return result;
	}
	
	/**
	 * Update the incremental winning states information stored in minUnrealBdd, minCompleteUnrealBdd.
	 */
	private void updateIncrementalInfo(RealizabilityCheckResult checkResult, int partsSize, boolean overridePrevResult) {
		if (checkResult == null || checkResult.sysWins || checkResult.getSysWinningStates() == null) {
			return;
		}
				
		if (minUnrealBdd != null) {
			minUnrealBdd.free();
		}
		minUnrealBdd = checkResult.getSysWinningStates().id();
		minUnrealPartsSize = partsSize;
		minUnrealComplete = checkResult.isComplete;
		allUnrealComplete = overridePrevResult ? minUnrealComplete : (allUnrealComplete & minUnrealComplete);
		if (allUnrealComplete) {
			if (minCompleteUnrealBdd != null) {
				minCompleteUnrealBdd.free();
			}
			minCompleteUnrealBdd = minUnrealBdd.id();
		}
	}
	
	/**
	 * Free the incremental winning states information.
	 */
	private void freeIncrementalInfo() {
		if (minUnrealBdd != null) {
			minUnrealBdd.free();
		}
		minUnrealBdd = null;
		minUnrealPartsSize = Integer.MAX_VALUE;
		minUnrealComplete = true;
		allUnrealComplete = true;
		
		if (minCompleteUnrealBdd != null) {
			minCompleteUnrealBdd.free();
		}
		minCompleteUnrealBdd = null;
	}
	
	/**
	 * A small utility function to merge multiple lists into a single one.
	 */
	@SafeVarargs
	private static List<Integer> mergeLists(List<Integer>... lists) {
		return new ArrayList<Integer>(Arrays.stream(lists).flatMap(List::stream).toList());
	}
}
