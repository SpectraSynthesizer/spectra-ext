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

package tau.smlab.syntech.dynamicupdate;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDVarSet;
import tau.smlab.syntech.games.util.SaveLoadWithDomains;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.jtlv.ModuleVariableException;
import tau.smlab.syntech.jtlv.env.module.ModuleBDDField;
import tau.smlab.syntech.ui.extension.console.ConsolePrinter;

public class DynamicUpdateClient {

	protected final static String BRIDGE_DIR = "bridge";
	protected final static String PREV_TRANS = "prevTrans.bdd";
	protected final static String PREV_VARS = "prevVars.doms";
	protected final static String RO = "trans.bdd";

	protected final static String VARS = "vars.doms";
	protected final static String SIZES = "sizes";
	protected final static String FIXPOINTS = "fixpoints.bdd";
	protected final static String TRANS = "trans.bdd";
	protected final static String JUSTICE = "justice.bdd";
	protected final static String SWITCH = "switch.bdd";
	protected final static String ENV_TRANS = "envTrans.bdd";
	protected final static String BRIDGE_VARS = "bridgeVars.doms";

	protected final static int MAX_FILE_SIZE = 6022386;
	protected final static int BLOCK_SIZE = 65536;

	protected final static String START_UPDATE = "Update";
	protected final static String CHECK_CONNECTION = "Check";
	protected final static String IN_REGION = "Yes";
	protected final static String CONNECTED = "Connected";
	protected final static String BRIDGE_ENABLED = "Bridge";
	protected final static String RECEIVED = "Received";

	protected final static String SWITCH_VAR = "switch";
	protected final static String ALLOWED_VAR = "allowed";
	
	protected final static int SOCKET_TIMEOUT = 10000;

	protected String ip;
	protected int port;
	protected Socket socket;
	protected ConsolePrinter console;
	protected PrintWriter out;
	protected BufferedReader in;
	protected String outFolder;
	protected String bridgeFolder;
	protected int bridgeCount;
	protected BDD switchVarTrue;
	protected BDD trans2;
	protected BDD envTrans;

	protected BDDVarSet sysPrimeVars;
	protected BDDVarSet envPrimeVars;

	public DynamicUpdateClient(String outFolder, String ip, int port, ConsolePrinter console) throws IOException {
		this.outFolder = outFolder + File.separator;
		this.bridgeFolder = this.outFolder + BRIDGE_DIR + File.separator;
		File bridgeFile = new File(this.bridgeFolder);
		if (!bridgeFile.exists()) {
			bridgeFile.mkdir();
		}
		this.ip = ip;
		this.port = port;
		this.console = console;
		this.socket = new Socket(this.ip, this.port);
		this.socket.setSoTimeout(SOCKET_TIMEOUT);
		this.out = new PrintWriter(socket.getOutputStream(), true);
		this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		this.bridgeCount = 0;
		printToConsole("Created dynamic update client.");
	}

	private void printToConsole(String msg) {
		if (this.console == null) {
			System.out.println(msg);
		} else {
			console.println(msg);
		}
	}
	

	void addTestData(String msg) {
		try
		{
		    String filename= "test_results.csv";
		    FileWriter fw = new FileWriter(filename, true); //the true will append the new data
		    fw.write(msg);//appends the string to the file
		    fw.close();
		}
		catch(IOException ioe)
		{
		    System.err.println("IOException: " + ioe.getMessage());
		}
	}

	public boolean checkConnection() throws IOException, SocketTimeoutException {
		this.out.println(CHECK_CONNECTION);
		
		return (this.in.readLine().startsWith(CONNECTED));
	}

	public void getSysEnvVars() throws IOException {
		Map<String, String[]> sysVars;
		Map<String, String[]> envVars;

		sysVars = new HashMap<>();
		envVars = new HashMap<>();

		SaveLoadWithDomains.loadStructureAndDomains(this.outFolder + VARS, sysVars, envVars);
//		SaveLoadWithDomains.loadStructureAndDomains(this.outFolder + PREV_VARS, sysVars, envVars);

		sysVars.entrySet().removeIf(var -> var.getKey().startsWith("util_"));
		sysVars.entrySet().removeIf(var -> var.getKey().startsWith("sfa_states"));
		envVars.entrySet().removeIf(var -> var.getKey().startsWith("util_"));
		sysVars.entrySet().removeIf(var -> var.getKey().startsWith("sfa_states"));

		this.sysPrimeVars = Env.getEmptySet();
		this.envPrimeVars = Env.getEmptySet();
		ModuleBDDField var;

		for (String sysVar : sysVars.keySet()) {
			var = Env.getVar(sysVar);
			if (var.isPrime()) {
				this.sysPrimeVars.unionWith(var.support());
			}
			else {
				this.sysPrimeVars.unionWith(var.prime().support());
			}
		}
		for (String envVar : envVars.keySet()) {
			var = Env.getVar(envVar);
			if (var.isPrime()) {
				this.envPrimeVars.unionWith(var.support());
			}
			else {
				this.envPrimeVars.unionWith(var.prime().support());
			}
		}
	}

