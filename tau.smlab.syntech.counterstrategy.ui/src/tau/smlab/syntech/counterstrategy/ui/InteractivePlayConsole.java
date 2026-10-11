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

package tau.smlab.syntech.counterstrategy.ui;

import java.util.ArrayList;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

import tau.smlab.syntech.counterstrategy.model.CounterStrategyGraphStruct.CSInvariant;
import tau.smlab.syntech.counterstrategy.model.CounterStrategyGraphStruct.CSNode;
import tau.smlab.syntech.counterstrategy.model.SymbolicCounterStrategyGraph;
import tau.smlab.syntech.counterstrategy.ui.handlers.PopupConcreteHandler;
import tau.smlab.syntech.counterstrategy.ui.model.ModelNode;
import tau.smlab.syntech.counterstrategy.ui.preferences.PreferencePage;


public class InteractivePlayConsole {
	
	/** The CounterStrategyGraph on which the user clicked "Play Interactively". */
	SymbolicCounterStrategyGraph csg;
	
	/** The node on which the user clicked "Play Interactively". */
	ModelNode chosenNodeForInteractivePlay;
	
	/** relevant variables that the user can assign. */
	ArrayList<String> vars;
	
	/**
	 * The node which contains the assignments of the user. Later to be passed
	 * to csg.getConcreteState().
	 */
	CSNode partialAssign;
	
	Combo assignmentVariablesOptions;
	Combo assignmentValueOptions;
	Button deleteLast;
	List addedAssignments;
	
	/** Indicates whether or not the assignment choice of the user is valid */
	boolean choiceValid = false;
	
	/** The last computed values list for the chosen variable */
	ArrayList<String> lastValueList;
	
	
	/**
	 * new Interactive Play dialog to show the user once he clicks "Play
	 * Interactively". In order to activate it do the following:<br>
	 * <code><dd>
	 * InteractivePlayConsole dialog = new InteractivePlayConsole();<br>
	 * if (dialog.isVariablesListEmpty()) {<ul>
	 * 		//there is no choice, probably this is the initial node.</ul>
	 * }<br>
	 * else {<ul>
	 * Display display = new Display();<br>
	 * Shell shell = dialog.createShell(display);<br>
	 * shell.open();</br>
	 * while (!shell.isDisposed())<br>
	 * <ul>	if (!display.readAndDispatch())<br><ul>
	 * 		display.sleep();</ul></ul></ul>}
	 * </dd></code>
	 * 
	 * @pre csg!=null
	 * @pre chosenNodeForInteractivePlay!=null
	 * @param csg
	 *            The CounterStrategyGraph on which the user clicked "Play
	 *            Interactively".
	 * @param chosenNodeForInteractivePlay
	 *            The node on which the user clicked "Play Interactively".
	 */
	public InteractivePlayConsole(SymbolicCounterStrategyGraph csg, ModelNode chosenNodeForInteractivePlay) {
		this.csg = csg;
		this.chosenNodeForInteractivePlay = chosenNodeForInteractivePlay;
		this.vars = new ArrayList<String>();
		ArrayList<Map.Entry<String, Boolean>> tempVars = csg.getVarList(chosenNodeForInteractivePlay.getSelfCsNode());
		for (int i = 0; i < tempVars.size(); i++) {
			if (!tempVars.get(i).getValue() || !PreferencePage.isHideAuxiliaryVariablesChecked()) {
				this.vars.add(tempVars.get(i).getKey());
			}
		}
		this.partialAssign = this.chosenNodeForInteractivePlay.getSelfCsNode().clone();
	}
	
	/**
	 * checks if {@link #vars} is empty.
	 * @return {@link #vars}.isEmpty().
	 */
	public boolean isVariablesListEmpty() {
		return vars.isEmpty();
	}
	
