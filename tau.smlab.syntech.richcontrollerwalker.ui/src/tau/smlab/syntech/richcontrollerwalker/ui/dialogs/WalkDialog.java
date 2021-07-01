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
import java.util.HashMap;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerEditor;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationEvent;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationStrategy;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TableViewerEditor;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.wb.swt.SWTResourceManager;

import tau.smlab.syntech.richcontrollerwalker.bdds.IPersistent;
import tau.smlab.syntech.richcontrollerwalker.util.IBreakpoint;

public abstract class WalkDialog extends TitleAreaDialog {
	private Shell shell;
	private String dialogTitle;


	protected Group stepsGrp;
	protected Text filterText;
	protected Button textFilterBtn;
	protected Label possibleStepsLabel;
	protected java.util.List<String> possibleStepsList = new ArrayList<>();
	protected List possibleStepsSWT;
	protected int selectedOptionIndex;
	protected Button loadMoreStepsBtn;

	private Label soFar;
	protected TableViewer consoleTableViewer;

	protected Button resetBtn;
	protected Button stepBackButton;
	protected Button nextButton;
	protected Button closeBtn;

	protected java.util.Map<String, String> ddFilters = new HashMap<>();
	protected Label ddFilterLabel;
	protected ACCombo ddVars;
	protected ACCombo ddVals;
	protected HashMap<String, java.util.List<String>> variablesData;
	protected static final String CHOOSE_VALUE_DEFAULT_STRING = "-Choose Value-";
	protected static final String CHOOSE_VARIABLE_DEFAULT_STRING = "-Choose Variable-";
	protected Button addFltrBtn;
	protected Button removeFlterBtn;
	protected Button clearFltrBtn;
	protected Button varsBtn;

	protected Label modeLabel;
	
	protected Button loadLogBtn;
	protected Button skipToStartBtn;
	protected Button SkipToEndBtn;

	protected String logPath = "";

	protected Button genLogBtn;
	protected Label genLoglabel;

	protected Button addBpBtn;
	protected Button removeBpBtn;
	protected Button removeAllBpsBtn;
	protected Button reachabilityBtn;

	protected TableViewer bpTableViewer;
	protected java.util.List<IBreakpoint> bpList;
	protected int lastSelectedBpIndex = -1;

	protected WalkDialog(Shell parentShell, String dialogTitle) {
		super(parentShell);
		setShellStyle(SWT.MIN | SWT.MAX | SWT.RESIZE);
		this.dialogTitle = dialogTitle;
	}

	protected abstract void connectToBackend();


	/**
	 * Returns the initial size of the dialog window that will be opened. The
	 * default value is Full-HD resolution standard.
	 */
	@Override
	protected Point getInitialSize() {
		return new Point(1920, 1080);
	}
	
	@Override
	public void create() {
		super.create();
		setTitle(dialogTitle);
		setMessage("Execute the symbolic controller by choosing next moves", IMessageProvider.INFORMATION);
		// initially disable OK button
		getButton(IDialogConstants.OK_ID).setEnabled(false);
		shell = this.getShell();
		shell.addListener(SWT.Close, new Listener() {
			@Override
			public void handleEvent(Event event) {
				cancelPressed();
			}
		});
		connectToBackend();
	}
	
