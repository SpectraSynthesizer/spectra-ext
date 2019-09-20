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

package tau.smlab.syntech.vacuity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.bddgenerator.BDDGenerator;
import tau.smlab.syntech.checks.BDDBuilder;
import tau.smlab.syntech.checks.GR1Implication;
import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.jtlv.Env;

/**
 * Base class for vacuity checks. 
 * @author shalom
 *
 */

public class Vacuity {
	public static BehaviorInfo bot = null; 
	protected static GameModel gm = null;
	private static boolean core;
	protected static boolean first;
	protected static long timeToAll;
	protected static long timeToFirst;
	
	public Vacuity() {
	}

	public void init(GameInput gi, VacuityType t, boolean withCore, boolean toFirst) {
		core = withCore;
		first = toFirst;
		gm = BDDGenerator.generateGameModel(gi, t.getTraceInfo());
		// no need for player modules
		gm.getSys().free(); 
		gm.getEnv().free();
		bot = new BehaviorInfo(Env.FALSE(), null, null, null, -1, false);
	}
	
	public void free() {
		gm.free();
		gm = null;
		bot.free();
		bot = null;
	}
	
	public static GameModel getModel() {
		return gm;
	}
	
	protected static List<Integer> coreOfBehavior(List<BehaviorInfo> rel, BehaviorInfo b) {
		List<BehaviorInfo> bArr = new ArrayList<BehaviorInfo>();
		bArr.add(b);
		return coreOfBehavior(rel, bArr);
	}

	protected static List<Integer> coreOfBehavior(List<BehaviorInfo> rel, List<BehaviorInfo> b) {
		List<Integer> impCore = null;
		if (core && !isTrivial(gm, b.get(0))) {
			Map<Integer, List<BehaviorInfo>> bundled = bundle(rel);
			ImplicationCore coreFinder = new ImplicationCore(bundled, b);
			impCore = coreFinder.minimize(new ArrayList<Integer>(bundled.keySet()));
		} else {
			impCore = new ArrayList<Integer>();
		}
		return impCore;
	}
	
	public static Map<Integer, List<BehaviorInfo>> bundle(List<BehaviorInfo> behaviors) {
		Map<Integer, List<BehaviorInfo>> bun = new HashMap<Integer, List<BehaviorInfo>>();
		
		for (BehaviorInfo b : behaviors) {
			Integer t = b.traceId;

			if (!bun.containsKey(t)) {
				bun.put(t, new ArrayList<BehaviorInfo>());
			}
			List<BehaviorInfo> currList = bun.get(t);
			currList.add(b);
		}
		return bun;
	}
	
	private static List<BehaviorInfo> getRelevantAux(BehaviorInfo b) {
		List<BehaviorInfo> inAux = new ArrayList<BehaviorInfo>();

		for (BehaviorInfo bi : gm.getAuxBehaviorInfo()) {
			if (bi.traceId == b.traceId) {
				inAux.add(bi);
			}
		}
		
		return inAux;
	}
	
	/**
	 * The implication is dependent on behaviors. Only justices are full GR(1) implication from all elements
	 * The rest are propositional implication from their type
	 * 
	 * @param implying left hand of implication
	 * @param implied  right hand of implication
	 * @return
	 */
	
	protected static boolean vacuityImplication(List<BehaviorInfo> implying, BehaviorInfo implied) {
		boolean result = false;
		if (implied.isInitial() && implied!=bot) {
			BDD ini = BDDBuilder.getIni(implying);
			ini.impWith(implied.initial.id());
			result = ini.isOne();
			ini.free();
		} else if (implied.isSafety()) {
			BDD safe = BDDBuilder.getTrans(implying);
			safe.impWith(implied.safety.id());
			result = safe.isOne();
			safe.free();
		} else {
			result = GR1Implication.imply(implying, implied);
		}
		return result;
	}
	
	public static long getTimeToFirst() {
		return timeToFirst;
	}
	
	public static long getTimeToAll() {
		return timeToAll;
	}
	
	/**
	 * Get only ini or safe behaviors
	 * @param from
	 * @param ini true=only ini, false=only safe
	 */
	protected static List<BehaviorInfo> filter(List<BehaviorInfo> from, boolean ini) {
		List<BehaviorInfo> haveRel = new ArrayList<BehaviorInfo>();
		for (BehaviorInfo bi : from) {
			if (bi.isInitial() && ini) {
				haveRel.add(bi);
			}
			if (bi.isSafety() && !ini) {
				haveRel.add(bi);
			}
		}
		return haveRel;
	}
	
	public static List<BehaviorInfo> getEnvPremiseSet(BehaviorInfo b) {
		List<BehaviorInfo> result = new ArrayList<BehaviorInfo>();
		if (b.isInitial() && b!=bot) {
			result.addAll(filter(gm.getEnvBehaviorInfo(), true));
		} else if (b.isSafety()) {
			result.addAll(filter(gm.getEnvBehaviorInfo(), false));
		} else {
			result.addAll(filter(gm.getEnvBehaviorInfo(), true));
			result.addAll(filter(gm.getSysBehaviorInfo(), true));
			result.addAll(filter(gm.getEnvBehaviorInfo(), false));
			result.addAll(filter(gm.getSysBehaviorInfo(), false));
			for (BehaviorInfo justEnv : gm.getEnvBehaviorInfo()) {
				if (justEnv.isJustice()) {
					result.add(justEnv);
					for (BehaviorInfo aux : gm.getAuxBehaviorInfo()) {
						if (aux.traceId == justEnv.traceId) {
							result.add(aux);
						}
					}
				}
			}
		}
		return result;
	}
	
	public static List<BehaviorInfo> getSysPremiseSet(BehaviorInfo b) {
		List<BehaviorInfo> result = new ArrayList<BehaviorInfo>();
		if (b.isInitial() && b!=bot) {
			result.addAll(filter(gm.getEnvBehaviorInfo(), true));
			result.addAll(filter(gm.getSysBehaviorInfo(), true));
		} else if (b.isSafety()) {
			result.addAll(filter(gm.getEnvBehaviorInfo(), false));
			result.addAll(filter(gm.getSysBehaviorInfo(), false));
		} else {
			result.addAll(gm.getSysBehaviorInfo());
			result.addAll(gm.getEnvBehaviorInfo());
			result.addAll(gm.getAuxBehaviorInfo());
		}
		return result;
	}
	
	public static boolean isTrivial(GameModel model, BehaviorInfo b) {
		
		return (b.isInitial() && b.initial.isOne() ||
				b.isSafety() && b.safety.isOne() ||
				b.isJustice() && (b.justice.isOne() || GR1Implication.imply(getRelevantAux(b), b)));

	}
}
