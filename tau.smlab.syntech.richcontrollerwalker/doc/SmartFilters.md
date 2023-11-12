# "Smart Filter" Algorithm
The "Smart Filter" dialog implements a partial assignments algorithm for finding possible filters that can be applied in order to find "interesting" variables.

Informally, the function tries to find variable assignments that cause all other variables to either become dont care variables, or get a fixed assignment.
The intuition behind the algorithm is that such assignments find "important" variables that determine all the other variables.

The algorithm goes over each possible assignment for each variable, and checks if it determines all other variables as previously described. If such an assignment is found, the algorithm tries to find more such variables recursively for each assignment of the variable that was found. Otherwise, the algorithm stops, and doesn't try pairs, triplets or more variables. The algorithm gets a `depth` parameter to decide the maximum recursion depth.

The result of the algorithm is a set of assignments that, together, cover all the possible assignments. The result depends on the variable order, and therefore the dialog allows changing the variable order and re-running the algorithm.

Here is the pseudo-code of the algorithm implemented in `PartialAssignment::getSmallestPartialAssignments`:

```py
def smartFilter(BDD successors, int depth):
    """
    The resulting "PartialAssignment" object can be seen as a tree. Each node in the tree is a possible assignment. 
    """
    # If we got a full assignment, we're finished
    if isFullAssignment(successors):
        return PartialAssignment(COMPLETE, True)
    # Else, if we reached depth 0 then the assignment is incomplete
    if depth == 0:
        return PartialAssignment(INCOMPLETE)

    # If there is no single determining variable, we finish this branch of the search
    var = findDeterminingVar(successors)
    if not var:
        return PartialAssignment(INCOMPLETE)

    result = PartialAssignment()
    for val in possibleValues(var):
        if isFullAssignment(successors && var=val):
            # var=val determines everything
            result.addChild(COMPLETE, var=val)
        else:
            # The current value doesn't determine, look for other determining variables recursively
            child = smartFilter(successors && var=val, depth - 1)
            child.assignment = (var=val)
            result.addChild(child.isComplete, child)

    return result

# Helper functions

def isFullAssignment(successors):
    # Return true if all variables are fixed/don't care

def findDeterminingVar(BDD successors):
    for var in allVars:
        for val in possibleValues(var):
            if isFullAssignment(successors && var=val):
                return var
    # There's no single determining variable
    return None
```