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

package tau.smlab.syntech.cores.util;

import java.io.PrintStream;
import java.util.List;

/**
 * Class for outputing cores. 
 * This allows writing cores to a PrintStream
 * 
 * @author shalom
 *
 * @param <T>
 */

public class CoreOutput<T> {

	private String specName = "";
	private long startTime = 0;
	protected PrintStream output = null;
	
	public enum Label {
		BEGIN, CORE, INTERSECT, END
	}
	
	public CoreOutput(String name, PrintStream out) {
		specName = name;
		output = out;
	}
	
	public void writeBegin() {
		output.println(specName + "," + Label.BEGIN);
		startTime = System.currentTimeMillis();
	}
	
	public void writeCoreIntersection(List<T> inter, int checks, int actual) {
		output.println(specName + "," + 
					Label.INTERSECT + "," + 
					inter.size() + "," + 
					(System.currentTimeMillis() - startTime) + "," + 
					checks + "," + 
					actual + "," +
					format(inter));
	}
	
	public void writeCore(List<T> core, int checks, int actual) {
		output.println(specName + "," + 
				Label.CORE + "," + 
				core.size() + "," + 
				(System.currentTimeMillis() - startTime) + "," + 
				checks + "," + 
				actual + "," +
				format(core));
	}

	public void writeEnd() {
		output.println(specName + "," + Label.END);
	}
	
	// allows outputing lists of type T as strings. Override this if it's not standard
	protected String format(List<T> core) {
		return core.toString();
	}
}
