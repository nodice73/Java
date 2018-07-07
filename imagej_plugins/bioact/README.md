# Bioact

An ImageJ plugin for analyzing fluorescence microscopy images.

Requires the [HCImaging](https://github.com/nodice73/Java/tree/master/hcimage) library (the jar included in this folder will work).

Bioact expects a particular folder structure:

The "project_path" should contain folders that represent
locations of the form [A-Z][0-9][0-9][a-z]. The first three characters 
correspond to positions in a microtiter plate. For example, B06, which would
be the second row and 6th column of a standard 96-well microtiter plate.
The fourth character corresponds to an optional position within that well.

In each position folder, there should be folders representing the
wavelength of light used in acquisition of the form WL[0-9]+. WL0 should
contain brightfield images.

Example images can be found at
      https://github.com/nodice73/ImageJ/tree/master. 
Download and run the "test_images" folder.
