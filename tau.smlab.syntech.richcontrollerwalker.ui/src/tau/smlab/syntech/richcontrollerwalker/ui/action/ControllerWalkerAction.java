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

package tau.smlab.syntech.richcontrollerwalker.ui.action;

import static tau.smlab.syntech.richcontrollerwalker.ui.Activator.PLUGIN_NAME;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.MessageBox;

import tau.smlab.syntech.richcontrollerwalker.SymbolicWalker;
import tau.smlab.syntech.richcontrollerwalker.bdds.BddUtil;
import tau.smlab.syntech.richcontrollerwalker.filters.FilterSummary;
import tau.smlab.syntech.richcontrollerwalker.filters.FilterType;
import tau.smlab.syntech.richcontrollerwalker.options.DisplayedOptions;
import tau.smlab.syntech.richcontrollerwalker.options.IOptionsReply;
import tau.smlab.syntech.richcontrollerwalker.ui.dialogs.VarsDialog;
import tau.smlab.syntech.richcontrollerwalker.ui.dialogs.WalkDialog;
import tau.smlab.syntech.richcontrollerwalker.ui.preferences.PreferencePage;
import tau.smlab.syntech.richcontrollerwalker.util.IBreakpoint;
import tau.smlab.syntech.richcontrollerwalker.util.Mode;
import tau.smlab.syntech.richcontrollerwalker.util.Mod;
import tau.smlab.syntech.richcontrollerwalker.util.Modules;
import tau.smlab.syntech.richcontrollerwalker.util.OptionsType;
import tau.smlab.syntech.richcontrollerwalker.util.Preferences;
import tau.smlab.syntech.ui.extension.console.ConsolePrinter;
import tau.smlab.syntech.ui.extension.SyntechAction;

public class ControllerWalkerAction extends SyntechAction<ControllerWalkerActionsID> {
	// dialog;
	@Override
	public String getPluginName() {
		return PLUGIN_NAME;
	}

	@Override
	public ControllerWalkerActionsID[] getActionItems() {
		return ControllerWalkerActionsID.values();
	}
	
	/**
	 * We cannot run as a job as we require UI interaction. Must override and return false.
	 */
	@Override
	protected boolean runAsJob() {
		return false;
	}

