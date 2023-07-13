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

package tau.smlab.syntech.richcontrollerwalker.ui.dialogs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDVarSet;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.richcontrollerwalker.SymbolicWalker;
import tau.smlab.syntech.richcontrollerwalker.bdds.BddUtil;
import tau.smlab.syntech.richcontrollerwalker.bdds.VarClassifier;
import tau.smlab.syntech.richcontrollerwalker.options.PartialAssignment;
import tau.smlab.syntech.richcontrollerwalker.ui.action.BDDStringUtil;
import tau.smlab.syntech.richcontrollerwalker.ui.action.DDFilterHelper;
import tau.smlab.syntech.richcontrollerwalker.ui.action.IVarConfiguration;

public class SmartFilterDialog extends TitleAreaDialog {

	private org.eclipse.swt.widgets.List possibleAssignmentsSWT;
	private ArrayList<PartialAssignment> possibleAssignments;
	private SymbolicWalker sw;
	private Text depth;

	private String selectedFilter = "";

	private VarsTable variablesTable;
	private IVarConfiguration varConfig;

	private static final int DEFAULT_DEPTH = 5;

	public SmartFilterDialog(Shell shell, SymbolicWalker sw, IVarConfiguration varConfig) {
		super(shell);
		setShellStyle(SWT.MIN | SWT.MAX | SWT.RESIZE);
		this.sw = sw;
		this.varConfig = varConfig;
	}

	public String getSelectedFilter() {
		return this.selectedFilter;
	}

	@Override
	public void create() {
		super.create();
		setTitle("Smart Filters");
	}

	private SashForm createSashForm(Composite body) {
		SashForm sashForm = new SashForm(body, SWT.HORIZONTAL);
		sashForm.setTouchEnabled(true);
		sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		return sashForm;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite body = (Composite) super.createDialogArea(parent);
		SashForm main = createSashForm(body);
		createLeftView(main);
		createRightView(main);
		return body;
	}

	private void createRightView(Composite parent) {
		Group rightView = new Group(parent, SWT.FILL);
		rightView.setText("Variables");
		rightView.setLayout(new GridLayout(1, false));
		rightView.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		this.variablesTable = new VarsTable(rightView);
		updateVars(sw.getOptionsManager().getFilteredSuccessors());
	}

	private void createLeftView(Composite parent) {
		Group leftView = new Group(parent, SWT.FILL);
		leftView.setText("Results");
		leftView.setLayout(new GridLayout(1, false));
		leftView.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		createPossibleStepsList(leftView);

		possibleAssignmentsSWT.add("<No Search Initiated>");
		possibleAssignmentsSWT.setEnabled(false);

		createDepthText(leftView);
		createFindButton(leftView);
	}

	private String assignmentToStr(PartialAssignment assignment, BDD successors, BDDVarSet moduleVars,
			List<String> orderedVars) {
		BDD step = assignment.getBDD();
		String result = BDDStringUtil.orderedBddToStr(step, orderedVars);

		BDD next = successors.id().andWith(step);
		result += " [" + (int) Env.getSatCount(next, moduleVars) + "]";
		next.free();

		if (!assignment.isComplete()) {
			// Partial end of the tree
			result += " [...]";
		}

		return result;
	}

