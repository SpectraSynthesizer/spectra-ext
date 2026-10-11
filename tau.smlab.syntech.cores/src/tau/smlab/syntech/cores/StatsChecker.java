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

import java.util.ArrayList;
import java.util.List;

import tau.smlab.syntech.cores.util.Checker;
import tau.smlab.syntech.cores.util.RealizabilityCheckResult;
import tau.smlab.syntech.cores.util.RealizabilityChecker;

/**
 * This is a unrealizable core checker class that keeps information of check inputs, time and results.
 * Useful for evaluation purposes of core computation, should not be used otherwise.
 */
public class StatsChecker extends Checker<Integer> {

	public static class UnrealCheckInfo {
		// The guarantees checked
		public final List<Integer> gars;
		// Total check time
		public final long timeMS;
		// Whether the guarantee set is unreal.
		// Required because checkResult may be null
		public final boolean isUnreal;
		// The raw RealizabilityCheckResult
		// The statistics object should not use any free-able object in it, such as the winning states BDD.
		public final RealizabilityCheckResult checkResult;
		
		public UnrealCheckInfo(List<Integer> parts, long time, boolean isUnreal, RealizabilityCheckResult checkResult) {
			this.gars = new ArrayList<Integer>(parts);
			this.timeMS = time;
			this.isUnreal = isUnreal;
			// Note that the check result will be null if the check was cached
			this.checkResult = checkResult;
		}
	}
	
	// The real checker doing all the work
	private RealizabilityChecker checker;
	// The history kept by the checker
	private List<UnrealCheckInfo> checkInfo;
	
	public StatsChecker(RealizabilityChecker checker) {
		this.checker = checker;
		this.checkInfo = new ArrayList<UnrealCheckInfo>();
	}
	
	/**
	 * Call the underlying checker while preserving call information.
	 */
	@Override
	public boolean wrappedCheck(List<Integer> part, boolean checkCache, boolean storeInCache) {
		checker.clearLastCheckResult();
		// Instead of going through caches and calling the internal check function, simply
		// call the checker's wrapped check function 
		long startTime = System.currentTimeMillis();
		boolean result = checker.wrappedCheck(part, checkCache, storeInCache);
		long totalTime = System.currentTimeMillis() - startTime;
		checkInfo.add(new UnrealCheckInfo(part, totalTime, result, checker.getLastCheckResult()));
		
		return result;
	}
	
	/**
	 * Get the all the check information from the previously performed checks.
	 * The list is not copied - modifications will be reflected by future calls!
	 */
	public List<UnrealCheckInfo> getCheckInformation() {
		return checkInfo;
	}

	@Override
	protected boolean check(List<Integer> part) {
		throw new RuntimeException("Not Implemented! Should never be called");
	}

}
