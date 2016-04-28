# pipeline-graph-analysis-plugin
Plugin for analyzing Jenkins pipeline (formerly workflow) runs by inspecting the directed acyclic graph of FlowNodes that comprises them.  

Planned contents:

* Generic API (FlowScanner) for searching/filtering/visiting the flow graph that is implementation agnostic
* Library of different graph exploration algorithms (depth-first search, single-ancestry search, single ancestry search that jumps over sibling blocks, forked search that visits parallel branches before proceding)
* Utility library containing common searches and match conditions (predicates)
* Caching implementation that continuously updates a piece of information exposed about an in-progress build as new nodes are added.

Future possibilities:

* Extension point for different metrics that can be extracted from a pipeline run by walking the flow graph (using describable/descriptor probably, since you're instantiating an object that is updated as you visit FlowNodes)
* Indexing of stages/blocks in a pipeline flow, to expedite walking the graph
