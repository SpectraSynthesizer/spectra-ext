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

package tau.smlab.syntech.counterstrategy.model;

import java.util.Vector;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.counterstrategy.model.JusticeViolationGraph.AssumptionSatisfactionGraph;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.spectragameinput.translator.Tracer;

/**
 * A node in the JVTS.
 */
public class JVGNode extends JVGCommonNode {
	JVGNodeType type;
	int zRankNum;
	boolean isSafeyViolated;
	boolean isOnlySafetyViolated;
	int violatedJusticeNum;
	Vector<BehaviorInfo> violatedSafeties;
	// the environment choices for concrete states inside this JVTS node
	BDD transitions;
	// only relevant for cycle nodes
	AssumptionSatisfactionGraph assumpGraph;
	
	public JVGNode() {
		this.type = JVGNodeType.UNKNOWN_NODE;
		this.zRankNum = -1;
		this.isSafeyViolated = false;
		this.violatedJusticeNum = -1;
		this.violatedSafeties = new Vector<BehaviorInfo>();
		this.invariants = new Vector<JVGInvariant>();
	}

	public JVGNode(RGNode node) {
		this.zRankNum = node.zRankNum;
		this.isSafeyViolated = node.isSafeyViolated;
		this.isOnlySafetyViolated = node.isOnlySafetyViolated;
		this.violatedJusticeNum = node.violatedJusticeNum;
		this.nodeBDD = node.nodeBDD.id();
		this.violatedSafeties = node.violatedSafeties;
	}

	public String toString() {
		return toString("\t", true);
	}
	public String toString(String space, boolean printBDD) {
		String res = space + "type = " + this.type + "\n" + space
				+ "zRankNum = " + this.zRankNum + "\n" + space
				+ "isSafetyViolated = " + this.isSafeyViolated + "\n"
				+ space + "isOnlySafetyViolated = "
				+ this.isOnlySafetyViolated + "\n" + space
				+ "violatedJusticeNum = " + this.violatedJusticeNum;
		for (int i = 0; i < violatedSafeties.size(); i++) {
			res += "\n" + space + "violated safety: "
					+ Tracer.getNiceStringForId(violatedSafeties.get(i).traceId);
		}
		if (printBDD) {
			res += "\n" + space + "bdd: "
					+ Env.toNiceSignleLineString(this.nodeBDD);
			res += "\n" + space + "transitions bdd: "
					+ Env.toNiceSignleLineString(this.transitions);
		}
		for (int i = 0; i < invariants.size(); i++) {
			res += "\n" + invariants.get(i).toString(space);
		}
		
		if (type == JVGNodeType.CYCLE_NODE) {
			res += "\n assumpSatMap = " + assumpGraph + "\n";
		}
		return res;
	}
	
	public JVGNodeType getType() {
		return type;
	}

	public BDD getTransitions() {
		return transitions;
	}

	public int getViolatedJusticeNum() {
		return violatedJusticeNum;
	}
	

}