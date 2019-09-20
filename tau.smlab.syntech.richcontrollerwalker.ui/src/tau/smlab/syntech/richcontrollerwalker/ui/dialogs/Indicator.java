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

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.wb.swt.ResourceManager;

public class Indicator extends Canvas {
    private boolean checked = false;
    private Image imageOff = ResourceManager.getPluginImage("tau.smlab.syntech.richcontrollerwalker.ui", "images/IndOff.png");
    private Image imageOn = ResourceManager.getPluginImage("tau.smlab.syntech.richcontrollerwalker.ui", "images/IndOn.png");

    public Indicator(Composite parent, int style, Display display) {
        super(parent, style);

        this.addPaintListener(new PaintListener() {
            @Override
            public void paintControl(PaintEvent e) {
                if(checked) {
                	e.gc.setBackground(display.getSystemColor(SWT.COLOR_GREEN)); 
                	e.gc.drawImage(imageOn, 0, 0); 
                	
                }
                else {
                	e.gc.setBackground(display.getSystemColor(SWT.COLOR_RED)); 
                	e.gc.drawImage(imageOff, 0, 0); 
                }
            }
        });
    }
    
    public void setState(boolean state) {
    	checked = state;
    	redraw();
    }
}
