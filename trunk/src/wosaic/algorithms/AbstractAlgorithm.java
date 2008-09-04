/**
 * 
 */
package wosaic.algorithms;

import wosaic.utilities.Mosaic;
import wosaic.utilities.Pixel;

/**
 * Represents the base class for all mosaic matching algorithms. Each algorithm
 * is responsible for analyzing a pool of images from a plugin and fitting them
 * appropriately into the mosaic.
 * 
 * @author swegner2
 */
public abstract class AbstractAlgorithm {
	/**
	 * Default constructor for AbstractAlgorithm class.
	 * 
	 * @param mos The mosaic object that should be filled
	 * @param colorMap Color data for the source image regions
	 */
	public AbstractAlgorithm(Mosaic mos, int[][][] colorMap) {
		Mos = mos;
		ColorMap = colorMap;
	}

	/**
	 * Consider a new Pixel that has been generated from the source plugin. Each
	 * algorithm subclass should handle this appropriately
	 * 
	 * @param pixel The new pixel
	 */
	abstract public void AddPixel(Pixel pixel);

	/**
	 * The mosaic object that we will be filling
	 */
	protected Mosaic Mos;

	/**
	 * Color data for each Pixel region of the source image
	 */
	protected int[][][] ColorMap;

	/**
	 * Return the distance "score" for a given pixel and source region. This
	 * uses the default scoring algorithm (a linear combination of red, green,
	 * and blue channel distance). Other algorithms can override this if they
	 * prefer.
	 * 
	 * @param pixel The input pixel to compare
	 * @param r The row of the pixel region in the source
	 * @param c The column of the pixel region in the source
	 * @return A positive distance "score".
	 */
	protected int getMatchScore(Pixel pixel, int r, int c) {
		int[] avgColors = new int[3];
		pixel.getAvgImageColor(avgColors);
		final int rmDiff = Math.abs(avgColors[0] - ColorMap[r][c][0]);
		final int gmDiff = Math.abs(avgColors[1] - ColorMap[r][c][1]);
		final int bmDiff = Math.abs(avgColors[2] - ColorMap[r][c][2]);

		// Keep a score that dictates how good a match is
		// Like in golf, a lower score is better. This is simply
		// made up of the total difference in each channel, added
		// together. Other weights can be added in the future.
		return rmDiff + gmDiff + bmDiff;
	}
}
