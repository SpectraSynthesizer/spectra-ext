=======================================================================================================================
__________.__       .__                                                                                         
\______   \__| ____ |  |__                                                                                      
 |       _/  |/ ___\|  |  \                                                                                     
 |    |   \  \  \___|   Y  \                                                                                    
 |____|_  /__|\___  >___|  /                                                                                    
        \/        \/     \/                                                                                     
_________                __                .__  .__                                                             
\_   ___ \  ____   _____/  |________  ____ |  | |  |   ___________                                              
/    \  \/ /  _ \ /    \   __\_  __ \/  _ \|  | |  | _/ __ \_  __ \                                             
\     \___(  <_> )   |  \  |  |  | \(  <_> )  |_|  |_\  ___/|  | \/                                             
 \______  /\____/|___|  /__|  |__|   \____/|____/____/\___  >__|                                                
        \/            \/                                  \/                                                    
 __      __        .__   __                                                                                     
/  \    /  \_____  |  | |  | __ ___________                                                                     
\   \/\/   /\__  \ |  | |  |/ // __ \_  __ \                                                                    
 \        /  / __ \|  |_|    <\  ___/|  | \/                                                                    
  \__/\  /  (____  /____/__|_ \\___  >__|                                                                       
       \/        \/          \/    \/                                                                           
========================================================================================================================

Rich Controller Walker was developed in order to improve the exploratory capabilities of SPECTRA engineers.
The walker offers rich debugging and analysis tools for specifications, and allows the user to gain a better 
understanding of the synthesized controller.

Once the engineers have synthesized the SPECTRA specification, they may begin "walking" on the controller. 
At its core, the RCW allows engineers to play as the environment, system or both. It is possible to choose
the next step from the list of possible successors ('Next Step'), as well as back-track ('Step Back').

In addition, the Rich Controller Walker offers the following functions -
1) Choose variables to set with auto-completion -
Under 'Filter', engineers can type/select names of variables in spec (auto-completion when pushing 'space'),
and set a constraining value to add as a filter. This will filter the alternative steps list presented to the user.
RCW supports infinitely many filters.

2) Enable/Disable log generation - 
Un/checking the 'Generate Log' check-box on the bottom left corner of the GUI toggles log generation.
The name of the generated log is displayed below the check-box. Generated logs carry a time-stamp.
It should be noted that the console keeps track of step history regardless of log generation.

3) Load and walk on log -
Engineers can load previously generated log files at any time by pushing 'Load Log'. Loading a log will clear 
previous walk’s state, but preserve watches and breakpoints. While in ‘Log Walk’ mode, users will traverse the
logged states until reaching end of log or actively pushing 'Exit Log'. Upon exiting the mode, the users will be
presented with all possible steps from the current state. Users can skip to end or beginning of log by clicking
'Skip to Start' or 'Skip to End'. ‘Log Walk’ mode is indicated by a red/green indicator (green – on; red – off), 
which includes the loaded log’s name (if relevant).

4) Back-tracking on walk to explore alternatives -
Clicking 'Step Back' returns the controller to its previous state. This can be done until no such states exist.
It should be noted that it is not possible to back-track from one mode to another. While in 'Log Walk', engineers
can click 'Skip to start' to back-track to the first logged state in the loaded log.

5) User-defined predicates as break points and watches -
Predicates can be added as 'Watches' and 'Break Points' by clicking on the appropriate 'Add' button underneath
each of the two grids on the right-most part of the GUI (labelled 'Watches' and 'Break Points'). 'Watch' values
are evaluated to their left (colored red when value chages), and breakpoints are highlighted in green while satisfied.
Invalid expressions are resolved to "N/A". Predicates can be removed by clicking the appropriate 'Remove' button.
Both watches and break-points are preserved as resource properties of the relevant specification file.

6) Reachability and walk to user-defined predicate -
Engineers can play the reachability game to any break-point. Upon success, the user can choose to ‘Reachability Walk’
on the calculated steps to the break-point (highlighted in yellow). Playing reachability to a satisfied break-point
results in a notification via dialog box. Similarly to ‘Log Walk’ mode, while in ‘Reachability Walk’, users will 
traverse states on route until reaching the break-point or clicking 'Exit Reachability'. In ‘Log Walk’, reachability
is played exclusively on logged states. Upon exiting the mode, the users will be presented with all possible steps 
from the current state. Users can skip to end of route by clicking 'Skip to End'. 
‘Reachability Walk’ mode is indicated by a red/green indicator.

==========================================================================================================================

