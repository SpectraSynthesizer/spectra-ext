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
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerEditor;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationEvent;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationStrategy;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TableViewerEditor;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
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
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.wb.swt.SWTResourceManager;

import tau.smlab.syntech.richcontrollerwalker.ControllerConstants;

public class WalkDialog extends TitleAreaDialog {
	private String dialogTitle;
	protected java.util.List<String> possibleStepsList;
	public ArrayList<String> consoleContent;
	public Button loadMoreSteps;
	private List possibleStepsSWT;
	// public Text consoleTableViewer;
	public TableViewer consoleTableViewer;
	public Label nextStepLabel;
	private Indicator logIndicator; // Indicates if walking on a log file.
	private Label logLabel;
	private Indicator reachabilityIndicator; // Indicates if reachability mode is enabled.
	private Label reachabilityLabel;
	protected Button stepBackButton;
	private Label filterLabel;
	private ACCombo variables;
	private ACCombo values;
	private String selectedStep;
	private int selectedStepIdx;
	private Button loadLogBtn;
	private Button reachabilityBtn;
	private Button nextStateButton;
	private Button removeWatchBtn;
	private Button removeBreakpointBtn;
	protected Button genLogBtn;
	private Label genLoglabel;
	private java.util.Map<String, String> filters = new HashMap<>();
	private Group stepsGrp;
	private Label soFar;
	private final String chooseValueDefaultString = "-Choose Value-";
	private final String chooseVariableDefaultString = "-Choose Variable-";
	private Composite body;
	private Color watchDfltClr = SWTResourceManager.getColor(SWT.COLOR_WHITE);

	protected String logPath = null;
	private HashMap<String, ArrayList<String>> variablesData;

	// test commit
	public WalkDialog(Shell parentShell, String dialogTitle, java.util.List<String> possibleStepsList,
			java.util.List<String> consoleContent) {
		super(parentShell);
		setShellStyle(SWT.MIN | SWT.MAX | SWT.RESIZE);
		this.dialogTitle = dialogTitle;
		this.possibleStepsList = new ArrayList<>(possibleStepsList);
		this.consoleContent = new ArrayList<>(consoleContent);
	}

	public TableViewer watchTableViewer;
	public CheckboxTableViewer breakpointTableViewer;
	public ArrayList<Watch> watchModel;
	public ArrayList<Breakpoint> breakpointModel;
	private int lastSelectedWatch = -1;
	private int lastSelectedBreakpoint = -1;
	private Shell shell;
	public int watchCounter = 0;
	public int bpCounter = 0;

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

		updateGenLogLabel();
		guiWasInit();
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		parent.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		// Load log button
		loadLogBtn = createButton(parent, IDialogConstants.OPEN_ID, "Load Log", true);
		createButton(parent, IDialogConstants.STOP_ID, "Skip to start", false);
		createButton(parent, IDialogConstants.SKIP_ID, "Skip to end", false);
		getButton(IDialogConstants.SKIP_ID).setEnabled(false);
		getButton(IDialogConstants.STOP_ID).setEnabled(false);

		// Create a spacer label
		Label spacer = new Label(parent, SWT.NONE);
		spacer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		// Update layout of the parent composite to count the spacer
		GridLayout layout = (GridLayout) parent.getLayout();
		layout.numColumns++;
		layout.makeColumnsEqualWidth = false;

