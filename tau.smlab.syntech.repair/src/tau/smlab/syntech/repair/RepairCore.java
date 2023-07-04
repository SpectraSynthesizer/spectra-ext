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

package tau.smlab.syntech.repair;

import java.util.ArrayList;
import java.util.List;

import tau.smlab.syntech.checks.ddmin.AbstractDdmin;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.ModuleException;
import tau.smlab.syntech.gamemodel.PlayerModule;
import tau.smlab.syntech.games.gr1.GR1Game;
import tau.smlab.syntech.games.gr1.GR1GameMemoryless;

/**
 * minimizes set of assumptions that constitute a repair
 * the minimization is done over BasicAssumption and not BehaviorInfo for ease of use
 * 
 * @author shalom
 * 
 */

public class RepairCore extends AbstractDdmin<BasicAssumption> {
  protected GameModel model;
  protected PlayerModule env;


  public RepairCore(GameModel model) {
    this.model = model;
    this.env = model.getEnv();
  }

  /**
   * check for realizability
   * 
   * @return true if env is realizable
   */
  protected boolean check(List<BasicAssumption> part) {

    try {
      buildEnv(part);
    } catch (ModuleException e) {
      throw new RuntimeException(e);
    }

    boolean realizable;
    GR1Game g = new GR1GameMemoryless(model);
    realizable = g.checkRealizability();
    g.free();

    return realizable;
  }

  /**
   * resets and then adds assumptions to env module on top of existing ones
   * 
   * @param part
   * @throws ModuleException
   */
  public void buildEnv(List<BasicAssumption> part) throws ModuleException {
    env.reset();

    ArrayList<BehaviorInfo> asms = new ArrayList<BehaviorInfo>();
    asms.addAll(model.getEnvBehaviorInfo()); // take all the model env as basis
    for (BasicAssumption b : part) {
     	asms.add(b.translateToBehavior());
    }
    
    for (BehaviorInfo a : asms) {
      if (a.isInitial()) {
        env.conjunctInitial(a.initial.id());
      }
      if (a.isSafety()) {
        env.conjunctTrans(a.safety.id());
      }
      if (a.isJustice()) {
        env.addJustice(a.justice.id(), a.traceId);
      }
    }
  }

}
