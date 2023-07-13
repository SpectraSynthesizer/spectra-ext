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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ICheckStateProvider;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.ViewerDropAdapter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.dnd.TransferData;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;

import tau.smlab.syntech.richcontrollerwalker.options.DisplayedOptions;
import tau.smlab.syntech.richcontrollerwalker.ui.action.IVarConfiguration;
import tau.smlab.syntech.richcontrollerwalker.util.Mod;

public class VarsTable {

	private final List<DisplayVar> vars = new ArrayList<>();
	private IVarConfiguration varConfig = null;
	private Mod mod;
	private TableViewer tableViewer;

	private static final String DONT_CARE = "Don't Care";
	private static final String FIXED_PREFIX = "Fixed: ";

	public VarsTable(Composite parent, String title, IVarStateChangedListener varChangeListener) {
		this.tableViewer = createTable(parent, title, varChangeListener);
	}

	public VarsTable(Composite parent) {
		this.tableViewer = createTable(parent, null, false);
	}

	public VarsTable(IVarConfiguration varConfig, DisplayedOptions dispOpts, Mod mod, boolean showInfo) {
		reset(varConfig, dispOpts, mod, showInfo);
	}

	public List<DisplayVar> getVars() {
		return vars;
	}

	public void reset(IVarConfiguration varConfig, Collection<String> dontCares, Map<String, String> fixed, Mod mod,
			boolean showInfo) {
		this.varConfig = varConfig;
		this.mod = mod;
		vars.clear();
		Collection<String> hidden = varConfig.getHidden();
		for (String name : varConfig.getVariablesByModule(mod)) {
			vars.add(new DisplayVar(name, !hidden.contains(name), showInfo ? getInfo(dontCares, fixed, name) : ""));
		}

		update();
	}

	public void reset(IVarConfiguration varConfig, DisplayedOptions dispOpts, Mod mod, boolean showInfo) {
		reset(varConfig, dispOpts != null ? dispOpts.getDontCares() : new ArrayList<String>(),
				dispOpts != null ? dispOpts.getFixed() : new HashMap<String, String>(), mod, showInfo);
	}

	public void resetOrder() {
		List<String> ordered = varConfig.getVariablesByModule(mod);
		vars.sort(new Comparator<DisplayVar>() {

			@Override
			public int compare(DisplayVar o1, DisplayVar o2) {
				return ordered.indexOf(o1.name) - ordered.indexOf(o2.name);
			}
		});
		tableViewer.refresh();
	}

	/**
	 * Create a CheckboxTableViewer in the given parent for this component.
	 */
	public void createTableComponent(Composite parent, String title) {
		this.tableViewer = createTable(parent, title, true);
		update();
	}

	private TableViewer createTable(Composite parent, String title, boolean hasCheckbox) {
		IVarStateChangedListener onChange = hasCheckbox ? new IVarStateChangedListener() {
			@Override
			public void varStateChanged(DisplayVar var) {
				return;
			}
		} : null;
		return createTable(parent, title, onChange);
	}

	private boolean shouldBeGray(DisplayVar dv) {
		return (varConfig.getHideFixed() && dv.isFixed()) || (varConfig.getHideDontCares() && dv.isDontCare());
	}

	private TableViewer createTableViewer(Composite parent, IVarStateChangedListener varChangeListener) {
		boolean hasCheckbox = varChangeListener != null;
		if (hasCheckbox) {
			CheckboxTableViewer tableViewer = CheckboxTableViewer.newCheckList(parent, SWT.BORDER | SWT.FULL_SELECTION);
			tableViewer.setCheckStateProvider(new ICheckStateProvider() {

				@Override
				public boolean isGrayed(Object element) {
					if (element instanceof DisplayVar) {
						return shouldBeGray((DisplayVar) element);
					}
					return false;
				}

				@Override
				public boolean isChecked(Object element) {
					if (isGrayed(element)) {
						return true;
					}

					if (element instanceof DisplayVar) {
						return ((DisplayVar) element).isChecked;
					}

					return false;
				}
			});

			ICheckStateListener varListener = new ICheckStateListener() {

				@Override
				public void checkStateChanged(CheckStateChangedEvent event) {
					DisplayVar dv = ((DisplayVar) event.getElement());
					if (shouldBeGray(dv)) {
						tableViewer.refresh();
						return;
					}
					dv.isChecked = event.getChecked();

					tableViewer.refresh();
					varChangeListener.varStateChanged(dv);
				}
			};

			tableViewer.addCheckStateListener(varListener);

			TableViewerColumn checkColumn = new TableViewerColumn(tableViewer, SWT.NONE);
			checkColumn.setLabelProvider(new EmptyProvider());
			checkColumn.getColumn().setResizable(false);

			return tableViewer;
		} else {
			TableViewer tableViewer = new TableViewer(parent, SWT.BORDER | SWT.FULL_SELECTION);
			return tableViewer;
		}
	}

