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

package tau.smlab.syntech.counterstrategy.ui.zestviewer;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.gef.zest.fx.ZestProperties;
import org.eclipse.gef.zest.fx.jface.IGraphAttributesProvider;
import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

import tau.smlab.syntech.counterstrategy.model.CounterStrategyGraphStruct.CSNodeType;
import tau.smlab.syntech.counterstrategy.ui.model.ModelNode;

public class ZestLabelProvider extends LabelProvider implements IColorProvider, IGraphAttributesProvider {
	@Override
	public String getText(Object element) {
		if (element instanceof ModelNode) {
			ModelNode myNode = (ModelNode) element;
			return myNode.getName();
		}
		// // Not called with the IGraphEntityContentProvider
		// if (element instanceof ModelConnection) {
		// ModelConnection myConnection = (ModelConnection) element;
		// return myConnection.getLabel();
		// }

		return "";
		// if (element instanceof EntityConnectionData) {
		// //EntityConnectionData test = (EntityConnectionData) element;
		// return "";
		// }
		// throw new RuntimeException("Wrong type: "
		// + element.getClass().toString());
	}

	// @Override
	// public IFigure getFigure(Object element) {
	/*
	 * if (element instanceof ModelNode) { MarriageFigure f = new MarriageFigure();
	 * f.setSize(f.getPreferredSize()); return f; } // TODO - providing these
	 * figures will result in needing to manage them // (selection color, events,
	 * drawing the text in them, etc'). How can we just change the shape without
	 * changing the // other aspects of the element? if (element instanceof
	 * ModelNode) { ModelNode elem = (ModelNode) element; if
	 * (!elem.getSelfCsNode().isConcrete) { if (elem.getSelfCsNode().type ==
	 * CSNodeType.CYCLE) { Ellipse ellipse = new Ellipse(); //
	 * ellipse.setBackgroundColor(ColorConstants.lightBlue); //
	 * ellipse.setOutline(true); ellipse.setSize(30, 30); return ellipse; } else if
	 * (elem.getSelfCsNode().type == CSNodeType.ATTRACTOR) { RectangleFigure r = new
	 * RectangleFigure(); r.setSize(30, 30); return r; } else if
	 * (elem.getSelfCsNode().type == CSNodeType.INITIAL) { Ellipse ellipse = new
	 * Ellipse(); ellipse.setSize(20, 20); return ellipse; } } }
	 */
	//
	// return null;
	// }

	// public static class MarriageFigure extends PolygonShape {
	// public MarriageFigure() {
	// Rectangle r = new Rectangle(0, 0, 50, 50);
	// setStart(r.getTop());
	// addPoint(r.getTop());
	// addPoint(r.getLeft());
	// addPoint(r.getBottom());
	// addPoint(r.getRight());
	// addPoint(r.getTop());
	// setEnd(r.getTop());
	// //setFill(true);
	// setBackgroundColor(ColorConstants.lightGray);
	// // Add 1 to include width of the border otherwise
	// // the diamond's right and bottom tips are missing 1 pixel
	// setPreferredSize(r.getSize().expand(1, 1));
	// // hook the listeners to the marriage figure
	//// new FigureMover(this);
	// }
	// }

	// @Override
	// public void selfStyleConnection(Object element, GraphConnection connection) {
	// if (element instanceof ModelConnection) {
	// ModelConnection elem = (ModelConnection) element;
	// if (elem.getCsEdge().isConcrete &&
	// elem.getCsEdge().type == CSEdgeType.BETWEEN_NODES) {
	// // using ZestStyles.CONNECTIONS_DASH here will cause a crash in SWT when
	// refreshing the viewer.
	// connection.setLineStyle(SWT.LINE_DASH);
	// }
	// // default is solid line
	// }
	//
	// }

	// @Override
	// public Color getBorderColor(Object entity) {
	// if (entity instanceof ModelNode) {
	// ModelNode node = (ModelNode) entity;
	// if (node.getSelfCsNode().type == CSNodeType.CYCLE) {
	// return ColorConstants.red;
	// } else if (node.getSelfCsNode().type == CSNodeType.INITIAL) {
	// return ColorConstants.black;
	// } else if (node.getSelfCsNode().type == CSNodeType.ATTRACTOR) {
	// return ColorConstants.blue;
	// }
	// }
	// return null;
	// }
	//
	// @Override
	// public Color getBorderHighlightColor(Object entity) {
	// if (entity instanceof ModelNode) {
	// ModelNode node = (ModelNode) entity;
	// if (node.getSelfCsNode().type == CSNodeType.CYCLE) {
	// return ColorConstants.red;
	// } else if (node.getSelfCsNode().type == CSNodeType.INITIAL) {
	// return ColorConstants.black;
	// } else if (node.getSelfCsNode().type == CSNodeType.ATTRACTOR) {
	// return ColorConstants.blue;
	// }
	// }
	// return null;
	// }

	// @Override
	// public int getBorderWidth(Object entity) {
	// // TODO Auto-generated method stub
	// return 2;
	// }

	@Override
	public Color getForeground(Object element) {
		if (element instanceof ModelNode) {
			Display display = Display.getCurrent();
			return display.getSystemColor(SWT.COLOR_BLACK);
		}
		return null;
	}

	@Override
	public Color getBackground(Object element) {
		if (element instanceof ModelNode) {
			Display display = Display.getCurrent();
			return display.getSystemColor(SWT.COLOR_WHITE);
		}
		return null;
	}

	@Override
	public Map<String, Object> getEdgeAttributes(Object sourceNode, Object targetNode) {
		// if (element instanceof ModelConnection) {
		// ModelConnection elem = (ModelConnection) element;
		// if (elem.getCsEdge().isConcrete &&
		// elem.getCsEdge().type == CSEdgeType.BETWEEN_NODES) {
		// // using ZestStyles.CONNECTIONS_DASH here will cause a crash in SWT when
		// refreshing the viewer.
		// connection.setLineStyle(SWT.LINE_DASH);
		// }
		// // default is solid line
		// }

		Map<String, Object> edgeAttributes = new HashMap<>();

		ModelNode source = (ModelNode) sourceNode;
		ModelNode target = (ModelNode) targetNode;

		if (source.getSelfCsNode().isConcrete && target.getSelfCsNode().isConcrete) {
			edgeAttributes.put(ZestProperties.CURVE_CSS_STYLE__E, "-fx-border-style: dashed; -fx-border-width: 1");
		}

		return edgeAttributes;
	}

	@Override
	public Map<String, Object> getGraphAttributes() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, Object> getNestedGraphAttributes(Object nestingNode) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, Object> getNodeAttributes(Object node) {

		Map<String, Object> nodeAttributes = new HashMap<>();
		
		String css = ""
				+ "-fx-stroke-width: 2; "
				+ "-fx-fill: azure; "
				+ "-fx-alignment: center; "
				+ "-fx-start-margin: 5"
				+ "-fx-border-radius: 5; ";

		ModelNode mnode = (ModelNode) node;
		if (mnode.getSelfCsNode().type == CSNodeType.CYCLE) {
			css += "-fx-stroke: red";
		} else if (mnode.getSelfCsNode().type == CSNodeType.INITIAL) {
			css += "-fx-stroke: black";
		} else if (mnode.getSelfCsNode().type == CSNodeType.ATTRACTOR) {
			css += "-fx-stroke: blue";
		}
		nodeAttributes.put(ZestProperties.SHAPE_CSS_STYLE__N, css);
		return nodeAttributes;
	}
}