# CPPNArtEvolution

This project contains several forms of interactive art evolution, all based on CPPNs. All code in this project is part of the main [MM-NEAT project](https://github.com/schrum2/MM-NEAT), but this project is simplified to remove much extraneous code not required for interactive evolution. This project is also much easier to use and launch. Just follow the instructions below:

## Windows

Just double-click any of the batch files with the Launch prefix:

* Launch-Picbreeder.bat: Evolve 2D pictures, just like the original [Picbreeder](http://picbreeder.org/)
* Launch-AnimationBreeder.bat: Evolve 2D animations!
* Launch-3DObjectBreeder.bat: Evolve 3D shapes, in a manner similar to [Endless Forms](http://endlessforms.com/)
* Launch-3DAnimationBreeder.bat: Evolve 3D animations!
* Launch-Breedesizer.bat: Evolve tones that can be used to play MIDI files, similar to the original [Breedesizer](http://bthj.is/breedesizer/)

These batch files all use the CPPNArtEvolution.jar file that is already in the repository. However, if you want to recompile this jar file, you can use the build.xml ANT build script, or create an executable jar file with the class edu.southwestern.MMNEAT.MMNEAT as the main class.

## Mac/Linux/Unix

The batch files listed above do not use any Windows-specific commands, so they can easily be executed as bash scripts as well. Alternately, you can just copy-paste the command inside of any of these files to your terminal.

## Help

If you need any help, then please contact me at schrum2@southwestern.edu!

Also, you can evolve neural networks for lots of other interesting applications by using the original [MM-NEAT project](https://github.com/schrum2/MM-NEAT), which contains the interactive evolution code from this project, along with code to evolve agent behavior for various tasks (Ms. Pac-Man, Tetris, and Mario to name a few)