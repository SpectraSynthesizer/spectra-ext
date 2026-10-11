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

package tau.smlab.syntech.counterstrategy.ui.views;

import org.eclipse.gef.layout.algorithms.RadialLayoutAlgorithm;
import org.eclipse.gef.layout.algorithms.SpringLayoutAlgorithm;
import org.eclipse.gef.layout.algorithms.TreeLayoutAlgorithm;
import org.eclipse.gef.zest.fx.jface.ZestContentViewer;
import org.eclipse.gef.zest.fx.jface.ZestFxJFaceModule;
import org.eclipse.gef.zest.fx.ui.parts.ZestFxUiView;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.PlatformUI;

import tau.smlab.syntech.counterstrategy.ui.zestviewer.ZestLabelProvider;

/**
 * A superclass for all of the graph views in the plugin. It contains actions
 * for right click menu, and a zoom action.
 */

public abstract class CounterStrategyView extends ZestFxUiView {

	/** the container view of the graph. */
	/*
	 * TODO if you change the viewer (nodes/connections) you need to do:
	 * viewer.changeLauout(setLayout(),true);
	 */
	protected ZestContentViewer viewer;

	/*
	 * The content provider class is responsible for providing objects to the view.
	 * It can wrap existing objects in adapters or simply return objects as-is.
	 * These objects may be sensitive to the current input of the view, or ignore it
	 * and always show the same content (like Task List, for example).
	 */

	/** actions for clicking options */
	protected Action changeLayoutAction;
	protected Action doubleClickAction;

	/**
	 * counter that indicates which layout will be shown with the next "Change
	 * Layout" click, related to changeLayoutAction.
	 */
	protected int layout = 1;

	/**
	 * The constructor.
	 */
	public CounterStrategyView() {
	}

	/**
	 * This is a callback that will allow us to create the viewer and initialize it.
	 */
	public void createPartControl(Composite parent) {
		// viewer = new ZestContentViewer(new CounterStrategyModule());
		viewer = new ZestContentViewer(new ZestFxJFaceModule());
		// Shell shell = new Shell(new Display());
		// shell.setLayout(new FillLayout(SWT.VERTICAL));
		viewer.createControl(parent, SWT.NONE);
		setContentProviderForNodes();
		viewer.setLabelProvider(new ZestLabelProvider());
		// viewer.setConnectionStyle(ZestStyles.CONNECTIONS_DIRECTED);
		getSite().setSelectionProvider(viewer); // enables the property view to be informed
		// of selection changes in our graph when our view is the active workbench part

		// Create the help context id for the viewer's control
		PlatformUI.getWorkbench().getHelpSystem().setHelp(viewer.getControl(), ".viewer");
		makeActions();
		hookContextMenu();
		hookDoubleClickAction();
		contributeToActionBars();
	}

	/**
	 * setting the content provider for the viewer. This method is called in
	 * {@link #createPartControl(Composite)}. It needs to be consists of a single
	 * line of code:
	 * <code>viewer.setContentProvider(new <i>the_class_of_the_content_provider</i>());</code>
	 */
	abstract protected void setContentProviderForNodes();

	/**
	 * Passing the focus request to the viewer's control.
	 */
	@Override
	public void setFocus() {
		if (viewer != null)
			viewer.getControl().setFocus();
	}

	// @Override
	// public AbstractZoomableViewer getZoomableViewer() {
	// return viewer;
	// }

	protected void hookContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				fillContextMenu(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}

	protected void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		fillLocalPullDown(bars.getMenuManager());
		// fillLocalToolBar(bars.getToolBarManager());
		bars.updateActionBars();
	}

	/**
	 * filling the local drop-down menu. clients who wants to override this method
	 * in order to add actions should do the following:<br>
	 * <code>super.fillLocalPullDown(manager);<br>manager.add(new Separator());<br>
	 * manager.add(action2);<br>manager.add(new Separator());<br>manager.add(action3);<br>...</code>
	 * 
	 * @param manager
	 */
	protected void fillLocalPullDown(IMenuManager manager) {
		manager.add(changeLayoutAction);
		// manager.add(new Separator());
		// manager.add(action2);
	}

	/**
	 * filling the context menu (by right clicking) with actions. subclasses that
	 * want to add actions to the context menu should override
	 * {@link #addActionsToContextMenu(IMenuManager)}.
	 * 
	 * @param manager
	 */
	protected final void fillContextMenu(IMenuManager manager) {
		manager.add(changeLayoutAction);
		// Other plug-ins can contribute their actions here
		addActionsToContextMenu(manager);
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}

	/**
	 * Adding actions to the context menu (right click menu). subclasses that wants
	 * to do so should do as follows:<br>
	 * <code>manager.add(action2);<br>manager.add(action3);<br>...</code>
	 * 
	 * @param manager
	 */
	protected void addActionsToContextMenu(IMenuManager manager) {
	}

	/**
	 * fill this view's toolBar with buttons, including zooming options.
	 */
	// protected void fillLocalToolBar(IToolBarManager manager) {
	// manager.add(zoomItem);
	// }

	/**
	 * Initiating actions. Subclasses that uses additional actions may override this
	 * method as follows:<br>
	 * <code>super.makeActions();<br>
	 * newAction = new Action() {<br>
	 * 		public void run() {<br>
	 * 			// Override this.<br>
	 * 		}<br>
	 * };<br>
	 * newAction.setText(Name);<br>
	 * newAction.setToolTipText(Text);<br>
	 * concretizeNodeAction.setImageDescriptor(Image); // recommended<br></code>
	 */
	protected void makeActions() {
		// zoomItem = new ZoomContributionViewItem(this);

		changeLayoutAction = new Action() {
			public void run() {
				setLayoutManager();
			}
		};
		changeLayoutAction.setText("Change Layout");
		changeLayoutAction.setToolTipText("change layout for this graph");
		changeLayoutAction.setImageDescriptor(
				PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_ETOOL_CLEAR));

		// doubleClickAction = new Action() {
		// public void run() {
		// concretizeNodeAction.run();
		// }
		// };
	}

	protected void hookDoubleClickAction() {
		if (doubleClickAction == null)
			return;
		viewer.getControl().addMouseListener(new MouseListener() {

			@Override
			public void mouseUp(MouseEvent e) {
			}

			@Override
			public void mouseDown(MouseEvent e) {
			}

			@Override
			public void mouseDoubleClick(MouseEvent e) {
				doubleClickAction.run();

			}
		});
	}

	/**
	 * change the current layout of the graph.
	 */
	public void setLayoutManager() {
		switch (layout) {
		case 1:
			viewer.setLayoutAlgorithm(new TreeLayoutAlgorithm(TreeLayoutAlgorithm.TOP_DOWN));
			layout++;
			break;
		case 2:
			viewer.setLayoutAlgorithm(new SpringLayoutAlgorithm());
			layout++;
			break;
		case 3:
			viewer.setLayoutAlgorithm(new TreeLayoutAlgorithm(TreeLayoutAlgorithm.LEFT_RIGHT));
			layout++;
			break;
		case 4:
			viewer.setLayoutAlgorithm(new RadialLayoutAlgorithm());
			layout++;
			break;
		case 5:
			viewer.setLayoutAlgorithm(new TreeLayoutAlgorithm(TreeLayoutAlgorithm.RIGHT_LEFT));
			layout++;
			break;
		case 6:
			viewer.setLayoutAlgorithm(new TreeLayoutAlgorithm(TreeLayoutAlgorithm.BOTTOM_UP));
			layout = 1;
			break;
		}
	}
}