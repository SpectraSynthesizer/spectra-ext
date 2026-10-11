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

package tau.smlab.syntech.cores.ui.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.RadioGroupFieldEditor;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import tau.smlab.syntech.cores.ui.Activator;


/**
 * This class represents a preference page that is contributed to the Preferences dialog. By subclassing
 * <samp>FieldEditorPreferencePage</samp>, we can use the field support built into JFace that allows us to create a page
 * that is small and knows how to save, restore and apply itself.
 * <p>
 * This page is used to modify preferences only. They are stored in the preference store that belongs to the main
 * plug-in class. That way, preferences can be accessed directly via the preference store.
 */

public class PreferencePage extends FieldEditorPreferencePage implements
    IWorkbenchPreferencePage {

	public PreferencePage() {
		super(GRID);
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
		setDescription("Core Preferences:");
	}

  /**
   * Creates the field editors. Field editors are abstractions of the common GUI blocks needed to manipulate various
   * types of preferences. Each field editor knows how to save and restore itself.
   */

	private BooleanFieldEditor useQC;
	private BooleanFieldEditor useQCHeuristics;
	private Button useQCHeuristicsInnerButton; // The inner checkbox button of useQCHeuristics
	private RadioGroupFieldEditor game;

	public void createFieldEditors() {
		useQC = new BooleanFieldEditor(PreferenceConstants.USE_QUICKCORE,
				"Use QuickCore for unrealizable core and related computations", getFieldEditorParent());
		
		useQCHeuristics = new BooleanFieldEditor(PreferenceConstants.USE_QUICKCORE_HEURISTICS, 
				"Use additional performance heuristics for QuickCore", getFieldEditorParent()) {
			
			// Make the second checkbox indented
			@Override
			protected void doFillIntoGrid(Composite parent, int numColumns) {
				super.doFillIntoGrid(parent, numColumns);
				
				useQCHeuristicsInnerButton = getChangeControl(parent);
				Object layoutData = useQCHeuristicsInnerButton.getLayoutData();
				if (layoutData instanceof GridData) {
					((GridData) layoutData).horizontalIndent = 16;
				}
			}
		};
	  
		game = new RadioGroupFieldEditor(PreferenceConstants.REALIZABILITY_CHECK, "Algorithm to use for realizability checking in core computations", 1,
					new String[][] { { "GR(1)", "GR1" }, { "Rabin", "RABIN" }},
					getFieldEditorParent(), true);
		
		addField(useQC);
		addField(useQCHeuristics);
		addField(game);		
	}
	
	@Override
	public void propertyChange(PropertyChangeEvent event) {
		super.propertyChange(event);
		
		// Set the heuristics checkbox to be enabled/disabled according to the QC checkbox
		if (event.getSource() == useQC) {
			boolean qcUsed = (Boolean)event.getNewValue();
			useQCHeuristics.setEnabled(qcUsed, getFieldEditorParent());
			
			// Also uncheck the heuristics checkbox when QC is disabled
			if (useQCHeuristicsInnerButton != null && !useQCHeuristicsInnerButton.isDisposed()) {
				useQCHeuristicsInnerButton.setSelection(qcUsed && getPreferenceStore().getBoolean(PreferenceConstants.USE_QUICKCORE_HEURISTICS));
			}
		}
	}
	
	@Override
	protected void initialize() {
		super.initialize();
		
		// Set the heuristics checkbox to be enabled/disabled according to the QC checkbox
		boolean qcUsed = getPreferenceStore().getBoolean(PreferenceConstants.USE_QUICKCORE);
		useQCHeuristics.setEnabled(qcUsed, getFieldEditorParent());
		
		// Uncheck the heuristics checkbox when QC is disabled
		if (useQCHeuristicsInnerButton != null && !useQCHeuristicsInnerButton.isDisposed()) {
			useQCHeuristicsInnerButton.setSelection(qcUsed && useQCHeuristics.getBooleanValue());
		}
	}
	
  /*
   * (non-Javadoc)
   * 
   * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
   */
	public void init(IWorkbench workbench) {
	}

	public static boolean getUseQuickCore() {
		return Activator.getDefault().getPreferenceStore().getBoolean(PreferenceConstants.USE_QUICKCORE);
	}
	
	public static boolean getUseQuickCoreHeuristics() {
		return Activator.getDefault().getPreferenceStore().getBoolean(PreferenceConstants.USE_QUICKCORE_HEURISTICS);
	}
	
	public static boolean useGR1Realizability() {
		return Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.REALIZABILITY_CHECK)
				.equals("GR1");
	}

}