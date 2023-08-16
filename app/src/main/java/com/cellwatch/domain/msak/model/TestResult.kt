package com.cellwatch.domain.msak.model

import com.cellwatch.data.model.Cell
import com.cellwatch.data.model.Location

data class TestResult(
    val latencyResult: LatencyResult? = null,
    val throughputTestResult: ThroughputTestResult? = null,
    var locations: List<Location>,
    var cells: List<Cell>? = null
)
