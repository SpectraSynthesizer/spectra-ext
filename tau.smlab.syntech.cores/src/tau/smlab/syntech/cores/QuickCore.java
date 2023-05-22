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
import java.util.List;

import tau.smlab.syntech.cores.domainagnostic.MinimizerFactory;
import tau.smlab.syntech.cores.domainagnostic.MinimizerType;
import tau.smlab.syntech.cores.interfaces.Minimizer;
import tau.smlab.syntech.cores.util.Checker;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.util.EnvTraceInfoBuilder;
import tau.smlab.syntech.gamemodel.util.SysTraceInfoBuilder;
import tau.smlab.syntech.gamemodel.util.TraceIdentifier;
import tau.smlab.syntech.games.gr1.GR1GameExperiments;
import tau.smlab.syntech.games.gr1.GR1GameImplC;

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
	
	private GameModel gm = null;
	private Checker<Integer> checker = null;
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
	
	public class Stats {
		public long iniTime;
		public long safeTime;
		public long justTime;
		public int justChecks;	
		public int justActualChecks;	
	}
	
	public QuickCore(GameModel gm, Checker<Integer> c) {
		this(gm, c, MinimizerType.DDMIN);
	}
	
	public QuickCore(GameModel gm, Checker<Integer> c, MinimizerType t) {
		this.gm = gm;
		checker = c;
		ti = new TraceIdentifier(gm);
		builder = new SysTraceInfoBuilder(gm);
		nonJust = new ArrayList<Integer>();
		justs = new ArrayList<Integer>();
		ini = new ArrayList<Integer>();
		newEnv = new ArrayList<Integer>();
		base = new ArrayList<Integer>();
		mtype = t;
		factory = new MinimizerFactory<Integer>();
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
			long startTime = System.currentTimeMillis();
			minimizeJustices(part);
			stats.justTime = System.currentTimeMillis() - startTime;
			stats.justChecks = checker.getChecks();
			stats.justActualChecks = checker.getActualChecks();
			startTime = System.currentTimeMillis();
			minimizeSafe();
			stats.safeTime = System.currentTimeMillis() - startTime;
			startTime = System.currentTimeMillis();
			minimizeIni();
			stats.iniTime = System.currentTimeMillis() - startTime;
		} else {
			minimizeJustices(part);
			minimizeSafe();
			minimizeIni();
		}
		
		List<Integer> min = new ArrayList<Integer>(nonJust);
		min.addAll(justs);
 		return min;
	}

	private void minimizeJustices(List<Integer> part) {
		checkCondition(!part.removeAll(base), "Base and part must be disjoint"); //verify that base and part are not intersected
		checkCondition(ti.getSysTraces().containsAll(part), "Part must be included in system"); // the part must be all sys traces
		List<Integer> allJusts = new ArrayList<Integer>();	

		for (Integer t : part) {
			(ti.getType(t)==TraceIdentifier.Type.JUST ? allJusts : nonJust).add(t);
		}
		
		List<Integer> newSys = new ArrayList<Integer>(nonJust);
		newSys.addAll(base);

		if (ti.countType(base, TraceIdentifier.Type.JUST)>0 || !unrealizable(newSys)) { // we need some of the justices for unrealizability, compute core of them alone
			newEnv = ti.getEnvTraces(); // env remains the same
			Minimizer<Integer> justFinder = factory.makeMinimizer(mtype, new Checker<Integer>() {
				@Override 
				public boolean check(List<Integer> part) {
					List<Integer> checked = new ArrayList<Integer>(nonJust);
					checked.addAll(base);
					checked.addAll(part);
					return unrealizable(checked);
				}
			});
			
			justs.addAll(justFinder.minimize(allJusts));
			newSys.addAll(justs);
		} else { // no need for any justices including env ones. Build a no just env
				 // we already have a built system with no justices if we got here
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
		Minimizer<Integer> finder = factory.makeMinimizer(mtype, new Checker<Integer>() {
			@Override 
			public boolean check(List<Integer> part) {
				List<Integer> partAndJusts = new ArrayList<Integer>(justs);
				partAndJusts.addAll(part);
				partAndJusts.addAll(base);
				partAndJusts.addAll(ini);
				return unrealizable(partAndJusts);
			}					
		});
		nonJust = new ArrayList<Integer>(finder.minimize(nonIni));
		nonJust.addAll(ini);
		buildCurrSys();	
	}

	private void minimizeIni() {
		boolean recycle = GR1GameExperiments.USE_FIXPOINT_RECYCLE;
		boolean stop = GR1GameExperiments.STOP_WHEN_INITIALS_LOST;
		
		GR1GameExperiments.USE_FIXPOINT_RECYCLE = GR1GameExperiments.STOP_WHEN_INITIALS_LOST = false;
		GR1GameExperiments game = new GR1GameImplC(gm);
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
		List<Integer> newSys = new ArrayList<Integer>(nonJust);
		newSys.addAll(justs);
		newSys.addAll(base);
		builder.build(newSys);			
	}
	
	private boolean unrealizable(List<Integer> part) {
		return checker.wrappedCheck(part);
	}
}