		// State control buttons.
		createButton(parent, IDialogConstants.FINISH_ID, "Reset", false);
		stepBackButton = createButton(parent, IDialogConstants.BACK_ID, "Step Back", true);
		stepBackButton.setEnabled(false);
		nextStateButton = createButton(parent, IDialogConstants.OK_ID, "Next Step", true);
		createButton(parent, IDialogConstants.CANCEL_ID, "Cancel", false);
	}

	@Override
	protected void okPressed() {
		saveInput();
		setReturnCode(OK);
	}

	@Override
	protected void buttonPressed(int buttonId) {
		switch (buttonId) {
		case IDialogConstants.OK_ID: {
			okPressed();
			break;
		}
		case IDialogConstants.CANCEL_ID: {
			cancelPressed();
			break;
		}
		case IDialogConstants.OPEN_ID: {
			openPressed();
			break;
		}
		case IDialogConstants.BACK_ID: {
			backPressed();
			break;
		}
		case IDialogConstants.FINISH_ID: {
			resetPressed();
			break;
		}
		case IDialogConstants.SKIP_ID: {
			skipToEndPressed();
			break;
		}
		case IDialogConstants.STOP_ID: {
			skipToStartPressed();
			break;
		}
		}
		if (IDialogConstants.CANCEL_ID != buttonId)
			pressedOnAButton();
	}

	private void skipToStartPressed() {
		skipToStart();
	}

	private void skipToEndPressed() {
		skipToEnd();
	}

	protected void resetPressed() {
		resetSteps();
	}

	protected void backPressed() {
		saveInput();
		setReturnCode(OK);
	}

	protected void updateGenLogLabel() {
		genLoglabel.setText("Writing log to: " + getGenLogPath());
	}

	protected void resetGenLogLabel() {
		genLoglabel.setText("");
	}

	protected String getGenLogPath() {
		// Overridden
		return "";
	}

	// Browse Dialog
	protected void openPressed() {
		if (!isFollowingLog()) {
			FileDialog dlg = new FileDialog(getShell());
			dlg.setFilterPath(this.getWorkingDir());
			dlg.setText("File Dialog");
			String dir = dlg.open(); // get the direction
			if (dir != null) {
				logPath = dir;
				// logLoaded = true;

				if (loadLog(logPath)) {
					loadLogBtn.setText("Exit Log");
					loadLogBtn.update();
				}
			}
		} else {
			exitLog();
		}

		updateNextStepLabelText();
	}

	protected String getWorkingDir() {
		return null;
	}

	protected void exitLog() {
		// Overridden
		loadLogBtn.setText("Load Log");
		loadLogBtn.update();
		exitLogOrReachabilityState();
		// logLoaded = false;
	}

	// save content of the fields because they get disposed
	// as soon as the Dialog closes
	private void saveInput() {
		if (possibleStepsSWT.getSelection().length > 0) {
			selectedStep = possibleStepsSWT.getSelection()[0];
			selectedStepIdx = possibleStepsSWT.getSelectionIndex();
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	protected Control createDialogArea(Composite parent) {
		body = (Composite) super.createDialogArea(parent);

		// Horizontal sash
		SashForm sashForm = new SashForm(body, SWT.HORIZONTAL);
		sashForm.setTouchEnabled(true);
		sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Composite stepsComp = new Composite(sashForm, SWT.FILL);

		stepsComp.setLayout(new GridLayout(1, false));

		Group group = new Group(stepsComp, SWT.NONE);
		group.setLayout(new FormLayout());
		GridData gd_group1 = new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1);
		gd_group1.heightHint = 130;
		gd_group1.widthHint = 1300;
		group.setLayoutData(gd_group1);
		group.setText("Filters");

		variables = new ACCombo(group, SWT.DROP_DOWN) {
			@Override
			protected void foundMatches(int numMatches) {
				if (numMatches == 1) { // Exact match was found.
					selectedVariablesCombo();
				} else {
					values.setEnabled(false);
					values.setText(chooseValueDefaultString);
				}
			}
		};
		FormData fd_variables = new FormData();
		fd_variables.left = new FormAttachment(0, 5);
		fd_variables.right = new FormAttachment(0, 230);
		fd_variables.top = new FormAttachment(0, 6);
		variables.setLayoutData(fd_variables);
		variables.setTouchEnabled(true);
		variables.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				selectedVariablesCombo();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				// Auto-generated method stub
			}
		});

		values = new ACCombo(group, SWT.DROP_DOWN);
		FormData fd_values = new FormData();
		fd_values.top = new FormAttachment(variables, 0, SWT.TOP);
		fd_values.left = new FormAttachment(0, 240);
		fd_values.right = new FormAttachment(0, 465);
		values.setLayoutData(fd_values);
		values.setTouchEnabled(true);
		values.setEnabled(false);
		values.setText(chooseValueDefaultString);

		Button addButton = new Button(group, SWT.NONE);
		FormData fd_addButton = new FormData();
		fd_addButton.right = new FormAttachment(0, 580);
		fd_addButton.top = new FormAttachment(variables, -2, SWT.TOP);
		fd_addButton.left = new FormAttachment(0, 480);
		addButton.setLayoutData(fd_addButton);
		addButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (!variables.isTextValid() || !values.isTextValid())
					return;
				filters.put(variables.getText(), values.getText());
				if (filterPossibleStepsList() > 0)
					selectStepAtIndex(0);
				else
					selectStepAtIndex(-1);
			}
		});
		addButton.setTouchEnabled(true);
		addButton.setText("Add");

		Button removeButton = new Button(group, SWT.NONE);
		FormData fd_removeButton = new FormData();
		fd_removeButton.right = new FormAttachment(0, 690);
		fd_removeButton.left = new FormAttachment(0, 590);
		fd_removeButton.top = new FormAttachment(variables, -2, SWT.TOP);
		removeButton.setLayoutData(fd_removeButton);
		removeButton.setText("Remove");
		removeButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				filters.put(variables.getText(), null);
				if (filterPossibleStepsList() > 0)
					selectStepAtIndex(0);
				else
					selectStepAtIndex(-1);
			}
		});

		filterLabel = new Label(group, SWT.NONE);
		FormData fd_filterLabel = new FormData();
		fd_filterLabel.top = new FormAttachment(variables, 6);
		fd_filterLabel.left = new FormAttachment(variables, 0, SWT.LEFT);
		filterLabel.setLayoutData(fd_filterLabel);
		filterLabel.setText("Filters: None");

		logIndicator = new Indicator(group, SWT.NO_REDRAW_RESIZE, getShell().getDisplay());
		FormData fd_canvas = new FormData();
		fd_canvas.top = new FormAttachment(filterLabel, 6);
		fd_canvas.left = new FormAttachment(variables, 0, SWT.LEFT);
		fd_canvas.right = new FormAttachment(0, 31);
		fd_canvas.bottom = new FormAttachment(0, 94);
		logIndicator.setLayoutData(fd_canvas);

		logLabel = new Label(group, SWT.NONE);
		FormData logLabelFD = new FormData();
		logLabelFD.bottom = new FormAttachment(filterLabel, 34, SWT.BOTTOM);
		logLabelFD.top = new FormAttachment(filterLabel, 6);
		logLabelFD.left = new FormAttachment(logIndicator, 6);
		logLabel.setLayoutData(logLabelFD);
		setFollowingLogLocal(null); // By default, we are not following a log file.

		stepsGrp = new Group(stepsComp, SWT.NONE);
		GridData stepsGrp_GD = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
		stepsGrp_GD.heightHint = 900;
		stepsGrp_GD.widthHint = 1300;
		stepsGrp.setLayoutData(stepsGrp_GD);
		stepsGrp.setText("Steps");
		stepsGrp.setLayout(new GridLayout(1, false));

		nextStepLabel = new Label(stepsGrp, SWT.LEFT);
		updateNextStepLabelText();
		createPossibleStepsList(stepsGrp);
		variables.setText(chooseVariableDefaultString);

		Button btnClear = new Button(group, SWT.NONE);
		logLabelFD.right = new FormAttachment(btnClear, 0, SWT.RIGHT);
		btnClear.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				filters.clear();
				updatePossibleStepsListView(possibleStepsList, false);
				selectStepAtIndex(0);
			}
		});
		FormData fd_btnClear = new FormData();
		fd_btnClear.right = new FormAttachment(0, 800);
		fd_btnClear.top = new FormAttachment(variables, -2, SWT.TOP);
		fd_btnClear.left = new FormAttachment(0, 700);
		btnClear.setLayoutData(fd_btnClear);
		btnClear.setText("Clear");

		reachabilityIndicator = new Indicator(group, SWT.NO_REDRAW_RESIZE, getShell().getDisplay());
		FormData fd_indicator = new FormData();
		fd_indicator.bottom = new FormAttachment(logIndicator, 34, SWT.BOTTOM);
		fd_indicator.right = new FormAttachment(variables, 26);
		fd_indicator.top = new FormAttachment(logIndicator, 6);
		fd_indicator.left = new FormAttachment(variables, 0, SWT.LEFT);
		reachabilityIndicator.setLayoutData(fd_indicator);

		reachabilityLabel = new Label(group, SWT.NONE);
		FormData reachabilityLabelFD = new FormData();
		reachabilityLabelFD.right = new FormAttachment(logLabel, 0, SWT.RIGHT);
		reachabilityLabelFD.left = new FormAttachment(reachabilityIndicator, 6);
		reachabilityLabelFD.bottom = new FormAttachment(logIndicator, 34, SWT.BOTTOM);
		reachabilityLabelFD.top = new FormAttachment(logIndicator, 6);
		reachabilityLabel.setLayoutData(reachabilityLabelFD);
		setFollowingReachabilityLocal(); // By default, reachability mode is off.

		loadMoreSteps = new Button(stepsGrp, SWT.NONE);
		loadMoreSteps.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		loadMoreSteps.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				loadMoreSteps();
			}
		});
		loadMoreSteps.setText("Load More Steps");

		soFar = new Label(stepsGrp, SWT.LEFT);
		soFar.setText("Steps so far:");
		GridData gridData = new GridData(1300, 300);
		gridData.grabExcessVerticalSpace = true;
		gridData.verticalAlignment = SWT.FILL;
		gridData.grabExcessHorizontalSpace = true;
		gridData.horizontalAlignment = GridData.FILL;

		TableLayout stepsLayoutableLayout = new TableLayout();
		stepsLayoutableLayout.addColumnData(new ColumnWeightData(1));
		stepsLayoutableLayout.addColumnData(new ColumnWeightData(9));

		consoleTableViewer = new TableViewer(stepsGrp, SWT.BORDER | SWT.READ_ONLY | SWT.V_SCROLL);
		Table consoleTable = consoleTableViewer.getTable();
		consoleTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		consoleTableViewer.getTable().setLinesVisible(true);
		consoleTableViewer.getTable().setHeaderVisible(true);
		consoleTableViewer.getTable().setLayout(stepsLayoutableLayout);

		TableViewerColumn playerColumn = new TableViewerColumn(consoleTableViewer, SWT.NONE);
		playerColumn.getColumn().setText("Player");
		playerColumn.setLabelProvider(new LogPlayerProvider());

		TableViewerColumn stateColumn = new TableViewerColumn(consoleTableViewer, SWT.NONE);
		stateColumn.getColumn().setText("State");
		stateColumn.setLabelProvider(new LogStateProvider());

		consoleTableViewer.setContentProvider(ArrayContentProvider.getInstance());
		consoleTableViewer.setInput(consoleContent);

		// consoleTableViewer = new Text(stepsGrp, SWT.READ_ONLY | SWT.V_SCROLL |
		// SWT.MULTI);
		// consoleTableViewer.setBackground(SWTResourceManager.getColor(SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW));
		// consoleTableViewer.setTouchEnabled(true);
		// appendToConsoleArea(null);
		// consoleTableViewer.setLayoutData(gridData);

		genLogBtn = new Button(stepsGrp, SWT.CHECK);
		genLogBtn.setSelection(true);
		genLogBtn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				changeLogState();
			}
		});
		genLogBtn.setText("Generate Log");

		genLoglabel = new Label(stepsGrp, SWT.NONE);
		updateGenLogLabel();

		// Create Breakpoint and Watches
		// *******************************************************************
		TableLayout watchLayoutableLayout = new TableLayout();
		watchLayoutableLayout.addColumnData(new ColumnWeightData(6));
		watchLayoutableLayout.addColumnData(new ColumnWeightData(1));

		int feature = ColumnViewerEditor.TABBING_HORIZONTAL | ColumnViewerEditor.TABBING_MOVE_TO_ROW_NEIGHBOR
				| ColumnViewerEditor.TABBING_VERTICAL | ColumnViewerEditor.KEYBOARD_ACTIVATION;

		// create table
		TableLayout breakpointTableLayout = new TableLayout();
		breakpointTableLayout.addColumnData(new ColumnWeightData(1));
		breakpointTableLayout.addColumnData(new ColumnWeightData(16));

		Composite watchBreakComp = new Composite(sashForm, SWT.NONE);
		watchBreakComp.setLayout(new FillLayout(SWT.VERTICAL));

		// Second part - Watches ************************************************
		Group comp1 = new Group(watchBreakComp, SWT.CENTER);
		comp1.setText("Watches");
		comp1.setLayout(new GridLayout());

		watchTableViewer = new TableViewer(comp1, SWT.BORDER | SWT.FULL_SELECTION);
		Table table = watchTableViewer.getTable();
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		watchTableViewer.getTable().setLinesVisible(true);
		watchTableViewer.getTable().setHeaderVisible(true);
		watchTableViewer.getTable().setLayout(watchLayoutableLayout);
		// watchTableViewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL,
		// true, true));

		// column 2 - equation
		TableViewerColumn watchColumn = new TableViewerColumn(watchTableViewer, SWT.NONE);
		watchColumn.getColumn().setText("Watch");
		watchColumn.setLabelProvider(new WatchEqProvider());
		watchColumn.setEditingSupport(new WatchEqEditing(watchTableViewer));

		// column 3 - value
		TableViewerColumn valueColumn = new TableViewerColumn(watchTableViewer, SWT.NONE);
		valueColumn.getColumn().setText("Value");
		valueColumn.setLabelProvider(new WatchValProvider());

		// table viewer settings
		watchTableViewer.setContentProvider(ArrayContentProvider.getInstance());
		watchTableViewer.setInput(createWatchModel());
		watchTableViewer.getTable().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent e) {
				int y = watchTableViewer.getTable().getHeaderHeight();
				for (int i = 0; i < watchTableViewer.getTable().getItemCount(); i++) {
					y += watchTableViewer.getTable().getItemHeight();
					if (e.y <= y) {
						lastSelectedWatch = i;
						removeWatchBtn.setEnabled(true);
						break;
					}
				}
			}

		});

		Composite watchBtnComp = new Composite(comp1, SWT.NONE);
		watchBtnComp.setLayout(new RowLayout(SWT.HORIZONTAL));

		Button addWatchBtn = new Button(watchBtnComp, SWT.NONE);
		addWatchBtn.setTouchEnabled(true);
		addWatchBtn.setText("Add Watch");
		addWatchBtn.addSelectionListener(new SelectionListener() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				watchModel.add(new Watch(ControllerConstants.EXP_PLACEHOLDER));
				watchTableViewer.refresh();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {

			}
		});
		removeWatchBtn = new Button(watchBtnComp, SWT.NONE);
		removeWatchBtn.setTouchEnabled(true);
		removeWatchBtn.setText("Remove Watch");
		removeWatchBtn.setEnabled(false);
		removeWatchBtn.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (lastSelectedWatch >= 0 && lastSelectedWatch < watchModel.size()) {
					if (removeWatch(watchModel.get(lastSelectedWatch).id)) {
						watchModel.remove(lastSelectedWatch);
						watchTableViewer.refresh();
						lastSelectedWatch = -1;
						removeWatchBtn.setEnabled(false);
						backupWatches();
					}
				}
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				// Auto-generated method stub

			}
		});

		// add double click support to viewer
		ColumnViewerEditorActivationStrategy actSupport = new ColumnViewerEditorActivationStrategy(watchTableViewer) {
			@Override
			protected boolean isEditorActivationEvent(ColumnViewerEditorActivationEvent event) {
				return event.eventType == ColumnViewerEditorActivationEvent.TRAVERSAL
						|| event.eventType == ColumnViewerEditorActivationEvent.MOUSE_DOUBLE_CLICK_SELECTION
						|| event.eventType == ColumnViewerEditorActivationEvent.PROGRAMMATIC;
			}
		};

		TableViewerEditor.create(watchTableViewer, actSupport, feature);

		// Third part - Breakpoints ************************************************

		// add Breakpoint listener
		ICheckStateListener breakpointListener = new ICheckStateListener() {
			@Override
			public void checkStateChanged(CheckStateChangedEvent event) {
				Breakpoint bp = ((Breakpoint) event.getElement());
				if (!bp.valid) {
					breakpointTableViewer.setChecked(bp, false);
					return;
				}
				if (event.getChecked()) {
					enableBreakpoint(bp.id);
					bp.enabled = true;
					updateBreakpoint(bp.id, getBreakpointValue(bp));
					breakpointTableViewer.refresh();
				} else {
					disableBreakpoint(bp.id);
					bp.enabled = false;
					updateBreakpoint(bp.id, false);
					breakpointTableViewer.refresh();
				}
			}
		};

		Group comp2 = new Group(watchBreakComp, SWT.CENTER);
		comp2.setText("Breakpoints");
		comp2.setLayout(new GridLayout());

		breakpointTableViewer = new CheckboxTableViewer(comp2, SWT.BORDER | SWT.FULL_SELECTION | SWT.CHECK);
		Table table_1 = breakpointTableViewer.getTable();
		table_1.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		breakpointTableViewer.getTable().setLinesVisible(true);
		breakpointTableViewer.getTable().setHeaderVisible(true);
		breakpointTableViewer.getTable().getColumn(0).setResizable(false);
		breakpointTableViewer.getTable().setLayout(breakpointTableLayout);
		breakpointTableViewer.addCheckStateListener(breakpointListener);
		breakpointTableViewer.getTable().setToolTipText("");

		// create breakpoint column
		TableViewerColumn watchColumn2 = new TableViewerColumn(breakpointTableViewer, SWT.NONE);
		watchColumn2.getColumn().setText("Breakpoint");
		watchColumn2.setLabelProvider(new BreakpointProvider());
		watchColumn2.setEditingSupport(new BreakpointEditing(breakpointTableViewer));

		breakpointTableViewer.setContentProvider(ArrayContentProvider.getInstance());
		breakpointTableViewer.setInput(createBreakpointModel());

		// update last selected watch field
		breakpointTableViewer.getTable().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent e) {
				int y = breakpointTableViewer.getTable().getHeaderHeight();
				for (int i = 0; i < breakpointTableViewer.getTable().getItemCount(); i++) {
					y += breakpointTableViewer.getTable().getItemHeight();
					if (e.y <= y) {
						lastSelectedBreakpoint = i;
						removeBreakpointBtn.setEnabled(true);
						reachabilityBtn.setEnabled(true);
						break;
					}
				}
			}
		});

		// add double click support
		TableViewerEditor.create(breakpointTableViewer, actSupport, feature);

		// add buttons
		Composite BreakBtnComp = new Composite(comp2, SWT.NONE);
		BreakBtnComp.setLayout(new RowLayout(SWT.HORIZONTAL));

		Button addBreakpointBtn = new Button(BreakBtnComp, SWT.NONE);
		addBreakpointBtn.setTouchEnabled(true);
		addBreakpointBtn.setText("Add Breakpoint");
		addBreakpointBtn.addSelectionListener(new SelectionListener() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				// Add breakpoint button has been pressed.
				breakpointModel.add(new Breakpoint(ControllerConstants.EXP_PLACEHOLDER));
				breakpointTableViewer.refresh();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				// Auto-generated method stub

			}
		});

		removeBreakpointBtn = new Button(BreakBtnComp, SWT.NONE);
		removeBreakpointBtn.setTouchEnabled(true);
		removeBreakpointBtn.setText("Remove Breakpoint");
		removeBreakpointBtn.setEnabled(false);
		removeBreakpointBtn.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				// .....tln("break-remove button pressed!");
				if (lastSelectedBreakpoint >= 0 && lastSelectedBreakpoint < breakpointModel.size()) {
					if (isBreakpointRemoved(breakpointModel.get(lastSelectedBreakpoint).id)) {
						breakpointModel.remove(lastSelectedBreakpoint);
						breakpointTableViewer.refresh();
						removeBreakpointBtn.setEnabled(false);
						reachabilityBtn.setEnabled(false);
						lastSelectedBreakpoint = -1;
					}
				}
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				// Auto-generated method stub

			}
		});

		reachabilityBtn = new Button(BreakBtnComp, SWT.NONE);
		reachabilityBtn.setTouchEnabled(true);
		reachabilityBtn.setEnabled(false);
		reachabilityBtn.setText("Check Reachability");
		sashForm.setWeights(new int[] { 1250, 549 });
		reachabilityBtn.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (!isReachability()) {
					reachabilityBtn.setText("Exit Reachability");
					Breakpoint bp = breakpointModel.get(lastSelectedBreakpoint);
					if (!bp.isBreakpointChecked(bp)) {
						enableBreakpoint(bp.id);
						bp.enabled = true;
						updateBreakpoint(bp.id, getBreakpointValue(bp));
						breakpointTableViewer.setChecked(bp, true);
					}
					if (bp.reached) {
						MessageBox dialog = new MessageBox(shell, SWT.OK);
						dialog.setText("Reachability Status");
						dialog.setMessage("Breakpoint is already reached.");
						reachabilityBtn.setText("Check Reachability");
						dialog.open();
						return;
					}
					if (!checkReachability(lastSelectedBreakpoint)) {
						MessageBox dialog = new MessageBox(shell, SWT.OK);
						dialog.setText("Reachability Status");
						dialog.setMessage("Could not reach from current state.");
						reachabilityBtn.setText("Check Reachability");
						dialog.open();
						return;
					}

					MessageBox dialog = new MessageBox(shell, SWT.ICON_QUESTION | SWT.OK | SWT.CANCEL);
					dialog.setText("Reachability Status");
					dialog.setMessage("Successfully reached breakpoint state.\nEnter reachability mode?");
					if (dialog.open() == SWT.CANCEL) {
						reachabilityBtn.setText("Check Reachability");
						return; // Cancelled by user.
					}

					bp.markForReach = true;
					breakpointTableViewer.refresh();
					startReachability();
					// isReachability = true;
				} else {
					exitReachability();
				}
				updateNextStepLabelText();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				// Auto-generated method stub

			}
		});

		return body;
	}

	protected void selectedVariablesCombo() {
		if (variables.getText() != null) {
			values.removeAll();
			for (String item : variablesData.get(variables.getText())) {
				values.add(item);
			}
			values.select(0);
			values.setEnabled(true);
		}
	}

	protected boolean isReachability() {
		return false;
	}

	protected boolean isFollowingLog() {
		return false;
	}

	protected void updateNextStepLabelText() {
		if (isLogOrReachabilityActive()) {
			nextStepLabel.setText("Current State:");
		} else {
			nextStepLabel.setText("Next " + (isENVTurn() ? "ENV" : "SYS") + " step:");
		}
	}

	protected boolean isLogOrReachabilityActive() {
		return false;
	}

	protected void changeLogState() {

	}

	protected int filterPossibleStepsList() {
		java.util.List<String> res = new ArrayList<>();
		for (String s : possibleStepsList) {
			if (filterString(s))
				res.add(s);
		}

		this.updatePossibleStepsListView(res, false);
		return res.size();
	}

	protected boolean getBreakpointValue(Breakpoint bp) {
		return true;
	}

	// Breakpoint Table Elements**************************************************
	public class Breakpoint {
		public String eq;
		public boolean enabled = false;
		public boolean reached = false;
		public int id;
		public boolean markForReach = false;
		public boolean valid = true;

		public Breakpoint(int id, String eq) {
			this.id = id;
			this.eq = eq;
		}

		public boolean isBreakpointChecked(Breakpoint bp) {
			return bp.enabled;
		}

		public Breakpoint(String eq) {
			this.id = bpCounter;
			this.eq = eq;
			bpCounter++;
		}
	}

	private ArrayList<Breakpoint> createBreakpointModel() {
		breakpointModel = loadBreakpoints();
		return breakpointModel;
	}

	protected ArrayList<Breakpoint> loadBreakpoints() {
		return new ArrayList<>();
	}

	private class BreakpointProvider extends ColumnLabelProvider {
		@Override
		public String getText(Object element) {
			return ((Breakpoint) element).eq;
		}

		@Override
		public void update(final ViewerCell cell) {
			Breakpoint bp = (Breakpoint) cell.getElement();
			cell.setText(bp.eq);

			if (!bp.valid) {
				cell.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_RED));
				return;
			}

			cell.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));

			if (bp.reached) {
				cell.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_GREEN));
				breakpointTableViewer.setSelection(StructuredSelection.EMPTY);
				if (removeBreakpointBtn != null) {
					removeBreakpointBtn.setEnabled(false);
				}
			} else if (bp.markForReach) {
				cell.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_YELLOW));
				breakpointTableViewer.setSelection(StructuredSelection.EMPTY);
			} else {
				cell.setBackground(watchDfltClr);
			}
		}

		@Override
		public Color getForeground(Object element) {
			return ((Breakpoint) element).valid ? Display.getDefault().getSystemColor(SWT.COLOR_BLACK)
					: Display.getDefault().getSystemColor(SWT.COLOR_RED);
		}
	}

	private class BreakpointEditing extends EditingSupport {
		private TextCellEditor cellEditor;

		public BreakpointEditing(TableViewer viewer) {
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
			if (ControllerConstants.EXP_PLACEHOLDER.equals(((Breakpoint) element).eq)) {
				return "";
			}
			return ((Breakpoint) element).eq;
		}

		@Override
		protected void setValue(Object element, Object value) {
			Breakpoint bp = ((Breakpoint) element);
			// String last = bp.eq;
			bp.eq = value.toString();
			if (addBreakpoint(bp.id, bp.eq)) {
				bp.enabled = true;
				bp.valid = true;
				bp.reached = getBreakpointValue(bp);
				breakpointTableViewer.setChecked(element, true);
			} else {
				bp.enabled = false;
				bp.valid = false;
				bp.reached = false;
				breakpointTableViewer.setChecked(element, false);
				breakpointTableViewer.refresh();
			}
			getViewer().update(element, null);
		}
	}

	// Watches Table Elements******************************************************
	private ArrayList<Watch> createWatchModel() {
		watchModel = loadWatches();
		return watchModel;
	}

	protected ArrayList<Watch> loadWatches() {
		return new ArrayList<>();
	}

	protected void backupWatches() {

	}

	public class Watch {
		public boolean enabled = false;
		public String eq;
		public String val = "";
		public int id;
		public boolean markForChange = false;

		public Watch(int id, String eq) {
			this.id = id;
			this.eq = eq;
		}

		public Watch(String eq) {
			this.id = watchCounter;
			this.eq = eq;
			watchCounter++;
		}
	}

	private class WatchEqProvider extends ColumnLabelProvider {
		@Override
		public String getText(Object element) {
			return ((Watch) element).eq;
		}
	}

	private class WatchEqEditing extends EditingSupport {
		private TextCellEditor cellEditor;

		public WatchEqEditing(TableViewer viewer) {
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
			if (ControllerConstants.EXP_PLACEHOLDER.equals(((Watch) element).eq)) {
				return "";
			}
			return ((Watch) element).eq;
		}

		@Override
		protected void setValue(Object element, Object value) {
			// try to add watch
			Watch watch = ((Watch) element);
			watch.eq = value.toString();
			if (addWatch(watch.id, watch.eq)) {
				watch.enabled = true;
				watch.val = getWatchEval(watch.id);
			} else {
				watch.enabled = false;
				watch.val = "N/A";
			}
			getViewer().update(element, null);
			// backupWatches();
		}
	}

	private class WatchValProvider extends ColumnLabelProvider {
		@Override
		public String getText(Object element) {
			return ((Watch) element).val;
		}

		@Override
		public Color getForeground(Object element) {
			return (((Watch) element).markForChange) ? new Color(Display.getCurrent(), new RGB(255, 0, 0))
					: new Color(Display.getCurrent(), new RGB(0, 0, 0));
		}
	}

	// ********************************************************************************

	private class LogPlayerProvider extends ColumnLabelProvider {
		@Override
		public String getText(Object element) {
			return ((String) element).split(":", 2)[0];
		}
	}

	private class LogStateProvider extends ColumnLabelProvider {
		@Override
		public String getText(Object element) {
			return ((String) element).split(":", 2)[1];
		}
	}

	public void appendToConsoleArea(java.util.List<String> newContent) {
		// StringBuilder sb = new StringBuilder();
		if (newContent != null) {
			consoleContent.addAll(newContent);
		}
		consoleTableViewer.getTable().setTopIndex(consoleTableViewer.getTable().getItemCount() - 1);
		consoleTableViewer.refresh();

		// for (String string: consoleContent) {
		// sb.append(string);
		// sb.append(System.lineSeparator());
		// }
		// consoleTableViewer.setText(sb.toString());
		// consoleTableViewer.setTopIndex(consoleTableViewer.getLineCount() - 1);
		// consoleTableViewer.update();
	}

	/**
	 * Changes the color of the parent composite to black and its children
	 * recursively.
	 * 
	 * @param parent
	 *            - parent composite.
	 * @param foreClr
	 *            - foreground color.
	 * @param backClr
	 *            - background color.
	 */
	public void changeColor(Composite parent, Color foreClr, Color backClr) {
		for (Control child : parent.getChildren()) {
			child.setBackground(foreClr);
			child.setForeground(backClr);
			if (child instanceof Composite) {
				changeColor((Composite) child, foreClr, backClr);
			}
		}
	}

	protected String getWatchEval(int idx) {
		return "";
	}

	protected void enableBreakpoint(int bpID) {

	}

	protected void disableBreakpoint(int bpID) {

	}

	protected boolean isBreakpointRemoved(int bpIndex) {
		return false;
	}

	public void removeLastConsoleLine() {
		if (consoleContent.size() <= 0) {
			return;
		}
		// StringBuilder sb = new StringBuilder();

		// remove seperator + log line
		consoleContent.remove(consoleContent.size() - 1);
		consoleTableViewer.getTable().setTopIndex(consoleTableViewer.getTable().getItemCount() - 1);
		consoleTableViewer.refresh();

		// update console view
		// for (String string: consoleContent) {
		// sb.append(string);
		// sb.append(System.lineSeparator());
		// }
		// consoleTableViewer.setText(sb.toString());
		// consoleTableViewer.setTopIndex(consoleTableViewer.getLineCount() - 1);
		// consoleTableViewer.update();
	}

	private void createPossibleStepsList(Composite container) {
		GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
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
		});

		possibleStepsSWT.addMouseListener(new MouseListener() {

			@Override
			public void mouseUp(MouseEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void mouseDown(MouseEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void mouseDoubleClick(MouseEvent e) {
				String[] selection = possibleStepsSWT.getSelection();
				if (selection.length == 1) {
					okPressed();
				}
			}
		});

		for (String item : possibleStepsList) {
			possibleStepsSWT.add(item);

		}

		variablesData = getVariablesAndValues(possibleStepsList);
		variables.removeAll();
		for (String item : variablesData.keySet())
			variables.add(item);
	}

	private void validate() {
		String[] selection = possibleStepsSWT.getSelection();

		if (selection == null || selection.length == 0) {
			getButton(IDialogConstants.OK_ID).setEnabled(false);
		} else if (selection.length == 1) {
			getButton(IDialogConstants.OK_ID).setEnabled(true);
		}
	}

	public String getSelectedStep() {
		return this.selectedStep;
	}

	public int getSelectedStepIndex() {
		return this.selectedStepIdx;
	}

	public void updatePossibleStepsList(java.util.List<String> possibleSteps) {
		possibleStepsList = possibleSteps;
		filters.clear();
	}

	public void updatePossibleStepsListView(java.util.List<String> possibleSteps, boolean isNewSet) {
		possibleStepsSWT.removeAll();
		if (possibleSteps != null) {
			if (isNewSet) {
				variablesData = getVariablesAndValues(possibleStepsList);
				variables.removeAll();
				for (String item : variablesData.keySet()) {
					variables.add(item);
				}
				variables.setText(chooseVariableDefaultString);
				values.setText(chooseValueDefaultString);
				values.setEnabled(false);
			}

			for (String item : possibleSteps) {
				possibleStepsSWT.add(item);
			}

			String fText = "";
			boolean first = true;
			for (String k : filters.keySet()) {
				if (filters.get(k) == null)
					continue;
				if (!first) {
					fText += ", ";
				}
				fText += k + ":" + filters.get(k);
				first = false;
			}

			if (fText.isEmpty()) {
				fText = "None";
			}

			filterLabel.setText("Filters: " + fText);
			filterLabel.getParent().layout();
		}
	}

	/**
	 * Filters a string, according to all of the active filter.
	 * 
	 * @param s
	 *            - the string to be filtered.
	 * @returns true iff all the filters hold for the given string.
	 */
	public boolean filterString(String s) {
		for (String f : filters.keySet()) {
			if (filters.get(f) != null && !s.contains(f + ":" + filters.get(f)))
				return false;
		}
		return true;
	}

	/**
	 * Calculate for each (of ENV/SYS) variables, its possible values.
	 * 
	 * @param possibleSteps
	 *            - the possible next steps list.
	 * @returns a hash-map of the variables as keys and their possible values as
	 *          values.
	 */
	public HashMap<String, ArrayList<String>> getVariablesAndValues(java.util.List<String> possibleSteps) {
		HashMap<String, ArrayList<String>> res = new HashMap<String, ArrayList<String>>();
		possibleSteps.forEach((s) -> {
			if (!s.equals("T")) {
				String[] stateVals = s.replace("<", "").replace(">", "").replace(" ", "").split(",");
				for (String asgmt : stateVals) {
					String[] asgm = asgmt.split(":");
					if (res.containsKey(asgm[0])) {
						if (!res.get(asgm[0]).contains(asgm[1])) {
							res.get(asgm[0]).add(asgm[1]);
						}
					} else {
						ArrayList<String> val = new ArrayList<>();
						val.add(asgm[1]);
						res.put(asgm[0], val);
					}
				}
			}
		});
		return res;
	}

	/**
	 * Allows choosing a log file to be loaded.
	 * 
	 * @param path
	 *            - the path of the log file to be loaded.
	 * @return - true iff the file was loaded successfully.
	 */
	protected boolean loadLog(String path) {
		// Overridden
		return false;
	}

	/**
	 * Clears the steps-so-far console.
	 */
	protected void clearConsole() {
		consoleContent = new ArrayList<String>();
		consoleTableViewer.getTable().setTopIndex(consoleTableViewer.getTable().getItemCount() - 1);
		consoleTableViewer.refresh();
		// consoleTableViewer.setText("");
		// consoleTableViewer.setTopIndex(consoleTableViewer.getLineCount() - 1);
		// consoleTableViewer.update();
	}

	/**
	 * Removes characters from the steps-so-far console.
	 * 
	 * @param numLines
	 *            - number of lines to remove from the console line.
	 */
	public void removeCharsFromConsole(int numLines) { // Lines, not chars.
		if (consoleContent == null || consoleContent.isEmpty())
			return;

		for (int i = 0; i < numLines; i++) {
			consoleContent.remove(consoleContent.size() - 1);
		}
		consoleTableViewer.getTable().setTopIndex(consoleTableViewer.getTable().getItemCount() - 1);
		consoleTableViewer.refresh();

		// StringBuilder sb = new StringBuilder();
		// for (String string: consoleContent) {
		// sb.append(string);
		// sb.append(System.lineSeparator());
		// }
		// consoleTableViewer.setText(sb.toString());
		// consoleTableViewer.setTopIndex(consoleTableViewer.getLineCount() - 1);
		// consoleTableViewer.update();
	}

	/**
	 * Selects a step from the next steps list.
	 * 
	 * @param i
	 *            - the index of the next step in the list (Uppermost index is 0).
	 */
	protected void selectStepAtIndex(int i) {
		this.selectedStepIdx = i;
		if (i >= 0) {
			this.selectedStep = this.possibleStepsList.get(this.selectedStepIdx);
			possibleStepsSWT.select(i);
		} else {
			nextStateButton.setEnabled(false);
			stepBackButton.setEnabled(false);
		}
	}

	/**
	 * Updates the log status in the GUI (indicator and label).
	 */
	protected void setFollowingLogLocal(String logFileName) {
		if (isFollowingLog()) {
			logIndicator.setState(true);
			logLabel.setText("Following log file: " + logFileName);
		} else {
			logIndicator.setState(false);
			logLabel.setText("Log mode is OFF.");
		}
	}

	/**
	 * Updates the reachability status in the GUI (indicator and label).
	 */
	protected void setFollowingReachabilityLocal() {
		if (isReachability()) {
			reachabilityIndicator.setState(true);
			reachabilityLabel.setText("Reachability mode is ON.");
		} else {
			reachabilityIndicator.setState(false);
			reachabilityLabel.setText("Reachability mode is OFF.");
		}
	}

	/**
	 * Returns the initial size of the dialog window that will be opened. The
	 * default value is Full-HD resolution standard.
	 */
	protected Point getInitialSize() {
		return new Point(1920, 1080);
	}

	/**
	 * Resets the walk to the first step.
	 */
	protected void resetSteps() {
		// Overridden.
	}

	/**
	 * If log mode is active, move to the last state of the log file.
	 */
	protected void skipToEnd() {
		// Overridden.
	}

	/**
	 * If log mode is active, move to the first state of the log file.
	 */
	protected void skipToStart() {
		// Overridden.
	}

	/**
	 * Displays a warning that resetting walk will clear the currently generated
	 * log.
	 * 
	 * @returns the choice of the dialog.
	 */
	protected int resetDialog() {
		MessageBox dialog = new MessageBox(shell, SWT.ICON_QUESTION | SWT.OK | SWT.CANCEL | SWT.CENTER);
		dialog.setText("Reset Walk");
		dialog.setMessage("Resetting walk will clear the currently generated log. Continue anyway?");
		return dialog.open();
	}

	/**
	 * Displays a warning that log generation before system's step may result in
	 * loss of current state.
	 * 
	 * @returns the choice of the dialog.
	 */
	protected int exitLogDialog() {
		MessageBox dialog = new MessageBox(shell, SWT.ICON_QUESTION | SWT.OK | SWT.CANCEL | SWT.CENTER);
		dialog.setText("Exit Log State");
		dialog.setMessage(
				"Disabling log generation before SYS completed a step may result in loss of current state. Continue anyway?");
		return dialog.open();
	}

	/**
	 * Displays a warning that a new log has to be generated in order to step back.
	 * 
	 * @returns the choice of the dialog.
	 */
	protected int stepBackLogGenerationDialog() {
		MessageBox dialog = new MessageBox(shell, SWT.ICON_QUESTION | SWT.OK | SWT.CANCEL | SWT.CENTER);
		dialog.setText("Generate New Log");
		dialog.setMessage("Back-tracking will result in creation of new log. Continue anyway?");
		return dialog.open();
	}

	/**
	 * Displays a warning that a new log has to be generated in order to load a log
	 * file.
	 * 
	 * @returns the choice of the dialog.
	 */
	protected int loadLogGenerationDialog() {
		MessageBox dialog = new MessageBox(shell, SWT.ICON_QUESTION | SWT.OK | SWT.CANCEL | SWT.CENTER);
		dialog.setText("Generate New Log");
		dialog.setMessage("Loading log will result in creation of new log. Continue anyway?");
		return dialog.open();
	}

	/**
	 * Called when log mode or reachability mode is enabled, and is used for
	 * updating the GUI labels to display state information.
	 */
	protected void enterLogOrReachabilityState() {
		stepsGrp.setText("States");
		soFar.setText("States so far:");
		stepBackButton.setText("Previous State");
		nextStateButton.setText("Next State");
	}

	/**
	 * Called when log mode or reachability mode is disabled, and is used for
	 * updating the GUI labels to display step information.
	 */
	protected void exitLogOrReachabilityState() {
		if (!this.isFollowingLog() && !this.isReachability()) {
			stepsGrp.setText("Steps");
			soFar.setText("Steps so far:");
			stepBackButton.setEnabled(false);
			stepBackButton.setText("Step Back");
			nextStateButton.setText("Next Step");
		}
	}

	/**
	 * Exits reachability mode in the SymbolicWalker.
	 */
	protected void stopPlayingReachability() {
	}

	/**
	 * Exits reachability mode in the GUI and in the SymbolicWalker.
	 */
	protected void exitReachability() {
		// Overridden
		stopPlayingReachability();
		reachabilityBtn.setText("Check Reachability");

		// clear all break points marks for reach
		for (int i = 0; i < breakpointModel.size(); i++) {
			breakpointModel.get(i).markForReach = false;
		}

		// refresh table
		breakpointTableViewer.refresh();
	}

	/**
	 * Returns true iff it is environment's turn.
	 * 
	 * @returns true iff it is environment's turn
	 */
	protected boolean isENVTurn() {
		// Overridden.
		return false;
	}

	/**
	 * Enables or disables the back button.
	 * 
	 * @param enable
	 *            - true iff the back button should be enabled.
	 */
	protected void setBackButtonEnable(boolean enable) {
		stepBackButton.setEnabled(enable);
	}

	/**
	 * Called when pressing on any button, except from the close button which closes
	 * the dialog.
	 */
	protected void pressedOnAButton() {
		// Overridden.
	}

	/**
	 * Invokes symbolicWalker's addWatch method.
	 * 
	 * @param idx
	 *            - the index of the watch to add.
	 * @param formula
	 *            - the value of the watch (an expression).
	 * @returns if the watch was added successfully.
	 */
	protected boolean addWatch(int idx, String formula) {
		// Overriden.
		return false;
	}

	/**
	 * Invokes symbolicWalker's removeWatch method.
	 * 
	 * @param idx
	 *            - index of the watch to remove.
	 * @returns if the watch was removed successfully.
	 */
	protected boolean removeWatch(int idx) {
		// Overriden.
		return false;
	}

	/**
	 * Invokes symbolicWalker's addBreakpoint method.
	 * 
	 * @param idx
	 *            - the index of the breakpoint to add.
	 * @param formula
	 *            - the value of the breakpoint (an expression).
	 * @returns if the breakpoint was added successfully.
	 */
	protected boolean addBreakpoint(int idx, String formula) {
		// Overriden.
		return false;
	}

	/**
	 * Updates breakpoints' data structure and refreshes the GUI, according to each
	 * breakpoint's evaluation result.
	 * 
	 * @param idx
	 *            - index of the breakpoint in the breakpoints table.
	 * @param reached
	 *            - does the breakpoint hold.
	 */
	protected void updateBreakpoint(int idx, boolean reached) {
		// Overidden
	}

	/**
	 * Starts reachability mode.
	 */
	protected void startReachability() {
		// Overidden
	}

	/**
	 * Calls symbolicWalker's checkReachability function.
	 * 
	 * @param bpIndex
	 * @return checkReachability's result.
	 */
	protected boolean checkReachability(int bpIndex) {
		// Overidden
		return false;
	}

	/**
	 * Shows more available steps, if there are any. This is useful if there are
	 * more steps available than the number of steps that can be displayed, which
	 * are limited to the number that is configured in the preferences panel.
	 */
	protected void loadMoreSteps() {
		// Overidden
	}

	/**
	 * Called once only, when the GUI was initialized.
	 */
	protected void guiWasInit() {
		// Overriden
	}
}