package wosaic;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.Iterator;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.Graphics;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.BevelBorder;

import wosaic.utilities.Facebook;
import wosaic.utilities.Mosaic;
import wosaic.utilities.Pixel;
import wosaic.utilities.MosaicListener;
import wosaic.utilities.MosaicEvent;
import wosaic.utilities.SourcePlugin;
import wosaic.utilities.Status;

import javax.swing.JScrollPane;
import java.awt.GridLayout;
import java.awt.Rectangle;

/**
 * The User interface for Wosaic, and application to create a photo-mosaic
 * using pictures drawn from Flickr.
 * @author scott
 */
public class WosaicUI extends JApplet {

	/* A 2-d array of JLabels that we will add to our
	 * UI and update pixels during processing.
	 */
	private Facebook fb;
	private Sources sources;
	private Status statusObject;
	
	/**
	 * Action queried to create the Mosaic
	 * @author scott
	 */
	public class GenerateMosaicAction extends AbstractAction {

		/**
		 * Generated by Eclipse
		 */
		private static final long serialVersionUID = -4914549621520228000L;

		Controller cont = null;

		Component parent = null;

		GenerateMosaicAction(final Component parent) {
			super();
			this.parent = parent;
		}

		/**
		 * Call the appropriate members to generate a Mosaic.
		 * 
		 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
		 */
		public void actionPerformed(final ActionEvent evt) {
			
			// Validate inputs
			JOptionPane jOptionsPane = new JOptionPane("Error", JOptionPane.ERROR_MESSAGE);
			BufferedImage bi = null;
			int resolution;
			double multiplier;
			boolean useFacebook = false;
			boolean useFlickr = false;
			int numSources = 0;
			
			statusObject.setStatus("Validating Inputs...");
			statusObject.setProgress(0);
			statusObject.setIndeterminate(true);
			
			// Check the filename
			try {
				System.out.println("Opening our source image to grab metadata...");
				File file = new File(FileField.getText());
			 	bi = ImageIO.read(file);
			} catch (Exception e) {
				jOptionsPane.showMessageDialog(this.parent, "Please enter a valid source image.");
				statusObject.setStatus("");
				return;
			}
			
			// Check the search query
			boolean flickrEnable = sources.isEnabled(Sources.FLICKR);
			
			if (flickrEnable && SearchField.getText().length() == 0) {
				jOptionsPane.showMessageDialog(this.parent, "Please enter a search term.");
				statusObject.setStatus("");
				return;
			} else if (flickrEnable) {
				FlickrService2 fl = (FlickrService2) sources.findType(Sources.FLICKR);
				if (fl != null) {
					fl.setSearchString(SearchField.getText());
				} else {
					System.out.println("FlickrService2 was not found in the sources list!");
					statusObject.setStatus("ERR: Flickr was not enabled...");
					return;
				}
			}
			
			// Check that the resolution is a number
			try {
				resolution = Integer.parseInt(ResolutionField.getText());
			} catch (Exception e) {
				jOptionsPane.showMessageDialog(this.parent, "Please enter a number for the resolution.");
				statusObject.setStatus("");
				return;
			}
			
			// Initialize a controller object and run it.
			final WosaicUI wos = (WosaicUI) parent;
			final int numThrds = WosaicUI.THREADS;
			int target = WosaicUI.TARGET;

			try {
				// FIXME: Infer xDim and yDim from the image size.
				final int xDim;
				final int yDim;
				
				// Check the dimensions of advanced options
				if (DimensionsMultiple.isSelected()) {
					try {
						multiplier = Double.parseDouble(DimensionsMultipleField.getText());
					} catch (Exception e) {
						jOptionsPane.showMessageDialog(this.parent, "Please enter a valid number for the multiplier.");
						statusObject.setStatus("");
						return;
					}
					xDim = (int) (bi.getWidth() * multiplier);
					yDim = (int) (bi.getHeight() * multiplier);

				} else if(DimensionsOriginal.isSelected()) {
					xDim = bi.getWidth();
					yDim = bi.getHeight();
					
				} else { // DimensionsCustom.isSelected()
					int parsedX, parsedY;
					try {
						// First stored parsed values into temp variables, because
						// xDim and yDim are marked final-- they need to be set outside
						// the catch statement to avoid compiler errors.
						parsedX = Integer.parseInt(DimensionsCustomFieldX.getText());
						parsedY = Integer.parseInt(DimensionsCustomFieldY.getText());
					} catch (Exception e) {
						jOptionsPane.showMessageDialog(this.parent, "Please enter a valid number for the dimensions.");
						statusObject.setStatus("");
						return;
					}
					xDim = parsedX;
					yDim = parsedY;
				}

				// Check what sources we use
				ArrayList<SourcePlugin> enSrcs = sources.getEnabledSources();
				
				for (int i = 0; i < enSrcs.size(); i++) {
					String err = enSrcs.get(i).validateParams();
					if (err != null) {
						jOptionsPane.showMessageDialog(this.parent, err);
						statusObject.setStatus("");
						return;
					}
					numSources++;
				}
				
				if (numSources == 0) {
					jOptionsPane.showMessageDialog(this.parent, "Please choose at least one source in the Advanced Options!");
					statusObject.setStatus("");
					return;
				}
				
				// FIXME: Infer numRows and numCols from resolution and dims
				final int numRows;
				final int numCols;
				if (xDim <= yDim) {
					numRows = resolution;
					numCols = (int) ((double) xDim / yDim * numRows);
				} else {
					numCols = resolution;
					numRows = (int) ((double) yDim / xDim * numCols);
				}

				final String search = wos.SearchField.getText();
				final String mImage = wos.FileField.getText();

				Mosaic mosaic = new Mosaic();
				SaveAction.addMosaic(mosaic);
				
				// Create a listener class
				class MosaicListen implements MosaicListener {
					
					Mosaic mos;
					
					MosaicListen(Mosaic m) {
						mos = m;
					}
					
					/**
					 * Updates the UI when we get word that the mosaic has changed.
					 */
					public void mosaicUpdated(MosaicEvent e) {
						ArrayList<Point> coords = e.Coords;
						for (int i = 0; i < coords.size(); i++) {
							int row = coords.get(i).x;
							int col = coords.get(i).y;
							
							//FIXME: We should probably hold onto the graphics object
							// as an instance variable, so we don't need to make this
							// method call each time.
							Graphics graph = ContentPanel.getGraphics();
							
							//FIXME: Do we need a buffered image here?
							BufferedImage img = mos.getPixelAt(row, col).getBufferedImage();
							
							int pixWidth = xDim / numCols;
							int pixHeight = yDim / numRows;
							//FIXME: Use pre-sclaed images so we don't need to scale them every time
							graph.drawImage(img, pixHeight*col, pixWidth*row, pixWidth, pixHeight, ContentPanel);
						}
					}
					
				}
				
				MosaicListen listener = new MosaicListen(mosaic);
				mosaic.addMosaicEventListener(listener);
				
				System.out.println("Initialize our controller.");
				cont = new Controller(target, numThrds, numRows, numCols, xDim,
						yDim, search, mImage, mosaic, sources, statusObject);
				System.out.println("Call our controller thread");
				final Thread t = new Thread(cont);
				t.run();
				
				SaveButton.setEnabled(true);
				statusObject.setStatus("Generating Mosaic...");
				
			} catch (Exception ex) {
				System.out.println(ex.getMessage());
			}
		}
	}

