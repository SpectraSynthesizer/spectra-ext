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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDVarSet;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.ModuleException;
import tau.smlab.syntech.gamemodel.PlayerModule;
import tau.smlab.syntech.games.AbstractGamesException;
import tau.smlab.syntech.games.gr1.GR1Game;
import tau.smlab.syntech.games.gr1.GR1Memory;
import tau.smlab.syntech.jtlv.CoreUtil;
import tau.smlab.syntech.jtlv.Env;

/**
 * checks well-separation of the environment as suggested by Klein and Pnueli
 * 
 * @author ringert
 * 
 */
public class WellSeparationChecker {

  private PlayerModule sys;
  private PlayerModule env;

  /**
   * reachable states of env
   */
  private BDD reach;

  /**
   * a core set of assumptions that make env non-well-separated
   */
  private List<BehaviorInfo> core;

  public enum Positions {
    /**
     * system can force violation all initial choices of the environment
     */
    ALL,
    /**
     * system can force violation from some states the environment can reach
     */
    REACH
  }

  public enum Systems {
    /**
     * Checks well-separation for system TRUE & G TRUE & GF FALSE but with safeties from pre-transformations
     */
    NONE,
    /**
     * Checks well-separation for systems satisfying the safeties in the given system spec
     */
    SPEC,
    /**
     * Checks whether a controller exists that does not violate the assumptions FIXME still don't know how to do that
     */
    CONTROLLER
  }

  public enum EnvSpecPart {
    /**
     * initial part of environment spec can be violated
     */
    INI,
    /**
     * safety part of environment spec can be violated (including possibly initial)
     */
    SAFETY,
    /**
     * liveness part of environment spec can be violated
     */
    JUSTICE
  }

  public boolean checkEnvWellSeparated(GameModel model, Systems s, EnvSpecPart e, Positions p,
      boolean minimize)
      throws ModuleException, AbstractGamesException {

    env = model.getEnv();
    sys = model.getSys();

    restrictEnv(e);

    restrictSys(model, s);

    boolean wellSep = false;

    GR1Game rg = new GR1Game(model);
    rg.checkRealizability();

    if (Positions.ALL.equals(p)) {
      wellSep = !rg.sysWinAllInitial();
    } else if (Positions.REACH.equals(p)) {
      BDD trans = env.trans().and(sys.trans());
      BDD ini = env.initial().and(sys.initial());
      reach = Env.allSucc(ini, trans);
      trans.free();
      wellSep = rg.getMem().getWin().and(reach).isZero();
      reach.free();
    }
    rg.free();

    if (!wellSep && minimize) {
      ArrayList<BehaviorInfo> asms = new ArrayList<>();
      for (BehaviorInfo asm : model.getEnvBehaviorInfo()) {
        if (!(asm.isSafety() && asm.safety.isOne())) {
          asms.add(asm);
        }
      }

      DdminNonWellSeparation ddmin = new DdminNonWellSeparation(model, p);
      core = ddmin.minimize(asms);

      // make sure last game was this
      ddmin.buildEnv(core, env);
      rg = new GR1Game(model);
      if (rg.checkRealizability()) {
        System.out.println("Minimal core found.");
      }

      System.out.println("From " + asms.size() + " to " + core.size());
      for (BehaviorInfo asm : core) {
        System.out.println(asm);
      }
    }

    return wellSep;
  }

  private void restrictSys(GameModel model, Systems s) {
    List<BehaviorInfo> auxSpecs = model.getAuxBehaviorInfo();

    switch (s) {
    case NONE:
      // create spec TRUE & G TRUE & GF FALSE
      sys.resetInitial();
      sys.resetTrans();
      // add auxiliary specs from PastLTL and response translations 
      for (BehaviorInfo spec : auxSpecs) {
        if (spec.isInitial()) {
          sys.conjunctInitial(spec.initial.id());
        }
        if (spec.isSafety()) {
          sys.conjunctTrans(spec.safety.id());
        }
      }
      sys.resetJustice();
      sys.addJustice(Env.FALSE());
      break;
    case SPEC:
      // keep all safeties but replace justice by FALSE
      sys.resetJustice();
      sys.addJustice(Env.FALSE());
      break;
    case CONTROLLER:
      // no modification here
      break;
    default:
      break;
    }
  }

