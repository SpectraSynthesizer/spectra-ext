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
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.jtlv.Env;

/**
 * A Ranking Graph node
 */
class RGNode {
	public RGNode() {
		this.zRankNum = -1;
		this.isInitialNode = false;
		this.isSafeyViolated = false;
		this.violatedJusticeNum = -1;
		this.violatedSafeties = new Vector<BehaviorInfo>();
	}

	public String toString() {
		return toString("\t");
	}
	
	public String toString(String space) {
		String res = space + "zRankNum = " + this.zRankNum + "\n" + space
				+ "isInitialNode = " + this.isInitialNode + "\n" + space
				+ "isSafetyViolated = " + this.isSafeyViolated + "\n"
				+ space + "isOnlySafetyViolated = "
				+ this.isOnlySafetyViolated + "\n" + space
				+ "violatedJusticeNum = " + this.violatedJusticeNum;
		for (int i = 0; i < violatedSafeties.size(); i++) {
			res += "\n" + space + "violated safety: "
					+ Env.toNiceSignleLineString(violatedSafeties.get(i).safety);
		}
		res += "\n" + space + "nodeBDD = "
				+ Env.toNiceSignleLineString(this.nodeBDD);
		return res;
	}

	// BDD representing the states in this RG node
	BDD nodeBDD;
	// Index of the cell in memZ from which this node was created
	int zRankNum;
	boolean isInitialNode;
	boolean isSafeyViolated;
	boolean isOnlySafetyViolated;
	int violatedJusticeNum;
	Vector<BehaviorInfo> violatedSafeties;
}