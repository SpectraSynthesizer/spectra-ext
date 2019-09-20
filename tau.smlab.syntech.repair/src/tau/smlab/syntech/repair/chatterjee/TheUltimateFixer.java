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

package tau.smlab.syntech.repair.chatterjee;

import java.util.ArrayList;
import java.util.List;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.checks.CouldAsmHelp;
import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.PlayerModule;
import tau.smlab.syntech.games.gr1.GR1Game;
import tau.smlab.syntech.games.gr1.GR1GameMemoryless;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker.EnvSpecPart;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker.Positions;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker.Systems;
import tau.smlab.syntech.repair.AbstractRepair;
import tau.smlab.syntech.repair.BasicAssumption;
import tau.smlab.syntech.repair.RepairCore;

/**
 * Implementation of GLASS algorithm (Algorithm 3 in the symbolic repair paper).
 * 
 * @author ringert
 *
 */

public class TheUltimateFixer extends AbstractRepair {

  private static boolean isRealizable = false;

  private long time = 0;
  private boolean quantifyAux = false;
  private boolean core = false;

  private boolean isFixable = true;

  public TheUltimateFixer(GameInput gameInput, boolean quantifyAux, boolean core) {
    this.quantifyAux = quantifyAux;
    this.core = core;
    
    gi = gameInput;
  }

  private TheUltimateFixer() {
    // only needed for static calls to fix(GameModel gm)
  }

  public long getRuntime() {
    return time;
  }

  public long getTimeToFirstRepair() {
    return results.isEmpty() ? -1 : time; // -1 means N/A
  }

  public long timeToWellSep() {
    return (results.isEmpty() || !isWellSep) ? -1 : time; // -1 means N/A
  }

  public int numberOfWellSeparated() {
    boolean wellSep = false;

    if (numberOfRepairsFound() == 1) {
      WellSeparationChecker ch = new WellSeparationChecker();
      try {
        wellSep = ch.checkEnvWellSeparated(gm, Systems.SPEC, EnvSpecPart.JUSTICE, Positions.REACH, false);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return wellSep ? 1 : 0;
  }

  public void execute() {
    results = new ArrayList<List<BasicAssumption>>();
    
    computeWellSep();

    isRealizable = realizable();

    isFixable = CouldAsmHelp.couldAsmHelp(gm);

    long startTime = System.currentTimeMillis();
    List<BehaviorInfo> result = fix();    
    time = System.currentTimeMillis() - startTime;

    if (result != null) {
      List<BasicAssumption> ret = new ArrayList<BasicAssumption>();

      for (BehaviorInfo b : result) {
        ret.add(new BasicAssumption(b));
      }
      if (core) {
    	  //use a clean copy of the model for core computation
    	  computeGameModel();
    	  RepairCore rc = new RepairCore(gm);
    	  ret = rc.minimize(ret);
      }

  	  recordRepair(ret, time, -1); //success

    }
  }

  public boolean isRealizable() {
    return isRealizable;
  }

  public static List<BehaviorInfo> fix(GameModel gameModel, boolean quantifyAux) {
    TheUltimateFixer f = new TheUltimateFixer();
    TheUltimateFixer.gm = gameModel;
    f.quantifyAux = quantifyAux;
    TheUltimateFixer.isRealizable = f.realizable();
    f.isFixable = CouldAsmHelp.couldAsmHelp(gm);
    return f.fix();
  }

  private List<BehaviorInfo> fix() {
    List<BehaviorInfo> fixes = new ArrayList<BehaviorInfo>();

    if (isRealizable) {
      return fixes;
    }

    if (!isFixable) {
      return null;
    }

    PlayerModule env = gm.getEnv();
    PlayerModule sys = gm.getSys();
    BDD doms = env.getDoms().and(sys.getDoms());
    
    BehaviorInfo safety = new BehaviorInfo();
    safety.safety = NonRestrictiveSafety.weakestNonRestrictiveSafety(gm, quantifyAux);
    // only add if it is not some form of "TRUE"
    if (!doms.impWith(safety.safety.id()).isOne()) {
      fixes.add(safety);      
    }
    doms.free();
    
    env.conjunctTrans(safety.safety.id());

    // check sat
    if (!CouldAsmHelp.couldAsmHelp(gm)) {
      return null;
    }
    // check realizability
    if (realizable()) {
      return fixes;
    }

    for (BDD j : BottomJustices.bottomJusticeAsm(gm, quantifyAux)) {
      BehaviorInfo justice = new BehaviorInfo();
      justice.justice = j;
      fixes.add(justice);
      env.addJustice(j);
    }

    // check sat
    if (!CouldAsmHelp.couldAsmHelp(gm)) {
      return null;
    }
    // check realizability
    if (realizable()) {
      return fixes;
    }

    BehaviorInfo ini = new BehaviorInfo();
    ini.initial = WeakestInitial.weakestIni(gm, quantifyAux);
    fixes.add(ini);

    env.conjunctInitial(ini.initial.id());

    // check sat
    if (!CouldAsmHelp.couldAsmHelp(gm)) {
      return null;
    }
    // check realizability
    if (realizable()) {
      return fixes;
    }

    return null;
  }

  /**
   * check realizability of a game model
   * 
   * @param m
   * @return
   */
  private boolean realizable() {
    GR1Game rg = new GR1GameMemoryless(gm);
    boolean isRealizable = rg.checkRealizability();
    rg.free();
    return isRealizable;
  }
}