  private void restrictEnv(EnvSpecPart e) {
    switch (e) {
    case INI:
      // env spec: ini & G TRUE & GF TRUE
      env.resetTrans();
      env.resetJustice();
      env.addJustice(Env.TRUE());
      break;
    case SAFETY:
      // env spec: ini & G safety & GF TRUE
      env.resetJustice();
      env.addJustice(Env.TRUE());
      break;
    case JUSTICE:
      // keep spec as is
      break;
    default:
      break;
    }
  }

  public List<BehaviorInfo> computeCore(GameModel model) throws Exception {
    return computeCore(model, Systems.NONE);
  }

  public List<BehaviorInfo> computeCore(GameModel model, Systems s) throws Exception {
	return computeCore(model, s, Positions.REACH);
  }

/**
   * computes a core for ENV_JUSTICE, P_REACH, S_NONE
   * 
   * @param cu
   * @return
   * @throws Exception
   */
  public List<BehaviorInfo> computeCore(GameModel model, Systems s, Positions p) throws Exception {

    env = model.getEnv();
    sys = model.getSys();

    restrictSys(model, s);

    ArrayList<BehaviorInfo> asms = new ArrayList<>();
    for (BehaviorInfo asm : model.getEnvBehaviorInfo()) {
      if (!(asm.isSafety() && asm.safety.isOne())) {
        asms.add(asm);
      }
    }

    DdminNonWellSeparation ddmin = new DdminNonWellSeparation(model, p);
    core = ddmin.minimize(asms);

    return core;
  }

  private Map<String, Set<Integer>> witnessInfo;

  /**
   * checks whether diagnose computed witness info
   * 
   * @return
   */
  public boolean hasWitnessInfo() {
    return witnessInfo != null && !witnessInfo.isEmpty();
  }

  /**
   * returns witness info possibly computed by diagnose
   * 
   * @return
   */
  public Map<String, Set<Integer>> getWitnessInfo() {
    return witnessInfo;
  }

  /**
   * diagnoses cases of non-well separation consisting of Position and EnvSpecPart
   * 
   * WARNING: messes up env and sys
   * 
   * @param cu
   * @return
   * @throws ModuleException
   * @throws AbstractGamesException
   */
  public List<String> diagnose(GameModel model, Systems sysPart) throws ModuleException, AbstractGamesException {

    env = model.getEnv();
    sys = model.getSys();

    List<String> res = new ArrayList<>();
    witnessInfo = new HashMap<String, Set<Integer>>();

    //////////////////////
    // Part E-ini
    //////////////////////
    if (env.initial().isZero()) {
      res.add(Positions.ALL + ", " + EnvSpecPart.INI);
      return res;
    }

    //////////////////////
    // Part E-safe
    //////////////////////

    // system (T+aux, T+aux, {F})
    restrictSys(model, sysPart);

    BDD reach = Env.allSucc(env.initial().and(sys.initial()), env.trans().and(sys.trans()));

    // environment with no justices
    List<BDD> envJ = new ArrayList<>();
    int j = env.justiceNum();
    for (int i = 0; i < j; i++) {
      // keep copies because we will reset
      envJ.add(env.justiceAt(i).id());
    }
    env.resetJustice();
    env.addJustice(Env.TRUE());

    GR1Game rg = new GR1Game(model);
    rg.checkRealizability();
    BDD wins = rg.sysWinningStates().id();
    BDD witness = wins.and(reach);
    if (!witness.isZero()) {
      wins.free();
      if (rg.sysWinAllInitial()) {
        res.add(Positions.ALL + ", " + EnvSpecPart.SAFETY);
        return res;
      } else {
        res.add(Positions.REACH + ", " + EnvSpecPart.SAFETY);
        // check easy case where state violates a SAFETY
        for (BehaviorInfo bi : model.getEnvBehaviorInfo()) {
          if (bi.isSafety()) {
            BDD statesWithSucc = witness.and(bi.safety).exist(env.modulePrimeVars());
            BDD test = witness.and(statesWithSucc.not());
            statesWithSucc.free();
            if (!test.isZero()) {
              // reduce to relevant variables
              BDDVarSet relevantUnPVars = bi.safety.support().minus(env.modulePrimeVars());
              BDD smallWitness = CoreUtil.satOne(test, relevantUnPVars);
              relevantUnPVars.free();
              
              Set<Integer> tids = new HashSet<>();
              tids.add(bi.traceId);
              witnessInfo.put(smallWitness.toStringWithDomains(Env.stringer), tids);
              
              smallWitness.free();
              test.free();
              break;
            }
            test.free();
          }
        }

        // TODO do more by computing safety core
      }
    }
    rg.free();

    //////////////////////
    // Part E-just
    //////////////////////

    env.resetJustice();
    for (BDD just : envJ) {
      // now adding back originals
      env.addJustice(just);
    }

    rg = new GR1Game(model);
    rg.checkRealizability();
    BDD win = rg.sysWinningStates().id();
    if (!win.and(reach).isZero()) {
      win.free();
      reach.free();
      if (rg.sysWinAllInitial()) {
        res.add(Positions.ALL + ", " + EnvSpecPart.JUSTICE);
      } else {
        res.add(Positions.REACH + ", " + EnvSpecPart.JUSTICE);
      }
    }
    rg.free();

    return res;
  }

