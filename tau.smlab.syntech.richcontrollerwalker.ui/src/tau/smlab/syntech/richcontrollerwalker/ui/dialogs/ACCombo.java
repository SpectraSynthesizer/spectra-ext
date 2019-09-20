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

import java.util.Arrays;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

public class ACCombo extends Combo {
	public ACCombo acCombo;
    public ACCombo(Composite parent, int style) {
		super(parent, style);
		acCombo = this;
		this.addListener(SWT.KeyDown, new Listener() {
	        @Override
	        public void handleEvent(Event arg0) {
	            if(arg0.character == SWT.SPACE || arg0.character == SWT.CR) {
            		//Auto-complete is supported when pressing space or enter.
                    String[] filtered = Arrays.stream(acCombo.getItems()).filter(s -> s.startsWith(acCombo.getText())).toArray(String[]::new);
                    String newText = getLongestCommonPrefix(filtered);
                    acCombo.setText(newText);
                    int len = newText.length();
                    acCombo.setSelection(new Point(len, len));
                    acCombo.foundMatches(filtered.length);
	            }
	        }
	    });
   		this.addVerifyListener(new VerifyListener() {
            @Override
            public void verifyText(VerifyEvent e) {
            	e.doit = e.keyCode != SWT.SPACE && e.keyCode != SWT.CR;
            }
        });
	}
    
    /**
     * Called after auto-complete was done.
     * @param numMatches - how many matches were found.
     * Note that numMatches=1 means there is an exact match among the items array.
     */
    protected void foundMatches(int numMatches) {
    	//May override.
	}
    
    /**
     * Checks if the current text (which is returned by getText()) is valid and the widget is enabled.
     * @return returns true iff the text value equals one of the values in the items array
     * and the widget is enabled.
     */
    protected boolean isTextValid() {
    	if (!this.getEnabled()) return false;
    	String thisText = this.getText();
    	for (String str : this.getItems()) {
    		if (thisText.equals(str)) return true;
    	}
    	return false;
    }
    
    /**
     * Returns the longest common prefix among string array.
     * @param array of strings.
     * @return the longest common prefix among string array.
     */
	public static String getLongestCommonPrefix(String[] strings) {
	    if (strings.length == 0) return "";
	    for (int prefixLen = 0; prefixLen < strings[0].length(); prefixLen++) {
	        char c = strings[0].charAt(prefixLen);
	        for (int i = 1; i < strings.length; i++) {
	            if ( prefixLen >= strings[i].length() ||
	                 strings[i].charAt(prefixLen) != c ) {
	                return strings[i].substring(0, prefixLen);
	            }
	        }
	    }
	    return strings[0];
	}
	
	@Override
    protected void checkSubclass() {
    	//Override and do nothing.
    }
}