	// Buttons Bar
	
	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		parent.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		createGuidedButtons(parent);
		createSpacer(parent);
		createNavigationButtons(parent);
	}

	private void createGuidedButtons(Composite parent) {
		loadLogBtn = createButton(parent, IDialogConstants.OPEN_ID, "Load Log", true);
		skipToStartBtn = createButton(parent, IDialogConstants.STOP_ID, "Skip to start", false);
		SkipToEndBtn = createButton(parent, IDialogConstants.SKIP_ID, "Skip to end", false);
		getButton(IDialogConstants.SKIP_ID).setEnabled(false);
		getButton(IDialogConstants.STOP_ID).setEnabled(false);
	}

	private void createSpacer(Composite parent) {
		// Create a spacer label
		Label spacer = new Label(parent, SWT.NONE);
		spacer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		// Update layout of the parent composite to count the spacer
		GridLayout layout = (GridLayout) parent.getLayout();
		layout.numColumns++;
		layout.makeColumnsEqualWidth = false;
	}

	private void createNavigationButtons(Composite parent) {
		resetBtn = createButton(parent, IDialogConstants.FINISH_ID, "Reset", false);
		stepBackButton = createButton(parent, IDialogConstants.BACK_ID, "Step Back", true);
		stepBackButton.setEnabled(false);
		nextButton = createButton(parent, IDialogConstants.OK_ID, "Next Step", true);
		closeBtn = createButton(parent, IDialogConstants.CANCEL_ID, "Close", false);
	}

	// Dialog Area

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite body = (Composite) super.createDialogArea(parent);

		// Horizontal sash
		SashForm sashForm = createSashForm(body);

		createStepsComponent(sashForm);

		createBreakpointComponent(sashForm);
		return body;
	}

	private SashForm createSashForm(Composite body) {
		SashForm sashForm = new SashForm(body, SWT.HORIZONTAL);
		sashForm.setTouchEnabled(true);
		sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		return sashForm;
	}

	
	// Steps component
	
	private void createStepsComponent(SashForm sashForm) {
		Composite stepsComp = new Composite(sashForm, SWT.FILL);
		stepsComp.setLayout(new GridLayout(1, false));

		createTopLeftGroup(stepsComp);
		createStepsGroup(stepsComp);
	}

	private void createTopLeftGroup(Composite parent) {
		Group topLeftGroup = new Group(parent, SWT.NONE);
		topLeftGroup.setLayout(new FormLayout());
		GridData gd_group1 = new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1);
		gd_group1.heightHint = 140;
		gd_group1.widthHint = 1300;
		topLeftGroup.setLayoutData(gd_group1);
		topLeftGroup.setText("Filters");

		ddVars = new ACCombo(topLeftGroup, SWT.DROP_DOWN) {
			@Override
			protected void foundMatches(int numMatches) {
				if (numMatches == 1) { // Exact match was found.
					selectedVariablesCombo();
				} else {
					ddVals.setEnabled(false);
					ddVals.setText(CHOOSE_VALUE_DEFAULT_STRING);
				}
			}
		};
		FormData fd_variables = new FormData();
		fd_variables.left = new FormAttachment(0, 5);
		fd_variables.right = new FormAttachment(0, 230);
		fd_variables.top = new FormAttachment(0, 6);
		ddVars.setLayoutData(fd_variables);
		ddVars.setTouchEnabled(true);
		ddVars.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				selectedVariablesCombo();
			}
		});
		ddVars.setText(CHOOSE_VARIABLE_DEFAULT_STRING);
		
		ddVals = new ACCombo(topLeftGroup, SWT.DROP_DOWN);
		FormData fd_values = new FormData();
		fd_values.top = new FormAttachment(ddVars, 0, SWT.TOP);
		fd_values.left = new FormAttachment(0, 240);
		fd_values.right = new FormAttachment(0, 465);
		ddVals.setLayoutData(fd_values);
		ddVals.setTouchEnabled(true);
		ddVals.setEnabled(false);
		ddVals.setText(CHOOSE_VALUE_DEFAULT_STRING);

		addFltrBtn = new Button(topLeftGroup, SWT.NONE);
		FormData fd_addButton = new FormData();
		fd_addButton.right = new FormAttachment(0, 580);
		fd_addButton.top = new FormAttachment(ddVars, -2, SWT.TOP);
		fd_addButton.left = new FormAttachment(0, 480);
		addFltrBtn.setLayoutData(fd_addButton);

		addFltrBtn.setTouchEnabled(true);
		addFltrBtn.setText("Add");

		removeFlterBtn = new Button(topLeftGroup, SWT.NONE);
		FormData fd_removeButton = new FormData();
		fd_removeButton.right = new FormAttachment(0, 690);
		fd_removeButton.left = new FormAttachment(0, 590);
		fd_removeButton.top = new FormAttachment(ddVars, -2, SWT.TOP);
		removeFlterBtn.setLayoutData(fd_removeButton);
		removeFlterBtn.setText("Remove");
		

		clearFltrBtn = new Button(topLeftGroup, SWT.NONE);
		FormData fd_btnClear = new FormData();
		fd_btnClear.right = new FormAttachment(0, 800);
		fd_btnClear.top = new FormAttachment(ddVars, -2, SWT.TOP);
		fd_btnClear.left = new FormAttachment(0, 700);
		clearFltrBtn.setLayoutData(fd_btnClear);
		clearFltrBtn.setText("Clear");
		
		
		ddFilterLabel = new Label(topLeftGroup, SWT.NONE);
		FormData fd_filterLabel = new FormData();
		fd_filterLabel.top = new FormAttachment(ddVars, 6);
		fd_filterLabel.left = new FormAttachment(ddVars, 0, SWT.LEFT);
		ddFilterLabel.setLayoutData(fd_filterLabel);
		ddFilterLabel.setText("Dropdown filter: None");	
		
	    filterText = new Text(topLeftGroup, SWT.BORDER);
	    FormData fd_filterText = new FormData();
	    fd_filterText.right = new FormAttachment(0, 230);
	    fd_filterText.left = new FormAttachment(0, 5);
	    fd_filterText.top = new FormAttachment(ddVars, 50, SWT.TOP);
	    filterText.setLayoutData(fd_filterText);
	    
	    textFilterBtn = new Button(topLeftGroup,  SWT.NONE);
		FormData fd_textFilterBtn = new FormData();
		fd_textFilterBtn.right = new FormAttachment(0, 320);
		fd_textFilterBtn.left = new FormAttachment(0, 240);
		fd_textFilterBtn.top = new FormAttachment(ddVars, 50, SWT.TOP);
		textFilterBtn.setLayoutData(fd_textFilterBtn);
		textFilterBtn.setText("Apply");
		
		varsBtn = new Button(topLeftGroup, SWT.NONE);
		FormData fd_btnVars = new FormData();
		fd_btnVars.right = new FormAttachment(0, 1290);
		fd_btnVars.left = new FormAttachment(0, 1170);
		fd_btnVars.top = new FormAttachment(ddVars, -2, SWT.TOP);
		varsBtn.setLayoutData(fd_btnVars);
		varsBtn.setText("Variables...");
		
		
		
		modeLabel = new Label(topLeftGroup, SWT.NONE);
		FormData fd_mode = new FormData();
		fd_mode.width = 1200;
		fd_mode.top = new FormAttachment(ddVars, 75, SWT.TOP);
		fd_mode.left = new FormAttachment(ddVars, 0, SWT.LEFT);
		modeLabel.setLayoutData(fd_mode);
		FontData[] fDModeLbl = modeLabel.getFont().getFontData();
		fDModeLbl[0].setHeight(10);
		modeLabel.setFont( new Font(null,fDModeLbl[0]));

		


	}
	
