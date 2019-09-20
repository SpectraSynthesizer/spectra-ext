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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import tau.smlab.syntech.jtlv.BDDPackage;
import tau.smlab.syntech.jtlv.BDDPackage.BBDPackageVersion;
import tau.smlab.syntech.richcontrollerwalker.ControllerConstants;
import tau.smlab.syntech.richcontrollerwalker.SymbolicWalker;
import tau.smlab.syntech.richcontrollerwalker.SymbolicWalker.UserModule;
import tau.smlab.syntech.richcontrollerwalker.ui.dialogs.WalkDialog;
import tau.smlab.syntech.richcontrollerwalker.ui.preferences.PreferencePage;
import tau.smlab.syntech.ui.console.ConsolePrinter;
import tau.smlab.syntech.ui.extension.SyntechAction;

public class ControllerWalkerAction extends SyntechAction<ControllerWalkerActionsID> {

	private static final int STATE_IDX = 0;
	private static final int DEFAULT_IDX = 0;
	private ConsolePrinter consolePrinter;
	private boolean isBothPlayers = false;
	public WalkDialog walkDialog;
	public SymbolicWalker symbolicWalker;

	@Override
	public String getPluginName() {
		return PLUGIN_NAME;
	}

	@Override
	public ControllerWalkerActionsID[] getActionItems() {
		return ControllerWalkerActionsID.values();
	}