	public void closeClient() throws IOException {
		if (null != this.in) {
			this.in.close();
		}
		if (null != this.out) {
			this.out.close();
		}
		if (null != this.socket) {
			this.socket.close();
		}
		if (null != switchVarTrue) {
			switchVarTrue.free();
		}
		if (null != trans2) {
			trans2.free();
		}
		if (null != envTrans) {
			envTrans.free();
		}
		if (null != sysPrimeVars) {
			sysPrimeVars.free();
		}
		if (null != envPrimeVars) {
			envPrimeVars.free();
		}
	}

	void receiveFile(Socket socket, String path) throws IOException {
		printToConsole("Receives file " + path);
		int bytesRead;
		FileOutputStream fos = null;
		BufferedOutputStream bos = null;
		DataInputStream dis = null;
		try {
			byte [] fileBytes = new byte [BLOCK_SIZE];
			InputStream is = socket.getInputStream();
			fos = new FileOutputStream(path);
			bos = new BufferedOutputStream(fos);
			dis = new DataInputStream(is);
			long fileSize = dis.readLong();
			printToConsole("Receiving " + String.valueOf((int) fileSize) + " bytes of file " + path);
			while (fileSize > 0) {
				bytesRead = dis.read(fileBytes, 0, (int) Math.min(BLOCK_SIZE, fileSize));
				printToConsole("Received " + String.valueOf((int) bytesRead) + " bytes of file " + path);
				bos.write(fileBytes, 0, bytesRead);
				fileSize -= bytesRead;
			}
			bos.flush();
			out.println(RECEIVED);
		}
		finally {
			if (fos != null) fos.close();
			if (bos != null) bos.close();
		}
	}

	void sendFile(Socket socket, String path) throws IOException {
		FileInputStream fis = null;
		BufferedInputStream bis = null;
		OutputStream os = null;
		DataOutputStream dos = null;
		try {
			File toSend = new File(path);
			byte [] fileBytes = new byte [(int)toSend.length()];
			fis = new FileInputStream(toSend);
			bis = new BufferedInputStream(fis);
			bis.read(fileBytes, 0, fileBytes.length);
			os = socket.getOutputStream();
			dos = new DataOutputStream(os);
			printToConsole("Sending " + String.valueOf(fileBytes.length) + " bytes of file " + path);
			dos.writeLong(fileBytes.length);
			os.write(fileBytes, 0, fileBytes.length);
			os.flush();
			String ret = in.readLine();
			printToConsole("Returned " + ret);
			if (!ret.startsWith(RECEIVED)) {
				printToConsole("Sending file " + path + " failed!");
			}
		}
		finally {
			if (bis != null) bis.close();
			if (fis != null) fis.close();
		}
	}

	BDD computeTrans(BDD switchBDD) throws ModuleVariableException {
		BDD trans1 = null;
		
		try {
			trans1 = Env.loadBDD(this.outFolder + PREV_TRANS);
		} catch (IOException e) {
			printToConsole("Bridge computation failed - missing new controller transitions.");
			return null;
		}
		
		try {
			SaveLoadWithDomains.loadStructureAndDomains(this.outFolder + BRIDGE_VARS, null, null);
		} catch (IOException e) {
			printToConsole("Failed to create switch and allowed vars.");
			return null;
		}
		printToConsole("Created new vars.");
		
		BDD switch_true = Env.getBDDValue(SWITCH_VAR , "true");
		BDD allowed_true = Env.getBDDValue(ALLOWED_VAR, "true");
		this.switchVarTrue = switch_true;

		BDD primed_switch_true = Env.prime(switch_true);
		BDD switch_false = switch_true.not();
		BDD primed_allowed_true = Env.prime(allowed_true);

		BDD T1 = trans1.or(this.trans2);
		BDD T2 = primed_switch_true.imp(this.trans2);

		BDD S1 = (switch_false.and(primed_switch_true)).imp(primed_allowed_true);
		BDD S2 = switch_true.imp(primed_switch_true);
		BDD S3 = (trans1.not()).imp(primed_switch_true);

		BDD P1 = primed_allowed_true.biimp((switchBDD.and(this.trans2)).or(allowed_true.and(this.trans2)));

		BDD trans = T1.and(T2.and(S1.and(S2.and(S3.and(P1)))));

		return trans;
	}

