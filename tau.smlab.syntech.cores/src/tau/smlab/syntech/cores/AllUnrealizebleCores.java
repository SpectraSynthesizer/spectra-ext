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
import java.util.ArrayList;
import java.util.List;

import tau.smlab.syntech.cores.util.Checker;
import tau.smlab.syntech.cores.util.CoreOutput;
import tau.smlab.syntech.cores.util.RealizabilityCheck;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.util.SysTraceInfoBuilder;
import tau.smlab.syntech.gamemodel.util.TraceIdentifier;
import tau.smlab.syntech.gamemodel.util.TraceInfoBuilder;

/**
 * This class is an extension of the punch algorithm for unrealizable cores
 * The Checker implements the unrealizability check and performs memoization and check counting
 * Allows use of QuickCore instead of ddmin for core searches
 * 
 * @author shalom
 *
 */

public class AllUnrealizebleCores extends AllCoresPunchAlgorithm<Integer> {

	private static TraceInfoBuilder builder = null;
	private boolean useQuickCore;
	
	public AllUnrealizebleCores(GameModel gm, boolean useQuickCore) {
		super(new Checker<Integer>() {
			protected boolean check(List<Integer> sys) {
				return !RealizabilityCheck.isRealizable(builder.build(sys));	
			}
		});

		this.useQuickCore = useQuickCore;
		builder = new SysTraceInfoBuilder(gm);
	}
	
	@Override
	public void setCoreOutput(String specName, PrintStream out) {
		output = new CoreOutput<Integer>(specName, out) {
			@Override
			protected String format(List<Integer> core) {
				return TraceIdentifier.formatLines(core);
			}
		};
	}

	@Override
	protected List<Integer> minimize(List<Integer> set, List<Integer> base) {
		return useQuickCore ? quickCore(set, base) : ddmin(set, base);
	}
	
	private List<Integer> quickCore(List<Integer> set, List<Integer> base) {
		List<Integer> min = new ArrayList<Integer>(base);

		// use the same checker so we count also checks by QuickCore
		QuickCore qc = new QuickCore(builder.restore(), checker);
		qc.setBase(base);
		set.removeAll(base);
		min = qc.minimize(set);
		// restore game model for outside users because QuickCore changes the model
		builder.restore();
		return min;
	}
}
