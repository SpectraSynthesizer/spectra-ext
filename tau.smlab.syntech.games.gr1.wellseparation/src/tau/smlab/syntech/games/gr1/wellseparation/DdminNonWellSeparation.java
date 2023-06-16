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

package tau.smlab.syntech.games.gr1.wellseparation;

import java.util.ArrayList;
import java.util.List;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.checks.ddmin.AbstractDdmin;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.ModuleException;
import tau.smlab.syntech.gamemodel.PlayerModule;
import tau.smlab.syntech.games.gr1.GR1Game;
import tau.smlab.syntech.games.gr1.GR1GameBuilder;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker.Positions;
import tau.smlab.syntech.jtlv.Env;

/**
 * minimizes set of assumptions to keep environment non-well-separated
 * 
 * set of assumptions should not include auxiliaries already added on the system
 * side
 * 
 * @author ringert
 * 
 */
public class DdminNonWellSeparation extends AbstractDdmin<BehaviorInfo> {

	private Positions p;
	private BDD reach;
	private PlayerModule env;
	private PlayerModule sys;
	private GameModel model;

	/**
	 * Computes a minimal core that is non-well-separated
	 * 
	 * Computes a core according to the definition where Postitions REACH is
	 * evaluated against reachable states of the original specification (makes core
	 * computation monotonic).
	 * 
	 * @param cu
	 * @param doms
	 * @param p
	 */
	public DdminNonWellSeparation(GameModel model, Positions p) {
		this.p = p;
		this.model = model;
		env = model.getEnv();
		sys = model.getSys();
		if (Positions.REACH.equals(p)) {
			GR1GameBuilder.stopWhenInitialsLost(false);
			BDD trans = env.trans().and(sys.trans());
			BDD ini = env.initial().and(sys.initial());
			this.reach = Env.allSucc(ini, trans);
			trans.free();
		}
	}

	/**
	 * check for non-well-separation
	 * 
	 * @return true if env is non-well-separated
	 */
	@Override
	protected boolean check(List<BehaviorInfo> part) {
		try {
			buildEnv(part, env);
		} catch (ModuleException e) {
			throw new RuntimeException(e);
		}

		boolean nonWellSeparated = false;
		GR1Game rg = GR1GameBuilder.getDefault(model);
		rg.checkRealizability();

		if (Positions.ALL.equals(p)) {
			nonWellSeparated = rg.sysWinAllInitial();
		} else if (Positions.REACH.equals(p)) {
			nonWellSeparated = !rg.sysWinningStates().and(reach).isZero();
		}

		rg.free();

		return nonWellSeparated;
	}

	/**
	 * resets and then adds assumptions to env module
	 * 
	 * @param part
	 * @param env
	 * @throws ModuleException
	 */
	public void buildEnv(List<BehaviorInfo> part, PlayerModule env) throws ModuleException {
		env.reset();

		ArrayList<BehaviorInfo> asms = new ArrayList<BehaviorInfo>();
		asms.addAll(part);

		for (BehaviorInfo asm : asms) {
			if (asm.isInitial()) {
				env.conjunctInitial(asm.initial.id());
			}
			if (asm.isSafety()) {
				env.conjunctTrans(asm.safety.id());
			}
			if (asm.isJustice()) {
				env.addJustice(asm.justice.id());
			}
		}

		if (env.justiceNum() == 0) {
			env.addJustice(Env.TRUE());
		}
	}

}