	/**
	 * Creates and shows a modal open-file dialog.
	 * 
	 * @author scott
	 * 
	 */
	public class OpenFileAction extends AbstractAction {
		/**
		 * Generated by Eclipse
		 */
		private static final long serialVersionUID = -3576454135128663771L;

		JFileChooser chooser;

		/**
		 * The image file chosen to be the source of the Wosaic
		 */
		public File file = null;

		Component parent;

		OpenFileAction(final Component parent, final JFileChooser chooser) {
			super("Open...");
			this.chooser = chooser;
			this.parent = parent;
		}

		/**
		 * Retrieve the file to open
		 * 
		 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
		 */
		public void actionPerformed(final ActionEvent evt) {
			
			// Show dialog; this method does not return until dialog is closed
			chooser.showOpenDialog(parent);
			// Get the selected file and put it into our text field.
			file = chooser.getSelectedFile();
			
			// File could be null if the user clicked cancel or something
			if (file != null) {
				((WosaicUI) parent).FileField.setText(file.getAbsolutePath());
			}
		}
	}

	/**
	 * Creates and shows a modal open-file dialog.
	 * 
	 * @author carl
	 * 
	 */
	public class SaveFileAction extends AbstractAction {

		JFileChooser chooser;
		private Mosaic mos;

		/**
		 * The image file chosen to be the source of the Wosaic
		 */
		public File file = null;