	@Override
	public void run(ControllerWalkerActionsID action, IFile specFile) {
		// Called once upon start.
		int walkingDepth = PreferencePage.getWalkingDepth();
		boolean isLogActive = PreferencePage.getIsLogActive();

		int alternativeStepCount = PreferencePage.getAlternativeStepCount();
		BDDPackage bddPackage = tau.smlab.syntech.ui.preferences.PreferencePage.getBDDPackageSelection();
		BBDPackageVersion bddPackageVersion = tau.smlab.syntech.ui.preferences.PreferencePage
				.getBDDPackageVersionSelection();

		try {
			consolePrinter = new ConsolePrinter(PLUGIN_NAME, ConsolePrinter.CLEAR_CONSOLE);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		boolean[] userPlayers = getSymbolicWalkingUserPlayers(action);
		boolean playEnv = userPlayers[0];
		boolean playSys = userPlayers[1];
		// create a new Symbolic Rich Controller Walker instance
		symbolicWalker = createNewSymbolicWalker(walkingDepth, alternativeStepCount, bddPackage, bddPackageVersion,
				playEnv, playSys, isLogActive);

		if (symbolicWalker == null) {
			return;
		}

		// do depth ENV-SYS consecutive steps, (MAYBE followed by a single ENV step)
		try {
			symbolicWalker.doDepthSteps();
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (symbolicWalker.isEnvDeadLock()) {
			MessageDialog.openInformation(shell, PLUGIN_NAME,
					"Environment deadlock has been reached after performing the initial steps shown in the console");
			return;
		}

		walkDialog = new WalkDialog(shell, "Rich Controller Walker", symbolicWalker.computeCurrentPlayerSteps(),
				symbolicWalker.getPrints()) {

			@Override
			protected void okPressed() {
				super.okPressed();

				doNextStep();

				// Enable/disable load more steps button.
				loadMoreSteps.setEnabled(symbolicWalker.hasMoreSteps());

				// Update watches and breakpoints before next step.
				updateWatches();

				// update breakpoints
				updateBreakpoints();
			}

			private void doNextStep() {
				String selectedStep = getSelectedStep();
				int selectedStepIdx = (selectedStep != null) ? this.getSelectedStepIndex() : -1;

				if (selectedStep != null) {
					// perform the selected ENV or SYS step
					if (!symbolicWalker.doChosenEnvStep(selectedStepIdx)) {
						symbolicWalker.doChosenSysStep(selectedStepIdx);
					}
				}

				if (!(symbolicWalker.isFollowingLog() || symbolicWalker.isFollowingReachability())) {
					// do depth ENV-SYS consecutive steps, MAYBE followed by a single ENV step
					try {
						symbolicWalker.doDepthSteps();
					} catch (IOException | NullPointerException e) {
						// e.printStackTrace();
					}
				}

				// accumulate all prints of the steps performed so far which are shown in the
				// dialog
				symbolicWalker.donePrintingToConsole(); // Do after depth steps (if there were any).
				List<String> newContent = symbolicWalker.getPrints();
				this.appendToConsoleArea(newContent);

				// clear the prints which have been generated by the recent steps
				symbolicWalker.clearPrints();

				if (!symbolicWalker.isFollowingLog() && !symbolicWalker.isFollowingReachability()) {
					// show the new possible SYS or ENV steps in the user dialog
					List<String> currentPlayerSteps = symbolicWalker.computeCurrentPlayerSteps();
					this.updatePossibleStepsList(currentPlayerSteps);
					this.updatePossibleStepsListView(this.possibleStepsList, true);
					updateNextStepLabelText();
					handleEnvDeadlock(symbolicWalker);
					super.selectStepAtIndex(DEFAULT_IDX);

				} else { // log and/or reachability state
					String curState = symbolicWalker.getCurrentState();
					if (curState != null) {
						List<String> currentPlayerStates = new ArrayList<>();
						currentPlayerStates.add(curState);
						this.updatePossibleStepsListView(currentPlayerStates, true);
						super.selectStepAtIndex(STATE_IDX);
						// markChosenStep();

					} else {
						if (!symbolicWalker.isFollowingLog()) {
							exitLog();
							setFollowingLog(null);
						} else if (!symbolicWalker.isFollowingReachability()) {
							exitReachability();
						}
					}
				}

				this.setBackButtonEnable(symbolicWalker.canStepBack());
			}

			/**
			 * Test
			 */
			protected void backPressed() { // Step back
				super.backPressed();

				// Prompt creation of new log
				if (symbolicWalker.isGeneratingLog()) {

					if (stepBackLogGenerationDialog() == SWT.CANCEL) {
						// On cancel
						return;
					}
				}

				int res = symbolicWalker.stepBack();
				if (res < 0)
					return;

				// Update generated log label after generating new file
				updateGenLogLabel();

				// Remove the last print from console.
				this.removeCharsFromConsole(res);

				// clear the prints which have been generated by the recent steps
				symbolicWalker.clearPrints();

				if (!(symbolicWalker.isFollowingLog() || symbolicWalker.isFollowingReachability())) {
					// Normal step back
					// show the new possible SYS or ENV steps in the user dialog
					// Note that the false parameter in computeCurrentPlayerSteps avoids updating
					// currentState.
					List<String> currentPlayerSteps = symbolicWalker.computeCurrentPlayerSteps(false);
					this.updatePossibleStepsList(currentPlayerSteps);
					this.updatePossibleStepsListView(currentPlayerSteps, true);
					handleEnvDeadlock(symbolicWalker);
					super.selectStepAtIndex(Math.max(0, symbolicWalker.lastStepIndex));
				} else {
					// * Step back on log mode *
					String curState = symbolicWalker.getCurrentState();
					List<String> currentPlayerStates = new ArrayList<>();
					currentPlayerStates.add(curState);
					this.updatePossibleStepsListView(currentPlayerStates, true);
					handleEnvDeadlock(symbolicWalker);
					super.selectStepAtIndex(STATE_IDX);
				}
				this.setBackButtonEnable(symbolicWalker.canStepBack());

				// Update breakpoints and watches
				updateWatches();
				updateBreakpoints();

			}

			@Override
			protected void resetSteps() {
				super.resetSteps();

				// Enable step back button.
				stepBackButton.setEnabled(false);

				// if log is being generated - warn user that reseting will erase log
				if (symbolicWalker.isGeneratingLog()) {
					int res = resetDialog();
					if (res == SWT.CANCEL) {
						return;
					}
				}

				// reset symbolic walker
				symbolicWalker.reset();

				// Jump to the first step. Update next steps.
				List<String> currentPlayerSteps = symbolicWalker.computeCurrentPlayerSteps();
				this.updatePossibleStepsList(currentPlayerSteps);
				this.updatePossibleStepsListView(currentPlayerSteps, true);
				this.setBackButtonEnable(false);

				// Refresh watches
				updateWatches();

				// Refresh breakpoints
				updateBreakpoints();

				// Clear console messages.
				super.clearConsole();

				// Set log mode off.
				setFollowingLog(null);

				// Reset reachability
				exitReachability();

				// update GUI labels
				exitLogOrReachabilityState();
			}

			@Override
			protected void cancelPressed() {
				super.cancelPressed();
//				MessageDialog.openInformation(shell, PLUGIN_NAME,
//						"Walker dialog closed. See steps history in the console");
				symbolicWalker.clear();
			}

			protected String getGenLogPath() {
				return symbolicWalker.getLogFileName();
			}

			protected void changeLogState() {
				try {
					if (symbolicWalker.isGeneratingLog()) {
						if (isBothPlayers && symbolicWalker.getPlayingModule() == UserModule.SYS) {
							if (exitLogDialog() == SWT.CANCEL) {
								// On cancel
								genLogBtn.setSelection(true);
								return;
							}
						}
						symbolicWalker.changeLogState();
						resetGenLogLabel();
					} else {
						symbolicWalker.changeLogState();
						updateGenLogLabel();
						this.getDialogArea().redraw();
					}
				} catch (IOException e) {
					// Auto-generated catch block
					e.printStackTrace();
				}
			}

			private void goToCurrentLogState() {
				if (!symbolicWalker.isFollowingLog())
					return;
				String curState = symbolicWalker.getCurrentState();
				List<String> currentPlayerStates = new ArrayList<>();
				currentPlayerStates.add(curState);
				this.updatePossibleStepsListView(currentPlayerStates, true);
				super.selectStepAtIndex(STATE_IDX);
				updateWatches();
				updateBreakpoints();
			}

			@Override
			protected boolean isFollowingLog() {
				return symbolicWalker.isFollowingLog();
			}

			private void setFollowingLog(String logFilename) {
				super.setFollowingLogLocal(logFilename);

				// Enable the jump buttons iff following a log.
				getButton(IDialogConstants.SKIP_ID).setEnabled(symbolicWalker.isFollowingLog());
				getButton(IDialogConstants.STOP_ID).setEnabled(symbolicWalker.isFollowingLog());
			}

			@Override
			protected boolean loadLog(String path) {
				super.loadLog(path);

				// Prompt creation of new log
				if (symbolicWalker.isGeneratingLog()) {

					if (loadLogGenerationDialog() == SWT.CANCEL) {
						// On cancel
						return false;
					}
				}

				// Generate BDDs from log file.
				if (symbolicWalker.loadLog(path)) {
					exitReachability();
					enterLogOrReachabilityState();

					// Clear console messages.
					super.clearConsole();

					String logFilename = path.substring(path.lastIndexOf('\\') + 1);
					setFollowingLog(logFilename);

					// mark step
					String curState = symbolicWalker.getCurrentState();
					List<String> currentPlayerStates = new ArrayList<>();
					currentPlayerStates.add(curState);
					this.updatePossibleStepsListView(currentPlayerStates, true);
					this.setBackButtonEnable(false);
					super.selectStepAtIndex(STATE_IDX);

					// Update watches and breakpoints
					updateWatches();
					updateBreakpoints();

					// Update generated log label after generating new file
					if (symbolicWalker.isGeneratingLog()) {
						updateGenLogLabel();
					}

					return true;
				} else {
					MessageDialog.openInformation(shell, PLUGIN_NAME,
							"Failed to load specified log. Please try a different file.");
				}
				return false;
			}

			@Override
			protected void exitLog() {
				symbolicWalker.exitLogMode();
				super.exitLog();
				setFollowingLog(null);
				super.exitReachability();
				super.setFollowingReachabilityLocal();
				// recalculate steps
				List<String> currentPlayerSteps = symbolicWalker.computeCurrentPlayerSteps();
				this.updatePossibleStepsList(currentPlayerSteps);
				this.updatePossibleStepsListView(this.possibleStepsList, true);
				updateNextStepLabelText();
				super.selectStepAtIndex(DEFAULT_IDX);

			}

			@Override
			protected void skipToStart() {
				if (!symbolicWalker.isFollowingLog())
					return;

				// Prompt creation of new log
				if (symbolicWalker.isGeneratingLog()) {
					if (stepBackLogGenerationDialog() == SWT.CANCEL) {
						// On cancel
						return;
					}
				}

				symbolicWalker.jumpToStart();

				// Update generated log label after generating new file
				updateGenLogLabel();

				// Clear console messages.
				super.clearConsole();

				// Disable back button.
				this.setBackButtonEnable(false);

				// Go to the chosen state in the log (according to logIndex).
				goToCurrentLogState();
			}

			@Override
			protected void skipToEnd() {
				if (symbolicWalker.isFollowingLog() || symbolicWalker.isFollowingReachability()) {
					// Step until end of walk.
					while (!(symbolicWalker.isEndOfReachability() || symbolicWalker.isEndOfLog())) {
						doNextStep();
					}

					// Update watches and breakpoints before next step.
					updateWatches();
					updateBreakpoints();
				}
			}

			@Override
			protected boolean isLogOrReachabilityActive() {
				return symbolicWalker.isFollowingLog() || symbolicWalker.isFollowingReachability();
			}

			@Override
			protected void stopPlayingReachability() {
				symbolicWalker.exitReachabilityMode();
			}

			@Override
			protected void exitReachability() {
				super.exitReachability();
				super.setFollowingReachabilityLocal();
				// Disable indicators and update relevant labels.
				if (!symbolicWalker.isFollowingLog()) {
					// recalculate states
					super.exitLog();
					setFollowingLog(null);
					List<String> currentPlayerSteps = symbolicWalker.computeCurrentPlayerSteps();
					this.updatePossibleStepsList(currentPlayerSteps);
					this.updatePossibleStepsListView(this.possibleStepsList, true);
					updateNextStepLabelText();
					exitLogOrReachabilityState();
					super.selectStepAtIndex(DEFAULT_IDX);

				}
			}

			@Override
			protected boolean checkReachability(int bpIndex) {
				return symbolicWalker.checkReachability(bpIndex);
			}

			@Override
			protected boolean isReachability() {
				return symbolicWalker.isFollowingReachability();
			}

			@Override
			protected void startReachability() {
				// update GUI labels
				enterLogOrReachabilityState();

				symbolicWalker.playReachability();
				super.setFollowingReachabilityLocal();
				getButton(IDialogConstants.SKIP_ID).setEnabled(true);

				String curState = symbolicWalker.getCurrentState();
				List<String> currentPlayerStates = new ArrayList<>();
				currentPlayerStates.add(curState);
				this.updatePossibleStepsListView(currentPlayerStates, true);
				super.selectStepAtIndex(STATE_IDX);

			}

			@Override
			protected boolean addWatch(int idx, String formula) {
				return symbolicWalker.addWatch(idx, formula);
			}

			@Override
			protected boolean removeWatch(int idx) {
				return symbolicWalker.removeWatch(idx);
			}

			@Override
			protected String getWatchEval(int idx) {
				if (symbolicWalker.getWatchValue(idx))
					return "True";
				return "False";
			}

			private void updateWatches() {
				for (int i = 0; i < watchModel.size(); i++) {
					if (!watchModel.get(i).enabled)
						continue;
					String value = getWatchEval(watchModel.get(i).id);
					// Do not update when value is null.

					if (value != null) {
						if (value.equals(watchModel.get(i).val))
							watchModel.get(i).markForChange = false;
						else
							watchModel.get(i).markForChange = true;
						watchModel.get(i).val = value;
					}
				}
				watchTableViewer.refresh();
			}

			@Override
			protected ArrayList<Watch> loadWatches() {
				ArrayList<Watch> watches = new ArrayList<>();
				HashMap<Integer, String> persistWatchDict = symbolicWalker.loadWatchExpressionsProperties();
				// List<String> strList = symbolicWalker.loadWatches();

				if (!persistWatchDict.keySet().isEmpty())
					watchCounter = Collections.max(persistWatchDict.keySet()) + 1;

				for (int idx : persistWatchDict.keySet()) {

					Watch watch = new Watch(idx, persistWatchDict.get(idx));
					if (addWatch(watch.id, watch.eq)) {
						watch.enabled = true;
						watch.val = getWatchEval(watch.id);
					} else {
						watch.enabled = false;
						watch.val = "N/A";
					}
					watches.add(watch);
				}
				return watches;
			}

			@Override
			protected boolean addBreakpoint(int idx, String formula) {
				return symbolicWalker.addBreakpoint(idx, formula);
			}

			protected boolean isBreakpointRemoved(int bpindex) {
				return symbolicWalker.removeBreakpoint(bpindex);
			}

			protected void enableBreakpoint(int bpID) {
				symbolicWalker.enableBreakPoint(bpID);
			}

			protected void disableBreakpoint(int bpID) {
				symbolicWalker.disableBreakPoint(bpID);
			}

			@Override
			protected boolean getBreakpointValue(Breakpoint bp) {
				return symbolicWalker.isBreakPointStop(bp.id);
			}

			private void updateBreakpoints() {
				// update breakpoints
				for (int i = 0; i < breakpointModel.size(); i++) {
					if (breakpointModel.get(i).enabled) {
						updateBreakpoint(i, symbolicWalker.isBreakPointStop(i));
					}
				}
				breakpointTableViewer.refresh();
			}

			protected void updateBreakpoint(int idx, boolean reached) {
				if (idx >= 0 && idx < breakpointModel.size())
					breakpointModel.get(idx).reached = reached;
			}

			@Override
			protected ArrayList<Breakpoint> loadBreakpoints() {
				ArrayList<Breakpoint> bps = new ArrayList<>();
				HashMap<Integer, String> persistBPDict = symbolicWalker.loadBreakpointExpressionsProperties();
				// List<String> strList = symbolicWalker.loadWatches();

				if (!persistBPDict.keySet().isEmpty())
					bpCounter = Collections.max(persistBPDict.keySet()) + 1;

				for (int idx : persistBPDict.keySet()) {
					Breakpoint bp = new Breakpoint(idx, persistBPDict.get(idx));

					if (addBreakpoint(bp.id, bp.eq)) {
						bp.valid = true;
						bp.reached = getBreakpointValue(bp);
					} else {
						bp.valid = false;
						bp.reached = false;
					}
					bp.enabled = false;
					bps.add(bp);
				}
				return bps;
			}

			@Override
			protected void guiWasInit() {
				loadMoreSteps.setEnabled(symbolicWalker.hasMoreSteps());

				// Last thing to check.
				isUnitTest();
			}

			@Override
			protected void pressedOnAButton() {
				// Display the current turn (ENV or SYS).
				updateNextStepLabelText();
				// Enable/disable load more steps button.
				loadMoreSteps.setEnabled(symbolicWalker.hasMoreSteps());
			}

			@Override
			protected boolean isENVTurn() {
				return symbolicWalker.getPlayingModule() == UserModule.ENV;
			}

			@Override
			protected String getWorkingDir() {
				return symbolicWalker.getWorkingDir();
			}

			@Override
			protected void loadMoreSteps() {
				// show the new possible SYS or ENV steps in the user dialog
				List<String> currentPlayerSteps = symbolicWalker.getMoreSteps();
				// if (currentPlayerSteps.size()==0) return;
				this.updatePossibleStepsList(currentPlayerSteps);
				this.updatePossibleStepsListView(currentPlayerSteps, true);
				if (!symbolicWalker.hasMoreSteps()) {
					loadMoreSteps.setEnabled(false);
				}
			}

			private boolean handleEnvDeadlock(SymbolicWalker symbolicWalker) {
				if (symbolicWalker.isEnvDeadLock()) {
					MessageDialog.openInformation(shell, PLUGIN_NAME,
							"Environment deadlock has been reached. See steps history in the console");
					close();
					return true;
				}
				return false;
			}

		};
		// clear the prints which have been generated by the first performed steps
		symbolicWalker.clearPrints();
		walkDialog.open();
		consolePrinter.showConsole(activePage);
	}

	private void isUnitTest() {
		String[] appArgs = Platform.getApplicationArgs();
		for (int i = 0; i < appArgs.length; i++) {
			/*
			 * if (appArgs[i].contains("-unittest")) { Junit.beginTests(this); break; }
			 */
		}
	}

	private boolean[] getSymbolicWalkingUserPlayers(ControllerWalkerActionsID action) {
		boolean[] userPlayers = new boolean[2]; // userPlayers[0] -> ENV, userPlayers[1] -> SYS
		if (ControllerWalkerActionsID.WALK_SYMBOLIC_CONTROLLER_BOTH.equals(action)) {
			isBothPlayers = true;
			userPlayers[0] = true;
			userPlayers[1] = true;
		} else if (ControllerWalkerActionsID.WALK_SYMBOLIC_CONTROLLER_SYS.equals(action)) {
			userPlayers[0] = false;
			userPlayers[1] = true;
		} else if (ControllerWalkerActionsID.WALK_SYMBOLIC_CONTROLLER_ENV.equals(action)) {
			userPlayers[0] = true;
			userPlayers[1] = false;
		}
		return userPlayers;
	}

	private SymbolicWalker createNewSymbolicWalker(int walkingDepth, int alternativeStepCount, BDDPackage bddPackage,
			BBDPackageVersion bddPackageVersion, boolean playEnv, boolean playSys, boolean isLogActive) {

		SymbolicWalker symbolicWalker = null;
		try {
			symbolicWalker = new SymbolicWalker(consolePrinter.getPrintStream(), walkingDepth, alternativeStepCount,
					specFile, bddPackage, bddPackageVersion, playEnv, playSys, isLogActive);
		} catch (Exception e) {
			if (e.getMessage().equals(ControllerConstants.MISMATCHED_CONTROLLER)) {
				MessageDialog.openInformation(shell, PLUGIN_NAME,
						"Mismatched controller. Please synthesize a symbolic controller for this spectra file ("
								+ specFile.getName() + ").");
			} else {
				MessageDialog.openInformation(shell, PLUGIN_NAME, "Controller is missing. FileNotFoundException: "
						+ e.getMessage() + ". Please synthesize a controller.");
			}
		}
		return symbolicWalker;
	}

}