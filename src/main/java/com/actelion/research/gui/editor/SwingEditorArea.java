package com.actelion.research.gui.editor;

import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.gui.clipboard.ClipboardHandler;
import com.actelion.research.gui.dnd.MoleculeDropAdapter;
import com.actelion.research.gui.generic.GenericCanvas;
import com.actelion.research.gui.generic.GenericDrawContext;
import com.actelion.research.gui.generic.GenericPoint;
import com.actelion.research.gui.generic.GenericUIHelper;
import com.actelion.research.gui.swing.SwingDrawContext;
import com.actelion.research.gui.swing.SwingKeyHandler;
import com.actelion.research.gui.swing.SwingMouseHandler;
import com.actelion.research.gui.swing.SwingUIHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;

public class SwingEditorArea extends JPanel implements GenericCanvas {
	private static final int ALLOWED_DROP_ACTIONS = DnDConstants.ACTION_COPY_OR_MOVE;

	private SwingEditorDrawArea mDrawArea;
	private SwingKeyHandler mKeyHandler;

	public class SwingEditorDrawArea extends GenericEditorArea {

		public SwingEditorDrawArea(StereoMolecule mol, int mode, GenericUIHelper helper, GenericCanvas canvas) {
			super(mol, mode, helper, canvas);
		}

		/**
		 * Allows for proper disposal of the Graphics2D object after a synchronous
		 * RepaintManager.repaintImmediately call during a drag-drop operation.
		 * this.initializeDragAndDrop creates a MoleculeDropAdapter whose 
		 * onDropMolecule method calls GenericEditorArea.addPastedOrDropped. 
		 * That method comes back here to getDrawContext. 
		 * which itself is called by MoleculeDropAdapter.  
		 * Disposed of immediately following that call.
		 * 
		 */
		private Graphics2D mTempGraphics;

		public boolean addPastedOrDropped(StereoMolecule mol, GenericPoint p) {
			// BH GenericEditorArea.addPastedOrDropped
			// BH will create a new Graphics2D object and 
			// BH never dispose of it. While this is not a 
			// BH disaster in Java, in JavaScript it is critical
			// BH to dispose properly of all Graphics2D objects.
			// BH Otherwise, the offsets get messed up.
			flushGraphics();
			boolean ret = super.addPastedOrDropped(mol, p);
			flushGraphics();
			return ret;
		}

		private void flushGraphics() {
			if (mTempGraphics != null) {
				mTempGraphics.dispose();
				mTempGraphics = null;
			}
		}

		/**
		 * called from super.addPastedOrDropped via get getDrawContext();
		 */
		protected GenericDrawContext getSwingDrawContext() {
			return new SwingDrawContext(mTempGraphics = (Graphics2D) getGraphics());
		}

	}

	public SwingEditorArea(StereoMolecule mol, int mode) {
		setFocusable(true);

		mDrawArea = new SwingEditorDrawArea(mol, mode, new SwingUIHelper(this), this);

		initializeDragAndDrop(ALLOWED_DROP_ACTIONS);

		SwingMouseHandler mouseHandler = new SwingMouseHandler(mDrawArea);
		addMouseListener(mouseHandler);
		addMouseMotionListener(mouseHandler);
		mouseHandler.addListener(mDrawArea);

		mKeyHandler = new SwingKeyHandler(mDrawArea);
		addKeyListener(mKeyHandler);
		mKeyHandler.addListener(mDrawArea);

		getGenericDrawArea().setClipboardHandler(new ClipboardHandler());
		}

	public SwingKeyHandler getKeyHandler() {
		return mKeyHandler;
		}

	public GenericEditorArea getGenericDrawArea() {
		return mDrawArea;
		}

	@Override
	public GenericDrawContext getDrawContext() {
		// BH only called by GenericEditorArea.addPastedOrDropped
		// BH this allows us to dispose of the graphics after the 
		// BH repaint operation.
		return mDrawArea.getSwingDrawContext();
	}

	@Override
	public double getCanvasWidth() {
		return getWidth();
		}

	@Override
	public double getCanvasHeight() {
		return getHeight();
		}

	@Override
	public int getBackgroundRGB() {
		return getBackground().getRGB();
		}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		((Graphics2D)g).setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

		mDrawArea.paintContent(new SwingDrawContext((Graphics2D)g));
		}

	private void initializeDragAndDrop(int dropAction) {
		if (dropAction != DnDConstants.ACTION_NONE) {
			MoleculeDropAdapter d = new MoleculeDropAdapter() {
				public void onDropMolecule(StereoMolecule mol, Point p) {
					mDrawArea.addPastedOrDropped(mol, (p == null ? null : new GenericPoint(p.x, p.y)));
				}
			};

			new DropTarget(this, dropAction, d, true, new OurFlavorMap());
		}
	}

	// This class is needed for inter-jvm drag&drop. Although not neccessary for standard environments, it prevents
// nasty "no native data was transfered" errors. It still might create ClassNotFoundException in the first place by
// the SystemFlavorMap, but as I found it does not hurt, since the context classloader will be installed after
// the first call. I know, that this depends heavely on a specific behaviour of the systemflavormap, but for now
// there's nothing I can do about it.
	static class OurFlavorMap implements java.awt.datatransfer.FlavorMap {
		@Override
		public java.util.Map<DataFlavor, String> getNativesForFlavors(DataFlavor[] dfs) {
			java.awt.datatransfer.FlavorMap m = java.awt.datatransfer.SystemFlavorMap.getDefaultFlavorMap();
			return m.getNativesForFlavors(dfs);
		}

		@Override
		public java.util.Map<String, DataFlavor> getFlavorsForNatives(String[] natives) {
			java.awt.datatransfer.FlavorMap m = java.awt.datatransfer.SystemFlavorMap.getDefaultFlavorMap();
			return m.getFlavorsForNatives(natives);
		}
	}
}