		Component parent;

		SaveFileAction(final Component parent, final JFileChooser chooser) {
			super("Save...");
			this.chooser = chooser;
			this.parent = parent;
		}
		
		/**
		 * Associate a mosaic with this save action.
		 * @param m
		 */
		public void addMosaic(Mosaic m) {
			this.mos = m;
		}

		/**
		 * Retrieve the file to open
		 * 
		 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
		 */
		public void actionPerformed(final ActionEvent evt) {
			// Show dialog; this method does not return until dialog is closed
			chooser.showSaveDialog(parent);
			
			// Get the selected file and save it
			file = chooser.getSelectedFile();
			
			BufferedImage img = mos.createImage();
			try {
				mos.save(img, file.getAbsolutePath(), "JPEG");
			} catch (Exception e) {
				System.out.println("Save failed: ");
				System.out.println(e);
			}
			
		}
	}
	
	/**
	 * Action event listener for the Dimensions radio buttons.
	 * @author carl-eriksvensson
	 *
	 */
	public class RadioButtonPress extends AbstractAction {

		public void actionPerformed(ActionEvent e) {
			if (DimensionsMultiple.isSelected()) {
				DimensionsCustomFieldX.setEnabled(false);
				DimensionsCustomFieldY.setEnabled(false);
				DimensionsMultipleField.setEnabled(true);
			} else if (DimensionsCustom.isSelected()) {
				DimensionsMultipleField.setEnabled(false);
				DimensionsCustomFieldX.setEnabled(true);
				DimensionsCustomFieldY.setEnabled(true);
			} else {
				DimensionsMultipleField.setEnabled(false);
				DimensionsCustomFieldX.setEnabled(false);
				DimensionsCustomFieldY.setEnabled(false);
			}
		}
		
	}

	public class EnableAction extends AbstractAction {
		
		public void actionPerformed(ActionEvent e) {
			String selection = (String) sourcesList.getSelectedValue();
			
			if(sources.addSource(selection)) {
				// Show confirmation... change text?
				enabledModel.addElement(selection);
				System.out.println(selection + " is enabled!");
			}
		}
		
	}
	
	public class DisableAction extends AbstractAction {
		
		public void actionPerformed(ActionEvent e) {
			String selection = (String) enabledList.getSelectedValue();
			
			if(sources.removeSource(selection)) {
				// Show confirmation... change text?
				enabledModel.removeElement(selection);
				System.out.println(selection + " is disabled!");
			}
		}
		
	}
	
	public class ConfigAction extends AbstractAction {

		public void actionPerformed(ActionEvent arg0) {
			String selection = (String) sourcesList.getSelectedValue();
			SourcePlugin src = sources.findType(selection);
			
			if(src != null) {
				// Show confirmation... change text?
				JFrame frame = src.getOptionsPane();
				if (frame != null) {
					frame.setVisible(true);
					System.out.println(selection + " config up!");
				} else {
					System.out.println("Unable to open options!");
				}
				
			}
		}
		
	}

	
	/**
	 * Generated by Eclipse
	 */
	private static final long serialVersionUID = -7379941758951948236L;;

	static final int TARGET = 500;

	static final int THREADS = 10;

	private JButton BrowseButton = null;

	/**
	 * A reference to a controller-- what actually calls the Flickr service and
	 * JAI processor to do all the work.
	 */
	public Controller controller;

	// Tabbed view manager
	JTabbedPane tabbedPane = null;
	
	// File I/O components
	JFileChooser FileChooser = null;
	JFileChooser SaveChooser = null;

	// UI Components
	private JTextField FileField = null;
	private JLabel FileLabel = null;
	GenerateMosaicAction GenerateAction = null;
	private JButton GenerateButton = null;
	private JButton SaveButton = null;
	//private JLabel ImageBox = null;
	private JPanel jContentPane = null;
	OpenFileAction OpenAction = null;
	SaveFileAction SaveAction = null;
	private JPanel OptionsPanel = null;
	private JTextField ResolutionField = null;
	private JLabel ResolutionLabel = null;
	private JTextField SearchField = null;
	private JLabel SearchLabel = null;
	private JScrollPane ContentScrollPane = null;
	