	private void MoveToConcreteState(Shell shell) {
		// This is the concrete state that should be shown in the concrete graph
		CSNode concreteStateToShow = csg.getConcreteState(partialAssign);
		shell.dispose();
		PopupConcreteHandler.activateConcreteGraphView(csg, PlatformUI.getWorkbench(), null, null,
				new ModelNode(1, concreteStateToShow), true);		
	}
	
	public Shell createShell(Shell parent) {
		
		/* main Shell */
		final Shell shell = new Shell(parent,
				SWT.APPLICATION_MODAL | SWT.CLOSE | SWT.MIN | SWT.MAX | SWT.RESIZE | SWT.TITLE);
		shell.setText("Interactive Play - Choosing Concrete State");
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 4;
		shell.setLayout(gridLayout);
		
		/* "Assignment:" label */
		new Label(shell, SWT.NONE).setText("Assignment:");
		
		/* variables (drop-down menu) assignment */
		assignmentVariablesOptions = new Combo(shell, SWT.NONE);
		assignmentVariablesOptions.setItems(vars.toArray(new String[vars.size()]));
		assignmentVariablesOptions.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				if (!vars.contains(assignmentVariablesOptions.getText())) {
					assignmentValueOptions.clearSelection();
					assignmentValueOptions.removeAll();
					assignmentValueOptions.setEnabled(false);
					choiceValid = false;
				} else {
					assignmentValueOptions.clearSelection();
					assignmentValueOptions.removeAll();
					lastValueList = csg.getValueList(partialAssign,
							assignmentVariablesOptions.getText());
					assignmentValueOptions.setItems(lastValueList.toArray(new String[lastValueList.size()]));
					assignmentValueOptions.setEnabled(true);
					choiceValid = false;
				}
			}
		});
		assignmentVariablesOptions.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false));
		
		/* "=" label */
		Label label = new Label(shell, SWT.NONE);
		label.setText("=");
		label.setLayoutData(new GridData(GridData.CENTER, GridData.CENTER, true, false));
		
		/* value (drop-down menu) assignment */
		assignmentValueOptions = new Combo(shell, SWT.NONE);
		assignmentValueOptions.setItems(new String[] {}); // Initially, this
			// list is empty. It depends on the variable choice of the user
		assignmentValueOptions.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false));
		assignmentValueOptions.setEnabled(false);
		assignmentValueOptions.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				if (lastValueList.contains(assignmentValueOptions.getText())) {
					choiceValid = true;
				} else {
					choiceValid = false;
				}
			}
		});
		
		/* "Add Assignment" button */
		Button addAssignment = new Button(shell, SWT.PUSH);
		addAssignment.setText("Add Assignment");
		GridData gridData = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING, GridData.CENTER, false, false);
		gridData.horizontalIndent = 5;
		gridData.verticalSpan = 4;
		addAssignment.setLayoutData(gridData);
		addAssignment.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				if (choiceValid) {
					// add the assignment to the list shown to the user
					addedAssignments
							.add(assignmentVariablesOptions.getText() + "\t=\t" + assignmentValueOptions.getText());
					// add the assignment to {@link #partialAssign}
					partialAssign.invariants.add(
							new CSInvariant(assignmentVariablesOptions.getText(), assignmentValueOptions.getText()));
					// update the list of possible variables
					vars.remove(partialAssign.invariants.get(partialAssign.invariants.size() - 1).var);
					assignmentVariablesOptions.clearSelection();
					assignmentValueOptions.clearSelection();
					assignmentVariablesOptions.removeAll();
					assignmentValueOptions.removeAll();
					assignmentVariablesOptions.setItems(vars.toArray(new String[vars.size()]));
					choiceValid = false;
					
					if (addedAssignments.getItemCount() != 0) {
						deleteLast.setEnabled(true);
					}
					
					if (vars.isEmpty()) {
						MoveToConcreteState(shell);
					}
				}
			}
		});
		
		/* list of assignments already added */
		addedAssignments = new List(shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL);
		addedAssignments.setItems(new String[] {});
		gridData = new GridData(GridData.FILL, GridData.FILL, true, true);
		gridData.verticalSpan = 1;
		gridData.horizontalSpan = 4;
		int listHeight = addedAssignments.getItemHeight() * 10; // Initially, there is a place for 10 objects in the shown list
		Rectangle trim = addedAssignments.computeTrim(0, 0, 0, listHeight);
		gridData.heightHint = trim.height;
		addedAssignments.setLayoutData(gridData);
		
		/* "Delete Last Assignment" button */
	    deleteLast = new Button(shell, SWT.PUSH);
		deleteLast.setText("Delete Last Assignment");
		if (addedAssignments.getItemCount() == 0) {
			deleteLast.setEnabled(false);
		} else {
			deleteLast.setEnabled(true);
		}
		gridData = new GridData(GridData.BEGINNING, GridData.BEGINNING, true, false);
