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

package tau.smlab.syntech.richcontrollerwalker;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Objects;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDVarSet;
import tau.smlab.syntech.jtlv.CoreUtil;
import tau.smlab.syntech.jtlv.Env;

final class Logger {
	private boolean generating;
	private final RichLog log;

	Logger(final boolean isGenerating, final String filePath, final String modelName) {
		generating = isGenerating;
		log = new RichLog(filePath, modelName);
	}

	/**
	 * @return true if writing to log file, false otherwise
	 */
	boolean isGenerating() {
		return generating;
	}

	/**
	 * Gets name of current log file
	 */
	String getFileName() {
		return generating ? log.getFileName() : "";
	}

	/**
	 * Toggles logging state
	 */
	void toggle() {
		generating = !generating;
		if (generating) {
			createNew();
		} else {
			log.flush();
		}
	}

	/**
	 * Creates new log file
	 */
	boolean createNew() {
		if (!generating) {
			throw new IllegalStateException("cannot create new log when log generation is not activated.");
		}
		final boolean isWriteSuccessful = flush();
		log.createNew();
		return isWriteSuccessful;
	}

	void writeState(final BDD state) {
		if (!generating) {
			throw new IllegalStateException("cannot write to log when not generating log.");
		}
		log.addState(state);
	}

	void removeLastState() {
		if (!generating) {
			throw new IllegalStateException("cannot delete from log when not generating log.");
		}
		if (!log.isEmpty()) {
			log.removeLastState();
		}
	}

	boolean flush() {
		return generating ? log.flush() : true;
	}

	private static final class RichLog {
		private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy_HH_mm_ss");
		private static final String LINE_SEPARATOR = System.lineSeparator();
		private final String filePath;
		private final String fileNamePrefix;

		private Deque<String> loggedStatesStack = new ArrayDeque<>();
		private String fileName;

		public RichLog(final String filePath, final String modelName) {
			this.filePath = filePath;
			this.fileNamePrefix = modelName + "_log_";
		}

		void createNew() {
			fileName = fileNamePrefix + getFileNameSuffix();
			clearStates();
		}

		void addState(BDD state) {
			Objects.requireNonNull(fileName);
			loggedStatesStack.addLast(stateToStr(state));
		}

		void removeLastState() {
			if (isEmpty()) {
				throw new IllegalStateException("cannot delete last line from empty log");
			}
			loggedStatesStack.removeLast();
		}

		String getFileName() {
			return Objects.requireNonNull(fileName);
		}

		boolean flush() {
			if (isEmpty()) {
				clear();
				return true;
			}
			try (FileWriter writer = new FileWriter(fullPath());) {
				writer.write(String.join(LINE_SEPARATOR, loggedStatesStack));
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			} finally {
				clear();
			}
			return true;
		}

		private boolean isEmpty() {
			return Objects.isNull(fileName) || Objects.isNull(loggedStatesStack)
					|| loggedStatesStack.isEmpty();
		}

		private void clear() {
			clearStates();
			clearFileName();
		}

		private void clearStates() {
			loggedStatesStack.clear();
		}

		private void clearFileName() {
			fileName = null;
		}

		private String stateToStr(final BDD state) {
			Objects.requireNonNull(state);
			if (state.isZero()) {
				return "FALSE";
			}
			final BDDVarSet vars = Env.globalUnprimeVars();
			if (vars.isEmpty()) {
				return "TRUE";
			}
			if (state.satCount() > 1) {
				final BDD one = CoreUtil.satOne(state, vars);
				final String str = one.toStringWithDomains(Env.stringer);
				one.free();
				return str;
			}
			return state.toString();
		}

		private String fullPath() {
			return filePath + fileName;
		}

		private String getFileNameSuffix() {
			return DATE_FORMAT.format(new Date()) + ".txt";
		}

	}
}