	// Advanced Options
	private JPanel DimensionsPanel = null;
	private JRadioButton DimensionsOriginal = null;
	private JRadioButton DimensionsMultiple = null;
	private JRadioButton DimensionsCustom = null;
	private ButtonGroup DimensionsGroup = null;
	private JTextField DimensionsMultipleField = null;
	private JTextField DimensionsCustomFieldX = null;
	private JTextField DimensionsCustomFieldY = null;
	
	private JPanel SourcesPanel = null;
	private JLabel SourcesLabel = null;
	private JCheckBox SourcesFacebook = null;
	private JCheckBox SourcesFlickr = null;
	private JButton SourcesFBAuth = null;
	private JList sourcesList = null;
	private JList enabledList = null;
	private DefaultListModel enabledModel = null;
	
	private JPanel StatusPanel = null;
	private JLabel StatusLabel = null;
	private JProgressBar progressBar = null;
	
	// Main content panel
	private JPanel ContentPanel = null;
	
	// Advanced Options panel
	private JPanel AdvancedOptions = null;

	
	/**
	 * This is the default constructor
	 */
	public WosaicUI() {
		super();
		FileChooser = new JFileChooser();
		SaveChooser = new JFileChooser();
		OpenAction = new OpenFileAction(this, FileChooser);
		SaveAction = new SaveFileAction(this, SaveChooser);
		GenerateAction = new GenerateMosaicAction(this);
		tabbedPane = new JTabbedPane();
		progressBar = new JProgressBar();
		statusObject = new Status(progressBar);
		sources = new Sources(statusObject);
	}

	/**
	 * This method initializes BrowseButton
	 * 
	 * @return javax.swing.JButton
	 */
	private JButton getBrowseButton() {
		if (BrowseButton == null) {
			BrowseButton = new JButton(OpenAction);
			BrowseButton.setText("Browse..");
		}
		return BrowseButton;
	}

	/**
	 * This method initializes FileField
	 * 
	 * @return javax.swing.JTextField
	 */
	private JTextField getFileField() {
		if (FileField == null) {
			FileField = new JTextField(20);
			FileField.setColumns(20);
			FileField.setText("");
		}
		return FileField;
	}

	/**
	 * This method initializes GenerateButton
	 * 
	 * @return javax.swing.JButton
	 */
	private JButton getGenerateButton() {
		if (GenerateButton == null) {
			GenerateButton = new JButton(GenerateAction);
			GenerateButton.setText("Generate Mosaic");
			GenerateButton.setMnemonic(KeyEvent.VK_ENTER);
		}
		return GenerateButton;
	}


	/**
	 * This method initializes jContentPane
	 * 
	 * @return javax.swing.JPanel
	 */
	private JPanel getJContentPane() {
		if (jContentPane == null) {
			jContentPane = new JPanel();
			jContentPane.setLayout(new BorderLayout());
			jContentPane.add(getOptionsPanel(), BorderLayout.NORTH);
			jContentPane.add(getContentScrollPane(), BorderLayout.CENTER);
			jContentPane.add(getStatusPane(), BorderLayout.SOUTH);
		}
		return jContentPane;
	}
	
	private JPanel getStatusPane() {
		if (StatusPanel == null) {
			StatusPanel = new JPanel();
			StatusPanel.setLayout(new GridLayout(1, 2));
			
			StatusLabel = new JLabel();
			StatusPanel.add(StatusLabel);
			StatusPanel.add(progressBar);
		}
		
		return StatusPanel;
	}

