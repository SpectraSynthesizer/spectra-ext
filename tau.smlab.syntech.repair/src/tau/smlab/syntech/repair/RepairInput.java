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

package tau.smlab.syntech.repair;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.jtlv.Env;

/**
 * Reads repairs from files
 * 
 * @author shalom
 *
 */
public class RepairInput extends RepairIO {

	public RepairInput(File folder) {
		if (!folder.exists()) {
			System.out.println("Warning! No repair input folder");
		}
		mFolder = folder;
	}
	
	public String getSpecFile() {
		File genIndex = new File(mFolder, generalIndexFilename);
		String specFilename = "";

		try {
			Scanner s = new Scanner(genIndex);
			while (s.hasNext() && specFilename.equals("")) {
				if (s.next().equals(tokFilename)) {
					specFilename = s.next();
				}
			}
			s.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return specFilename;		
	}
	
	public boolean hasRemovedAsm() {
		File genIndex = new File(mFolder, generalIndexFilename);
		boolean has = false;

		try {
			Scanner s = new Scanner(genIndex);
			while (s.hasNext() && !has) {
				if (s.next().equals(tokRemovedAsm)) {
					has = true;
				}
			}
			s.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return has;		
	}

	public long removedAsmNum() {
		File genIndex = new File(mFolder, generalIndexFilename);
		long number = -1;

		try {
			Scanner s = new Scanner(genIndex);
			while (s.hasNext() && number==-1) {
				if (s.next().equals(tokRemovedAsm)) {
					number = s.nextLong();
				}
			}
			s.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return number;		
	}
	public boolean hasIndex(long i) {
		File indexFile = new File(mFolder, formatIndexFile(i));
		return indexFile.exists();
	}
	
	public String getEnvironmentPath(long i) {
		String envPath = "";
		File indexFile = new File(mFolder, formatIndexFile(i));
		try {
			Scanner s = new Scanner(indexFile);
			while (s.hasNext() && envPath.equals("")) {
				if (s.next().equals(tokEnv)) {
					envPath = s.next();
				}
			}
			s.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return envPath;
	}

	private long getBDDNum(long i) {
		long bdds = 0;
		File indexFile = new File(mFolder, formatIndexFile(i));
		try {
			Scanner s = new Scanner(indexFile);
			while (s.hasNext() && bdds==0) {
				String kind = s.next();
				if (kind.equals(tokBDDs)) {
					bdds = s.nextLong();
				} else {
					s.next();
				}
			}
			s.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return bdds;
	}
	
	public List<String> getBDDPaths(long i) {
		List<String> paths = new ArrayList<String>();
		
		File indexFile = new File(mFolder, formatIndexFile(i));
		try {
			Scanner s = new Scanner(indexFile);
			while (s.hasNext()) {
				String kind = s.next();
				if (kind.equals(tokGlobaly) || kind.equals(tokGlobalyFinaly) || kind.equals(tokInit)) {
					paths.add(s.next());
				} else {
					s.next();
				}
			}
			s.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return paths;
	}

	public List<BehaviorInfo> getBehaviors(long i) {
		List<BehaviorInfo> behaviors = new ArrayList<BehaviorInfo>();
		
		File indexFile = new File(mFolder, formatIndexFile(i));
		try {
			Scanner s = new Scanner(indexFile);
			while (s.hasNext()) {
				String kind = s.next();
				switch(kind) {
				case tokGlobalyFinaly:
					behaviors.add(new BehaviorInfo(null, null, Env.loadBDD(mFolder.getAbsolutePath() + "/" + s.next()), null, null, 0, false));
					break;
				case tokGlobaly:
					behaviors.add(new BehaviorInfo(null, Env.loadBDD(mFolder.getAbsolutePath() + "/" + s.next()), null, null, null, 0, false));
					break;
				case tokInit:
					behaviors.add(new BehaviorInfo(Env.loadBDD(mFolder.getAbsolutePath() + "/" + s.next()), null, null, null, null, 0, false));
					break;
				default:
					s.next();
					break;
				}
			}
			s.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e2) {
			e2.printStackTrace();
		}
		return behaviors;
	}
	
	public boolean isValid(long i) {
		boolean v = true;

		v &= hasIndex(i);
		long howMany = getBDDNum(i);		
		List<String> bddPaths = getBDDPaths(i);
		
		v &= (howMany==bddPaths.size());
		for (String p : bddPaths) {
			v &= (new File(mFolder, p)).exists();
		}
		return v;
	}
}