	public BDD cPredStates(BDD sysTrans, BDD to) {
		BDD tmpPrimedBdd = Env.prime(to);

		BDD exy;
		BDD tmpAndBdd = tmpPrimedBdd.and(sysTrans);

		exy = tmpAndBdd.exist(this.sysPrimeVars);
		tmpAndBdd.free();

		BDD exyImp = this.envTrans.imp(exy);
		BDD res = exyImp.forAll(this.envPrimeVars);

		tmpPrimedBdd.free();
		exy.free();
		exyImp.free();
		return res;
	}

	BDD computeBridgeSteps(BDD sysTrans, BDD winning) throws IOException {
		printToConsole("Computing bridge steps.");
		BDDVarSet bridgeVars = Env.getEmptySet();
		bridgeVars.unionWith(Env.getVar(SWITCH_VAR).getDomain().set());
		bridgeVars.unionWith(Env.getVar(ALLOWED_VAR).getDomain().set());
		BDD W = Env.unprime(winning);
		W = W.exist(bridgeVars).and(this.switchVarTrue);
		BDD prev = null;

		while (!W.equals(prev)) {
			printToConsole("Another iteration");
			prev = W;
			Env.saveBDD(this.bridgeFolder + "bridge" + String.valueOf(this.bridgeCount) + ".bdd", W, true);
			printToConsole("Saved bridge" + String.valueOf(this.bridgeCount) + ".bdd");
			this.bridgeCount += 1;
			W = cPredStates(sysTrans, prev).exist(Env.globalPrimeVars());
		}
		
		printToConsole("Finished computing bridge steps");
		return W;
	}
	
	BDD extractWinningFromFixpoints() {
	    BDD fixpoints = null;
		try {
			fixpoints = Env.loadBDD(this.outFolder + FIXPOINTS);
		} catch (IOException e) {
			printToConsole("Bridge computation failed - missing new controller fixpoints file.");
			return null;
		}

		int n = 0;
		int m = 0;
		int[] ranks = null;

		try {
			BufferedReader sizesReader = new BufferedReader(new FileReader(this.outFolder + File.separator + SIZES));

			n = Integer.parseInt(sizesReader.readLine());
			m = Integer.parseInt(sizesReader.readLine());
			ranks = new int[n];
			for (int j = 0; j < n; j++) {
				ranks[j] = Integer.parseInt(sizesReader.readLine());
			}

			sizesReader.close();
		} catch (IOException e) {
			printToConsole("Bridge computation failed - missing new controller sizes file.");
			return null;
		}

		BDD[][] X = new BDD[n][m];

		for (int j = 0; j < n; j++) {
			for (int i = 0; i < m; i++) {
				BDD temp = Env.getVar("util_In").getDomain().ithVar(i);
				temp.andWith(Env.getVar("util_Jn").getDomain().ithVar(j));
				temp.andWith(Env.getVar("util_Rn").getDomain().ithVar(ranks[j] - 1));

				BDD XBDD = fixpoints.restrict(temp);
				X[j][i] = Env.prime(XBDD);
				XBDD.free();
				temp.free();
			}
		}
		
		// Extract Y from X on current r
		BDD[] Y = new BDD[n];
		for (int j = 0; j < n; j++) {
			Y[j] = Env.FALSE();
			for (int i = 0; i < m; i++) {
				Y[j].orWith(X[j][i].id());
			}
		}

		BDD winning = Env.TRUE();
		for (int j = 0; j < n; j++) {
			winning.andWith(Y[j]);
		}

		fixpoints.free();
		return winning;
	}
	
	boolean extractNewSpecTranses() {
		BDD trans = null;

		try {
			trans = Env.loadBDD(this.outFolder + TRANS);
		} catch (IOException e) {
			printToConsole("Failed to extract trans bdds.");
			return false;
		}
		
		BDD temp;
		
		temp = Env.getVar("util_0").getDomain().ithVar(0);
		temp.andWith(Env.getVar("util_Jn").getDomain().ithVar(1));
		this.trans2 = trans.restrict(temp);
		temp.free();
		
		temp = Env.getVar("util_0").getDomain().ithVar(1);
		temp.andWith(Env.getVar("util_In").getDomain().ithVar(1));
		this.envTrans = trans.restrict(temp);
		temp.free();
		
		this.trans2 = this.trans2.and(envTrans);
		
		printToConsole("Extracted trans bdds.");
		Env.deleteVar("util_In");
		Env.deleteVar("util_Jn");
		Env.deleteVar("util_Rn");
		Env.deleteVar("util_0");
		
		return true;
	}

