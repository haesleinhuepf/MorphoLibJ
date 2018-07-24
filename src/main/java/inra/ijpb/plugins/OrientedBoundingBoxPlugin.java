/*-
 * #%L
 * Mathematical morphology library and plugins for ImageJ/Fiji.
 * %%
 * Copyright (C) 2014 - 2017 INRA.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
package inra.ijpb.plugins;


import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.Map;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import inra.ijpb.geometry.OrientedBox2D;
import inra.ijpb.label.LabelImages;
import inra.ijpb.measure.region2d.OrientedBoundingBox2D;

public class OrientedBoundingBoxPlugin implements PlugIn
{
    // ====================================================
    // Global Constants
    
    
    // ====================================================
    // Class variables
 
	// ====================================================
    // Calling functions 
    
	/* (non-Javadoc)
     * @see ij.plugin.PlugIn#run(java.lang.String)
     */
	public void run(String args)
	{
		// Open a dialog to choose:
		// - a label image
		// - a set of weights
		int[] indices = WindowManager.getIDList();
		if (indices==null)
		{
			IJ.error("No image", "Need at least one image to work");
			return;
		}
		
		// create the list of image names
		String[] imageNames = new String[indices.length];
		for (int i=0; i<indices.length; i++)
		{
			imageNames[i] = WindowManager.getImage(indices[i]).getTitle();
		}
		
		// name of selected image
		String selectedImageName = IJ.getImage().getTitle();

		// create the dialog
		GenericDialog gd = new GenericDialog("Bounding Box");
		gd.addChoice("Label Image:", imageNames, selectedImageName);
		gd.addCheckbox("Show Overlay Result", true);
		gd.addChoice("Image to overlay:", imageNames, selectedImageName);
		gd.showDialog();
		
		if (gd.wasCanceled())
			return;
		
		// set up current parameters
		int labelImageIndex = gd.getNextChoiceIndex();
		ImagePlus labelImage = WindowManager.getImage(labelImageIndex + 1);
		boolean showOverlay = gd.getNextBoolean();
		int resultImageIndex = gd.getNextChoiceIndex();
		
		// check if image is a label image
		if (!LabelImages.isLabelImageType(labelImage))
		{
            IJ.showMessage("Input image should be a label image");
            return;
        }

        // Execute the plugin
		OrientedBoundingBox2D op = new OrientedBoundingBox2D();
		Map<Integer, OrientedBox2D> boxes = op.analyzeRegions(labelImage);
        ResultsTable results = op.createTable(boxes);
        
		// show result
    	String tableName = labelImage.getShortTitle() + "-BBox"; 
    	results.show(tableName);
    	
		// Check if results must be displayed on an image
		if (showOverlay)
		{
			// find image for displaying geometric overlays
			ImagePlus resultImage = WindowManager.getImage(resultImageIndex + 1);
			showResultsAsOverlay(boxes, resultImage);
		}
    }
	
	/**
	 * Display the result of oriented bounding box extraction as overlay on a
	 * given image.
	 * 
	 * @param target
	 *            the ImagePlus used to display result
	 * @param results
	 *            the associative map between region label and bounding box
	 */
	private void showResultsAsOverlay(Map<Integer, OrientedBox2D> results, ImagePlus target)	
	{
		// get spatial calibration of target image
		Calibration calib = target.getCalibration();
		
		// create overlay
		Overlay overlay = new Overlay();
		Roi roi;
		
		// add each box to the overlay
		for (int label : results.keySet()) 
		{
			// Coordinates of inscribed circle, in pixel coordinates
			OrientedBox2D box = results.get(label);
			roi = createUncalibratedRoi(box, calib);
			
			// draw inscribed circle
			roi.setStrokeColor(Color.GREEN);
			overlay.add(roi);
		}
		
		target.setOverlay(overlay);
	}

	/**
	 * Determines the ROI corresponding to the uncalibrated version of this
	 * box, assuming it was defined in calibrated coordinates.
	 * 
	 * @param box
	 *            the oriented box in calibrated coordinates
	 * @param calib
	 *            the spatial calibration to consider
	 * @return the ROI corresponding to the box
	 */
	private final static Roi createUncalibratedRoi(OrientedBox2D box, Calibration calib)
	{
		Point2D center = box.center();
		double xc = center.getX();
		double yc = center.getY();
		double dx = box.length() / 2;
		double dy = box.width() / 2;
		double theta = Math.toRadians(box.orientation());
		double cot = Math.cos(theta);
		double sit = Math.sin(theta);
		
		// coordinates of polygon ROI
		float[] xp = new float[4];
		float[] yp = new float[4];
		
		// iterate over vertices
		double x, y;
		x = xc + dx * cot - dy * sit;
		y = yc + dx * sit + dy * cot;
		xp[0] = (float) ((x - calib.xOrigin) / calib.pixelWidth);
		yp[0] = (float) ((y - calib.yOrigin) / calib.pixelHeight);
		x = xc - dx * cot - dy * sit;
		y = yc - dx * sit + dy * cot;
		xp[1] = (float) ((x - calib.xOrigin) / calib.pixelWidth);
		yp[1] = (float) ((y - calib.yOrigin) / calib.pixelHeight);
		x = xc - dx * cot + dy * sit;
		y = yc - dx * sit - dy * cot;
		xp[2] = (float) ((x - calib.xOrigin) / calib.pixelWidth);
		yp[2] = (float) ((y - calib.yOrigin) / calib.pixelHeight);
		x = xc + dx * cot + dy * sit;
		y = yc + dx * sit - dy * cot;
		xp[3] = (float) ((x - calib.xOrigin) / calib.pixelWidth);
		yp[3] = (float) ((y - calib.yOrigin) / calib.pixelHeight);
		return new PolygonRoi(xp, yp, 4, Roi.POLYGON);
	}
	
//	private final static Roi createRoi(Box2D box)
//	{
//		// Coordinates of box, in pixel coordinates
//		double xmin = box.getXMin();
//		double xmax = box.getXMax();
//		double ymin = box.getYMin();
//		double ymax = box.getYMax();
//		
//		return new Roi(xmin, ymin, xmax - xmin, ymax - ymin);
//	}
}