  /**
   * diagnoses cases of non-well separation consisting of Position and EnvSpecPart
   * 
   * WARNING: messes up env and sys
   * 
   * @param model
   * @return
   * @throws ModuleException
   * @throws AbstractGamesException
   */
  public List<String> diagnose(GameModel model) throws ModuleException, AbstractGamesException {
    return diagnose(model, Systems.NONE);
  }

  /**
   * returns reachable states as computed by <code>checkEnvWellSeparated</code> when invoked with Positions.REACH
   * 
   * @return
   */
  public BDD getReach() {
    return reach;
  }

  public GR1Memory computeNonWellSepGame(GameModel model, Systems sysPart) {

    env = model.getEnv();
    sys = model.getSys();

    //////////////////////
    // Part E-ini
    //////////////////////
    if (env.initial().isZero()) {
      GR1Memory res = new GR1Memory();
      res.setComplete(true);
      res.setWin(Env.TRUE());
      return res;
    }

    //////////////////////
    // Part E-safe
    //////////////////////

    // system (T+aux, T+aux, {F})
    restrictSys(model, sysPart);

    BDD reach = Env.allSucc(env.initial().and(sys.initial()), env.trans().and(sys.trans()));
    
    if (reach.isZero()) {
      // some player deadlocks in initial state
      if (env.initial().isZero()) {
        // environment has no initial states
        GR1Memory mem = new GR1Memory();
        mem.setComplete(true);
        mem.setWin(Env.TRUE());
        return mem; 
      } else {
        // system deadlocks on all env.ini() states
        return null;
      }
    }

    // environment with no justices
    List<BDD> envJ = new ArrayList<>();
    int j = env.justiceNum();
    for (int i = 0; i < j; i++) {
      // keep copies because we will reset
      envJ.add(env.justiceAt(i).id());
    }
    env.resetJustice();
    env.addJustice(Env.TRUE());

    GR1Game rg = new GR1Game(model);
    rg.checkRealizability();
    BDD wins = rg.sysWinningStates().id();
    BDD reachAndNonWellSep = wins.and(reach);
    if (!reachAndNonWellSep.isZero()) {
      wins.free();
      if (rg.sysWinAllInitial()) {
        return rg.getMem();
      } else {
        // restrict game to go from reachable winning states only
        model.getSys().resetInitial();
        model.getSys().conjunctInitial(reachAndNonWellSep.id());
        model.getEnv().resetInitial();
        model.getEnv().conjunctInitial(reachAndNonWellSep.exist(sys.modulePrimeVars()));
        // FIXME probably this can already lead to a deadlock
        return rg.getMem();
      }
    }
    rg.free();

    //////////////////////
    // Part E-just
    //////////////////////

    env.resetJustice();
    for (BDD just : envJ) {
      // now adding back originals
      env.addJustice(just);
    }

    rg = new GR1Game(model);
    rg.checkRealizability();
    BDD win = rg.sysWinningStates().id();
    reachAndNonWellSep = win.and(reach);
    if (!reachAndNonWellSep.isZero()) {
      win.free();
      reach.free();
      if (rg.sysWinAllInitial()) {
        return rg.getMem();
      } else {
        // restrict game to go from reachable winning states only
        model.getSys().resetInitial();
        model.getSys().conjunctInitial(reachAndNonWellSep.id());
        model.getEnv().resetInitial();
        model.getEnv().conjunctInitial(reachAndNonWellSep.exist(sys.modulePrimeVars()));
        // FIXME probably this can already lead to a deadlock
        return rg.getMem();
      }
    }
    rg.free();

    return null;
  }

}