	private JPanel getAdvancedOptionsPanel() {

		if (AdvancedOptions == null) {
			AdvancedOptions = new JPanel();
			AdvancedOptions.setLayout(new GridBagLayout());
			//AdvancedOptions.setLayout(new GridLayout(0,2));
			AdvancedOptions.setPreferredSize(new Dimension(600, 60));
			
			// Dimensions Panel
			DimensionsPanel = new JPanel();
			DimensionsPanel.setLayout(new GridBagLayout());
			//DimensionsPanel.setPreferredSize(new Dimension(400, 100));
			GridBagConstraints dimensionsPanelConstraints = new GridBagConstraints();
			dimensionsPanelConstraints.gridx = 0;
			dimensionsPanelConstraints.gridy = 0;
			dimensionsPanelConstraints.anchor = GridBagConstraints.WEST;
			
			// Mosaic Dimensions Label
			GridBagConstraints dimensionsLabelConstraints = new GridBagConstraints();
			dimensionsLabelConstraints.gridx = 0;
			dimensionsLabelConstraints.gridy = 0;
			dimensionsLabelConstraints.anchor = GridBagConstraints.WEST;
			dimensionsLabelConstraints.gridwidth = 2;
			dimensionsLabelConstraints.gridheight = 1;
			JLabel mosaicDimensionsLabel = new JLabel();
			mosaicDimensionsLabel.setText("Mosiac Dimensions");
			DimensionsPanel.add(mosaicDimensionsLabel, dimensionsLabelConstraints);
			
			GridBagConstraints spacerConstraints = new GridBagConstraints();
			spacerConstraints.gridx = 0;
			spacerConstraints.gridy = 1;
			spacerConstraints.anchor = GridBagConstraints.WEST;
			JLabel spacerLabel = new JLabel();
			spacerLabel.setText("      ");
			DimensionsPanel.add(spacerLabel, spacerConstraints);
			
			// Mosaic Dimensions Radio Buttons - Original
			RadioButtonPress listener = new RadioButtonPress();
			DimensionsOriginal = new JRadioButton("Original");
			DimensionsOriginal.setSelected(true);
			DimensionsOriginal.addActionListener(listener);
			GridBagConstraints dimensionsOriginalConstraints = new GridBagConstraints();
			dimensionsOriginalConstraints.gridx = 1;
			dimensionsOriginalConstraints.gridy = 1;
			dimensionsOriginalConstraints.anchor = GridBagConstraints.WEST;
			DimensionsPanel.add(DimensionsOriginal, dimensionsOriginalConstraints);
			
			// Mosaic Dimensions Radio Buttons - Multiple
			DimensionsMultiple = new JRadioButton("Multiple");
			DimensionsMultiple.addActionListener(listener);
			GridBagConstraints dimensionsMultipleConstraints = new GridBagConstraints();
			dimensionsMultipleConstraints.gridx = 1;
			dimensionsMultipleConstraints.gridy = 2;
			dimensionsMultipleConstraints.anchor = GridBagConstraints.WEST;
			DimensionsPanel.add(DimensionsMultiple, dimensionsMultipleConstraints);
			
			DimensionsMultipleField = new JTextField(8);
			DimensionsMultipleField.setColumns(8);
			DimensionsMultipleField.setText("1.0");
			DimensionsMultipleField.setEnabled(false);
			//DimensionsMultipleField.setPreferredSize(new Dimension(5, 30));
			GridBagConstraints dimensionsMultipleFieldConstraints = new GridBagConstraints();
			dimensionsMultipleFieldConstraints.gridx = 1;
			dimensionsMultipleFieldConstraints.gridy = 3;
			dimensionsMultipleFieldConstraints.anchor = GridBagConstraints.WEST;
			dimensionsMultipleFieldConstraints.ipadx = 7;
			DimensionsPanel.add(DimensionsMultipleField, dimensionsMultipleFieldConstraints);
			
			// Mosaic Dimensions Radio Buttons - Custom
			DimensionsCustom = new JRadioButton("Custom");
			DimensionsCustom.addActionListener(listener);
			GridBagConstraints dimensionsCustomConstraints = new GridBagConstraints();
			dimensionsCustomConstraints.gridx = 1;
			dimensionsCustomConstraints.gridy = 4;
			dimensionsCustomConstraints.anchor = GridBagConstraints.WEST;
			DimensionsPanel.add(DimensionsCustom, dimensionsCustomConstraints);
			
			DimensionsCustomFieldX = new JTextField(8);
			DimensionsCustomFieldX.setColumns(8);
			DimensionsCustomFieldX.setText("X-Dimm");
			DimensionsCustomFieldX.setEnabled(false);
			//DimensionsMultipleField.setPreferredSize(new Dimension(5, 30));
			GridBagConstraints dimensionsMultipleCustomXConstraints = new GridBagConstraints();
			dimensionsMultipleCustomXConstraints.gridx = 1;
			dimensionsMultipleCustomXConstraints.gridy = 5;
			dimensionsMultipleCustomXConstraints.anchor = GridBagConstraints.WEST;
			//dimensionsMultipleCustomXConstraints.ipadx = 0;
			dimensionsMultipleCustomXConstraints.fill = GridBagConstraints.NONE;
			DimensionsPanel.add(DimensionsCustomFieldX, dimensionsMultipleCustomXConstraints);
			
			DimensionsCustomFieldY = new JTextField(8);
			DimensionsCustomFieldY.setColumns(8);
			DimensionsCustomFieldY.setText("Y-Dimm");
			DimensionsCustomFieldY.setEnabled(false);
			//DimensionsMultipleField.setPreferredSize(new Dimension(5, 30));
			GridBagConstraints dimensionsMultipleCustomYConstraints = new GridBagConstraints();
			dimensionsMultipleCustomYConstraints.gridx = 2;
			dimensionsMultipleCustomYConstraints.gridy = 5;
			dimensionsMultipleCustomYConstraints.anchor = GridBagConstraints.WEST;
			dimensionsMultipleCustomYConstraints.fill = GridBagConstraints.NONE;
			//dimensionsMultipleCustomYConstraints.ipadx = 0;
			DimensionsPanel.add(DimensionsCustomFieldY, dimensionsMultipleCustomYConstraints);
			
			// Mosaic Dimensions Radio Buttons - Group
			DimensionsGroup = new ButtonGroup();
			DimensionsGroup.add(DimensionsOriginal);
			DimensionsGroup.add(DimensionsMultiple);
			DimensionsGroup.add(DimensionsCustom);
			
			AdvancedOptions.add(DimensionsPanel, dimensionsPanelConstraints);
			//AdvancedOptions.add(DimensionsPanel);
			
			// Sources Panel
			SourcesPanel = new JPanel();
			SourcesPanel.setLayout(new GridBagLayout());
			//SourcesPanel.setPreferredSize(new Dimension(250, 200));
			//DimensionsPanel.setPreferredSize(new Dimension(400, 100));
			GridBagConstraints sourcesPanelConstraints = new GridBagConstraints();
			sourcesPanelConstraints.gridx = 1;
			sourcesPanelConstraints.gridy = 0;
			sourcesPanelConstraints.anchor = GridBagConstraints.WEST;
			
			// Sources Label
			GridBagConstraints sourcesLabelConstraints = new GridBagConstraints();
			sourcesLabelConstraints.gridx = 0;
			sourcesLabelConstraints.gridy = 0;
			sourcesLabelConstraints.anchor = GridBagConstraints.WEST;
			sourcesLabelConstraints.gridwidth = 2;
			SourcesLabel = new JLabel();
			SourcesLabel.setText("Image Sources");
			SourcesPanel.add(SourcesLabel, sourcesLabelConstraints);
			
			// Sources list
			sourcesList = new JList(sources.getSourcesList()); 
			sourcesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			sourcesList.setLayoutOrientation(JList.VERTICAL);
			sourcesList.setVisibleRowCount(-1);
			
			JScrollPane listScroller = new JScrollPane(sourcesList);
			listScroller.setPreferredSize(new Dimension(150, 80));
			
			GridBagConstraints sourcesListConstraints = new GridBagConstraints();
			sourcesListConstraints.gridx = 0;
			sourcesListConstraints.gridy = 1;
			sourcesListConstraints.anchor = GridBagConstraints.WEST;
			sourcesListConstraints.gridwidth = 2;
			
			SourcesPanel.add(listScroller, sourcesListConstraints);
			
			// Enable Button
			JButton SourcesEnableButton = new JButton("Enable");
			SourcesEnableButton.addActionListener(new EnableAction());
			GridBagConstraints sourcesEnableConstraints = new GridBagConstraints();
			sourcesEnableConstraints.gridx = 0;
			sourcesEnableConstraints.gridy = 2;
			sourcesEnableConstraints.anchor = GridBagConstraints.WEST;
			SourcesPanel.add(SourcesEnableButton, sourcesEnableConstraints);
			
			// Configure Button
			JButton SourcesConfigButton = new JButton("Config");
			SourcesConfigButton.addActionListener(new ConfigAction());
			GridBagConstraints sourcesConfigConstraints = new GridBagConstraints();
			sourcesConfigConstraints.gridx = 1;
			sourcesConfigConstraints.gridy = 2;
			sourcesConfigConstraints.anchor = GridBagConstraints.WEST;
			SourcesPanel.add(SourcesConfigButton, sourcesConfigConstraints);
			
			// Enabled Label
			GridBagConstraints sourcesEnLabelConstraints = new GridBagConstraints();
			sourcesEnLabelConstraints.gridx = 2;
			sourcesEnLabelConstraints.gridy = 0;
			sourcesEnLabelConstraints.anchor = GridBagConstraints.WEST;
			sourcesEnLabelConstraints.gridwidth = 2;
			JLabel EnSourcesLabel = new JLabel();
			EnSourcesLabel.setText("Enabled Sources");
			SourcesPanel.add(EnSourcesLabel, sourcesEnLabelConstraints);
			
			// Enabled list
			enabledModel = new DefaultListModel();
			String[] enSources = sources.getEnabledSourcesList();
			
			for (int i=0; i < enSources.length; i++) {
				enabledModel.addElement(enSources[i]);
			}
			
			enabledList = new JList(enabledModel); 
			enabledList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			enabledList.setLayoutOrientation(JList.VERTICAL);
			enabledList.setVisibleRowCount(-1);
			
			JScrollPane listEnabledScroller = new JScrollPane(enabledList);
			listEnabledScroller.setPreferredSize(new Dimension(150, 80));
			
			GridBagConstraints sourcesEnListConstraints = new GridBagConstraints();
			sourcesEnListConstraints.gridx = 2;
			sourcesEnListConstraints.gridy = 1;
			sourcesEnListConstraints.anchor = GridBagConstraints.WEST;
			sourcesEnListConstraints.gridwidth = 2;
			
			SourcesPanel.add(listEnabledScroller, sourcesEnListConstraints);
			
			// Disable Button
			JButton SourcesDisableButton = new JButton("Disable");
			SourcesDisableButton.addActionListener(new DisableAction());
			GridBagConstraints sourcesDisableonstraints = new GridBagConstraints();
			sourcesDisableonstraints.gridx = 2;
			sourcesDisableonstraints.gridy = 2;
			sourcesDisableonstraints.anchor = GridBagConstraints.WEST;
			SourcesPanel.add(SourcesDisableButton, sourcesDisableonstraints);
			
			AdvancedOptions.add(SourcesPanel, sourcesPanelConstraints);
		}
		
		return AdvancedOptions;
	}
	
