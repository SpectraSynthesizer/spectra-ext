# Spectra Synthesizer Extensions
This repository contains extensions for the Spectra synthesizer tool. The extensions are Eclipse plug-ins and are independant of each other:
- Symbolic counter strategy generator
- Well separation checker
- Repair for unrealizable specifications
- Rich controller walker
- Vacuity checker

In order to integrate any of the extensions into your working copy of Eclipse with Spectra tools installed:
- Follow the instructions on `spectra-synt` repository.
- `git clone` this repository into your computer.
- Import relevant projects into your workspace. After the clone you should have a folder called `spectra-ext` in your file system, containing many Java projects. Import the desired extensions by 'Import -> Projects from Folder or Archive', then choosing `spectra-ext` directory, and selecting relevant projects from there. Do not import the spectra-ext folder itself (it is not a real project and it might cause problems if imported together with the other real projects).
- Rebuild from Eclipse.
- Run an Eclipse instance by creating a new Eclipse Application configuration in the Run Configurations window.

For further information about the Spectra synthesizer and language, please visit `spectra-synt` repository.