//		gridData.horizontalIndent = 5;
		deleteLast.setLayoutData(gridData);
		deleteLast.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				if (addedAssignments.getItemCount() != 0) {
					// delete last assignment from the list shown to the user
					addedAssignments.remove(addedAssignments.getItemCount() - 1);
					// recompute the list of variables
					vars.add(partialAssign.invariants.get(partialAssign.invariants.size() - 1).var);
					// delete the assignment from {@link #partialAssign}
					partialAssign.invariants.remove(partialAssign.invariants.size() - 1);
					// update the list of possible variables
					assignmentVariablesOptions.clearSelection();
					assignmentValueOptions.clearSelection();
					assignmentVariablesOptions.removeAll();
					assignmentValueOptions.removeAll();
					assignmentVariablesOptions.setItems(vars.toArray(new String[vars.size()]));
					
					if (addedAssignments.getItemCount() == 0) {
						deleteLast.setEnabled(false);
					}			
				}
			}
		});
		
		/* "Fill variable choices" button */
		Button FillVariableChoices = new Button(shell, SWT.PUSH);
		FillVariableChoices.setText("Fill variable choices");
		gridData = new GridData(GridData.FILL, GridData.CENTER, true, false);
		FillVariableChoices.setLayoutData(gridData);
		FillVariableChoices.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				// the variables (and their values) which have only one option for the value
				ArrayList<CSInvariant> variablesAndValues = csg.getVarsAndVals(partialAssign);
				if (variablesAndValues.isEmpty()) {
					return;
				} else {
					for (CSInvariant invariant : variablesAndValues) {
						// add assignment to the list shown to the user
						addedAssignments.add(invariant.var + "\t=\t" + invariant.val);
						// add the assignment to {@link #partialAssign}
						partialAssign.invariants.add(invariant.clone());
						vars.remove(invariant.var); // recompute the list of variables
					}
					// update the list of possible variables
					assignmentVariablesOptions.clearSelection();
					assignmentValueOptions.clearSelection();
					assignmentVariablesOptions.removeAll();
					assignmentValueOptions.removeAll();
					assignmentVariablesOptions.setItems(vars.toArray(new String[vars.size()]));
				}
			}
		});

		/* "Choose concrete state" button */
		Button ChooseConcreteState = new Button(shell, SWT.PUSH);
		ChooseConcreteState.setText("Choose concrete state");
		gridData = new GridData(GridData.FILL, GridData.CENTER, true, false);
		ChooseConcreteState.setLayoutData(gridData);
		ChooseConcreteState.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				MoveToConcreteState(shell);
			}
		});
		
		/* "Cancel" button */
		Button Cancel = new Button(shell, SWT.PUSH);
		Cancel.setText("Cancel");
		gridData = new GridData(GridData.FILL, GridData.CENTER, true, false);
		Cancel.setLayoutData(gridData);
		Cancel.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				shell.dispose();
			}
		});
		
		shell.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent arg0) {
				assignmentVariablesOptions.dispose();;
				assignmentValueOptions.dispose();
				addedAssignments.dispose();
			}
		});
		
		shell.pack();
		
		return shell;
	}
}