	/**
	 * This method initializes OptionsPanel
	 * 
	 * @return javax.swing.JPanel
	 */
	private JPanel getOptionsPanel() {
		if (OptionsPanel == null) {
			
			// Options Panel
			OptionsPanel = new JPanel();
			OptionsPanel.setLayout(new GridBagLayout());
			OptionsPanel.setPreferredSize(new Dimension(600, 60));
			OptionsPanel.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createBevelBorder(BevelBorder.RAISED),
					BorderFactory.createEmptyBorder(5, 5, 5, 5)));
			
			// Save Button
			GridBagConstraints saveButtonConstraints = new GridBagConstraints();
			saveButtonConstraints.gridx = 5;
			saveButtonConstraints.gridy = 0;
			OptionsPanel.add(getSaveButton(), saveButtonConstraints);
			
			// Generate Button
			final GridBagConstraints generateButtonConstraints = new GridBagConstraints();
			generateButtonConstraints.gridx = 4;
			generateButtonConstraints.gridheight = 1;
			generateButtonConstraints.gridy = 1;
			OptionsPanel.add(getGenerateButton(), generateButtonConstraints);
			
			// Resolution Field
			final GridBagConstraints resolutionFieldConstraints = new GridBagConstraints();
			resolutionFieldConstraints.fill = GridBagConstraints.HORIZONTAL;
			resolutionFieldConstraints.gridy = 1;
			resolutionFieldConstraints.weightx = 1.0;
			resolutionFieldConstraints.anchor = GridBagConstraints.WEST;
			resolutionFieldConstraints.gridx = 3;
			OptionsPanel.add(getResolutionField(), resolutionFieldConstraints);
			
