# pipeline-graph-analysis-plugin
Plugin for analyzing Jenkins pipeline (formerly workflow) runs by inspecting the directed acyclic graph of FlowNodes that comprises them. Intended for where dependencies prevent it from being including in the workflow api plugin.

Planned contents:

* Timing/status computation APIs 
* Some common visitors/predicates for working with stages & parallel blocks + utilities around them

Future possibilities:

* Extension point for different metrics that can be extracted from a pipeline run by walking the flow graph (using describable/descriptor probably, since you're instantiating an object that is updated as you visit FlowNodes)
* Indexing of stages/blocks in a pipeline flow, to expedite walking the graph
