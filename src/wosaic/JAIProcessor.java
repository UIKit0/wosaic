package wosaic;

/**
 * JAIProcessor.java
 * 
 * This file contains the outline for processing images
 * for use in a mosaic.
 */

import wosaic.utilities.Parameters;
import wosaic.utilities.Pixel;
import wosaic.utilities.ImageBuffer;
import wosaic.utilities.Mosaic;
import wosaic.utilities.Status;

import java.io.*;
import java.util.Vector;
import java.awt.image.renderable.ParameterBlock;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.awt.image.BufferedImage;
import com.sun.image.codec.jpeg.*;


/**
 * @author carl-erik svensson
 * @version 0.1
 */
public class JAIProcessor implements Runnable {

	public static int TOLERANCE = 30;
	public static int INFINITY = 255*3;
	public static int MINUS_INFINITY = -1 - (255*3);
	public static int SLEEP_TIME = 500;

	/**
	 * A set of parameters for this mosaic.  It primarily holds resolution information
	 */
	public Parameters params;
	
	/**
	 * This is the Pixel object for the master image.
	 */
	public Pixel master;
	
	/**
	 * A grid representing the mosaic as pixel objects.
	 */
	public Pixel[][] wosaic;
	public Mosaic mosaic;
	
	/**
	 * A reference to the controller object.  This is needed for access
	 * to the shared buffer.
	 */
	public ImageBuffer sourcesBuffer;
	int[][][] colorMap;
	
	private Status statusObject;
	//private JPEGImageDecoder jpegDecoder;
	
	
	/**
	 * This constructor should be used by the threaded application.
	 * 
	 * @param mPixel the master image
	 * @param param mosaic parameters
	 * @param buf reference to a shared buffer that contains images to be processed
	 * @param mos
	 * @param stat a reference to a shared status object
	 */
	public JAIProcessor(Pixel mPixel, Parameters param, ImageBuffer buf, Mosaic mos, Status stat) {
		params = param;
		master = mPixel;
		sourcesBuffer = buf;
		mosaic = mos;
		statusObject = stat;
		
		//jpegDecoder = JPEGCodec.createJPEGDecoder(arg0)
	}
	
	
	/**
	 * Creates a mosaic by analyzing the master image, and then getting
	 * images from the controller's shared buffer to place in the mosaic.
	 * This thread automatically saves the output (this will change).
	 */
	public void run() {
		System.out.println("Running MosaicThrd...");
		
		// Calculate average colors of the segments of the master
		colorMap = analyzeSegments(params.resRows, params.resCols, master.width / params.resCols,
				master.height / params.resRows, master);
		
		while(!sourcesBuffer.isComplete || sourcesBuffer.size() != 0) 
		{
			System.out.println("Removing elements from img buf...");
			BufferedImage newImg = sourcesBuffer.removeFromImageBuffer();
			Pixel newPixel = new Pixel(newImg);
			
			mosaic.updateMosaic(newPixel, colorMap);
		}
		
		statusObject.setStatus("Mosaic Complete!");
		
		System.out.println("JAIProcessor finished.");
		System.out.println("Exiting MosaicThrd...");
	}
	
	/**
	 * Writes an image to the specified file.
	 * 
	 * @param img the image to be written to disk
	 * @param file the filename for the image
	 * @param type the encoding for the image
	 * @throws IOException
	 */
	
	public void writeResult(BufferedImage img, String file, String type) throws IOException {
		FileOutputStream os = new FileOutputStream(file);
		//JAI.create("encode", img, os, type, null);
		JPEGImageEncoder encoder = JPEGCodec.createJPEGEncoder(os);
        encoder.encode(img);
        os.close();
	}

	/**
	 * Creates a BufferedImage of the final mosaic from the input sources.
	 * 
	 * @return the mosaic
	 */
	public BufferedImage createImage() {
		
		Pixel[][] sources = mosaic.getPixelArr();
		BufferedImage mImage = master.getBufferedImage();
		
		// Calculate the target height/width
		int height = (int) params.sHeight * params.resRows;
		int width = (int) params.sWidth * params.resCols;
		
		// Create a writable raster
		Raster raster;
		WritableRaster wr;
		
		//DBG
		System.out.println("Initializing mosaic rasters...");
		
		try {
			raster = mImage.getData();
			wr = raster.createCompatibleWritableRaster(width, height);
		} catch (Exception e) {
			System.out.println(e);
			System.out.println("We're running out of memory!");
			return null;
		}
		
		//DBG
		System.out.println("About to iterate through the mosaic pieces...");
		
		// Create the resulting image!
		for (int r=0; r < params.resRows; r++) {
			for (int c=0; c < params.resCols; c++) {
				
				try {
					// Scale the source
					sources[r][c].scaleSource(params.sWidth, params.sHeight);
					
					// Copy the pixels
					wr.setRect(c * sources[r][c].width, r * sources[r][c].height, sources[r][c].getRaster());
				} catch (Exception e) {
					System.out.println(e);
					System.out.println("Running out of memory! ... Continuing");
					System.gc();
				}
			}
		}
		
		//DBG
		System.out.println("Setting the raster data...");
		
		BufferedImage result = null;
		
		try {
			result = new BufferedImage(params.getMasterWidth(), params.getMasterHeight(), BufferedImage.TYPE_INT_RGB);
			result.setData(wr);
		} catch (Exception e) {
			System.out.println("Writing result failed!");
			System.out.println(e);
		}
		
		return result;
	}

	
	/**
	 * Split an image up into segments, and calculate its average color.
	 * 
	 * @param numRows
	 * @param numCols
	 * @param width the width of a segment
	 * @param height the height of a segment
	 * @param mPixel the source image
	 * @return the average colors of each segment
	 */
	public int[][][] analyzeSegments(int numRows, int numCols, int width, int height, Pixel mPixel) {
		int[][][] avgColors = new int[numRows][numCols][3];
		
		for (int r=0; r < numRows; r++){
			for(int c=0; c < numCols; c++) {
				int startY = r * height;
				int startX = c * width;
				
				mPixel.getAvgColor(startX, startY, width, 
									height, avgColors[r][c]);
			}
		}
		
		return avgColors;
	}
}