			// Resolution Label
			final GridBagConstraints resolutionLabelConstraints = new GridBagConstraints();
			resolutionLabelConstraints.gridx = 2;
			resolutionLabelConstraints.gridy = 1;
			ResolutionLabel = new JLabel();
			ResolutionLabel.setText("Resolution:");
			OptionsPanel.add(ResolutionLabel, resolutionLabelConstraints);
			
			// Search Field
			final GridBagConstraints searchFieldConstraints = new GridBagConstraints();
			searchFieldConstraints.fill = GridBagConstraints.HORIZONTAL;
			searchFieldConstraints.gridy = 1;
			searchFieldConstraints.weightx = 1.0;
			searchFieldConstraints.anchor = GridBagConstraints.WEST;
			searchFieldConstraints.gridx = 1;
			OptionsPanel.add(getSearchField(), searchFieldConstraints);
			
			// Search Label
			final GridBagConstraints searchLabelConstraints = new GridBagConstraints();
			searchLabelConstraints.gridx = 0;
			searchLabelConstraints.anchor = GridBagConstraints.EAST;
			searchLabelConstraints.gridy = 1;
			SearchLabel = new JLabel();
			SearchLabel.setText("Search String:");
			OptionsPanel.add(SearchLabel, searchLabelConstraints);
			
