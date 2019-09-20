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
import tau.smlab.syntech.jtlv.Env;

/**
 * An edge in the JVTS
 */
public class JVGEdge {
	
	JVGEdgeType type;
	int destInd;
	/**
	 * @deprecated - used to contain a BDD of the transitions represented by this edge. Instead
	 * we compute this on-the-fly using the source and target nodes and their inner transitions.
	 * Should in the future this will be calculated again, it should be done in 
	 * JusticeViolationGraph::updateEdgesJVG, where an edge is created.
	 */
	BDD transBDD;
	Vector<JVGInvariant> invariants;
	
	public JVGEdge() {
		this.type = JVGEdgeType.UNKNOWN_EDGE;
		this.destInd = -1;
		this.invariants = new Vector<JVGInvariant>();
		this.transBDD = Env.FALSE();
	}
	
	public String toString() {
		return toString("\t\t");
	}
	public String toString(String space) {
		String res = space + "destination node index = " + this.destInd + "\n"
				+ space + "type = " + this.type;
		if (!this.transBDD.isZero()) {
			res += "\n" + space + "transBDD = " +
				Env.toNiceSignleLineString(this.transBDD);
		}
		for (int i = 0; i < invariants.size(); i++) {
			res += "\n" + invariants.get(i).toString(space);
		}
		return res;
	}

	public int getDestInd() {
		return destInd;
	}

}