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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.Vector;

import tau.smlab.syntech.games.controller.enumerate.EnumStateImpl;
import tau.smlab.syntech.games.controller.enumerate.EnumStrategyI;
import tau.smlab.syntech.games.gr1.GR1RankInfo;
import tau.smlab.syntech.jtlv.Env;

public class AbstractFST {
	
	private Vector<AbstractFSTState> states;
	private AbstractFSTState initialState;
	
	
	public AbstractFST() {
		states = new Vector<AbstractFSTState>();
	}
	
	public AbstractFST(EnumStrategyI counterStategy) {
		states = new Vector<AbstractFSTState>();
		boolean initStateSelected = false;
		for (int i=0; i< counterStategy.numOfStates(); i++){
			AbstractFSTState s = new AbstractFSTState(counterStategy.getState(i));
			states.add(s);
			if (!initStateSelected && counterStategy.getState(i).isInitial())
			{
				initialState = s;
				initStateSelected = true;
			}
			
		}
	}
	/**
	 * dead ends are handled by connecting them to a self loop, finally and finally next patterns that have this state 
	 * are ignored.
	 * this method adds this state and returns it so patterns that have it are identified.
	 * s
	 * @param counterStategy
	 * @return
	 */
	public AbstractFSTState addEscapeState(EnumStrategyI counterStategy) {
		AbstractFSTState escape = new AbstractFSTState ( new EnumStateImpl(counterStategy, counterStategy.numOfStates(), false, Env.TRUE(), new GR1RankInfo(0)));
		states.add(escape);
		for (AbstractFSTState s : states) {
			if (s.getSucc().isEmpty()) {
				List<Integer> succ = s.getSucc();
				succ.add(counterStategy.numOfStates());
			}
		}
		return escape;
	}

	public Vector<AbstractFSTState> getStates() {
		return new Vector<AbstractFSTState>(states);
	}


	public AbstractFSTState getInitialState() {
		return initialState;
	}

	public int getMaxOutgoingEdge() {
		int moe = 1;
		for (AbstractFSTState state : states) {
			moe = java.lang.Math.max(moe, state.getSucc().size());
		}
		return moe;
	}
	
	public AbstractFST genPartialFST(List<AbstractFSTState> set) {
		AbstractFST partialFST = new AbstractFST();
		partialFST.setInitialState(this.initialState);
		for(AbstractFSTState state : this.states){
			if (!set.contains(state)){
				AbstractFSTState newState = new AbstractFSTState(state);
				newState.setSucc(set);
				partialFST.addState(newState);
			}
		}
		return partialFST;
		
	}

	public void setInitialState(AbstractFSTState initialState) {
		this.initialState = initialState;
	}
	
	public void addState(AbstractFSTState state) {
		this.states.add(state);
	}
	
	public boolean hasCycle(){
		Map<Integer, String> color = new HashMap<Integer, String>();
		for(AbstractFSTState s : states){
			color.put(s.getID(), "White");
		}
		Stack<Integer> stack = new Stack<Integer>();
		stack.push(initialState.getID());
		while(!stack.isEmpty()){
			int n = stack.pop();
			color.replace(n, "Grey");
			for (int v : getSucc(n)){
				if (color.get(v).equals("Grey"))
					return true;
				if (color.get(v).equals("White"))
					stack.push(v);
			}
			//color.replace(n, "Black");
		}
		return false;
	}
	
	public AbstractFSTState getStateByID(int id) {
		for(AbstractFSTState state : states)
			if(state.getID()==id)
				return state; 
		return null;
	}
	
	

	private Vector<Integer> getSucc(int id) {
		for(AbstractFSTState state : states)
			if(state.getID()==id)
				return new Vector<Integer>(state.getSucc()); 
		return null;
	}

	public List<AbstractFSTState> getNextStates(List<AbstractFSTState> states) {
		List<AbstractFSTState> nextStates = new ArrayList<AbstractFSTState>();
		for (AbstractFSTState s : states){
			for(Integer i : s.getSucc()){
				nextStates.add(getStateByID(i));
			}
		}
		return nextStates;
	}
	
	public List<Integer>[] generateGraph() {
		int V = states.size();
        List<Integer>[] g = new List[V];        
        for (int i = 0; i < V; i++)
            g[i] = new ArrayList<Integer>();        
        
        
        for (int i = 0; i < V; i++) {
        	Vector<Integer> succ = this.getStateByID(i).getSucc();
        	for (int j = 0; j < succ.size(); j++) {
        		g[i].add(succ.get(j));
        	}
        }
        
        return g;
	}
}