	@Override
	public void run(ControllerWalkerActionsID modulesPlayedByUser, IFile specFile) { // Called once upon start.
		// create a new Symbolic Rich Controller Walker instance
		SymbolicWalker sw = createNewSymbolicWalker(PreferencePage.getPreferences(),
				modulesPlayedByUser.toUserModule());

		if (sw.isDeadlock()) {
			MessageDialog.openInformation(shell, PLUGIN_NAME,
					"Environment deadlock has been reached in the initial state.");
			sw.close();
			return;
		}

		WalkDialog dialog = new WalkDialog(shell, "Rich Controller Walker") {
			private String loadedLogFile;
			private final DDFilterHelper ddFltrHelper = new DDFilterHelper();
			protected final IMask mask = new Mask();
			DisplayedOptions dispOpts;

			@Override
			protected void buttonPressed(final int buttonId) {
				switch (buttonId) {
				case IDialogConstants.OK_ID:
					okPressed();
					break;
				case IDialogConstants.CANCEL_ID:
					cancelPressed();
					break;
				case IDialogConstants.OPEN_ID:
					openLogPressed();
					break;
				case IDialogConstants.BACK_ID:
					backPressed();
					break;
				case IDialogConstants.FINISH_ID:
					resetSteps();
					break;
				case IDialogConstants.SKIP_ID:
					skipToEnd();
					break;
				case IDialogConstants.STOP_ID:
					skipToStart();
					break;
				}
			}

			private void processAndUpdateOptions(IOptionsReply reply) {
				processReply(reply);
				updateOptions(reply.strList());
			}

			private void processReply(IOptionsReply reply) {
				dispOpts = null;
				Mode mode = sw.getMode();
				if (mode.isFree() && !reply.isEmpty()) {
					dispOpts = (DisplayedOptions) reply;
				}
			}

			private void updateOptions(List<String> opts) {
				updatePossibleStepsList(opts);
				updatePossibleStepsListView();
				selectStepAtIndex(possibleStepsList.size() > 0 ? 0 : -1);
			}

			private void updateUI() {
				Mode mode = sw.getMode();

				FilterSummary ddf_summary = sw.getFilterSummary(FilterType.DROPDOWN);
				ddFilterLabel.setText(ddf_summary.getExpression());
				FilterSummary tf_summary = sw.getFilterSummary(FilterType.TEXT);
				filterText.setText(tf_summary.getExpression());
				updateModeLabel();
				setPossibleStepsTitle();
				loadMoreStepsBtn.setEnabled(hasMoreOptions());
				stepBackButton.setEnabled(sw.canStepBack());
				getButton(IDialogConstants.SKIP_ID).setEnabled(mode.isGuided() && !sw.isRouteEnd());
				getButton(IDialogConstants.STOP_ID).setEnabled(mode.isGuided() && !sw.isRouteStart());
				nextButton.setEnabled(!sw.isDeadlock() && selectedOptionIndex >= 0); // Todo: recheck
				genLogBtn.setSelection(sw.isGeneratingLog());
				genLoglabel.setText(sw.isGeneratingLog() ? "Writing log to: " + sw.getLogFileName() : "");
				genLoglabel.getParent().layout();
				updateRemoveAllBpsBtn();
				if (!sw.getMode().isFree()) {
					reachabilityBtn.setEnabled(false);
					setLoadLogBtnText(sw.getMode().isReach() ? "Exit reachability" : "Exit log");
					stepBackButton.setText("Previous State");
					nextButton.setText("Next State");
				} else {
					setLoadLogBtnText("Load Log");
					stepBackButton.setText("Step Back");
					nextButton.setText("Next Step");
				}
				updateConsoleArea();
			}

			private boolean hasMoreOptions() {
				return sw.getMode().isFree() && dispOpts != null && dispOpts.hasMoreOptions();
			}

			private void loadMoreOptions() {
				updateOptions(dispOpts.nextStrList());
				updateUI();
			}

			private void setPossibleStepsTitle() {
				Mode mode = sw.getMode();
				Mod turn = sw.getTurn();
				if (mode.isFree()) {
					stepsGrp.setText("Next possible steps for " + turn);
				} else {
					stepsGrp.setText("Next Guided State");
				}
			}

			private void updateModeLabel() {
				Mode mode = sw.getMode();
				String modeLabelText = "MODE: " + mode;
				switch (mode) {
				case FREE:
					modeLabelText += "\nUser plays: " + sw.getUser() + ".";
					break;
				case LOG:
					modeLabelText += "\nWalking on log file: " + loadedLogFile + ".";
				case REACH:
					modeLabelText += "\nNumber of states remaining on guided route: " + sw.numRemainingRouteStates()
							+ " out of " + sw.numRouteStates() + ".";
					break;
				default:
					throw new IllegalArgumentException("Unexpected value: " + mode);
				}
				modeLabel.setText(modeLabelText);
				modeLabel.getParent().layout();
			}

			private void setLoadLogBtnText(String text) {
				loadLogBtn.setText(text);
				loadLogBtn.update();
			}

			private void updateConsoleArea() {
				// TODO: does this line do anything?
				consoleTableViewer.getTable().setTopIndex(consoleTableViewer.getTable().getItemCount() - 1);
				consoleTableViewer.refresh();
			}

			@Override
			protected void okPressed() {
				if (possibleStepsSWT.getSelection().length > 0) {
					selectedOptionIndex = possibleStepsSWT.getSelectionIndex(); // TODO: is this still necessary?
				}
				IOptionsReply reply = null;
				if (sw.getMode().isGuided()) {
					reply = sw.doNextStep(-1);
				} else {
					int id = dispOpts.getOptId(selectedOptionIndex);
					if (dispOpts.getType() == OptionsType.INCLUSIONS) {
						reply = sw.selectInclusion(id);
					} else {
						reply = sw.doNextStep(id);
					}
				}
				processAndUpdateOptions(reply);
				updateUI();
				updateBreakpoints();
				checkAndHandleDeadlock();
				updateConsoleArea();
			}

			protected void backPressed() { // Step back
				// clear the prints which have been generated by the recent steps
				processAndUpdateOptions(sw.stepBack());
				updateUI();
				updateBreakpoints();
			}

			protected void resetSteps() {
				// if log is being generated - warn user that reseting will erase log
				if (sw.isGeneratingLog() && popupDialog("Reset Walk",
						"Resetting walk will clear the currently generated log.") == SWT.CANCEL) {
					return;
				}
				processAndUpdateOptions(sw.reset());
				updateUI();
				updateBreakpoints();
			}

			@Override
			protected void cancelPressed() {
				sw.close();
				setReturnCode(CANCEL);
				close();
			}

			// Browse Dialog
			protected void openLogPressed() {
				if (!sw.getMode().isFree()) {
					processAndUpdateOptions(sw.exitRoute());
					updateUI();
					updateBreakpoints();
					checkAndHandleDeadlock();
					updateConsoleArea();
				} else {
					FileDialog dlg = new FileDialog(getShell());
					dlg.setFilterPath(sw.getWorkingDir());
					dlg.setText("File Dialog");
					String dir = dlg.open(); // get the direction
					if (dir != null) {
						loadedLogFile = Paths.get(dir).getFileName().toString();
						loadLog(dir);
					}
				}
			}

			private boolean loadLog(String path) {
				// Prompt creation of new log
				if (sw.isGeneratingLog() && popupDialog("Generate New Log",
						"Loading log will result in creation of new log.") == SWT.CANCEL) {
					return false;
				}
				final IOptionsReply reply = sw.startLogWalk(path);
				if (reply.isEmpty()) {
					MessageDialog.openInformation(shell, PLUGIN_NAME,
							"Failed to load specified log. Please try a different file.");
					return false;
				}
				processAndUpdateOptions(reply);
				updateUI();
				updateBreakpoints();
				updateConsoleArea();
				return true;
			}

			protected void skipToStart() {
				while (!sw.isRouteStart()) {
					backPressed();
				}
			}

			protected void skipToEnd() {
				while (!sw.isRouteEnd()) {
					okPressed();
				}
			}

			private boolean checkReachability(int bpId) {
				return sw.isReachable(bpId);
			}

			private void startReachability(int bpId) {
				processAndUpdateOptions(sw.startReachability(bpId));
				updateUI();
				updateConsoleArea();
			}

			private void removeBreakpoint(int bpId) {
				sw.removeBreakpoint(bpId);
				postBpRemoval();
			}

			private void updateBreakpoints() {
				sw.updateBreakpoints();
				updateRemoveAllBpsBtn();
				bpTableViewer.refresh();
			}

			private boolean checkAndHandleDeadlock() {
				if (sw.isDeadlock()) {
					MessageDialog.openInformation(shell, PLUGIN_NAME,
							"Environment deadlock has been reached. See steps history in the console");
					return true;
				}
				return false;
			}

			private void postBpRemoval() {
				removeBpBtn.setEnabled(false);
				reachabilityBtn.setEnabled(false);
				lastSelectedBpIndex = -1;
				updateBreakpoints();
			}

			@Override
			protected void replaceBreakpoint(int bpId, String newExpression) {
				sw.replaceBreakpoint(bpId, newExpression);
				updateBreakpoints();
			}
			
			protected void updatePossibleStepsListView() {
				possibleStepsSWT.removeAll();
				if (possibleStepsList != null) {
					for (String option : possibleStepsList) {
						possibleStepsSWT.add(mask.transform(option));
					}
				}
			}

			@Override
			protected void connectToBackend() {
				loadMoreStepsBtn.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						loadMoreOptions();
					}
				});

