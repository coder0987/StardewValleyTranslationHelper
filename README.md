# Translate Stardew Valley
Without the technical skills it previously demanded

Stardew Valley Translation Helper is made to handle all of the difficult programming and JSON formatting so you can do what you came here to do: translate

## Installation

0. Ensure Java and Stardew Valley are installed on your machine
1. Download the latest release and unzip it anywhere
2. Run the program
3. Translate!

## Running the Program

On Windows devices with Java installed, it should be as easy as double-clicking on the .jar  
If that isn't the case, there are two ways to run the program:
1. On Windows, you can set Java as the default program for .jar files:
   - Right click on StardewValleyTranslationHelper.jar
   - Select "Open With"
   - Search for "Java Platform(TM) SE Binary"
   - Select "Always use this app to open .jar files"
   - Click Ok
2. If you aren't on Windows, or the previous solution did not work, you can run jar files from the command line:
   - Open a Terminal / Command Prompt in the directory you downloaded StardewValleyTranslationHelper.jar to
   - Run ``java -jar StardewValleyTranslationHelper.jar``
   - This method also has the added benefit of seeing any logged errors in the console
  
## Issues, Errors, and Debugging

### Permission Errors

Especially on Linux systems, there can be permission issues with the installation process  
While SVTH does try and handle these automatically and provide instructions for potential issues, some cases might not be covered.  
In most cases, following the [Getting Started guide](https://stardewvalleywiki.com/Modding:Player_Guide/Getting_Started) should fix your issues  

If you have issues after installing, you can try changing the file permissions for the mods directory like so:
1. First, navigate to Stardew Valley/mods
2. Open a Terminal
3. Run ``chmod +rw -R LANGUAGE_NAME``

If your issue persists, I encourage your to create an issue on GitHub and include any relevant information

### Miscellaneous Issues

For a clean install:
1. Backup your Translation by copying the language folder to another directory
2. Delete the Stardew Valley directory and everything in it
3. Uninstall / Reinstall Stardew Valley on Steam
4. Replace your language folder in the mods directory
5. Run SVTH to reinstall SMAPI, XNBHack, etc.

If this doesn't fix your issue, I encourage your to create an issue on GitHub and include any relevant information