			// Browse Button
			final GridBagConstraints browseButtonConstraints = new GridBagConstraints();
			browseButtonConstraints.gridx = 4;
			browseButtonConstraints.anchor = GridBagConstraints.WEST;
			browseButtonConstraints.gridy = 0;
			OptionsPanel.add(getBrowseButton(), browseButtonConstraints);
			
			// File Field
			final GridBagConstraints fileFieldConstraints = new GridBagConstraints();
			fileFieldConstraints.fill = GridBagConstraints.HORIZONTAL;
			fileFieldConstraints.gridy = 0;
			fileFieldConstraints.gridwidth = 3;
			fileFieldConstraints.anchor = GridBagConstraints.WEST;
			fileFieldConstraints.gridx = 1;
			OptionsPanel.add(getFileField(), fileFieldConstraints);
			
			// File Label
			final GridBagConstraints fileLabelConstraints = new GridBagConstraints();
			fileLabelConstraints.gridx = 0;
			fileLabelConstraints.anchor = GridBagConstraints.EAST;
			fileLabelConstraints.gridy = 0;
			FileLabel = new JLabel();
			FileLabel.setText("Source Image:");
			OptionsPanel.add(FileLabel, fileLabelConstraints);

		}
		
		return OptionsPanel;
	}

	/**
	 * This method initializes ResolutionField
	 * 
	 * @return javax.swing.JTextField
	 */
	private JTextField getResolutionField() {
		if (ResolutionField == null) {
			ResolutionField = new JTextField(5);
			ResolutionField.setColumns(5);
			ResolutionField.setText("25");
		}
		return ResolutionField;
	}

	/**
	 * This method initializes SearchField
	 * 
	 * @return javax.swing.JTextField
	 */
	private JTextField getSearchField() {
		if (SearchField == null) {
			SearchField = new JTextField(10);
			SearchField.setColumns(10);
		}
		return SearchField;
	}

	/**
	 * This method initializes this
	 * 
	 * 
	 */
	@Override
	public void init() {
		this.setBounds(new Rectangle(0, 0, 600, 400));
		tabbedPane.addTab("Mosaic", getJContentPane());
		tabbedPane.addTab("AdvancedOptions", getAdvancedOptionsPanel());
		setContentPane(tabbedPane);
		statusObject.setLabel(StatusLabel);
	}

	/**
	 * This method initializes ContentScrollPane	
	 * 	
	 * @return javax.swing.JScrollPane	
	 */
	private JScrollPane getContentScrollPane() {
		if (ContentScrollPane == null) {
			//ImageBox = new JLabel();
			//ImageBox.setText("");
			//ImageBox.setHorizontalTextPosition(SwingConstants.CENTER);
			//ImageBox.setHorizontalAlignment(SwingConstants.CENTER);
			ContentScrollPane = new JScrollPane();
			//ContentScrollPane.setViewportView(ImageBox);
			ContentScrollPane.setBorder(null);
			ContentScrollPane.setViewportView(getContentPanel());
		}
		return ContentScrollPane;
	}

	/**
	 * This method initializes ContentPanel	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getContentPanel() {
		if (ContentPanel == null) {
			GridLayout gridLayout = new GridLayout();
			gridLayout.setRows(1);
			ContentPanel = new JPanel();
			ContentPanel.setLayout(gridLayout);
		}
		return ContentPanel;
	}

	/**
	 * This method initializes SaveButton	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getSaveButton() {
		if (SaveButton == null) {
			SaveButton = new JButton(SaveAction);
			SaveButton.setText("Save");
			SaveButton.setEnabled(false);
		}
		return SaveButton;
	}

}
