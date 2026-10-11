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

package tau.smlab.syntech.counterstrategy.ui.preferences;

import org.eclipse.jface.preference.*;
import org.eclipse.ui.IWorkbenchPreferencePage;

import tau.smlab.syntech.counterstrategy.ui.Activator;
 
import org.eclipse.ui.IWorkbench;

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
    setDescription("General SYNTECH preferences:");
  }

  /**
   * Creates the field editors. Field editors are abstractions of the common GUI blocks needed to manipulate various
   * types of preferences. Each field editor knows how to save and restore itself.
   */

  private BooleanFieldEditor mergeAttractors;
  private BooleanFieldEditor hideAuxiliaryVariables;
  private IntegerFieldEditor concretizationDepth;
  public void createFieldEditors() {
	  mergeAttractors = new BooleanFieldEditor(PreferenceConstants.MERGE_ATTRACTORS, "Merge attractors - "
			  + "Attractor nodes in the symbolic graph are combined if their combination does not result in a cycle in the symbolic graph", getFieldEditorParent());
	  hideAuxiliaryVariables = new BooleanFieldEditor(PreferenceConstants.HIDE_AUXILIARY_VARIABLES, "Hide auxiliary variables - Auxiliary variables, which are not part of the specification, will not be displayed as invariants of states in the graph", getFieldEditorParent());
	  concretizationDepth = new IntegerFieldEditor(PreferenceConstants.CONCRETIZATION_DEPTH, "Concretization Depth - The number of levels to display in the concrete graph view when computing concrete sub graph", getFieldEditorParent());
	  concretizationDepth.setValidRange(1, Integer.MAX_VALUE);
	  addField(mergeAttractors);
	  addField(hideAuxiliaryVariables);
	  addField(concretizationDepth);
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
   */
  public void init(IWorkbench workbench) {
  }


  public static boolean isMergeAttractorsChecked()
  {
	  return Activator.getDefault().getPreferenceStore().getBoolean(PreferenceConstants.MERGE_ATTRACTORS);
  }
  
  public static boolean isHideAuxiliaryVariablesChecked()
  {
	  return Activator.getDefault().getPreferenceStore().getBoolean(PreferenceConstants.HIDE_AUXILIARY_VARIABLES);	  
  }
  
  public static int getConcretizationDepth()
  {
	  return Activator.getDefault().getPreferenceStore().getInt(PreferenceConstants.CONCRETIZATION_DEPTH);
  }

}