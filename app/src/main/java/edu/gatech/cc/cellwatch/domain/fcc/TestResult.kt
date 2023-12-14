package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.data.model.Cell
import edu.gatech.cc.cellwatch.data.model.Location

data class TestResult(
    val latencyResult: LatencyResult? = null,
    val throughputTestResult: ThroughputResult? = null,
    var locations: List<Location>,
    var cells: List<Cell>? = null
)
