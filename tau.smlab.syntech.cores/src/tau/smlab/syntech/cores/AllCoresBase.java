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

package tau.smlab.syntech.cores;

import java.io.PrintStream;

/**
 * 
 * Abstract class for algorithms that compute and return all cores and a global core
 * It has an abstract check function for the violation, which should be monotonic
 * Has statistics class for the number of all checks of the property, and checks done by the abstract method check (actual).
 * 
 * @author shalom
 */

import java.util.List;

import tau.smlab.syntech.cores.util.CoreData;
import tau.smlab.syntech.cores.util.CoreOutput;

public abstract class AllCoresBase<T> {

	protected CoreData<T> data = null;
	protected CoreOutput<T> output = null;
	
	AllCoresBase() {
		data = new CoreData<T>();
	}
	
	public void setCoreOutput(String specName, PrintStream out) {
		output = new CoreOutput<T>(specName, out);
	}
	
	public CoreData<T> getCoreData() {
		return data;
	}
	
	protected void start() {
		if (output!=null) {
			output.writeBegin();
		}
	}
	
	protected void registerCore(List<T> core) {
		data.registerCore(core);
		if (output!=null) {
			output.writeCore(core, checks(), actualChecks());
		}
	}

	protected void setIntersection(List<T> inter) {
		// The computed base at the highest recursion level is the intersection of all cores
		if (!data.hasCoresIntersection()) {
			data.setCoresIntersection(inter);
			if (output!=null) {
				output.writeCoreIntersection(inter, checks(), actualChecks());
			}
		}
	}
	
	protected void end() {
		if (output!=null) {
			output.writeEnd();
		}
	}	
	/**
	 * All Cores (ac) check.
	 * Abstract check method for detecting a subset that fulfills the criterion.
	 * Assumed to be monotonic.
	 * 
	 * @param part
	 * @return
	 */
	protected abstract boolean acCheck(List<T> part);

	/**
	 * This abstract method is the one containing the algorithm for all cores
	 * @param superSet
	 */
	public abstract void computeAllCores(List<T> superSet);
	
	/** Supply information about the number of checks and actual checks perfromed
	 * 
	 * @return
	 */
	public abstract int checks();
	public abstract int actualChecks();
}
