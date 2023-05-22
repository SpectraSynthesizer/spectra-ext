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

package tau.smlab.syntech.richcontrollerwalker.ui.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.RadioGroupFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import tau.smlab.syntech.richcontrollerwalker.ui.Activator;
import tau.smlab.syntech.richcontrollerwalker.util.Preferences;

/**
 * This class represents a preference page that is contributed to the
 * Preferences dialog. By subclassing <samp>FieldEditorPreferencePage</samp>, we
 * can use the field support built into JFace that allows us to create a page
 * that is small and knows how to save, restore and apply itself.
 * <p>
 * This page is used to modify preferences only. They are stored in the
 * preference store that belongs to the main plug-in class. That way,
 * preferences can be accessed directly via the preference store.
 */

public class PreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	public PreferencePage() {
		super(GRID);
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
		setDescription("Rich Controller Walker preferences:");
	}

	/**
	 * Creates the field editors. Field editors are abstractions of the common GUI
	 * blocks needed to manipulate various types of preferences. Each field editor
	 * knows how to save and restore itself.
	 */

	private IntegerFieldEditor alternativeStepCount;
	private BooleanFieldEditor isLogActive;
	private RadioGroupFieldEditor controllerType;

	public void createFieldEditors() {
		alternativeStepCount = new IntegerFieldEditor(PreferenceConstants.ALTERNATIVE_STEP_COUNT,
				"Alternative Step Count - The number of alternatives steps to choose from", getFieldEditorParent());
		alternativeStepCount.setValidRange(0, Integer.MAX_VALUE);
		addField(alternativeStepCount);

		isLogActive = new BooleanFieldEditor(PreferenceConstants.LOG_ACTIVE_ON_START, "Create log on startup",
				getFieldEditorParent());
		addField(isLogActive);
		
		controllerType = new RadioGroupFieldEditor(PreferenceConstants.CONTROLLER_TYPE, "Controller type", 1,
				new String[][] { { "Just-in-time Symbolic Controller", PreferenceConstants.JITS },
						{ "Static Symbolic Controller", PreferenceConstants.STATIC } },
				getFieldEditorParent(), true);

		addField(controllerType);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
	 */
	public void init(IWorkbench workbench) {
	}

	public static Preferences getPreferences() {
		return new Preferences(getIsLogActive(), getAlternativeStepCount(), getUseJitController(),
				tau.smlab.syntech.ui.preferences.PreferencePage.getBDDPackageSelection(),
				tau.smlab.syntech.ui.preferences.PreferencePage.getBDDPackageVersionSelection());
	}

	public static int getAlternativeStepCount() {
		return Activator.getDefault().getPreferenceStore().getInt(PreferenceConstants.ALTERNATIVE_STEP_COUNT);
	}

	public static boolean getIsLogActive() {
		return Activator.getDefault().getPreferenceStore().getBoolean(PreferenceConstants.LOG_ACTIVE_ON_START);
	}
	
	public static boolean getUseJitController() {
		return PreferenceConstants.JITS.equals(Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.CONTROLLER_TYPE));
	}
}