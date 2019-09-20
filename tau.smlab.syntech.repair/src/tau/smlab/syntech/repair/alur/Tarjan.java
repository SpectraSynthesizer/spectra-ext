/*
Copyright (c) since 2015, Tel Aviv University and Software Modeling Lab

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of Tel Aviv University and Software Modeling Lab nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL Tel Aviv University and Software Modeling Lab 
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE 
GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT 
LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT 
OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
*/

package tau.smlab.syntech.repair.alur;
import java.util.*;

/**
 * Strongly connected components of a graph
 * The implementation of the algorithm taken from: http://www.sanfoundry.com/java-program-tarjan-algorithm/
 * 
 * @author moskovitch
 * @author novgorodov
 */

class Tarjan
{
    /** number of vertices **/
    private int V;    
    /** preorder number counter **/
    private int preCount;
    /** low number of v **/
    private int[] low;
    /** to check if v is visited **/
    private boolean[] visited;      
    /** to store given graph **/
    private List<Integer>[] graph;
    /** to store all scc **/
    private List<List<Integer>> sccComp;
    private Stack<Integer> stack;
 
    /** function to get all strongly connected components **/
    public List<List<Integer>> getSCComponents(List<Integer>[] graph) 
    {
        V = graph.length;
        this.graph = graph;
        low = new int[V];
        visited = new boolean[V];
        stack = new Stack<Integer>();
        sccComp = new ArrayList<>();
 
        for (int v = 0; v < V; v++)
              if (!visited[v])
                dfs(v);
 
        return sccComp;
    }
    /** function dfs **/
    public void dfs(int v) 
    {
        low[v] = preCount++;
        visited[v] = true;
        stack.push(v);
        int min = low[v];
        for (int w : graph[v]) 
        {
            if (!visited[w])
                dfs(w);
            if (low[w] < min) 
                min = low[w];
        }
        if (min < low[v]) 
        { 
            low[v] = min; 
            return; 
        }        
        List<Integer> component = new ArrayList<Integer>();
        int w;
        do
        {
            w = stack.pop();
            component.add(w);
            low[w] = V;                
        } while (w != v);
        sccComp.add(component);        
    }    
}