				genLogBtn.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						if (sw.isGeneratingLog() && sw.isUserBoth() && sw.getTurn().isSys() && popupDialog(
								"Exit Log State",
								"Disabling log generation before SYS completed a step may result in loss of current state.") == SWT.CANCEL) {
							return;
						}
						sw.toggleLog();
						updateUI();
						getDialogArea().redraw();
					}
				});

				for (String domainVal : BddUtil.getAllVarsMap().keySet()) {
					ddVars.add(domainVal);
				}
				ddVars.setEnabled(true);

				addFltrBtn.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						if (!ddVars.isTextValid() || !ddVals.isTextValid()) {
							return;
						}
						String newExp = ddFltrHelper.add(ddFilterLabel.getText(), ddVars.getText(), ddVals.getText());

						handleFilterActionReply(sw.addFilter(newExp, FilterType.DROPDOWN));
					}

				});

				removeFlterBtn.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						String newExp = ddFltrHelper.remove(ddFilterLabel.getText(), ddVars.getText());
						if (newExp.isBlank()) {
							handleFilterActionReply(sw.removeFilter(FilterType.DROPDOWN));
						} else {
							handleFilterActionReply(sw.addFilter(newExp, FilterType.DROPDOWN));
						}
					}

				});

				clearFltrBtn.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						handleFilterActionReply(sw.removeFilter(FilterType.DROPDOWN));
					}
				});

				textFilterBtn.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						String fExp = filterText.getText();
						IOptionsReply reply;
						if (fExp.isBlank()) {
							reply = sw.removeFilter(FilterType.TEXT);
						} else {
							reply = sw.addFilter(fExp, FilterType.TEXT);
						}
						if (sw.getMode().isFree()) {
							processAndUpdateOptions(reply);
						}
						updateUI();
					}
				});

				filterText.setText(sw.getFilterSummary(FilterType.TEXT).getExpression());

				consoleTableViewer.setInput(sw.getStepsSoFar());

				bpList = sw.getBreakpointsList();
				bpTableViewer.setInput(bpList);

				// update last selected breakpoint field
				bpTableViewer.getTable().addMouseListener(new MouseAdapter() {
					@Override
					public void mouseDown(MouseEvent e) {
						int y = bpTableViewer.getTable().getHeaderHeight();
						for (int i = 0; i < bpTableViewer.getTable().getItemCount(); i++) {
							y += bpTableViewer.getTable().getItemHeight();
							if (e.y <= y) {
								lastSelectedBpIndex = i;
								removeBpBtn.setEnabled(true);
								if (sw.getMode().isFree() && bpList.get(lastSelectedBpIndex).eval().isValid()) {
									reachabilityBtn.setEnabled(true);
								}
								break;
							}
						}
					}
				});

				addBpBtn.addSelectionListener(new SelectionAdapter() {

					@Override
					public void widgetSelected(SelectionEvent e) {
						// Add breakpoint button has been pressed.
						sw.addNewBreakpoint();
						updateBreakpoints();
					}
				});

				removeBpBtn.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						if (lastSelectedBpIndex >= 0 && lastSelectedBpIndex < bpList.size()) {
							removeBreakpoint(bpList.get(lastSelectedBpIndex).getId());
						}
					}
				});

				removeAllBpsBtn.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						if (popupDialog("Remove All Breakpoints",
								"All breakpoints will be removed and can only be restored manually.") == SWT.CANCEL) {
							return;
						}
						sw.removeAllBreakpoints();
						postBpRemoval();
					}
				});

				varsBtn.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						DisplayedOptions DO = sw.getMode().isFree() ? dispOpts : null;
						VarsDialog vd = new VarsDialog(shell, mask, DO ,sw.getTurn(), sw.getMode().isFree());
						vd.open();
						updatePossibleStepsListView();
					}
				});
				
				reachabilityBtn.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						IBreakpoint bp = bpList.get(lastSelectedBpIndex);
						if (bp.eval().isTrue()) {
							MessageBox dialog = new MessageBox(shell, SWT.OK);
							dialog.setText("Reachability Status");
							dialog.setMessage("Breakpoint is already reached.");
							dialog.open();
							return;
						}
						if (!checkReachability(bp.getId())) {
							MessageBox dialog = new MessageBox(shell, SWT.OK);
							dialog.setText("Reachability Status");
							dialog.setMessage("Could not reach from current state.");
							dialog.open();
							return;
						}

						MessageBox dialog = new MessageBox(shell, SWT.ICON_QUESTION | SWT.OK | SWT.CANCEL);
						dialog.setText("Reachability Status");
						dialog.setMessage("Successfully reached breakpoint state.\nEnter reachability mode?");
						if (dialog.open() == SWT.CANCEL) {
							return; // Cancelled by user.
						}
						bpTableViewer.refresh();
						startReachability(bp.getId());
					}
		
					
				});

				processAndUpdateOptions(sw.getDisplayOptions());
				updateUI();
				updateBreakpoints();
			}

			private void handleFilterActionReply(IOptionsReply reply) {
				if (sw.getMode().isFree()) {
					processAndUpdateOptions(reply);
				}
				updateUI();
			}

			@Override
			protected void selectedVariablesCombo() {
				if (ddVars.getText() != null) {
					ddVals.removeAll();
					for (String domainVal : BddUtil.getAllVarsMap().get(ddVars.getText()).domain()) {
						ddVals.add(domainVal);
					}
					ddVals.select(0);
					ddVals.setEnabled(true);
				}
			}
			
			
		};
		

		
		dialog.open();
	}

	
	
	private int popupDialog(String title, String message) {
		MessageBox dialog = new MessageBox(shell, SWT.ICON_QUESTION | SWT.OK | SWT.CANCEL | SWT.CENTER);
		dialog.setText(title);
		dialog.setMessage(message + " continue anyway?");
		return dialog.open();
	}

	private SymbolicWalker createNewSymbolicWalker(Preferences preferences, Modules userModule) {
		ConsolePrinter consolePrinter;
		try {
			consolePrinter = new ConsolePrinter(PLUGIN_NAME, ConsolePrinter.CLEAR_CONSOLE);
		} catch (IllegalAccessException | NoSuchFieldException e) {
			e.printStackTrace();
			throw new RuntimeException("cannot initialize console printer;");
		}
		consolePrinter.showConsole(activePage);
		try {
			return new SymbolicWalker(consolePrinter.getPrintStream(), specFile, userModule, preferences);
		} catch (IOException e) {
			e.printStackTrace();
			MessageDialog.openInformation(shell, PLUGIN_NAME,
					"Mismatched/Missing controller. Please synthesize a symbolic controller for this spectra file ("
							+ specFile.getName() + ").");
			throw new IllegalArgumentException(
					"Mismatched/Missing controller. Please synthesize a symbolic controller for this spectra file ("
							+ specFile.getName() + ").");
		}
	}
}