	/**
	 * Create a CheckboxTableViewer in the given parent with the given title. If the
	 * title is null, no title is added.
	 */
	private TableViewer createTable(Composite parent, String title, IVarStateChangedListener varChangeListener) {
		boolean hasCheckbox = varChangeListener != null;
		TableLayout layout = new TableLayout();
		if (hasCheckbox) {
			layout.addColumnData(new ColumnWeightData(1));
		}
		layout.addColumnData(new ColumnWeightData(6));
		layout.addColumnData(new ColumnWeightData(6));

		Composite comp = new Composite(parent, SWT.NONE);
		comp.setLayout(new GridLayout(1, false));
		comp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Composite container;
		if (title != null) {
			Group group = new Group(comp, SWT.CENTER);
			group.setText(title);
			group.setLayout(new GridLayout(1, false));
			group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			container = group;
		} else {
			container = comp;
		}

		TableViewer tableViewer = createTableViewer(container, varChangeListener);

		Table table = tableViewer.getTable();
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		tableViewer.getTable().setLinesVisible(true);
		tableViewer.getTable().setHeaderVisible(true);
		tableViewer.getTable().setLayout(layout);
		tableViewer.setLabelProvider(new EmptyProvider());
		tableViewer.getTable().setToolTipText("");

		TableViewerColumn nameColumn = new TableViewerColumn(tableViewer, SWT.NONE);
		nameColumn.getColumn().setText("Name");
		nameColumn.setLabelProvider(new VarsProvider(this));

		TableViewerColumn infoColumn = new TableViewerColumn(tableViewer, SWT.NONE);
		infoColumn.getColumn().setText("Info");
		infoColumn.setLabelProvider(new InfoProvider(this));

		tableViewer.setContentProvider(ArrayContentProvider.getInstance());

		addDragAndDropSupport(tableViewer);

		return tableViewer;
	}

	private void addDragAndDropSupport(TableViewer tableViewer) {
		Transfer[] types = new Transfer[] { TextTransfer.getInstance() };
		tableViewer.addDragSupport(DND.DROP_DEFAULT | DND.DROP_MOVE, types, new DragSourceAdapter() {
			@Override
			public void dragSetData(DragSourceEvent event) {
				// Get the selected items in the drag source
				DragSource dataSource = (DragSource) event.widget;
				Table table = (Table) dataSource.getControl();
				DisplayVar displayVar = (DisplayVar) table.getSelection()[0].getData();

				// Pass the index + variable name for validation
				event.data = String.join("\t", String.valueOf(table.getSelectionIndex()), displayVar.name);
			}
		});

		tableViewer.addDropSupport(DND.DROP_DEFAULT | DND.DROP_MOVE, types, new ViewerDropAdapter(tableViewer) {

			@Override
			public boolean performDrop(Object data) {
				DropTargetEvent dropEvent = getCurrentEvent();
				TableItem dropTarget = (TableItem) dropEvent.item;
				if (dropTarget == null) {
					return false;
				}
				String[] splitSource = ((String) data).split("\t");
				String sourceName = splitSource[1];
				int fromIndex = Integer.parseInt(splitSource[0]);

				if (fromIndex >= vars.size() || !vars.get(fromIndex).name.equals(sourceName)) {
					return false;
				}

				int toIndex = dropTarget.getParent().indexOf(dropTarget);

				// Move the variable in the array
				DisplayVar toMove = vars.remove(fromIndex);
				vars.add(toIndex, toMove);

				varConfig.setVariables(mod, vars.stream().map(v -> v.name).toList());

				tableViewer.refresh();

				return true;
			}

			@Override
			public boolean validateDrop(Object target, int operation, TransferData transferType) {
				return true;
			}
		});
	}

	/**
	 * Update the tableViewer's input to match the variables.
	 */
	private void update() {
		if (this.tableViewer == null) {
			return;
		}

		this.tableViewer.setInput(vars);
	}

	private String getInfo(Collection<String> dontCares, Map<String, String> fixed, String var) {
		if (fixed.containsKey(var)) {
			return FIXED_PREFIX + fixed.get(var);
		}
		if (dontCares.contains(var)) {
			return DONT_CARE;
		}
		return "";
	}

	public static class DisplayVar {
		public final String name;
		private boolean isChecked;
		public final String info;

		public DisplayVar(String name, boolean isChecked, String info) {
			this.name = name;
			this.isChecked = isChecked;
			this.info = info;
		}

		public boolean getChecked() {
			return isChecked;
		}

		public boolean isFixed() {
			return info.startsWith(FIXED_PREFIX);
		}

		public boolean isDontCare() {
			return info.equals(DONT_CARE);
		}
	}

	public static interface IVarStateChangedListener {
		public abstract void varStateChanged(DisplayVar var);
	}

	// Helper Display classes

	private static class MarkHiddenProvider extends ColumnLabelProvider {
		private VarsTable varsTable;

		public MarkHiddenProvider(VarsTable varsTable) {
			super();
			this.varsTable = varsTable;
		}

		@Override
		public Color getForeground(Object element) {
			if (!(element instanceof DisplayVar)) {
				return null;
			}

			DisplayVar dv = (DisplayVar) element;
			if (!dv.isChecked || varsTable.shouldBeGray(dv)) {
				return Display.getCurrent().getSystemColor(SWT.COLOR_DARK_GRAY);
			}
			return null;
		}

	}

	private static class EmptyProvider extends ColumnLabelProvider {
		@Override
		public String getText(Object element) {
			return "";
		}
	}

	private static class VarsProvider extends MarkHiddenProvider {

		public VarsProvider(VarsTable varsTable) {
			super(varsTable);
		}

		@Override
		public String getText(Object element) {
			return ((DisplayVar) element).name;
		}
	}

	private static class InfoProvider extends MarkHiddenProvider {

		public InfoProvider(VarsTable varsTable) {
			super(varsTable);
		}

		@Override
		public String getText(Object element) {
			return ((DisplayVar) element).info;
		}
	}
}
