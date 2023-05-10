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

package tau.smlab.syntech.ctd.ui;

import static tau.smlab.syntech.ctd.ui.Activator.PLUGIN_NAME;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.eclipse.core.resources.IFile;

import tau.smlab.syntech.ctd.CTDUILogic;
import tau.smlab.syntech.jtlv.BDDPackage;
import tau.smlab.syntech.ui.extension.SyntechAction;
//import tau.smlab.syntech.mtd.Mtd;
import tau.smlab.syntech.regextesting.Mtd;
import tau.smlab.syntech.ui.preferences.PreferencePage;

public class CTDMenu extends SyntechAction<CTDActionID> {

	@Override
	public String getPluginName() {
		return PLUGIN_NAME;
	}

	/**
	 * Indicate whether the run-method of this action may be run as a job.
	 * 
	 * default is <code>true</code> and must be overriden for <code>false</code>,
	 * e.g., if access to UI is needed.
	 * 
	 * @return
	 */
	protected boolean runAsJob() {
		return false;
	}

	@Override
	public CTDActionID[] getActionItems() {
		return CTDActionID.values();
	}

	@Override
	public void run(CTDActionID actionID, IFile specFile) {
		BDDPackage.setCurrPackage(PreferencePage.getBDDPackageSelection(), PreferencePage.getBDDPackageVersionSelection());
		switch (actionID) {
		case GENERATE_CONFIG_SPEC:
			try {
				CTDUILogic.generateConfigFromSpec(specFile, consolePrinter);
			} catch (Exception e) {
				consolePrinter.println(getStackTrace(e));
			}
			break;

		case GENERATE_CONFIG_SC:
			try {
				CTDUILogic.generateConfigFromSC(specFile, consolePrinter, tau.smlab.syntech.ctd.ui.preferences.PreferencePage.getUseJitController());
			} catch (Exception e) {
				consolePrinter.println(getStackTrace(e));
			}
			break;

		case COMPUTE_CTD:
			try {
				CTDUILogic.compute(specFile, consolePrinter, tau.smlab.syntech.ctd.ui.preferences.PreferencePage.getUseJitController());
			} catch (Exception e) {
				consolePrinter.println(getStackTrace(e));
			}
			break;

		case COMPUTE_MTD:
			try {
				boolean useJitController = tau.smlab.syntech.ctd.ui.preferences.PreferencePage.getUseJitController();
				String outPath = specFile.getLocation().removeLastSegments(1).addTrailingSeparator().toString() + "out"
						+ File.separator + (useJitController? "jit" : "static") + File.separator;

				File ctDir = new File(outPath, "rtd.out");
				if (!ctDir.exists()) {
					ctDir.mkdirs();
				}
				Mtd.compute(specFile.getFullPath().toOSString(), outPath, ctDir.getAbsolutePath(), true, consolePrinter, useJitController);

			} catch (Exception e) {
				consolePrinter.println(getStackTrace(e));
			}
			break;

		default:
			break;
		}
	}

	private String getStackTrace(Exception e) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		return sw.toString();
	}
}