protected abstract void selectedVariablesCombo();


	private void createStepsGroup(Composite parent) {
		stepsGrp = new Group(parent, SWT.NONE);
		GridData stepsGrp_GD = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
		stepsGrp_GD.heightHint = 900;
		stepsGrp_GD.widthHint = 1300;
		stepsGrp.setLayoutData(stepsGrp_GD);
		stepsGrp.setText("Steps");
		stepsGrp.setLayout(new GridLayout(1, false));

		createPossibleStepsList(stepsGrp);
		possibleStepsLabel = new Label(stepsGrp, SWT.LEFT);

		loadMoreStepsBtn = new Button(stepsGrp, SWT.NONE);
		loadMoreStepsBtn.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		loadMoreStepsBtn.setText("Load More Steps");

		soFar = new Label(stepsGrp, SWT.LEFT);
		soFar.setText("Steps so far:");
		GridData gridData = new GridData(1300, 300);
		gridData.grabExcessVerticalSpace = true;
		gridData.verticalAlignment = SWT.TOP;
		gridData.grabExcessHorizontalSpace = true;
		gridData.horizontalAlignment = GridData.FILL;

		TableLayout stepsLayoutableLayout = new TableLayout();
		stepsLayoutableLayout.addColumnData(new ColumnWeightData(1));
		stepsLayoutableLayout.addColumnData(new ColumnWeightData(9));

		consoleTableViewer = new TableViewer(stepsGrp, SWT.BORDER | SWT.READ_ONLY | SWT.V_SCROLL);
		Table consoleTable = consoleTableViewer.getTable();
		GridData consoleTable_GD = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
		consoleTable_GD.heightHint = 500;
		consoleTable.setLayoutData(consoleTable_GD);
		consoleTableViewer.getTable().setLinesVisible(true);
		consoleTableViewer.getTable().setHeaderVisible(true);
		consoleTableViewer.getTable().setLayout(stepsLayoutableLayout);

		TableViewerColumn playerColumn = new TableViewerColumn(consoleTableViewer, SWT.NONE);
		playerColumn.getColumn().setText("Player");
		playerColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((String) element).split(":", 2)[0];
			}
		});

		TableViewerColumn stateColumn = new TableViewerColumn(consoleTableViewer, SWT.NONE);
		stateColumn.getColumn().setText("State");
		stateColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((String) element).split(":", 2)[1];
			}
		});

		consoleTableViewer.setContentProvider(ArrayContentProvider.getInstance());

		genLogBtn = new Button(stepsGrp, SWT.CHECK);
		genLogBtn.setSelection(true);
		genLogBtn.setText("Generate Log");

		genLoglabel = new Label(stepsGrp, SWT.NONE);
		GridData gd = new GridData();
		genLoglabel.setLayoutData(gd);
	}

	private void createPossibleStepsList(Composite container) {
		GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1);
		gridData.heightHint = 200;
		gridData.grabExcessHorizontalSpace = true;
		gridData.horizontalAlignment = SWT.FILL;

		possibleStepsSWT = new List(container, SWT.V_SCROLL | SWT.H_SCROLL);
		possibleStepsSWT.setTouchEnabled(true);

		possibleStepsSWT.setLayoutData(gridData);

		possibleStepsSWT.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				validate();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				validate();
			}
			
			private void validate() {
				String[] selection = possibleStepsSWT.getSelection();

				if (selection == null || selection.length == 0) {
					getButton(IDialogConstants.OK_ID).setEnabled(false);
				} else if (selection.length == 1) {
					getButton(IDialogConstants.OK_ID).setEnabled(true);
				}
			}
		});

		possibleStepsSWT.addMouseListener(new MouseListener() {

			@Override
			public void mouseUp(MouseEvent e) {
			}

			@Override
			public void mouseDown(MouseEvent e) {
			}

			@Override
			public void mouseDoubleClick(MouseEvent e) {
				String[] selection = possibleStepsSWT.getSelection();
				if (selection.length == 1) {
					okPressed();
				}
			}
		});

 

