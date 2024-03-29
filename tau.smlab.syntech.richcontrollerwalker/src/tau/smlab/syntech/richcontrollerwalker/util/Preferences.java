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

package tau.smlab.syntech.richcontrollerwalker.util;

import tau.smlab.syntech.jtlv.BDDPackage;
import tau.smlab.syntech.jtlv.BDDPackage.BBDPackageVersion;

public class Preferences {

	private static class BddPreferences {
		private final BDDPackage bddPackage;
		private final BBDPackageVersion bddPackageVersion;

		private BddPreferences(BDDPackage bddPackage, BBDPackageVersion bddPackageVersion) {
			this.bddPackage = bddPackage;
			this.bddPackageVersion = bddPackageVersion;
		}

		private void setPackage() {
			BDDPackage.setCurrPackage(bddPackage, bddPackageVersion);
		}
	}

	private final boolean isLogActive;
	private final boolean useJitController;
	private final int maxNumDisplayedSteps;
	private final BddPreferences bddPrefs;

	public Preferences(boolean isLogActive, int stepsBound, boolean useJitController, BDDPackage bddPackage,
			BBDPackageVersion bddPackageVersion) {
		this.maxNumDisplayedSteps = stepsBound;
		this.isLogActive = isLogActive;
		this.useJitController = useJitController;
		this.bddPrefs = new BddPreferences(bddPackage, bddPackageVersion);
	}

	public boolean isLogActive() {
		return isLogActive;
	}
	
	public boolean useJitController() {
		return useJitController;
	}

	public int getMaxNumDisplayedSteps() {
		return maxNumDisplayedSteps;
	}

	public void setCurrentBDDPackage() {
		bddPrefs.setPackage();
	}

}
