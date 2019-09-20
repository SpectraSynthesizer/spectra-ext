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

import java.io.File;

/**
 * Base class for repair input and output files
 * 
 * @author shalom
 *
 */
public class RepairIO {
	protected static File mFolder = null;

	protected static final String generalIndexFilename = "GenIndex.txt";
	protected static final String tokFilename = "FileName";
	protected static final String tokRemovedAsm = "RemovedAsmNum";
	
	protected static final String tokTime = "Time";
	protected static final String tokDepth = "Depth";
	protected static final String tokEnv = "Environment";
	protected static final String tokBDDs = "BDDs";
	protected static final String tokGlobaly = "G";
	protected static final String tokGlobalyFinaly = "GF";
	protected static final String tokInit = "INIT";
	
	public void eraseFiles() {
		for (File f : mFolder.listFiles()) {
			f.delete();
		}
		mFolder.delete();
	}
	
	protected static String formatIndexFile(long r) {
		return "Index_" + r + ".txt";
	}
	
	protected static String formatEnvFile(long r) {
		return "Env_" + r;
	}
	
	protected static String formatBDDFile(long r, long bdd) {
		return "BDD_" + r + "_" + bdd;
	}
	
	protected static String formatBDDTextFile(long r, long bdd) {
		return "BDDText_" + r + "_" + bdd + ".txt";
	}
	
	
}