//		variablesData = getVariablesAndValues(possibleStepsList);
//		variables.removeAll();
//		for (String item : variablesData.keySet())
//			variables.add(item);
	}

	/**
	 * Selects a step from the next steps list.
	 * 
	 * @param i - the index of the next step in the list (Uppermost index is 0).
	 */
	protected void selectStepAtIndex(int i) {
		this.selectedOptionIndex = i;
		if (i >= 0) {
			possibleStepsSWT.select(i);
		}
	}

	protected void updatePossibleStepsList(java.util.List<String> possibleSteps) {
		possibleStepsList = possibleSteps;
	}

	

	
	// Breakpoints component
	
	private void createBreakpointComponent(SashForm sashForm) {
		// create table
		TableLayout breakpointTableLayout = new TableLayout();
		breakpointTableLayout.addColumnData(new ColumnWeightData(6));
		breakpointTableLayout.addColumnData(new ColumnWeightData(1));

		Composite breakComp = new Composite(sashForm, SWT.NONE);
		sashForm.setWeights(new int[] { 1250, 549 });
		breakComp.setLayout(new FillLayout(SWT.VERTICAL));

		Group bpGroup = new Group(breakComp, SWT.CENTER);
		bpGroup.setText("Breakpoints");
		bpGroup.setLayout(new GridLayout());
		createBreakpointTable(bpGroup, breakpointTableLayout);
		createBreakpointButtons(bpGroup);
	}

	private void createBreakpointTable(Group bpGroup, TableLayout breakpointTableLayout) {		
		bpTableViewer = new TableViewer(bpGroup, SWT.BORDER | SWT.FULL_SELECTION);
		bpTableViewer.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		bpTableViewer.getTable().setLinesVisible(true);
		bpTableViewer.getTable().setHeaderVisible(true);
		// * breakpointTableViewer.getTable().getColumn(0).setResizable(false);
		bpTableViewer.getTable().setLayout(breakpointTableLayout);
		bpTableViewer.getTable().setToolTipText("");

		// create breakpoint column
		TableViewerColumn bpColumm = new TableViewerColumn(bpTableViewer, SWT.NONE);
		bpColumm.getColumn().setText("Breakpoint");
		bpColumm.setLabelProvider(new BpEqProvider());
		bpColumm.setEditingSupport(new BpEditing(bpTableViewer));

		// create value column
		TableViewerColumn bpValColumn = new TableViewerColumn(bpTableViewer, SWT.NONE);
		bpValColumn.getColumn().setText("Value");
		bpValColumn.setLabelProvider(new BpValProvider());

		bpTableViewer.setContentProvider(ArrayContentProvider.getInstance());

		ColumnViewerEditorActivationStrategy actSupport = new ColumnViewerEditorActivationStrategy(bpTableViewer) {
			@Override
			protected boolean isEditorActivationEvent(ColumnViewerEditorActivationEvent event) {
				return event.eventType == ColumnViewerEditorActivationEvent.TRAVERSAL
						|| event.eventType == ColumnViewerEditorActivationEvent.MOUSE_DOUBLE_CLICK_SELECTION
						|| event.eventType == ColumnViewerEditorActivationEvent.PROGRAMMATIC;
			}
		};

		// add double click support
		int feature = ColumnViewerEditor.TABBING_HORIZONTAL | ColumnViewerEditor.TABBING_MOVE_TO_ROW_NEIGHBOR
				| ColumnViewerEditor.TABBING_VERTICAL | ColumnViewerEditor.KEYBOARD_ACTIVATION;
		TableViewerEditor.create(bpTableViewer, actSupport, feature);
	}

	private void createBreakpointButtons(Group bpGroup) {
		// add buttons
		Composite BreakBtnComp = new Composite(bpGroup, SWT.NONE);
		BreakBtnComp.setLayout(new RowLayout(SWT.HORIZONTAL));

		addBpBtn = new Button(BreakBtnComp, SWT.NONE);
		addBpBtn.setTouchEnabled(true);
		addBpBtn.setText("Add Breakpoint");

		removeBpBtn = new Button(BreakBtnComp, SWT.NONE);
		removeBpBtn.setTouchEnabled(true);
		removeBpBtn.setText("Remove Breakpoint");
		removeBpBtn.setEnabled(false);

		removeAllBpsBtn = new Button(BreakBtnComp, SWT.NONE);
		removeAllBpsBtn.setTouchEnabled(true);
		removeAllBpsBtn.setText("Remove All Breakpoints");

		reachabilityBtn = new Button(BreakBtnComp, SWT.NONE);
		reachabilityBtn.setTouchEnabled(true);
		reachabilityBtn.setEnabled(false);
		reachabilityBtn.setText("Check Reachability");
	}

	protected void updateRemoveAllBpsBtn() {
		removeAllBpsBtn.setEnabled(bpTableViewer.getTable().getItemCount() > 0);
	}

	private class BpEqProvider extends ColumnLabelProvider {
		@Override
		public String getText(Object element) {
			return ((IBreakpoint) element).getExpression();
		}

		@Override
		public void update(final ViewerCell cell) {
			IBreakpoint bp = (IBreakpoint) cell.getElement();
			cell.setText(bp.getExpression());

			if (!bp.eval().isValid()) {
				cell.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_RED));
				return;
			}

			cell.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));

			if (bp.eval().isTrue()) {
				cell.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_GREEN));
				bpTableViewer.setSelection(StructuredSelection.EMPTY);
				if (removeBpBtn != null) {
					removeBpBtn.setEnabled(false);
				}
			} else if (bp.isMarkedForReach()) {
				cell.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_YELLOW));
				bpTableViewer.setSelection(StructuredSelection.EMPTY);
			} else {
				cell.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
			}
		}

		@Override
		public Color getForeground(Object element) {
			return ((IBreakpoint) element).eval().isValid() ? Display.getDefault().getSystemColor(SWT.COLOR_BLACK)
					: Display.getDefault().getSystemColor(SWT.COLOR_RED);
		}
	}

	private class BpEditing extends EditingSupport {
		private TextCellEditor cellEditor;

		public BpEditing(TableViewer viewer) {
			super(viewer);
			cellEditor = new TextCellEditor(viewer.getTable());
		}

		@Override
		protected boolean canEdit(Object element) {
			return true;
		}

		@Override
		protected CellEditor getCellEditor(Object element) {
			return cellEditor;
		}

		@Override
		protected Object getValue(Object element) {
			if (IPersistent.EXP_PLACEHOLDER.equals(((IBreakpoint) element).getExpression())) {
				return "";
			}
			return ((IBreakpoint) element).getExpression();
		}

		@Override
		protected void setValue(Object element, Object value) {
			IBreakpoint bp = ((IBreakpoint) element);
			replaceBreakpoint(bp.getId(), value.toString());
		}	
	}
	
	protected abstract void replaceBreakpoint(int id, String string);
	
	private class BpValProvider extends ColumnLabelProvider {
		@Override
		public String getText(Object element) {
			IBreakpoint bp = (IBreakpoint) element;
			return bp.eval().toString();
		}

		@Override
		public Color getForeground(Object element) {
			return ((IBreakpoint) element).isMarkedForReach() ? new Color(Display.getCurrent(), new RGB(255, 0, 0))
					: new Color(Display.getCurrent(), new RGB(0, 0, 0));
		}
	}
}