	private void createFindButton(Composite parent) {
		Button find = new Button(parent, SWT.NONE);
		find.setText("Find!");
		find.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				possibleAssignmentsSWT.removeAll();
				int depthVal = getCurrentDepth();
				List<String> varOrder = varConfig.getVariablesByModule(sw.getTurn());
				possibleAssignments = sw.getPartialAssignments(varOrder, depthVal);
				if (!possibleAssignments.isEmpty()) {
					BDD successors = sw.getOptionsManager().getFilteredSuccessors();
					BDDVarSet moduleVars = BddUtil.getVarsByModule(sw.getTurn()).toVarSet();
					possibleAssignments.forEach(
							s -> possibleAssignmentsSWT.add(assignmentToStr(s, successors, moduleVars, varOrder)));
					possibleAssignmentsSWT.setEnabled(true);
				} else {
					possibleAssignmentsSWT.add("<No Options Found>");
					possibleAssignmentsSWT.setEnabled(false);
				}
			}
		});
	}

	private int getCurrentDepth() {
		String text = depth.getText();
		if (text.isEmpty()) {
			return DEFAULT_DEPTH;
		}
		return Integer.parseInt(text);
	}

	private void createDepthText(Composite parent) {
		Composite depthGroup = new Composite(parent, SWT.NONE);
		RowLayout layout = new RowLayout(SWT.HORIZONTAL);
		layout.center = true;
		depthGroup.setLayout(layout);

		Label depthLabel = new Label(depthGroup, SWT.NONE);
		depthLabel.setText("Search Depth: ");

		depth = new Text(depthGroup, SWT.NONE);
		depth.addVerifyListener(event -> {
			if (!event.text.matches("[0-9]*")) {
				event.doit = false;
			}
		});
		depth.setText(String.valueOf(DEFAULT_DEPTH));
	}

	private void updateVars(BDD successors) {
		List<String> dontCares = VarClassifier.findDontCareVars(successors).stream().map(v -> v.name()).toList();
		Map<String, String> fixed = VarClassifier.findFixedVars(successors).entrySet().stream()
				.collect(Collectors.toMap(e1 -> e1.getKey().name(), e2 -> e2.getValue().name()));

		variablesTable.reset(varConfig, dontCares, fixed, sw.getTurn(), true);

	}

	private void createPossibleStepsList(Composite container) {
		GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1);
		gridData.heightHint = 200;
		gridData.grabExcessHorizontalSpace = true;
		gridData.horizontalAlignment = SWT.FILL;

		possibleAssignmentsSWT = new org.eclipse.swt.widgets.List(container, SWT.V_SCROLL | SWT.H_SCROLL);
		possibleAssignmentsSWT.setTouchEnabled(true);

		possibleAssignmentsSWT.setLayoutData(gridData);

		possibleAssignmentsSWT.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (validate()) {
					PartialAssignment assignment = possibleAssignments.get(possibleAssignmentsSWT.getSelectionIndex());
					BDD successors = assignment.getBDD().andWith(sw.getOptionsManager().getFilteredSuccessors().id());
					updateVars(successors);
				}
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				validate();
			}

			private boolean validate() {
				String[] selection = possibleAssignmentsSWT.getSelection();

				if (selection == null || selection.length == 0) {
					getButton(IDialogConstants.OK_ID).setEnabled(false);
				} else if (selection.length == 1) {
					getButton(IDialogConstants.OK_ID).setEnabled(true);
					return true;
				}
				return false;
			}
		});

		possibleAssignmentsSWT.addMouseListener(new MouseListener() {

			@Override
			public void mouseUp(MouseEvent e) {
			}

			@Override
			public void mouseDown(MouseEvent e) {
			}

			@Override
			public void mouseDoubleClick(MouseEvent e) {
				String[] selection = possibleAssignmentsSWT.getSelection();
				if (selection.length == 1) {
					okPressed();
				}
			}
		});
	}

	@Override
	protected void okPressed() {
		if (possibleAssignmentsSWT.getSelectionCount() == 1) {
			BDD value = possibleAssignments.get(possibleAssignmentsSWT.getSelectionIndex()).getBDD();
			DDFilterHelper helper = new DDFilterHelper();
			String result = "";
			for (var entry : VarClassifier.findFixedVars(value).entrySet()) {
				result = helper.add(result, entry.getKey().name(), entry.getValue().name());
			}
			this.selectedFilter = result;
		}
		super.okPressed();
	}

	/**
	 * Returns the initial size of the dialog window that will be opened. The
	 * default value is Full-HD resolution standard.
	 */
	@Override
	protected Point getInitialSize() {
		return new Point(960, 540);
	}
}
