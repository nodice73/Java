# Bioact

An ImageJ plugin for analyzing fluorescence microscopy images.

Requires the [HCImaging](https://github.com/nodice73/Java/tree/master/hcimage) library. The jar included in this folder should work. If not, re-compile and add a link to the jar file.

To use as an ImageJ plugin, copy or link any of the Bioact_.java files to the "plugin" folder of your Fiji/ImageJ installation. Copy or link the hcimage.jar file to the "jar" folder of your Fiji/ImageJ installation.

## Folder structure
The project folder should contain folders that represent
locations of the form [A-Z][0-9][0-9][a-z]. The first three characters 
correspond to positions in a microtiter plate. For example, B06, which would
be the second row and 6th column of a standard 96-well microtiter plate.
The fourth character corresponds to an optional position within that well.

In each position folder, there should be folders representing the
wavelength of light used in acquisition of the form WL[0-9]+. WL0 should
contain brightfield images.

Image names are of the form *project*\_*image_number*\_*position*\_*wavelength*.tif.

For example, myproject_0001_A09a_WL0.tif

## Picture information
In addition, the project folder should contain a folder called "picture_information"
that contains text files. These text files should be named identically to the images,
but end with ".txt" and should contain the image metadata.

Image metadata is tab-delimited. Currently, the following information is understood:

    // Date           M/dd/yyyy |
    // Time           H:mm a    |  Using SimpleDateFormat
    // Exposure       \d\.\d{6}
    // Binning        \dX\d
    // Filter_Cube    \w+
    // Analog_Gain    \dX
    // X_position     -?\d+\.\d{6}
    // Y_position     -?\d+\.\d{6}
    // Z_position     -?\d+\.\d{6}
    // x_dimension    \d+
    // y_dimension    \d+
    // These next two were added recently, and are not in experiments
    // before May 2010.
    // iris_setting   \d+
    // brightness (percent)      \d+


An example project folder can be found at
      https://github.com/nodice73/ImageJ/tree/master. 
Download and run the "test_images" folder.
