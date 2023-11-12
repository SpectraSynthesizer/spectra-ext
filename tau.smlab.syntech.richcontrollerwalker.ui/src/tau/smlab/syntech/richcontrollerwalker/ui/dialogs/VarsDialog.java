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

import java.util.LinkedList;
import java.util.List;

import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

import tau.smlab.syntech.richcontrollerwalker.options.DisplayedOptions;
import tau.smlab.syntech.richcontrollerwalker.ui.action.IVarConfiguration;
import tau.smlab.syntech.richcontrollerwalker.ui.dialogs.VarsTable.DisplayVar;
import tau.smlab.syntech.richcontrollerwalker.util.Mod;

public class VarsDialog extends TitleAreaDialog {
	private Shell shell;

	private VarsTable envTable;
	private VarsTable sysTable;
	private Button resetButton;
	private final IVarConfiguration varConfig;

	public VarsDialog(Shell parent, IVarConfiguration varConfig, DisplayedOptions dispOpts, Mod turn,
			boolean showInfo) {
		super(parent);
		this.varConfig = varConfig;
		setShellStyle(SWT.MIN | SWT.MAX | SWT.RESIZE);
		envTable = new VarsTable(varConfig, dispOpts, Mod.ENV, showInfo && turn.equals(Mod.ENV));
		sysTable = new VarsTable(varConfig, dispOpts, Mod.SYS, showInfo && turn.equals(Mod.SYS));
	}

	@Override
	public void create() {
		super.create();
		setTitle("Presentation Settings");
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
		for (DisplayVar dv : envTable.getVars()) {
			if (!dv.getChecked()) {
				hidden.add(dv.name);
			}
		}
		for (DisplayVar dv : sysTable.getVars()) {
			if (!dv.getChecked()) {
				hidden.add(dv.name);
			}
		}
		varConfig.setHidden(hidden);
		super.okPressed();
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite body = (Composite) super.createDialogArea(parent);
		// Horizontal sash
		SashForm sashForm = createSashForm(body);

		envTable.createTableComponent(sashForm, "Env Variables");
		sysTable.createTableComponent(sashForm, "Sys Variables");
		
		Composite btnComp = new Composite(body, SWT.NONE);
		RowLayout layout = new RowLayout(SWT.HORIZONTAL);
		layout.center = true;
		layout.justify = true;
		btnComp.setLayout(layout);
		
		resetButton = new Button(btnComp, SWT.NONE);
		resetButton.setText("Reset Variables Order");
		resetButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				varConfig.resetVariableOrder();
				envTable.resetOrder();
				sysTable.resetOrder();
			}
		});

		return body;
	}

	private SashForm createSashForm(Composite body) {
		SashForm sashForm = new SashForm(body, SWT.HORIZONTAL);
		sashForm.setTouchEnabled(true);
		sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		return sashForm;
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
