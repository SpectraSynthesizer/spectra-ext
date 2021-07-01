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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;

import tau.smlab.syntech.richcontrollerwalker.bdds.BddUtil;
import tau.smlab.syntech.richcontrollerwalker.options.DisplayedOptions;
import tau.smlab.syntech.richcontrollerwalker.ui.action.IMask;
import tau.smlab.syntech.richcontrollerwalker.util.Mod;

public class VarsDialog extends TitleAreaDialog {
	private Shell shell;
	private CheckboxTableViewer envTableViewer;
	private CheckboxTableViewer sysTableViewer;

	private final List<DisplayVar> envVars = new ArrayList<>();
	private final List<DisplayVar> sysVars = new ArrayList<>();
	
	private final IMask mask;
	private final List<String> envNames;
	private final List<String> sysNames;
	private final DisplayedOptions dispOpts;
	//private final Map<String, Boolean> shownMap = new HashMap<String, Boolean>();

	public VarsDialog(Shell parent, IMask mask, DisplayedOptions dispOpts, Mod turn, boolean showInfo) {
		super(parent);
		this.mask = mask;
		this.dispOpts = dispOpts;
		setShellStyle(SWT.MIN | SWT.MAX | SWT.RESIZE);
		envNames = computeNamesList(Mod.ENV);
		sysNames = computeNamesList(Mod.SYS);
		Set<String> hidden = mask.getHidden();
		for (String name : envNames) {
			envVars.add(new DisplayVar(name, !hidden.contains(name), (showInfo && turn.equals(Mod.ENV)) ? getInfo(name): ""));
			//shownMap.put(name, !hidden.contains(name));
		}
		for (String name : sysNames) {
			sysVars.add(new DisplayVar(name, !hidden.contains(name), (showInfo && turn.equals(Mod.SYS)) ? getInfo(name): ""));
		}
	}
	
	
	private String getInfo(String var) {
		if (dispOpts == null) {
			return "";
		}
		if (dispOpts.getFixed().containsKey(var)) {
			return "Fixed: " + dispOpts.getFixed().get(var);
		} 
		if (dispOpts.getDontCares().contains(var)) {
			return "Don't Care";
		} 
		return "";
	}

	private List<String> computeNamesList(Mod mod) {
		List<String> l = BddUtil.getVarNamesListByModule(mod);
		l.sort(null);
		return Collections.unmodifiableList(l);
	}

	@Override
	public void create() {
		super.create();
		setTitle("Presentation Settings");
		// setMessage("Execute the symbolic controller by choosing next moves",
		// IMessageProvider.INFORMATION);
		// initially disable OK button
		// getButton(IDialogConstants.OK_ID).setEnabled(false);
		shell = this.getShell();
		shell.addListener(SWT.Close, new Listener() {
			@Override
			public void handleEvent(Event event) {
				cancelPressed();
			}
		});
	}

	@Override
	protected void cancelPressed() {
		super.cancelPressed();
	}

	@Override
	protected void okPressed() {
		List<String> hidden = new LinkedList<>();
		for (DisplayVar dv : envVars) {
			if (!dv.isChecked) {
				hidden.add(dv.name);
			}
		}
		for (DisplayVar dv : sysVars) {
			if (!dv.isChecked) {
				hidden.add(dv.name);
			}
		}
//		for (Entry<String, Boolean> e : shownMap.entrySet()) {
//			if (!e.getValue()) {
//				hidden.add(e.getKey());
//			}
//		}
		mask.setHidden(hidden);
		super.okPressed();
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite body = (Composite) super.createDialogArea(parent);
		// Horizontal sash
		SashForm sashForm = createSashForm(body);

		createTableComponent(sashForm, Mod.ENV);
		createTableComponent(sashForm, Mod.SYS);

		return body;
	}

	private SashForm createSashForm(Composite body) {
		SashForm sashForm = new SashForm(body, SWT.HORIZONTAL);
		sashForm.setTouchEnabled(true);
		sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		return sashForm;
	}

	private void createTableComponent(SashForm sashForm, Mod mod) {

		// create table
		TableLayout layout = new TableLayout();
		layout.addColumnData(new ColumnWeightData(1));
		layout.addColumnData(new ColumnWeightData(10));
		layout.addColumnData(new ColumnWeightData(10));

		Composite comp = new Composite(sashForm, SWT.NONE);
		//sashForm.setWeights(new int[] { 800, 800 });
		comp.setLayout(new FillLayout(SWT.VERTICAL));

		Group group = new Group(comp, SWT.CENTER);
		group.setText(mod.equals(Mod.ENV) ? "Env Variables" : "Sys Variables");
		group.setLayout(new GridLayout());

		CheckboxTableViewer tableViewer = new CheckboxTableViewer(group, SWT.BORDER | SWT.FULL_SELECTION | SWT.CHECK);

		ICheckStateListener varListener = new ICheckStateListener() {
			@Override
			public void checkStateChanged(CheckStateChangedEvent event) {
				DisplayVar dv = ((DisplayVar) event.getElement());
				tableViewer.setChecked(dv, event.getChecked());
				dv.isChecked = event.getChecked();
				//shownMap.put(varName, event.getChecked());
				tableViewer.refresh();
			}
		};

		Table table = tableViewer.getTable();
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		tableViewer.getTable().setLinesVisible(true);
		tableViewer.getTable().setHeaderVisible(true);
		tableViewer.getTable().getColumn(0).setResizable(false);
		tableViewer.getTable().setLayout(layout);
		tableViewer.addCheckStateListener(varListener);
		tableViewer.getTable().setToolTipText("");

		
		TableViewerColumn nameColumn = new TableViewerColumn(tableViewer, SWT.NONE);
		nameColumn.getColumn().setText("Name");
		nameColumn.setLabelProvider(new VarsProvider());
		
		TableViewerColumn infoColumn = new TableViewerColumn(tableViewer, SWT.NONE);
		infoColumn.getColumn().setText("Info");
		infoColumn.setLabelProvider(new InfoProvider());
	

		tableViewer.setContentProvider(ArrayContentProvider.getInstance());
		List<DisplayVar> input = mod.equals(Mod.ENV) ? envVars : sysVars;
		tableViewer.setInput(input);
		
		for (DisplayVar dv : input) {
			tableViewer.setChecked(dv, dv.isChecked);
		}

		if (mod.equals(Mod.ENV)) {
			envTableViewer = tableViewer;
		} else {
			sysTableViewer = tableViewer;
		}
	}

	private class VarsProvider extends ColumnLabelProvider {
		@Override
		public String getText(Object element) {
			return ((DisplayVar) element).name;
		}
	}
	
	private class InfoProvider extends ColumnLabelProvider {
		@Override
		public String getText(Object element) {
			return ((DisplayVar) element).info;
		}
	}

//	@Override
//	protected void createButtonsForButtonBar(Composite parent) {
////		parent.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
////		createGuidedButtons(parent);
////		createSpacer(parent);
////		createNavigationButtons(parent);
//	}

	/**
	 * Returns the initial size of the dialog window that will be opened. The
	 * default value is Full-HD resolution standard.
	 */
	@Override
	protected Point getInitialSize() {
		return new Point(960, 540);
	}
	
	
	private static class DisplayVar {
		private final String name;
		private  boolean isChecked;
		private final String info;
		
		public DisplayVar(String name, boolean isChecked, String info) {
			this.name = name;
			this.isChecked = isChecked;
			this.info = info;
		}
	}
}
