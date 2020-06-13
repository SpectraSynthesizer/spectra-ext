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

import net.sf.javabdd.BDD;
import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.jtlv.Env;

/** structure for assumptions that differs GF from G
 * 	allows initial assumptions, which are used only with GLASS
 * 
 * @author shalom
 *
 */

public class BasicAssumption {
	public enum Kind {INIT, SAFETY, JUSTICE};
	public BDD bdd;
	public Kind k;
	
	public BasicAssumption(BDD b, Kind k) {
		bdd = b;
		this.k = k;
	}
	
	/**
	 * assumes the behavior is either a justice or a safety
	 * @param b
	 */
	public BasicAssumption(BehaviorInfo b) {
		if (b.isJustice()) {
			bdd = b.justice;
			k = Kind.JUSTICE;
		} else if (b.isInitial()) {
			bdd = b.initial; 
			k = Kind.INIT;
		} else if (b.isSafety()) {
			bdd = b.safety;
			k = Kind.SAFETY;
		}
	}
	
	public String toString() {
		String p = "";
		switch (k) {
		case INIT:
			p = "";
			break;
		case SAFETY:
			p = "G ";
			break;
		case JUSTICE:
			p = "GF ";
			break;
		}
			
		return "asm " + p + asmText(bdd) +";";
	}
	
	public Kind kind() {
		return k;
	}
	
	/**
	 * 
	 * @param other the assumption that may or not be stronger
	 * @return true if we are weaker than other
	 * 
	 * If both are the same kind and other BDD implies ours, or if other is G with the same implication (or both), other is stronger
	 */
	public boolean weakerThan(BasicAssumption other) {
		return (k == other.k || other.k == Kind.SAFETY) && compareBDDs(other.bdd, bdd);
	}
	
	/**
	 * 
	 * @param list of assumptions
	 * @return only ones which are not implied by others
	 */
	public static List<BasicAssumption> removeStrong(List<BasicAssumption> all) {
		List<BasicAssumption> result = new ArrayList<BasicAssumption>();
		boolean weak;
		while (!all.isEmpty()) {
			weak = true;
			BasicAssumption curr = all.remove(0);
			List<BasicAssumption> checkList = new ArrayList<BasicAssumption>(all);
			checkList.addAll(result);
			while (!checkList.isEmpty() && weak) {
				if (checkList.remove(0).weakerThan(curr)) {
					weak = false;
				}
			}

			if (weak) {
				result.add(curr);
			}
		}
		return result;
	}
	
	public BehaviorInfo translateToBehavior() {
		BehaviorInfo bh = null;
		switch (k) {
		case INIT:
			bh = new BehaviorInfo(bdd.id(), null, null, null, null, 0, false);
			break;
		case SAFETY:
			bh = new BehaviorInfo(null, bdd.id(), null, null, null, 0, false);
			break;
		case JUSTICE:
			bh = new BehaviorInfo(null, null, bdd.id(), null, null, 0, false);
			break;
		}

		return bh;
	}
	
	public void addToModel(GameModel m) {
		switch (k) {
		case INIT:
			m.getEnv().conjunctInitial(bdd.id());
			break;
		case SAFETY:
			m.getEnv().conjunctTrans(bdd.id()); //conjunction consumes the BDD thus the .id() 
			break;
		case JUSTICE:
			m.getEnv().addJustice(bdd.id());
			break;
		}
		
	}
	
	public static List<BasicAssumption> conjuctAsm(List<BasicAssumption> list) {
		List<BasicAssumption> result = new ArrayList<BasicAssumption>();
		BDD GBDD = Env.TRUE();
		BDD IBDD = Env.TRUE();
		
		while (!list.isEmpty()) {
			BasicAssumption curr = list.remove(0);
			
			switch (curr.k) {
			case INIT:
				IBDD.andWith(curr.bdd.id());
				break;
			case SAFETY:
				GBDD.andWith(curr.bdd.id());
				break;
			case JUSTICE:
				result.add(curr);
				break;
			}
		}
		
		if (!IBDD.isOne()) {
			result.add(new BasicAssumption(IBDD, Kind.INIT));
		}
		if (!GBDD.isOne()) {
			result.add(new BasicAssumption(GBDD, Kind.SAFETY));
		}
		return result;	
	}
	
	private static boolean compareBDDs(BDD a, BDD b) {
		return a.imp(b).isOne();
	}
	
	private static String asmText(BDD bdd) {
		return bdd.toString().replaceAll("\\w*\\'", "next($0)").replaceAll("\\'\\)","\\)");
	}
}