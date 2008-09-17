package wosaic;

/**
 * JAIProcessor.java This file contains the outline for processing images for
 * use in a mosaic.
 */

import java.awt.image.BufferedImage;

import wosaic.utilities.ImageBuffer;
import wosaic.utilities.Mosaic;
import wosaic.utilities.Parameters;
import wosaic.utilities.Pixel;
import wosaic.utilities.Status;

/**
 * @author carl-erik svensson
 * @version 0.1
 */
public class JAIProcessor implements Runnable {

	private int[][][] colorMap;

	/**
	 * This is the Pixel object for the master image.
	 */
	public Pixel master;

	private Mosaic mosaic;

	/**
	 * A set of parameters for this mosaic. It primarily holds resolution
	 * information
	 */
	public Parameters params;

	/**
	 * A reference to the controller object. This is needed for access to the
	 * shared buffer.
	 */
	public ImageBuffer sourcesBuffer;

	private final Status statusObject;

	/**
	 * A grid representing the mosaic as pixel objects.
	 */
	public Pixel[][] wosaic;

	// private JPEGImageDecoder jpegDecoder;

	/**
	 * This constructor should be used by the threaded application.
	 * 
	 * @param mPixel the master image
	 * @param param mosaic parameters
	 * @param buf reference to a shared buffer that contains images to be
	 *            processed
	 * @param mos The Mosaic object we will be filling
	 * @param stat a reference to a shared status object
	 */
	public JAIProcessor(final Pixel mPixel, final Parameters param,
			final ImageBuffer buf, final Mosaic mos, final Status stat) {
		params = param;
		master = mPixel;
		sourcesBuffer = buf;
		mosaic = mos;
		statusObject = stat;
	}

	/**
	 * Split an image up into segments, and calculate its average color.
	 * 
	 * @param numRows Number of rows in the mosaic
	 * @param numCols Number of columns in the mosaic
	 * @param width the width of a segment
	 * @param height the height of a segment
	 * @param mPixel the source image
	 * @return the average colors of each segment
	 */
	public int[][][] analyzeSegments(final int numRows, final int numCols,
			final int width, final int height, final Pixel mPixel) {

		final int[][][] avgColors = new int[numRows][numCols][3];

		for (int r = 0; r < numRows; r++)
			for (int c = 0; c < numCols; c++) {
				final int startY = r * height;
				final int startX = c * width;
				mPixel.getAvgColor(startX, startY, width, height,
						avgColors[r][c]);
			}

		return avgColors;
	}

	/**
	 * Creates a mosaic by analyzing the master image, and then getting images
	 * from the controller's shared buffer to place in the mosaic. This thread
	 * automatically saves the output (this will change).
	 */
	public void run() {
		// System.out.println("Running MosaicThrd...");

		// Calculate average colors of the segments of the master
		colorMap = analyzeSegments(params.resRows, params.resCols, master.width
				/ params.resCols, master.height / params.resRows, master);

		BufferedImage newImg = null;
		while (sourcesBuffer.size() != 0 || !Thread.interrupted()) {

			try {
				newImg = sourcesBuffer.removeFromImageBuffer();
			} catch (final InterruptedException e) {
				return;
			}
			final Pixel newPixel = new Pixel(newImg);

			mosaic.updateMosaic(newPixel, colorMap);
			Thread.yield();
		}

		statusObject.setStatus("Mosaic Complete!");

		// System.out.println("JAIProcessor finished.");
		// System.out.println("Exiting MosaicThrd...");
	}
}