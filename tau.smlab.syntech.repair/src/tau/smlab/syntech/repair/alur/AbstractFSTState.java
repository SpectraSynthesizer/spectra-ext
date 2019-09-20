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

package tau.smlab.syntech.repair.alur;

/**
 * @author moskovitch
 * @author novgorodov
 */

import java.util.List;
import java.util.Vector;

import tau.smlab.syntech.games.controller.enumerate.EnumStateI;

public class AbstractFSTState {
	
	private Vector<Integer> succ;
	private EnumStateI stateI;
	
	//for tests only
	public int id;

	public AbstractFSTState(EnumStateI state) {
		this.stateI = state;
		succ = new Vector<Integer>();
		for(EnumStateI s : state.getSuccessors()){
			succ.add(s.getStateId());
		}
	}

	public Vector<Integer> getSucc() {
		return succ;
	}

	public EnumStateI getStateI() {
		return stateI;
	}

	public AbstractFSTState(AbstractFSTState state) {
		this.stateI = state.getStateI();
		this.succ = new Vector<Integer>(state.getSucc());
		//for tests only
		if(stateI == null)
			this.id = state.id;
	}
	
	
	
	//for tests only
	public AbstractFSTState(int i, Vector<Integer> succ) {
		id =i;
		this.succ = succ; 
	}

	public void setSucc(List<AbstractFSTState> set) {

		for(AbstractFSTState state : set){
			if (this.succ.contains(state.getID()))
				succ.removeElement(state.getID());
		}
	}
	
	public int getID(){
		//for tests only
		if (this.stateI==null)
			return id;
		return this.stateI.getStateId();
	}

	public boolean hasSelfLoop() {
		for(Integer i : succ)
			if (i == this.getID())
				return true;
		return false;
	}
	
	public void printSucc() {
		System.out.print("Succ: ");
		for(Integer i : succ)
			System.out.print(i+",");
		System.out.println();
	}

}