	BDD computeBridge() throws IOException, ModuleVariableException {
		printToConsole("Starts computing bridge.");
		BDD winning = extractWinningFromFixpoints();
		if (!extractNewSpecTranses()) {
			return null;
		}

		BDD switchBDD = Env.TRUE();
		try {
			switchBDD = Env.loadBDD(this.outFolder + SWITCH);
			printToConsole("Switch conditions loaded.");
		} catch (IOException e){
			printToConsole("No switch conditions were specified.");
		}

		BDD sysTrans = computeTrans(switchBDD);
		printToConsole("Trans computed.");

		if (winning == null || sysTrans == null) {
			return null;
		}

		Env.saveBDD(this.bridgeFolder + RO, sysTrans, true);
		return computeBridgeSteps(sysTrans, winning);
	}

	boolean isControllerStateInRegion() throws IOException {
		sendFile(this.socket, this.bridgeFolder + "bridge" + String.valueOf(this.bridgeCount - 1) + ".bdd");
		String ret = in.readLine();
		printToConsole("Server returned " + ret);

		return (ret.startsWith(IN_REGION));
	}

	void sendBridge() throws IOException {
		this.out.println(String.valueOf(this.bridgeCount));

		for (int i = 0; i < this.bridgeCount; i++) {
			sendFile(this.socket, this.bridgeFolder + "bridge" + String.valueOf(i) + ".bdd");
		}

		sendFile(this.socket, this.bridgeFolder + RO);
	}

	void sendNewController() throws IOException {
		sendFile(socket, this.outFolder + FIXPOINTS);
		sendFile(socket, this.outFolder + JUSTICE);
		sendFile(socket, this.outFolder + SIZES);
		sendFile(socket, this.outFolder + TRANS);
		sendFile(socket, this.outFolder + VARS);
	}

	void sendControllers() throws IOException {
		sendBridge();
		printToConsole("Bridge controller was sent.");
		sendNewController();
		printToConsole("New controller was sent.");
	}
	
	void removeFolder(File folder) {
	    File[] files = folder.listFiles();
	    if (files != null) { //some JVMs return null for empty dirs
	        for (File f: files) {
	            if (f.isDirectory()) {
	                removeFolder(f);
	            } else {
	                f.delete();
	            }
	        }
	    }
	    folder.delete();
	}
	
	void cleanFiles() {
		File f = new File(this.outFolder + PREV_TRANS);
		if (f.exists()) {
			f.delete();
		}
		f = new File(this.outFolder + PREV_VARS);
		if (f.exists()) {
			f.delete();
		}
		f = new File(this.outFolder + BRIDGE_VARS);
		if (f.exists()) {
			f.delete();
		}
		f = new File(this.outFolder + SWITCH);
		if (f.exists()) {
			f.delete();
		}

		File bridgeFile = new File(this.bridgeFolder);
		removeFolder(bridgeFile);
	}
	
	public void printUpdateError(String errorMsg) {
		printToConsole("Update failed: " + errorMsg);
	}

	public boolean dynamicUpdate() {
		boolean ret = true;
		this.out.println(START_UPDATE);
		printToConsole("Starting dynamic update communication");
		
	error : {
		try {
			receiveFile(this.socket, this.outFolder + PREV_TRANS);
			receiveFile(this.socket, this.outFolder + PREV_VARS);
			receiveFile(this.socket, this.outFolder + BRIDGE_VARS);
		} catch (IOException e) {
			printUpdateError("Failed to receive old controller files");
			ret = false;
			break error;
		}
		printToConsole("Received running controller trans.");
		
		try {
			getSysEnvVars();
		} catch (IOException e) {
			printUpdateError("Failed to initialize vars");
			ret = false;
			break error;
		}
		
		long start = System.currentTimeMillis();
		BDD W = null;
		try {
			W = computeBridge(); // Backtracking algorithm to compute W
		} catch (IOException | ModuleVariableException e) {
			printUpdateError("Bridge computation failed.");
			ret = false;
			break error;
		}
		if (W == null) {
			printUpdateError("Bridge computation failed.");
			ret = false;
			break error;
		}
		printToConsole("Bridge computation succeeded.");
		long finish = System.currentTimeMillis();
		long bridgeTime = finish - start;
		printToConsole("Bridge computation time: " + String.valueOf(bridgeTime) + "ms.");
		addTestData(String.valueOf(bridgeTime) + ",");

		try {
			if (!isControllerStateInRegion()) {
				printToConsole("Dynamic update cannot be performed.");
				ret = false;
				break error;
			}
		} catch (IOException e) {
			printUpdateError("Failed to check if update is possible.");
			ret = false;
			break error;
		}

		printToConsole("Dynamic update can be performed, sending controllers.");
		try {
			sendControllers();
		} catch (IOException e) {
			printUpdateError("Failed to send controllers.");
			ret = false;
			break error;
		}

		printToConsole("Dynamic update completed successfully.");
		
	}
		cleanFiles();

		return ret;
	}
}
